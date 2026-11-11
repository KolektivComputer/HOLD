package computer.kolektiv.hold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import computer.kolektiv.hold.model.Entry
import computer.kolektiv.hold.model.Payload
import computer.kolektiv.hold.model.Snapshot
import computer.kolektiv.hold.model.SyncConfig
import computer.kolektiv.hold.model.buildSeed
import computer.kolektiv.hold.model.newId
import computer.kolektiv.hold.model.nowMillis
import computer.kolektiv.hold.parse.parseFlipper
import computer.kolektiv.hold.store.HoldRepository
import computer.kolektiv.hold.sync.pushSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface JsonPoster {
    fun post(url: String, body: String, token: String): String
}

interface Speech {
    fun listen(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit)
    fun stop()
}

object NoSpeech : Speech {
    override fun listen(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onError: (String) -> Unit) {
        onError("This runtime can't listen yet. Type it under Write.")
    }

    override fun stop() = Unit
}

private class HoldController(private val repository: HoldRepository) {
    var snapshot by mutableStateOf(initial())
        private set
    var screen by mutableStateOf("log")
    var selectedId by mutableStateOf<String?>(null)
    var status by mutableStateOf("")

    private fun initial(): Snapshot {
        val loaded = repository.load()
        if (loaded.entries.isEmpty() && loaded.sync.deviceId.isEmpty()) {
            val seed = buildSeed()
            repository.save(seed)
            return seed
        }
        return loaded
    }

    fun visible(): List<Entry> = snapshot.entries
        .filter { it.deletedAt == null }
        .sortedWith(compareByDescending<Entry> { it.pinned }.thenByDescending { it.updatedAt })

    fun selected(): Entry? = snapshot.entries.find { it.id == selectedId && it.deletedAt == null }

    fun save(entry: Entry) {
        val next = entry.copy(updatedAt = nowMillis(), dirty = true)
        snapshot = snapshot.copy(entries = snapshot.entries.filter { it.id != next.id } + next)
        repository.save(snapshot)
        selectedId = next.id
        screen = "log"
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

    fun updateSync(config: SyncConfig) {
        snapshot = snapshot.copy(sync = config.copy(deviceId = snapshot.sync.deviceId.ifBlank { config.deviceId }))
        repository.save(snapshot)
    }

    fun replace(next: Snapshot) {
        snapshot = next
        repository.save(next)
    }
}

@Composable
fun HoldApp(repository: HoldRepository, poster: JsonPoster, speech: Speech = NoSpeech) {
    val controller = remember(repository) { HoldController(repository) }
    HoldTheme {
        Scaffold(
            containerColor = HoldThemeColors.bg,
            bottomBar = {
                NavigationBar(containerColor = HoldThemeColors.bg) {
                    listOf("log" to "Log", "voice" to "Catch", "write" to "Write", "library" to "Library").forEach { (id, label) ->
                        NavigationBarItem(
                            selected = controller.screen == id,
                            onClick = { controller.screen = id },
                            label = { Text(label) },
                            icon = { Text(label.take(1)) },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("HOLD", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                when (controller.screen) {
                    "write" -> WriteScreen(controller)
                    "voice" -> VoiceScreen(controller, speech)
                    "library" -> LibraryScreen(controller, poster)
                    else -> LogScreen(controller)
                }
            }
        }
    }
}

@Composable
private fun LogScreen(controller: HoldController) {
    val selected = controller.selected()
    if (selected != null) {
        DetailScreen(selected, controller)
        return
    }
    Text("On this device", color = HoldThemeColors.muted)
    controller.visible().forEach { entry ->
        TextButton(onClick = { controller.selectedId = entry.id }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(entry.kind.uppercase(), color = HoldThemeColors.muted)
                Text(entry.title.ifBlank { "Untitled" })
            }
        }
    }
}

@Composable
private fun DetailScreen(entry: Entry, controller: HoldController) {
    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var body by remember(entry.id) { mutableStateOf(entry.body) }
    TextButton(onClick = { controller.selectedId = null }) { Text("Back") }
    OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(body, { body = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
    Text(payloadLine(entry.payload), color = HoldThemeColors.muted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { controller.save(entry.copy(title = title.trim().ifBlank { entry.title }, body = body.trim())) }) { Text("Save") }
        TextButton(onClick = { controller.delete(entry.id) }) { Text("Delete") }
    }
}

@Composable
private fun WriteScreen(controller: HoldController) {
    var kind by remember { mutableStateOf("note") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    Text("File something on this device.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("note", "number", "radio", "health", "place").forEach { item ->
            TextButton(onClick = { kind = item }) { Text(if (item == kind) item.uppercase() else item) }
        }
    }
    OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        extra,
        { extra = it },
        label = { Text(if (kind == "radio") "Dump or UID" else if (kind == "number") "Phone" else if (kind == "health") "Value" else "Extra") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(body, { body = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        val now = nowMillis()
        val payload = payloadFor(kind, extra, now)
        val id = newId()
        controller.save(
            Entry(
                id = id,
                kind = kind,
                title = title.ifBlank { defaultTitle(kind, payload) },
                body = body.trim(),
                createdAt = now,
                updatedAt = now,
                deviceId = controller.snapshot.sync.deviceId,
                payload = payload,
            ),
        )
        title = ""
        body = ""
        extra = ""
    }) { Text("File it") }
}

@Composable
private fun VoiceScreen(controller: HoldController, speech: Speech) {
    var heard by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Tap once. Talk. Tap again to file it.") }
    var listening by remember { mutableStateOf(false) }
    Text(note)
    Text(heard.ifBlank { "…" })
    Button(onClick = {
        if (listening) {
            speech.stop()
            listening = false
            val now = nowMillis()
            if (heard.isBlank()) {
                note = "Nothing to file."
                return@Button
            }
            controller.save(
                Entry(
                    id = newId(),
                    kind = "voice",
                    title = heard.take(80),
                    body = "",
                    createdAt = now,
                    updatedAt = now,
                    deviceId = controller.snapshot.sync.deviceId,
                    payload = Payload.Voice(transcript = heard),
                ),
            )
            note = "Saved on this device."
            heard = ""
        } else {
            listening = true
            note = "Listening"
            speech.listen(
                onPartial = { heard = it },
                onFinal = { heard = it },
                onError = {
                    listening = false
                    note = it
                },
            )
        }
    }) { Text(if (listening) "Stop" else "Catch") }
}

@Composable
private fun LibraryScreen(controller: HoldController, poster: JsonPoster) {
    val scope = rememberCoroutineScope()
    var baseUrl by remember(controller.snapshot.sync.baseUrl) { mutableStateOf(controller.snapshot.sync.baseUrl) }
    var token by remember(controller.snapshot.sync.token) { mutableStateOf(controller.snapshot.sync.token) }
    Text("Sync only when you ask. Point this at your own server.")
    OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Server") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(token, { token = it }, label = { Text("Token") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            controller.updateSync(controller.snapshot.sync.copy(baseUrl = baseUrl.trim(), token = token.trim()))
            controller.status = "Server settings saved on this device."
        }) { Text("Save server") }
        Button(onClick = {
            controller.updateSync(controller.snapshot.sync.copy(baseUrl = baseUrl.trim(), token = token.trim()))
            val snap = controller.snapshot
            scope.launch {
                controller.status = "Syncing…"
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { pushSync(snap) { url, body, token -> poster.post(url, body, token) } }
                }
                outcome.onSuccess {
                    controller.replace(it.snapshot)
                    controller.status = it.summary
                }.onFailure {
                    controller.status = it.message ?: "Sync failed."
                }
            }
        }) { Text("Sync now") }
    }
    if (controller.status.isNotBlank()) Text(controller.status)
    Text("${controller.visible().size} entries on this device.", color = HoldThemeColors.muted)
}

private fun payloadFor(kind: String, extra: String, now: Long): Payload = when (kind) {
    "number" -> Payload.Phone(extra.trim())
    "radio" -> {
        val parsed = if (extra.contains(":")) parseFlipper(extra) else null
        Payload.Radio(
            protocol = parsed?.protocol ?: "other",
            uid = parsed?.uid ?: extra.trim(),
            frequencyHz = parsed?.frequencyHz,
            filetype = parsed?.filetype.orEmpty(),
            raw = parsed?.raw ?: extra.trim(),
        )
    }
    "health" -> Payload.Health(metric = "Reading", value = extra.toDoubleOrNull() ?: 0.0, recordedAt = now)
    "place" -> Payload.Place()
    else -> Payload.Note
}

private fun defaultTitle(kind: String, payload: Payload): String = when (payload) {
    is Payload.Phone -> payload.phone.ifBlank { "Number" }
    is Payload.Radio -> payload.uid.ifBlank { "Radio dump" }
    is Payload.Health -> payload.metric
    is Payload.Voice -> "Voice"
    is Payload.Place -> "Place"
    Payload.Note -> if (kind == "note") "Note" else kind
}

private fun payloadLine(payload: Payload): String = when (payload) {
    is Payload.Phone -> payload.phone
    is Payload.Voice -> payload.transcript.ifBlank { "Voice" }
    is Payload.Radio -> listOf(payload.protocol.uppercase(), payload.uid).filter { it.isNotBlank() }.joinToString(" · ")
    is Payload.Health -> "${payload.value} ${payload.unit}".trim()
    is Payload.Place -> listOfNotNull(payload.lat, payload.lon).joinToString(", ").ifBlank { "No coordinates" }
    Payload.Note -> ""
}
