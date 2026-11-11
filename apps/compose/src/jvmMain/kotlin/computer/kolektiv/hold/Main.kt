package computer.kolektiv.hold

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import computer.kolektiv.hold.geo.nominatimAddress
import computer.kolektiv.hold.store.JsonHoldRepository
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

fun main() = application {
    val root = File(System.getProperty("user.home"), ".hold")
    val file = File(root, "log.json")
    val repository = JsonHoldRepository(
        read = { file.takeIf { it.exists() }?.readText() },
        write = {
            file.parentFile?.mkdirs()
            file.writeText(it)
        },
    )
    Window(onCloseRequest = ::exitApplication, title = "HOLD") {
        HoldApp(repository, poster = ::postJson, ports = DesktopPorts(root))
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

private class DesktopPorts(private val root: File) : PocketPorts {
    private val blobs = File(root, "blobs")
    private val tiles = File(root, "tiles")

    override fun pickPicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit) {
        Thread {
            val dialog = FileDialog(Frame(), "Picture", FileDialog.LOAD)
            dialog.isVisible = true
            val name = dialog.file
            val picked = if (name == null) null else File(dialog.directory, name)
            if (picked == null || !picked.exists()) onError("No picture.") else onBytes(picked.readBytes())
        }.start()
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

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "blob" }
}
