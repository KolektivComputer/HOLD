package computer.kolektiv.hold

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

/** Lucide strokes this app actually draws. The full pack is not on the classpath. */
internal object HoldIcons {
    val Library: ImageVector = icon(
        "Library",
        path {
            moveTo(16f, 6f)
            lineToRelative(4f, 14f)
        },
        path {
            moveTo(12f, 6f)
            verticalLineToRelative(14f)
        },
        path {
            moveTo(8f, 8f)
            verticalLineToRelative(12f)
        },
        path {
            moveTo(4f, 4f)
            verticalLineToRelative(16f)
        },
    )

    val List: ImageVector = icon(
        "List",
        path {
            moveTo(3f, 5f)
            horizontalLineToRelative(0.01f)
        },
        path {
            moveTo(3f, 12f)
            horizontalLineToRelative(0.01f)
        },
        path {
            moveTo(3f, 19f)
            horizontalLineToRelative(0.01f)
        },
        path {
            moveTo(8f, 5f)
            horizontalLineToRelative(13f)
        },
        path {
            moveTo(8f, 12f)
            horizontalLineToRelative(13f)
        },
        path {
            moveTo(8f, 19f)
            horizontalLineToRelative(13f)
        },
    )

    val Brush: ImageVector = icon(
        "Brush",
        path {
            moveTo(11f, 10f)
            lineToRelative(3f, 3f)
        },
        path {
            moveTo(6.5f, 21f)
            arcTo(3.5f, 3.5f, 0f, true, false, 3f, 17.5f)
            arcToRelative(2.62f, 2.62f, 0f, false, true, -0.708f, 1.792f)
            arcTo(1f, 1f, 0f, false, false, 3f, 21f)
            close()
        },
        path {
            moveTo(9.969f, 17.031f)
            lineTo(21.378f, 5.624f)
            arcToRelative(1f, 1f, 0f, false, false, -3.002f, -3.002f)
            lineTo(6.967f, 14.031f)
        },
    )

    val Eraser: ImageVector = icon(
        "Eraser",
        path {
            moveTo(21f, 21f)
            horizontalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, -1.42f, -0.587f)
            lineToRelative(-3.994f, -3.999f)
            arcToRelative(2f, 2f, 0f, false, true, 0f, -2.828f)
            lineToRelative(10f, -10f)
            arcToRelative(2f, 2f, 0f, false, true, 2.829f, 0f)
            lineToRelative(5.999f, 6f)
            arcToRelative(2f, 2f, 0f, false, true, 0f, 2.828f)
            lineTo(12.834f, 21f)
        },
        path {
            moveTo(5.082f, 11.09f)
            lineToRelative(8.828f, 8.828f)
        },
    )

    val Highlighter: ImageVector = icon(
        "Highlighter",
        path {
            moveTo(9f, 11f)
            lineToRelative(-6f, 6f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(9f)
            lineToRelative(3f, -3f)
        },
        path {
            moveTo(22f, 12f)
            lineToRelative(-4.6f, 4.6f)
            arcToRelative(2f, 2f, 0f, false, true, -2.8f, 0f)
            lineToRelative(-5.2f, -5.2f)
            arcToRelative(2f, 2f, 0f, false, true, 0f, -2.8f)
            lineTo(14f, 4f)
        },
    )

    val Image: ImageVector = icon(
        "Image",
        path {
            moveTo(5f, 3f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 21f, 5f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 19f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 5f, 3f)
            close()
        },
        path {
            moveTo(11f, 9f)
            arcTo(2f, 2f, 0f, false, true, 9f, 11f)
            arcTo(2f, 2f, 0f, false, true, 7f, 9f)
            arcTo(2f, 2f, 0f, false, true, 11f, 9f)
            close()
        },
        path {
            moveTo(21f, 15f)
            lineToRelative(-3.086f, -3.086f)
            arcToRelative(2f, 2f, 0f, false, false, -2.828f, 0f)
            lineTo(6f, 21f)
        },
    )

    val Pen: ImageVector = icon(
        "Pen",
        path {
            moveTo(21.174f, 6.812f)
            arcToRelative(1f, 1f, 0f, false, false, -3.986f, -3.987f)
            lineTo(3.842f, 16.174f)
            arcToRelative(2f, 2f, 0f, false, false, -0.5f, 0.83f)
            lineToRelative(-1.321f, 4.352f)
            arcToRelative(0.5f, 0.5f, 0f, false, false, 0.623f, 0.622f)
            lineToRelative(4.353f, -1.32f)
            arcToRelative(2f, 2f, 0f, false, false, 0.83f, -0.497f)
            close()
        },
    )

    val PenLine: ImageVector = icon(
        "PenLine",
        path {
            moveTo(13f, 21f)
            horizontalLineToRelative(8f)
        },
        path {
            moveTo(21.174f, 6.812f)
            arcToRelative(1f, 1f, 0f, false, false, -3.986f, -3.987f)
            lineTo(3.842f, 16.174f)
            arcToRelative(2f, 2f, 0f, false, false, -0.5f, 0.83f)
            lineToRelative(-1.321f, 4.352f)
            arcToRelative(0.5f, 0.5f, 0f, false, false, 0.623f, 0.622f)
            lineToRelative(4.353f, -1.32f)
            arcToRelative(2f, 2f, 0f, false, false, 0.83f, -0.497f)
            close()
        },
    )

    val Pencil: ImageVector = icon(
        "Pencil",
        path {
            moveTo(21.174f, 6.812f)
            arcToRelative(1f, 1f, 0f, false, false, -3.986f, -3.987f)
            lineTo(3.842f, 16.174f)
            arcToRelative(2f, 2f, 0f, false, false, -0.5f, 0.83f)
            lineToRelative(-1.321f, 4.352f)
            arcToRelative(0.5f, 0.5f, 0f, false, false, 0.623f, 0.622f)
            lineToRelative(4.353f, -1.32f)
            arcToRelative(2f, 2f, 0f, false, false, 0.83f, -0.497f)
            close()
        },
        path {
            moveTo(15f, 5f)
            lineToRelative(4f, 4f)
        },
    )

    val Redo2: ImageVector = icon(
        "Redo2",
        path {
            moveTo(15f, 14f)
            lineToRelative(5f, -5f)
            lineToRelative(-5f, -5f)
        },
        path {
            moveTo(20f, 9f)
            horizontalLineTo(9.5f)
            arcTo(5.5f, 5.5f, 0f, false, false, 4f, 14.5f)
            arcTo(5.5f, 5.5f, 0f, false, false, 9.5f, 20f)
            horizontalLineTo(13f)
        },
    )

    val Save: ImageVector = icon(
        "Save",
        path {
            moveTo(15.2f, 3f)
            arcToRelative(2f, 2f, 0f, false, true, 1.4f, 0.6f)
            lineToRelative(3.8f, 3.8f)
            arcToRelative(2f, 2f, 0f, false, true, 0.6f, 1.4f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            close()
        },
        path {
            moveTo(17f, 21f)
            verticalLineToRelative(-7f)
            arcToRelative(1f, 1f, 0f, false, false, -1f, -1f)
            horizontalLineTo(8f)
            arcToRelative(1f, 1f, 0f, false, false, -1f, 1f)
            verticalLineToRelative(7f)
        },
        path {
            moveTo(7f, 3f)
            verticalLineToRelative(4f)
            arcToRelative(1f, 1f, 0f, false, false, 1f, 1f)
            horizontalLineToRelative(7f)
        },
    )

    val Tags: ImageVector = icon(
        "Tags",
        path {
            moveTo(13.172f, 2f)
            arcToRelative(2f, 2f, 0f, false, true, 1.414f, 0.586f)
            lineToRelative(6.71f, 6.71f)
            arcToRelative(2.4f, 2.4f, 0f, false, true, 0f, 3.408f)
            lineToRelative(-4.592f, 4.592f)
            arcToRelative(2.4f, 2.4f, 0f, false, true, -3.408f, 0f)
            lineToRelative(-6.71f, -6.71f)
            arcTo(2f, 2f, 0f, false, true, 6f, 9.172f)
            verticalLineTo(3f)
            arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
            close()
        },
        path {
            moveTo(2f, 7f)
            verticalLineToRelative(6.172f)
            arcToRelative(2f, 2f, 0f, false, false, 0.586f, 1.414f)
            lineToRelative(6.71f, 6.71f)
            arcToRelative(2.4f, 2.4f, 0f, false, false, 3.191f, 0.193f)
        },
        path {
            moveTo(11f, 6.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 10.5f, 7f)
            arcTo(0.5f, 0.5f, 0f, false, true, 10f, 6.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 11f, 6.5f)
            close()
        },
    )

    val Undo2: ImageVector = icon(
        "Undo2",
        path {
            moveTo(9f, 14f)
            lineTo(4f, 9f)
            lineToRelative(5f, -5f)
        },
        path {
            moveTo(4f, 9f)
            horizontalLineToRelative(10.5f)
            arcToRelative(5.5f, 5.5f, 0f, false, true, 5.5f, 5.5f)
            arcToRelative(5.5f, 5.5f, 0f, false, true, -5.5f, 5.5f)
            horizontalLineTo(11f)
        },
    )

    val X: ImageVector = icon(
        "X",
        path {
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        },
        path {
            moveTo(6f, 6f)
            lineToRelative(12f, 12f)
        },
    )

    val Camera: ImageVector = icon(
        "Camera",
        path {
            moveTo(13.997f, 4f)
            arcToRelative(2f, 2f, 0f, false, true, 1.76f, 1.05f)
            lineToRelative(0.486f, 0.9f)
            arcTo(2f, 2f, 0f, false, false, 18.003f, 7f)
            horizontalLineTo(20f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineToRelative(9f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(4f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(9f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineToRelative(1.997f)
            arcToRelative(2f, 2f, 0f, false, false, 1.759f, -1.048f)
            lineToRelative(0.489f, -0.904f)
            arcTo(2f, 2f, 0f, false, true, 10.004f, 4f)
            close()
        },
        path {
            moveTo(15f, 13f)
            arcTo(3f, 3f, 0f, false, true, 12f, 16f)
            arcTo(3f, 3f, 0f, false, true, 9f, 13f)
            arcTo(3f, 3f, 0f, false, true, 15f, 13f)
            close()
        },
    )

    val Check: ImageVector = icon(
        "Check",
        path {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineToRelative(-5f, -5f)
        },
    )

    val MicOff: ImageVector = icon(
        "MicOff",
        path {
            moveTo(12f, 19f)
            verticalLineToRelative(3f)
        },
        path {
            moveTo(15f, 9.34f)
            verticalLineTo(5f)
            arcToRelative(3f, 3f, 0f, false, false, -5.68f, -1.33f)
        },
        path {
            moveTo(16.95f, 16.95f)
            arcTo(7f, 7f, 0f, false, true, 5f, 12f)
            verticalLineToRelative(-2f)
        },
        path {
            moveTo(18.89f, 13.23f)
            arcTo(7f, 7f, 0f, false, false, 19f, 12f)
            verticalLineToRelative(-2f)
        },
        path {
            moveTo(2f, 2f)
            lineToRelative(20f, 20f)
        },
        path {
            moveTo(9f, 9f)
            verticalLineToRelative(3f)
            arcToRelative(3f, 3f, 0f, false, false, 5.12f, 2.12f)
        },
    )

    val Square: ImageVector = icon(
        "Square",
        path {
            moveTo(5f, 3f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 21f, 5f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 19f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 5f, 3f)
            close()
        },
    )

    val FileText: ImageVector = icon(
        "FileText",
        path {
            moveTo(6f, 22f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(4f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineToRelative(8f)
            arcToRelative(2.4f, 2.4f, 0f, false, true, 1.704f, 0.706f)
            lineToRelative(3.588f, 3.588f)
            arcTo(2.4f, 2.4f, 0f, false, true, 20f, 8f)
            verticalLineToRelative(12f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            close()
        },
        path {
            moveTo(14f, 2f)
            verticalLineToRelative(5f)
            arcToRelative(1f, 1f, 0f, false, false, 1f, 1f)
            horizontalLineToRelative(5f)
        },
        path {
            moveTo(10f, 9f)
            horizontalLineTo(8f)
        },
        path {
            moveTo(16f, 13f)
            horizontalLineTo(8f)
        },
        path {
            moveTo(16f, 17f)
            horizontalLineTo(8f)
        },
    )

    val MapPin: ImageVector = icon(
        "MapPin",
        path {
            moveTo(20f, 10f)
            curveToRelative(0f, 4.993f, -5.539f, 10.193f, -7.399f, 11.799f)
            arcToRelative(1f, 1f, 0f, false, true, -1.202f, 0f)
            curveTo(9.539f, 20.193f, 4f, 14.993f, 4f, 10f)
            arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
        },
        path {
            moveTo(15f, 10f)
            arcTo(3f, 3f, 0f, false, true, 12f, 13f)
            arcTo(3f, 3f, 0f, false, true, 9f, 10f)
            arcTo(3f, 3f, 0f, false, true, 15f, 10f)
            close()
        },
    )

    val Mic: ImageVector = icon(
        "Mic",
        path {
            moveTo(12f, 19f)
            verticalLineToRelative(3f)
        },
        path {
            moveTo(19f, 10f)
            verticalLineToRelative(2f)
            arcToRelative(7f, 7f, 0f, false, true, -14f, 0f)
            verticalLineToRelative(-2f)
        },
        path {
            moveTo(12f, 2f)
            horizontalLineTo(12f)
            arcTo(3f, 3f, 0f, false, true, 15f, 5f)
            verticalLineTo(12f)
            arcTo(3f, 3f, 0f, false, true, 12f, 15f)
            horizontalLineTo(12f)
            arcTo(3f, 3f, 0f, false, true, 9f, 12f)
            verticalLineTo(5f)
            arcTo(3f, 3f, 0f, false, true, 12f, 2f)
            close()
        },
    )

    val Pause: ImageVector = icon(
        "Pause",
        path {
            moveTo(15f, 3f)
            horizontalLineTo(18f)
            arcTo(1f, 1f, 0f, false, true, 19f, 4f)
            verticalLineTo(20f)
            arcTo(1f, 1f, 0f, false, true, 18f, 21f)
            horizontalLineTo(15f)
            arcTo(1f, 1f, 0f, false, true, 14f, 20f)
            verticalLineTo(4f)
            arcTo(1f, 1f, 0f, false, true, 15f, 3f)
            close()
        },
        path {
            moveTo(6f, 3f)
            horizontalLineTo(9f)
            arcTo(1f, 1f, 0f, false, true, 10f, 4f)
            verticalLineTo(20f)
            arcTo(1f, 1f, 0f, false, true, 9f, 21f)
            horizontalLineTo(6f)
            arcTo(1f, 1f, 0f, false, true, 5f, 20f)
            verticalLineTo(4f)
            arcTo(1f, 1f, 0f, false, true, 6f, 3f)
            close()
        },
    )

    val Play: ImageVector = icon(
        "Play",
        path {
            moveTo(5f, 5f)
            arcToRelative(2f, 2f, 0f, false, true, 3.008f, -1.728f)
            lineToRelative(11.997f, 6.998f)
            arcToRelative(2f, 2f, 0f, false, true, 0.003f, 3.458f)
            lineToRelative(-12f, 7f)
            arcTo(2f, 2f, 0f, false, true, 5f, 19f)
            close()
        },
    )

    val Plus: ImageVector = icon(
        "Plus",
        path {
            moveTo(5f, 12f)
            horizontalLineToRelative(14f)
        },
        path {
            moveTo(12f, 5f)
            verticalLineToRelative(14f)
        },
    )

    val Radio: ImageVector = icon(
        "Radio",
        path {
            moveTo(16.247f, 7.761f)
            arcToRelative(6f, 6f, 0f, false, true, 0f, 8.478f)
        },
        path {
            moveTo(19.075f, 4.933f)
            arcToRelative(10f, 10f, 0f, false, true, 0f, 14.134f)
        },
        path {
            moveTo(4.925f, 19.067f)
            arcToRelative(10f, 10f, 0f, false, true, 0f, -14.134f)
        },
        path {
            moveTo(7.753f, 16.239f)
            arcToRelative(6f, 6f, 0f, false, true, 0f, -8.478f)
        },
        path {
            moveTo(14f, 12f)
            arcTo(2f, 2f, 0f, false, true, 12f, 14f)
            arcTo(2f, 2f, 0f, false, true, 10f, 12f)
            arcTo(2f, 2f, 0f, false, true, 14f, 12f)
            close()
        },
    )

    val Palette: ImageVector = icon(
        "Palette",
        path {
            moveTo(12f, 22f)
            arcToRelative(1f, 1f, 0f, false, true, 0f, -20f)
            arcToRelative(10f, 9f, 0f, false, true, 10f, 9f)
            arcToRelative(5f, 5f, 0f, false, true, -5f, 5f)
            horizontalLineToRelative(-2.25f)
            arcToRelative(1.75f, 1.75f, 0f, false, false, -1.4f, 2.8f)
            lineToRelative(0.3f, 0.4f)
            arcToRelative(1.75f, 1.75f, 0f, false, true, -1.4f, 2.8f)
            close()
        },
        path {
            moveTo(14f, 6.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 13.5f, 7f)
            arcTo(0.5f, 0.5f, 0f, false, true, 13f, 6.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 14f, 6.5f)
            close()
        },
        path {
            moveTo(18f, 10.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 17.5f, 11f)
            arcTo(0.5f, 0.5f, 0f, false, true, 17f, 10.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 18f, 10.5f)
            close()
        },
        path {
            moveTo(7f, 12.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 6.5f, 13f)
            arcTo(0.5f, 0.5f, 0f, false, true, 6f, 12.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 7f, 12.5f)
            close()
        },
        path {
            moveTo(9f, 7.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 8.5f, 8f)
            arcTo(0.5f, 0.5f, 0f, false, true, 8f, 7.5f)
            arcTo(0.5f, 0.5f, 0f, false, true, 9f, 7.5f)
            close()
        },
    )

    val Folder: ImageVector = icon(
        "Folder",
        path {
            moveTo(20f, 20f)
            lineTo(4f, 20f)
            lineTo(4f, 8f)
            lineTo(9f, 8f)
            lineTo(11f, 6f)
            lineTo(20f, 6f)
            close()
        },
    )

    val More: ImageVector = icon(
        "More",
        path {
            moveTo(12f, 5f)
            horizontalLineTo(12.01f)
        },
        path {
            moveTo(12f, 12f)
            horizontalLineTo(12.01f)
        },
        path {
            moveTo(12f, 19f)
            horizontalLineTo(12.01f)
        },
    )
}

private fun path(block: PathBuilder.() -> Unit): List<PathNode> = PathBuilder().apply(block).nodes

private fun icon(name: String, vararg strokes: List<PathNode>): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    val ink = SolidColor(Color.Black)
    strokes.forEach { nodes ->
        builder.addPath(
            pathData = nodes,
            stroke = ink,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 4f,
        )
    }
    return builder.build()
}
