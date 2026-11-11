package computer.kolektiv.hold

interface PocketPorts {
    fun here(onFix: (lat: Double, lon: Double, accuracyM: Double?) -> Unit, onError: (String) -> Unit) {
        onError("This build can't read a position yet. Type the coordinates.")
    }

    fun watch(onFix: (lat: Double, lon: Double, accuracyM: Double?) -> Unit): () -> Unit = { }

    fun pickPicture(onBytes: (ByteArray) -> Unit, onError: (String) -> Unit) {
        onError("This build can't open pictures yet.")
    }

    fun saveBlob(id: String, bytes: ByteArray) = Unit

    fun loadBlob(id: String): ByteArray? = null

    fun fetchTile(z: Int, x: Int, y: Int): ByteArray? = null

    fun street(lat: Double, lon: Double): String? = null

    fun play(id: String, onDone: () -> Unit) {
        onDone()
    }

    fun stopPlay() = Unit
}

object EmptyPorts : PocketPorts
