package computer.kolektiv.hold

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.delay
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

@Composable
actual fun HoldBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun WatchCaptures(onText: (String, String) -> Unit, onAudio: (ByteArray) -> Unit) {
    LaunchedEffect(Unit) {
        while (true) {
            HoldBridge.drainText().forEach { (kind, text) -> onText(kind, text) }
            HoldBridge.drainAudio().forEach { path ->
                readFile(path)?.let(onAudio)
            }
            delay(400)
        }
    }
}

object HoldBridge {
    private val texts = mutableListOf<Pair<String, String>>()
    private val audios = mutableListOf<String>()

    fun text(kind: String, text: String) {
        if (text.isBlank()) return
        synchronized(texts) { texts += kind to text }
    }

    fun audio(path: String) {
        if (path.isBlank()) return
        synchronized(audios) { audios += path }
    }

    fun drainText(): List<Pair<String, String>> = synchronized(texts) { texts.toList().also { texts.clear() } }

    fun drainAudio(): List<String> = synchronized(audios) { audios.toList().also { audios.clear() } }
}

@OptIn(ExperimentalForeignApi::class)
internal fun readFile(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val size = data.length.toInt()
    if (size <= 0) return null
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}
