package computer.kolektiv.hold

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import computer.kolektiv.hold.parse.MdBlock
import computer.kolektiv.hold.parse.parseMarkdown
import kotlin.math.max
import kotlin.math.min

@Composable
fun MarkdownBody(source: String, style: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseMarkdown(source).forEach { block ->
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(mark, style = style)
                        Text(inline(block.text), style = style)
                    }
                }
                is MdBlock.Numbered -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${block.index}.", style = style)
                    Text(inline(block.text), style = style)
                }
                is MdBlock.Quote -> Text(inline(block.text), style = style, color = HoldThemeColors.muted, modifier = Modifier.padding(start = 12.dp))
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
            "table" to "Table",
            "link" to "Link",
        ).forEach { (action, label) ->
            TextButton(onClick = { onApply(action) }) { Text(label) }
        }
    }
}

fun applyMarkdown(value: TextFieldValue, action: String): TextFieldValue = when (action) {
    "bold" -> wrap(value, "**", "**")
    "italic" -> wrap(value, "*", "*")
    "underline" -> wrap(value, "<u>", "</u>")
    "strike" -> wrap(value, "~~", "~~")
    "link" -> wrap(value, "[", "](https://)")
    "heading" -> prefix(value, "## ")
    "bullet" -> prefix(value, "- ")
    "number" -> prefix(value, "1. ")
    "check" -> prefix(value, "- [ ] ")
    "quote" -> prefix(value, "> ")
    "table" -> insert(value, "\n| Column | Column |\n| --- | --- |\n|  |  |\n")
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

private fun prefix(value: TextFieldValue, mark: String): TextFieldValue {
    val cursor = value.selection.start
    val lineStart = value.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val next = value.text.substring(0, lineStart) + mark + value.text.substring(lineStart)
    return TextFieldValue(next, androidx.compose.ui.text.TextRange(cursor + mark.length))
}

private fun insert(value: TextFieldValue, block: String): TextFieldValue {
    val cursor = max(value.selection.start, value.selection.end)
    val next = value.text.substring(0, cursor) + block + value.text.substring(cursor)
    return TextFieldValue(next, androidx.compose.ui.text.TextRange(cursor + block.length))
}

private fun inline(source: String) = buildAnnotatedString {
    var index = 0
    while (index < source.length) {
        when {
            source.startsWith("**", index) -> {
                val end = source.indexOf("**", index + 2)
                if (end < 0) {
                    append(source[index])
                    index++
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(source.substring(index + 2, end)) }
                    index = end + 2
                }
            }
            source.startsWith("~~", index) -> {
                val end = source.indexOf("~~", index + 2)
                if (end < 0) {
                    append(source[index])
                    index++
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(source.substring(index + 2, end)) }
                    index = end + 2
                }
            }
            source.startsWith("<u>", index) -> {
                val end = source.indexOf("</u>", index + 3)
                if (end < 0) {
                    append(source[index])
                    index++
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(source.substring(index + 3, end)) }
                    index = end + 4
                }
            }
            source.startsWith("`", index) -> {
                val end = source.indexOf('`', index + 1)
                if (end < 0) {
                    append(source[index])
                    index++
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(source.substring(index + 1, end)) }
                    index = end + 1
                }
            }
            source.startsWith("[", index) -> {
                val labelEnd = source.indexOf("](", index + 1)
                val urlEnd = if (labelEnd < 0) -1 else source.indexOf(')', labelEnd + 2)
                if (labelEnd < 0 || urlEnd < 0) {
                    append(source[index])
                    index++
                } else {
                    withStyle(SpanStyle(color = HoldThemeColors.accent, textDecoration = TextDecoration.Underline)) {
                        append(source.substring(index + 1, labelEnd))
                    }
                    index = urlEnd + 1
                }
            }
            source[index] == '*' -> {
                val end = source.indexOf('*', index + 1)
                if (end < 0) {
                    append(source[index])
                    index++
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(source.substring(index + 1, end)) }
                    index = end + 1
                }
            }
            else -> {
                append(source[index])
                index++
            }
        }
    }
}
