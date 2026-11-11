package computer.kolektiv.hold.model

import kotlinx.serialization.json.Json

val HoldJson = Json {
    classDiscriminator = "kind"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}
