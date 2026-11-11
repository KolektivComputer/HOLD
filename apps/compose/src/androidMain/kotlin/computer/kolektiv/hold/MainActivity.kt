package computer.kolektiv.hold

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import computer.kolektiv.hold.geo.nominatimAddress
import computer.kolektiv.hold.store.JsonHoldRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private val captureState = mutableStateOf<CaptureRequest?>(null)
    private var pictureBytes: ((ByteArray) -> Unit)? = null
    private var pictureError: ((String) -> Unit)? = null
    private var whenLocated: (() -> Unit)? = null
    private var locationRefused: (() -> Unit)? = null
    private var whenMic: (() -> Unit)? = null
    private var micRefused: (() -> Unit)? = null
    private var whenCamera: (() -> Unit)? = null
    private var cameraRefused: (() -> Unit)? = null
    private var deleteAfter = false
    private var shotFile: File? = null
    private var photoUri: Uri? = null

    private val pickPicture = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        deliverPicture(uri)
    }

    private val takeShot = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        deliverPicture(if (saved) photoUri else null)
    }

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) whenCamera?.invoke() else cameraRefused?.invoke()
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) whenLocated?.invoke() else locationRefused?.invoke()
    }

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) whenMic?.invoke() else micRefused?.invoke()
    }
    private var outsideReady: (() -> Unit)? = null
    private var outsideFailed: ((String) -> Unit)? = null
    private var exportBytes: ByteArray? = null
    private var exportDone: (() -> Unit)? = null
    private var exportFailed: ((String) -> Unit)? = null
    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            outsideFailed?.invoke("No folder was opened.")
            return@registerForActivityResult
        }
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val both = read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (runCatching { contentResolver.takePersistableUriPermission(uri, both) }.isFailure) {
            runCatching { contentResolver.takePersistableUriPermission(uri, read) }
        }
        File(filesDir, "outside-tree.txt").writeText(uri.toString())
        if (outsideTree() == null) outsideFailed?.invoke("HOLD couldn't keep permission to read that folder.")
        else outsideReady?.invoke()
    }
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val bytes = exportBytes
        if (uri == null || bytes == null) {
            exportFailed?.invoke("Export was cancelled.")
            return@registerForActivityResult
        }
        val wrote = runCatching { contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }.isSuccess
        if (wrote) exportDone?.invoke() else exportFailed?.invoke("Couldn't write that file.")
    }

    internal fun outsideTree(): Uri? {
        val raw = File(filesDir, "outside-tree.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val uri = Uri.parse(raw)
        val held = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return if (held) uri else null
    }

    internal fun chooseOutside(onReady: () -> Unit, onError: (String) -> Unit) {
        outsideReady = onReady
        outsideFailed = onError
        openTree.launch(null)
    }

    internal fun writeOutside(name: String, bytes: ByteArray, onDone: () -> Unit, onError: (String) -> Unit) {
        exportBytes = bytes
        exportDone = onDone
        exportFailed = onError
        createDoc.launch(name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureState.value = captureOf(intent)
        val file = File(filesDir, "hold.json")
        val repository = JsonHoldRepository(
            read = { file.takeIf { it.exists() }?.readText() },
            write = { file.writeText(it) },
        )
        val ports = AndroidPorts(this)
        setContent {
            val capture by captureState
            HoldApp(repository, poster = ::postJson, speech = AndroidSpeech(this), ports = ports, capture = capture)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureState.value = captureOf(intent)
    }

    private fun captureOf(intent: Intent?): CaptureRequest? {
        val data = intent?.data ?: return null
        if (data.scheme != "hold" || data.host != "capture") return null
        val text = data.getQueryParameter("text") ?: return null
        if (text.isBlank()) return null
        return CaptureRequest(data.getQueryParameter("kind") ?: "note", text)
    }

    internal fun locationAllowed(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    internal fun ensureLocation(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (locationAllowed()) {
            onGranted()
            return
        }
        whenLocated = onGranted
        locationRefused = onDenied
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    internal fun ensureMic(onGranted: () -> Unit, onDenied: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
            return
        }
        whenMic = onGranted
        micRefused = onDenied
        askMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    internal fun choosePicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit, deleteAfter: Boolean) {
        pictureBytes = onBytes
        pictureError = onError
        this.deleteAfter = deleteAfter
        pickPicture.launch("image/*")
    }

    internal fun takePicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit, deleteAfter: Boolean) {
        val launch = {
            val file = File(cacheDir, "shot-${System.currentTimeMillis()}.jpg")
            shotFile = file
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            photoUri = uri
            pictureBytes = onBytes
            pictureError = onError
            this.deleteAfter = deleteAfter
            takeShot.launch(uri)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launch()
            return
        }
        whenCamera = launch
        cameraRefused = { onError("Camera permission was refused.") }
        askCamera.launch(Manifest.permission.CAMERA)
    }

    private fun deliverPicture(uri: Uri?) {
        if (uri == null) {
            pictureError?.invoke("No picture.")
            return
        }
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        shotFile?.delete()
        shotFile = null
        if (bytes == null) {
            pictureError?.invoke("Couldn't read that picture.")
            return
        }
        pictureBytes?.invoke(bytes)
        if (!deleteAfter) return
        val removed = runCatching { contentResolver.delete(uri, null, null) }.getOrDefault(0)
        if (removed == 0) pictureError?.invoke("Saved in HOLD. The original could not be deleted.")
    }
}

private fun postJson(url: String, body: String, token: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
    connection.doOutput = true
    connection.connectTimeout = 20_000
    connection.readTimeout = 40_000
    connection.outputStream.use { it.write(body.encodeToByteArray()) }
    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val text = stream?.bufferedReader()?.readText().orEmpty()
    if (code !in 200..299) error(text.ifBlank { "The server answered $code." })
    return text
}

private class AndroidPorts(private val activity: MainActivity) : PocketPorts {
    private val blobs = File(activity.filesDir, "blobs")
    private val tiles = File(activity.filesDir, "tiles")
    private var player: MediaPlayer? = null

    override fun stopPlay() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
    }

    override fun play(id: String, onDone: () -> Unit) {
        val bytes = loadBlob(id) ?: run {
            activity.runOnUiThread(onDone)
            return
        }
        stopPlay()
        val file = File(activity.cacheDir, "play-$id")
        file.writeBytes(bytes)
        val next = MediaPlayer()
        try {
            next.setDataSource(file.absolutePath)
            next.setOnCompletionListener {
                activity.runOnUiThread {
                    stopPlay()
                    onDone()
                }
            }
            next.prepare()
            next.start()
            player = next
        } catch (_: Exception) {
            next.release()
            activity.runOnUiThread(onDone)
        }
    }

    override fun here(onFix: (Double, Double, Double?) -> Unit, onError: (String) -> Unit) {
        activity.ensureLocation(onGranted = { deliver(onFix, onError, once = true) }, onDenied = { onError("Location permission was refused.") })
    }

    override fun watch(onFix: (Double, Double, Double?) -> Unit): () -> Unit {
        if (!activity.locationAllowed()) return {}
        val manager = activity.getSystemService(LocationManager::class.java) ?: return {}
        val provider = provider(manager) ?: return {}
        val listener = LocationListener { location -> onFix(location.latitude, location.longitude, location.accuracy.toDouble()) }
        manager.requestLocationUpdates(provider, 20_000L, 15f, listener, Looper.getMainLooper())
        return { manager.removeUpdates(listener) }
    }

    override fun pickPicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit, deleteAfter: Boolean) {
        activity.choosePicture(onBytes, onError, deleteAfter)
    }

    override fun takePicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit, deleteAfter: Boolean) {
        activity.takePicture(onBytes, onError, deleteAfter)
    }

    override fun saveBlob(id: String, bytes: ByteArray) {
        blobs.mkdirs()
        File(blobs, safe(id)).writeBytes(bytes)
    }

    override fun loadBlob(id: String): ByteArray? = File(blobs, safe(id)).takeIf { it.exists() }?.readBytes()

    override fun fetchTile(z: Int, x: Int, y: Int): ByteArray? {
        val n = 1 shl z.coerceIn(0, 20)
        val wrappedX = ((x % n) + n) % n
        val wrappedY = y.coerceIn(0, n - 1)
        val file = File(tiles, "$z-$wrappedX-$wrappedY.png")
        if (file.exists() && file.length() > 0L) return file.readBytes()
        val connection = URL("https://basemaps.cartocdn.com/rastertiles/voyager/$z/$wrappedX/$wrappedY.png").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "HOLD/0.1 (personal pocket log)")
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        if (connection.responseCode !in 200..299) return null
        val bytes = connection.inputStream.use { it.readBytes() }
        tiles.mkdirs()
        file.writeBytes(bytes)
        return bytes
    }

    override fun street(lat: Double, lon: Double): String? {
        val connection = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon").openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "HOLD/0.1 (personal pocket log)")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        if (connection.responseCode !in 200..299) return null
        return nominatimAddress(connection.inputStream.bufferedReader().readText())
    }

    private fun deliver(onFix: (Double, Double, Double?) -> Unit, onError: (String) -> Unit, once: Boolean) {
        val manager = activity.getSystemService(LocationManager::class.java) ?: return onError("No location service.")
        val provider = provider(manager) ?: return onError("Location is turned off.")
        val last = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        if (last != null) onFix(last.latitude, last.longitude, last.accuracy.toDouble())
        if (!once && last != null) return
        manager.requestLocationUpdates(provider, 0L, 0f, object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onFix(location.latitude, location.longitude, location.accuracy.toDouble())
                manager.removeUpdates(this)
            }
        }, Looper.getMainLooper())
    }

    private fun provider(manager: LocationManager): String? = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "blob" }

    private val shelf = ArrayDeque<Pair<String, String>>()

    override fun hasOutside(): Boolean = activity.outsideTree() != null

    override fun openOutside(onReady: () -> Unit, onError: (String) -> Unit) {
        activity.chooseOutside(onReady, onError)
    }

    override fun resetOutside() {
        shelf.clear()
    }

    override fun outsideList(): List<ShelfNode> {
        val tree = activity.outsideTree() ?: return emptyList()
        if (shelf.isEmpty()) {
            val id = DocumentsContract.getTreeDocumentId(tree)
            shelf.addLast(id to documentName(tree, id))
        }
        return children(tree, shelf.last().first)
    }

    override fun outsideEnter(id: String, name: String) {
        shelf.addLast(id to name)
    }

    override fun outsideUp(): Boolean {
        if (shelf.size <= 1) return false
        shelf.removeLast()
        return true
    }

    override fun outsidePath(): String = shelf.joinToString(" / ") { it.second }

    override fun outsideRead(id: String): ByteArray? {
        val tree = activity.outsideTree() ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        return activity.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    override fun outsideExport(name: String, bytes: ByteArray, onDone: () -> Unit, onError: (String) -> Unit) {
        activity.writeOutside(name, bytes, onDone, onError)
    }

    private fun children(tree: Uri, parent: String): List<ShelfNode> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val rows = mutableListOf<ShelfNode>()
        activity.contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: "Untitled"
                val mime = cursor.getString(mimeColumn).orEmpty()
                val directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val kind = when {
                    directory -> "folder"
                    mime.startsWith("image/") -> "image"
                    mime.startsWith("audio/") -> "audio"
                    mime.startsWith("text/") || name.endsWith(".md", true) || name.endsWith(".txt", true) -> "text"
                    else -> "other"
                }
                rows += ShelfNode(id, name, directory, kind)
            }
        }
        return rows.sortedWith(compareBy<ShelfNode> { !it.directory }.thenBy { it.name.lowercase() })
    }

    private fun documentName(tree: Uri, id: String): String {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        activity.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0) ?: "Outside"
        }
        return "Outside"
    }
}

private class AndroidSpeech(private val activity: MainActivity) : Speech {
    private var recognizer: SpeechRecognizer? = null
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var mime = "audio/mp4"
    private var startedAt = 0L
    private var transcript = ""
    private var onDone: ((CatchResult) -> Unit)? = null
    private var onLevel: ((Float) -> Unit)? = null
    private var onPartial: (String) -> Unit = {}
    private var transcribe = true
    private var muted = false
    private var paused = false
    private var delivered = false
    private val peaks = ArrayList<Float>()
    private val handler = Handler(Looper.getMainLooper())
    private val clock = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if (!paused) {
                val level = if (muted) 0f else runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)
                peaks += level
                onLevel?.invoke(level)
            }
            clock.postDelayed(this, 70)
        }
    }

    override fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit, onError: (String) -> Unit, transcribe: Boolean) {
        activity.ensureMic(
            onGranted = { begin(onPartial, onLevel, onError, transcribe) },
            onDenied = { onError("Microphone permission was refused.") },
        )
    }

    private fun begin(onPartial: (String) -> Unit, onLevel: (Float) -> Unit, onError: (String) -> Unit, transcribe: Boolean) {
        val started = startRecorder()
        if (started == null) {
            onError("Couldn't open the microphone.")
            return
        }
        recorder = started.first
        file = started.second
        mime = started.third
        startedAt = System.currentTimeMillis()
        transcript = ""
        peaks.clear()
        delivered = false
        paused = false
        muted = false
        this.transcribe = transcribe
        this.onPartial = onPartial
        onDone = null
        this.onLevel = onLevel
        clock.post(poll)
        if (transcribe) startRecognizer(onPartial)
    }

    override fun pause() {
        paused = true
        runCatching { recorder?.pause() }
        recognizer?.stopListening()
    }

    override fun resume() {
        paused = false
        if (muted) return
        runCatching { recorder?.resume() }
        if (transcribe) startRecognizer(onPartial)
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        if (paused) return
        if (muted) {
            runCatching { recorder?.pause() }
            recognizer?.stopListening()
        } else {
            runCatching { recorder?.resume() }
            if (transcribe) startRecognizer(onPartial)
        }
    }

    override fun stop(onDone: (CatchResult) -> Unit) {
        this.onDone = onDone
        clock.removeCallbacks(poll)
        val rec = recorder
        recorder = null
        try {
            rec?.stop()
        } catch (_: RuntimeException) {
            // An empty clip throws. The file is kept if any audio landed.
        }
        rec?.release()
        recognizer?.stopListening()
        handler.postDelayed({ finish() }, 900)
    }

    private fun finish() {
        if (delivered) return
        delivered = true
        clock.removeCallbacks(poll)
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        val bytes = file?.takeIf { it.exists() && it.length() > 0L }?.readBytes()
        file?.delete()
        file = null
        val duration = if (startedAt == 0L) 0L else (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        onDone?.invoke(CatchResult(transcript.trim(), bytes, if (bytes == null) "" else mime, duration, peaks.toList()))
        peaks.clear()
    }

    private fun startRecorder(): Triple<MediaRecorder, File, String>? {
        val dir = File(activity.filesDir, "voice").apply { mkdirs() }
        val attempts = listOf(
            Triple(MediaRecorder.OutputFormat.MPEG_4, MediaRecorder.AudioEncoder.AAC, "audio/mp4" to "m4a"),
            Triple(MediaRecorder.OutputFormat.THREE_GPP, MediaRecorder.AudioEncoder.AMR_NB, "audio/3gpp" to "3gp"),
        )
        for ((format, encoder, kind) in attempts) {
            val target = File(dir, "catch-${System.currentTimeMillis()}.${kind.second}")
            val next = MediaRecorder()
            try {
                next.setAudioSource(MediaRecorder.AudioSource.MIC)
                next.setOutputFormat(format)
                next.setAudioEncoder(encoder)
                next.setOutputFile(target.absolutePath)
                next.prepare()
                next.start()
                return Triple(next, target, kind.first)
            } catch (_: RuntimeException) {
                next.release()
                target.delete()
            }
        }
        return null
    }

    private fun startRecognizer(onPartial: (String) -> Unit) {
        recognizer?.destroy()
        recognizer = null
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) return
        val rec = runCatching { SpeechRecognizer.createSpeechRecognizer(activity) }.getOrNull() ?: return
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    transcript = text
                    onPartial(text)
                }
                if (onDone != null) finish()
            }

            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    transcript = text
                    onPartial(text)
                }
            }

            override fun onError(error: Int) {
                if (onDone != null) finish()
            }

            override fun onReadyForSpeech(params: Bundle) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { rec.startListening(intent) }
    }

    override fun transcribe(bytes: ByteArray, mime: String, onText: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onError("This device has no speech recognizer. The recording is still saved.")
            return
        }
        activity.ensureMic(onGranted = {
            val ext = if (mime.contains("3gp")) "3gp" else "m4a"
            val clip = File(activity.cacheDir, "hear-${System.currentTimeMillis()}.$ext")
            clip.writeBytes(bytes)
            val rec = runCatching { SpeechRecognizer.createSpeechRecognizer(activity) }.getOrNull()
            if (rec == null) {
                clip.delete()
                onError("Couldn't start transcription.")
                return@ensureMic
            }
            var player: MediaPlayer? = null
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    player?.release()
                    rec.destroy()
                    clip.delete()
                    if (text.isBlank()) onError("No words came back.") else onText(text)
                }

                override fun onPartialResults(partialResults: Bundle) = Unit

                override fun onError(error: Int) {
                    player?.release()
                    rec.destroy()
                    clip.delete()
                    onError("Couldn't transcribe that recording.")
                }

                override fun onReadyForSpeech(params: Bundle) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            if (runCatching { rec.startListening(intent) }.isFailure) {
                rec.destroy()
                clip.delete()
                onError("Couldn't start transcription.")
                return@ensureMic
            }
            val next = MediaPlayer()
            player = next
            if (runCatching {
                    next.setDataSource(clip.absolutePath)
                    next.setOnCompletionListener { rec.stopListening() }
                    next.prepare()
                    next.start()
                }.isFailure
            ) {
                next.release()
                rec.destroy()
                clip.delete()
                onError("Couldn't play the recording back.")
            }
        }, onDenied = { onError("Microphone permission was refused.") })
    }
}
