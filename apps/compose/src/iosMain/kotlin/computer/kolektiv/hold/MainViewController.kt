package computer.kolektiv.hold

import androidx.compose.ui.window.ComposeUIViewController
import computer.kolektiv.hold.store.JsonHoldRepository
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Foundation.NSData
import platform.Foundation.create

fun MainViewController() = ComposeUIViewController {
    val directory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    val path = "$directory/hold.json"
    val blobs = "$directory/blobs"
    NSFileManager.defaultManager.createDirectoryAtPath(blobs, true, null, null)
    val repository = JsonHoldRepository(
        read = {
            NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) as String?
        },
        write = { value ->
            NSString.create(string = value).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        },
    )
    HoldApp(repository, poster = { _, _, _ -> error("Sync from iOS isn't hooked up yet.") }, ports = IosPorts(blobs))
}

private class IosPorts(private val directory: String) : PocketPorts {
    override fun saveBlob(id: String, bytes: ByteArray) {
        writeBytes("$directory/${safe(id)}", bytes)
    }

    override fun loadBlob(id: String): ByteArray? = readFile("$directory/${safe(id)}")

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "blob" }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeBytes(path: String, bytes: ByteArray) {
    if (bytes.isEmpty()) return
    bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())?.writeToFile(path, atomically = true)
    }
}
