package computer.kolektiv.hold.parse

data class ParsedRadio(
    val protocol: String,
    val uid: String,
    val frequencyHz: Long?,
    val filetype: String,
    val raw: String,
    val title: String,
)

fun parseFlipper(text: String): ParsedRadio {
    val raw = text.removePrefix("\uFEFF").trim()
    val fields = linkedMapOf<String, String>()
    for (line in raw.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        val idx = trimmed.indexOf(':')
        if (idx <= 0) continue
        val key = trimmed.substring(0, idx).trim().lowercase()
        val value = trimmed.substring(idx + 1).trim()
        if (key !in fields) fields[key] = value
    }
    val filetype = fields["filetype"].orEmpty()
    val keyType = fields["key type"] ?: fields["device type"] ?: fields["protocol"].orEmpty()
    val blob = "$filetype $keyType".lowercase()
    val protocol = when {
        "nfc" in blob -> "nfc"
        "rfid" in blob || "em4100" in blob || "hid" in blob -> "rfid"
        "subghz" in blob || "sub-ghz" in blob -> "subghz"
        "infrared" in blob || "ir" in blob -> "ir"
        "ibutton" in blob || "ibtn" in blob -> "ibutton"
        else -> "other"
    }
    val uid = fields["uid"] ?: fields["data"].orEmpty()
    val frequencyHz = fields["frequency"]?.toLongOrNull()
    val label = keyType.ifBlank { filetype.ifBlank { "Radio dump" } }
    val title = if (uid.isBlank()) label else "$label · $uid"
    return ParsedRadio(protocol, uid, frequencyHz, filetype, raw, title)
}
