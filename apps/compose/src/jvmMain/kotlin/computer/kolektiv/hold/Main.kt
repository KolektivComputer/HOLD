package computer.kolektiv.hold

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import computer.kolektiv.hold.store.JsonHoldRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

fun main() = application {
    val file = File(System.getProperty("user.home"), ".hold/log.json")
    val repository = JsonHoldRepository(
        read = { file.takeIf { it.exists() }?.readText() },
        write = {
            file.parentFile?.mkdirs()
            file.writeText(it)
        },
    )
    Window(onCloseRequest = ::exitApplication, title = "HOLD") {
        HoldApp(repository, poster = ::postJson)
    }
}

fun postJson(url: String, body: String, token: String): String {
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
