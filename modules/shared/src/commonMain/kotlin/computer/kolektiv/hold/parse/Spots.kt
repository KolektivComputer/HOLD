package computer.kolektiv.hold.parse

import computer.kolektiv.hold.model.Entry
import computer.kolektiv.hold.model.Handle
import computer.kolektiv.hold.model.Payload
import computer.kolektiv.hold.model.newId
import computer.kolektiv.hold.model.nowMillis

data class Spot(val kind: String, val value: String)

private data class Rule(val kind: String, val pattern: Regex, val pick: Int)

private val rules = listOf(
    Rule("email", Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), 0),
    Rule("discord", Regex("\\b[A-Za-z0-9._]{2,32}#\\d{4}\\b"), 0),
    Rule("discord", Regex("\\bdiscord(?:\\s+account)?[:\\s]+@?([A-Za-z0-9._]{2,32})", RegexOption.IGNORE_CASE), 1),
    Rule("fluxer", Regex("\\bfluxer[:\\s]+@?([A-Za-z0-9._]{2,32})", RegexOption.IGNORE_CASE), 1),
    Rule("x", Regex("\\b(?:twitter|x(?:\\.com)?)[:\\s]+@?([A-Za-z0-9_]{1,15})\\b", RegexOption.IGNORE_CASE), 1),
    Rule(
        "phone",
        Regex("(?<!\\d)(?:\\+\\d{1,3}[\\s.-]?)?(?:\\(?\\d{3}\\)?[\\s.-]?)\\d{3}[\\s.-]?\\d{4}(?!\\d)"),
        0,
    ),
    Rule(
        "address",
        Regex(
            "\\b\\d{1,6}\\s+[A-Za-z0-9.'-]{2,}(?:\\s+[A-Za-z0-9.'-]{2,}){0,4}\\s+(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Way|Court|Ct)\\b\\.?",
            RegexOption.IGNORE_CASE,
        ),
        0,
    ),
)

fun canon(kind: String, value: String): String {
    val trimmed = value.trim().lowercase()
    return if (kind == "phone") trimmed.filter { it.isDigit() } else trimmed.removePrefix("@")
}

fun spotText(text: String): List<Spot> {
    data class Hit(val kind: String, val value: String, val start: Int, val end: Int)
    val found = ArrayList<Hit>()
    for (rule in rules) {
        for (match in rule.pattern.findAll(text)) {
            val raw = if (rule.pick == 0) match.value else match.groupValues.getOrNull(1)
            if (raw.isNullOrBlank()) continue
            val value = raw.trim().trimEnd('.', ',', ';', ':', '!', '?')
            if (rule.kind == "phone" && value.filter { it.isDigit() }.length < 7) continue
            val start = match.range.first + if (rule.pick == 0) 0 else match.value.indexOf(raw).coerceAtLeast(0)
            found += Hit(rule.kind, value, start, start + value.length)
        }
    }
    found.sortWith(compareBy<Hit> { it.start }.thenByDescending { it.end - it.start })
    val kept = ArrayList<Spot>()
    val seen = HashSet<String>()
    var cursor = -1
    for (hit in found) {
        if (hit.start < cursor) continue
        val key = "${hit.kind}:${canon(hit.kind, hit.value)}"
        cursor = hit.end
        if (!seen.add(key)) continue
        kept += Spot(hit.kind, hit.value)
    }
    return kept
}

fun filedSpot(kind: String, value: String, deviceId: String, title: String = ""): Entry {
    val now = nowMillis()
    val name = title.trim()
    val label = if (name.isNotEmpty() && !name.equals(value.trim(), ignoreCase = true)) name else value.trim()
    val payload: Payload = if (kind == "phone") {
        Payload.Phone(value.trim())
    } else {
        Payload.Contact(listOf(Handle(kind, value.trim())))
    }
    return Entry(
        id = newId(),
        kind = if (kind == "phone") "number" else "contact",
        title = label,
        createdAt = now,
        updatedAt = now,
        deviceId = deviceId,
        dirty = true,
        payload = payload,
    )
}

fun phraseEntry(kind: String, text: String, deviceId: String): Entry? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (kind == "phone" || kind == "number") {
        val phone = spotText(trimmed).find { it.kind == "phone" }?.value ?: trimmed
        return filedSpot("phone", phone, deviceId)
    }
    if (kind == "discord" || kind == "email" || kind == "x" || kind == "fluxer" || kind == "address") {
        val value = spotText(trimmed).find { it.kind == kind }?.value
            ?: trimmed.replace(Regex("^$kind\\s*", RegexOption.IGNORE_CASE), "").trim().ifBlank { trimmed }
        return filedSpot(kind, value, deviceId)
    }
    val now = nowMillis()
    if (kind == "voice") {
        return Entry(
            id = newId(),
            kind = "voice",
            title = trimmed.take(80),
            createdAt = now,
            updatedAt = now,
            deviceId = deviceId,
            dirty = true,
            payload = Payload.Voice(transcript = trimmed),
        )
    }
    return Entry(
        id = newId(),
        kind = "note",
        title = trimmed.take(80),
        body = trimmed,
        createdAt = now,
        updatedAt = now,
        deviceId = deviceId,
        dirty = true,
        payload = Payload.Note(),
    )
}
