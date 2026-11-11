package computer.kolektiv.hold.sync

import computer.kolektiv.hold.model.Entry
import computer.kolektiv.hold.model.HoldJson
import computer.kolektiv.hold.model.NamedItem
import computer.kolektiv.hold.model.SavedView
import computer.kolektiv.hold.model.Snapshot
import computer.kolektiv.hold.model.nowMillis
import kotlinx.serialization.Serializable

@Serializable
data class SyncRequest(
    val deviceId: String,
    val deviceName: String,
    val since: String? = null,
    val categories: List<NamedItem> = emptyList(),
    val tags: List<NamedItem> = emptyList(),
    val views: List<SavedView> = emptyList(),
    val upserts: List<Entry> = emptyList(),
    val deleted: List<String> = emptyList(),
)

@Serializable
data class SyncResponse(
    val cursor: String,
    val categories: List<NamedItem> = emptyList(),
    val tags: List<NamedItem> = emptyList(),
    val views: List<SavedView> = emptyList(),
    val upserts: List<Entry> = emptyList(),
    val deleted: List<String> = emptyList(),
)

data class SyncOutcome(val snapshot: Snapshot, val summary: String)

fun pushSync(snapshot: Snapshot, post: (url: String, body: String, token: String) -> String): SyncOutcome {
    val sync = snapshot.sync
    if (sync.baseUrl.isBlank()) error("Set a server address first.")
    val endpoint = syncEndpoint(sync.baseUrl)
    val now = nowMillis()
    val entries = outbound(snapshot.entries)
    val categories = outbound(snapshot.categories)
    val tags = outbound(snapshot.tags)
    val views = outbound(snapshot.views)
    val request = SyncRequest(
        deviceId = sync.deviceId,
        deviceName = sync.deviceName,
        since = sync.lastCursor,
        categories = categories.live,
        tags = tags.live,
        views = views.live,
        upserts = entries.live,
        deleted = entries.deleted + categories.deleted + tags.deleted + views.deleted,
    )
    val raw = post(endpoint, HoldJson.encodeToString(SyncRequest.serializer(), request), sync.token)
    val response = HoldJson.decodeFromString(SyncResponse.serializer(), raw)
    if (response.cursor.isBlank()) error("The server reply had no cursor.")
    val mergedEntries = applyRemote(
        snapshot.entries,
        response.upserts.map { it.copy(dirty = false, syncedAt = now) },
        response.deleted + entries.deleted + entries.dropLocal,
    ) { it.copy(dirty = false) }
    val sentAt = entries.live.associate { it.id to it.updatedAt }
    val cleanedEntries = mergedEntries.next.map { entry ->
        val pushedAt = sentAt[entry.id]
        if (pushedAt != null && entry.updatedAt == pushedAt) entry.copy(dirty = false, syncedAt = now) else entry
    }
    fun <T : computer.kolektiv.hold.model.Stamp> clean(items: List<T>, sent: List<T>, copy: (T, Boolean, Long) -> T): List<T> {
        val times = sent.associate { it.id to it.updatedAt }
        return items.map { item ->
            val pushedAt = times[item.id]
            if (pushedAt != null && item.updatedAt == pushedAt) copy(item, false, now) else item
        }
    }
    val mergedCategories = applyRemote(snapshot.categories, response.categories.map { it.copy(dirty = false, syncedAt = now) }, response.deleted + categories.deleted + categories.dropLocal) { it.copy(dirty = false) }
    val mergedTags = applyRemote(snapshot.tags, response.tags.map { it.copy(dirty = false, syncedAt = now) }, response.deleted + tags.deleted + tags.dropLocal) { it.copy(dirty = false) }
    val mergedViews = applyRemote(snapshot.views, response.views.map { it.copy(dirty = false, syncedAt = now) }, response.deleted + views.deleted + views.dropLocal) { it.copy(dirty = false) }
    val next = snapshot.copy(
        entries = cleanedEntries,
        categories = clean(mergedCategories.next, categories.live) { item, dirty, synced -> item.copy(dirty = dirty, syncedAt = synced) },
        tags = clean(mergedTags.next, tags.live) { item, dirty, synced -> item.copy(dirty = dirty, syncedAt = synced) },
        views = clean(mergedViews.next, views.live) { item, dirty, synced -> item.copy(dirty = dirty, syncedAt = synced) },
        sync = sync.copy(lastCursor = response.cursor, lastSyncAt = now, lastError = null),
    )
    return SyncOutcome(next, "Sent ${entries.live.size}. Received ${response.upserts.size}.")
}

private data class Outbound<T>(val live: List<T>, val deleted: List<String>, val dropLocal: List<String>)

private fun <T : computer.kolektiv.hold.model.Stamp> outbound(items: List<T>) = Outbound(
    live = items.filter { needsPush(it) && it.deletedAt == null },
    deleted = items.filter { it.deletedAt != null && it.dirty && it.syncedAt != null }.map { it.id },
    dropLocal = items.filter { it.deletedAt != null && it.syncedAt == null }.map { it.id },
)
