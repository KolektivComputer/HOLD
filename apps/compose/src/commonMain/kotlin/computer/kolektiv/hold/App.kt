package computer.kolektiv.hold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Library
import com.composables.icons.lucide.List as LogIcon
import com.composables.icons.lucide.Lucide
import computer.kolektiv.hold.geo.haversineMeters
import computer.kolektiv.hold.geo.plusCode
import computer.kolektiv.hold.model.BUILTIN_TAGS
import computer.kolektiv.hold.model.Entry
import computer.kolektiv.hold.model.Fix
import computer.kolektiv.hold.model.Handle
import computer.kolektiv.hold.model.InkStroke
import computer.kolektiv.hold.model.NamedItem
import computer.kolektiv.hold.model.Payload
import computer.kolektiv.hold.model.PocketSettings
import computer.kolektiv.hold.model.SEED_CATEGORY_IDS
import computer.kolektiv.hold.model.SEED_ENTRY_IDS
import computer.kolektiv.hold.model.SEED_TAG_IDS
import computer.kolektiv.hold.model.SEED_VIEW_IDS
import computer.kolektiv.hold.model.Snapshot
import computer.kolektiv.hold.model.SyncConfig
import computer.kolektiv.hold.model.buildSeed
import computer.kolektiv.hold.model.isExampleSet
import computer.kolektiv.hold.model.medium
import computer.kolektiv.hold.model.newId
import computer.kolektiv.hold.model.nowMillis
import computer.kolektiv.hold.parse.canon
import computer.kolektiv.hold.parse.phraseEntry
import computer.kolektiv.hold.parse.spotText
import computer.kolektiv.hold.store.HoldRepository
import computer.kolektiv.hold.sync.pushSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface JsonPoster {
    fun post(url: String, body: String, token: String): String
}

data class CatchResult(
    val transcript: String = "",
    val audio: ByteArray? = null,
    val mime: String = "",
    val durationMs: Long = 0,
    val peaks: List<Float> = emptyList(),
)

interface Speech {
    fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit, onError: (String) -> Unit, transcribe: Boolean = true)
    fun stop(onDone: (CatchResult) -> Unit)
    fun transcribe(bytes: ByteArray, mime: String, onText: (String) -> Unit, onError: (String) -> Unit) {
        onError("This runtime can't transcribe yet.")
    }
}

object NoSpeech : Speech {
    override fun listen(onPartial: (String) -> Unit, onLevel: (Float) -> Unit, onError: (String) -> Unit, transcribe: Boolean) {
        onError("This runtime can't listen yet. Type it under Write.")
    }

    override fun stop(onDone: (CatchResult) -> Unit) {
        onDone(CatchResult())
    }
}

data class CaptureRequest(val kind: String, val text: String)

class NoteDraft {
    var kind by mutableStateOf("note")
    var title by mutableStateOf("")
    var body by mutableStateOf("")
    var extra by mutableStateOf("")
    var lat by mutableStateOf<Double?>(null)
    var lon by mutableStateOf<Double?>(null)
    var accuracy by mutableStateOf<Double?>(null)
    var address by mutableStateOf("")
    var w3w by mutableStateOf("")
    var strokes by mutableStateOf<List<InkStroke>>(emptyList())
    var shotId by mutableStateOf<String?>(null)
    var shot by mutableStateOf<ByteArray?>(null)
    var medium by mutableStateOf("text")
    var tagIds by mutableStateOf<List<String>>(emptyList())

    fun clear() {
        title = ""
        body = ""
        extra = ""
        lat = null
        lon = null
        accuracy = null
        address = ""
        w3w = ""
        strokes = emptyList()
        shotId = null
        shot = null
        tagIds = emptyList()
    }
}

class HoldController(private val repository: HoldRepository) {
    var snapshot by mutableStateOf(initial())
        private set
    var screen by mutableStateOf("log")
    var selectedId by mutableStateOf<String?>(null)
    var status by mutableStateOf("")
    var tool by mutableStateOf("pen")
    var ink by mutableStateOf(INK_COLORS.first())
    val draft = NoteDraft()

    private fun initial(): Snapshot {
        val loaded = repository.load()
        val base = if (loaded.entries.isEmpty() && loaded.sync.deviceId.isEmpty()) buildSeed() else loaded
        return storeBuiltin(base)
    }

    private fun storeBuiltin(source: Snapshot): Snapshot {
        val have = source.tags.map { it.id }.toSet()
        val extra = BUILTIN_TAGS.filter { it.first !in have }.map { (id, name) ->
            NamedItem(id = id, name = name, updatedAt = nowMillis(), dirty = true)
        }
        if (extra.isEmpty()) return source
        val next = source.copy(tags = source.tags + extra)
        repository.save(next)
        return next
    }

    fun visible(): List<Entry> = snapshot.entries
        .filter { it.deletedAt == null }
        .sortedWith(compareByDescending<Entry> { it.pinned }.thenByDescending { it.updatedAt })

    fun selected(): Entry? = snapshot.entries.find { it.id == selectedId && it.deletedAt == null }

    fun save(entry: Entry, stay: Boolean = false) {
        val fresh = snapshot.entries.none { it.id == entry.id }
        val typed = if (entry.type.isBlank()) entry.copy(type = entry.medium()) else entry
        val tagged = if (fresh) withAutoTags(typed) else typed
        val next = tagged.copy(updatedAt = nowMillis(), dirty = true)
        snapshot = snapshot.copy(entries = snapshot.entries.filter { it.id != next.id } + next)
        repository.save(snapshot)
        selectedId = next.id
        if (!stay) screen = "log"
    }

    fun delete(id: String) {
        val current = snapshot.entries.find { it.id == id } ?: return
        val entries = if (current.syncedAt == null) {
            snapshot.entries.filter { it.id != id }
        } else {
            snapshot.entries.map {
                if (it.id == id) it.copy(deletedAt = nowMillis(), updatedAt = nowMillis(), dirty = true) else it
            }
        }
        snapshot = snapshot.copy(entries = entries)
        repository.save(snapshot)
        selectedId = null
        screen = "log"
    }

    fun clearExamples() {
        if (!isExampleSet(snapshot.entries)) return
        snapshot = snapshot.copy(
            entries = snapshot.entries.filter { it.id !in SEED_ENTRY_IDS },
            categories = snapshot.categories.filter { it.id !in SEED_CATEGORY_IDS },
            tags = snapshot.tags.filter { it.id !in SEED_TAG_IDS },
            views = snapshot.views.filter { it.id !in SEED_VIEW_IDS },
        )
        repository.save(snapshot)
        selectedId = null
        status = "Examples cleared. This pocket is empty."
    }

    fun updateSync(config: SyncConfig) {
        snapshot = snapshot.copy(sync = config.copy(deviceId = snapshot.sync.deviceId.ifBlank { config.deviceId }))
        repository.save(snapshot)
    }

    fun updateSettings(settings: PocketSettings) {
        val next = if (settings.mode == "thin") settings.copy(follow = false) else settings
        snapshot = snapshot.copy(settings = next)
        repository.save(snapshot)
    }

    fun replace(next: Snapshot) {
        snapshot = next
        repository.save(next)
    }

    fun addFix(lat: Double, lon: Double, accuracyM: Double?) {
        val last = snapshot.trail.lastOrNull()
        if (last != null && haversineMeters(last.lat, last.lon, lat, lon) < 25) return
        val fix = Fix(id = newId(), at = nowMillis(), lat = lat, lon = lon, accuracyM = accuracyM)
        snapshot = snapshot.copy(trail = (snapshot.trail + fix).takeLast(500))
        repository.save(snapshot)
    }

    fun addTag(name: String): String? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val existing = snapshot.tags.find { it.deletedAt == null && it.name.equals(clean, ignoreCase = true) }
        if (existing != null) return existing.id
        val item = NamedItem(id = newId(), name = clean, updatedAt = nowMillis(), dirty = true)
        snapshot = snapshot.copy(tags = snapshot.tags + item)
        repository.save(snapshot)
        return item.id
    }

    fun withAutoTags(entry: Entry): Entry {
        val phone = (entry.payload as? Payload.Phone)?.phone.orEmpty()
        val transcript = (entry.payload as? Payload.Voice)?.transcript.orEmpty()
        val text = listOf(entry.title, entry.body, phone, transcript).joinToString("\n")
        val ids = spotText(text).mapNotNull { spot -> BUILTIN_TAGS.find { it.second == spot.kind }?.first }
        if (ids.isEmpty()) return entry
        return entry.copy(tagIds = (entry.tagIds + ids).distinct())
    }

    fun addHandle(id: String, kind: String, value: String) {
        val entry = snapshot.entries.find { it.id == id } ?: return
        val contact = entry.payload as? Payload.Contact ?: return
        val key = canon(kind, value)
        if (contact.handles.any { canon(it.kind, it.value) == key }) {
            status = "Already on ${entry.title.ifBlank { "that contact" }}."
            return
        }
        save(entry.copy(payload = contact.copy(handles = contact.handles + Handle(kind, value.trim()))), stay = true)
        status = "Added to ${entry.title.ifBlank { "the contact" }}."
    }

    fun filePhrase(kind: String, text: String) {
        val entry = phraseEntry(kind, text, snapshot.sync.deviceId) ?: return
        save(entry)
        status = "Filed from the shortcut."
    }
}

@Composable
fun HoldApp(
    repository: HoldRepository,
    poster: JsonPoster,
    speech: Speech = NoSpeech,
    ports: PocketPorts = EmptyPorts,
    capture: CaptureRequest? = null,
) {
    val controller = remember(repository) { HoldController(repository) }
    var fabOpen by remember { mutableStateOf(false) }
    LaunchedEffect(capture?.kind, capture?.text) {
        val request = capture ?: return@LaunchedEffect
        if (request.text.isBlank()) return@LaunchedEffect
        controller.filePhrase(request.kind, request.text)
    }
    HoldTheme {
        val selected = controller.selected()
        val inkBleed = (selected?.medium() == "ink") || (selected == null && controller.screen == "write" && controller.draft.medium == "ink" && controller.draft.kind == "note")
        val onLog = selected == null && controller.screen == "log"
        Scaffold(
            containerColor = HoldThemeColors.bg,
            floatingActionButton = {
                if (onLog) {
                    HoldFab(fabOpen, { fabOpen = !fabOpen }) { action ->
                        fabOpen = false
                        controller.selectedId = null
                        controller.draft.clear()
                        when (action) {
                            "audio" -> controller.screen = "voice"
                            "ink" -> {
                                controller.draft.kind = "note"
                                controller.draft.medium = "ink"
                                controller.screen = "write"
                            }
                            "place" -> {
                                controller.draft.kind = "place"
                                controller.draft.medium = "text"
                                controller.screen = "write"
                            }
                            "radio" -> {
                                controller.draft.kind = "radio"
                                controller.draft.medium = "text"
                                controller.screen = "write"
                            }
                            else -> {
                                controller.draft.kind = "note"
                                controller.draft.medium = "text"
                                controller.screen = "write"
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = HoldThemeColors.bg) {
                    listOf("log" to ("Log" to Lucide.LogIcon), "library" to ("Library" to Lucide.Library)).forEach { (id, item) ->
                        NavigationBarItem(
                            selected = controller.screen == id && selected == null,
                            onClick = {
                                controller.selectedId = null
                                controller.screen = id
                                fabOpen = false
                            },
                            label = { Text(item.first) },
                            icon = { Icon(item.second, item.first, tint = HoldThemeColors.fg) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    if (controller.snapshot.settings.mode == "thin") "HOLD · thin" else "HOLD",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                if (controller.status.isNotBlank()) {
                    Text(controller.status, color = HoldThemeColors.muted, modifier = Modifier.padding(horizontal = 16.dp))
                }
                Box(Modifier.weight(1f).padding(horizontal = if (inkBleed) 0.dp else 16.dp)) {
                    when {
                        selected != null -> DetailScreen(selected, controller, ports, poster, speech)
                        controller.screen == "write" -> WriteScreen(controller, ports)
                        controller.screen == "voice" -> VoiceScreen(controller, speech, ports)
                        controller.screen == "library" -> LibraryScreen(controller, poster, ports)
                        else -> LogScreen(controller, ports)
                    }
                }
            }
        }
    }
}

internal suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

fun summary(entry: Entry): String = when (val payload = entry.payload) {
    is Payload.Phone -> payload.phone
    is Payload.Voice -> payload.transcript.ifBlank { if (payload.hasAudio) "Voice recording" else "Voice" }
    is Payload.Radio -> listOf(payload.protocol.uppercase(), payload.uid).filter { it.isNotBlank() }.joinToString(" · ")
    is Payload.Health -> "${payload.value} ${payload.unit}".trim()
    is Payload.Place -> {
        val lat = payload.lat
        val lon = payload.lon
        when {
            payload.address.isNotBlank() -> payload.address
            payload.plus.isNotBlank() -> payload.plus
            lat != null && lon != null -> plusCode(lat, lon)
            else -> "No coordinates"
        }
    }
    is Payload.Contact -> payload.handles.joinToString(" · ") { "${it.kind} ${it.value}" }.ifBlank { "Contact" }
    is Payload.Note -> if (payload.ink.isNotEmpty()) "Ink" else entry.body
}

fun defaultTitle(kind: String, payload: Payload): String = when (payload) {
    is Payload.Phone -> payload.phone.ifBlank { "Number" }
    is Payload.Radio -> payload.uid.ifBlank { "Radio dump" }
    is Payload.Health -> payload.metric
    is Payload.Voice -> "Voice"
    is Payload.Place -> "Place"
    is Payload.Contact -> payload.handles.firstOrNull()?.value ?: "Contact"
    is Payload.Note -> if (kind == "note") "Note" else kind
}
