package com.example.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.util.DateCurrencyUtils
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Representasi pembacaan GPS real-time perangkat (GPS CURRENT LOCATION).
 */
data class LiveGpsReading(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long,
    val formattedTimestamp: String,
    val dateIso: String,
    val timeStr: String,
    val provider: String = "FusedLocationProvider"
)

/**
 * State pemisahan tegas antara:
 * 1. GPS CURRENT LOCATION (posisi aktual perangkat yang bergerak real-time)
 * 2. CUSTOMER SAVED LOCATION (titik lokasi pelanggan yang dikunci/disimpan)
 *
 * Aturan Kritis (Section 4):
 * Perubahan posisi perangkat (GPS CURRENT LOCATION) TIDAK BOLEH otomatis mengubah
 * CUSTOMER SAVED LOCATION yang sudah ditetapkan, digeser manual, atau disimpan.
 */
data class ActiveGpsTaggingState(
    val isGpsEnabled: Boolean = false,
    val hasLocationPermission: Boolean = false,
    // 1. GPS CURRENT LOCATION (Real-Time Device Telemetry)
    val gpsCurrentLat: Double? = null,
    val gpsCurrentLon: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val gpsTimestampMillis: Long? = null,
    val gpsFormattedTimestamp: String = "",
    val gpsCurrentAddress: String = "",
    // 2. Active Marker on Map (can be adjusted manually before/after saving)
    val markerLat: Double = LocationGpsHelper.SOLO_DEFAULT_LAT,
    val markerLon: Double = LocationGpsHelper.SOLO_DEFAULT_LON,
    val markerAddress: String = "",
    // 3. CUSTOMER SAVED LOCATION (Locked / Confirmed Customer Point)
    val customerSavedLat: Double? = null,
    val customerSavedLon: Double? = null,
    val customerSavedAddress: String = "",
    val customerSavedTimestamp: String = "",
    val isCustomerLocationSaved: Boolean = false,
    val isManualAdjustmentActive: Boolean = false,
    val hasAutoPlacedInitialMarker: Boolean = false
) {
    /**
     * Dipanggil saat GPS perangkat mengirim pembaruan posisi real-time.
     * - Selalu memperbarui GPS CURRENT LOCATION (`gpsCurrentLat`, `gpsCurrentLon`, `gpsAccuracyMeters`, `gpsFormattedTimestamp`).
     * - Hanya menempatkan marker secara otomatis pada saat pertama kali (`!hasAutoPlacedInitialMarker`)
     *   DAN jika belum ada lokasi pelanggan yang disimpan (`!isCustomerLocationSaved`) maupun digeser manual (`!isManualAdjustmentActive`).
     * - Ketika perangkat berpindah lokasi setelahnya, `customerSavedLat`/`customerSavedLon` dan `markerLat`/`markerLon`
     *   TIDAK berubah otomatis.
     */
    fun onLiveGpsUpdate(reading: LiveGpsReading, resolvedAddress: String = ""): ActiveGpsTaggingState {
        val shouldAutoPlaceMarker = !hasAutoPlacedInitialMarker &&
            !isCustomerLocationSaved &&
            !isManualAdjustmentActive &&
            customerSavedLat == null &&
            customerSavedLon == null

        return copy(
            isGpsEnabled = true,
            gpsCurrentLat = reading.latitude,
            gpsCurrentLon = reading.longitude,
            gpsAccuracyMeters = reading.accuracyMeters,
            gpsTimestampMillis = reading.timestampMillis,
            gpsFormattedTimestamp = reading.formattedTimestamp,
            gpsCurrentAddress = if (resolvedAddress.isNotBlank()) resolvedAddress else gpsCurrentAddress,
            markerLat = if (shouldAutoPlaceMarker) reading.latitude else markerLat,
            markerLon = if (shouldAutoPlaceMarker) reading.longitude else markerLon,
            markerAddress = if (shouldAutoPlaceMarker && resolvedAddress.isNotBlank()) resolvedAddress else markerAddress,
            hasAutoPlacedInitialMarker = true
        )
    }

    /**
     * Dipanggil saat pengguna menggeser marker atau memilih titik lokasi lain secara manual di peta.
     * Mengunci mode manual sehingga pergerakan GPS perangkat tidak menimpa posisi marker.
     */
    fun onManualMarkerMoved(newLat: Double, newLon: Double, resolvedAddress: String = ""): ActiveGpsTaggingState {
        return copy(
            markerLat = newLat,
            markerLon = newLon,
            markerAddress = if (resolvedAddress.isNotBlank()) resolvedAddress else markerAddress,
            isManualAdjustmentActive = true,
            hasAutoPlacedInitialMarker = true
        )
    }

    /**
     * Dipanggil saat pengguna menekan tombol SIMPAN TITIK LOKASI.
     * Menyimpan latitude & longitude ke CUSTOMER SAVED LOCATION dan menguncinya.
     */
    fun onSaveCustomerLocation(
        savedTimestamp: String = LocationGpsHelper.formatLocationTimestamp(System.currentTimeMillis()),
        resolvedAddress: String = ""
    ): ActiveGpsTaggingState {
        val finalAddr = resolvedAddress.ifBlank { markerAddress.ifBlank { gpsCurrentAddress } }
        return copy(
            customerSavedLat = markerLat,
            customerSavedLon = markerLon,
            customerSavedAddress = finalAddr,
            customerSavedTimestamp = savedTimestamp,
            isCustomerLocationSaved = true,
            hasAutoPlacedInitialMarker = true
        )
    }

    /**
     * Dipanggil hanya ketika pengguna secara eksplisit memilih untuk memindahkan marker ke titik GPS saat ini.
     */
    fun snapMarkerToCurrentGps(): ActiveGpsTaggingState {
        val curLat = gpsCurrentLat ?: return this
        val curLon = gpsCurrentLon ?: return this
        return copy(
            markerLat = curLat,
            markerLon = curLon,
            markerAddress = gpsCurrentAddress.ifBlank { markerAddress },
            isManualAdjustmentActive = false
        )
    }
}

data class GpsLocationResult(
    val isSuccess: Boolean,
    val isGpsDisabled: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val addressText: String = "",
    val provinsi: String = "Jawa Tengah",
    val kabupatenKota: String = "Kota Surakarta",
    val kecamatan: String = "",
    val kelurahanDesa: String = "",
    val kodePos: String = "",
    val googleMapsLink: String = "",
    val dateIso: String = "",
    val timeStr: String = "",
    val errorMessage: String = ""
)

object LocationGpsHelper {

    const val SOLO_DEFAULT_LAT = -7.566620
    const val SOLO_DEFAULT_LON = 110.816650

    fun getGoogleMapsApiKey(): String {
        return try {
            BuildConfig.GOOGLE_MAPS_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    fun isGoogleMapsApiConfigured(): Boolean {
        val key = getGoogleMapsApiKey()
        return key.length >= 30 &&
            key.startsWith("AIza") &&
            key != "MY_GOOGLE_MAPS_API_KEY" &&
            key != "YOUR_API_KEY"
    }

    fun initializeMapsSdk(context: Context): Boolean {
        if (!isGoogleMapsApiConfigured()) return false
        return try {
            com.google.android.gms.maps.MapsInitializer.initialize(context.applicationContext)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun formatGoogleMapsUrl(lat: Double, lon: Double): String {
        return String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lon)
    }

    fun openInGoogleMaps(context: Context, lat: Double, lon: Double, label: String = "Lokasi Freezer") {
        val geoUri = android.net.Uri.parse("geo:$lat,$lon?q=$lat,$lon(${android.net.Uri.encode(label)})")
        val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            val fallbackUri = android.net.Uri.parse("https://maps.google.com/?q=$lat,$lon")
            try {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, fallbackUri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    fun formatOpenStreetMapUrl(lat: Double?, lon: Double?): String {
        return if (lat != null && lon != null) {
            String.format(java.util.Locale.US, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=16/%.6f/%.6f", lat, lon, lat, lon)
        } else {
            ""
        }
    }

    fun openInMapOrBrowser(context: Context, lat: Double, lon: Double, label: String = "Lokasi Freezer") {
        openInGoogleMaps(context, lat, lon, label)
    }

    fun openTurnByTurnNavigation(context: Context, lat: Double, lon: Double) {
        val navUri = android.net.Uri.parse("google.navigation:q=$lat,$lon&mode=d")
        val navIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(navIntent)
        } catch (_: Exception) {
            val fallbackUri = android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon")
            try {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, fallbackUri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Generates HTML for an interactive embedded Google Maps view.
     * Displays Google Maps Street and Satellite tiles, draggable marker, tap-to-pin,
     * and sends coordinate updates directly back to Android Kotlin via AndroidBridge.
     */
    fun generateInteractiveMapHtml(
        lat: Double,
        lon: Double,
        zoom: Int = 16,
        isDraggable: Boolean = true,
        markerLabel: String = "Lokasi Penggunaan Freezer"
    ): String {
        val safeLabel = markerLabel.replace("'", "\\'").replace("\"", "\\\"")
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #0A2540; font-family: sans-serif; overflow: hidden; }
                    #map { width: 100%; height: 100%; }
                    .custom-badge {
                        background: rgba(10, 37, 64, 0.9);
                        color: #fff;
                        padding: 4px 8px;
                        border-radius: 6px;
                        font-size: 11px;
                        font-weight: bold;
                        border: 1px solid #38BDF8;
                    }
                    .custom-pin {
                        width: 32px;
                        height: 32px;
                        background: radial-gradient(circle, #EF4444 60%, #991B1B 100%);
                        border-radius: 50% 50% 50% 0;
                        transform: rotate(-45deg);
                        border: 2px solid #FFFFFF;
                        box-shadow: 0 4px 8px rgba(0,0,0,0.4);
                    }
                    .custom-pin::after {
                        content: '';
                        width: 12px;
                        height: 12px;
                        margin: 8px 0 0 8px;
                        background: #FFFFFF;
                        position: absolute;
                        border-radius: 50%;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var currentLat = $lat;
                    var currentLon = $lon;
                    var isDraggable = $isDraggable;

                    // Google Maps Tile Layers
                    var googleStreets = L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        attribution: 'Google Maps'
                    });

                    var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        attribution: 'Google Maps Satelit'
                    });

                    var osmFallback = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: 'OpenStreetMap'
                    });

                    var map = L.map('map', {
                        center: [currentLat, currentLon],
                        zoom: $zoom,
                        zoomControl: true,
                        layers: [googleStreets]
                    });

                    var baseMaps = {
                        "Peta Jalan": googleStreets,
                        "Satelit": googleHybrid,
                        "OSM Alternatif": osmFallback
                    };
                    L.control.layers(baseMaps, null, { position: 'topright' }).addTo(map);

                    var pinIcon = L.divIcon({
                        className: '',
                        html: '<div class="custom-pin"></div>',
                        iconSize: [32, 32],
                        iconAnchor: [16, 32],
                        popupAnchor: [0, -32]
                    });

                    var marker = L.marker([currentLat, currentLon], {
                        draggable: isDraggable,
                        icon: pinIcon
                    }).addTo(map);

                    marker.bindPopup('<div class="custom-badge">$safeLabel</div>').openPopup();

                    function notifyAndroid(lat, lon) {
                        currentLat = lat;
                        currentLon = lon;
                        if (window.AndroidBridge && window.AndroidBridge.onPinMoved) {
                            window.AndroidBridge.onPinMoved(lat, lon);
                        }
                    }

                    if (isDraggable) {
                        marker.on('dragend', function (e) {
                            var pos = marker.getLatLng();
                            notifyAndroid(pos.lat, pos.lng);
                        });

                        map.on('click', function (e) {
                            marker.setLatLng(e.latlng);
                            notifyAndroid(e.latlng.lat, e.latlng.lng);
                        });
                    }

                    window.updateMarkerLocation = function(newLat, newLon, newZoom) {
                        currentLat = newLat;
                        currentLon = newLon;
                        marker.setLatLng([newLat, newLon]);
                        map.setView([newLat, newLon], newZoom || map.getZoom(), { animate: true });
                    };

                    window.setSatelliteView = function(enabled) {
                        if (enabled) {
                            map.removeLayer(googleStreets);
                            map.addLayer(googleHybrid);
                        } else {
                            map.removeLayer(googleHybrid);
                            map.addLayer(googleStreets);
                        }
                    };
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generates HTML for an interactive multi-marker Google Map
     * showing all active rental freezers scattered across Solo Raya.
     */
    fun generateMultiMarkerMapHtml(
        markersDataJson: String,
        centerLat: Double = SOLO_DEFAULT_LAT,
        centerLon: Double = SOLO_DEFAULT_LON,
        zoom: Int = 12
    ): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #0A2540; font-family: sans-serif; }
                    #map { width: 100%; height: 100%; }
                    .freezer-pin {
                        background: #0284C7;
                        color: #fff;
                        padding: 3px 6px;
                        border-radius: 12px;
                        font-size: 11px;
                        font-weight: bold;
                        border: 2px solid #FFFFFF;
                        white-space: nowrap;
                        box-shadow: 0 2px 6px rgba(0,0,0,0.4);
                        display: inline-block;
                        text-align: center;
                    }
                    .freezer-pin-rented {
                        background: #059669;
                    }
                    .freezer-pin-due {
                        background: #EA580C;
                    }
                    .freezer-pin-overdue {
                        background: #DC2626;
                    }
                    .popup-title { font-weight: bold; color: #0A2540; margin-bottom: 4px; }
                    .popup-btn {
                        background: #0284C7;
                        color: white;
                        padding: 5px 10px;
                        border: none;
                        border-radius: 4px;
                        cursor: pointer;
                        font-size: 11px;
                        margin-top: 6px;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var googleStreets = L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        attribution: 'Google Maps'
                    });
                    var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
                        maxZoom: 20,
                        attribution: 'Google Maps Satelit'
                    });

                    var map = L.map('map', {
                        center: [$centerLat, $centerLon],
                        zoom: $zoom,
                        layers: [googleStreets]
                    });

                    L.control.layers({
                        "Peta Jalan": googleStreets,
                        "Satelit": googleHybrid
                    }, null, { position: 'topright' }).addTo(map);

                    var items = $markersDataJson;
                    var bounds = [];

                    items.forEach(function(item) {
                        if (item.lat && item.lon) {
                            var pinClass = "freezer-pin";
                            if (item.statusPembayaran === "Lunas") {
                                pinClass += " freezer-pin-rented";
                            } else if (item.statusPembayaran === "Terlambat") {
                                pinClass += " freezer-pin-overdue";
                            } else {
                                pinClass += " freezer-pin-due";
                            }

                            var markerIcon = L.divIcon({
                                className: '',
                                html: '<div class="' + pinClass + '">❄ ' + item.kodeUnit + '</div>',
                                iconSize: [70, 26],
                                iconAnchor: [35, 13]
                            });

                            var marker = L.marker([item.lat, item.lon], { icon: markerIcon }).addTo(map);
                            var popupContent = '<div style="min-width:180px;">' +
                                '<div class="popup-title">' + item.kodeUnit + ' - ' + item.namaUnit + '</div>' +
                                '<div><strong>Penyewa:</strong> ' + item.namaPelanggan + '</div>' +
                                '<div><strong>Alamat:</strong> ' + (item.alamat || '-') + '</div>' +
                                '<div><strong>Status:</strong> ' + item.statusPembayaran + '</div>' +
                                '<button class="popup-btn" onclick="selectUnit(' + item.id + ')">Detail Transaksi</button>' +
                                '</div>';
                            marker.bindPopup(popupContent);
                            bounds.push([item.lat, item.lon]);
                        }
                    });

                    if (bounds.length > 0) {
                        map.fitBounds(bounds, { padding: [30, 30], maxZoom: 15 });
                    }

                    function selectUnit(txId) {
                        if (window.AndroidBridge && window.AndroidBridge.onSelectTransaction) {
                            window.AndroidBridge.onSelectTransaction(txId);
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Searches an address using Android Geocoder (B. Integrasi Google Maps - Pencarian alamat).
     * Never generates fake coordinates if the address cannot be resolved or API is not configured.
     */
    @Suppress("DEPRECATION")
    suspend fun searchLocationByAddress(context: Context, query: String): GpsLocationResult = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) {
            return@withContext GpsLocationResult(
                isSuccess = false,
                errorMessage = "Masukkan alamat atau nama lokasi yang ingin dicari."
            )
        }
        try {
            if (!Geocoder.isPresent()) {
                return@withContext GpsLocationResult(
                    isSuccess = false,
                    errorMessage = if (!isGoogleMapsApiConfigured()) {
                        "Layanan Geocoder / Google Maps API belum dikonfigurasi. Silakan masukkan GOOGLE_MAPS_API_KEY pada panel Secrets AI Studio atau gunakan Mode Tagging Manual."
                    } else {
                        "Layanan Geocoder tidak tersedia pada perangkat ini. Gunakan Mode Tagging Manual pada peta."
                    }
                )
            }
            val geocoder = Geocoder(context, Locale("id", "ID"))
            val results = geocoder.getFromLocationName(cleaned, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                val lat = addr.latitude
                val lon = addr.longitude
                val line = addr.getAddressLine(0)?.takeIf { it.isNotBlank() } ?: cleaned
                return@withContext GpsLocationResult(
                    isSuccess = true,
                    latitude = lat,
                    longitude = lon,
                    accuracyMeters = 10.0f,
                    addressText = line,
                    provinsi = addr.adminArea ?: "Jawa Tengah",
                    kabupatenKota = addr.subAdminArea ?: addr.locality ?: "Kota Surakarta",
                    kecamatan = addr.locality ?: "",
                    kelurahanDesa = addr.subLocality ?: "",
                    kodePos = addr.postalCode ?: "",
                    googleMapsLink = formatGoogleMapsUrl(lat, lon),
                    dateIso = DateCurrencyUtils.todayIso(),
                    timeStr = DateCurrencyUtils.nowTime()
                )
            } else {
                return@withContext GpsLocationResult(
                    isSuccess = false,
                    errorMessage = if (!isGoogleMapsApiConfigured()) {
                        "Google Maps API belum dikonfigurasi (GOOGLE_MAPS_API_KEY kosong) dan Geocoder perangkat tidak menemukan hasil untuk '$cleaned'. Koordinat palsu tidak dibuat — silakan gunakan Tagging Manual atau buka aplikasi Google Maps."
                    } else {
                        "Alamat '$cleaned' tidak ditemukan oleh Geocoder. Silakan sesuaikan kata kunci atau geser pin secara manual."
                    }
                )
            }
        } catch (e: Exception) {
            return@withContext GpsLocationResult(
                isSuccess = false,
                errorMessage = if (!isGoogleMapsApiConfigured()) {
                    "Google Maps API belum dikonfigurasi di Secrets AI Studio (${e.localizedMessage ?: "Geocoder offline"}). Gunakan Tagging Manual tanpa membuat koordinat palsu."
                } else {
                    "Pencarian alamat gagal: ${e.localizedMessage ?: "Cek koneksi internet"}"
                }
            )
        }
    }

    /**
     * Captures real GPS coordinates from the smartphone using FusedLocationProviderClient + LocationManager fallback.
     * Includes GPS accuracy in meters and structured reverse geocoding (Provinsi, Kabupaten/Kota, Kecamatan, Kelurahan/Desa, Kode Pos).
     */
    @SuppressLint("MissingPermission")
    suspend fun captureCurrentLocation(context: Context): GpsLocationResult = withContext(Dispatchers.IO) {
        if (!isGpsEnabled(context)) {
            return@withContext GpsLocationResult(
                isSuccess = false,
                isGpsDisabled = true,
                errorMessage = "GPS pada smartphone tidak aktif! Silakan aktifkan layanan Lokasi / GPS di pengaturan HP atau gunakan fitur Tagging Manual pada Peta."
            )
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val loc = suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            } ?: suspendCancellableCoroutine { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }

            if (loc == null) {
                return@withContext GpsLocationResult(
                    isSuccess = false,
                    isGpsDisabled = false,
                    errorMessage = "Sinyal GPS belum ditemukan. Anda dapat mencoba kembali atau menggeser pin lokasi secara manual pada peta di bawah."
                )
            }

            val lat = loc.latitude
            val lon = loc.longitude
            val accuracy = if (loc.hasAccuracy()) loc.accuracy else 6.5f
            val geo = reverseGeocodeStructured(context, lat, lon)

            GpsLocationResult(
                isSuccess = true,
                isGpsDisabled = false,
                latitude = lat,
                longitude = lon,
                accuracyMeters = accuracy,
                addressText = geo.fullAddress,
                provinsi = geo.provinsi,
                kabupatenKota = geo.kabupatenKota,
                kecamatan = geo.kecamatan,
                kelurahanDesa = geo.kelurahanDesa,
                kodePos = geo.kodePos,
                googleMapsLink = formatGoogleMapsUrl(lat, lon),
                dateIso = DateCurrencyUtils.todayIso(),
                timeStr = DateCurrencyUtils.nowTime()
            )
        } catch (e: Exception) {
            GpsLocationResult(
                isSuccess = false,
                isGpsDisabled = false,
                errorMessage = "Gagal mengambil koordinat GPS: ${e.localizedMessage ?: "Error tidak diketahui"}"
            )
        }
    }

    data class StructuredGeocode(
        val fullAddress: String,
        val provinsi: String,
        val kabupatenKota: String,
        val kecamatan: String,
        val kelurahanDesa: String,
        val kodePos: String
    )

    @Suppress("DEPRECATION")
    fun reverseGeocodeStructured(context: Context, lat: Double, lon: Double): StructuredGeocode {
        return try {
            val geocoder = Geocoder(context, Locale("id", "ID"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val line = addr.getAddressLine(0)?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality, addr.subAdminArea)
                        .joinToString(", ")
                        .ifBlank { "Koordinat GPS (${String.format(Locale.US, "%.6f, %.6f", lat, lon)})" }

                StructuredGeocode(
                    fullAddress = line,
                    provinsi = addr.adminArea ?: "Jawa Tengah",
                    kabupatenKota = addr.subAdminArea ?: addr.locality ?: "Kota Surakarta",
                    kecamatan = addr.locality ?: "",
                    kelurahanDesa = addr.subLocality ?: "",
                    kodePos = addr.postalCode ?: ""
                )
            } else {
                StructuredGeocode(
                    fullAddress = "Titik Lokasi GPS (${String.format(Locale.US, "%.6f, %.6f", lat, lon)})",
                    provinsi = "Jawa Tengah",
                    kabupatenKota = "Solo Raya",
                    kecamatan = "",
                    kelurahanDesa = "",
                    kodePos = ""
                )
            }
        } catch (_: Exception) {
            StructuredGeocode(
                fullAddress = "Titik Lokasi GPS (${String.format(Locale.US, "%.6f, %.6f", lat, lon)})",
                provinsi = "Jawa Tengah",
                kabupatenKota = "Solo Raya",
                kecamatan = "",
                kelurahanDesa = "",
                kodePos = ""
            )
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return try {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            fine || coarse
        } catch (_: Throwable) {
            false
        }
    }

    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignore if settings activity is unavailable in test environment
        }
    }

    fun formatLocationTimestamp(epochMillis: Long): String {
        val safeMillis = if (epochMillis > 0L) epochMillis else System.currentTimeMillis()
        return try {
            SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale("id", "ID")).format(Date(safeMillis))
        } catch (_: Exception) {
            "${DateCurrencyUtils.todayIso()} ${DateCurrencyUtils.nowTime()}"
        }
    }

    fun toLiveGpsReading(location: Location, providerLabel: String = location.provider ?: "GPS"): LiveGpsReading {
        val ts = if (location.time > 0L) location.time else System.currentTimeMillis()
        val acc = if (location.hasAccuracy() && location.accuracy > 0f) location.accuracy else 8.5f
        val formatted = formatLocationTimestamp(ts)
        val parts = formatted.split(" ")
        return LiveGpsReading(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = acc,
            timestampMillis = ts,
            formattedTimestamp = formatted,
            dateIso = parts.getOrNull(0) ?: DateCurrencyUtils.todayIso(),
            timeStr = parts.getOrNull(1) ?: DateCurrencyUtils.nowTime(),
            provider = providerLabel
        )
    }

    /**
     * Memulai pemantauan GPS aktif secara real-time menggunakan FusedLocationProviderClient
     * (dengan fallback ke LocationManager) dan mengembalikan fungsi cleanup `() -> Unit`
     * untuk menghentikan pembaruan ketika komponen ditutup.
     */
    @SuppressLint("MissingPermission")
    fun startRealtimeGpsUpdates(
        context: Context,
        intervalMs: Long = 2500L,
        onLocationUpdate: (LiveGpsReading) -> Unit
    ): () -> Unit {
        if (!hasLocationPermission(context)) {
            return {}
        }

        val fusedClient = try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (_: Throwable) {
            null
        }

        val locationManager = try {
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        } catch (_: Throwable) {
            null
        }

        // Ambil lastKnownLocation segera agar peta langsung mengarah ke posisi terkini saat dibuka
        try {
            val gpsLast = if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null
            val netLast = if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else null
            val bestInitial = when {
                gpsLast != null && netLast != null ->
                    if (gpsLast.time >= netLast.time) gpsLast else netLast
                gpsLast != null -> gpsLast
                else -> netLast
            }
            if (bestInitial != null) {
                onLocationUpdate(toLiveGpsReading(bestInitial, bestInitial.provider ?: "LastKnown"))
            }
        } catch (_: Throwable) {
        }

        val fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onLocationUpdate(toLiveGpsReading(loc, "FusedLocationProvider"))
            }
        }

        var fusedRegistered = false
        if (fusedClient != null) {
            try {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                    .setMinUpdateIntervalMillis(1000L)
                    .setMinUpdateDistanceMeters(0.5f)
                    .setWaitForAccurateLocation(false)
                    .build()
                fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
                fusedRegistered = true
            } catch (_: Throwable) {
                fusedRegistered = false
            }
        }

        val platformListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationUpdate(toLiveGpsReading(location, location.provider ?: "GPS_PROVIDER"))
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        var platformRegistered = false
        if (locationManager != null) {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        intervalMs,
                        0.5f,
                        platformListener,
                        Looper.getMainLooper()
                    )
                    platformRegistered = true
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        intervalMs,
                        0.5f,
                        platformListener,
                        Looper.getMainLooper()
                    )
                    platformRegistered = true
                }
            } catch (_: Throwable) {
            }
        }

        return {
            if (fusedRegistered && fusedClient != null) {
                try {
                    fusedClient.removeLocationUpdates(fusedCallback)
                } catch (_: Throwable) {
                }
            }
            if (platformRegistered && locationManager != null) {
                try {
                    locationManager.removeUpdates(platformListener)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
