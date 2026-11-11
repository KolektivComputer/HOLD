package computer.kolektiv.hold

import androidx.compose.ui.graphics.ImageBitmap
import computer.kolektiv.hold.model.InkStroke

actual fun decodePocketImage(bytes: ByteArray): ImageBitmap? = null

actual fun rasterPocketPage(strokes: List<InkStroke>, background: ByteArray?): ByteArray? = null
