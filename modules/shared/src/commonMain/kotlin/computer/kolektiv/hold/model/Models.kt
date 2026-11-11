package computer.kolektiv.hold.model

import kotlin.time.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface Stamp {
    val id: String
    val updatedAt: Long
    val deletedAt: Long?
    val dirty: Boolean
    val syncedAt: Long?
}

@Serializable
data class InkPoint(val x: Float, val y: Float, val p: Float = 0.5f)

@Serializable
data class InkStroke(
    val tool: String,
    val color: String,
    val width: Float,
    val points: List<InkPoint> = emptyList(),
)

@Serializable
data class Handle(val kind: String, val value: String)

@Serializable
sealed interface Payload {
    @Serializable
    @SerialName("note")
    data class Note(
        val ink: List<InkStroke> = emptyList(),
        val shotId: String? = null,
    ) : Payload

    @Serializable
    @SerialName("number")
    data class Phone(val phone: String) : Payload

    @Serializable
    @SerialName("voice")
    data class Voice(
        val durationMs: Long = 0,
        val transcript: String = "",
        val hasAudio: Boolean = false,
        val mime: String = "",
        val peaks: List<Float> = emptyList(),
    ) : Payload

    @Serializable
    @SerialName("radio")
    data class Radio(
        val protocol: String,
        val uid: String = "",
        val frequencyHz: Long? = null,
        val filetype: String = "",
        val raw: String = "",
    ) : Payload

    @Serializable
    @SerialName("health")
    data class Health(
        val metric: String,
        val value: Double,
        val unit: String = "",
        val source: String = "manual",
        val recordedAt: Long,
    ) : Payload

    @Serializable
    @SerialName("place")
    data class Place(
        val lat: Double? = null,
        val lon: Double? = null,
        val accuracyM: Double? = null,
        val plus: String = "",
        val address: String = "",
        val w3w: String = "",
    ) : Payload

    @Serializable
    @SerialName("contact")
    data class Contact(val handles: List<Handle> = emptyList()) : Payload
}

@Serializable
data class Entry(
    override val id: String,
    val kind: String,
    val title: String,
    val body: String = "",
    val createdAt: Long,
    override val updatedAt: Long,
    val categoryId: String? = null,
    val tagIds: List<String> = emptyList(),
    val pinned: Boolean = false,
    override val deletedAt: Long? = null,
    override val dirty: Boolean = false,
    override val syncedAt: Long? = null,
    val deviceId: String = "",
    val type: String = "",
    val payload: Payload,
) : Stamp

@Serializable
data class NamedItem(
    override val id: String,
    val name: String,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val dirty: Boolean = false,
    override val syncedAt: Long? = null,
) : Stamp

@Serializable
data class SavedView(
    override val id: String,
    val name: String,
    val kinds: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val categoryId: String? = null,
    val query: String = "",
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val dirty: Boolean = false,
    override val syncedAt: Long? = null,
) : Stamp

@Serializable
data class SyncConfig(
    val baseUrl: String = "",
    val token: String = "",
    val deviceName: String = "This pocket",
    val deviceId: String = "",
    val lastCursor: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

@Serializable
data class PocketSettings(
    val mode: String = "full",
    val follow: Boolean = false,
    val deepseekKey: String = "",
    val autoTranscribe: Boolean = true,
)

@Serializable
data class Fix(
    val id: String,
    val at: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Double? = null,
)

val BUILTIN_TAGS = listOf(
    "tag_phone" to "phone",
    "tag_email" to "email",
    "tag_address" to "address",
    "tag_discord" to "discord",
    "tag_x" to "x",
    "tag_fluxer" to "fluxer",
)

fun Entry.medium(): String = when (type) {
    "audio", "ink", "text" -> type
    else -> when (val body = payload) {
        is Payload.Voice -> "audio"
        is Payload.Note -> if (body.ink.isNotEmpty() || body.shotId != null) "ink" else "text"
        else -> "text"
    }
}

@Serializable
data class Snapshot(
    val entries: List<Entry> = emptyList(),
    val categories: List<NamedItem> = emptyList(),
    val tags: List<NamedItem> = emptyList(),
    val views: List<SavedView> = emptyList(),
    val sync: SyncConfig = SyncConfig(),
    val settings: PocketSettings = PocketSettings(),
    val trail: List<Fix> = emptyList(),
)

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun newId(): String =
    "id_${kotlin.random.Random.nextLong().toULong().toString(16)}${nowMillis().toString(16)}"
