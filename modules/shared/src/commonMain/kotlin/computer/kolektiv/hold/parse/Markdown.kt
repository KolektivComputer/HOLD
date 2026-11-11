package computer.kolektiv.hold.parse

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val checked: Boolean? = null) : MdBlock
    data class Numbered(val index: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val out = mutableListOf<MdBlock>()
    var index = 0
    while (index < lines.size) {
        val raw = lines[index]
        val trim = raw.trim()
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
            out += MdBlock.Bullet(check.groupValues[2], check.groupValues[1].equals("x", ignoreCase = true))
            index++
            continue
        }
        val bullet = bulletPattern.find(trim)
        if (bullet != null) {
            out += MdBlock.Bullet(bullet.groupValues[1], null)
            index++
            continue
        }
        val number = numberPattern.find(trim)
        if (number != null) {
            out += MdBlock.Numbered(number.groupValues[1], number.groupValues[2])
            index++
            continue
        }
        if (trim.startsWith(">")) {
            out += MdBlock.Quote(trim.removePrefix(">").trim())
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
    return out
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
