package computer.kolektiv.hold

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import computer.kolektiv.hold.store.JsonHoldRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 21)
        }
        val file = File(filesDir, "hold.json")
        val repository = JsonHoldRepository(
            read = { file.takeIf { it.exists() }?.readText() },
            write = { file.writeText(it) },
        )
        setContent {
            HoldApp(repository, poster = ::postJson, speech = AndroidSpeech(this))
        }
    }
}

private fun postJson(url: String, body: String, token: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Accept", "application/json")
    if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
    connection.doOutput = true
    connection.connectTimeout = 15_000
    connection.readTimeout = 15_000
    connection.outputStream.use { it.write(body.encodeToByteArray()) }
    val code = connection.responseCode
    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
    val text = stream?.bufferedReader()?.readText().orEmpty()
    if (code !in 200..299) error(text.ifBlank { "The server answered $code." })
    return text
}

private class AndroidSpeech(private val activity: ComponentActivity) : Speech {
    private var recognizer: SpeechRecognizer? = null

    override fun listen(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onError("Speech recognition isn't on this device.")
            return
        }
        val rec = SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                onFinal(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            }

            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }

            override fun onError(error: Int) {
                onError("Couldn't hear that.")
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
        rec.startListening(intent)
    }

    override fun stop() {
        recognizer?.stopListening()
    }
}
