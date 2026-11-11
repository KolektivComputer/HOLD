package computer.kolektiv.hold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import computer.kolektiv.hold.geo.plusCode
import computer.kolektiv.hold.geo.project
import computer.kolektiv.hold.geo.tilesAround
import computer.kolektiv.hold.geo.unproject
import computer.kolektiv.hold.model.Entry
import computer.kolektiv.hold.model.Payload
import computer.kolektiv.hold.model.PocketSettings
import computer.kolektiv.hold.model.isExampleSet
import computer.kolektiv.hold.model.newId
import computer.kolektiv.hold.model.nowMillis
import computer.kolektiv.hold.parse.parseFlipper
import computer.kolektiv.hold.parse.spotText
import computer.kolektiv.hold.sync.pushSync
import kotlin.io.encoding.Base64
import kotlinx.coroutines.launch

private val kinds = listOf("note", "number", "contact", "place", "radio", "health")

@Composable
fun LogScreen(controller: HoldController) {
    var filter by remember { mutableStateOf("all") }
    val rows = controller.visible().filter { filter == "all" || it.kind == filter || (filter == "number" && it.kind == "contact") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isExampleSet(controller.snapshot.entries)) {
            Text("These six are examples, so the pocket isn't empty.", color = HoldThemeColors.muted)
            Button(onClick = { controller.clearExamples() }) { Text("Clear examples") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("all", "note", "number", "place", "radio", "voice", "health").forEach { item ->
                TextButton(onClick = { filter = item }) { Text(if (item == filter) item.uppercase() else item) }
            }
        }
        if (rows.isEmpty()) Text("Nothing filed on this device.", color = HoldThemeColors.muted)
        rows.forEach { entry ->
            TextButton(onClick = { controller.selectedId = entry.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(entry.kind.uppercase(), color = HoldThemeColors.muted)
                    Text(entry.title.ifBlank { "Untitled" })
                    val line = summary(entry)
                    if (line.isNotBlank()) Text(line, color = HoldThemeColors.muted)
                }
            }
        }
    }
}

@Composable
fun WriteScreen(controller: HoldController, ports: PocketPorts) {
    val draft = controller.draft
    val background = remember(draft.shot) { draft.shot?.let { decodePocketImage(it) } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            kinds.forEach { item ->
                TextButton(onClick = { draft.kind = item }) { Text(if (item == draft.kind) item.uppercase() else item) }
            }
        }
        OutlinedTextField(draft.title, { draft.title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        if (draft.kind == "note") {
            InkTools(controller.tool, controller.ink, { controller.tool = it }, { controller.ink = it }, {
                draft.strokes = draft.strokes.dropLast(1)
            }, { draft.strokes = emptyList() })
            Row {
                TextButton(onClick = {
                    ports.pickPicture(
                        onBytes = {
                            draft.shot = it
                            draft.shotId = draft.shotId ?: newId()
                        },
                        onError = { controller.status = it },
                    )
                }) { Text(if (draft.shot == null) "Draw on a picture" else "Replace picture") }
            }
            Box(Modifier.weight(1f)) {
                InkPad(draft.strokes, controller.tool, controller.ink, background, { draft.strokes = it }, Modifier.fillMaxSize())
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (draft.kind) {
                    "place" -> PlaceFields(draft.lat, draft.lon, draft.accuracy, draft.address, draft.w3w, ports, controller, onChange = { lat, lon, accuracy, address, w3w ->
                        draft.lat = lat
                        draft.lon = lon
                        draft.accuracy = accuracy
                        draft.address = address
                        draft.w3w = w3w
                    })
                    "contact" -> Text("Write the handle in the details. The chips below file it.", color = HoldThemeColors.muted)
                    else -> OutlinedTextField(
                        draft.extra,
                        { draft.extra = it },
                        label = { Text(if (draft.kind == "radio") "Dump or UID" else if (draft.kind == "number") "Phone" else "Value") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(draft.body, { draft.body = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
                SpotRow("${draft.title}\n${draft.body}\n${draft.extra}", controller)
            }
        }
        if (draft.kind == "note") SpotRow("${draft.title}\n${draft.body}", controller)
        Button(onClick = { fileDraft(controller, ports) }) { Text("File it") }
    }
}

@Composable
private fun SpotRow(text: String, controller: HoldController) {
    val spots = remember(text) { spotText(text) }
    if (spots.isEmpty()) return
    val contacts = controller.visible().filter { it.payload is Payload.Contact }
    Column {
        spots.forEach { spot ->
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    controller.save(computer.kolektiv.hold.parse.filedSpot(spot.kind, spot.value, controller.snapshot.sync.deviceId, controller.draft.title))
                    controller.status = "Filed as ${spot.kind}."
                }) { Text("Save as ${spot.kind}") }
                contacts.filter { contact ->
                    val name = contact.title.trim()
                    name.length >= 3 && text.contains(name, ignoreCase = true)
                }.forEach { contact ->
                    TextButton(onClick = { controller.addHandle(contact.id, spot.kind, spot.value) }) {
                        Text("Add to ${contact.title}")
                    }
                }
            }
        }
    }
}

private fun fileDraft(controller: HoldController, ports: PocketPorts) {
    val draft = controller.draft
    val now = nowMillis()
    val payload = when (draft.kind) {
        "number" -> Payload.Phone(draft.extra.ifBlank { draft.body }.trim())
        "contact" -> {
            val handles = spotText("${draft.title}\n${draft.body}").filter { it.kind != "phone" }.map {
                computer.kolektiv.hold.model.Handle(it.kind, it.value)
            }
            if (handles.isEmpty()) {
                controller.status = "No handle to file. Write a discord, email, or address."
                return
            }
            Payload.Contact(handles)
        }
        "radio" -> {
            val parsed = if (draft.extra.contains(":")) parseFlipper(draft.extra) else null
            Payload.Radio(
                protocol = parsed?.protocol ?: "other",
                uid = parsed?.uid ?: draft.extra.trim(),
                frequencyHz = parsed?.frequencyHz,
                filetype = parsed?.filetype.orEmpty(),
                raw = parsed?.raw ?: draft.extra.trim(),
            )
        }
        "health" -> Payload.Health(
            metric = draft.title.ifBlank { "Reading" },
            value = draft.extra.toDoubleOrNull() ?: 0.0,
            recordedAt = now,
        )
        "place" -> {
            val lat = draft.lat
            val lon = draft.lon
            Payload.Place(
                lat = lat,
                lon = lon,
                accuracyM = draft.accuracy,
                plus = if (lat != null && lon != null) plusCode(lat, lon) else "",
                address = draft.address.trim(),
                w3w = draft.w3w.trim(),
            )
        }
        else -> {
            val shot = draft.shot
            val shotId = draft.shotId
            if (shot != null && shotId != null) ports.saveBlob(shotId, shot)
            Payload.Note(ink = draft.strokes, shotId = shotId)
        }
    }
    if (draft.kind == "note" && draft.title.isBlank() && draft.body.isBlank() && draft.strokes.isEmpty() && draft.shot == null) {
        controller.status = "The page is empty."
        return
    }
    controller.save(
        Entry(
            id = newId(),
            kind = if (draft.kind == "contact") "contact" else draft.kind,
            title = draft.title.ifBlank { defaultTitle(draft.kind, payload) },
            body = draft.body.trim(),
            createdAt = now,
            updatedAt = now,
            deviceId = controller.snapshot.sync.deviceId,
            payload = payload,
        ),
    )
    draft.clear()
    controller.status = "Filed on this device."
}

@Composable
fun DetailScreen(entry: Entry, controller: HoldController, ports: PocketPorts, poster: JsonPoster) {
    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var body by remember(entry.id) { mutableStateOf(entry.body) }
    val note = entry.payload as? Payload.Note
    var strokes by remember(entry.id) { mutableStateOf(note?.ink ?: emptyList()) }
    var shotId by remember(entry.id) { mutableStateOf(note?.shotId) }
    var shot by remember(entry.id) { mutableStateOf(shotId?.let { ports.loadBlob(it) }) }
    val background = remember(shot) { shot?.let { decodePocketImage(it) } }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { controller.selectedId = null }) { Text("Back") }
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        if (note != null) {
            InkTools(controller.tool, controller.ink, { controller.tool = it }, { controller.ink = it }, {
                strokes = strokes.dropLast(1)
                controller.save(entry.copy(title = title.trim(), body = body.trim(), payload = note.copy(ink = strokes, shotId = shotId)), stay = true)
            }, {
                strokes = emptyList()
                controller.save(entry.copy(title = title.trim(), body = body.trim(), payload = note.copy(ink = emptyList(), shotId = shotId)), stay = true)
            })
            TextButton(onClick = {
                ports.pickPicture(onBytes = { bytes ->
                    val id = shotId ?: newId()
                    ports.saveBlob(id, bytes)
                    shot = bytes
                    shotId = id
                    controller.save(entry.copy(title = title.trim(), body = body.trim(), payload = note.copy(ink = strokes, shotId = id)), stay = true)
                }, onError = { controller.status = it })
            }) { Text(if (shot == null) "Draw on a picture" else "Replace picture") }
            Box(Modifier.weight(1f)) {
                InkPad(strokes, controller.tool, controller.ink, background, { next ->
                    strokes = next
                    controller.save(entry.copy(title = title.trim(), body = body.trim(), payload = note.copy(ink = next, shotId = shotId)), stay = true)
                }, Modifier.fillMaxSize())
            }
            TextButton(onClick = {
                scope.launch {
                    controller.status = "Reading the page…"
                    val text = runCatching { readPage(strokes, shot, controller.snapshot.settings.deepseekKey, poster) }.getOrElse { it.message ?: "Couldn't read it." }
                    controller.status = text
                    if (!text.startsWith("DeepSeek") && !text.startsWith("Add a") && !text.startsWith("This build") && !text.startsWith("Couldn't")) {
                        body = text
                    }
                }
            }) { Text("Read the page") }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary(entry), color = HoldThemeColors.muted)
                val place = entry.payload as? Payload.Place
                if (place != null) {
                    PlaceFields(
                        place.lat,
                        place.lon,
                        place.accuracyM,
                        place.address,
                        place.w3w,
                        ports,
                        controller,
                    ) { lat, lon, accuracy, address, w3w ->
                        controller.save(
                            entry.copy(
                                title = title.trim().ifBlank { entry.title },
                                body = body.trim(),
                                payload = place.copy(
                                    lat = lat,
                                    lon = lon,
                                    accuracyM = accuracy,
                                    plus = if (lat != null && lon != null) plusCode(lat, lon) else "",
                                    address = address,
                                    w3w = w3w,
                                ),
                            ),
                            stay = true,
                        )
                    }
                }
                OutlinedTextField(body, { body = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
                SpotRow("$title\n$body", controller)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val payload = if (note != null) note.copy(ink = strokes, shotId = shotId) else entry.payload
                controller.save(entry.copy(title = title.trim().ifBlank { entry.title }, body = body.trim(), payload = payload))
            }) { Text("Save") }
            TextButton(onClick = { controller.delete(entry.id) }) { Text("Delete") }
        }
    }
}

private suspend fun readPage(strokes: List<computer.kolektiv.hold.model.InkStroke>, shot: ByteArray?, key: String, poster: JsonPoster): String {
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return "Add a DeepSeek key in Library first. It stays on this device."
    val jpeg = rasterPocketPage(strokes, shot) ?: return "This build can draw. Reading the page is on the Android build."
    val image = "data:image/jpeg;base64,${Base64.encode(jpeg)}"
    val body = """{"model":"deepseek-flash","messages":[{"role":"user","content":[{"type":"text","text":"Read the handwriting or screenshot. Return only the text you can see. Keep phone numbers and handles exact."},{"type":"image_url","image_url":{"url":"$image"}}]}]}"""
    val raw = io { poster.post("https://api.deepseek.com/chat/completions", body, trimmed) }
    val marker = "\"content\":\""
    val start = raw.indexOf(marker)
    if (start < 0) return "DeepSeek returned nothing."
    return raw.substring(start + marker.length).substringBefore("\"").replace("\\n", "\n")
}

@Composable
private fun PlaceFields(
    lat: Double?,
    lon: Double?,
    accuracy: Double?,
    address: String,
    w3w: String,
    ports: PocketPorts,
    controller: HoldController,
    onChange: (Double?, Double?, Double?, String, String) -> Unit,
) {
    var addressText by remember(address) { mutableStateOf(address) }
    var words by remember(w3w) { mutableStateOf(w3w) }
    val scope = rememberCoroutineScope()
    PlaceMap(lat, lon, ports) { nextLat, nextLon -> onChange(nextLat, nextLon, accuracy, addressText, words) }
    Text(
        if (lat != null && lon != null) "${plusCode(lat, lon)} · $lat, $lon" else "No coordinates yet. The plus code is computed on the device.",
        color = HoldThemeColors.muted,
    )
    var latText by remember(lat) { mutableStateOf(lat?.toString().orEmpty()) }
    var lonText by remember(lon) { mutableStateOf(lon?.toString().orEmpty()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(latText, {
            latText = it
            onChange(it.toDoubleOrNull(), lonText.toDoubleOrNull(), accuracy, addressText, words)
        }, label = { Text("Latitude") }, modifier = Modifier.weight(1f))
        OutlinedTextField(lonText, {
            lonText = it
            onChange(latText.toDoubleOrNull(), it.toDoubleOrNull(), accuracy, addressText, words)
        }, label = { Text("Longitude") }, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            ports.here(
                onFix = { nextLat, nextLon, nextAccuracy -> onChange(nextLat, nextLon, nextAccuracy, addressText, words) },
                onError = { controller.status = it },
            )
        }) { Text("Where I am") }
        TextButton(onClick = {
            if (lat == null || lon == null) {
                controller.status = "Need a position before a street name."
                return@TextButton
            }
            scope.launch {
                val street = runCatching { io { ports.street(lat, lon) } }.getOrNull()
                if (street.isNullOrBlank()) controller.status = "No street name came back. The coordinates are still saved."
                else {
                    addressText = street
                    onChange(lat, lon, accuracy, street, words)
                }
            }
        }) { Text("Closest street") }
        TextButton(onClick = {
            if (lat == null || lon == null) return@TextButton
            scope.launch {
                val count = io {
                    (13..15).flatMap { zoom -> tilesAround(lat, lon, zoom, 768, 768) }.count { ports.fetchTile(it.z, it.x, it.y) != null }
                }
                controller.status = "Kept $count map tiles on this device."
            }
        }) { Text("Keep this area") }
    }
    OutlinedTextField(addressText, {
        addressText = it
        onChange(lat, lon, accuracy, it, words)
    }, label = { Text("Street") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(words, {
        words = it
        onChange(lat, lon, accuracy, addressText, it)
    }, label = { Text("What3words, if you have one") }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PlaceMap(lat: Double?, lon: Double?, ports: PocketPorts, onPick: (Double, Double) -> Unit) {
    var tiles by remember(lat, lon) { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var pan by remember(lat, lon) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(lat, lon) {
        if (lat == null || lon == null) return@LaunchedEffect
        tiles = io {
            tilesAround(lat, lon, 15, 700, 420).mapNotNull { tile ->
                val bytes = ports.fetchTile(tile.z, tile.x, tile.y) ?: return@mapNotNull null
                val image = decodePocketImage(bytes) ?: return@mapNotNull null
                "${tile.x}:${tile.y}" to image
            }.toMap()
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(HoldThemeColors.fg.copy(alpha = 0.08f))
            .pointerInput(lat, lon) {
                if (lat == null || lon == null) return@pointerInput
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        pan += drag
                    },
                    onDragEnd = {
                        val (cx, cy) = project(lat, lon, 15)
                        val (nextLat, nextLon) = unproject(cx - pan.x.toDouble(), cy - pan.y.toDouble(), 15)
                        pan = Offset.Zero
                        onPick(nextLat, nextLon)
                    },
                )
            },
    ) {
        if (lat != null && lon != null) {
            val (cx, cy) = project(lat, lon, 15)
            val originX = cx - size.width / 2 + pan.x
            val originY = cy - size.height / 2 + pan.y
            tiles.forEach { (key, image) ->
                val (x, y) = key.split(":").let { it[0].toInt() to it[1].toInt() }
                val left = (x * 256 - originX).toInt()
                val top = (y * 256 - originY).toInt()
                drawImage(image, dstOffset = IntOffset(left, top), dstSize = IntSize(256, 256))
            }
        }
        drawCircle(HoldThemeColors.accent, radius = 6.dp.toPx(), center = center)
        drawCircle(HoldThemeColors.accent, radius = 14.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun VoiceScreen(controller: HoldController, speech: Speech, ports: PocketPorts) {
    var heard by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Tap once. It will ask for the microphone, record, then write down what it heard.") }
    var listening by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(note)
        Text(heard.ifBlank { "…" })
        Button(onClick = {
            if (listening) {
                listening = false
                note = "Saving…"
                speech.stop { result ->
                    val text = result.transcript.ifBlank { heard }.trim()
                    if (text.isBlank() && result.audio == null) {
                        note = "Nothing to file."
                        return@stop
                    }
                    val now = nowMillis()
                    val id = newId()
                    if (result.audio != null) ports.saveBlob(id, result.audio)
                    controller.save(
                        Entry(
                            id = id,
                            kind = "voice",
                            title = text.ifBlank { "Voice" }.take(80),
                            createdAt = now,
                            updatedAt = now,
                            deviceId = controller.snapshot.sync.deviceId,
                            payload = Payload.Voice(
                                durationMs = result.durationMs,
                                transcript = text,
                                hasAudio = result.audio != null,
                                mime = result.mime,
                            ),
                        ),
                    )
                    note = if (text.isBlank()) "Saved the recording. No transcript came back." else "Saved on this device."
                    heard = ""
                }
            } else {
                listening = true
                heard = ""
                note = "Recording"
                speech.listen(
                    onPartial = {
                        heard = it
                        note = "Recording"
                    },
                    onError = {
                        listening = false
                        note = it
                    },
                )
            }
        }) { Text(if (listening) "Stop" else "Catch") }
        Text("A shortcut can open hold://capture?kind=voice&text= with the dictated line. kind is voice, phone, or discord.", color = HoldThemeColors.muted)
        Text("Hey Siri, save a voice note in my Hold. Hey Siri, save a phone number in my Hold. Hey Siri, save a discord account in my Hold.", color = HoldThemeColors.muted)
    }
}

@Composable
fun LibraryScreen(controller: HoldController, poster: JsonPoster, ports: PocketPorts) {
    val scope = rememberCoroutineScope()
    val settings = controller.snapshot.settings
    var baseUrl by remember(controller.snapshot.sync.baseUrl) { mutableStateOf(controller.snapshot.sync.baseUrl) }
    var token by remember(controller.snapshot.sync.token) { mutableStateOf(controller.snapshot.sync.token) }
    var key by remember(settings.deepseekKey) { mutableStateOf(settings.deepseekKey) }
    val thin = settings.mode == "thin"
    DisposableEffect(settings.follow, thin) {
        if (!settings.follow || thin) return@DisposableEffect onDispose { }
        val stop = ports.watch { lat, lon, accuracy -> controller.addFix(lat, lon, accuracy) }
        onDispose { stop() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (thin) "Thin. Offline on this device. Sync runs only when you tap it." else "Full. Sync still waits until you tap it. Follow can keep a trail.")
        Row {
            TextButton(onClick = {
                controller.updateSettings(PocketSettings(mode = if (thin) "full" else "thin", follow = false, deepseekKey = key.trim()))
            }) { Text(if (thin) "Use full" else "Use thin") }
            if (!thin) {
                TextButton(onClick = {
                    controller.updateSettings(settings.copy(follow = !settings.follow, deepseekKey = key.trim()))
                }) { Text(if (settings.follow) "Stop following" else "Follow me") }
            }
        }
        Text("${controller.snapshot.trail.size} trail points on this device.", color = HoldThemeColors.muted)
        OutlinedTextField(key, { key = it }, label = { Text("DeepSeek key, on this device") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { controller.updateSettings(settings.copy(deepseekKey = key.trim())) }) { Text("Save key") }
        Text("Point sync at a server you host. Nothing leaves until you tap Sync.", color = HoldThemeColors.muted)
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
                    val outcome = runCatching { io { pushSync(snap) { url, body, token -> poster.post(url, body, token) } } }
                    outcome.onSuccess {
                        controller.replace(it.snapshot)
                        controller.status = it.summary
                    }.onFailure {
                        controller.status = it.message ?: "Sync failed."
                    }
                }
            }) { Text(if (thin) "Sync once" else "Sync now") }
        }
        Text("${controller.visible().size} entries on this device.", color = HoldThemeColors.muted)
    }
}
