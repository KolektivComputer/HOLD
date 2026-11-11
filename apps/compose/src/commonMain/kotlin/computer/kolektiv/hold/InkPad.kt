package computer.kolektiv.hold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Checkbox
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.key
import computer.kolektiv.hold.model.Palette
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import computer.kolektiv.hold.model.InkPoint
import computer.kolektiv.hold.model.InkStroke
import computer.kolektiv.hold.model.NamedItem
import kotlin.math.hypot

val INK_TOOLS = listOf("pen", "fountain", "pencil", "brush", "marker", "highlighter", "eraser")
val INK_COLORS = listOf("#12140f", "#e0a106", "#9c3b2e", "#2d5d8a", "#ebe6d6")

fun inkWidth(tool: String): Float = when (tool) {
    "fountain" -> 11f
    "pencil" -> 6f
    "brush" -> 26f
    "marker" -> 30f
    "highlighter" -> 46f
    "eraser" -> 34f
    else -> 9f
}

fun inkColor(hex: String): Color {
    val rgb = hex.removePrefix("#").toLongOrNull(16) ?: 0x12140F
    return Color(0xFF000000 or rgb)
}

private fun toolIcon(tool: String): ImageVector = when (tool) {
    "fountain" -> HoldIcons.PenLine
    "pencil" -> HoldIcons.Pencil
    "brush" -> HoldIcons.Brush
    "marker" -> HoldIcons.Pen
    "highlighter" -> HoldIcons.Highlighter
    "eraser" -> HoldIcons.Eraser
    else -> HoldIcons.Pen
}

@Composable
fun InkEditor(
    strokes: List<InkStroke>,
    tool: String,
    color: String,
    background: ImageBitmap?,
    tagIds: List<String>,
    tags: List<NamedItem>,
    onStrokes: (List<InkStroke>) -> Unit,
    onTool: (String) -> Unit,
    onColor: (String) -> Unit,
    onTags: (List<String>) -> Unit,
    onCreateTag: (String) -> Unit,
    title: String,
    onTitle: (String) -> Unit,
    colors: List<String>,
    palettes: List<Palette>,
    onSavePalette: (String, List<String>) -> Unit,
    onUsePalette: (List<String>) -> Unit,
    locate: Boolean,
    onLocate: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onOpenImage: () -> Unit,
    onRead: (() -> Unit)? = null,
    taken: Long? = null,
    edited: Long? = null,
    modifier: Modifier = Modifier,
) {
    var past by remember { mutableStateOf(listOf<List<InkStroke>>()) }
    var future by remember { mutableStateOf(listOf<List<InkStroke>>()) }
    var current by remember { mutableStateOf(strokes) }
    var toolsOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var colorsOpen by remember { mutableStateOf(false) }
    var bankOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Int?>(null) }
    fun commit(next: List<InkStroke>) {
        past = past + listOf(current)
        current = next
        future = emptyList()
        onStrokes(next)
    }
    Box(modifier.fillMaxSize().background(HoldThemeColors.bg)) {
        InkPad(current, tool, color, background, ::commit, Modifier.fillMaxSize())
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(HoldThemeColors.bg.copy(alpha = 0.42f))
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                IconButton(onClick = onSave) { Icon(HoldIcons.Save, "Save", tint = HoldThemeColors.fg) }
                IconButton(onClick = onDiscard) { Icon(HoldIcons.X, "Discard", tint = HoldThemeColors.fg) }
            }
            Row {
                IconButton(onClick = {
                    if (past.isEmpty()) return@IconButton
                    future = listOf(current) + future
                    current = past.last()
                    past = past.dropLast(1)
                    onStrokes(current)
                }) { Icon(HoldIcons.Undo2, "Undo", tint = if (past.isEmpty()) HoldThemeColors.muted else HoldThemeColors.fg) }
                IconButton(onClick = {
                    if (future.isEmpty()) return@IconButton
                    past = past + listOf(current)
                    current = future.first()
                    future = future.drop(1)
                    onStrokes(current)
                }) { Icon(HoldIcons.Redo2, "Redo", tint = if (future.isEmpty()) HoldThemeColors.muted else HoldThemeColors.fg) }
                IconButton(onClick = onOpenImage) { Icon(HoldIcons.Image, "Open image", tint = HoldThemeColors.fg) }
                if (onRead != null) TextButton(onClick = onRead) { Text("Read") }
            }
        }
        if (taken != null && edited != null) {
            Text(
                "Taken ${formatStamp(taken)}   Edited ${formatStamp(edited)}",
                color = HoldThemeColors.muted,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(HoldThemeColors.bg.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (toolsOpen) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    INK_TOOLS.forEach { item ->
                        IconButton(onClick = {
                            onTool(item)
                            toolsOpen = false
                        }) {
                            Icon(toolIcon(item), item, tint = if (item == tool) HoldThemeColors.accent else HoldThemeColors.fg)
                        }
                    }
                }
            }
            if (infoOpen) {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(title, onTitle, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    LocateRow(locate, onLocate)
                    TagEditor(tagIds, tags, onTags, onCreateTag)
                }
            }
            if (colorsOpen) {
                if (bankOpen) {
                    PaletteBank(
                        palettes = palettes,
                        colors = colors,
                        onUse = {
                            onUsePalette(it)
                            selected = 0
                            editing = null
                            it.firstOrNull()?.let(onColor)
                            bankOpen = false
                        },
                        onSave = { name, stored ->
                            onSavePalette(name, stored)
                            bankOpen = false
                        },
                        onClose = { bankOpen = false },
                    )
                } else {
                    val edit = editing
                    if (edit != null && edit in colors.indices) {
                        key(edit) {
                            ColorPicker(colors[edit]) { next ->
                                val updated = colors.toMutableList()
                                updated[edit] = next
                                onUsePalette(updated)
                                onColor(next)
                            }
                        }
                    }
                    PaletteWheel(
                        colors = colors,
                        selected = selected.coerceIn(0, (colors.size - 1).coerceAtLeast(0)),
                        onSelect = { index ->
                            selected = index
                            editing = null
                            onColor(colors[index])
                        },
                        onEdit = { editing = it },
                        onAdd = {
                            val updated = colors + "#ebe6d6"
                            onUsePalette(updated)
                            selected = updated.lastIndex
                            editing = updated.lastIndex
                            onColor(updated.last())
                        },
                        onOpenBank = { bankOpen = true },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    toolsOpen = !toolsOpen
                    if (toolsOpen) {
                        infoOpen = false
                        colorsOpen = false
                        editing = null
                    }
                }) { Icon(toolIcon(tool), "Tools", tint = HoldThemeColors.fg) }
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(inkColor(color))
                        .border(2.dp, HoldThemeColors.fg, CircleShape)
                        .clickable {
                            colorsOpen = !colorsOpen
                            if (colorsOpen) {
                                infoOpen = false
                                toolsOpen = false
                                bankOpen = false
                                editing = null
                                selected = colors.indexOfFirst { it.equals(color, true) }.coerceAtLeast(0)
                            }
                        },
                )
                IconButton(onClick = {
                    infoOpen = !infoOpen
                    if (infoOpen) {
                        toolsOpen = false
                        colorsOpen = false
                        editing = null
                    }
                }) { Icon(HoldIcons.Tags, "Info", tint = HoldThemeColors.fg) }
                Text(tool, color = HoldThemeColors.muted)
            }
        }
    }
}

@Composable
fun LocateRow(checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Text("Save where I am", color = HoldThemeColors.fg)
    }
}

@Composable
private fun PaletteWheel(
    colors: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    onOpenBank: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.forEachIndexed { index, swatch ->
                val on = index == selected
                Box(
                    Modifier
                        .size(if (on) 34.dp else 28.dp)
                        .clip(CircleShape)
                        .background(inkColor(swatch))
                        .border(if (on) 2.dp else 0.dp, HoldThemeColors.fg, CircleShape)
                        .clickable { if (on) onEdit(index) else onSelect(index) },
                )
            }
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, HoldThemeColors.muted, CircleShape)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HoldIcons.Plus, "Add color", tint = HoldThemeColors.muted, modifier = Modifier.size(14.dp))
            }
        }
        IconButton(onClick = onOpenBank, modifier = Modifier.size(32.dp)) {
            Icon(HoldIcons.Palette, "Palettes", tint = HoldThemeColors.muted.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ColorPicker(hex: String, onHex: (String) -> Unit) {
    val start = remember { hsvOf(hex) }
    var hue by remember { mutableStateOf(start.first) }
    var sat by remember { mutableStateOf(start.second) }
    var value by remember { mutableStateOf(start.third) }
    var field by remember { mutableStateOf(hex) }
    val hueNow = rememberUpdatedState(hue)
    val satNow = rememberUpdatedState(sat)
    val valueNow = rememberUpdatedState(value)
    fun emit(nextHue: Float, nextSat: Float, nextValue: Float) {
        hue = nextHue
        sat = nextSat
        value = nextValue
        val next = colorHex(Color.hsv(nextHue, nextSat, nextValue))
        field = next
        onHex(next)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun apply(offset: Offset) {
                            emit(
                                hueNow.value,
                                (offset.x / size.width).coerceIn(0f, 1f),
                                1f - (offset.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                        apply(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            apply(change.position)
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val center = Offset(sat * size.width, (1f - value) * size.height)
                drawCircle(Color.White, radius = 9.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
                drawCircle(inkColor(colorHex(Color.hsv(hue, sat, value))), radius = 6.dp.toPx(), center = center)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun at(x: Float) = emit((x / size.width).coerceIn(0f, 1f) * 360f, satNow.value, valueNow.value)
                        at(down.position.x)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            at(change.position.x)
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it, 1f, 1f) },
                    ),
                )
                val center = Offset((hue / 360f) * size.width, size.height / 2f)
                drawCircle(Color.White, radius = 8.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
                drawCircle(Color.hsv(hue, 1f, 1f), radius = 5.dp.toPx(), center = center)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                field,
                { raw ->
                    field = raw
                    val clean = raw.removePrefix("#")
                    if (clean.length == 6 && clean.all { it.digitToIntOrNull(16) != null }) {
                        val parsed = hsvOf("#$clean")
                        hue = parsed.first
                        sat = parsed.second
                        value = parsed.third
                        onHex("#$clean".lowercase())
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(inkColor(field)),
            )
        }
    }
}

@Composable
private fun PaletteBank(
    palettes: List<Palette>,
    colors: List<String>,
    onUse: (List<String>) -> Unit,
    onSave: (String, List<String>) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val live = palettes.filter { it.deletedAt == null && (query.isBlank() || it.name.contains(query, ignoreCase = true)) }
    Column(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Palette bank", color = HoldThemeColors.fg, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(HoldIcons.X, "Close", tint = HoldThemeColors.muted) }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
            if (live.isEmpty()) Text("Nothing saved under that name.", color = HoldThemeColors.muted)
            live.forEach { palette ->
                TextButton(onClick = { onUse(palette.colors) }, modifier = Modifier.fillMaxWidth()) {
                    Text(palette.name, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Name this palette") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            TextButton(onClick = {
                if (name.isBlank() || colors.isEmpty()) return@TextButton
                onSave(name, colors)
                name = ""
            }) { Text("Save") }
        }
    }
}

internal fun colorHex(color: Color): String {
    fun part(channel: Float) = (channel * 255f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#${part(color.red)}${part(color.green)}${part(color.blue)}"
}

internal fun hsvOf(hex: String): Triple<Float, Float, Float> {
    val clean = hex.removePrefix("#")
    if (clean.length < 6) return Triple(40f, 0.85f, 0.88f)
    val r = clean.substring(0, 2).toIntOrNull(16)?.div(255f) ?: return Triple(40f, 0.85f, 0.88f)
    val g = clean.substring(2, 4).toIntOrNull(16)?.div(255f) ?: return Triple(40f, 0.85f, 0.88f)
    val b = clean.substring(4, 6).toIntOrNull(16)?.div(255f) ?: return Triple(40f, 0.85f, 0.88f)
    val peak = max(r, max(g, b))
    val floor = min(r, min(g, b))
    val delta = peak - floor
    val hue = when {
        delta == 0f -> 0f
        peak == r -> 60f * (((g - b) / delta) % 6f)
        peak == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return Triple(if (hue < 0f) hue + 360f else hue, if (peak == 0f) 0f else delta / peak, peak)
}

@Composable
fun TagEditor(
    selected: List<String>,
    tags: List<NamedItem>,
    onChange: (List<String>) -> Unit,
    onCreate: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val live = tags.filter { it.deletedAt == null }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            live.forEach { tag ->
                val on = tag.id in selected
                Text(
                    tag.name,
                    color = if (on) HoldThemeColors.bg else HoldThemeColors.fg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) HoldThemeColors.accent else HoldThemeColors.fg.copy(alpha = 0.12f))
                        .clickable {
                            onChange(if (on) selected - tag.id else selected + tag.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                draft,
                { draft = it },
                label = { Text("New tag") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = {
                val name = draft.trim()
                if (name.isEmpty()) return@IconButton
                onCreate(name)
                draft = ""
            }) { Icon(HoldIcons.Tags, "Add tag", tint = HoldThemeColors.fg) }
        }
    }
}

@Composable
fun Waveform(peaks: List<Float>, modifier: Modifier = Modifier, color: Color = HoldThemeColors.accent) {
    Canvas(modifier.height(48.dp).fillMaxWidth()) {
        val bars = if (peaks.isEmpty()) List(24) { 0.08f } else peaks
        val gap = 2.dp.toPx()
        val width = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.dp.toPx())
        bars.forEachIndexed { index, level ->
            val h = (size.height * level.coerceIn(0.06f, 1f))
            val x = index * (width + gap)
            drawLine(
                color = color,
                start = Offset(x + width / 2f, (size.height - h) / 2f),
                end = Offset(x + width / 2f, (size.height + h) / 2f),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

fun packPeaks(peaks: List<Float>, bars: Int = 48): List<Float> {
    if (peaks.isEmpty()) return emptyList()
    if (peaks.size <= bars) return peaks
    val step = peaks.size.toFloat() / bars
    return List(bars) { index ->
        val start = (index * step).toInt()
        val end = ((index + 1) * step).toInt().coerceAtLeast(start + 1).coerceAtMost(peaks.size)
        peaks.subList(start, end).max()
    }
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
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val view = remember { View() }
    view.scale = scale
    view.pan = pan
    val strokesNow = rememberUpdatedState(strokes)
    val toolNow = rememberUpdatedState(tool)
    val colorNow = rememberUpdatedState(color)
    val commitNow = rememberUpdatedState(onCommit)
    Canvas(
        modifier
            .fillMaxSize()
            .background(HoldThemeColors.bg)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var zooming = false
                    val drawn = ArrayList<InkPoint>()
                    fun content(position: Offset): Offset {
                        val local = (position - view.pan) / view.scale
                        val width = size.width.coerceAtLeast(1)
                        val height = size.height.coerceAtLeast(1)
                        return Offset(
                            x = (local.x / width * 1000f).coerceIn(0f, 1000f),
                            y = (local.y / height * 1000f).coerceIn(0f, 1000f),
                        )
                    }
                    fun add(position: Offset, pressure: Float) {
                        val point = content(position)
                        drawn += InkPoint(point.x, point.y, pressure.coerceIn(0.15f, 1f))
                    }
                    add(down.position, down.pressure)
                    live = drawn.toList()
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            zooming = true
                            drawn.clear()
                            live = emptyList()
                            val a = pressed[0]
                            val b = pressed[1]
                            val previousSpan = hypot(
                                (a.previousPosition.x - b.previousPosition.x).toDouble(),
                                (a.previousPosition.y - b.previousPosition.y).toDouble(),
                            ).toFloat().coerceAtLeast(1f)
                            val span = hypot((a.position.x - b.position.x).toDouble(), (a.position.y - b.position.y).toDouble()).toFloat().coerceAtLeast(1f)
                            val centroid = Offset((a.position.x + b.position.x) / 2f, (a.position.y + b.position.y) / 2f)
                            val previousCentroid = Offset((a.previousPosition.x + b.previousPosition.x) / 2f, (a.previousPosition.y + b.previousPosition.y) / 2f)
                            val nextScale = (view.scale * span / previousSpan).coerceIn(1f, 5f)
                            val nextPan = centroid - (centroid - view.pan) * (nextScale / view.scale) + (centroid - previousCentroid)
                            view.scale = nextScale
                            view.pan = nextPan
                            scale = nextScale
                            pan = nextPan
                            pressed.forEach { it.consume() }
                            continue
                        }
                        if (zooming) {
                            pressed.forEach { it.consume() }
                            continue
                        }
                        val change = pressed.first()
                        if (change.positionChange() != Offset.Zero) change.consume()
                        add(change.position, change.pressure)
                        live = drawn.toList()
                    }
                    val finished = drawn.toList()
                    live = emptyList()
                    if (zooming || finished.isEmpty()) return@awaitEachGesture
                    val activeTool = toolNow.value
                    val page = strokesNow.value
                    if (activeTool == "eraser") {
                        commitNow.value(page.filter { stroke -> stroke.points.none { point -> finished.any { near(it, point) } } })
                    } else {
                        commitNow.value(page + InkStroke(tool = activeTool, color = colorNow.value, width = inkWidth(activeTool), points = finished))
                    }
                }
            },
    ) {
        drawContext.canvas.save()
        drawContext.canvas.translate(pan.x, pan.y)
        drawContext.canvas.scale(scale, scale)
        drawRect(HoldThemeColors.fg)
        background?.let { image ->
            drawImage(image, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()))
        }
        strokes.forEach { drawStroke(it, this.size.width, this.size.height) }
        if (live.isNotEmpty() && tool != "eraser") {
            drawStroke(InkStroke(tool, color, inkWidth(tool), live), this.size.width, this.size.height)
        }
        drawContext.canvas.restore()
    }
}

private class View {
    var scale = 1f
    var pan = Offset.Zero
}

private fun near(a: InkPoint, b: InkPoint) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) < 36.0

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: InkStroke, widthPx: Float, heightPx: Float) {
    val points = stroke.points
    if (points.isEmpty()) return
    val sx = widthPx / 1000f
    val sy = heightPx / 1000f
    val paint = inkColor(stroke.color).let { color ->
        when (stroke.tool) {
            "highlighter" -> color.copy(alpha = 0.38f)
            "pencil" -> color.copy(alpha = 0.8f)
            else -> color
        }
    }
    if (points.size == 1) {
        val point = points.first()
        drawCircle(paint, radius = stroke.width * point.p * sx * 2.4f, center = Offset(point.x * sx, point.y * sy))
        return
    }
    for (index in 1 until points.size) {
        val a = points[index - 1]
        val b = points[index]
        drawLine(
            color = paint,
            start = Offset(a.x * sx, a.y * sy),
            end = Offset(b.x * sx, b.y * sy),
            strokeWidth = stroke.width * ((a.p + b.p) / 2f) * sx * 2.4f,
            cap = StrokeCap.Round,
        )
    }
}
