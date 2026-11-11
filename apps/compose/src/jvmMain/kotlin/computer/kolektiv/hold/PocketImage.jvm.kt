package computer.kolektiv.hold

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import computer.kolektiv.hold.model.InkStroke
import org.jetbrains.skia.Image

actual fun decodePocketImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

actual fun rasterPocketPage(strokes: List<InkStroke>, background: ByteArray?): ByteArray? = null
