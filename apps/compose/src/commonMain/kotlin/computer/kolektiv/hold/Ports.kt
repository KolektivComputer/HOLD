package computer.kolektiv.hold

interface PocketPorts {
    fun here(onFix: (lat: Double, lon: Double, accuracyM: Double?) -> Unit, onError: (String) -> Unit) {
        onError("This build can't read a position yet. Type the coordinates.")
    }

    fun watch(onFix: (lat: Double, lon: Double, accuracyM: Double?) -> Unit): () -> Unit = { }

    fun pickPicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit, deleteAfter: Boolean = false) {
        onError("This build can't open pictures yet.")
    }

    fun takePicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit, deleteAfter: Boolean = false) {
        onError("This build can't use the camera yet.")
    }

    fun saveBlob(id: String, bytes: ByteArray) = Unit

    fun loadBlob(id: String): ByteArray? = null

    fun fetchTile(z: Int, x: Int, y: Int): ByteArray? = null

    fun street(lat: Double, lon: Double): String? = null

    fun play(id: String, onDone: () -> Unit) {
        onDone()
    }

    fun stopPlay() = Unit

    fun hasOutside(): Boolean = false

    fun openOutside(onReady: () -> Unit, onError: (String) -> Unit) {
        onError("This build can't browse device storage yet.")
    }

    fun outsideList(): List<ShelfNode> = emptyList()

    fun outsideEnter(id: String, name: String) = Unit

    fun outsideUp(): Boolean = false

    fun outsideJump(index: Int) = Unit

    fun outsidePath(): String = ""

    fun outsideRead(id: String): ByteArray? = null

    fun outsideExport(name: String, bytes: ByteArray, onDone: () -> Unit, onError: (String) -> Unit) {
        onError("This build can't export yet.")
    }

    fun resetOutside() = Unit
}

data class ShelfNode(
    val id: String,
    val name: String,
    val directory: Boolean,
    val kind: String,
)

object EmptyPorts : PocketPorts
