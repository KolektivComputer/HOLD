package computer.kolektiv.hold

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import computer.kolektiv.hold.model.Entry
import computer.kolektiv.hold.model.Payload
import computer.kolektiv.hold.model.medium
import computer.kolektiv.hold.model.newId
import computer.kolektiv.hold.model.nowMillis

@Composable
fun LibraryScreen(controller: HoldController, ports: PocketPorts) {
    var outside by remember { mutableStateOf(false) }
    var generation by remember { mutableStateOf(0) }
    var naming by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf("") }
    var movingEntry by remember { mutableStateOf<String?>(null) }
    var movingFolder by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) {
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { outside = false }) { Text(if (!outside) "HOLD" else "Hold") }
            TextButton(onClick = { outside = true }) { Text(if (outside) "OUTSIDE" else "Outside") }
        }
        if (!outside) HoldShelf(controller, ports, { naming = true }, { id, name ->
            renaming = id
            rename = name
        }, { movingFolder = it }, { movingEntry = it }, playing, { playing = it })
        else OutsideShelf(controller, ports, generation, { generation++ })
    }
    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = HoldThemeColors.bg,
            title = { Text("New folder") },
            text = { OutlinedTextField(folderName, { folderName = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    controller.makeFolder(folderName)
                    folderName = ""
                    naming = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = HoldThemeColors.bg,
            title = { Text("Rename folder") },
            text = { OutlinedTextField(rename, { rename = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    controller.renameFolder(renaming!!, rename)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
    val moving = movingEntry ?: movingFolder
    if (moving != null) {
        val folders = controller.snapshot.folders.filter { it.deletedAt == null }
        AlertDialog(
            onDismissRequest = {
                movingEntry = null
                movingFolder = null
            },
            containerColor = HoldThemeColors.bg,
            title = { Text("Move to") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = {
                        if (movingEntry != null) controller.moveEntry(movingEntry!!, null) else controller.moveFolder(movingFolder!!, null)
                        movingEntry = null
                        movingFolder = null
                    }) { Text("Hold") }
                    folders.forEach { folder ->
                        TextButton(onClick = {
                            if (movingEntry != null) controller.moveEntry(movingEntry!!, folder.id) else controller.moveFolder(movingFolder!!, folder.id)
                            movingEntry = null
                            movingFolder = null
                        }) { Text(folder.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    movingEntry = null
                    movingFolder = null
                }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HoldShelf(
    controller: HoldController,
    ports: PocketPorts,
    onNewFolder: () -> Unit,
    onRename: (String, String) -> Unit,
    onMoveFolder: (String) -> Unit,
    onMoveEntry: (String) -> Unit,
    playing: String?,
    onPlaying: (String?) -> Unit,
) {
    val crumbs = controller.folderCrumbs()
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { controller.folderId = null }) { Text(if (controller.folderId == null) "Hold" else "Hold") }
        crumbs.forEach { folder ->
            Text("/", color = HoldThemeColors.muted)
            TextButton(onClick = { controller.folderId = folder.id }) { Text(folder.name) }
        }
    }
    TextButton(onClick = onNewFolder, modifier = Modifier.padding(horizontal = 8.dp)) { Text("New folder") }
    val folders = controller.foldersHere()
    val entries = controller.entriesHere()
    if (folders.isEmpty() && entries.isEmpty()) {
        Text("Nothing in this folder.", color = HoldThemeColors.muted, modifier = Modifier.padding(16.dp))
    }
    folders.forEach { folder ->
        ShelfRow(folder.name, folder = true, onOpen = { controller.folderId = folder.id }) { close ->
            DropdownMenuItem(text = { Text("Open") }, onClick = {
                close()
                controller.folderId = folder.id
            })
            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                close()
                onRename(folder.id, folder.name)
            })
            DropdownMenuItem(text = { Text("Move") }, onClick = {
                close()
                onMoveFolder(folder.id)
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                close()
                controller.deleteFolder(folder.id)
            })
        }
    }
    entries.forEach { entry ->
        ShelfRow(entry.title.ifBlank { "Untitled" }, folder = false, onOpen = { controller.selectedId = entry.id }) { close ->
            when (entry.medium()) {
                "image" -> DropdownMenuItem(text = { Text("Draw on this") }, onClick = {
                    close()
                    controller.selectedId = entry.id
                })
                "text" -> DropdownMenuItem(text = { Text("Edit") }, onClick = {
                    close()
                    controller.preferEdit = true
                    controller.selectedId = entry.id
                })
                "audio" -> DropdownMenuItem(text = { Text(if (playing == entry.id) "Stop" else "Listen") }, onClick = {
                    close()
                    if (playing == entry.id) {
                        ports.stopPlay()
                        onPlaying(null)
                    } else {
                        onPlaying(entry.id)
                        ports.play(entry.id) { onPlaying(null) }
                    }
                })
            }
            DropdownMenuItem(text = { Text("Open") }, onClick = {
                close()
                controller.selectedId = entry.id
            })
            DropdownMenuItem(text = { Text("Export") }, onClick = {
                close()
                exportEntry(entry, ports, controller)
            })
            DropdownMenuItem(text = { Text("Move") }, onClick = {
                close()
                onMoveEntry(entry.id)
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                close()
                controller.delete(entry.id)
            })
        }
    }
}

@Composable
private fun OutsideShelf(
    controller: HoldController,
    ports: PocketPorts,
    generation: Int,
    bump: () -> Unit,
) {
    val ready = ports.hasOutside()
    Text("Outside is read-only. Import copies into HOLD.", color = HoldThemeColors.muted, modifier = Modifier.padding(horizontal = 16.dp))
    Row(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = {
            ports.openOutside(onReady = {
                ports.resetOutside()
                bump()
            }, onError = { controller.status = it })
        }) { Text(if (ready) "Change folder" else "Open a folder") }
        if (ready) {
            TextButton(onClick = {
                if (ports.outsideUp()) bump()
            }) { Text("Up") }
        }
    }
    if (ready) Text(ports.outsidePath().ifBlank { "Outside" }, color = HoldThemeColors.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    val nodes = remember(generation, ready) { if (ready) ports.outsideList() else emptyList() }
    if (ready && nodes.isEmpty()) Text("This folder is empty.", color = HoldThemeColors.muted, modifier = Modifier.padding(16.dp))
    nodes.forEach { node ->
        ShelfRow(
            node.name,
            folder = node.directory,
            onOpen = {
                if (node.directory) {
                    ports.outsideEnter(node.id, node.name)
                    bump()
                }
            },
            menu = if (node.directory || (node.kind != "image" && node.kind != "text")) null else { close ->
                if (node.kind == "image") {
                    DropdownMenuItem(text = { Text("Import & draw") }, onClick = {
                        close()
                        importImage(node.id, node.name, controller, ports)
                    })
                }
                if (node.kind == "text") {
                    DropdownMenuItem(text = { Text("Import & write") }, onClick = {
                        close()
                        importText(node.id, node.name, controller, ports)
                    })
                }
            },
        )
    }
}

@Composable
private fun ShelfRow(name: String, folder: Boolean, onOpen: () -> Unit, menu: (@Composable (close: () -> Unit) -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (folder) Icon(HoldIcons.Folder, "Folder", tint = HoldThemeColors.fg)
            Text(name, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 14.dp))
            if (menu != null) {
                IconButton(onClick = { open = true }) {
                    Icon(HoldIcons.More, "Menu", tint = HoldThemeColors.fg)
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
                }
            }
        }
        HorizontalDivider(color = HoldThemeColors.fg.copy(alpha = 0.12f))
    }
}

private fun importImage(id: String, name: String, controller: HoldController, ports: PocketPorts) {
    val bytes = ports.outsideRead(id)
    if (bytes == null || bytes.size > 20_000_000) {
        controller.status = "That image could not be copied in."
        return
    }
    val shotId = newId()
    ports.saveBlob(shotId, bytes)
    val now = nowMillis()
    controller.save(
        Entry(
            id = newId(),
            kind = "note",
            title = bare(name),
            createdAt = now,
            updatedAt = now,
            deviceId = controller.snapshot.sync.deviceId,
            folderId = controller.folderId,
            type = "image",
            payload = Payload.Note(shotId = shotId),
        ),
        stay = true,
    )
    controller.status = "Imported into HOLD."
}

private fun importText(id: String, name: String, controller: HoldController, ports: PocketPorts) {
    val bytes = ports.outsideRead(id)
    if (bytes == null || bytes.size > 2_000_000) {
        controller.status = "That note could not be copied in."
        return
    }
    val now = nowMillis()
    controller.preferEdit = true
    controller.save(
        Entry(
            id = newId(),
            kind = "note",
            title = bare(name),
            body = bytes.decodeToString(),
            createdAt = now,
            updatedAt = now,
            deviceId = controller.snapshot.sync.deviceId,
            folderId = controller.folderId,
            type = "text",
            payload = Payload.Note(),
        ),
        stay = true,
    )
    controller.status = "Imported into HOLD."
}

private fun exportEntry(entry: Entry, ports: PocketPorts, controller: HoldController) {
    val note = entry.payload as? Payload.Note
    val voice = entry.payload as? Payload.Voice
    val bytes = when {
        entry.medium() == "image" -> note?.shotId?.let { ports.loadBlob(it) }
        entry.medium() == "audio" && voice?.hasAudio == true -> ports.loadBlob(entry.id)
        else -> null
    }
    if ((entry.medium() == "image" || entry.medium() == "audio") && bytes == null) {
        controller.status = "That file isn't on this device."
        return
    }
    val name = bare(entry.title.ifBlank { "note" })
    val file = when (entry.medium()) {
        "image" -> "$name.jpg" to bytes!!
        "audio" -> "$name.m4a" to bytes!!
        else -> "$name.md" to exportText(entry).encodeToByteArray()
    }
    ports.outsideExport(file.first, file.second, onDone = { controller.status = "Exported ${file.first}." }, onError = { controller.status = it })
}

private fun exportText(entry: Entry): String = when (val payload = entry.payload) {
    is Payload.Voice -> payload.transcript.ifBlank { entry.body }
    is Payload.Phone -> payload.phone
    is Payload.Place -> listOf(payload.address, payload.plus, payload.w3w, entry.body).filter { it.isNotBlank() }.joinToString("\n")
    else -> entry.body.ifBlank { entry.title }
}

private fun bare(name: String): String = name.substringBeforeLast('.').ifBlank { name }.ifBlank { "Note" }
