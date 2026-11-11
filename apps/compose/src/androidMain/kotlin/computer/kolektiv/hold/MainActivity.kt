package computer.kolektiv.hold

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import androidx.core.content.ContextCompat
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

    private val pickPicture = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            pictureError?.invoke("No picture.")
            return@registerForActivityResult
        }
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) pictureError?.invoke("Couldn't read that picture.") else pictureBytes?.invoke(bytes)
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) whenLocated?.invoke() else locationRefused?.invoke()
    }

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) whenMic?.invoke() else micRefused?.invoke()
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

    internal fun choosePicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit) {
        pictureBytes = onBytes
        pictureError = onError
        pickPicture.launch("image/*")
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

    override fun pickPicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit) {
        activity.choosePicture(onBytes, onError)
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
}

private class AndroidSpeech(private val activity: MainActivity) : Speech {
    private var recognizer: SpeechRecognizer? = null
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var mime = "audio/mp4"
    private var startedAt = 0L
    private var transcript = ""
    private var onDone: ((CatchResult) -> Unit)? = null
    private var delivered = false
    private val handler = Handler(Looper.getMainLooper())

    override fun listen(onPartial: (String) -> Unit, onError: (String) -> Unit) {
        activity.ensureMic(
            onGranted = { begin(onPartial, onError) },
            onDenied = { onError("Microphone permission was refused.") },
        )
    }

    private fun begin(onPartial: (String) -> Unit, onError: (String) -> Unit) {
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
        delivered = false
        onDone = null
        startRecognizer(onPartial)
    }

    override fun stop(onDone: (CatchResult) -> Unit) {
        this.onDone = onDone
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
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        val bytes = file?.takeIf { it.exists() && it.length() > 0L }?.readBytes()
        file?.delete()
        file = null
        val duration = if (startedAt == 0L) 0L else (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        onDone?.invoke(CatchResult(transcript.trim(), bytes, if (bytes == null) "" else mime, duration))
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
}
