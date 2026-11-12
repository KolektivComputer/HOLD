package computer.kolektiv.hold

import computer.kolektiv.hold.geo.nominatimAddress
import computer.kolektiv.hold.geo.plusCode
import computer.kolektiv.hold.model.HoldJson
import computer.kolektiv.hold.model.Payload
import computer.kolektiv.hold.model.Snapshot
import computer.kolektiv.hold.model.inLast
import computer.kolektiv.hold.model.isExampleSet
import computer.kolektiv.hold.parse.parseMarkdown
import computer.kolektiv.hold.parse.MdBlock
import computer.kolektiv.hold.parse.renderMarkdown
import computer.kolektiv.hold.model.buildSeed
import computer.kolektiv.hold.parse.parseFlipper
import computer.kolektiv.hold.parse.spotText
import computer.kolektiv.hold.parse.parseHealthText
import computer.kolektiv.hold.model.Stamp
import computer.kolektiv.hold.sync.applyRemote
import computer.kolektiv.hold.sync.needsPush
import computer.kolektiv.hold.sync.syncEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class Row(
    override val id: String,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
    override val dirty: Boolean = false,
    override val syncedAt: Long? = 1,
    val body: String = "",
) : Stamp

class HoldTest {
    @Test
    fun plusCodeAndSpots() {
        assertEquals("87JCMQ7M+66", plusCode(42.663, -71.217))
        val spots = spotText("discord: lizzy and me@example.com and 555-014-0188")
        assertEquals(listOf("discord", "email", "phone"), spots.map { it.kind })
        assertEquals("lizzy", spots.first().value)
    }

    @Test
    fun recentWindowAndMarkdown() {
        val now = 20L * 86_400_000L
        assertTrue(inLast(now - 86_400_000L, now, "week"))
        assertFalse(inLast(now - 8L * 86_400_000L, now, "week"))
        val blocks = parseMarkdown("# Gate\n\n- [x] lock\n\n| Name | Code |\n| --- | --- |\n| shed | 12 |\n")
        assertTrue(blocks[0] is MdBlock.Heading)
        assertTrue(blocks[1] is MdBlock.Bullet)
        val table = blocks[2] as MdBlock.Table
        assertEquals(listOf("shed", "12"), table.rows[1])
        val nested = parseMarkdown("- parent\n  - child\n")
        assertEquals(0, (nested[0] as MdBlock.Bullet).depth)
        assertEquals(1, (nested[1] as MdBlock.Bullet).depth)
        assertTrue(renderMarkdown(nested).contains("  - child"))
    }

    @Test
    fun oldNoteJsonStillLoads() {
        val raw = """{"entries":[{"id":"a","kind":"note","title":"Shed","body":"","createdAt":1,"updatedAt":1,"payload":{"kind":"note"}}],"sync":{"deviceId":"d"}}"""
        val snapshot = HoldJson.decodeFromString<Snapshot>(raw)
        val note = snapshot.entries.single().payload as Payload.Note
        assertEquals(0, note.ink.size)
        assertTrue(isExampleSet(buildSeed(1).entries))
        assertEquals("Bridge St", nominatimAddress("""{"display_name":"Bridge St"}"""))
    }

    @Test
    fun flipperNfc() {
        val parsed = parseFlipper("Filetype: Flipper NFC device\nDevice type: NTAG215\nUID: 04 11 22\n")
        assertEquals("nfc", parsed.protocol)
        assertEquals("04 11 22", parsed.uid)
        assertTrue(parsed.title.contains("NTAG215"))
    }

    @Test
    fun flipperSubGhz() {
        val parsed = parseFlipper("Filetype: Flipper SubGhz RAW File\nFrequency: 433920000\nProtocol: RAW\n")
        assertEquals("subghz", parsed.protocol)
        assertEquals(433920000L, parsed.frequencyHz)
    }

    @Test
    fun healthJson() {
        val rows = parseHealthText("""[{"metric":"Heart rate","value":62,"unit":"bpm","recordedAt":"2026-11-10T12:00:00Z"}]""")
        assertEquals(1, rows.size)
        assertEquals(62.0, rows.first().value)
        assertEquals("bpm", rows.first().unit)
    }

    @Test
    fun mergeKeepsDirtyLocal() {
        val local = Row(id = "a", updatedAt = 20, dirty = true, body = "local")
        val remote = Row(id = "a", updatedAt = 10, body = "remote")
        val next = applyRemote(listOf(local), listOf(remote), emptyList()) { it.copy(dirty = false) }
        assertEquals("local", next.next.single().body)
        assertTrue(next.next.single().dirty)
    }

    @Test
    fun mergeTakesRemoteAndDeletes() {
        val local = listOf(Row("a", 5), Row("b", 5))
        val remote = Row("a", 8, body = "server")
        val next = applyRemote(local, listOf(remote), listOf("b")) { it.copy(dirty = false) }
        assertEquals(1, next.next.size)
        assertEquals("server", next.next.single().body)
        assertEquals(listOf("b"), next.removed)
    }

    @Test
    fun neverSyncedDeleteIsNotPushed() {
        assertFalse(needsPush(Row(id = "a", updatedAt = 1, deletedAt = 2, syncedAt = null, dirty = true)))
    }

    @Test
    fun endpoint() {
        assertEquals("https://hold.home/v1/sync", syncEndpoint("https://hold.home"))
        assertEquals("https://hold.home/v1/sync", syncEndpoint("https://hold.home/v1/"))
        assertEquals("http://10.7.0.2:8787/v1/sync", syncEndpoint("http://10.7.0.2:8787/v1/sync"))
    }
}
