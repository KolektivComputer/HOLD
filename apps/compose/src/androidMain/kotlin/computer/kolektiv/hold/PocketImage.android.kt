package computer.kolektiv.hold

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import computer.kolektiv.hold.model.InkStroke
import java.io.ByteArrayOutputStream

actual fun decodePocketImage(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

actual fun rasterPocketPage(strokes: List<InkStroke>, background: ByteArray?): ByteArray? {
    val width = 800
    val height = 1100
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.parseColor("#EBE6D6"))
    background?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { image ->
            val scale = minOf(width.toFloat() / image.width, height.toFloat() / image.height)
            val dw = image.width * scale
            val dh = image.height * scale
            val left = (width - dw) / 2f
            val top = (height - dh) / 2f
            canvas.drawBitmap(image, null, android.graphics.RectF(left, top, left + dw, top + dh), null)
        }
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    for (stroke in strokes) {
        paint.color = AndroidColor.parseColor(stroke.color)
        paint.alpha = when (stroke.tool) {
            "highlighter" -> 90
            "pencil" -> 180
            else -> 255
        }
        val points = stroke.points
        if (points.size == 1) {
            val point = points.first()
            canvas.drawCircle(point.x / 1000f * width, point.y / 1000f * height, stroke.width * point.p, paint)
        }
        for (index in 1 until points.size) {
            val a = points[index - 1]
            val b = points[index]
            paint.strokeWidth = stroke.width * ((a.p + b.p) / 2f)
            canvas.drawLine(a.x / 1000f * width, a.y / 1000f * height, b.x / 1000f * width, b.y / 1000f * height, paint)
        }
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return out.toByteArray()
}
