package computer.kolektiv.hold.model

fun buildSeed(now: Long = nowMillis()): Snapshot {
    val deviceId = newId()
    fun named(id: String, name: String, offset: Long) = NamedItem(id = id, name = name, updatedAt = now - offset)
    val categories = listOf(
        named("cat_personal", "Personal", 10_000),
        named("cat_radio", "Radio", 9_000),
        named("cat_errands", "Errands", 8_000),
        named("cat_health", "Health", 7_000),
    )
    val tags = listOf(
        named("tag_later", "later", 6_000),
        named("tag_driving", "driving", 5_000),
        named("tag_flipper", "flipper", 4_000),
    )
    val views = listOf(
        SavedView(id = "view_radio", name = "Radio dumps", kinds = listOf("radio"), updatedAt = now - 3_000),
        SavedView(id = "view_driving", name = "Driving", tagIds = listOf("tag_driving"), updatedAt = now - 2_000),
    )
    val nfc = listOf(
        "Filetype: Flipper NFC device",
        "Version: 4",
        "Device type: NTAG215",
        "UID: 04 A1 22 9C 5E 67 80",
        "ATQA: 44 00",
        "SAK: 00",
    ).joinToString("\n")
    fun entry(
        id: String,
        kind: String,
        title: String,
        body: String,
        ago: Long,
        categoryId: String?,
        tagIds: List<String>,
        pinned: Boolean = false,
        payload: Payload,
    ) = Entry(
        id = id,
        kind = kind,
        title = title,
        body = body,
        createdAt = now - ago,
        updatedAt = now - ago,
        categoryId = categoryId,
        tagIds = tagIds,
        pinned = pinned,
        deviceId = deviceId,
        payload = payload,
    )
    val entries = listOf(
        entry("ent_shed", "note", "Shed gate", "Code is on the post, not the door. The lock sticks if you turn it left first.", 3_600_000, "cat_personal", listOf("tag_later"), true, Payload.Note),
        entry("ent_tow", "number", "Tow — Ray's", "Ask for the night desk.", 7_200_000, "cat_errands", listOf("tag_driving"), payload = Payload.Phone("+1 555 014 0188")),
        entry("ent_voice", "voice", "Charger", "", 5_400_000, null, listOf("tag_driving"), payload = Payload.Voice(durationMs = 3200, transcript = "Left the charger in the truck.")),
        entry("ent_nfc", "radio", "NTAG215 · 04 A1 22 9C 5E 67 80", "Plant tag on the bench vise.", 9_000_000, "cat_radio", listOf("tag_flipper"), payload = Payload.Radio(protocol = "nfc", uid = "04 A1 22 9C 5E 67 80", filetype = "Flipper NFC device", raw = nfc)),
        entry("ent_hr", "health", "Resting heart rate", "", 10_800_000, "cat_health", emptyList(), payload = Payload.Health(metric = "Heart rate", value = 62.0, unit = "bpm", recordedAt = now - 10_800_000)),
        entry("ent_place", "place", "Pull-off past the bridge", "Gravel on the right, before the narrow part. No signal there.", 14_400_000, null, listOf("tag_driving"), payload = Payload.Place()),
    )
    return Snapshot(
        entries = entries,
        categories = categories,
        tags = tags,
        views = views,
        sync = SyncConfig(deviceId = deviceId),
    )
}
