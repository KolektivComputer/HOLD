package computer.kolektiv.hold.parse

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val checked: Boolean? = null, val depth: Int = 0) : MdBlock
    data class Numbered(val index: String, val text: String, val depth: Int = 0) : MdBlock
    data class Quote(val text: String, val depth: Int = 0) : MdBlock
    data class ListItem(val text: String, val checked: Boolean? = null, val depth: Int = 0, val index: String = "")
    data class ItemList(val ordered: Boolean = false, val items: List<ListItem> = emptyList()) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

fun MdBlock.body(): String = when (this) {
    is MdBlock.Heading -> text
    is MdBlock.Paragraph -> text
    is MdBlock.Bullet -> text
    is MdBlock.Numbered -> text
    is MdBlock.Quote -> text
    is MdBlock.ItemList -> items.joinToString("\n") { it.text }
    is MdBlock.Table -> ""
    is MdBlock.Rule -> ""
}

fun renderMarkdown(blocks: List<MdBlock>): String = blocks.joinToString("\n\n") { block ->
    when (block) {
        is MdBlock.Heading -> "${"#".repeat(block.level.coerceIn(1, 3))} ${block.text}"
        is MdBlock.Paragraph -> block.text
        is MdBlock.Bullet -> {
            val mark = when (block.checked) {
                null -> "- "
                true -> "- [x] "
                false -> "- [ ] "
            }
            "  ".repeat(block.depth) + mark + block.text
        }
        is MdBlock.Numbered -> "  ".repeat(block.depth) + "${block.index}. ${block.text}"
        is MdBlock.Quote -> "> ".repeat(block.depth + 1) + block.text
        is MdBlock.ItemList -> block.items.joinToString("\n") { item ->
            val pad = "  ".repeat(item.depth)
            if (block.ordered) pad + "${item.index.ifBlank { "1" }}. ${item.text}"
            else {
                val mark = when (item.checked) {
                    null -> "- "
                    true -> "- [x] "
                    false -> "- [ ] "
                }
                pad + mark + item.text
            }
        }
        is MdBlock.Rule -> "---"
        is MdBlock.Table -> {
            val lines = block.rows.map { row -> "| ${row.joinToString(" | ")} |" }.toMutableList()
            if (lines.size >= 1) {
                val cells = block.rows.first().size.coerceAtLeast(1)
                lines.add(1, "| ${List(cells) { "---" }.joinToString(" | ")} |")
            }
            lines.joinToString("\n")
        }
    }
}

fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val out = mutableListOf<MdBlock>()
    var index = 0
    while (index < lines.size) {
        val raw = lines[index]
        val trim = raw.trim()
        val depth = leadingDepth(raw)
        if (trim.isEmpty()) {
            index++
            continue
        }
        if (trim == "---" || trim == "***") {
            out += MdBlock.Rule
            index++
            continue
        }
        if (isTable(trim)) {
            val rows = mutableListOf<List<String>>()
            while (index < lines.size && isTable(lines[index].trim())) {
                val cells = lines[index].trim().trim('|').split('|').map { it.trim() }
                if (cells.none { it.isNotEmpty() && it.all { char -> char == '-' || char == ':' } }) rows += cells
                index++
            }
            if (rows.isNotEmpty()) out += MdBlock.Table(rows)
            continue
        }
        val heading = headingPattern.find(trim)
        if (heading != null) {
            out += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            index++
            continue
        }
        val check = checkPattern.find(trim)
        if (check != null) {
            out += MdBlock.Bullet(check.groupValues[2], check.groupValues[1].equals("x", ignoreCase = true), depth)
            index++
            continue
        }
        val bullet = bulletPattern.find(trim)
        if (bullet != null) {
            out += MdBlock.Bullet(bullet.groupValues[1], null, depth)
            index++
            continue
        }
        val number = numberPattern.find(trim)
        if (number != null) {
            out += MdBlock.Numbered(number.groupValues[1], number.groupValues[2], depth)
            index++
            continue
        }
        if (trim.startsWith(">")) {
            val marks = trim.takeWhile { it == '>' }.length.coerceAtLeast(1)
            out += MdBlock.Quote(trim.drop(marks).trim(), marks - 1)
            index++
            continue
        }
        val text = StringBuilder(trim)
        index++
        while (index < lines.size && lines[index].isNotBlank() && !structural(lines[index].trim())) {
            text.append(' ').append(lines[index].trim())
            index++
        }
        out += MdBlock.Paragraph(text.toString())
    }
    return groupLists(out)
}

private fun groupLists(flat: List<MdBlock>): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    var index = 0
    while (index < flat.size) {
        when (val block = flat[index]) {
            is MdBlock.Bullet -> {
                val items = mutableListOf<MdBlock.ListItem>()
                while (index < flat.size && flat[index] is MdBlock.Bullet) {
                    val item = flat[index] as MdBlock.Bullet
                    items += MdBlock.ListItem(item.text, item.checked, item.depth)
                    index++
                }
                out += MdBlock.ItemList(ordered = false, items = items)
            }
            is MdBlock.Numbered -> {
                val items = mutableListOf<MdBlock.ListItem>()
                while (index < flat.size && flat[index] is MdBlock.Numbered) {
                    val item = flat[index] as MdBlock.Numbered
                    items += MdBlock.ListItem(item.text, depth = item.depth, index = item.index)
                    index++
                }
                out += MdBlock.ItemList(ordered = true, items = items)
            }
            else -> {
                out += block
                index++
            }
        }
    }
    return out
}

private fun leadingDepth(raw: String): Int {
    var spaces = 0
    for (char in raw) {
        when (char) {
            ' ' -> spaces++
            '\t' -> spaces += 2
            else -> return spaces / 2
        }
    }
    return spaces / 2
}

private fun isTable(line: String) = line.startsWith("|") && line.indexOf('|', 1) > 0

private fun structural(line: String): Boolean {
    if (line == "---" || line == "***" || isTable(line) || line.startsWith(">")) return true
    return headingPattern.containsMatchIn(line) || checkPattern.containsMatchIn(line) || bulletPattern.containsMatchIn(line) || numberPattern.containsMatchIn(line)
}

private val headingPattern = Regex("^(#{1,3})\\s+(.*)$")
private val checkPattern = Regex("^-\\s+\\[([ xX])]\\s+(.*)$")
private val bulletPattern = Regex("^[-*]\\s+(.*)$")
private val numberPattern = Regex("^(\\d+)[.)]\\s+(.*)$")
