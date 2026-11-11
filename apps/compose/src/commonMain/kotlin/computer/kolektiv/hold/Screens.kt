package computer.kolektiv.hold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import computer.kolektiv.hold.geo.plusCode
import computer.kolektiv.hold.geo.project
import computer.kolektiv.hold.geo.tilesAround
import computer.kolektiv.hold.geo.unproject
import computer.kolektiv.hold.model.Entry
import computer.kolektiv.hold.model.NamedItem
import computer.kolektiv.hold.model.Payload
import computer.kolektiv.hold.model.isExampleSet
import computer.kolektiv.hold.model.medium
import computer.kolektiv.hold.model.newId
import computer.kolektiv.hold.model.nowMillis
import computer.kolektiv.hold.parse.parseFlipper
import computer.kolektiv.hold.parse.spotText
import computer.kolektiv.hold.sync.pushSync
import kotlin.io.encoding.Base64
import kotlinx.coroutines.launch

private val kinds = listOf("note", "number", "contact", "place", "radio", "health")

val LocalSheetDismiss = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun EntrySheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        shown = false
        delay(280)
        onDismiss()
    }
    val offset by animateDpAsState(if (shown) 0.dp else 900.dp, tween(280), label = "sheet")
    HoldBackHandler(enabled = !closing) { closing = true }
    CompositionLocalProvider(LocalSheetDismiss provides { closing = true }) {
        Box(Modifier.fillMaxSize().offset(y = offset).background(HoldThemeColors.bg)) {
            content()
        }
    }
}

@Composable
fun NotePage(
    title: String,
    body: String,
    tagIds: List<String>,
    tags: List<NamedItem>,
    startEditing: Boolean,
    onTags: (List<String>) -> Unit,
    onCreateTag: (String) -> Unit,
    onCommit: (String, String, List<String>) -> Unit,
    onDiscard: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    var editing by remember { mutableStateOf(startEditing) }
    var header by remember { mutableStateOf(title) }
    var rest by remember { mutableStateOf(body) }
    val headerStyle = MaterialTheme.typography.headlineMedium.merge(TextStyle(color = HoldThemeColors.fg))
    val bodyStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = HoldThemeColors.fg))
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (editing && (header.isNotBlank() || rest.isNotBlank())) onCommit(header.trim(), rest.trim(), tagIds)
                onDiscard()
            }) { Icon(HoldIcons.X, "Close", tint = HoldThemeColors.fg) }
            TextButton(onClick = onDiscard) { Text("Discard") }
            Box(Modifier.weight(1f))
            IconButton(onClick = {
                if (editing) {
                    if (header.isNotBlank() || rest.isNotBlank()) onCommit(header.trim(), rest.trim(), tagIds)
                    editing = false
                } else {
                    editing = true
                }
            }) {
                Icon(if (editing) HoldIcons.Check else HoldIcons.Pencil, if (editing) "Done" else "Edit", tint = HoldThemeColors.fg)
            }
        }
        if (editing) {
            TagEditor(tagIds, tags, onTags, onCreateTag)
        } else {
            TagChips(tagIds, tags)
        }
        footer()
        if (editing) {
            BasicTextField(
                value = header,
                onValueChange = { value ->
                    val parts = value.split('\n', limit = 2)
                    header = parts[0]
                    if (parts.size == 2) rest = listOf(parts[1], rest).filter { it.isNotEmpty() }.joinToString("\n")
                },
                textStyle = headerStyle,
                cursorBrush = SolidColor(HoldThemeColors.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (header.isEmpty()) Text("Title", style = headerStyle, color = HoldThemeColors.muted)
                    inner()
                },
            )
            BasicTextField(
                value = rest,
                onValueChange = { rest = it },
                textStyle = bodyStyle,
                cursorBrush = SolidColor(HoldThemeColors.accent),
                modifier = Modifier.fillMaxWidth().weight(1f),
                decorationBox = { inner ->
                    if (rest.isEmpty()) Text("Write", style = bodyStyle, color = HoldThemeColors.muted)
                    inner()
                },
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(header.ifBlank { "New note" }, style = headerStyle)
                if (rest.isNotBlank()) Text(rest, style = bodyStyle)
            }
        }
    }
}

@Composable
private fun TagChips(selected: List<String>, tags: List<NamedItem>) {
    val names = selected.mapNotNull { id -> tags.find { it.id == id && it.deletedAt == null }?.name }
    if (names.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { name ->
            Text(
                name,
                color = HoldThemeColors.bg,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(HoldThemeColors.accent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PhotoSourceDialog(
    onBlank: (() -> Unit)?,
    onCamera: (Boolean) -> Unit,
    onLibrary: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var wipe by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HoldThemeColors.bg,
        title = { Text("Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (onBlank == null) "Take a photo or choose one." else "Start blank, or draw on a photo.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wipe, onCheckedChange = { wipe = it })
                    Text("Delete the original. Only HOLD keeps it.")
                }
            }
        },
        confirmButton = {
            if (onBlank != null) TextButton(onClick = onBlank) { Text("Blank page") }
            else TextButton(onClick = { onCamera(wipe) }) { Text("Camera") }
        },
        dismissButton = {
            Row {
                if (onBlank != null) TextButton(onClick = { onCamera(wipe) }) { Text("Camera") }
                TextButton(onClick = { onLibrary(wipe) }) { Text("Library") }
            }
        },
    )
}

@Composable
fun HoldFab(actions: List<String>, open: Boolean, onToggle: () -> Unit, onPick: (String) -> Unit) {
    val items = listOf(
        Triple("text", HoldIcons.FileText, "Text"),
        Triple("image", HoldIcons.PenLine, "Image"),
        Triple("audio", HoldIcons.Mic, "Audio"),
        Triple("place", HoldIcons.MapPin, "Place"),
        Triple("radio", HoldIcons.Radio, "Radio"),
    ).filter { it.first in actions }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (open) {
            items.forEach { (id, icon, label) ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(HoldThemeColors.fg)
                        .clickable { onPick(id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(icon, label, tint = HoldThemeColors.bg)
                    Text(label, color = HoldThemeColors.bg)
                }
            }
        }
        FloatingActionButton(onClick = onToggle, containerColor = HoldThemeColors.accent) {
            Icon(if (open) HoldIcons.X else HoldIcons.Plus, if (open) "Close" else "New", tint = HoldThemeColors.bg)
        }
    }
}

@Composable
fun LogScreen(controller: HoldController, ports: PocketPorts) {
    var filter by remember { mutableStateOf("all") }
    var playing by remember { mutableStateOf<String?>(null) }
    val rows = controller.visible().filter { filter == "all" || it.medium() == filter }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {
        if (isExampleSet(controller.snapshot.entries)) {
            Text("These six are examples, so the pocket isn't empty.", color = HoldThemeColors.muted, modifier = Modifier.padding(horizontal = 16.dp))
            Button(onClick = { controller.clearExamples() }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Clear examples") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("all", "text", "image", "audio").forEach { item ->
                TextButton(onClick = { filter = item }) { Text(if (item == filter) item.replaceFirstChar { it.uppercase() } else item) }
            }
        }
        if (rows.isEmpty()) Text("Nothing filed on this device.", color = HoldThemeColors.muted, modifier = Modifier.padding(16.dp))
        rows.forEach { entry ->
            val voice = entry.payload as? Payload.Voice
            val names = entry.tagIds.mapNotNull { id -> controller.snapshot.tags.find { it.id == id && it.deletedAt == null }?.name }
            SwipeItem(
                onEdit = { controller.selectedId = entry.id },
                onDelete = { controller.delete(entry.id) },
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = HoldThemeColors.bg),
                    overlineContent = { Text(entry.medium().replaceFirstChar { it.uppercase() }) },
                    headlineContent = { Text(entry.title.ifBlank { "Untitled" }) },
                    supportingContent = {
                        Column {
                            if (voice != null && voice.hasAudio) Waveform(voice.peaks)
                            else {
                                val line = summary(entry)
                                if (line.isNotBlank()) Text(line)
                            }
                            if (names.isNotEmpty()) Text(names.joinToString(" · "), color = HoldThemeColors.accent)
                            if (entry.lat != null) Text("Place saved", color = HoldThemeColors.muted)
                        }
                    },
                    trailingContent = {
                        if (voice?.hasAudio == true) {
                            IconButton(onClick = {
                                if (playing == entry.id) {
                                    ports.stopPlay()
                                    playing = null
                                    return@IconButton
                                }
                                if (ports.loadBlob(entry.id) == null) {
                                    controller.status = "That recording isn't on this device."
                                    return@IconButton
                                }
                                playing = entry.id
                                ports.play(entry.id) { playing = null }
                            }) {
                                Icon(if (playing == entry.id) HoldIcons.Pause else HoldIcons.Play, "Play", tint = HoldThemeColors.fg)
                            }
                        }
                    },
                    modifier = Modifier.clickable { controller.selectedId = entry.id },
                )
            }
            HorizontalDivider(color = HoldThemeColors.fg.copy(alpha = 0.12f))
        }
    }
}

@Composable
private fun SwipeItem(onEdit: () -> Unit, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val reveal = with(density) { 156.dp.toPx() }
    var offset by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Row {
                TextButton(onClick = {
                    offset = 0f
                    onEdit()
                }) { Text("Edit") }
                TextButton(onClick = {
                    offset = 0f
                    onDelete()
                }) { Text("Delete") }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .fillMaxWidth()
                .background(HoldThemeColors.bg)
                .pointerInput(reveal) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, drag -> offset = (offset + drag).coerceIn(-reveal, 0f) },
                        onDragEnd = { offset = if (offset < -reveal / 2f) -reveal else 0f },
                    )
                },
        ) { content() }
    }
}

@Composable
fun WriteScreen(controller: HoldController, ports: PocketPorts) {
    val draft = controller.draft
    val dismiss = LocalSheetDismiss.current
    var askPhoto by remember { mutableStateOf(false) }
    fun receiveShot(bytes: ByteArray) {
        draft.shot = bytes
        draft.shotId = draft.shotId ?: newId()
    }
    LaunchedEffect(controller.shotSource) {
        val source = controller.shotSource
        if (source.isBlank()) return@LaunchedEffect
        controller.shotSource = ""
        if (source == "camera") ports.takePicture(::receiveShot, { controller.status = it }, controller.deleteImport)
        else ports.pickPicture(::receiveShot, { controller.status = it }, controller.deleteImport)
    }
    if (draft.kind == "note" && (draft.medium == "image" || draft.medium == "ink")) {
        val background = remember(draft.shot) { draft.shot?.let { decodePocketImage(it) } }
        key(draft.shotId ?: "ink") {
            InkEditor(
                strokes = draft.strokes,
                tool = controller.tool,
                color = controller.ink,
                background = background,
                tagIds = draft.tagIds,
                tags = controller.snapshot.tags,
                onStrokes = { draft.strokes = it },
                onTool = { controller.tool = it },
                onColor = { controller.ink = it },
                onTags = { draft.tagIds = it },
                onCreateTag = { name ->
                    val id = controller.addTag(name) ?: return@InkEditor
                    if (id !in draft.tagIds) draft.tagIds = draft.tagIds + id
                },
                title = draft.title,
                onTitle = { draft.title = it },
                colors = controller.swatches,
                palettes = controller.snapshot.palettes,
                onSavePalette = { name, colors ->
                    controller.swatches = colors
                    controller.savePalette(name, colors)
                },
                onUsePalette = { controller.swatches = it },
                locate = draft.locate,
                onLocate = { on ->
                    draft.locate = on
                    if (!on) {
                        draft.lat = null
                        draft.lon = null
                        draft.accuracy = null
                    } else {
                        ports.here(
                            { lat, lon, accuracy ->
                                draft.lat = lat
                                draft.lon = lon
                                draft.accuracy = accuracy
                            },
                            { controller.status = it },
                        )
                    }
                },
                onSave = { fileDraft(controller, ports) },
                onDiscard = dismiss,
                onOpenImage = { askPhoto = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (askPhoto) {
            PhotoSourceDialog(onBlank = null, onCamera = { wipe ->
                askPhoto = false
                ports.takePicture(::receiveShot, { controller.status = it }, wipe)
            }, onLibrary = { wipe ->
                askPhoto = false
                ports.pickPicture(::receiveShot, { controller.status = it }, wipe)
            }, onDismiss = { askPhoto = false })
        }
        return
    }
    if (draft.kind == "note") {
        NotePage(
            title = draft.title,
            body = draft.body,
            tagIds = draft.tagIds,
            tags = controller.snapshot.tags,
            startEditing = true,
            onTags = { draft.tagIds = it },
            onCreateTag = { name ->
                val id = controller.addTag(name) ?: return@NotePage
                if (id !in draft.tagIds) draft.tagIds = draft.tagIds + id
            },
            onCommit = { header, rest, tags ->
                draft.title = header
                draft.body = rest
                draft.tagIds = tags
                fileDraft(controller, ports)
            },
            onDiscard = dismiss,
            footer = {
                LocateRow(draft.locate) { on ->
                    draft.locate = on
                    if (!on) {
                        draft.lat = null
                        draft.lon = null
                        draft.accuracy = null
                    } else {
                        ports.here(
                            { lat, lon, accuracy ->
                                draft.lat = lat
                                draft.lon = lon
                                draft.accuracy = accuracy
                            },
                            { controller.status = it },
                        )
                    }
                }
            },
        )
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = dismiss) { Text("Close") }
        TextButton(onClick = dismiss) { Text("Discard") }
        if (draft.kind != "note") {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                kinds.forEach { item ->
                    TextButton(onClick = { draft.kind = item }) { Text(if (item == draft.kind) item.uppercase() else item) }
                }
            }
        }
        OutlinedTextField(draft.title, { draft.title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        when (draft.kind) {
            "place" -> PlaceFields(draft.lat, draft.lon, draft.accuracy, draft.address, draft.w3w, ports, controller, onChange = { lat, lon, accuracy, address, w3w ->
                draft.lat = lat
                draft.lon = lon
                draft.accuracy = accuracy
                draft.address = address
                draft.w3w = w3w
            })
            "contact" -> Text("Write the handle in the details. The chips below file it.", color = HoldThemeColors.muted)
            "radio", "number", "health" -> OutlinedTextField(
                draft.extra,
                { draft.extra = it },
                label = { Text(if (draft.kind == "radio") "Dump or UID" else if (draft.kind == "number") "Phone" else "Value") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(draft.body, { draft.body = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
        TagEditor(draft.tagIds, controller.snapshot.tags, { draft.tagIds = it }) { name ->
            val id = controller.addTag(name) ?: return@TagEditor
            if (id !in draft.tagIds) draft.tagIds = draft.tagIds + id
        }
        SpotRow("${draft.title}\n${draft.body}\n${draft.extra}", controller)
        LocateRow(draft.locate) { on ->
            draft.locate = on
            if (!on) {
                draft.lat = null
                draft.lon = null
                draft.accuracy = null
            } else {
                ports.here(
                    { lat, lon, accuracy ->
                        draft.lat = lat
                        draft.lon = lon
                        draft.accuracy = accuracy
                    },
                    { controller.status = it },
                )
            }
        }
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
            tagIds = draft.tagIds,
            type = if (draft.kind == "note" && (draft.medium == "image" || draft.medium == "ink")) "image" else if (payload is Payload.Voice) "audio" else "text",
            lat = draft.lat,
            lon = draft.lon,
            accuracyM = draft.accuracy,
            payload = payload,
        ),
    )
    draft.clear()
    controller.status = "Filed on this device."
}

@Composable
fun DetailScreen(entry: Entry, controller: HoldController, ports: PocketPorts, poster: JsonPoster, speech: Speech) {
    var title by remember(entry.id) { mutableStateOf(entry.title) }
    var body by remember(entry.id) { mutableStateOf(entry.body) }
    var tagIds by remember(entry.id) { mutableStateOf(entry.tagIds) }
    val note = entry.payload as? Payload.Note
    val voice = entry.payload as? Payload.Voice
    var transcript by remember(entry.id) { mutableStateOf(voice?.transcript.orEmpty()) }
    var strokes by remember(entry.id) { mutableStateOf(note?.ink ?: emptyList()) }
    var shotId by remember(entry.id) { mutableStateOf(note?.shotId) }
    var shot by remember(entry.id) { mutableStateOf(shotId?.let { ports.loadBlob(it) }) }
    var askPhoto by remember(entry.id) { mutableStateOf(false) }
    var playing by remember(entry.id) { mutableStateOf(false) }
    var locate by remember(entry.id) { mutableStateOf(entry.lat != null) }
    var lat by remember(entry.id) { mutableStateOf(entry.lat) }
    var lon by remember(entry.id) { mutableStateOf(entry.lon) }
    var accuracy by remember(entry.id) { mutableStateOf(entry.accuracyM) }
    fun pinPlace(on: Boolean) {
        locate = on
        if (!on) {
            lat = null
            lon = null
            accuracy = null
            return
        }
        ports.here(
            { a, b, c ->
                lat = a
                lon = b
                accuracy = c
            },
            { controller.status = it },
        )
    }
    val background = remember(shot) { shot?.let { decodePocketImage(it) } }
    val scope = rememberCoroutineScope()
    if (entry.medium() == "image" && note != null) {
        key(entry.id) {
            InkEditor(
                strokes = strokes,
                tool = controller.tool,
                color = controller.ink,
                background = background,
                tagIds = tagIds,
                tags = controller.snapshot.tags,
                onStrokes = { strokes = it },
                onTool = { controller.tool = it },
                onColor = { controller.ink = it },
                onTags = { tagIds = it },
                onCreateTag = { name ->
                    val id = controller.addTag(name) ?: return@InkEditor
                    if (id !in tagIds) tagIds = tagIds + id
                },
                title = title,
                onTitle = { title = it },
                colors = controller.swatches,
                palettes = controller.snapshot.palettes,
                onSavePalette = { name, colors ->
                    controller.swatches = colors
                    controller.savePalette(name, colors)
                },
                onUsePalette = { controller.swatches = it },
                locate = locate,
                onLocate = ::pinPlace,
                onSave = {
                    if (shot != null && shotId != null) ports.saveBlob(shotId!!, shot!!)
                    controller.save(
                        entry.copy(
                            title = title.trim().ifBlank { entry.title.ifBlank { "Image" } },
                            body = body.trim(),
                            tagIds = tagIds,
                            type = "image",
                            lat = if (locate) lat else null,
                            lon = if (locate) lon else null,
                            accuracyM = if (locate) accuracy else null,
                            payload = note.copy(ink = strokes, shotId = shotId),
                        ),
                    )
                },
                onDiscard = LocalSheetDismiss.current,
                onOpenImage = { askPhoto = true },
                onRead = {
                    scope.launch {
                        controller.status = "Reading the page…"
                        val text = runCatching { readPage(strokes, shot, controller.snapshot.settings.deepseekKey, poster) }.getOrElse { it.message ?: "Couldn't read it." }
                        controller.status = text
                        if (!text.startsWith("DeepSeek") && !text.startsWith("Add a") && !text.startsWith("This build") && !text.startsWith("Couldn't")) body = text
                    }
                },
            )
        }
        if (askPhoto) {
            PhotoSourceDialog(onBlank = null, onCamera = { wipe ->
                askPhoto = false
                ports.takePicture(onBytes = { bytes ->
                    val id = shotId ?: newId()
                    shot = bytes
                    shotId = id
                }, { controller.status = it }, wipe)
            }, onLibrary = { wipe ->
                askPhoto = false
                ports.pickPicture(onBytes = { bytes ->
                    val id = shotId ?: newId()
                    shot = bytes
                    shotId = id
                }, { controller.status = it }, wipe)
            }, onDismiss = { askPhoto = false })
        }
        return
    }
    if (note != null) {
        NotePage(
            title = title,
            body = body,
            tagIds = tagIds,
            tags = controller.snapshot.tags,
            startEditing = false,
            onTags = { tagIds = it },
            onCreateTag = { name ->
                val id = controller.addTag(name) ?: return@NotePage
                if (id !in tagIds) tagIds = tagIds + id
            },
            onCommit = { header, rest, tags ->
                controller.save(
                    entry.copy(
                        title = header.ifBlank { "Note" },
                        body = rest,
                        tagIds = tags,
                        type = "text",
                        lat = if (locate) lat else null,
                        lon = if (locate) lon else null,
                        accuracyM = if (locate) accuracy else null,
                        payload = note,
                    ),
                    stay = true,
                )
            },
            onDiscard = LocalSheetDismiss.current,
            footer = { LocateRow(locate, ::pinPlace) },
        )
        return
    }
    val dismiss = LocalSheetDismiss.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            TextButton(onClick = dismiss) { Text("Close") }
            TextButton(onClick = dismiss) { Text("Discard") }
        }
        OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        LocateRow(locate, ::pinPlace)
        if (voice != null) {
            Waveform(voice.peaks)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (voice.hasAudio) {
                    IconButton(onClick = {
                        if (playing) {
                            ports.stopPlay()
                            playing = false
                            return@IconButton
                        }
                        if (ports.loadBlob(entry.id) == null) {
                            controller.status = "That recording isn't on this device."
                            return@IconButton
                        }
                        playing = true
                        ports.play(entry.id) { playing = false }
                    }) { Icon(if (playing) HoldIcons.Pause else HoldIcons.Play, "Play", tint = HoldThemeColors.fg) }
                }
                if (!controller.snapshot.settings.autoTranscribe || transcript.isBlank()) {
                    TextButton(onClick = {
                        val bytes = ports.loadBlob(entry.id)
                        if (bytes == null) {
                            controller.status = "That recording isn't on this device."
                            return@TextButton
                        }
                        controller.status = "Playing the clip so this device can hear it."
                        speech.transcribe(bytes, voice.mime, onText = { text ->
                            transcript = text
                            controller.save(
                                entry.copy(
                                    title = text.take(80).ifBlank { title.ifBlank { "Voice" } },
                                    body = body.trim(),
                                    tagIds = tagIds,
                                    type = "audio",
                                    payload = voice.copy(transcript = text),
                                ),
                                stay = true,
                            )
                            controller.status = "Transcript saved."
                        }, onError = { controller.status = it })
                    }) { Text("Transcribe") }
                }
            }
            OutlinedTextField(transcript, { transcript = it }, label = { Text("Transcript") }, modifier = Modifier.fillMaxWidth())
        }
        val place = entry.payload as? Payload.Place
        if (place != null) {
            PlaceFields(place.lat, place.lon, place.accuracyM, place.address, place.w3w, ports, controller) { lat, lon, accuracy, address, w3w ->
                controller.save(
                    entry.copy(
                        title = title.trim().ifBlank { entry.title },
                        body = body.trim(),
                        tagIds = tagIds,
                        type = "text",
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
        if (voice == null) {
            OutlinedTextField(body, { body = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
        }
        TagEditor(tagIds, controller.snapshot.tags, { tagIds = it }) { name ->
            val id = controller.addTag(name) ?: return@TagEditor
            if (id !in tagIds) tagIds = tagIds + id
        }
        if (voice == null) SpotRow("$title\n$body", controller)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val payload = when {
                    voice != null -> voice.copy(transcript = transcript.trim())
                    note != null -> note.copy(ink = strokes, shotId = shotId)
                    else -> entry.payload
                }
                val nextTitle = title.trim().ifBlank { if (voice != null) transcript.trim().take(80) else entry.title }
                controller.save(entry.copy(title = nextTitle.ifBlank { entry.title }, body = body.trim(), tagIds = tagIds, type = entry.medium(), payload = payload))
            }) { Text("Save") }
            TextButton(onClick = { controller.delete(entry.id) }) { Text("Delete") }
            if (note != null && strokes.isNotEmpty()) {
                TextButton(onClick = {
                    scope.launch {
                        controller.status = "Reading the page…"
                        val text = runCatching { readPage(strokes, shot, controller.snapshot.settings.deepseekKey, poster) }.getOrElse { it.message ?: "Couldn't read it." }
                        controller.status = text
                        if (!text.startsWith("DeepSeek") && !text.startsWith("Add a") && !text.startsWith("This build") && !text.startsWith("Couldn't")) body = text
                    }
                }) { Text("Read the page") }
            }
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
    val levels = remember { mutableStateListOf<Float>() }
    var note by remember { mutableStateOf("Record") }
    var listening by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var locate by remember { mutableStateOf(false) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var accuracy by remember { mutableStateOf<Double?>(null) }
    val dismiss = LocalSheetDismiss.current
    fun file(result: CatchResult) {
        val text = result.transcript.ifBlank { heard }.trim()
        if (text.isBlank() && result.audio == null) {
            note = "Nothing to file."
            return
        }
        val now = nowMillis()
        val id = newId()
        if (result.audio != null) ports.saveBlob(id, result.audio)
        controller.save(
            Entry(
                id = id,
                kind = "voice",
                title = text.lineSequence().first().ifBlank { "Voice" }.take(80),
                createdAt = now,
                updatedAt = now,
                deviceId = controller.snapshot.sync.deviceId,
                type = "audio",
                lat = if (locate) lat else null,
                lon = if (locate) lon else null,
                accuracyM = if (locate) accuracy else null,
                payload = Payload.Voice(
                    durationMs = result.durationMs,
                    transcript = text,
                    hasAudio = result.audio != null,
                    mime = result.mime,
                    peaks = packPeaks(result.peaks.ifEmpty { levels.toList() }),
                ),
            ),
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            TextButton(onClick = {
                if (listening) speech.stop { }
                dismiss()
            }) { Text("Close") }
            TextButton(onClick = {
                if (listening) speech.stop { }
                dismiss()
            }) { Text("Discard") }
        }
        LocateRow(locate) { on ->
            locate = on
            if (!on) {
                lat = null
                lon = null
                accuracy = null
            } else {
                ports.here(
                    { a, b, c ->
                        lat = a
                        lon = b
                        accuracy = c
                    },
                    { controller.status = it },
                )
            }
        }
        Text(note, color = HoldThemeColors.muted)
        Waveform(levels, modifier = Modifier.fillMaxWidth())
        if (heard.isNotBlank()) Text(heard.lineSequence().first(), style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!listening) {
                IconButton(onClick = {
                    listening = true
                    paused = false
                    muted = false
                    heard = ""
                    levels.clear()
                    note = "Recording"
                    speech.listen(
                        onPartial = { heard = it },
                        onLevel = { level ->
                            levels.add(level)
                            if (levels.size > 96) levels.removeAt(0)
                        },
                        onError = {
                            listening = false
                            note = it
                        },
                        transcribe = controller.snapshot.settings.autoTranscribe && !muted,
                    )
                }) { Icon(HoldIcons.Mic, "Record", tint = HoldThemeColors.accent, modifier = Modifier.size(48.dp)) }
            } else {
                IconButton(onClick = {
                    paused = !paused
                    if (paused) speech.pause() else speech.resume()
                    note = if (paused) "Paused" else if (muted) "Muted" else "Recording"
                }) { Icon(if (paused) HoldIcons.Play else HoldIcons.Pause, if (paused) "Resume" else "Pause", tint = HoldThemeColors.fg) }
                IconButton(onClick = {
                    muted = !muted
                    speech.setMuted(muted)
                    note = if (muted) "Muted" else if (paused) "Paused" else "Recording"
                }) { Icon(if (muted) HoldIcons.MicOff else HoldIcons.Mic, if (muted) "Unmute" else "Mute", tint = HoldThemeColors.fg) }
                IconButton(onClick = {
                    listening = false
                    note = "Saving…"
                    speech.stop { file(it) }
                }) { Icon(HoldIcons.Square, "Stop", tint = HoldThemeColors.accent) }
            }
        }
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
                controller.updateSettings(settings.copy(mode = if (thin) "full" else "thin", follow = false, deepseekKey = key.trim()))
            }) { Text(if (thin) "Use full" else "Use thin") }
            TextButton(onClick = {
                controller.updateSettings(settings.copy(autoTranscribe = !settings.autoTranscribe, deepseekKey = key.trim()))
            }) { Text(if (settings.autoTranscribe) "Auto transcribe on" else "Auto transcribe off") }
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

private val quickChoices = listOf(
    "text" to "Text",
    "image" to "Image",
    "audio" to "Audio",
    "place" to "Place",
    "radio" to "Radio",
)

@Composable
fun SettingsScreen(controller: HoldController, poster: JsonPoster) {
    val settings = controller.snapshot.settings
    val sync = controller.snapshot.sync
    var baseUrl by remember(sync.baseUrl) { mutableStateOf(sync.baseUrl) }
    var token by remember(sync.token) { mutableStateOf(sync.token) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pocket", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (settings.mode == "thin") "Thin" else "Full", modifier = Modifier.weight(1f))
            TextButton(onClick = {
                controller.updateSettings(settings.copy(mode = if (settings.mode == "thin") "full" else "thin"))
            }) { Text("Switch") }
        }
        Text("Quick list", style = MaterialTheme.typography.titleMedium)
        Text("The new button only shows what you leave on.", color = HoldThemeColors.muted)
        quickChoices.forEach { (id, label) ->
            val on = id in settings.quick
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = on, onCheckedChange = {
                    val next = if (on) settings.quick - id else settings.quick + id
                    controller.updateSettings(settings.copy(quick = if (next.isEmpty()) listOf(id) else next))
                })
                Text(label)
            }
        }
        Text("Sync", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Server") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it }, label = { Text("Token") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            controller.updateSync(sync.copy(baseUrl = baseUrl.trim(), token = token.trim()))
            val snap = controller.snapshot
            scope.launch {
                controller.status = "Syncing…"
                val outcome = runCatching { io { pushSync(snap) { url, body, tok -> poster.post(url, body, tok) } } }
                outcome.onSuccess {
                    controller.replace(it.snapshot)
                    controller.status = it.summary
                }.onFailure { controller.status = it.message ?: "Sync failed." }
            }
        }) { Text("Sync now") }
        Text("Tags", style = MaterialTheme.typography.titleMedium)
        controller.snapshot.tags.filter { it.deletedAt == null }.forEach { tag ->
            if (renaming == tag.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(rename, { rename = it }, modifier = Modifier.weight(1f), singleLine = true)
                    TextButton(onClick = {
                        controller.renameTag(tag.id, rename)
                        renaming = null
                    }) { Text("Save") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tag.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        renaming = tag.id
                        rename = tag.name
                    }) { Text("Rename") }
                    TextButton(onClick = { controller.deleteTag(tag.id) }) { Text("Delete") }
                }
            }
        }
        var fresh by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(fresh, { fresh = it }, label = { Text("New tag") }, modifier = Modifier.weight(1f), singleLine = true)
            TextButton(onClick = {
                controller.addTag(fresh)
                fresh = ""
            }) { Text("Add") }
        }
        Text("Palettes", style = MaterialTheme.typography.titleMedium)
        val palettes = controller.snapshot.palettes.filter { it.deletedAt == null }
        if (palettes.isEmpty()) Text("Save one from the image editor.", color = HoldThemeColors.muted)
        palettes.forEach { palette ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(palette.name, modifier = Modifier.weight(1f))
                Text("${palette.colors.size}", color = HoldThemeColors.muted)
                TextButton(onClick = { controller.deletePalette(palette.id) }) { Text("Delete") }
            }
        }
    }
}
