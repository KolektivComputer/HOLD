package computer.kolektiv.hold.store

import computer.kolektiv.hold.model.HoldJson
import computer.kolektiv.hold.model.Snapshot

interface HoldRepository {
    fun load(): Snapshot
    fun save(snapshot: Snapshot)
}

class JsonHoldRepository(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) : HoldRepository {
    override fun load(): Snapshot {
        val raw = read()?.takeIf { it.isNotBlank() } ?: return Snapshot()
        return runCatching { HoldJson.decodeFromString<Snapshot>(raw) }.getOrElse { Snapshot() }
    }

    override fun save(snapshot: Snapshot) {
        write(HoldJson.encodeToString(snapshot))
    }
}
