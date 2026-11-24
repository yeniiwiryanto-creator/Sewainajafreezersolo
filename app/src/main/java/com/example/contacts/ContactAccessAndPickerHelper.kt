package com.example.contacts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.storage.LocalStorageAndBackupManager
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.SmartCaseOutlinedTextField
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SeaBluePrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Status resmi Izin Akses Kontak Telepon Android sesuai spesifikasi:
 * - Belum diizinkan
 * - Diizinkan
 * - Izin ditolak
 */
enum class ContactPermissionState(val displayText: String) {
    BELUM_DIIZINKAN("Belum diizinkan"),
    DIIZINKAN("Diizinkan"),
    IZIN_DITOLAK("Izin ditolak")
}

data class ContactPermissionStatusInfo(
    val state: ContactPermissionState,
    val osPermissionGranted: Boolean,
    val appToggleEnabled: Boolean,
    val wasRevokedFromSystemSettings: Boolean,
    val detailMessage: String
)

data class ImportedContactPhone(
    val label: String,
    val rawNumber: String,
    val normalizedNumber: String
)

data class ImportedContactResult(
    val rawDisplayName: String,
    val displayNameUpper: String,
    val phoneNumbers: List<ImportedContactPhone>
)

/**
 * Utilitas normalisasi nomor telepon & WhatsApp (08..., +62..., 62...)
 * untuk konsistensi penyimpanan & deteksi duplikasi nomor pelanggan.
 */
object PhoneNumberNormalizer {

    fun normalizeWhatsAppNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""

        return when {
            digitsOnly.startsWith("0062") && digitsOnly.length > 4 -> {
                val tail = digitsOnly.removePrefix("0062").dropWhile { it == '0' }
                "0$tail"
            }
            digitsOnly.startsWith("62") && digitsOnly.length >= 10 -> {
                val tail = digitsOnly.removePrefix("62").dropWhile { it == '0' }
                "0$tail"
            }
            digitsOnly.startsWith("0") -> digitsOnly
            digitsOnly.startsWith("8") && digitsOnly.length >= 8 -> "0$digitsOnly"
            hasLeadingPlus -> "+$digitsOnly"
            else -> digitsOnly
        }
    }

    fun areEquivalentWhatsAppNumbers(first: String, second: String): Boolean {
        val normA = normalizeWhatsAppNumber(first)
        val normB = normalizeWhatsAppNumber(second)
        return normA.length >= 8 && normA == normB
    }

    fun formatImportedCustomerName(rawName: String): String {
        return rawName.trim().replace(Regex("\\s+"), " ").uppercase(Locale.getDefault())
    }
}

/**
 * Manajer status izin READ_CONTACTS & pembaca kontak Android secara nyata.
 * - Tidak pernah meminta izin kontak secara otomatis saat aplikasi pertama dibuka.
 * - Mendeteksi pencabutan izin melalui Pengaturan Android pada saat ON_RESUME / re-check.
 */
object ContactAccessManager {

    private const val PREFS_NAME = "saf_soc_contacts_prefs"
    private const val KEY_APP_TOGGLE_ENABLED = "contacts_app_toggle_enabled"
    private const val KEY_OS_DENIED_ONCE = "contacts_os_denied_once"
    private const val KEY_WAS_EVER_GRANTED = "contacts_was_ever_granted"
    private const val KEY_REVOKED_DETECTED = "contacts_revoked_detected"

    fun evaluatePermissionState(context: Context): ContactPermissionStatusInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val osGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val wasEverGranted = prefs.getBoolean(KEY_WAS_EVER_GRANTED, false)
        val deniedOnce = prefs.getBoolean(KEY_OS_DENIED_ONCE, false)

        if (osGranted) {
            // Izin OS aktif; sinkronkan flag bahwa pernah diberikan
            val editor = prefs.edit()
                .putBoolean(KEY_WAS_EVER_GRANTED, true)
                .putBoolean(KEY_OS_DENIED_ONCE, false)
                .putBoolean(KEY_REVOKED_DETECTED, false)
            if (!prefs.contains(KEY_APP_TOGGLE_ENABLED)) {
                editor.putBoolean(KEY_APP_TOGGLE_ENABLED, true)
            }
            editor.apply()

            val toggleOn = prefs.getBoolean(KEY_APP_TOGGLE_ENABLED, true)
            return if (toggleOn) {
                ContactPermissionStatusInfo(
                    state = ContactPermissionState.DIIZINKAN,
                    osPermissionGranted = true,
                    appToggleEnabled = true,
                    wasRevokedFromSystemSettings = false,
                    detailMessage = "Akses kontak telepon aktif. Import nomor WhatsApp siap digunakan."
                )
            } else {
                ContactPermissionStatusInfo(
                    state = ContactPermissionState.BELUM_DIIZINKAN,
                    osPermissionGranted = true,
                    appToggleEnabled = false,
                    wasRevokedFromSystemSettings = false,
                    detailMessage = "Dinonaktifkan sementara melalui Pengaturan Aplikasi. Aktifkan kembali kapan saja."
                )
            }
        }

        // Jika sebelumnya OS pernah mengizinkan lalu dicabut lewat Pengaturan Android
        val revokedNow = wasEverGranted
        if (revokedNow) {
            prefs.edit()
                .putBoolean(KEY_REVOKED_DETECTED, true)
                .putBoolean(KEY_APP_TOGGLE_ENABLED, false)
                .putBoolean(KEY_OS_DENIED_ONCE, true)
                .apply()
            return ContactPermissionStatusInfo(
                state = ContactPermissionState.IZIN_DITOLAK,
                osPermissionGranted = false,
                appToggleEnabled = false,
                wasRevokedFromSystemSettings = true,
                detailMessage = "Izin akses kontak dicabut melalui Pengaturan Android. Anda tetap dapat mengetik nomor WhatsApp secara manual."
            )
        }

        return if (deniedOnce) {
            ContactPermissionStatusInfo(
                state = ContactPermissionState.IZIN_DITOLAK,
                osPermissionGranted = false,
                appToggleEnabled = false,
                wasRevokedFromSystemSettings = false,
                detailMessage = "Izin akses kontak ditolak. Input nomor WhatsApp manual tetap berfungsi normal, atau gunakan Pemilih Kontak Sistem."
            )
        } else {
            ContactPermissionStatusInfo(
                state = ContactPermissionState.BELUM_DIIZINKAN,
                osPermissionGranted = false,
                appToggleEnabled = false,
                wasRevokedFromSystemSettings = false,
                detailMessage = "Belum diizinkan. Izin hanya akan diminta saat Anda mengaktifkan atau menekan ikon Import Kontak."
            )
        }
    }

    fun markPermissionGranted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WAS_EVER_GRANTED, true)
            .putBoolean(KEY_OS_DENIED_ONCE, false)
            .putBoolean(KEY_REVOKED_DETECTED, false)
            .putBoolean(KEY_APP_TOGGLE_ENABLED, true)
            .apply()
    }

    fun markPermissionDenied(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_OS_DENIED_ONCE, true)
            .putBoolean(KEY_APP_TOGGLE_ENABLED, false)
            .apply()
    }

    fun setAppContactsToggleEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_APP_TOGGLE_ENABLED, enabled)
            .apply()
    }

    fun resetForTesting(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun buildSystemPhoneContactPickerIntent(): Intent {
        return Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        }
    }

    /**
     * Membaca kontak langsung dari URI hasil Android Contact Picker (`ACTION_PICK`).
     * Berjalan aman pada `Dispatchers.IO`. Jika `READ_CONTACTS` diberikan dan kontak memiliki
     * lebih dari satu nomor telepon, seluruh nomor untuk `CONTACT_ID` tersebut ikut diambil
     * agar pengguna dapat memilih nomor yang tepat.
     */
    suspend fun extractContactFromPickerUri(
        context: Context,
        contactUri: Uri
    ): ImportedContactResult? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var displayName = ""
            var pickedRawNumber = ""
            var pickedType = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            var pickedCustomLabel: String? = null
            var contactId: Long? = null

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )

            resolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                    val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx).orEmpty()
                    if (numIdx >= 0) pickedRawNumber = cursor.getString(numIdx).orEmpty()
                    if (typeIdx >= 0) pickedType = cursor.getInt(typeIdx)
                    if (labelIdx >= 0) pickedCustomLabel = cursor.getString(labelIdx)
                    if (idIdx >= 0 && !cursor.isNull(idIdx)) contactId = cursor.getLong(idIdx)
                }
            }

            val phones = mutableListOf<ImportedContactPhone>()
            val seenNormalized = mutableSetOf<String>()

            fun addPhone(rawNum: String, type: Int, customLabel: String?) {
                val normalized = PhoneNumberNormalizer.normalizeWhatsAppNumber(rawNum)
                if (normalized.isNotBlank() && seenNormalized.add(normalized)) {
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        type,
                        customLabel
                    ).toString().ifBlank { "Telepon" }
                    phones.add(
                        ImportedContactPhone(
                            label = typeLabel,
                            rawNumber = rawNum.trim(),
                            normalizedNumber = normalized
                        )
                    )
                }
            }

            if (pickedRawNumber.isNotBlank()) {
                addPhone(pickedRawNumber, pickedType, pickedCustomLabel)
            }

            // Jika kontak memiliki banyak nomor & READ_CONTACTS aktif, ambil semua nomor kontak tsb
            val hasReadContacts = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (contactId != null && hasReadContacts) {
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null
                )?.use { allCursor ->
                    while (allCursor.moveToNext()) {
                        val nIdx = allCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val pIdx = allCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val tIdx = allCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                        val lIdx = allCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                        if (displayName.isBlank() && nIdx >= 0) {
                            displayName = allCursor.getString(nIdx).orEmpty()
                        }
                        val raw = if (pIdx >= 0) allCursor.getString(pIdx).orEmpty() else ""
                        val t = if (tIdx >= 0) allCursor.getInt(tIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        val l = if (lIdx >= 0) allCursor.getString(lIdx) else null
                        if (raw.isNotBlank()) addPhone(raw, t, l)
                    }
                }
            }

            if (phones.isEmpty() && displayName.isBlank()) {
                null
            } else {
                ImportedContactResult(
                    rawDisplayName = displayName.trim(),
                    displayNameUpper = PhoneNumberNormalizer.formatImportedCustomerName(displayName),
                    phoneNumbers = phones
                )
            }
        }.getOrNull()
    }

    /**
     * Query fallback daftar kontak ke `ContactsContract` di background thread (`Dispatchers.IO`)
     * saat izin `READ_CONTACTS` aktif (berguna jika perangkat tidak memiliki handler `ACTION_PICK`).
     */
    suspend fun queryDeviceContacts(
        context: Context,
        searchFilter: String = ""
    ): List<ImportedContactResult> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext emptyList()
        }

        runCatching {
            val grouped = linkedMapOf<String, Pair<String, MutableList<ImportedContactPhone>>>()
            val seenPerContact = mutableMapOf<String, MutableSet<String>>()
            val filterLower = searchFilter.trim().lowercase(Locale.getDefault())

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                while (cursor.moveToNext()) {
                    val contactKey = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val rawNum = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                    val normalized = PhoneNumberNormalizer.normalizeWhatsAppNumber(rawNum)
                    if (normalized.isBlank()) continue

                    if (filterLower.isNotBlank() &&
                        !name.lowercase(Locale.getDefault()).contains(filterLower) &&
                        !normalized.contains(filterLower) &&
                        !rawNum.contains(filterLower)
                    ) {
                        continue
                    }

                    val groupKey = contactKey.ifBlank { name.ifBlank { normalized } }
                    val pair = grouped.getOrPut(groupKey) { name to mutableListOf() }
                    val seenNums = seenPerContact.getOrPut(groupKey) { mutableSetOf() }
                    if (seenNums.add(normalized)) {
                        val t = if (typeIdx >= 0) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        val l = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                        val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            t,
                            l
                        ).toString().ifBlank { "Telepon" }
                        pair.second.add(
                            ImportedContactPhone(
                                label = typeLabel,
                                rawNumber = rawNum,
                                normalizedNumber = normalized
                            )
                        )
                    }
                }
            }

            grouped.values.map { (rawName, phones) ->
                ImportedContactResult(
                    rawDisplayName = rawName,
                    displayNameUpper = PhoneNumberNormalizer.formatImportedCustomerName(rawName),
                    phoneNumbers = phones
                )
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * Tombol ikon kontak standar di sebelah kanan kolom input Nomor WhatsApp.
 */
@Composable
fun WhatsAppContactPickerTrailingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Contacts,
            contentDescription = "Import Kontak WhatsApp dari HP",
            tint = SeaBluePrimary
        )
    }
}

/**
 * Orchestrator reusable untuk fitur IMPORT KONTAK WHATSAPP DARI HP.
 * Mendukung:
 * - Android System Contact Picker (`ACTION_PICK`) sebagai prioritas utama
 * - Pemilihan multi-nomor apabila 1 kontak memiliki lebih dari 1 nomor telepon
 * - Fallback in-app contact browser jika picker sistem tidak tersedia & READ_CONTACTS aktif
 * - Fallback manual penuh apabila izin akses kontak ditolak
 */
@Composable
fun rememberWhatsAppContactImporter(
    onContactImported: (normalizedWhatsApp: String, formattedUpperName: String) -> Unit,
    onStatusMessage: (String) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingMultiNumberContact by remember { mutableStateOf<ImportedContactResult?>(null) }
    var showPermissionGuideDialog by remember { mutableStateOf(false) }
    var showInAppContactListDialog by remember { mutableStateOf(false) }
    var inAppContacts by remember { mutableStateOf<List<ImportedContactResult>>(emptyList()) }

    fun handleImportedContact(result: ImportedContactResult?) {
        if (result == null || result.phoneNumbers.isEmpty()) {
            onStatusMessage("Kontak yang dipilih tidak memiliki nomor telepon yang valid. Silakan isi manual.")
            return
        }
        if (result.phoneNumbers.size == 1) {
            val single = result.phoneNumbers.first()
            onContactImported(single.normalizedNumber, result.displayNameUpper)
            onStatusMessage("Kontak diimpor: ${result.displayNameUpper.ifBlank { single.normalizedNumber }} (${single.normalizedNumber})")
        } else {
            pendingMultiNumberContact = result
        }
    }

    val systemContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val dataUri = activityResult.data?.data
        if (dataUri != null) {
            scope.launch {
                val extracted = ContactAccessManager.extractContactFromPickerUri(context, dataUri)
                handleImportedContact(extracted)
            }
        }
    }

    fun launchSystemContactPickerWithFallback() {
        try {
            systemContactPickerLauncher.launch(ContactAccessManager.buildSystemPhoneContactPickerIntent())
        } catch (_: ActivityNotFoundException) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                scope.launch {
                    inAppContacts = ContactAccessManager.queryDeviceContacts(context)
                    showInAppContactListDialog = true
                }
            } else {
                showPermissionGuideDialog = true
            }
        } catch (_: Exception) {
            showPermissionGuideDialog = true
        }
    }

    val readContactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ContactAccessManager.markPermissionGranted(context)
            launchSystemContactPickerWithFallback()
        } else {
            ContactAccessManager.markPermissionDenied(context)
            showPermissionGuideDialog = true
        }
    }

    // Dialog pilih nomor ketika kontak memiliki > 1 nomor telepon
    pendingMultiNumberContact?.let { multi ->
        AlertDialog(
            onDismissRequest = { pendingMultiNumberContact = null },
            title = {
                Text(
                    text = "Pilih Nomor WhatsApp (${multi.displayNameUpper.ifBlank { "Kontak" }})",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Kontak ini memiliki beberapa nomor telepon. Pilih nomor WhatsApp yang akan diimpor:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    multi.phoneNumbers.forEachIndexed { idx, phoneItem ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onContactImported(phoneItem.normalizedNumber, multi.displayNameUpper)
                                    onStatusMessage("Nomor ${phoneItem.normalizedNumber} dipilih untuk ${multi.displayNameUpper}")
                                    pendingMultiNumberContact = null
                                }
                                .testTag("multi_contact_phone_option_$idx"),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                            border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text(
                                            text = phoneItem.normalizedNumber,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = NavyDark
                                        )
                                        Text(
                                            text = "${phoneItem.label} (Asli: ${phoneItem.rawNumber})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = "Pilih", tint = SeaBluePrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingMultiNumberContact = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog panduan / fallback apabila izin ditolak atau fitur kontak sedang nonaktif
    if (showPermissionGuideDialog) {
        val currentInfo = ContactAccessManager.evaluatePermissionState(context)
        AlertDialog(
            onDismissRequest = { showPermissionGuideDialog = false },
            title = { Text("Akses Kontak Telepon (${currentInfo.state.displayText})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = currentInfo.detailMessage,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Anda tetap dapat mengetik nomor WhatsApp secara manual, atau gunakan Pemilih Kontak Sistem untuk memilih satu kontak tanpa izin penuh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NavyDark
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionGuideDialog = false
                        launchSystemContactPickerWithFallback()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary)
                ) {
                    Text("Pemilih Kontak Sistem")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (currentInfo.state == ContactPermissionState.IZIN_DITOLAK) {
                        TextButton(
                            onClick = {
                                showPermissionGuideDialog = false
                                LocalStorageAndBackupManager.openDeviceAppSettings(context)
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pengaturan")
                        }
                    }
                    TextButton(
                        onClick = {
                            showPermissionGuideDialog = false
                            onStatusMessage("Silakan ketik nomor WhatsApp pelanggan secara manual.")
                        }
                    ) {
                        Text("Isi Manual")
                    }
                }
            }
        )
    }

    // Fallback in-app contact browser apabila tidak ada app Contact Picker sistem
    if (showInAppContactListDialog) {
        var filterQuery by remember { mutableStateOf("") }
        val filteredContacts = remember(inAppContacts, filterQuery) {
            if (filterQuery.isBlank()) inAppContacts
            else inAppContacts.filter { c ->
                c.displayNameUpper.contains(filterQuery, ignoreCase = true) ||
                    c.phoneNumbers.any { it.normalizedNumber.contains(filterQuery) }
            }
        }
        AlertDialog(
            onDismissRequest = { showInAppContactListDialog = false },
            title = { Text("Pilih Kontak Telepon") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartCaseOutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text("Cari nama atau nomor...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (filteredContacts.isEmpty()) {
                        Text("Tidak ada kontak dengan nomor telepon yang cocok.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            items(filteredContacts) { item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showInAppContactListDialog = false
                                            handleImportedContact(item)
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Icon(Icons.Default.PersonSearch, contentDescription = null, tint = SeaBluePrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(item.displayNameUpper.ifBlank { "TANPA NAMA" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                item.phoneNumbers.joinToString(" • ") { it.normalizedNumber },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showInAppContactListDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    return remember(context) {
        {
            val statusInfo = ContactAccessManager.evaluatePermissionState(context)
            when (statusInfo.state) {
                ContactPermissionState.DIIZINKAN -> {
                    launchSystemContactPickerWithFallback()
                }
                ContactPermissionState.BELUM_DIIZINKAN -> {
                    if (statusInfo.osPermissionGranted && !statusInfo.appToggleEnabled) {
                        // Pengguna pernah mematikan toggle di Pengaturan Aplikasi -> tanyakan / minta konfirmasi atau buka picker sistem
                        showPermissionGuideDialog = true
                    } else {
                        // Minta izin secara eksplisit hanya ketika pengguna menekan fitur Import Kontak
                        readContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
                ContactPermissionState.IZIN_DITOLAK -> {
                    // Tetap berfungsi dengan System Contact Picker (ACTION_PICK) atau fallback input manual
                    showPermissionGuideDialog = true
                }
            }
        }
    }
}
