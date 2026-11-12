package computer.kolektiv.hold

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import computer.kolektiv.hold.parse.MdBlock
import computer.kolektiv.hold.parse.body
import computer.kolektiv.hold.parse.parseMarkdown
import computer.kolektiv.hold.parse.renderMarkdown
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private class Piece(val id: Int, val block: MdBlock, val value: TextFieldValue)

@Composable
fun MarkdownBody(source: String, style: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseMarkdown(source).forEach { block -> BlockView(block, style) }
    }
}

@Composable
fun MarkdownEditor(initial: String, onChange: (String) -> Unit, style: TextStyle, modifier: Modifier = Modifier) {
    val parsed = remember(initial) {
        parseMarkdown(initial).ifEmpty { listOf(MdBlock.Paragraph("")) }
    }
    var pieces by remember(initial) {
        mutableStateOf(parsed.mapIndexed { index, block -> Piece(index, block, TextFieldValue(block.body(), androidx.compose.ui.text.TextRange(block.body().length))) })
    }
    var focus by remember(initial) { mutableIntStateOf(0) }
    var dragId by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableStateOf(0f) }
    val holder = remember(initial) { PieceHolder(pieces) }
    holder.items = pieces
    fun publish(next: List<Piece>) {
        holder.items = next
        pieces = next
        onChange(renderMarkdown(next.map { it.block }))
    }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MarkdownBar { action ->
            val index = focus.coerceIn(0, pieces.lastIndex.coerceAtLeast(0))
            if (pieces.isEmpty()) return@MarkdownBar
            val piece = pieces[index]
            val edited = retarget(piece.block, piece.value, action)
            publish(pieces.toMutableList().also { it[index] = Piece(piece.id, edited.first, edited.second) })
        }
        pieces.forEachIndexed { index, piece ->
            val current = holder
            key(piece.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragId == piece.id) 1f else 0f)
                        .offset { IntOffset(0, if (dragId == piece.id) dragY.roundToInt() else 0) }
                        .padding(start = ((piece.block as? MdBlock.Bullet)?.depth ?: (piece.block as? MdBlock.Numbered)?.depth ?: (piece.block as? MdBlock.Quote)?.depth ?: 0).times(16).dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "⋮⋮",
                        color = HoldThemeColors.muted,
                        modifier = Modifier.pointerInput(piece.id) {
                            detectDragGestures(
                                onDragStart = {
                                    dragId = piece.id
                                    dragY = 0f
                                },
                                onDragCancel = {
                                    dragId = -1
                                    dragY = 0f
                                },
                                onDragEnd = {
                                    dragId = -1
                                    dragY = 0f
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val list = current.items
                                    val from = list.indexOfFirst { it.id == piece.id }
                                    if (from < 0) return@detectDragGestures
                                    dragY += drag.y
                                    val step = 48.dp.toPx()
                                    if (dragY > step && from < list.lastIndex) {
                                        val next = list.toMutableList()
                                        val item = next.removeAt(from)
                                        next.add(from + 1, item)
                                        dragY = 0f
                                        focus = from + 1
                                        publish(next)
                                    } else if (dragY < -step && from > 0) {
                                        val next = list.toMutableList()
                                        val item = next.removeAt(from)
                                        next.add(from - 1, item)
                                        dragY = 0f
                                        focus = from - 1
                                        publish(next)
                                    }
                                },
                            )
                        }.padding(vertical = 8.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        when (val block = piece.block) {
                            is MdBlock.Table -> TableEdit(block, style) { rows ->
                                publish(pieces.toMutableList().also { it[index] = Piece(piece.id, MdBlock.Table(rows), piece.value) })
                            }
                            is MdBlock.Rule -> HorizontalDivider(color = HoldThemeColors.fg.copy(alpha = 0.35f), modifier = Modifier.padding(vertical = 10.dp))
                            else -> BasicTextField(
                                value = piece.value,
                                onValueChange = { value ->
                                    focus = index
                                    val written = withBody(piece.block, value.text)
                                    publish(pieces.toMutableList().also { it[index] = Piece(piece.id, written, value) })
                                },
                                textStyle = styleFor(piece.block, style),
                                cursorBrush = SolidColor(HoldThemeColors.accent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class PieceHolder(var items: List<Piece>)

@Composable
private fun BlockView(block: MdBlock, style: TextStyle) {
    val nest = ((block as? MdBlock.Bullet)?.depth ?: (block as? MdBlock.Numbered)?.depth ?: (block as? MdBlock.Quote)?.depth ?: 0) * 16
    when (block) {
        is MdBlock.Heading -> Text(
            inline(block.text),
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }.merge(TextStyle(color = HoldThemeColors.fg)),
        )
        is MdBlock.Paragraph -> Text(inline(block.text), style = style)
        is MdBlock.Bullet -> {
            val mark = when (block.checked) {
                null -> "•"
                true -> "☑"
                false -> "☐"
            }
            Row(Modifier.padding(start = nest.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mark, style = style)
                Text(inline(block.text), style = style)
            }
        }
        is MdBlock.Numbered -> Row(Modifier.padding(start = nest.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${block.index}.", style = style)
            Text(inline(block.text), style = style)
        }
        is MdBlock.Quote -> Text(inline(block.text), style = style, color = HoldThemeColors.muted, modifier = Modifier.padding(start = (12 + nest).dp))
        is MdBlock.Rule -> HorizontalDivider(color = HoldThemeColors.fg.copy(alpha = 0.2f))
        is MdBlock.Table -> Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                block.rows.forEachIndexed { index, row ->
                    Row {
                        row.forEach { cell ->
                            Text(
                                inline(cell),
                                style = if (index == 0) style.merge(TextStyle(fontWeight = FontWeight.Bold)) else style,
                                modifier = Modifier.width(128.dp).border(1.dp, HoldThemeColors.fg.copy(alpha = 0.2f)).padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableEdit(block: MdBlock.Table, style: TextStyle, onRows: (List<List<String>>) -> Unit) {
    Column {
        block.rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                row.forEachIndexed { column, cell ->
                    BasicTextField(
                        value = cell,
                        onValueChange = { text ->
                            val rows = block.rows.mapIndexed { index, line ->
                                if (index != rowIndex) line else line.mapIndexed { at, value -> if (at == column) text else value }
                            }
                            onRows(rows)
                        },
                        textStyle = style,
                        cursorBrush = SolidColor(HoldThemeColors.accent),
                        modifier = Modifier.width(120.dp).border(1.dp, HoldThemeColors.fg.copy(alpha = 0.2f)).padding(6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownBar(onApply: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        listOf(
            "bold" to "B",
            "italic" to "I",
            "underline" to "U",
            "strike" to "S",
            "heading" to "H",
            "bullet" to "•",
            "number" to "1.",
            "check" to "☐",
            "quote" to ">",
            "nest" to "Nest",
            "lift" to "Lift",
            "table" to "Table",
            "link" to "Link",
        ).forEach { (action, label) ->
            TextButton(onClick = { onApply(action) }) { Text(label) }
        }
    }
}

@Composable
private fun styleFor(block: MdBlock, body: TextStyle): TextStyle = when (block) {
    is MdBlock.Heading -> when (block.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }.merge(TextStyle(color = HoldThemeColors.fg))
    is MdBlock.Quote -> body.merge(TextStyle(color = HoldThemeColors.muted))
    else -> body
}

private fun retarget(block: MdBlock, value: TextFieldValue, action: String): Pair<MdBlock, TextFieldValue> {
    val depth = (block as? MdBlock.Bullet)?.depth ?: (block as? MdBlock.Numbered)?.depth ?: (block as? MdBlock.Quote)?.depth ?: 0
    return when (action) {
        "heading" -> {
            val level = if (block is MdBlock.Heading) (block.level % 3) + 1 else 2
            MdBlock.Heading(level, value.text) to value
        }
        "bullet" -> MdBlock.Bullet(value.text, null, depth) to value
        "number" -> MdBlock.Numbered("1", value.text, depth) to value
        "check" -> MdBlock.Bullet(value.text, false, depth) to value
        "quote" -> MdBlock.Quote(value.text, depth) to value
        "nest" -> shift(block, 1) to value
        "lift" -> shift(block, -1) to value
        "table" -> MdBlock.Table(listOf(listOf("Column", "Column"), listOf("", ""))) to TextFieldValue("")
        else -> {
            val next = applyMarkdown(value, action)
            withBody(block, next.text) to next
        }
    }
}

private fun shift(block: MdBlock, delta: Int): MdBlock = when (block) {
    is MdBlock.Bullet -> block.copy(depth = (block.depth + delta).coerceAtLeast(0))
    is MdBlock.Numbered -> block.copy(depth = (block.depth + delta).coerceAtLeast(0))
    is MdBlock.Quote -> block.copy(depth = (block.depth + delta).coerceAtLeast(0))
    else -> block
}

private fun withBody(block: MdBlock, text: String): MdBlock = when (block) {
    is MdBlock.Heading -> block.copy(text = text)
    is MdBlock.Paragraph -> block.copy(text = text)
    is MdBlock.Bullet -> block.copy(text = text)
    is MdBlock.Numbered -> block.copy(text = text)
    is MdBlock.Quote -> block.copy(text = text)
    is MdBlock.Table -> block
    is MdBlock.Rule -> block
}

fun applyMarkdown(value: TextFieldValue, action: String): TextFieldValue = when (action) {
    "bold" -> wrap(value, "**", "**")
    "italic" -> wrap(value, "*", "*")
    "underline" -> wrap(value, "<u>", "</u>")
    "strike" -> wrap(value, "~~", "~~")
    "link" -> wrap(value, "[", "](https://)")
    else -> value
}

private fun wrap(value: TextFieldValue, before: String, after: String): TextFieldValue {
    val start = min(value.selection.start, value.selection.end)
    val end = max(value.selection.start, value.selection.end)
    val selected = value.text.substring(start, end)
    val next = value.text.substring(0, start) + before + selected + after + value.text.substring(end)
    val cursor = if (selected.isEmpty()) start + before.length else end + before.length + after.length
    return TextFieldValue(next, androidx.compose.ui.text.TextRange(cursor))
}

private fun inline(source: String) = buildAnnotatedString { appendInline(source) }

private fun AnnotatedStringBuilder.appendInline(source: String) {
    var index = 0
    while (index < source.length) {
        when {
            source.startsWith("***", index) -> {
                val end = source.indexOf("***", index + 3)
                if (end < 0) {
                    append(source[index]); index++
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        appendInline(source.substring(index + 3, end))
                    }
                    index = end + 3
                }
            }
            source.startsWith("**", index) -> index = nest(source, index, "**", "**", SpanStyle(fontWeight = FontWeight.Bold))
            source.startsWith("~~", index) -> index = nest(source, index, "~~", "~~", SpanStyle(textDecoration = TextDecoration.LineThrough))
            source.startsWith("<u>", index) -> index = nest(source, index, "<u>", "</u>", SpanStyle(textDecoration = TextDecoration.Underline))
            source.startsWith("`", index) -> {
                val end = source.indexOf('`', index + 1)
                if (end < 0) {
                    append(source[index]); index++
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(source.substring(index + 1, end)) }
                    index = end + 1
                }
            }
            source.startsWith("[", index) && source.indexOf("](", index + 1) > 0 -> {
                val labelEnd = source.indexOf("](", index + 1)
                val urlEnd = source.indexOf(')', labelEnd + 2)
                if (urlEnd < 0) {
                    append(source[index]); index++
                } else {
                    withStyle(SpanStyle(color = HoldThemeColors.accent, textDecoration = TextDecoration.Underline)) {
                        appendInline(source.substring(index + 1, labelEnd))
                    }
                    index = urlEnd + 1
                }
            }
            source.startsWith("*", index) && !source.startsWith("**", index) -> index = nest(source, index, "*", "*", SpanStyle(fontStyle = FontStyle.Italic)) { at ->
                source[at] == '*' && (at + 1 >= source.length || source[at + 1] != '*')
            }
            else -> {
                append(source[index])
                index++
            }
        }
    }
}

private typealias AnnotatedStringBuilder = androidx.compose.ui.text.AnnotatedString.Builder

private fun AnnotatedStringBuilder.nest(
    source: String,
    index: Int,
    open: String,
    close: String,
    style: SpanStyle,
    isClose: (Int) -> Boolean = { source.startsWith(close, it) },
): Int {
    val end = findClose(source, index + open.length, isClose)
    if (end < 0) {
        append(source[index])
        return index + 1
    }
    withStyle(style) { appendInline(source.substring(index + open.length, end)) }
    return end + close.length
}

private fun findClose(source: String, start: Int, isClose: (Int) -> Boolean): Int {
    var index = start
    while (index < source.length) {
        if (isClose(index)) return index
        index++
    }
    return -1
}
