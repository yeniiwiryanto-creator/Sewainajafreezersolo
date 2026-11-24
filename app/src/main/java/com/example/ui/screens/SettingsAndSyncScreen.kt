package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.cloud.GoogleAccountInfo
import com.example.data.local.CloudMigrationStateEntity
import com.example.data.local.CloudPhotoJobEntity
import com.example.data.local.CompanyProfile
import com.example.data.local.DEFAULT_PERJANJIAN_SEWA
import com.example.data.local.DEFAULT_SEWA_HAK_MILIK_PERIODS
import com.example.data.local.LocalBackupEntity
import com.example.data.local.PaymentMethodEntity
import com.example.data.local.PdfTemplateSettings
import com.example.data.local.SmartNotificationSettings
import com.example.data.local.SyncLogEntity
import com.example.media.ImageQualitySettings
import com.example.media.ImageStorageAnalytics
import com.example.media.SmartImageStorageOptimizer
import com.example.pdf.PdfTemplateRenderer
import com.example.backup.RestoreMode
import com.example.backup.RestorePreviewSummary
import com.example.data.local.BackupAuditLogEntity
import com.example.data.local.BackupScheduleConfig
import com.example.data.local.DrivePhotoCompressionConfig
import com.example.data.local.DrivePhotoMetadataEntity
import com.example.data.local.DriveStorageSummaryStats
import com.example.drive.DriveBackupFileItem
import com.example.drive.DriveConnectionStatus
import com.example.ui.components.FieldErrorSupportingText
import com.example.ui.components.FormValidationAlertBanner
import com.example.util.FormValidationEngine
import com.example.util.FormValidationResult
import com.example.drive.DriveFolderTreeSummary
import com.example.drive.DriveUploadProgressState
import com.example.sheets.DuplicateImportPolicy
import com.example.sheets.ExcelSheetsEngine
import com.example.sheets.ExcelTemplateAndReportExporter.TemplateType
import com.example.sheets.ImportExecutionReport
import com.example.sheets.SheetImportPreview
import com.example.sheets.SmartImportExecutionReport
import com.example.sheets.SmartImportPreviewState
import com.example.sheets.SmartImportTarget
import com.example.storage.DeviceStorageSummary
import com.example.storage.LocalStorageAndBackupManager
import com.example.storage.ManagedDocumentCategory
import com.example.storage.PermissionCategoryStatus
import com.example.storage.StorageIntegrityReport
import com.example.storage.UnifiedManagedDocumentItem
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.components.BackupAndRestoreSettingsCard
import com.example.ui.components.CloudAccountAndSyncSection
import com.example.ui.components.DataManagementHubCard
import com.example.ui.components.DevicePermissionsSettingsCard
import com.example.ui.components.DigitalSignaturePad
import com.example.ui.components.DrivePhotoStorageSettingsCard
import com.example.ui.components.ImageQualitySettingsCard
import com.example.ui.components.InputTextCaseMode
import com.example.ui.components.SmartCaseOutlinedTextField
import com.example.data.local.DataConsistencyAuditReport
import com.example.data.local.DriveMirrorDashboardState
import com.example.data.local.SyncTechnicalLogEntity
import com.example.ui.components.CustomizeAppearanceSettingsCard
import com.example.ui.components.DocumentManagementSettingsCard
import com.example.ui.components.DriveMirrorSyncDashboardCard
import com.example.ui.components.PhotoDocumentationSlot
import com.example.ui.components.StorageInfoAndAboutSettingsCard
import com.example.ui.components.StorageManagementAndAnalyticsCard
import com.example.ui.theme.AppAppearanceConfig
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.StatusOverdueRed
import com.example.ui.theme.StatusPaidGreen
import com.example.ui.theme.StatusPaidGreenBg
import com.example.ui.theme.StatusWarningYellow
import com.example.ui.theme.appOutlinedTextFieldColors
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private enum class ResetConfirmationType {
    SETTINGS_ONLY,
    TRANSACTIONS_ONLY,
    DEFAULT_SOLO_RAYA,
    ALL_EMPTY
}

enum class SettingSubmenuTab(
    val numberLabel: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeColor: Color
) {
    CLOUD_ACCOUNT("0", "Akun Google & Cloud", "Auth, Firestore, Foto & Migrasi", Icons.Default.CloudDone, Color(0xFF0284C7)),
    PROFILE_PERUSAHAAN("1", "Profil Perusahaan", "Identitas & TTD Owner", Icons.Default.Business, SeaBluePrimary),
    CUSTOMIZE_APP("1B", "Pengaturan Tampilan", "Tema warna, font & background", Icons.Default.Palette, Color(0xFF7C3AED)),
    PERJANJIAN("2", "Perjanjian", "Pengaturan poin perjanjian", Icons.Default.Gavel, Color(0xFF0284C7)),
    CUSTOMIZE_TEMPLATE("3", "Customize Template", "Font, Header, Logo & Halaman PDF", Icons.Default.PictureAsPdf, Color(0xFF0D9488)),
    METODE_PEMBAYARAN("4", "Metode Pembayaran", "Bank, QRIS & Cash/Tunai", Icons.Default.Payments, Color(0xFF059669)),
    NOTIFIKASI_OTOMATIS("4B", "Notifikasi Pintar", "Jatuh tempo, foto & unit", Icons.Default.NotificationsActive, Color(0xFF0284C7)),
    IZIN_AKSES_PERANGKAT("5", "Izin Akses Perangkat", "Kamera, Galeri, GPS & Storage", Icons.Default.Security, Color(0xFF0284C7)),
    DOCUMENT_MANAGEMENT("6", "Document Management", "6 Kategori Dokumen & Integritas", Icons.Default.Description, Color(0xFF0369A1)),
    BACKUP_RESTORE("7", "Pengaturan Data", "Drive, Import, Restore & Template", Icons.Default.Backup, Color(0xFF1D4ED8)),
    INFORMASI_PENYIMPANAN("8", "Informasi Penyimpanan", "Kapasitas Folder SEWAIN_AJA_FREEZER", Icons.Default.Info, Color(0xFF0F766E)),
    KUALITAS_FOTO("8A", "Pengaturan Kualitas Foto", "Hemat, Standar, HD & Kompresi", Icons.Default.HighQuality, Color(0xFF0284C7)),
    PENGELOLAAN_PENYIMPANAN("8B", "Pengelolaan Penyimpanan", "Cache Cleaner, Analytics & Cloud", Icons.Default.CleaningServices, Color(0xFF0F766E)),
    PENGATURAN_PENYIMPANAN("8C", "Pengaturan Penyimpanan", "Google Drive Foto, Folder & Sync", Icons.Default.PhotoLibrary, Color(0xFF0284C7)),
    TENTANG_APLIKASI("9", "Tentang Aplikasi", "Versi, APK Signed & Info Sistem", Icons.Default.Android, Color(0xFF4F46E5)),
    FACTORY_RESET("10", "Factory Reset", "Reset pengaturan dan data", Icons.Default.SettingsBackupRestore, StatusOverdueRed),
    SYNC("11", "Sync", "Cloud, Sheets, Excel, CSV", Icons.Default.CloudSync, Color(0xFF2563EB)),
    APLIKASI("9B", "Aplikasi", "Versi dan export APK", Icons.Default.Android, Color(0xFF4F46E5))
}

@Composable
fun SettingsAndSyncScreen(
    companyProfile: CompanyProfile,
    paymentMethods: List<PaymentMethodEntity> = emptyList(),
    onSavePaymentMethod: (PaymentMethodEntity) -> Unit = {},
    onDeletePaymentMethod: (PaymentMethodEntity) -> Unit = {},
    onTogglePaymentMethodActive: (PaymentMethodEntity, Boolean) -> Unit = { _, _ -> },
    googleSheetUrl: String,
    webhookScriptUrl: String,
    autoSyncEnabled: Boolean,
    maskNikInPdf: Boolean,
    lastSyncTime: String,
    syncStatusIndicator: String,
    importPreview: SheetImportPreview?,
    lastImportReport: ImportExecutionReport?,
    syncLogs: List<SyncLogEntity>,
    perjanjianSewaText: String = DEFAULT_PERJANJIAN_SEWA,
    pdfTemplateSettings: PdfTemplateSettings = PdfTemplateSettings(),
    sewaHakMilikPeriods: List<Int> = DEFAULT_SEWA_HAK_MILIK_PERIODS,
    onUpdateSewaHakMilikPeriods: (List<Int>) -> Unit = {},
    appVersionName: String = "v2.7.0-release (Android 16 / API 36)",
    appBuildNumber: String = "Build #20261110-SAF-SOC",
    lastApkBuildDate: String = "10 Nov 2026, 09:30 WIB",
    isBuildingApk: Boolean = false,
    apkBuildProgress: Float = 0f,
    apkBuildStatusText: String = "Siap membuat paket instalasi APK Signed",
    apkBuildCompleted: Boolean = false,
    generatedApkSizeMb: String = "Siap Digenerate",
    onTriggerGenerateApkBuild: () -> Unit = {},
    onSavePdfTemplateSettings: (PdfTemplateSettings) -> Unit = {},
    onResetPdfTemplateSettings: () -> Unit = {},
    onGenerateTemplatePreviewPdf: ((File) -> Unit) -> Unit = {},
    onImportTemplateAssetFile: (Uri, String, String, (String) -> Unit) -> Unit = { _, _, _, _ -> },
    onSavePerjanjianSewa: (String) -> Unit = {},
    onResetPerjanjianSewa: () -> Unit = {},
    onLoadFileForPreview: (Uri, String, Boolean) -> Unit,
    onLoadBuiltInTimelinePreview: () -> Unit,
    onSelectSheetInPreview: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onDismissPreview: () -> Unit,
    onImportFromGoogleSheets: (String) -> Unit,
    onManualSyncNow: () -> Unit,
    onExportFullXlsx: () -> Unit,
    onExportCategoryCsv: (String) -> Unit = {},
    onExportAndShareApk: () -> Unit,
    onSaveApkToUri: (Uri) -> Unit,
    onSaveCompanyProfile: (CompanyProfile) -> Unit,
    onSaveSyncSettings: (String, String, Boolean, Boolean) -> Unit,
    onBackupDatabaseBeforeReset: () -> Unit = onExportFullXlsx,
    onResetSettingsOnly: () -> Unit = {},
    onResetTransactionsOnly: () -> Unit,
    onResetToDefaultSoloRayaData: () -> Unit,
    onResetAllDataEmpty: () -> Unit,
    permissionStatuses: List<PermissionCategoryStatus> = emptyList(),
    onboardingDecision: String = "SETUJUI_LANJUTKAN",
    onRefreshPermissions: () -> Unit = {},
    onShowOnboardingAgain: () -> Unit = {},
    managedDocuments: List<UnifiedManagedDocumentItem> = emptyList(),
    storageIntegrityReport: StorageIntegrityReport? = null,
    onRunIntegrityCheck: () -> Unit = {},
    onUploadNewDocumentBitmap: (ManagedDocumentCategory, android.graphics.Bitmap) -> Unit = { _, _ -> },
    onUploadNewDocumentUri: (ManagedDocumentCategory, Uri) -> Unit = { _, _ -> },
    onShareManagedDocument: (UnifiedManagedDocumentItem) -> Unit = {},
    onDeleteManagedDocument: (UnifiedManagedDocumentItem) -> Unit = {},
    localBackups: List<LocalBackupEntity> = emptyList(),
    backupSchedule: String = "HARIAN_OTOMATIS",
    lastRestoreMessage: String? = null,
    onCreateLocalBackup: (String) -> Unit = {},
    onRestoreFromBackupEntity: (LocalBackupEntity) -> Unit = {},
    onRestoreFromExternalZipUri: (Uri) -> Unit = {},
    onUpdateBackupSchedule: (String) -> Unit = {},
    deviceStorageSummary: DeviceStorageSummary? = null,
    onRefreshStorageInfo: () -> Unit = {},
    smartNotificationSettings: SmartNotificationSettings = SmartNotificationSettings(),
    onSaveSmartNotificationSettings: (SmartNotificationSettings) -> Unit = {},
    onTriggerSmartNotificationCheck: () -> Unit = {},
    googleAccountInfo: GoogleAccountInfo? = null,
    cloudSyncStatus: String = "Tersinkronisasi",
    cloudLastSyncTimestamp: String = "",
    cloudSyncError: String? = null,
    cloudMigrationState: CloudMigrationStateEntity? = null,
    cloudPhotoJobs: List<CloudPhotoJobEntity> = emptyList(),
    cloudPendingSyncCount: Int = 0,
    onManualCloudSync: () -> Unit = {},
    onRetryFailedPhotos: () -> Unit = {},
    onRetrySingleFailedPhoto: (Long) -> Unit = {},
    imageQualitySettings: ImageQualitySettings = ImageQualitySettings(),
    imageStorageAnalytics: ImageStorageAnalytics = ImageStorageAnalytics(),
    onUpdateImageQualitySettings: (ImageQualitySettings) -> Unit = {},
    onCleanSmartStorageCache: () -> Unit = {},
    onResumeCloudMigration: () -> Unit = {},
    onSwitchGoogleAccount: () -> Unit = {},
    onSignOutGoogleAccount: () -> Unit = {},
    onExportCustomersExcelToUri: (Uri) -> Unit = {},
    onExportTransactionsPdfToUri: (Uri) -> Unit = {},
    onBackupToGoogleDriveUri: (Uri, String) -> Unit = { _, _ -> },
    onShareBackupToGoogleDrive: (String) -> Unit = {},
    driveConnectionStatus: DriveConnectionStatus = DriveConnectionStatus(),
    driveBackupsList: List<DriveBackupFileItem> = emptyList(),
    backupScheduleConfig: BackupScheduleConfig = BackupScheduleConfig(),
    backupAuditLogs: List<BackupAuditLogEntity> = emptyList(),
    restorePreviewSummary: RestorePreviewSummary? = null,
    isBackupOrRestoreBusy: Boolean = false,
    smartImportPreview: SmartImportPreviewState? = null,
    smartImportReport: SmartImportExecutionReport? = null,
    isSmartImporting: Boolean = false,
    onConnectOrSwitchDrive: (String?) -> Unit = {},
    onDisconnectDrive: () -> Unit = {},
    onRunDriveBackupNow: () -> Unit = {},
    onRefreshDriveBackups: () -> Unit = {},
    onDownloadDriveBackupToUri: (DriveBackupFileItem, Uri) -> Unit = { _, _ -> },
    onDeleteDriveBackup: (DriveBackupFileItem) -> Unit = {},
    onDeleteLocalBackup: (LocalBackupEntity) -> Unit = {},
    onInspectDriveBackupForRestore: (DriveBackupFileItem) -> Unit = {},
    onInspectLocalBackupForRestore: (LocalBackupEntity) -> Unit = {},
    onInspectExternalZipForRestore: (Uri) -> Unit = {},
    onExecuteStructuredRestore: (RestoreMode) -> Unit = {},
    onDismissRestorePreview: () -> Unit = {},
    onSaveAutoBackupScheduleConfig: (BackupScheduleConfig) -> Unit = {},
    onPickSmartImportFileUri: (Uri) -> Unit = {},
    onChangeSmartImportTargetAndSheet: (SmartImportTarget, String?) -> Unit = { _, _ -> },
    onUpdateSmartImportColumnMapping: (Int, String?) -> Unit = { _, _ -> },
    onChangeSmartImportDuplicatePolicy: (DuplicateImportPolicy) -> Unit = {},
    onExecuteSmartImport: () -> Unit = {},
    onDismissSmartImportPreview: () -> Unit = {},
    onExportSmartImportErrorReportXlsx: (Uri) -> Unit = {},
    onExportSmartImportErrorReportCsv: (Uri) -> Unit = {},
    onExportExcelTemplateToUri: (TemplateType, Uri) -> Unit = { _, _ -> },
    drivePhotoMetadataList: List<DrivePhotoMetadataEntity> = emptyList(),
    driveStorageStats: DriveStorageSummaryStats = DriveStorageSummaryStats(),
    drivePhotoCompressionConfig: DrivePhotoCompressionConfig = DrivePhotoCompressionConfig(),
    driveFolderTreeSummary: DriveFolderTreeSummary = DriveFolderTreeSummary(),
    drivePhotoLiveProgress: DriveUploadProgressState = DriveUploadProgressState(),
    openedDrivePhotoFile: File? = null,
    openedDrivePhotoMetadata: DrivePhotoMetadataEntity? = null,
    onSyncDrivePhotosNow: () -> Unit = {},
    onVerifyDriveFoldersNow: () -> Unit = {},
    onSaveDrivePhotoCompressionConfig: (DrivePhotoCompressionConfig) -> Unit = {},
    onRetrySingleDrivePhotoUpload: (DrivePhotoMetadataEntity) -> Unit = {},
    onOpenDrivePhotoItem: (DrivePhotoMetadataEntity) -> Unit = {},
    onDismissOpenedDrivePhotoDialog: () -> Unit = {},
    driveMirrorDashboardState: DriveMirrorDashboardState = DriveMirrorDashboardState(),
    onSyncCloudMirrorNow: () -> Unit = {},
    onRetryFailedCloudMirrorSync: () -> Unit = {},
    onResetLocalAndCloud: (Boolean) -> Unit = {},
    onResetLocalOnly: () -> Unit = {},
    consistencyAuditReport: DataConsistencyAuditReport? = null,
    isAuditingConsistency: Boolean = false,
    syncTechnicalLogs: List<SyncTechnicalLogEntity> = emptyList(),
    onRunConsistencyAudit: () -> Unit = {},
    onCleanOrphanAndResync: () -> Unit = {},
    driveQuarantineItems: List<com.example.data.local.DriveCloudQuarantineEntity> = emptyList(),
    onScanStaleCloud: () -> Unit = {},
    onPurgeVerifiedQuarantine: () -> Unit = {},
    appAppearanceConfig: AppAppearanceConfig = AppAppearanceConfig.DEFAULT,
    previewAppearanceConfig: AppAppearanceConfig? = null,
    onPreviewAppearanceChange: (AppAppearanceConfig?) -> Unit = {},
    onSaveAppearanceConfig: (AppAppearanceConfig) -> Unit = {},
    onResetAppearanceConfig: () -> Unit = {}
) {
    val context = LocalContext.current

    // Submenu filter state — default null agar menu setting tampil rapi sebelum salah satu submenu dipilih
    var selectedSubmenu by remember { mutableStateOf<SettingSubmenuTab?>(null) }

    BackHandler(enabled = selectedSubmenu != null) {
        selectedSubmenu = null
    }

    // 1. Profile Perusahaan + Tanda Tangan Permanen Owner State
    var namaPerusahaan by remember(companyProfile) { mutableStateOf(companyProfile.namaPerusahaan) }
    var alamatPerusahaan by remember(companyProfile) { mutableStateOf(companyProfile.alamat) }
    var nomorHpPerusahaan by remember(companyProfile) { mutableStateOf(companyProfile.nomorHp) }
    var emailPerusahaan by remember(companyProfile) { mutableStateOf(companyProfile.email) }
    var identitasUsaha by remember(companyProfile) { mutableStateOf(companyProfile.identitasUsaha) }
    var informasiTambahan by remember(companyProfile) { mutableStateOf(companyProfile.informasiTambahan) }
    var instagramPerusahaan by remember(companyProfile) { mutableStateOf(companyProfile.instagram) }
    var facebookPerusahaan by remember(companyProfile) { mutableStateOf(companyProfile.facebook) }
    var profilePictureUri by remember(companyProfile) { mutableStateOf(companyProfile.profilePictureUri) }
    var ownerSignatureUri by remember(companyProfile) { mutableStateOf(companyProfile.ownerSignatureUri) }
    var ownerSignatureSizePt by remember(companyProfile) { mutableIntStateOf(companyProfile.ownerSignatureSizePt.roundToInt()) }
    var ownerSignaturePosition by remember(companyProfile) { mutableStateOf(companyProfile.ownerSignaturePosition) }
    var namaOwner by remember(companyProfile) { mutableStateOf(companyProfile.ownerName) }
    var showDrawOwnerSignaturePad by remember { mutableStateOf(false) }

    val ownerSignatureGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val dir = File(context.filesDir, "photos/owner_signature")
            if (!dir.exists()) dir.mkdirs()
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: "image/png"
            val ext = if (mime.contains("jpg") || mime.contains("jpeg")) "jpg" else "png"
            val destFile = File(dir, "OWNER_SIG_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            ownerSignatureUri = destFile.absolutePath
        }
    }

    // 2. Metode Pembayaran Modal State
    var editingPaymentMethod by remember { mutableStateOf<PaymentMethodEntity?>(null) }
    var showPaymentMethodDialog by remember { mutableStateOf(false) }

    // 3. Perjanjian Sewa Editor State
    var perjanjianDraft by remember(perjanjianSewaText) { mutableStateOf(perjanjianSewaText) }
    var newPointInput by remember { mutableStateOf("") }
    var showPerjanjianPreviewDialog by remember { mutableStateOf(false) }

    val parsedAgreementPoints = remember(perjanjianDraft) {
        perjanjianDraft
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("PERJANJIAN SEWA FREEZER", ignoreCase = true) }
    }

    fun updatePointsList(newPoints: List<String>) {
        perjanjianDraft = newPoints.mapIndexed { idx, line ->
            val stripped = line.replace(Regex("^\\d+[.)\\-]\\s*"), "")
            "${idx + 1}. $stripped"
        }.joinToString("\n")
    }

    // 4. Customize Template PDF State (Advanced A. Font, B. Header, C. Logo, D. Halaman, E. Live Preview)
    var tplBackgroundUri by remember(pdfTemplateSettings) { mutableStateOf(pdfTemplateSettings.backgroundUri) }
    var tplBackgroundType by remember(pdfTemplateSettings) { mutableStateOf(pdfTemplateSettings.backgroundType) }
    var tplBackgroundFileName by remember(pdfTemplateSettings) { mutableStateOf(pdfTemplateSettings.backgroundFileName) }
    var tplOpacity by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.backgroundOpacity) }
    var tplPaperSize by remember(pdfTemplateSettings) { mutableStateOf(pdfTemplateSettings.paperSize) }
    var tplMarginTop by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.marginTopPt) }
    var tplMarginBottom by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.marginBottomPt) }
    var tplMarginHorizontal by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.marginHorizontalPt) }
    var tplMarginLeft by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.marginLeftPt) }
    var tplMarginRight by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.marginRightPt) }
    var tplContentOffsetY by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.contentOffsetYPt) }
    var tplCustomLogoUri by remember(pdfTemplateSettings) { mutableStateOf(pdfTemplateSettings.customLogoUri) }
    var tplLogoPosition by remember(pdfTemplateSettings) { mutableStateOf(pdfTemplateSettings.logoPosition) }
    var tplLogoSizePt by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.logoSizePt) }

    // A. Pengaturan Font
    var tplFontFamily by remember(pdfTemplateSettings) { mutableStateOf(pdfTemplateSettings.fontFamily) }
    var tplFontWeight by remember(pdfTemplateSettings) { mutableStateOf(pdfTemplateSettings.fontWeight) }
    var tplBodyFontSize by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.bodyFontSizePt) }
    var tplTitleFontSize by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.titleFontSizePt) }
    var tplSubtitleFontSize by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.subtitleFontSizePt) }
    var tplCaptionFontSize by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.captionFontSizePt) }

    // B. Pengaturan Header
    var tplHeaderHeight by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.headerHeightPt) }
    var tplHeaderWidthPercent by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.headerWidthPct) }
    var tplCompanyNameFontSize by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.companyNameFontSizePt) }
    var tplCompanyAddressFontSize by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.companyAddressFontSizePt) }
    var tplCompanyPhoneFontSize by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.companyPhoneFontSizePt) }
    var tplHeaderElementSpacing by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.headerElementSpacingPt) }

    // D. Pengaturan Halaman tambahan
    var tplFooterHeight by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.footerHeightPt) }
    var tplParagraphSpacing by remember(pdfTemplateSettings) { mutableFloatStateOf(pdfTemplateSettings.paragraphSpacingPt) }

    var showTemplatePreviewModal by remember { mutableStateOf(false) }

    val currentDraftTemplate = remember(
        tplBackgroundUri, tplBackgroundType, tplBackgroundFileName, tplOpacity,
        tplPaperSize, tplMarginTop, tplMarginBottom, tplMarginHorizontal, tplMarginLeft, tplMarginRight,
        tplContentOffsetY, tplCustomLogoUri, tplLogoPosition, tplLogoSizePt,
        tplFontFamily, tplFontWeight, tplBodyFontSize, tplTitleFontSize, tplSubtitleFontSize, tplCaptionFontSize,
        tplHeaderHeight, tplHeaderWidthPercent, tplCompanyNameFontSize, tplCompanyAddressFontSize, tplCompanyPhoneFontSize,
        tplHeaderElementSpacing, tplFooterHeight, tplParagraphSpacing
    ) {
        PdfTemplateSettings(
            backgroundUri = tplBackgroundUri,
            backgroundType = tplBackgroundType,
            backgroundFileName = tplBackgroundFileName,
            backgroundOpacity = tplOpacity,
            paperSize = tplPaperSize,
            marginTopPt = tplMarginTop,
            marginBottomPt = tplMarginBottom,
            marginHorizontalPt = tplMarginHorizontal,
            contentOffsetYPt = tplContentOffsetY,
            customLogoUri = tplCustomLogoUri,
            logoPosition = tplLogoPosition,
            logoSizePt = tplLogoSizePt,
            fontFamily = tplFontFamily,
            bodyFontSizePt = tplBodyFontSize,
            titleFontSizePt = tplTitleFontSize,
            subtitleFontSizePt = tplSubtitleFontSize,
            captionFontSizePt = tplCaptionFontSize,
            fontWeight = tplFontWeight,
            headerHeightPt = tplHeaderHeight,
            headerWidthPct = tplHeaderWidthPercent,
            companyNameFontSizePt = tplCompanyNameFontSize,
            companyAddressFontSizePt = tplCompanyAddressFontSize,
            companyPhoneFontSizePt = tplCompanyPhoneFontSize,
            headerElementSpacingPt = tplHeaderElementSpacing,
            marginLeftPt = tplMarginLeft,
            marginRightPt = tplMarginRight,
            footerHeightPt = tplFooterHeight,
            paragraphSpacingPt = tplParagraphSpacing
        )
    }

    // 5. Factory Reset Double Confirmation State
    var pendingResetType by remember { mutableStateOf<ResetConfirmationType?>(null) }
    var resetConfirmationStep by remember { mutableIntStateOf(1) }

    // 6. Sync Settings State
    var sheetUrlInput by remember(googleSheetUrl) { mutableStateOf(googleSheetUrl) }
    var webhookInput by remember(webhookScriptUrl) { mutableStateOf(webhookScriptUrl) }
    var autoSyncToggle by remember(autoSyncEnabled) { mutableStateOf(autoSyncEnabled) }
    var maskNikToggle by remember(maskNikInPdf) { mutableStateOf(maskNikInPdf) }
    var selectedGoogleSheetTab by remember { mutableStateOf(ExcelSheetsEngine.SUPPORTED_TEMPLATE_SHEETS.first()) }

    // 7. Sewa Hak Milik Flexible Period Input State
    var customPeriodInput by remember { mutableStateOf("") }

    val saveApkDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        if (uri != null) {
            onSaveApkToUri(uri)
        }
    }

    val pdfBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportTemplateAssetFile(uri, "bg_template_pdf", "pdf") { savedPath ->
                tplBackgroundUri = savedPath
                tplBackgroundType = "PDF"
                tplBackgroundFileName = "Background PDF Kustom (${File(savedPath).name})"
            }
        }
    }

    val imageBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportTemplateAssetFile(uri, "bg_template_img", "png") { savedPath ->
                tplBackgroundUri = savedPath
                tplBackgroundType = "IMAGE"
                tplBackgroundFileName = "Background Gambar JPG/PNG (${File(savedPath).name})"
            }
        }
    }

    val customLogoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportTemplateAssetFile(uri, "logo_template", "png") { savedPath ->
                tplCustomLogoUri = savedPath
            }
        }
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onLoadFileForPreview(uri, "TIMELINE SEWA IN AJA FREEZER.xlsx", false)
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onLoadFileForPreview(uri, "IMPORT_DATA_SAF.csv", true)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        // ==================== HEADER MENU SETTING (7 SUBMENU UTAMA) ====================
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_setting_menu_hub"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDark),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MENU SETTING — SEWAin Aja Freezer SOC",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = selectedSubmenu?.let {
                                    "Aktif: ${it.numberLabel}. ${it.title}"
                                } ?: "Pilih salah satu submenu pengaturan di bawah ini untuk menampilkannya.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                        if (selectedSubmenu != null) {
                            FilledTonalButton(
                                onClick = { selectedSubmenu = null },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_close_submenu")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali ke daftar submenu",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Menu", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    val mainTabs = listOf(
                        SettingSubmenuTab.CLOUD_ACCOUNT,
                        SettingSubmenuTab.PROFILE_PERUSAHAAN,
                        SettingSubmenuTab.CUSTOMIZE_APP,
                        SettingSubmenuTab.PERJANJIAN,
                        SettingSubmenuTab.CUSTOMIZE_TEMPLATE,
                        SettingSubmenuTab.METODE_PEMBAYARAN,
                        SettingSubmenuTab.NOTIFIKASI_OTOMATIS,
                        SettingSubmenuTab.IZIN_AKSES_PERANGKAT,
                        SettingSubmenuTab.DOCUMENT_MANAGEMENT,
                        SettingSubmenuTab.BACKUP_RESTORE,
                        SettingSubmenuTab.INFORMASI_PENYIMPANAN,
                        SettingSubmenuTab.KUALITAS_FOTO,
                        SettingSubmenuTab.PENGELOLAAN_PENYIMPANAN,
                        SettingSubmenuTab.TENTANG_APLIKASI,
                        SettingSubmenuTab.FACTORY_RESET,
                        SettingSubmenuTab.SYNC
                    )

                    val activeTab = selectedSubmenu
                    if (activeTab == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            mainTabs.chunked(2).forEach { rowTabs ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowTabs.forEach { tab ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF1E3A8A).copy(alpha = 0.65f),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = Color(0xFF38BDF8).copy(alpha = 0.35f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedSubmenu = tab }
                                                .testTag("submenu_tab_${tab.numberLabel}")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.16f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = tab.icon,
                                                        contentDescription = tab.title,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${tab.numberLabel}. ${tab.title}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = tab.subtitle,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFE0F2FE),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            mainTabs.forEach { tab ->
                                val isSelected = activeTab == tab
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) tab.badgeColor else Color(0xFF1E3A8A).copy(alpha = 0.65f),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color(0xFF38BDF8).copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            selectedSubmenu = if (isSelected) null else tab
                                        }
                                        .testTag("submenu_tab_${tab.numberLabel}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${tab.numberLabel}. ${tab.title}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedSubmenu == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_setting_select_prompt"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, SeaBluePrimary.copy(alpha = 0.25f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SeaBluePrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Pilih Submenu Pengaturan",
                                tint = SeaBluePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pilih Salah Satu Submenu Setting",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Text(
                                text = "Ketuk salah satu menu pengaturan di atas untuk menampilkan dan mengubah konfigurasinya.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ==================== DASHBOARD SINKRONISASI CLOUD MIRROR & RESET DATA SINKRONISASI ====================
        if (
            selectedSubmenu == null ||
            selectedSubmenu == SettingSubmenuTab.CLOUD_ACCOUNT ||
            selectedSubmenu == SettingSubmenuTab.SYNC ||
            selectedSubmenu == SettingSubmenuTab.FACTORY_RESET ||
            selectedSubmenu == SettingSubmenuTab.PENGATURAN_PENYIMPANAN
        ) {
            item {
                DriveMirrorSyncDashboardCard(
                    state = driveMirrorDashboardState,
                    onSyncNowClick = onSyncCloudMirrorNow,
                    onRetryFailedClick = onRetryFailedCloudMirrorSync,
                    onResetLocalAndCloudConfirm = onResetLocalAndCloud,
                    onResetLocalOnlyConfirm = onResetLocalOnly,
                    consistencyAuditReport = consistencyAuditReport,
                    isAuditingConsistency = isAuditingConsistency,
                    syncTechnicalLogs = syncTechnicalLogs,
                    onRunConsistencyAuditClick = onRunConsistencyAudit,
                    onCleanOrphanAndResyncClick = onCleanOrphanAndResync,
                    quarantineItems = driveQuarantineItems,
                    onScanStaleCloudClick = onScanStaleCloud,
                    onPurgeVerifiedQuarantineClick = onPurgeVerifiedQuarantine,
                    onReconnectDriveClick = {
                        onConnectOrSwitchDrive(driveMirrorDashboardState.connectedEmail.ifBlank { null })
                    }
                )
            }
        }

        // ==================== SUBMENU 1B: PENGATURAN TAMPILAN / CUSTOMIZE APP ====================
        if (selectedSubmenu == SettingSubmenuTab.CUSTOMIZE_APP) {
            item {
                CustomizeAppearanceSettingsCard(
                    savedConfig = previewAppearanceConfig ?: appAppearanceConfig,
                    onPreviewConfig = onPreviewAppearanceChange,
                    onSaveConfig = onSaveAppearanceConfig,
                    onResetDefault = onResetAppearanceConfig,
                    onFeedbackMessage = { msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ==================== SUBMENU 1: PROFILE PERUSAHAAN + TANDA TANGAN OWNER ====================
        if (selectedSubmenu == SettingSubmenuTab.PROFILE_PERUSAHAAN) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_company_profile"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val hasAvatar = remember(profilePictureUri) {
                                profilePictureUri.isNotBlank() && (
                                    profilePictureUri.startsWith("content://") ||
                                        profilePictureUri.startsWith("file://") ||
                                        File(profilePictureUri).exists()
                                    )
                            }
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(NavyDark)
                                    .border(2.dp, LightBlueAccent, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasAvatar) {
                                    AsyncImage(
                                        model = if (profilePictureUri.startsWith("/")) File(profilePictureUri) else profilePictureUri,
                                        contentDescription = "Profile Picture Perusahaan",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AcUnit,
                                        contentDescription = "Logo Perusahaan Default",
                                        tint = LightBlueAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Business,
                                        contentDescription = null,
                                        tint = SeaBluePrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "1. PROFILE PERUSAHAAN",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                }
                                Text(
                                    text = "Identitas perusahaan — terintegrasi langsung dengan Header Aplikasi, Struk WhatsApp & Dokumen Invoice PDF A4.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        PhotoDocumentationSlot(
                            label = "Logo / Profile Picture Perusahaan",
                            photoKey = "COMPANY_LOGO",
                            transactionOrEntityFolder = "company_profile",
                            currentUriOrPath = profilePictureUri,
                            isMandatory = false,
                            onPhotoSaved = { profilePictureUri = it }
                        )

                        SmartCaseOutlinedTextField(
                            value = namaPerusahaan,
                            onValueChange = { namaPerusahaan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Nama Perusahaan") },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_company_name")
                        )

                        SmartCaseOutlinedTextField(
                            value = identitasUsaha,
                            onValueChange = { identitasUsaha = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Identitas Usaha (Izin / Bidang Operasional)") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_company_identitas_usaha")
                        )

                        SmartCaseOutlinedTextField(
                            value = alamatPerusahaan,
                            onValueChange = { alamatPerusahaan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Alamat Perusahaan") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_company_address")
                        )

                        SmartCaseOutlinedTextField(
                            value = nomorHpPerusahaan,
                            onValueChange = { nomorHpPerusahaan = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            label = { Text("Nomor WhatsApp Perusahaan") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_company_phone")
                        )

                        SmartCaseOutlinedTextField(
                            value = emailPerusahaan,
                            onValueChange = { emailPerusahaan = it },
                            caseMode = InputTextCaseMode.LOWERCASE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            label = { Text("Email Perusahaan") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_company_email")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            SmartCaseOutlinedTextField(
                                value = instagramPerusahaan,
                                onValueChange = { instagramPerusahaan = it },
                                caseMode = InputTextCaseMode.PRESERVE,
                                label = { Text("Akun Instagram") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_company_instagram")
                            )

                            SmartCaseOutlinedTextField(
                                value = facebookPerusahaan,
                                onValueChange = { facebookPerusahaan = it },
                                caseMode = InputTextCaseMode.PRESERVE,
                                label = { Text("Akun Facebook") },
                                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_company_facebook")
                            )
                        }

                        SmartCaseOutlinedTextField(
                            value = informasiTambahan,
                            onValueChange = { informasiTambahan = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Informasi Tambahan Perusahaan (Jam Layanan / Rekening / Catatan PDF)") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_company_info_tambahan")
                        )

                        // ==================== SECTION 5: TANDA TANGAN PERMANEN OWNER PADA PDF ====================
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_owner_signature")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = SeaBluePrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "SETTING → PROFILE PERUSAHAAN → TANDA TANGAN OWNER",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = NavyDark
                                        )
                                        Text(
                                            text = "Muncul di setiap halaman PDF (Invoice, Perjanjian, Serah Terima & Pengembalian) • Verified by SEWAin Aja Freezer SOC",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SeaBluePrimary
                                        )
                                    }
                                }

                                SmartCaseOutlinedTextField(
                                    value = namaOwner,
                                    onValueChange = { namaOwner = it },
                                    caseMode = InputTextCaseMode.UPPERCASE,
                                    label = { Text("Nama / Jabatan Owner Penanda Tangan") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Preview Tanda Tangan Owner
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        horizontalAlignment = when (ownerSignaturePosition) {
                                            "LEFT" -> Alignment.Start
                                            "CENTER" -> Alignment.CenterHorizontally
                                            else -> Alignment.End
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Preview Area Verifikasi Bawah Halaman PDF (Posisi: $ownerSignaturePosition • Ukuran: $ownerSignatureSizePt pt):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        if (ownerSignatureUri.isNotBlank() && File(ownerSignatureUri).exists()) {
                                            Box(
                                                modifier = Modifier
                                                    .height(ownerSignatureSizePt.dp.coerceIn(40.dp, 110.dp))
                                                    .width((ownerSignatureSizePt * 2).dp.coerceIn(90.dp, 220.dp))
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFF8FAFC))
                                            ) {
                                                AsyncImage(
                                                    model = File(ownerSignatureUri),
                                                    contentDescription = "Preview Tanda Tangan Owner",
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = namaOwner.ifBlank { "Owner SEWAin Aja Freezer SOC" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = NavyDark
                                            )
                                        }
                                        Text(
                                            text = "Verified by SEWAin Aja Freezer SOC",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SeaBluePrimary
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            ownerSignatureGalleryLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_upload_owner_signature")
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (ownerSignatureUri.isBlank()) "Upload PNG/JPG" else "Ganti PNG/JPG",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { showDrawOwnerSignaturePad = !showDrawOwnerSignaturePad },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("btn_draw_owner_signature")
                                    ) {
                                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Goreskan TTD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (showDrawOwnerSignaturePad) {
                                    DigitalSignaturePad(
                                        title = "Goreskan Tanda Tangan Permanen Owner",
                                        signerName = namaOwner,
                                        transactionFolder = "owner_signature",
                                        filePrefix = "owner_perm",
                                        existingSignaturePath = ownerSignatureUri,
                                        onSignatureSaved = {
                                            ownerSignatureUri = it
                                            showDrawOwnerSignaturePad = false
                                        }
                                    )
                                }

                                // Ukuran & Posisi Tanda Tangan Owner
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Ukuran Tanda Tangan Owner:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("$ownerSignatureSizePt pt", style = MaterialTheme.typography.labelSmall, color = SeaBluePrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = ownerSignatureSizePt.toFloat(),
                                        onValueChange = { ownerSignatureSizePt = it.roundToInt().coerceIn(36, 110) },
                                        valueRange = 36f..110f,
                                        modifier = Modifier.testTag("slider_owner_sig_size")
                                    )
                                }

                                Text("Posisi Tanda Tangan Owner di Footer Verifikasi:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf(
                                        "LEFT" to "Kiri",
                                        "CENTER" to "Tengah",
                                        "RIGHT" to "Kanan"
                                    ).forEach { (posCode, label) ->
                                        FilterChip(
                                            selected = ownerSignaturePosition == posCode,
                                            onClick = { ownerSignaturePosition = posCode },
                                            label = { Text("Posisi $label") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Text(
                                    text = "Ketentuan Historis: Dokumen lama yang sudah terbit tetap menggunakan snapshot tanda tangan saat diterbitkan. Dokumen baru otomatis menggunakan tanda tangan terbaru ini.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onSaveCompanyProfile(
                                    CompanyProfile(
                                        namaPerusahaan = namaPerusahaan.trim().ifBlank { "SEWAin Aja Freezer SOC" },
                                        alamat = alamatPerusahaan.trim(),
                                        nomorHp = nomorHpPerusahaan.trim(),
                                        email = emailPerusahaan.trim(),
                                        instagram = instagramPerusahaan.trim(),
                                        facebook = facebookPerusahaan.trim(),
                                        profilePictureUri = profilePictureUri,
                                        identitasUsaha = identitasUsaha.trim(),
                                        informasiTambahan = informasiTambahan.trim(),
                                        ownerSignatureUri = ownerSignatureUri,
                                        ownerSignatureSizePt = ownerSignatureSizePt.toFloat(),
                                        ownerSignaturePosition = ownerSignaturePosition,
                                        ownerName = namaOwner.trim().ifBlank { "Owner SEWAin Aja Freezer SOC" }
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_save_company_profile")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SIMPAN PROFILE PERUSAHAAN & TTD OWNER", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ==================== SUBMENU 2: SETTING → METODE PEMBAYARAN ====================
        if (selectedSubmenu == SettingSubmenuTab.METODE_PEMBAYARAN) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_payment_methods"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF059669))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "2. SETTING → METODE PEMBAYARAN",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark
                                    )
                                    Text(
                                        text = "Metode pembayaran aktif otomatis muncul pada halaman Transaksi Baru (QRIS, Transfer Bank BCA/BRI/Mandiri/BNI, Cash/Tunai, & Custom).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    editingPaymentMethod = null
                                    showPaymentMethodDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_add_payment_method")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Metode Baru", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (paymentMethods.isEmpty()) {
                            Text(
                                text = "Belum ada metode pembayaran. Tekan [+ Metode Baru] untuk menambahkan.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            paymentMethods.forEach { method ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (method.isAktif) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = when (method.kategoriMetode) {
                                                    "QRIS" -> Icons.Default.QrCode2
                                                    "TUNAI" -> Icons.Default.Payments
                                                    else -> Icons.Default.AccountBalance
                                                },
                                                contentDescription = null,
                                                tint = if (method.isAktif) Color(0xFF059669) else Color.Gray,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = method.namaMetode,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = NavyDark
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        color = if (method.isAktif) StatusPaidGreenBg else Color.LightGray.copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = if (method.isAktif) "AKTIF" else "NONAKTIF",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (method.isAktif) StatusPaidGreen else Color.DarkGray,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (method.kategoriMetode == "TRANSFER_BANK" || method.nomorRekening.isNotBlank()) {
                                                    Text(
                                                        text = "${method.namaBankAtauPenyedia} • Rek: ${method.nomorRekening} a.n ${method.namaPemilikRekening}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = SeaBluePrimary
                                                    )
                                                } else if (method.kategoriMetode == "QRIS") {
                                                    Text(
                                                        text = "QRIS: ${method.namaPemilikRekening} ${if (method.fotoQrisUri.isNotBlank()) "• (Gambar QRIS Tersimpan ✓)" else ""}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = SeaBluePrimary
                                                    )
                                                } else {
                                                    Text(
                                                        text = method.keteranganPembayaran.ifBlank { "Pembayaran Tunai / Cash" },
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = method.isAktif,
                                                onCheckedChange = { active -> onTogglePaymentMethodActive(method, active) }
                                            )
                                            IconButton(
                                                onClick = {
                                                    editingPaymentMethod = method
                                                    showPaymentMethodDialog = true
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Metode", tint = SeaBluePrimary)
                                            }
                                            IconButton(
                                                onClick = { onDeletePaymentMethod(method) }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Hapus Metode", tint = StatusOverdueRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== SUBMENU 3: PERJANJIAN ====================
        if (selectedSubmenu == SettingSubmenuTab.PERJANJIAN) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_customize_perjanjian_sewa"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = SeaBluePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "3. PERJANJIAN — PENGATURAN POIN PERJANJIAN SEWA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Text(
                                    text = "Otomatis ditampilkan pada dokumen PDF tepat sebelum Tanda Tangan Konsumen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SeaBluePrimary
                                )
                            }
                        }

                        Text(
                            text = "Tambah, edit, hapus, dan atur urutan poin perjanjian sewa freezer. Perubahan perjanjian hanya berlaku untuk transaksi baru dan tidak merusak transaksi lama yang sudah tersimpan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SmartCaseOutlinedTextField(
                                value = newPointInput,
                                onValueChange = { newPointInput = it },
                                caseMode = InputTextCaseMode.UPPERCASE,
                                placeholder = { Text("Ketik poin perjanjian baru...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_new_agreement_point")
                            )
                            FilledTonalButton(
                                onClick = {
                                    val textToAdd = newPointInput.trim().ifBlank {
                                        "Ketentuan tambahan penyewaan unit freezer."
                                    }
                                    val updated = parsedAgreementPoints + textToAdd
                                    updatePointsList(updated)
                                    newPointInput = ""
                                },
                                modifier = Modifier.testTag("btn_add_agreement_point")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Tambah Poin", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (parsedAgreementPoints.isNotEmpty()) {
                            Text(
                                text = "Daftar Poin Perjanjian (${parsedAgreementPoints.size} Poin — Klik panah untuk atur urutan atau ikon sampah untuk hapus):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                parsedAgreementPoints.forEachIndexed { index, pointLine ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = pointLine,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val mutable = parsedAgreementPoints.toMutableList()
                                                        val tmp = mutable[index - 1]
                                                        mutable[index - 1] = mutable[index]
                                                        mutable[index] = tmp
                                                        updatePointsList(mutable)
                                                    }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowUpward,
                                                    contentDescription = "Geser Poin Naik",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (index < parsedAgreementPoints.lastIndex) {
                                                        val mutable = parsedAgreementPoints.toMutableList()
                                                        val tmp = mutable[index + 1]
                                                        mutable[index + 1] = mutable[index]
                                                        mutable[index] = tmp
                                                        updatePointsList(mutable)
                                                    }
                                                },
                                                enabled = index < parsedAgreementPoints.lastIndex,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ArrowDownward,
                                                    contentDescription = "Geser Poin Turun",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    val mutable = parsedAgreementPoints.toMutableList()
                                                    mutable.removeAt(index)
                                                    updatePointsList(mutable)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Hapus Poin",
                                                    tint = StatusOverdueRed,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    updatePointsList(parsedAgreementPoints)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_auto_number_agreement")
                            ) {
                                Text("Rapikan & Penomoran Otomatis", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        SmartCaseOutlinedTextField(
                            value = perjanjianDraft,
                            onValueChange = { perjanjianDraft = it },
                            caseMode = InputTextCaseMode.UPPERCASE,
                            label = { Text("Editor Lengkap Isi Perjanjian Sewa Freezer (Tampil di Invoice PDF)") },
                            minLines = 6,
                            maxLines = 14,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_perjanjian_sewa_editor")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { onSavePerjanjianSewa(perjanjianDraft) },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("btn_save_rental_agreement")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan", fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { showPerjanjianPreviewDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_preview_rental_agreement")
                            ) {
                                Text("Preview PDF", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    perjanjianDraft = DEFAULT_PERJANJIAN_SEWA
                                    onResetPerjanjianSewa()
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("btn_reset_rental_agreement")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }

        // ==================== SUBMENU 4: CUSTOMIZE TEMPLATE PDF LANJUTAN (A. FONT, B. HEADER, C. LOGO, D. HALAMAN, E. LIVE PREVIEW) ====================
        if (selectedSubmenu == SettingSubmenuTab.CUSTOMIZE_TEMPLATE) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_customize_pdf_template"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = SeaBluePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "4. CUSTOMIZE TEMPLATE PDF LANJUTAN",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Text(
                                    text = "Pengaturan Font, Header, Logo, Halaman A4, Background & Live Preview untuk seluruh dokumen PDF.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status Template Aktif
                        Surface(
                            color = Color(0xFFF0F9FF),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Konfigurasi Aktif: Font $tplFontFamily ($tplFontWeight) • Header ${tplHeaderHeight.roundToInt()}pt (${tplHeaderWidthPercent.roundToInt()}% Lebar) • Logo $tplLogoPosition (${tplLogoSizePt.roundToInt()}pt)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SeaBluePrimary
                                )
                                Text(
                                    text = "Kertas: $tplPaperSize • Margin Atas/Bawah/Kiri/Kanan: ${tplMarginTop.roundToInt()}/${tplMarginBottom.roundToInt()}/${tplMarginLeft.roundToInt()}/${tplMarginRight.roundToInt()} pt • Footer: ${tplFooterHeight.roundToInt()} pt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // ==================== A. PENGATURAN FONT ====================
                        HorizontalDivider()
                        Text(
                            text = "A. PENGATURAN FONT (JENIS, UKURAN & KETEBALAN)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )

                        Text("Jenis Font Dokumen PDF:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                "SANS_SERIF" to "Sans Serif (Modern)",
                                "SERIF" to "Serif (Resmi)",
                                "MONOSPACE" to "Monospace (Teknis)"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = tplFontFamily == code,
                                    onClick = { tplFontFamily = code },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text("Ketebalan Font (Font Weight):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                "REGULAR" to "Regular",
                                "MEDIUM" to "Medium",
                                "BOLD" to "Bold (Tebal)"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = tplFontWeight == code,
                                    onClick = { tplFontWeight = code },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ukuran Judul: ${tplTitleFontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplTitleFontSize,
                                    onValueChange = { tplTitleFontSize = it.roundToInt().coerceIn(9, 18).toFloat() },
                                    valueRange = 9f..18f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ukuran Subjudul: ${tplSubtitleFontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplSubtitleFontSize,
                                    onValueChange = { tplSubtitleFontSize = it.roundToInt().coerceIn(8, 15).toFloat() },
                                    valueRange = 8f..15f
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ukuran Font Isi: ${tplBodyFontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplBodyFontSize,
                                    onValueChange = { tplBodyFontSize = it.roundToInt().coerceIn(7, 13).toFloat() },
                                    valueRange = 7f..13f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ukuran Keterangan: ${tplCaptionFontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplCaptionFontSize,
                                    onValueChange = { tplCaptionFontSize = it.roundToInt().coerceIn(6, 11).toFloat() },
                                    valueRange = 6f..11f
                                )
                            }
                        }

                        // ==================== B. PENGATURAN HEADER ====================
                        HorizontalDivider()
                        Text(
                            text = "B. PENGATURAN HEADER DOKUMEN PDF",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tinggi Header: ${tplHeaderHeight.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplHeaderHeight,
                                    onValueChange = { tplHeaderHeight = it.roundToInt().coerceIn(54, 120).toFloat() },
                                    valueRange = 54f..120f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lebar Header: ${tplHeaderWidthPercent.roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplHeaderWidthPercent,
                                    onValueChange = { tplHeaderWidthPercent = it.roundToInt().coerceIn(60, 100).toFloat() },
                                    valueRange = 60f..100f
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ukuran Nama Perusahaan: ${tplCompanyNameFontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplCompanyNameFontSize,
                                    onValueChange = { tplCompanyNameFontSize = it.roundToInt().coerceIn(10, 20).toFloat() },
                                    valueRange = 10f..20f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ukuran Alamat Perusahaan: ${tplCompanyAddressFontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplCompanyAddressFontSize,
                                    onValueChange = { tplCompanyAddressFontSize = it.roundToInt().coerceIn(6, 12).toFloat() },
                                    valueRange = 6f..12f
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ukuran Nomor Telepon: ${tplCompanyPhoneFontSize.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplCompanyPhoneFontSize,
                                    onValueChange = { tplCompanyPhoneFontSize = it.roundToInt().coerceIn(6, 12).toFloat() },
                                    valueRange = 6f..12f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Jarak Antar Elemen Header: ${tplHeaderElementSpacing.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplHeaderElementSpacing,
                                    onValueChange = { tplHeaderElementSpacing = it.roundToInt().coerceIn(4, 24).toFloat() },
                                    valueRange = 4f..24f
                                )
                            }
                        }

                        // ==================== C. PENGATURAN LOGO ====================
                        HorizontalDivider()
                        Text(
                            text = "C. PENGATURAN LOGO PERUSAHAAN PADA PDF",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )

                        // Preview Logo
                        if (tplCustomLogoUri.isNotBlank() && File(tplCustomLogoUri).exists()) {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color.LightGray),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    AsyncImage(
                                        model = File(tplCustomLogoUri),
                                        contentDescription = "Preview Logo PDF",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Column {
                                        Text("Preview Logo Perusahaan Aktif", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text("Posisi: $tplLogoPosition • Ukuran: ${tplLogoSizePt.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    customLogoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_upload_pdf_logo")
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (tplCustomLogoUri.isNotBlank()) "Ganti Logo Perusahaan" else "Upload Logo Perusahaan",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (tplCustomLogoUri.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { tplCustomLogoUri = "" }
                                ) {
                                    Text("Reset Logo", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "LEFT" to "Kiri",
                                "CENTER" to "Tengah",
                                "RIGHT" to "Kanan"
                            ).forEach { (posCode, label) ->
                                FilterChip(
                                    selected = tplLogoPosition == posCode,
                                    onClick = { tplLogoPosition = posCode },
                                    label = { Text("Logo $label") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chip_logo_pos_${posCode.lowercase()}")
                                )
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Ukuran Logo Perusahaan di PDF",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${tplLogoSizePt.roundToInt()} pt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SeaBluePrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = tplLogoSizePt,
                                onValueChange = { tplLogoSizePt = it.roundToInt().coerceIn(28, 72).toFloat() },
                                valueRange = 28f..72f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("slider_pdf_logo_size")
                            )
                        }

                        // ==================== D. PENGATURAN HALAMAN & BACKGROUND ====================
                        HorizontalDivider()
                        Text(
                            text = "D. PENGATURAN HALAMAN A4, MARGIN, FOOTER & BACKGROUND",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = tplPaperSize == "A4",
                                onClick = { tplPaperSize = "A4" },
                                label = { Text("Kertas A4 Standar (595 x 842 pt)") },
                                leadingIcon = {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Margin Atas: ${tplMarginTop.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplMarginTop,
                                    onValueChange = { tplMarginTop = it.roundToInt().coerceIn(16, 120).toFloat() },
                                    valueRange = 16f..120f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Margin Bawah: ${tplMarginBottom.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplMarginBottom,
                                    onValueChange = { tplMarginBottom = it.roundToInt().coerceIn(20, 100).toFloat() },
                                    valueRange = 20f..100f
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Margin Kiri: ${tplMarginLeft.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplMarginLeft,
                                    onValueChange = {
                                        val v = it.roundToInt().coerceIn(20, 80).toFloat()
                                        tplMarginLeft = v
                                        tplMarginHorizontal = v
                                    },
                                    valueRange = 20f..80f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Margin Kanan: ${tplMarginRight.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplMarginRight,
                                    onValueChange = { tplMarginRight = it.roundToInt().coerceIn(20, 80).toFloat() },
                                    valueRange = 20f..80f
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ukuran Footer: ${tplFooterHeight.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplFooterHeight,
                                    onValueChange = { tplFooterHeight = it.roundToInt().coerceIn(30, 80).toFloat() },
                                    valueRange = 30f..80f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Jarak Antar Paragraf: ${tplParagraphSpacing.roundToInt()} pt", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = tplParagraphSpacing,
                                    onValueChange = { tplParagraphSpacing = it.roundToInt().coerceIn(2, 18).toFloat() },
                                    valueRange = 2f..18f
                                )
                            }
                        }

                        // Upload Background PDF atau Gambar JPG/PNG
                        Text(
                            text = "Upload Background Dokumen (PDF atau Gambar JPG/PNG Opsional):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { pdfBackgroundLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_upload_bg_pdf")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = {
                                    imageBackgroundLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_upload_bg_image")
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload JPG/PNG", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Transparansi Background",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${(tplOpacity * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SeaBluePrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = tplOpacity,
                                onValueChange = { tplOpacity = it.coerceIn(0.05f, 1.0f) },
                                valueRange = 0.05f..1.0f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("slider_pdf_bg_opacity")
                            )
                        }

                        // ==================== E. LIVE PREVIEW & TOMBOL AKSI ====================
                        HorizontalDivider()
                        Text(
                            text = "E. LIVE PREVIEW & SIMPAN PENGATURAN TEMPLATE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { showTemplatePreviewModal = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_preview_pdf_template")
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PREVIEW PDF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = { onSavePdfTemplateSettings(currentDraftTemplate) },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("btn_save_pdf_template")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SIMPAN TEMPLATE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = {
                                    val def = PdfTemplateSettings()
                                    tplBackgroundUri = def.backgroundUri
                                    tplBackgroundType = def.backgroundType
                                    tplBackgroundFileName = def.backgroundFileName
                                    tplOpacity = def.backgroundOpacity
                                    tplPaperSize = def.paperSize
                                    tplMarginTop = def.marginTopPt
                                    tplMarginBottom = def.marginBottomPt
                                    tplMarginHorizontal = def.marginHorizontalPt
                                    tplMarginLeft = def.marginLeftPt
                                    tplMarginRight = def.marginRightPt
                                    tplContentOffsetY = def.contentOffsetYPt
                                    tplCustomLogoUri = def.customLogoUri
                                    tplLogoPosition = def.logoPosition
                                    tplLogoSizePt = def.logoSizePt
                                    tplFontFamily = def.fontFamily
                                    tplFontWeight = def.fontWeight
                                    tplBodyFontSize = def.bodyFontSizePt
                                    tplTitleFontSize = def.titleFontSizePt
                                    tplSubtitleFontSize = def.subtitleFontSizePt
                                    tplCaptionFontSize = def.captionFontSizePt
                                    tplHeaderHeight = def.headerHeightPt
                                    tplHeaderWidthPercent = def.headerWidthPct
                                    tplCompanyNameFontSize = def.companyNameFontSizePt
                                    tplCompanyAddressFontSize = def.companyAddressFontSizePt
                                    tplCompanyPhoneFontSize = def.companyPhoneFontSizePt
                                    tplHeaderElementSpacing = def.headerElementSpacingPt
                                    tplFooterHeight = def.footerHeightPt
                                    tplParagraphSpacing = def.paragraphSpacingPt
                                    onResetPdfTemplateSettings()
                                },
                                modifier = Modifier
                                    .weight(0.95f)
                                    .testTag("btn_reset_pdf_template")
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RESET PENGATURAN", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // ==================== SUBMENU 5: FACTORY RESET ====================
        if (selectedSubmenu == SettingSubmenuTab.FACTORY_RESET) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_reset_data"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = StatusOverdueRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "5. FACTORY RESET — RESET PENGATURAN DAN DATA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    text = "Dilengkapi Peringatan Keamanan, Backup Data Sebelum Reset & Konfirmasi 2 Kali.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Peringatan Keamanan Sebelum Reset",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E)
                                    )
                                }
                                Text(
                                    text = "Sangat disarankan melakukan Backup Data Sebelum Reset. Seluruh aksi reset wajib melewati Konfirmasi Dua Kali (2-Step Verification) dan tidak pernah berjalan tanpa persetujuan Anda.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF78350F)
                                )
                                Button(
                                    onClick = onBackupDatabaseBeforeReset,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_backup_before_reset")
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("BACKUP DATA SEBELUM RESET (.XLSX)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(
                            text = "A. Reset Pengaturan (Tanpa Menghapus Data Pelanggan, Transaksi & Foto)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        FilledTonalButton(
                            onClick = {
                                resetConfirmationStep = 1
                                pendingResetType = ResetConfirmationType.SETTINGS_ONLY
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFE0F2FE),
                                contentColor = NavyDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_reset_settings_only")
                        ) {
                            Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset Pengaturan Saja (Aman: Database & Foto Tetap Utuh)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        Text(
                            text = "B. Reset Database & Seluruh Data Aplikasi",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B)
                        )

                        OutlinedButton(
                            onClick = {
                                resetConfirmationStep = 1
                                pendingResetType = ResetConfirmationType.TRANSACTIONS_ONLY
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_reset_transactions_only")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = StatusOverdueRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset Riwayat Transaksi Saja (Simpan Unit & Pelanggan)",
                                color = StatusOverdueRed,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                resetConfirmationStep = 1
                                pendingResetType = ResetConfirmationType.DEFAULT_SOLO_RAYA
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFFFEE2E2),
                                contentColor = Color(0xFF991B1B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_reset_default_data")
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset ke Data Awal Operasional Solo Raya",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                resetConfirmationStep = 1
                                pendingResetType = ResetConfirmationType.ALL_EMPTY
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusOverdueRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_reset_all_empty")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset Seluruh Data Aplikasi (Kosongkan Database Mulai dari 0)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ==================== SUBMENU 0 & 6: AKUN GOOGLE, CLOUD FIRESTORE & SYNC ====================
        if (selectedSubmenu == SettingSubmenuTab.CLOUD_ACCOUNT || selectedSubmenu == SettingSubmenuTab.SYNC) {
            item {
                CloudAccountAndSyncSection(
                    googleAccountInfo = googleAccountInfo,
                    cloudSyncStatus = cloudSyncStatus,
                    cloudLastSyncTimestamp = cloudLastSyncTimestamp,
                    cloudSyncError = cloudSyncError,
                    cloudMigrationState = cloudMigrationState,
                    cloudPhotoJobs = cloudPhotoJobs,
                    cloudPendingSyncCount = cloudPendingSyncCount,
                    onManualCloudSync = onManualCloudSync,
                    onRetryFailedPhotos = onRetryFailedPhotos,
                    onRetrySinglePhoto = onRetrySingleFailedPhoto,
                    onResumeCloudMigration = onResumeCloudMigration,
                    onSwitchGoogleAccount = onSwitchGoogleAccount,
                    onSignOutGoogleAccount = onSignOutGoogleAccount
                )
            }
        }

        if (selectedSubmenu == SettingSubmenuTab.SYNC) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "6. SYNC — A. SINKRONISASI GOOGLE SHEETS",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Indikator Progres & Status: $syncStatusIndicator",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Waktu Sinkronisasi Terakhir: $lastSyncTime",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.LightGray
                        )
                        Text(
                            text = "Sinkronisasi mencakup Data Pelanggan, Data Transaksi, Riwayat Pembayaran & Status Jatuh Tempo. Jika sinkronisasi gagal, data lokal dijamin tetap utuh dan tidak rusak.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onManualSyncNow,
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_manual_sync")
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sinkronisasi Google Sheets", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = onExportFullXlsx,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup XLSX", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "B. IMPORT DATA (EXCEL .XLSX, CSV .CSV & GOOGLE SHEETS)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Text(
                            text = "Mendukung pemilihan Sheet, Preview & Validasi sebelum import, deteksi duplikat otomatis, dan pemetaan kolom.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    xlsxLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/octet-stream"
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_xlsx")
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Excel (.xlsx)", style = MaterialTheme.typography.labelMedium)
                            }

                            FilledTonalButton(
                                onClick = {
                                    csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_csv")
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import CSV (.csv)", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = onLoadBuiltInTimelinePreview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_preview_builtin_timeline")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buka Preview Struktur TIMELINE SEWA IN AJA FREEZER.xlsx")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Import Langsung dari Google Sheets (Pilih Sheet):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            ExcelSheetsEngine.SUPPORTED_TEMPLATE_SHEETS.forEach { sheetName ->
                                FilterChip(
                                    selected = selectedGoogleSheetTab == sheetName,
                                    onClick = { selectedGoogleSheetTab = sheetName },
                                    label = { Text(sheetName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { onImportFromGoogleSheets(selectedGoogleSheetTab) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Google Sheets: '$selectedGoogleSheetTab'")
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                        Text(
                            text = "C. EXPORT DATA (EXCEL .XLSX, CSV .CSV & GOOGLE SHEETS)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onExportFullXlsx,
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_export_xlsx_sync")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Excel (.xlsx)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = { onExportCategoryCsv("TRANSAKSI") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_export_csv_sync")
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export CSV (.csv)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onExportCategoryCsv("PELANGGAN") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export CSV Pelanggan", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onExportCategoryCsv("UNIT") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export CSV Unit", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onManualSyncNow,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export ke Sheets", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            if (lastImportReport != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusPaidGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Notifikasi Hasil Import Sheet: ${lastImportReport.sheetName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46)
                                )
                            }
                            Text(
                                text = "Berhasil diimpor: ${lastImportReport.successCount} baris | Duplikat digabung aman: ${lastImportReport.duplicateSkippedOrMerged} | Gagal: ${lastImportReport.failedCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF065F46)
                            )
                            if (lastImportReport.errors.isNotEmpty()) {
                                lastImportReport.errors.take(3).forEach { err ->
                                    Text("• $err", style = MaterialTheme.typography.labelSmall, color = StatusOverdueRed)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "KONFIGURASI KONEKSI AKUN / SPREADSHEET GOOGLE SHEETS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        SmartCaseOutlinedTextField(
                            value = sheetUrlInput,
                            onValueChange = { sheetUrlInput = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            label = { Text("Link / ID Google Spreadsheet (TIMELINE SEWA IN AJA FREEZER)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        SmartCaseOutlinedTextField(
                            value = webhookInput,
                            onValueChange = { webhookInput = it },
                            caseMode = InputTextCaseMode.PRESERVE,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            label = { Text("URL Google Apps Script Webhook (Push Sinkronisasi Dua Arah)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sinkronisasi Otomatis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Simpan snapshot setiap ada perubahan pelanggan, transaksi, atau pembayaran", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoSyncToggle, onCheckedChange = { autoSyncToggle = it })
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Masking NIK pada Invoice PDF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Tampilkan NIK secara terbatas (contoh: 337201******0001) demi privasi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = maskNikToggle, onCheckedChange = { maskNikToggle = it })
                        }

                        Button(
                            onClick = {
                                onSaveSyncSettings(sheetUrlInput, webhookInput, autoSyncToggle, maskNikToggle)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_save_settings")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SIMPAN PENGATURAN SINKRONISASI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ==================== SUBMENU 7: APLIKASI (VERSI, EXPORT APK & KONFIGURASI PROGRAM) ====================
        if (
            selectedSubmenu == SettingSubmenuTab.APLIKASI ||
            selectedSubmenu == SettingSubmenuTab.TENTANG_APLIKASI
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_setting_aplikasi_versi"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2547)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "7. APLIKASI — VERSI DAN EXPORT APK",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Informasi nama & versi aplikasi, nomor build, tanggal build, riwayat versi, dan pusat export APK.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFBAE6FD)
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF1E3A8A).copy(alpha = 0.65f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Nama Aplikasi:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBAE6FD))
                                    Text("SEWAin Aja Freezer SOC", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Versi Aplikasi:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBAE6FD))
                                    Text(appVersionName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Nomor Build:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBAE6FD))
                                    Text(appBuildNumber, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Tanggal Build:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBAE6FD))
                                    Text(lastApkBuildDate, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Kompatibilitas Android:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBAE6FD))
                                    Text("Android 8.0 s/d Android 16 (API 36)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                                }
                            }
                        }

                        if (isBuildingApk || apkBuildProgress > 0f || apkBuildCompleted) {
                            Surface(
                                color = Color(0xFF0A192F),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isBuildingApk) "Proses Build APK Berjalan..." else if (apkBuildCompleted) "Build APK Selesai ($generatedApkSizeMb)" else "Status Build APK",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Text(
                                            text = "${(apkBuildProgress * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { apkBuildProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF38BDF8),
                                        trackColor = Color(0xFF1E3A8A)
                                    )
                                    if (apkBuildStatusText.isNotBlank()) {
                                        Text(
                                            text = apkBuildStatusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE0F2FE)
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onTriggerGenerateApkBuild,
                            enabled = !isBuildingApk,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_generate_apk_version")
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBuildingApk) "SEDANG MEMPROSES BUILD APK..." else "GENERATE APK (BUILD INSTALLER)",
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    saveApkDocumentLauncher.launch("SEWAin_Aja_Freezer_SOC_Android16_Signed.apk")
                                },
                                enabled = !isBuildingApk,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_download_apk_to_storage")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DOWNLOAD APK", fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = onExportAndShareApk,
                                enabled = !isBuildingApk,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF38BDF8),
                                    contentColor = NavyDark
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_export_share_apk")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("EXPORT APK", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_hak_milik_periods_config"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFF6A1B9A))
                            Column {
                                Text(
                                    text = "KONFIGURASI PERIODE PROGRAM SEWA HAK MILIK",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Text(
                                    text = "Tambah atau kelola pilihan jumlah periode Sewa Hak Milik secara fleksibel tanpa perlu mengubah struktur kode utama.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            sewaHakMilikPeriods.sorted().forEach { p ->
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        if (sewaHakMilikPeriods.size > 1) {
                                            onUpdateSewaHakMilikPeriods(sewaHakMilikPeriods.filter { it != p })
                                        }
                                    },
                                    label = { Text("$p Kali ✕", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = customPeriodInput,
                                onValueChange = { customPeriodInput = it.filter { c -> c.isDigit() } },
                                label = { Text("Tambah Periode Baru (Misal: 30)") },
                                singleLine = true,
                                colors = appOutlinedTextFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val newP = customPeriodInput.toIntOrNull()
                                    if (newP != null && newP in 2..120) {
                                        onUpdateSewaHakMilikPeriods((sewaHakMilikPeriods + newP).distinct().sorted())
                                        customPeriodInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                            ) {
                                Text("+ Tambah", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    onUpdateSewaHakMilikPeriods(DEFAULT_SEWA_HAK_MILIK_PERIODS)
                                }
                            ) {
                                Text("Default")
                            }
                        }
                    }
                }
            }
        }

        // ==================== SUBMENU 4B: SMART NOTIFICATION SETTINGS ====================
        if (selectedSubmenu == SettingSubmenuTab.NOTIFIKASI_OTOMATIS) {
            item {
                var notifDraft by remember(smartNotificationSettings) { mutableStateOf(smartNotificationSettings) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_smart_notifications_settings"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = SeaBluePrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "4B. NOTIFIKASI PINTAR & OTOMASI PENGINGAT",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                                Text(
                                    text = "Atur pengingat otomatis tagihan, unit jatuh tempo, kelengkapan foto & kontrak selesai.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider()

                        @Composable
                        fun NotifSwitchRow(title: String, subtitle: String, checked: Boolean, tag: String, onToggle: (Boolean) -> Unit) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = checked,
                                    onCheckedChange = onToggle,
                                    modifier = Modifier.testTag(tag)
                                )
                            }
                        }

                        NotifSwitchRow(
                            title = "Pengingat Sebelum Jatuh Tempo",
                            subtitle = "Kirim notifikasi otomatis sebelum tanggal jatuh tempo unit",
                            checked = notifDraft.enableDueSoonReminder,
                            tag = "switch_notif_due_soon"
                        ) { notifDraft = notifDraft.copy(enableDueSoonReminder = it) }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Rentang Hari:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            listOf(1, 3, 5, 7).forEach { days ->
                                FilterChip(
                                    selected = notifDraft.dueReminderDaysBefore == days,
                                    onClick = { notifDraft = notifDraft.copy(dueReminderDaysBefore = days) },
                                    label = { Text("H-$days") }
                                )
                            }
                        }

                        NotifSwitchRow(
                            title = "Pengingat Tagihan Terlambat (Overdue)",
                            subtitle = "Peringatkan admin untuk tagihan unit yang melewati jatuh tempo",
                            checked = notifDraft.enableOverdueReminder,
                            tag = "switch_notif_overdue"
                        ) { notifDraft = notifDraft.copy(enableOverdueReminder = it) }

                        NotifSwitchRow(
                            title = "Notifikasi Penambahan Unit Baru",
                            subtitle = "Pemberitahuan saat unit baru ditambahkan ke kontrak pelanggan",
                            checked = notifDraft.enableUnitAddedNotification,
                            tag = "switch_notif_unit_added"
                        ) { notifDraft = notifDraft.copy(enableUnitAddedNotification = it) }

                        NotifSwitchRow(
                            title = "Notifikasi Kontrak Sewa Selesai",
                            subtitle = "Konfirmasi saat pengembalian unit / kontrak tuntas diselesaikan",
                            checked = notifDraft.enableContractCompletedNotification,
                            tag = "switch_notif_contract_done"
                        ) { notifDraft = notifDraft.copy(enableContractCompletedNotification = it) }

                        NotifSwitchRow(
                            title = "Peringatan Foto Dokumentasi Unit Belum Lengkap",
                            subtitle = "Deteksi unit aktif yang belum memiliki 4 foto kondisi & serah terima",
                            checked = notifDraft.enableIncompletePhotoReminder,
                            tag = "switch_notif_incomplete_docs"
                        ) { notifDraft = notifDraft.copy(enableIncompletePhotoReminder = it) }

                        NotifSwitchRow(
                            title = "Peringatan Unit Belum Dikembalikan Setelah Jatuh Tempo",
                            subtitle = "Tandai unit yang masa sewanya telah berakhir namun belum dikembalikan",
                            checked = notifDraft.enableUnreturnedUnitReminder,
                            tag = "switch_notif_unreturned"
                        ) { notifDraft = notifDraft.copy(enableUnreturnedUnitReminder = it) }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { onSaveSmartNotificationSettings(notifDraft) },
                                colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_save_smart_notif_settings")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan Notifikasi", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    onSaveSmartNotificationSettings(notifDraft)
                                    onTriggerSmartNotificationCheck()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_test_smart_notif_now")
                            ) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cek Sekarang")
                            }
                        }
                    }
                }
            }
        }

        // ==================== SUBMENU 5: IZIN AKSES PERANGKAT ====================
        if (selectedSubmenu == SettingSubmenuTab.IZIN_AKSES_PERANGKAT) {
            item {
                val effectivePerms = if (permissionStatuses.isNotEmpty()) {
                    permissionStatuses
                } else {
                    remember(context) { LocalStorageAndBackupManager.evaluateAllPermissionCategories(context) }
                }
                DevicePermissionsSettingsCard(
                    permissionStatuses = effectivePerms,
                    onboardingDecision = onboardingDecision,
                    onRefreshPermissions = onRefreshPermissions,
                    onShowOnboardingAgain = onShowOnboardingAgain
                )
            }
        }

        // ==================== SUBMENU 6: DOCUMENT MANAGEMENT ====================
        if (selectedSubmenu == SettingSubmenuTab.DOCUMENT_MANAGEMENT) {
            item {
                DocumentManagementSettingsCard(
                    documents = managedDocuments,
                    integrityReport = storageIntegrityReport,
                    onRunIntegrityCheck = onRunIntegrityCheck,
                    onUploadNewDocumentBitmap = onUploadNewDocumentBitmap,
                    onUploadNewDocumentUri = onUploadNewDocumentUri,
                    onShareDocument = onShareManagedDocument,
                    onDeleteDocumentConfirmed = onDeleteManagedDocument
                )
            }
        }

        // ==================== SUBMENU 7: PENGATURAN DATA (DRIVE, BACKUP, RESTORE, SMART IMPORT, TEMPLATE) ====================
        if (selectedSubmenu == SettingSubmenuTab.BACKUP_RESTORE) {
            item {
                DataManagementHubCard(
                    driveStatus = driveConnectionStatus,
                    driveBackups = driveBackupsList,
                    localBackups = localBackups,
                    backupScheduleConfig = backupScheduleConfig,
                    auditLogs = backupAuditLogs,
                    restorePreview = restorePreviewSummary,
                    lastRestoreMessage = lastRestoreMessage,
                    isBusy = isBackupOrRestoreBusy,
                    smartImportPreview = smartImportPreview,
                    smartImportReport = smartImportReport,
                    isImporting = isSmartImporting,
                    onConnectOrSwitchDrive = onConnectOrSwitchDrive,
                    onDisconnectDrive = onDisconnectDrive,
                    onRunDriveBackupNow = onRunDriveBackupNow,
                    onRefreshDriveBackups = onRefreshDriveBackups,
                    onDownloadDriveBackupToUri = onDownloadDriveBackupToUri,
                    onDeleteDriveBackup = onDeleteDriveBackup,
                    onDeleteLocalBackup = onDeleteLocalBackup,
                    onBackupToSafUri = onBackupToGoogleDriveUri,
                    onInspectDriveBackupForRestore = onInspectDriveBackupForRestore,
                    onInspectLocalBackupForRestore = onInspectLocalBackupForRestore,
                    onInspectExternalZipForRestore = onInspectExternalZipForRestore,
                    onExecuteRestore = onExecuteStructuredRestore,
                    onDismissRestorePreview = onDismissRestorePreview,
                    onSaveScheduleConfig = onSaveAutoBackupScheduleConfig,
                    onPickImportFileUri = onPickSmartImportFileUri,
                    onChangeImportTargetAndSheet = onChangeSmartImportTargetAndSheet,
                    onUpdateImportColumnMapping = onUpdateSmartImportColumnMapping,
                    onChangeImportDuplicatePolicy = onChangeSmartImportDuplicatePolicy,
                    onExecuteSmartImport = onExecuteSmartImport,
                    onDismissSmartImportPreview = onDismissSmartImportPreview,
                    onExportErrorReportXlsx = onExportSmartImportErrorReportXlsx,
                    onExportErrorReportCsv = onExportSmartImportErrorReportCsv,
                    onExportFullXlsx = onExportFullXlsx,
                    onExportCategoryCsv = onExportCategoryCsv,
                    onExportCustomersXlsxUri = onExportCustomersExcelToUri,
                    onExportTransactionsPdfUri = onExportTransactionsPdfToUri,
                    onExportTemplateToUri = onExportExcelTemplateToUri
                )
            }
            item {
                DriveMirrorSyncDashboardCard(
                    state = driveMirrorDashboardState,
                    onSyncNowClick = onSyncCloudMirrorNow,
                    onRetryFailedClick = onRetryFailedCloudMirrorSync,
                    onResetLocalAndCloudConfirm = onResetLocalAndCloud,
                    onResetLocalOnlyConfirm = onResetLocalOnly,
                    consistencyAuditReport = consistencyAuditReport,
                    isAuditingConsistency = isAuditingConsistency,
                    syncTechnicalLogs = syncTechnicalLogs,
                    onRunConsistencyAuditClick = onRunConsistencyAudit,
                    onCleanOrphanAndResyncClick = onCleanOrphanAndResync,
                    quarantineItems = driveQuarantineItems,
                    onScanStaleCloudClick = onScanStaleCloud,
                    onPurgeVerifiedQuarantineClick = onPurgeVerifiedQuarantine,
                    onReconnectDriveClick = {
                        onConnectOrSwitchDrive(driveMirrorDashboardState.connectedEmail.ifBlank { null })
                    }
                )
            }
        }

        // ==================== SUBMENU 8 & 9: INFORMASI PENYIMPANAN & TENTANG APLIKASI ====================
        if (
            selectedSubmenu == SettingSubmenuTab.INFORMASI_PENYIMPANAN ||
            selectedSubmenu == SettingSubmenuTab.TENTANG_APLIKASI
        ) {
            item {
                val effectiveSummary = deviceStorageSummary
                    ?: remember(context) { LocalStorageAndBackupManager.getDeviceStorageSummary(context) }
                StorageInfoAndAboutSettingsCard(
                    storageSummary = effectiveSummary,
                    appVersionName = appVersionName,
                    appBuildNumber = appBuildNumber,
                    onRefreshStorageInfo = onRefreshStorageInfo
                )
            }
        }

        // ==================== SUBMENU 8A: PENGATURAN KUALITAS FOTO ====================
        if (selectedSubmenu == SettingSubmenuTab.KUALITAS_FOTO) {
            item {
                val effectiveAnalytics = remember(context, imageStorageAnalytics, cloudPhotoJobs) {
                    if (imageStorageAnalytics.totalDeviceBytes > 0L) {
                        imageStorageAnalytics
                    } else {
                        SmartImageStorageOptimizer.computeActualStorageAnalytics(context, cloudPhotoJobs)
                    }
                }
                ImageQualitySettingsCard(
                    settings = imageQualitySettings,
                    analytics = effectiveAnalytics,
                    onUpdateSettings = onUpdateImageQualitySettings,
                    onRefreshAnalytics = onRefreshStorageInfo
                )
            }
        }

        // ==================== SUBMENU 8B: PENGELOLAAN PENYIMPANAN & ANALYTICS ====================
        if (selectedSubmenu == SettingSubmenuTab.PENGELOLAAN_PENYIMPANAN) {
            item {
                val effectiveAnalytics = remember(context, imageStorageAnalytics, cloudPhotoJobs) {
                    if (imageStorageAnalytics.totalDeviceBytes > 0L) {
                        imageStorageAnalytics
                    } else {
                        SmartImageStorageOptimizer.computeActualStorageAnalytics(context, cloudPhotoJobs)
                    }
                }
                StorageManagementAndAnalyticsCard(
                    analytics = effectiveAnalytics,
                    cloudSyncStatus = cloudSyncStatus,
                    cloudPhotoJobs = cloudPhotoJobs,
                    onRefreshAnalytics = onRefreshStorageInfo,
                    onConfirmCleanCache = onCleanSmartStorageCache,
                    onRetryAllFailedPhotos = onRetryFailedPhotos,
                    onRetrySinglePhoto = onRetrySingleFailedPhoto
                )
            }
        }

        // ==================== SUBMENU 8C: PENGATURAN PENYIMPANAN (PUSAT DOKUMENTASI FOTO GOOGLE DRIVE) ====================
        if (
            selectedSubmenu == SettingSubmenuTab.PENGATURAN_PENYIMPANAN ||
            selectedSubmenu == SettingSubmenuTab.PENGELOLAAN_PENYIMPANAN
        ) {
            item {
                DrivePhotoStorageSettingsCard(
                    driveConnectionStatus = driveConnectionStatus,
                    drivePhotoItems = drivePhotoMetadataList,
                    driveStorageStats = driveStorageStats,
                    compressionConfig = drivePhotoCompressionConfig,
                    folderTreeSummary = driveFolderTreeSummary,
                    liveUploadProgress = drivePhotoLiveProgress,
                    isSyncBusy = isBackupOrRestoreBusy,
                    openedPhotoFile = openedDrivePhotoFile,
                    openedPhotoMetadata = openedDrivePhotoMetadata,
                    onConnectOrSwitchDrive = onConnectOrSwitchDrive,
                    onSyncNow = onSyncDrivePhotosNow,
                    onDisconnectDrive = onDisconnectDrive,
                    onSaveCompressionConfig = onSaveDrivePhotoCompressionConfig,
                    onEnsureDriveFolderHierarchy = onVerifyDriveFoldersNow,
                    onRetrySinglePhotoUpload = onRetrySingleDrivePhotoUpload,
                    onOpenPhotoFromDriveOrLocal = onOpenDrivePhotoItem,
                    onDismissOpenedPhotoDialog = onDismissOpenedDrivePhotoDialog
                )
            }
        }

        // ==================== RIWAYAT LOG SINKRONISASI, IMPORT & RESET ====================
        if (selectedSubmenu == SettingSubmenuTab.SYNC || selectedSubmenu == SettingSubmenuTab.FACTORY_RESET) {
            item {
                Text(
                    text = "RIWAYAT LOG SINKRONISASI, IMPORT & RESET (${syncLogs.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(syncLogs, key = { it.id }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(log.jenisOperasi, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SeaBluePrimary)
                            Text(log.timestamp, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Sheet: ${log.sheetName} • Status: ${log.status}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(log.detailPesan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // ==================== DIALOG TAMBAH / EDIT METODE PEMBAYARAN ====================
    if (showPaymentMethodDialog) {
        PaymentMethodFormDialog(
            initialMethod = editingPaymentMethod,
            onDismiss = {
                showPaymentMethodDialog = false
                editingPaymentMethod = null
            },
            onSave = { method ->
                onSavePaymentMethod(method)
                showPaymentMethodDialog = false
                editingPaymentMethod = null
            }
        )
    }

    // ==================== DIALOG FACTORY RESET DENGAN KONFIRMASI DUA KALI ====================
    var alsoResetCloudMirrorOnAllEmpty by remember(pendingResetType) { mutableStateOf(false) }
    pendingResetType?.let { resetType ->
        val (titleTextStep1, bodyTextStep1) = when (resetType) {
            ResetConfirmationType.SETTINGS_ONLY ->
                "Peringatan Reset Pengaturan (Tahap 1/2)" to
                    "Anda akan mengembalikan seluruh pengaturan aplikasi (Profil Perusahaan, Template Perjanjian, Template PDF & Sync) ke kondisi default.\n\nData Pelanggan, Transaksi, Unit Freezer, dan Foto/Dokumen TIDAK akan dihapus."
            ResetConfirmationType.TRANSACTIONS_ONLY ->
                "Peringatan Reset Riwayat Transaksi (Tahap 1/2)" to
                    "Apakah Anda yakin ingin menghapus seluruh riwayat transaksi sewa dan perpanjangan? Data Unit Freezer dan Data Pelanggan tetap tersimpan, dan status unit akan dikembalikan menjadi Tersedia."
            ResetConfirmationType.DEFAULT_SOLO_RAYA ->
                "Peringatan Reset ke Data Awal Solo Raya (Tahap 1/2)" to
                    "Sistem akan membuat Backup XLSX otomatis terlebih dahulu, lalu mengembalikan database ke data awal operasional Solo Raya."
            ResetConfirmationType.ALL_EMPTY ->
                "PERINGATAN KERAS: Reset Seluruh Data Aplikasi (Tahap 1/2)" to
                    "Seluruh database Pelanggan, Unit Freezer, Transaksi, foto lokal, dan antrean sinkronisasi akan dihapus hingga 0. Anda juga dapat memilih untuk menghapus Cloud Mirror Google Drive sekaligus."
        }

        val (titleTextStep2, bodyTextStep2) = when (resetType) {
            ResetConfirmationType.SETTINGS_ONLY ->
                "Konfirmasi Kedua / Terakhir: Reset Pengaturan (Tahap 2/2)" to
                    "KONFIRMASI KEDUA: Apakah Anda benar-benar menyetujui pengembalian pengaturan aplikasi ke kondisi awal sekarang?"
            ResetConfirmationType.TRANSACTIONS_ONLY ->
                "Konfirmasi Kedua / Terakhir: Hapus Transaksi (Tahap 2/2)" to
                    "KONFIRMASI KEDUA: Tindakan ini tidak dapat dibatalkan tanpa file backup. Lanjutkan hapus seluruh riwayat transaksi sekarang?"
            ResetConfirmationType.DEFAULT_SOLO_RAYA ->
                "Konfirmasi Kedua / Terakhir: Reset Data Solo Raya (Tahap 2/2)" to
                    "KONFIRMASI KEDUA: Lanjutkan mereset database ke data awal operasional Solo Raya sekarang?"
            ResetConfirmationType.ALL_EMPTY ->
                if (alsoResetCloudMirrorOnAllEmpty) {
                    "Konfirmasi Kedua / Terakhir: RESET LOKAL & CLOUD (Tahap 2/2)" to
                        "KONFIRMASI KEDUA (FINAL): Seluruh data SQLite lokal DAN file aktif Google Drive Mirror akan dihapus permanen. Data lama tidak akan pernah direstore otomatis."
                } else {
                    "Konfirmasi Kedua / Terakhir: KOSONGKAN DATA LOKAL (Tahap 2/2)" to
                        "KONFIRMASI KEDUA (FINAL): Seluruh data pelanggan, transaksi, unit, dan foto lokal akan dihapus permanen. File Google Drive tetap disimpan."
                }
        }

        AlertDialog(
            onDismissRequest = {
                pendingResetType = null
                resetConfirmationStep = 1
            },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusOverdueRed)
            },
            title = {
                Text(
                    text = if (resetConfirmationStep == 1) titleTextStep1 else titleTextStep2,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (resetConfirmationStep == 1) bodyTextStep1 else bodyTextStep2)
                    if (resetType == ResetConfirmationType.ALL_EMPTY && resetConfirmationStep == 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { alsoResetCloudMirrorOnAllEmpty = !alsoResetCloudMirrorOnAllEmpty }
                                .padding(8.dp)
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = alsoResetCloudMirrorOnAllEmpty,
                                onCheckedChange = { alsoResetCloudMirrorOnAllEmpty = it },
                                modifier = Modifier.testTag("chk_also_reset_cloud_mirror")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sekaligus hapus Cloud Mirror di Google Drive (RESET LOKAL + CLOUD)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (resetConfirmationStep == 1) {
                        OutlinedButton(
                            onClick = onBackupDatabaseBeforeReset,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup Data Sebelum Reset (.XLSX)")
                        }
                    }
                }
            },
            confirmButton = {
                if (resetConfirmationStep == 1) {
                    Button(
                        onClick = { resetConfirmationStep = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        modifier = Modifier.testTag("btn_reset_step1_next")
                    ) {
                        Text("LANJUT KONFIRMASI KE-2 (1/2)", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            when (resetType) {
                                ResetConfirmationType.SETTINGS_ONLY -> onResetSettingsOnly()
                                ResetConfirmationType.TRANSACTIONS_ONLY -> onResetTransactionsOnly()
                                ResetConfirmationType.DEFAULT_SOLO_RAYA -> onResetToDefaultSoloRayaData()
                                ResetConfirmationType.ALL_EMPTY -> {
                                    if (alsoResetCloudMirrorOnAllEmpty) {
                                        onResetLocalAndCloud(false)
                                    } else {
                                        onResetAllDataEmpty()
                                    }
                                }
                            }
                            pendingResetType = null
                            resetConfirmationStep = 1
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusOverdueRed),
                        modifier = Modifier.testTag("btn_confirm_reset_data")
                    ) {
                        Text("YA, RESET SEKARANG (2/2)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        pendingResetType = null
                        resetConfirmationStep = 1
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }

    // PREVIEW DIALOG FOR PERJANJIAN SEWA INVOICE PDF
    if (showPerjanjianPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showPerjanjianPreviewDialog = false },
            title = {
                Text(
                    text = "Preview Perjanjian Sewa di Invoice PDF",
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Urutan Penempatan di Invoice PDF A4:",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeaBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Identitas Bisnis, Cap Status Pembayaran & Informasi Invoice\n2. Data Pelanggan & Data Unit Freezer\n3. Rincian Biaya & Jenis Program\n4. Alamat Penggunaan & Tagging Lokasi\n5. Foto Dokumentasi\n6. ISI PERJANJIAN SEWA (Di Bawah Ini)\n7. Tanda Tangan Konsumen & Tanda Tangan Permanen Owner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Surface(
                        color = Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "PERJANJIAN SEWA FREEZER",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        text = perjanjianDraft.ifBlank { DEFAULT_PERJANJIAN_SEWA },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePerjanjianSewa(perjanjianDraft)
                        showPerjanjianPreviewDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary)
                ) {
                    Text("Simpan & Tutup")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPerjanjianPreviewDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // LIVE PREVIEW DIALOG FOR CUSTOMIZE PDF TEMPLATE
    if (showTemplatePreviewModal) {
        val previewCompany = remember(
            namaPerusahaan, alamatPerusahaan, nomorHpPerusahaan, emailPerusahaan,
            instagramPerusahaan, facebookPerusahaan, profilePictureUri, identitasUsaha,
            informasiTambahan, ownerSignatureUri, ownerSignatureSizePt, ownerSignaturePosition, namaOwner
        ) {
            CompanyProfile(
                namaPerusahaan = namaPerusahaan,
                alamat = alamatPerusahaan,
                nomorHp = nomorHpPerusahaan,
                email = emailPerusahaan,
                instagram = instagramPerusahaan,
                facebook = facebookPerusahaan,
                profilePictureUri = profilePictureUri,
                identitasUsaha = identitasUsaha,
                informasiTambahan = informasiTambahan,
                ownerSignatureUri = ownerSignatureUri,
                ownerSignatureSizePt = ownerSignatureSizePt.toFloat(),
                ownerSignaturePosition = ownerSignaturePosition,
                ownerName = namaOwner
            )
        }
        val previewBitmap = remember(currentDraftTemplate, previewCompany) {
            runCatching {
                PdfTemplateRenderer.renderTemplatePreviewBitmap(
                    context = context,
                    companyProfile = previewCompany,
                    template = currentDraftTemplate
                )
            }.getOrNull()
        }

        AlertDialog(
            onDismissRequest = { showTemplatePreviewModal = false },
            title = {
                Text(
                    text = "Live Preview Template PDF Resmi (Kertas A4)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Menampilkan simulasi halaman A4 dengan Font ($tplFontFamily $tplFontWeight), Header (${tplHeaderHeight.roundToInt()}pt), Cap Status Pembayaran di kanan atas, dan Tanda Tangan Permanen Owner di bagian bawah halaman.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (previewBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Preview Template PDF A4",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            text = "Gagal membuat pratinjau gambar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusOverdueRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePdfTemplateSettings(currentDraftTemplate)
                        showTemplatePreviewModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeaBluePrimary)
                ) {
                    Text("Simpan Template")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTemplatePreviewModal = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // PREVIEW DIALOG BEFORE IMPORT
    if (importPreview != null) {
        SheetImportPreviewDialog(
            preview = importPreview,
            onSelectSheet = onSelectSheetInPreview,
            onConfirmImport = onConfirmImport,
            onDismiss = onDismissPreview
        )
    }
}

@Composable
private fun PaymentMethodFormDialog(
    initialMethod: PaymentMethodEntity?,
    onDismiss: () -> Unit,
    onSave: (PaymentMethodEntity) -> Unit
) {
    val context = LocalContext.current
    var namaMetode by remember(initialMethod) { mutableStateOf(initialMethod?.namaMetode ?: "Transfer BCA") }
    var kategoriMetode by remember(initialMethod) { mutableStateOf(initialMethod?.kategoriMetode ?: "TRANSFER_BANK") }
    var namaBankAtauPenyedia by remember(initialMethod) { mutableStateOf(initialMethod?.namaBankAtauPenyedia ?: "Bank Central Asia (BCA)") }
    var nomorRekening by remember(initialMethod) { mutableStateOf(initialMethod?.nomorRekening ?: "") }
    var namaPemilikRekening by remember(initialMethod) { mutableStateOf(initialMethod?.namaPemilikRekening ?: "SEWAin Aja Freezer SOC") }
    var fotoQrisUri by remember(initialMethod) { mutableStateOf(initialMethod?.fotoQrisUri ?: "") }
    var logoBankUri by remember(initialMethod) { mutableStateOf(initialMethod?.logoBankUri ?: "") }
    var keteranganPembayaran by remember(initialMethod) { mutableStateOf(initialMethod?.keteranganPembayaran ?: "") }
    var isAktif by remember(initialMethod) { mutableStateOf(initialMethod?.isAktif ?: true) }
    var methodValidationResult by remember { mutableStateOf<FormValidationResult?>(null) }

    val qrisPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val dir = File(context.filesDir, "photos/payment_methods")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "QRIS_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            fotoQrisUri = destFile.absolutePath
        }
    }

    val logoBankPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val dir = File(context.filesDir, "photos/payment_methods")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "BANK_LOGO_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            logoBankUri = destFile.absolutePath
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialMethod == null) "Tambah Metode Pembayaran" else "Edit Metode Pembayaran",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                FormValidationAlertBanner(
                    validationResult = methodValidationResult,
                    testTagName = "payment_method_validation_banner"
                )
                Text("Pilih Kategori Metode:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "TRANSFER_BANK" to "Transfer Bank",
                        "QRIS" to "QRIS",
                        "TUNAI" to "Cash / Tunai",
                        "LAINNYA" to "Custom / Lainnya"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = kategoriMetode == code,
                            onClick = {
                                kategoriMetode = code
                                if (code == "QRIS" && namaMetode.startsWith("Transfer")) {
                                    namaMetode = "QRIS"
                                    namaBankAtauPenyedia = "QRIS All Payment"
                                } else if (code == "TUNAI") {
                                    namaMetode = "Cash / Tunai"
                                    namaBankAtauPenyedia = "Tunai"
                                    nomorRekening = ""
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Quick Preset Bank Chips
                if (kategoriMetode == "TRANSFER_BANK") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "Transfer BCA" to "Bank Central Asia (BCA)",
                            "Transfer BRI" to "Bank Rakyat Indonesia (BRI)",
                            "Transfer Mandiri" to "Bank Mandiri",
                            "Transfer BNI" to "Bank Negara Indonesia (BNI)"
                        ).forEach { (presetName, presetBank) ->
                            FilterChip(
                                selected = namaMetode == presetName,
                                onClick = {
                                    namaMetode = presetName
                                    namaBankAtauPenyedia = presetBank
                                },
                                label = { Text(presetName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                SmartCaseOutlinedTextField(
                    value = namaMetode,
                    onValueChange = {
                        namaMetode = it
                        if (methodValidationResult != null) methodValidationResult = null
                    },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Nama Metode Pembayaran *") },
                    isError = methodValidationResult?.fieldErrors?.containsKey("namaMetode") == true,
                    supportingText = { FieldErrorSupportingText(methodValidationResult?.fieldErrors?.get("namaMetode")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SmartCaseOutlinedTextField(
                    value = namaBankAtauPenyedia,
                    onValueChange = { namaBankAtauPenyedia = it },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Nama Bank / Penyedia Layanan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (kategoriMetode != "TUNAI") {
                    SmartCaseOutlinedTextField(
                        value = nomorRekening,
                        onValueChange = {
                            nomorRekening = it
                            if (methodValidationResult != null) methodValidationResult = null
                        },
                        caseMode = InputTextCaseMode.PRESERVE,
                        label = { Text(if (kategoriMetode == "TRANSFER_BANK") "Nomor Rekening *" else "Nomor Rekening / ID Merchant") },
                        isError = methodValidationResult?.fieldErrors?.containsKey("nomorRekening") == true,
                        supportingText = { FieldErrorSupportingText(methodValidationResult?.fieldErrors?.get("nomorRekening")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SmartCaseOutlinedTextField(
                        value = namaPemilikRekening,
                        onValueChange = {
                            namaPemilikRekening = it
                            if (methodValidationResult != null) methodValidationResult = null
                        },
                        caseMode = InputTextCaseMode.UPPERCASE,
                        label = { Text(if (kategoriMetode == "TRANSFER_BANK") "Nama Pemilik Rekening *" else "Nama Pemilik Rekening") },
                        isError = methodValidationResult?.fieldErrors?.containsKey("namaPemilikRekening") == true,
                        supportingText = { FieldErrorSupportingText(methodValidationResult?.fieldErrors?.get("namaPemilikRekening")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (kategoriMetode == "QRIS") {
                    if (fotoQrisUri.isNotBlank() && File(fotoQrisUri).exists()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.06f))
                        ) {
                            AsyncImage(
                                model = File(fotoQrisUri),
                                contentDescription = "Gambar QRIS",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            qrisPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (fotoQrisUri.isBlank()) "Upload Gambar QRIS" else "Ganti Gambar QRIS")
                    }
                }

                OutlinedButton(
                    onClick = {
                        logoBankPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (logoBankUri.isBlank()) "Upload Logo Bank (Opsional)" else "Ganti Logo Bank ✓")
                }

                SmartCaseOutlinedTextField(
                    value = keteranganPembayaran,
                    onValueChange = { keteranganPembayaran = it },
                    caseMode = InputTextCaseMode.UPPERCASE,
                    label = { Text("Keterangan / Instruksi Pembayaran") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Status Aktif (Tampil di Transaksi Baru):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Switch(checked = isAktif, onCheckedChange = { isAktif = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = FormValidationEngine.validatePaymentMethodForm(
                        namaMetode = namaMetode,
                        kategoriMetode = kategoriMetode,
                        nomorRekening = nomorRekening,
                        namaPemilikRekening = namaPemilikRekening
                    )
                    if (!res.isValid) {
                        methodValidationResult = res
                        return@Button
                    }
                    methodValidationResult = null
                    onSave(
                        PaymentMethodEntity(
                            id = initialMethod?.id ?: 0L,
                            namaMetode = namaMetode.trim(),
                            kategoriMetode = kategoriMetode,
                            namaBankAtauPenyedia = namaBankAtauPenyedia.trim(),
                            nomorRekening = if (kategoriMetode == "TUNAI") "" else nomorRekening.trim(),
                            namaPemilikRekening = namaPemilikRekening.trim(),
                            fotoQrisUri = fotoQrisUri,
                            logoBankUri = logoBankUri,
                            keteranganPembayaran = keteranganPembayaran.trim(),
                            isAktif = isAktif
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                modifier = Modifier.testTag("btn_save_payment_method")
            ) {
                Text("Simpan Metode", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun SheetImportPreviewDialog(
    preview: SheetImportPreview,
    onSelectSheet: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preview & Validasi Sebelum Import", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(preview.fileName, style = MaterialTheme.typography.labelSmall, color = SeaBluePrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Pilih Sheet yang Ingin Diimpor:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    preview.availableSheets.forEach { sheetName ->
                        FilterChip(
                            selected = preview.selectedSheet == sheetName,
                            onClick = { onSelectSheet(sheetName) },
                            label = { Text(sheetName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Hasil Deteksi & Validasi Otomatis (${preview.rows.size} Baris Data):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Data Duplikat Terdeteksi: ${preview.detectedDuplicates} (akan diperbarui tanpa menghapus data lama)", style = MaterialTheme.typography.bodySmall)
                        Text("• Validasi Tanggal Tidak Sesuai: ${preview.invalidDatesCount}", style = MaterialTheme.typography.bodySmall)
                        Text("• Validasi Nominal Tidak Sesuai: ${preview.invalidNominalsCount}", style = MaterialTheme.typography.bodySmall)
                        Text("• Validasi Nomor HP Tidak Sesuai: ${preview.invalidPhonesCount}", style = MaterialTheme.typography.bodySmall)

                        preview.mismatchedColumnsWarning.forEach { warn ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarningYellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(warn, style = MaterialTheme.typography.labelSmall, color = StatusWarningYellow)
                            }
                        }
                        preview.rowErrors.take(4).forEach { err ->
                            Text("⚠ $err", style = MaterialTheme.typography.labelSmall, color = StatusOverdueRed)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Tabel Preview Baris Data:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier
                            .background(NavyDark, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        preview.headers.forEach { h ->
                            Text(
                                text = h,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }

                    preview.rows.take(25).forEachIndexed { idx, row ->
                        Row(
                            modifier = Modifier
                                .background(if (idx % 2 == 0) Color.Transparent else Color(0xFFF1F5F9))
                                .padding(8.dp)
                        ) {
                            preview.headers.indices.forEach { cIdx ->
                                Text(
                                    text = row.getOrNull(cIdx) ?: "-",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }
                    Button(
                        onClick = onConfirmImport,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusPaidGreen),
                        modifier = Modifier
                            .weight(2f)
                            .testTag("btn_confirm_import_sheet")
                    ) {
                        Text("KONFIRMASI & IMPORT SEKARANG", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
