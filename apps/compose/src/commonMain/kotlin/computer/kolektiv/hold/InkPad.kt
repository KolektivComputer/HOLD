package computer.kolektiv.hold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import computer.kolektiv.hold.model.InkPoint
import computer.kolektiv.hold.model.InkStroke
import kotlin.math.hypot

val INK_TOOLS = listOf("pen", "fountain", "pencil", "brush", "marker", "highlighter", "eraser")
val INK_COLORS = listOf("#12140f", "#e0a106", "#9c3b2e", "#2d5d8a", "#ebe6d6")

fun inkWidth(tool: String): Float = when (tool) {
    "fountain" -> 3.2f
    "pencil" -> 1.5f
    "brush" -> 14f
    "marker" -> 18f
    "highlighter" -> 32f
    else -> 2.6f
}

fun inkColor(hex: String): Color {
    val rgb = hex.removePrefix("#").toLongOrNull(16) ?: 0x12140F
    return Color(0xFF000000 or rgb)
}

@Composable
fun InkPad(
    strokes: List<InkStroke>,
    tool: String,
    color: String,
    background: ImageBitmap?,
    onCommit: (List<InkStroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var live by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Canvas(
        modifier
            .fillMaxWidth()
            .background(HoldThemeColors.fg)
            .onSizeChanged { size = it }
            .pointerInput(tool, color, strokes) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val drawn = ArrayList<InkPoint>()
                    fun add(x: Float, y: Float, pressure: Float) {
                        if (size.width == 0 || size.height == 0) return
                        drawn += InkPoint(
                            x = (x / size.width * 1000f).coerceIn(0f, 1000f),
                            y = (y / size.height * 1000f).coerceIn(0f, 1000f),
                            p = pressure.coerceIn(0.15f, 1f),
                        )
                    }
                    add(down.position.x, down.position.y, down.pressure)
                    live = drawn.toList()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if (!change.pressed) break
                        if (change.positionChange() != Offset.Zero) change.consume()
                        add(change.position.x, change.position.y, change.pressure)
                        live = drawn.toList()
                    }
                    val finished = drawn.toList()
                    live = emptyList()
                    if (finished.isEmpty()) return@awaitEachGesture
                    if (tool == "eraser") {
                        onCommit(strokes.filter { stroke -> stroke.points.none { point -> finished.any { near(it, point) } } })
                    } else {
                        onCommit(strokes + InkStroke(tool = tool, color = color, width = inkWidth(tool), points = finished))
                    }
                }
            },
    ) {
        background?.let { image ->
            drawImage(image, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()))
        }
        strokes.forEach { drawStroke(it, this.size.width, this.size.height) }
        if (live.isNotEmpty() && tool != "eraser") {
            drawStroke(InkStroke(tool, color, inkWidth(tool), live), this.size.width, this.size.height)
        }
    }
}

private fun near(a: InkPoint, b: InkPoint) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) < 36.0

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: InkStroke, widthPx: Float, heightPx: Float) {
    val points = stroke.points
    if (points.isEmpty()) return
    val sx = widthPx / 1000f
    val sy = heightPx / 1000f
    val paint = inkColor(stroke.color).let { color ->
        when (stroke.tool) {
            "highlighter" -> color.copy(alpha = 0.35f)
            "pencil" -> color.copy(alpha = 0.7f)
            else -> color
        }
    }
    if (points.size == 1) {
        val point = points.first()
        drawCircle(paint, radius = stroke.width * point.p * sx, center = Offset(point.x * sx, point.y * sy))
        return
    }
    for (index in 1 until points.size) {
        val a = points[index - 1]
        val b = points[index]
        drawLine(
            color = paint,
            start = Offset(a.x * sx, a.y * sy),
            end = Offset(b.x * sx, b.y * sy),
            strokeWidth = stroke.width * ((a.p + b.p) / 2f) * sx,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun InkTools(
    tool: String,
    color: String,
    onTool: (String) -> Unit,
    onColor: (String) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        INK_TOOLS.forEach { item ->
            TextButton(onClick = { onTool(item) }) {
                Text(if (item == tool) item.uppercase() else item)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        INK_COLORS.forEach { swatch ->
            Box(
                Modifier
                    .size(22.dp)
                    .background(inkColor(swatch), CircleShape)
                    .border(if (swatch == color) 2.dp else 0.dp, HoldThemeColors.accent, CircleShape),
            ) {
                TextButton(onClick = { onColor(swatch) }, modifier = Modifier.fillMaxSize()) { Text("") }
            }
        }
        TextButton(onClick = onUndo) { Text("Undo") }
        TextButton(onClick = onClear) { Text("Clear ink") }
    }
}
