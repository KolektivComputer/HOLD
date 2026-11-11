package computer.kolektiv.hold.sync

import computer.kolektiv.hold.model.Stamp

fun needsPush(item: Stamp): Boolean {
    if (item.deletedAt != null && item.syncedAt == null) return false
    return item.dirty || item.syncedAt == null
}

data class RemoteApply<T>(val next: List<T>, val removed: List<String>)

fun <T : Stamp> applyRemote(local: List<T>, incoming: List<T>, deletedIds: List<String>, clean: (T) -> T): RemoteApply<T> {
    val map = local.associateBy { it.id }.toMutableMap()
    val removed = mutableListOf<String>()
    fun drop(id: String) {
        val current = map[id]
        if (current == null || !current.dirty) {
            if (current != null) removed += id
            map.remove(id)
        }
    }
    deletedIds.forEach(::drop)
    for (item in incoming) {
        if (item.deletedAt != null) {
            drop(item.id)
            continue
        }
        val current = map[item.id]
        if (current == null || !(current.dirty && current.updatedAt >= item.updatedAt)) {
            map[item.id] = clean(item)
        }
    }
    return RemoteApply(map.values.toList(), removed)
}
