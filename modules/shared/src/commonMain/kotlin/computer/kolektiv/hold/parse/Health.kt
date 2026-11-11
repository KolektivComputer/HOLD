package computer.kolektiv.hold.parse

import computer.kolektiv.hold.model.nowMillis
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

data class HealthDraft(val metric: String, val value: Double, val unit: String, val recordedAt: Long)

private val json = Json { ignoreUnknownKeys = true }

fun parseHealthText(text: String, now: Long = nowMillis()): List<HealthDraft> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) error("That file was empty.")
    if (trimmed.startsWith("[") || trimmed.startsWith("{")) return parseJson(trimmed, now)
    return parseCsv(trimmed, now)
}

private fun parseJson(text: String, now: Long): List<HealthDraft> {
    val root = json.parseToJsonElement(text)
    val rows = when (root) {
        is JsonArray -> root
        else -> listOf(root)
    }
    val drafts = rows.mapIndexedNotNull { index, row -> one(row, now + index) }
    if (drafts.isEmpty()) error("No readings in that JSON.")
    return drafts
}

private fun one(element: JsonElement, fallback: Long): HealthDraft? {
    val row = element as? JsonObject ?: return null
    val metric = (row.string("metric") ?: row.string("type") ?: row.string("name") ?: "Reading").ifBlank { "Reading" }
    val value = row.double("value") ?: return null
    val unit = row.string("unit").orEmpty()
    val recorded = row["recordedAt"] ?: row["at"] ?: row["date"]
    val recordedAt = when (recorded) {
        is JsonPrimitive if recorded.isString -> recorded.longOrNull ?: parseIso(recorded.content) ?: fallback
        is JsonPrimitive -> recorded.longOrNull ?: recorded.doubleOrNull?.toLong() ?: fallback
        else -> fallback
    }
    return HealthDraft(metric, value, unit, recordedAt)
}

private fun parseIso(text: String): Long? {
    val match = Regex("""(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})""").find(text) ?: return null
    val year = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    val hour = match.groupValues[4].toInt()
    val minute = match.groupValues[5].toInt()
    val second = match.groupValues[6].toInt()
    val days = civilDays(year, month, day)
    return days * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1_000L
}

private fun civilDays(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = if (y >= 0) y else y - 399
    val yoe = y - era * 400
    val doy = (153 * (month + if (month > 2) -3 else 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content

private fun JsonObject.double(key: String): Double? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.content.toDoubleOrNull()
}

private fun parseCsv(text: String, now: Long): List<HealthDraft> {
    val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    if (lines.size < 2) error("The CSV needs a header and at least one row.")
    val header = lines.first().split(',').map { it.trim().lowercase() }
    val metricAt = header.indexOfFirst { it == "metric" || it == "type" || it == "name" }
    val valueAt = header.indexOf("value")
    val unitAt = header.indexOf("unit")
    val timeAt = header.indexOfFirst { it in setOf("recordedat", "at", "date", "time") }
    if (valueAt < 0) error("The CSV needs a value column.")
    val drafts = lines.drop(1).mapNotNull { line ->
        val cells = line.split(',').map { it.trim() }
        val value = cells.getOrNull(valueAt)?.toDoubleOrNull() ?: return@mapNotNull null
        HealthDraft(
            metric = if (metricAt >= 0) cells.getOrNull(metricAt).orEmpty().ifBlank { "Reading" } else "Reading",
            value = value,
            unit = if (unitAt >= 0) cells.getOrNull(unitAt).orEmpty() else "",
            recordedAt = if (timeAt >= 0) cells.getOrNull(timeAt)?.toLongOrNull() ?: now else now,
        )
    }
    if (drafts.isEmpty()) error("No readings in that CSV.")
    return drafts
}
