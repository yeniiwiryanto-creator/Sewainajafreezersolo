package com.example.ui.components

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RentalTransactionEntity
import com.example.location.GoogleMapTileLoader
import com.example.location.LocationGpsHelper
import com.example.ui.theme.SeaBluePrimary
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Renders real Google Maps Street (`lyrs=m`) & Satellite Hybrid (`lyrs=y`) map tiles
 * natively on a Jetpack Compose Canvas for a single freezer location pin.
 * Supports smooth drag-to-pan, tap-to-place-pin, Zoom In/Out (z=10..19), and center-on-pin.
 */
@Composable
fun GoogleMapsSinglePinTileView(
    latitude: Double,
    longitude: Double,
    isSatellite: Boolean,
    isDraggable: Boolean,
    markerLabel: String,
    onPinMoved: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier,
    liveGpsLatitude: Double? = null,
    liveGpsLongitude: Double? = null,
    liveGpsAccuracyMeters: Float? = null,
    centerTargetLat: Double? = null,
    centerTargetLon: Double? = null,
    centerTriggerTick: Int = 0
) {
    val density = LocalDensity.current.density
    val tileDisplaySizePx = remember(density) { 256f * density.coerceIn(1.35f, 2.25f) }

    var zoom by remember { mutableIntStateOf(16) }
    var cameraLat by remember { mutableDoubleStateOf(latitude) }
    var cameraLon by remember { mutableDoubleStateOf(longitude) }
    var canvasW by remember { mutableFloatStateOf(1f) }
    var canvasH by remember { mutableFloatStateOf(1f) }
    var tileLoadTick by remember { mutableIntStateOf(0) }
    var isDraggingMarker by remember { mutableIntStateOf(0) }

    // Recenter camera when external latitude/longitude changes (unless user is actively dragging marker)
    LaunchedEffect(latitude, longitude) {
        if (isDraggingMarker == 0) {
            cameraLat = latitude
            cameraLon = longitude
        }
    }

    // Explicit CENTER MY LOCATION trigger
    LaunchedEffect(centerTriggerTick) {
        if (centerTriggerTick > 0) {
            cameraLat = centerTargetLat ?: liveGpsLatitude ?: latitude
            cameraLon = centerTargetLon ?: liveGpsLongitude ?: longitude
        }
    }

    Box(
        modifier = modifier
            .background(if (isSatellite) Color(0xFF0E221B) else Color(0xFFF2EFE9))
            .onSizeChanged { sz ->
                canvasW = sz.width.toFloat().coerceAtLeast(1f)
                canvasH = sz.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(isDraggable, zoom, cameraLat, cameraLon, tileDisplaySizePx) {
                detectTapGestures { tapOffset ->
                    if (isDraggable && onPinMoved != null) {
                        val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
                            cameraLat,
                            cameraLon,
                            zoom,
                            tileDisplaySizePx
                        )
                        val topLeftWx = centerWx - canvasW / 2.0
                        val topLeftWy = centerWy - canvasH / 2.0
                        val tappedWx = topLeftWx + tapOffset.x
                        val tappedWy = topLeftWy + tapOffset.y
                        val (newLat, newLon) = GoogleMapTileLoader.worldPxToLatLon(
                            tappedWx,
                            tappedWy,
                            zoom,
                            tileDisplaySizePx
                        )
                        onPinMoved(newLat, newLon)
                    }
                }
            }
            .pointerInput(isDraggable, zoom, latitude, longitude, cameraLat, cameraLon, tileDisplaySizePx) {
                var draggingPinDirectly = false
                detectDragGestures(
                    onDragStart = { startOffset ->
                        if (isDraggable && onPinMoved != null) {
                            val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
                                cameraLat,
                                cameraLon,
                                zoom,
                                tileDisplaySizePx
                            )
                            val topLeftWx = centerWx - canvasW / 2.0
                            val topLeftWy = centerWy - canvasH / 2.0
                            val (pinWx, pinWy) = GoogleMapTileLoader.latLonToWorldPx(
                                latitude,
                                longitude,
                                zoom,
                                tileDisplaySizePx
                            )
                            val pinX = (pinWx - topLeftWx).toFloat()
                            val pinY = (pinWy - topLeftWy).toFloat() - 16f
                            val dist = sqrt((startOffset.x - pinX) * (startOffset.x - pinX) + (startOffset.y - pinY) * (startOffset.y - pinY))
                            draggingPinDirectly = dist <= 64f * density
                            if (draggingPinDirectly) {
                                isDraggingMarker = 1
                            }
                        } else {
                            draggingPinDirectly = false
                        }
                    },
                    onDragEnd = {
                        draggingPinDirectly = false
                        isDraggingMarker = 0
                    },
                    onDragCancel = {
                        draggingPinDirectly = false
                        isDraggingMarker = 0
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (draggingPinDirectly && isDraggable && onPinMoved != null) {
                            val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
                                cameraLat,
                                cameraLon,
                                zoom,
                                tileDisplaySizePx
                            )
                            val topLeftWx = centerWx - canvasW / 2.0
                            val topLeftWy = centerWy - canvasH / 2.0
                            val draggedWx = topLeftWx + change.position.x
                            val draggedWy = topLeftWy + change.position.y
                            val (newLat, newLon) = GoogleMapTileLoader.worldPxToLatLon(
                                draggedWx,
                                draggedWy,
                                zoom,
                                tileDisplaySizePx
                            )
                            onPinMoved(newLat, newLon)
                        } else {
                            val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
                                cameraLat,
                                cameraLon,
                                zoom,
                                tileDisplaySizePx
                            )
                            val (newLat, newLon) = GoogleMapTileLoader.worldPxToLatLon(
                                centerWx - dragAmount.x,
                                centerWy - dragAmount.y,
                                zoom,
                                tileDisplaySizePx
                            )
                            cameraLat = newLat
                            cameraLon = newLon
                        }
                    }
                )
            }
            .testTag("google_maps_sdk_single_view")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read tileLoadTick so Canvas recomposes whenever a new Google Maps tile finishes loading
            val tickRead = tileLoadTick
            if (tickRead >= 0) {
                drawGoogleMapsSlippyTiles(
                    centerLat = cameraLat,
                    centerLon = cameraLon,
                    zoom = zoom,
                    isSatellite = isSatellite,
                    tileDisplaySizePx = tileDisplaySizePx,
                    onTileLoaded = { tileLoadTick++ }
                )
            }

            val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
                cameraLat,
                cameraLon,
                zoom,
                tileDisplaySizePx
            )
            val topLeftWx = centerWx - size.width / 2.0
            val topLeftWy = centerWy - size.height / 2.0

            // 1. Draw GPS CURRENT LOCATION (Blue Live Radar Dot + Accuracy Circle) if available
            if (liveGpsLatitude != null && liveGpsLongitude != null) {
                val (gpsWx, gpsWy) = GoogleMapTileLoader.latLonToWorldPx(
                    liveGpsLatitude,
                    liveGpsLongitude,
                    zoom,
                    tileDisplaySizePx
                )
                val gpsX = (gpsWx - topLeftWx).toFloat()
                val gpsY = (gpsWy - topLeftWy).toFloat()
                val accRadiusDp = ((liveGpsAccuracyMeters ?: 12f).coerceIn(8f, 60f)).dp.toPx()

                // Accuracy halo for GPS CURRENT LOCATION
                drawCircle(
                    color = Color(0xFF0EA5E9).copy(alpha = 0.22f),
                    radius = accRadiusDp,
                    center = Offset(gpsX, gpsY)
                )
                drawCircle(
                    color = Color(0xFF0284C7).copy(alpha = 0.65f),
                    radius = accRadiusDp,
                    center = Offset(gpsX, gpsY),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // White outer ring + Blue live GPS dot
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = Offset(gpsX, gpsY)
                )
                drawCircle(
                    color = Color(0xFF0284C7),
                    radius = 6.5.dp.toPx(),
                    center = Offset(gpsX, gpsY)
                )

                // If GPS CURRENT LOCATION is distinct from CUSTOMER SAVED LOCATION, show a label badge for GPS
                val isDistinctFromPin = kotlin.math.abs(liveGpsLatitude - latitude) > 0.00008 ||
                    kotlin.math.abs(liveGpsLongitude - longitude) > 0.00008
                if (isDistinctFromPin) {
                    drawPinLabelBubble(
                        tipX = gpsX,
                        tipY = gpsY - 12.dp.toPx(),
                        title = "GPS CURRENT LOCATION",
                        subtitle = String.format(Locale.US, "%.5f, %.5f", liveGpsLatitude, liveGpsLongitude),
                        badgeColor = android.graphics.Color.parseColor("#0369A1")
                    )
                }
            }

            // 2. Draw CUSTOMER SAVED LOCATION / Active Customer Marker (Red Teardrop Pin)
            val (pinWx, pinWy) = GoogleMapTileLoader.latLonToWorldPx(
                latitude,
                longitude,
                zoom,
                tileDisplaySizePx
            )
            val pinX = (pinWx - topLeftWx).toFloat()
            val pinY = (pinWy - topLeftWy).toFloat()

            // Subtle target halo around customer marker
            drawCircle(
                color = Color(0xFFEA4335).copy(alpha = 0.16f),
                radius = 22.dp.toPx(),
                center = Offset(pinX, pinY)
            )
            drawCircle(
                color = Color(0xFFEA4335).copy(alpha = 0.50f),
                radius = 22.dp.toPx(),
                center = Offset(pinX, pinY),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Draw authentic Google Maps Red Teardrop Marker
            drawGoogleMapTeardropPin(
                tipX = pinX,
                tipY = pinY,
                pinColor = Color(0xFFEA4335),
                innerDotColor = Color(0xFFB31412),
                scale = 1.15f
            )

            // Draw callout label pill above the pin
            drawPinLabelBubble(
                tipX = pinX,
                tipY = pinY - 38.dp.toPx(),
                title = markerLabel.take(30),
                subtitle = String.format(Locale.US, "%.5f, %.5f", latitude, longitude),
                badgeColor = android.graphics.Color.parseColor("#0F172A")
            )
        }

        // Right-side Google Maps Controls: MyLocation (Center on Live GPS or Pin), Zoom + / -
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(34.dp)
                    .clickable {
                        cameraLat = liveGpsLatitude ?: latitude
                        cameraLon = liveGpsLongitude ?: longitude
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Pusatkan ke Lokasi GPS",
                        tint = Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { zoom = (zoom + 1).coerceAtMost(19) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { zoom = (zoom - 1).coerceAtLeast(10) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // If user panned away and isDraggable is enabled, offer a quick "Pasang Pin di Sini" chip
        if (isDraggable && onPinMoved != null &&
            (kotlin.math.abs(cameraLat - latitude) > 0.00015 || kotlin.math.abs(cameraLon - longitude) > 0.00015)
        ) {
            Surface(
                color = SeaBluePrimary,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clickable {
                        onPinMoved(cameraLat, cameraLon)
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pindahkan Pin ke Tengah",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Google Maps attribution badge in bottom-right
        Surface(
            color = Color.White.copy(alpha = 0.85f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
        ) {
            Text(
                text = "Google Maps • z$zoom",
                color = Color(0xFF334155),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Renders real Google Maps Street (`lyrs=m`) & Satellite Hybrid (`lyrs=y`) map tiles
 * natively on a Jetpack Compose Canvas with multiple interactive freezer rental unit pins across Solo Raya.
 */
@Composable
fun GoogleMapsMultiMarkerTileView(
    validTransactions: List<RentalTransactionEntity>,
    selectedTxId: Long?,
    isSatellite: Boolean,
    onToggleSatellite: () -> Unit,
    onSelectTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val tileDisplaySizePx = remember(density) { 256f * density.coerceIn(1.35f, 2.25f) }

    val allCoords = remember(validTransactions) {
        validTransactions.mapNotNull { tx ->
            if (tx.latitude != null && tx.longitude != null) Pair(tx.latitude, tx.longitude) else null
        }
    }

    val bestFitZoom = remember(allCoords) {
        GoogleMapTileLoader.computeBestFitZoom(allCoords)
    }

    val defaultCenter = remember(allCoords) {
        if (allCoords.isNotEmpty()) {
            Pair(allCoords.map { it.first }.average(), allCoords.map { it.second }.average())
        } else {
            Pair(LocationGpsHelper.SOLO_DEFAULT_LAT, LocationGpsHelper.SOLO_DEFAULT_LON)
        }
    }

    var zoom by remember(bestFitZoom) { mutableIntStateOf(bestFitZoom) }
    var cameraLat by remember(defaultCenter) { mutableDoubleStateOf(defaultCenter.first) }
    var cameraLon by remember(defaultCenter) { mutableDoubleStateOf(defaultCenter.second) }
    var canvasW by remember { mutableFloatStateOf(1f) }
    var canvasH by remember { mutableFloatStateOf(1f) }
    var tileLoadTick by remember { mutableIntStateOf(0) }

    // Center & zoom in on selected freezer unit when selectedTxId changes
    LaunchedEffect(selectedTxId) {
        val target = validTransactions.find { it.id == selectedTxId }
        if (target?.latitude != null && target.longitude != null) {
            cameraLat = target.latitude
            cameraLon = target.longitude
            if (zoom < 15) zoom = 15
        }
    }

    Box(
        modifier = modifier
            .background(if (isSatellite) Color(0xFF0E221B) else Color(0xFFF2EFE9))
            .onSizeChanged { sz ->
                canvasW = sz.width.toFloat().coerceAtLeast(1f)
                canvasH = sz.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(validTransactions, zoom, cameraLat, cameraLon, tileDisplaySizePx) {
                detectTapGestures { tapOffset ->
                    val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
                        cameraLat,
                        cameraLon,
                        zoom,
                        tileDisplaySizePx
                    )
                    val topLeftWx = centerWx - canvasW / 2.0
                    val topLeftWy = centerWy - canvasH / 2.0

                    var closestTx: RentalTransactionEntity? = null
                    var minDistance = Float.MAX_VALUE

                    validTransactions.forEach { tx ->
                        val lat = tx.latitude ?: return@forEach
                        val lon = tx.longitude ?: return@forEach
                        val (mWx, mWy) = GoogleMapTileLoader.latLonToWorldPx(lat, lon, zoom, tileDisplaySizePx)
                        val px = (mWx - topLeftWx).toFloat()
                        val py = (mWy - topLeftWy).toFloat() - 16f
                        val dx = tapOffset.x - px
                        val dy = tapOffset.y - py
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < minDistance) {
                            minDistance = dist
                            closestTx = tx
                        }
                    }

                    if (closestTx != null && minDistance <= 110f) {
                        onSelectTransaction(closestTx!!.id)
                        cameraLat = closestTx!!.latitude ?: cameraLat
                        cameraLon = closestTx!!.longitude ?: cameraLon
                    }
                }
            }
            .pointerInput(zoom, tileDisplaySizePx) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
                        cameraLat,
                        cameraLon,
                        zoom,
                        tileDisplaySizePx
                    )
                    val (newLat, newLon) = GoogleMapTileLoader.worldPxToLatLon(
                        centerWx - dragAmount.x,
                        centerWy - dragAmount.y,
                        zoom,
                        tileDisplaySizePx
                    )
                    cameraLat = newLat
                    cameraLon = newLon
                }
            }
            .testTag("google_maps_sdk_multi_marker_view")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickRead = tileLoadTick
            if (tickRead >= 0) {
                drawGoogleMapsSlippyTiles(
                    centerLat = cameraLat,
                    centerLon = cameraLon,
                    zoom = zoom,
                    isSatellite = isSatellite,
                    tileDisplaySizePx = tileDisplaySizePx,
                    onTileLoaded = { tileLoadTick++ }
                )
            }

            val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
                cameraLat,
                cameraLon,
                zoom,
                tileDisplaySizePx
            )
            val topLeftWx = centerWx - size.width / 2.0
            val topLeftWy = centerWy - size.height / 2.0

            // Draw unselected markers first, then selected marker on top
            val sortedTxs = validTransactions.sortedBy { if (it.id == selectedTxId) 1 else 0 }
            sortedTxs.forEach { tx ->
                val lat = tx.latitude ?: return@forEach
                val lon = tx.longitude ?: return@forEach
                val (mWx, mWy) = GoogleMapTileLoader.latLonToWorldPx(lat, lon, zoom, tileDisplaySizePx)
                val px = (mWx - topLeftWx).toFloat()
                val py = (mWy - topLeftWy).toFloat()

                // Only draw markers within or near the visible viewport
                if (px in -120f..(size.width + 120f) && py in -120f..(size.height + 120f)) {
                    val isSelected = tx.id == selectedTxId
                    val (pinColor, innerColor, badgeHex) = when {
                        isSelected -> Triple(Color(0xFF0284C7), Color(0xFF0369A1), "#0284C7")
                        tx.statusPembayaran.equals("Lunas", ignoreCase = true) ->
                            Triple(Color(0xFF16A34A), Color(0xFF14532D), "#15803D")
                        tx.statusPembayaran.equals("Terlambat", ignoreCase = true) ->
                            Triple(Color(0xFFDC2626), Color(0xFF7F1D1D), "#B91C1C")
                        else -> Triple(Color(0xFFEA580C), Color(0xFF7C2D12), "#C2410C")
                    }

                    if (isSelected) {
                        drawCircle(
                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                            radius = 28.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }

                    drawGoogleMapTeardropPin(
                        tipX = px,
                        tipY = py,
                        pinColor = pinColor,
                        innerDotColor = innerColor,
                        scale = if (isSelected) 1.2f else 1.0f
                    )

                    drawPinLabelBubble(
                        tipX = px,
                        tipY = py - (if (isSelected) 40.dp.toPx() else 33.dp.toPx()),
                        title = "${tx.kodeUnitSnapshot} • ${tx.namaPelangganSnapshot.take(12)}",
                        subtitle = if (isSelected) tx.statusPembayaran else null,
                        badgeColor = android.graphics.Color.parseColor(badgeHex)
                    )
                }
            }
        }

        // Top-right Controls: Semua Unit & Peta Jalan / Satelit
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Surface(
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.clickable {
                    cameraLat = defaultCenter.first
                    cameraLon = defaultCenter.second
                    zoom = bestFitZoom
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "Semua Unit",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Semua (${validTransactions.size})",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }

            Surface(
                color = if (isSatellite) SeaBluePrimary else Color(0xDD0F172A),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.clickable { onToggleSatellite() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Mode Peta",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSatellite) "Satelit" else "Peta Jalan",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Right-side Zoom + / - Controls
        Column(
            verticalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { zoom = (zoom + 1).coerceAtMost(19) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .size(34.dp)
                    .clickable { zoom = (zoom - 1).coerceAtLeast(9) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bottom active unit quick info & Google Maps navigation bar
        val activeUnitTx = validTransactions.find { it.id == selectedTxId } ?: validTransactions.firstOrNull()
        if (activeUnitTx != null) {
            Surface(
                color = Color(0xE6061626),
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable {
                        onSelectTransaction(activeUnitTx.id)
                        cameraLat = activeUnitTx.latitude ?: cameraLat
                        cameraLon = activeUnitTx.longitude ?: cameraLon
                        if (zoom < 15) zoom = 16
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${activeUnitTx.kodeUnitSnapshot} • ${activeUnitTx.namaPelangganSnapshot}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeUnitTx.alamatLokasiGps.ifBlank {
                                String.format(Locale.US, "GPS: %.5f, %.5f", activeUnitTx.latitude ?: 0.0, activeUnitTx.longitude ?: 0.0)
                            },
                            color = Color(0xFFBAE6FD),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = SeaBluePrimary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            val lat = activeUnitTx.latitude ?: LocationGpsHelper.SOLO_DEFAULT_LAT
                            val lon = activeUnitTx.longitude ?: LocationGpsHelper.SOLO_DEFAULT_LON
                            LocationGpsHelper.openTurnByTurnNavigation(context, lat, lon)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = "Navigasi",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Rute",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGoogleMapsSlippyTiles(
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    isSatellite: Boolean,
    tileDisplaySizePx: Float,
    onTileLoaded: () -> Unit
) {
    val w = size.width
    val h = size.height
    val numTiles = 1 shl zoom

    val (centerWx, centerWy) = GoogleMapTileLoader.latLonToWorldPx(
        centerLat,
        centerLon,
        zoom,
        tileDisplaySizePx
    )
    val topLeftWx = centerWx - w / 2.0
    val topLeftWy = centerWy - h / 2.0

    val minTx = floor(topLeftWx / tileDisplaySizePx).toInt()
    val maxTx = floor((topLeftWx + w) / tileDisplaySizePx).toInt()
    val minTy = floor(topLeftWy / tileDisplaySizePx).toInt()
    val maxTy = floor((topLeftWy + h) / tileDisplaySizePx).toInt()

    val tileRenderSize = ceil(tileDisplaySizePx).toInt() + 1
    val gridLineColor = if (isSatellite) Color(0xFF1E3A2F) else Color(0xFFE2DFD7)

    for (tx in minTx..maxTx) {
        for (ty in minTy..maxTy) {
            if (ty < 0 || ty >= numTiles) continue
            val wrappedTx = ((tx % numTiles) + numTiles) % numTiles
            val dstX = (tx * tileDisplaySizePx - topLeftWx).roundToInt()
            val dstY = (ty * tileDisplaySizePx - topLeftWy).roundToInt()

            val cachedBitmap = GoogleMapTileLoader.getCachedTile(isSatellite, zoom, wrappedTx, ty)
            if (cachedBitmap != null) {
                drawImage(
                    image = cachedBitmap,
                    dstOffset = IntOffset(dstX, dstY),
                    dstSize = IntSize(tileRenderSize, tileRenderSize)
                )
            } else {
                // Draw subtle placeholder grid cell while the Google Maps tile finishes downloading
                drawLine(
                    color = gridLineColor,
                    start = Offset(dstX.toFloat(), dstY.toFloat()),
                    end = Offset((dstX + tileRenderSize).toFloat(), dstY.toFloat()),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridLineColor,
                    start = Offset(dstX.toFloat(), dstY.toFloat()),
                    end = Offset(dstX.toFloat(), (dstY + tileRenderSize).toFloat()),
                    strokeWidth = 1f
                )
                GoogleMapTileLoader.requestTile(
                    isSatellite = isSatellite,
                    zoom = zoom,
                    tileX = wrappedTx,
                    tileY = ty,
                    onTileReady = onTileLoaded
                )
            }
        }
    }
}

private fun DrawScope.drawGoogleMapTeardropPin(
    tipX: Float,
    tipY: Float,
    pinColor: Color,
    innerDotColor: Color,
    scale: Float = 1.0f
) {
    val radius = 11.dp.toPx() * scale
    val headCenterY = tipY - (19.dp.toPx() * scale)

    // Ground shadow ellipse
    drawCircle(
        color = Color.Black.copy(alpha = 0.28f),
        radius = 5.dp.toPx() * scale,
        center = Offset(tipX, tipY + 1.dp.toPx())
    )

    // Teardrop cone from head to tip
    val teardropPath = Path().apply {
        moveTo(tipX, tipY)
        lineTo(tipX - radius * 0.85f, headCenterY + radius * 0.42f)
        lineTo(tipX + radius * 0.85f, headCenterY + radius * 0.42f)
        close()
    }
    drawPath(path = teardropPath, color = Color.White)

    // White outer border circle
    drawCircle(
        color = Color.White,
        radius = radius + 1.8.dp.toPx(),
        center = Offset(tipX, headCenterY)
    )

    // Colored inner teardrop cone
    val innerCone = Path().apply {
        moveTo(tipX, tipY - 1.5.dp.toPx())
        lineTo(tipX - radius * 0.78f, headCenterY + radius * 0.38f)
        lineTo(tipX + radius * 0.78f, headCenterY + radius * 0.38f)
        close()
    }
    drawPath(path = innerCone, color = pinColor)

    // Colored pin head
    drawCircle(
        color = pinColor,
        radius = radius,
        center = Offset(tipX, headCenterY)
    )

    // Inner dark dot (classic Google Maps pin center)
    drawCircle(
        color = innerDotColor,
        radius = radius * 0.42f,
        center = Offset(tipX, headCenterY)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.85f),
        radius = radius * 0.22f,
        center = Offset(tipX, headCenterY)
    )
}

private fun DrawScope.drawPinLabelBubble(
    tipX: Float,
    tipY: Float,
    title: String,
    subtitle: String?,
    badgeColor: Int
) {
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 10.5.sp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E2E8F0")
        textSize = 9.sp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = badgeColor
        alpha = 235
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.2.dp.toPx()
    }

    val titleWidth = titlePaint.measureText(title)
    val subWidth = if (!subtitle.isNullOrBlank()) subPaint.measureText(subtitle) else 0f
    val padH = 7.dp.toPx()
    val padV = 4.dp.toPx()
    val boxW = maxOf(titleWidth, subWidth) + padH * 2
    val boxH = if (!subtitle.isNullOrBlank()) 28.dp.toPx() else 19.dp.toPx()

    val left = (tipX - boxW / 2f).coerceIn(4f, (size.width - boxW - 4f).coerceAtLeast(4f))
    val top = (tipY - boxH).coerceAtLeast(4f)
    val rect = RectF(left, top, left + boxW, top + boxH)
    val corner = 6.dp.toPx()

    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(rect, corner, corner, bgPaint)
        drawRoundRect(rect, corner, corner, strokePaint)
        drawText(title, left + padH, top + padV + 9.5.dp.toPx(), titlePaint)
        if (!subtitle.isNullOrBlank()) {
            drawText(subtitle, left + padH, top + padV + 20.dp.toPx(), subPaint)
        }
    }
}
