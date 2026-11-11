package computer.kolektiv.hold.sync

fun syncEndpoint(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "Use an http or https address." }
    val schemeSplit = trimmed.indexOf("://")
    val rest = trimmed.substring(schemeSplit + 3)
    val slash = rest.indexOf('/')
    val origin = if (slash < 0) trimmed else trimmed.substring(0, schemeSplit + 3 + slash)
    val path = if (slash < 0) "" else rest.substring(slash).trimEnd('/')
    return when {
        path.endsWith("/v1/sync") -> origin + path
        path.endsWith("/v1") -> "$origin$path/sync"
        else -> "$origin$path/v1/sync"
    }
}
