package computer.kolektiv.hold

import androidx.compose.ui.graphics.ImageBitmap
import computer.kolektiv.hold.model.InkStroke

expect fun decodePocketImage(bytes: ByteArray): ImageBitmap?

expect fun rasterPocketPage(strokes: List<InkStroke>, background: ByteArray?): ByteArray?
