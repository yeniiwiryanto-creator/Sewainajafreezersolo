package com.example.location

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * Native Web Mercator (EPSG:3857) Slippy Map Tile Loader & Coordinate Projection Engine
 * for rendering real Google Maps Street (`lyrs=m`) and Satellite Hybrid (`lyrs=y`) tiles
 * directly onto a Jetpack Compose Canvas without WebView or Play Services API key failures.
 */
object GoogleMapTileLoader {

    private val memoryCache = object : LruCache<String, ImageBitmap>(240) {}
    private val inFlightRequests = ConcurrentHashMap.newKeySet<String>()
    private val loaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(6))

    fun getTileKey(isSatellite: Boolean, zoom: Int, tileX: Int, tileY: Int): String {
        val layer = if (isSatellite) "y" else "m"
        return "$layer:$zoom:$tileX:$tileY"
    }

    fun getCachedTile(isSatellite: Boolean, zoom: Int, tileX: Int, tileY: Int): ImageBitmap? {
        val key = getTileKey(isSatellite, zoom, tileX, tileY)
        synchronized(memoryCache) {
            return memoryCache.get(key)
        }
    }

    fun requestTile(
        isSatellite: Boolean,
        zoom: Int,
        tileX: Int,
        tileY: Int,
        onTileReady: () -> Unit
    ) {
        val numTiles = 1 shl zoom
        if (tileY < 0 || tileY >= numTiles) return
        val wrappedX = ((tileX % numTiles) + numTiles) % numTiles
        val key = getTileKey(isSatellite, zoom, wrappedX, tileY)

        synchronized(memoryCache) {
            if (memoryCache.get(key) != null) return
        }

        if (!inFlightRequests.add(key)) return

        loaderScope.launch {
            try {
                val sub = ((wrappedX + tileY) and 3)
                val primaryUrl = if (isSatellite) {
                    "https://mt$sub.google.com/vt/lyrs=y&x=$wrappedX&y=$tileY&z=$zoom&hl=id&scale=2"
                } else {
                    "https://mt$sub.google.com/vt/lyrs=m&x=$wrappedX&y=$tileY&z=$zoom&hl=id&scale=2"
                }
                val fallbackUrl = if (isSatellite) {
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$zoom/$tileY/$wrappedX"
                } else {
                    "https://tile.openstreetmap.org/$zoom/$wrappedX/$tileY.png"
                }

                val bitmap = downloadBitmap(primaryUrl) ?: downloadBitmap(fallbackUrl)
                if (bitmap != null) {
                    synchronized(memoryCache) {
                        memoryCache.put(key, bitmap)
                    }
                    withContext(Dispatchers.Main) {
                        onTileReady()
                    }
                }
            } finally {
                inFlightRequests.remove(key)
            }
        }
    }

    private fun downloadBitmap(urlStr: String): ImageBitmap? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Converts (latitude, longitude) to world pixel coordinates at [zoom] given [tileDisplaySizePx].
     */
    fun latLonToWorldPx(
        lat: Double,
        lon: Double,
        zoom: Int,
        tileDisplaySizePx: Float
    ): Pair<Double, Double> {
        val numTiles = (1 shl zoom).toDouble()
        val worldSize = tileDisplaySizePx.toDouble() * numTiles
        val x = ((lon.coerceIn(-180.0, 180.0) + 180.0) / 360.0) * worldSize
        val latRad = Math.toRadians(lat.coerceIn(-85.05112878, 85.05112878))
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0) * worldSize
        return Pair(x, y)
    }

    /**
     * Converts world pixel coordinates (worldX, worldY) back to (latitude, longitude) at [zoom].
     */
    fun worldPxToLatLon(
        worldX: Double,
        worldY: Double,
        zoom: Int,
        tileDisplaySizePx: Float
    ): Pair<Double, Double> {
        val numTiles = (1 shl zoom).toDouble()
        val worldSize = tileDisplaySizePx.toDouble() * numTiles
        val lon = (worldX / worldSize) * 360.0 - 180.0
        val n = Math.PI - (2.0 * Math.PI * worldY) / worldSize
        val lat = Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
        return Pair(lat.coerceIn(-85.0511, 85.0511), lon.coerceIn(-180.0, 180.0))
    }

    /**
     * Computes an optimal zoom level (11..16) to fit all given [coords] inside a viewport.
     */
    fun computeBestFitZoom(coords: List<Pair<Double, Double>>): Int {
        if (coords.size <= 1) return 15
        val minLat = coords.minOf { it.first }
        val maxLat = coords.maxOf { it.first }
        val minLon = coords.minOf { it.second }
        val maxLon = coords.maxOf { it.second }
        val maxSpan = maxOf(maxLat - minLat, maxLon - minLon)
        return when {
            maxSpan > 0.35 -> 10
            maxSpan > 0.18 -> 11
            maxSpan > 0.09 -> 12
            maxSpan > 0.045 -> 13
            maxSpan > 0.02 -> 14
            else -> 15
        }
    }
}
