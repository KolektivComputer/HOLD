package computer.kolektiv.hold.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val ALPHABET = "23456789CFGHJMPQRVWX"
const val TILE_SIZE = 256

fun plusCode(lat: Double, lon: Double): String {
    var latitude = min(90.0, maxOf(-90.0, lat))
    var longitude = lon
    while (longitude < -180) longitude += 360
    while (longitude >= 180) longitude -= 360
    if (latitude == 90.0) latitude = 90.0 - 1e-12
    latitude += 90
    longitude += 180
    val code = StringBuilder()
    var latPlace = 20.0
    var lonPlace = 20.0
    for (pair in 0 until 5) {
        val latDigit = min(19, floor(latitude / latPlace).toInt())
        val lonDigit = min(19, floor(longitude / lonPlace).toInt())
        code.append(ALPHABET[latDigit])
        code.append(ALPHABET[lonDigit])
        latitude -= latDigit * latPlace
        longitude -= lonDigit * lonPlace
        latPlace /= 20
        lonPlace /= 20
        if (pair == 3) code.append('+')
    }
    return code.toString()
}

fun haversineMeters(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = (bLat - aLat) * PI / 180
    val dLon = (bLon - aLon) * PI / 180
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(aLat * PI / 180) * cos(bLat * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * 6_371_000 * asin(min(1.0, sqrt(h)))
}

data class TilePoint(val z: Int, val x: Int, val y: Int)

fun project(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
    val n = (1 shl zoom).toDouble()
    val x = ((lon + 180) / 360) * n * TILE_SIZE
    val sine = sin(lat * PI / 180)
    val y = (0.5 - ln((1 + sine) / (1 - sine)) / (4 * PI)) * n * TILE_SIZE
    return x to y
}

fun unproject(x: Double, y: Double, zoom: Int): Pair<Double, Double> {
    val n = (1 shl zoom).toDouble()
    val lon = (x / (n * TILE_SIZE)) * 360 - 180
    val latRad = atan(sinh(PI * (1 - (2 * y) / (n * TILE_SIZE))))
    return (latRad * 180 / PI) to lon
}

fun tilesAround(lat: Double, lon: Double, zoom: Int, widthPx: Int, heightPx: Int): List<TilePoint> {
    val (cx, cy) = project(lat, lon, zoom)
    val left = cx - widthPx / 2.0
    val top = cy - heightPx / 2.0
    val x0 = floor(left / TILE_SIZE).toInt()
    val y0 = floor(top / TILE_SIZE).toInt()
    val x1 = floor((left + widthPx) / TILE_SIZE).toInt()
    val y1 = floor((top + heightPx) / TILE_SIZE).toInt()
    val tiles = ArrayList<TilePoint>()
    for (x in x0..x1) {
        for (y in y0..y1) tiles += TilePoint(zoom, x, y)
    }
    return tiles
}

fun nominatimAddress(json: String): String? = runCatching {
    Json.parseToJsonElement(json).jsonObject["display_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
}.getOrNull()
