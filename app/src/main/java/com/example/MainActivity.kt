package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.cloud.CloudSyncAndMigrationManager
import com.example.cloud.GoogleAuthGateScreen
import com.example.cloud.GoogleAuthManager
import com.example.data.local.CustomerEntity
import com.example.data.local.FormDraftEntity
import com.example.data.local.RentalTransactionEntity
import com.example.pdf.InvoicePdfGenerator
import com.example.reset.HardResetBlockingGateScreen
import com.example.reset.HardResetUiState
import com.example.storage.CustomerPhotoStorageHelper
import com.example.ui.components.AppCustomBackgroundLayer
import com.example.ui.components.BlockingOperationProgressOverlay
import com.example.ui.components.FirstInstallOnboardingDialog
import com.example.ui.components.FormDraftTypes
import com.example.ui.components.FormDraftsManagerDialog
import com.example.ui.components.GlobalOperationLoadingBanner
import com.example.ui.components.GlobalOperationNotificationBanner
import com.example.ui.components.InterruptedProcessRecoveryBanner
import com.example.ui.components.LocationDocSlots
import com.example.ui.components.SmartLoadingProcessDialog
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FreezerReturnScreen
import com.example.ui.screens.ReportsAndInvestmentScreen
import com.example.ui.screens.SettingsAndSyncScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.UnitsScreen
import com.example.ui.screens.UsageLocationScreen
import com.example.ui.theme.AppAppearanceConfig
import com.example.ui.theme.AppColorTokens
import com.example.ui.theme.LightBlueAccent
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SEWAinAjaFreezerTheme
import com.example.ui.theme.SeaBluePrimary
import com.example.ui.theme.TextNavActive
import com.example.viewmodel.FreezerManagementViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File

data class BottomNavDestination(
    val index: Int,
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

val MAIN_NAV_DESTINATIONS = listOf(
    BottomNavDestination(0, "Dashboard", Icons.Default.Dashboard, "nav_dashboard"),
    BottomNavDestination(1, "Data Unit", Icons.Default.Kitchen, "nav_units"),
    BottomNavDestination(2, "Pelanggan", Icons.Default.People, "nav_customers"),
    BottomNavDestination(3, "Transaksi", Icons.Default.ReceiptLong, "nav_transactions"),
    BottomNavDestination(4, "Laporan", Icons.Default.Assessment, "nav_reports"),
    BottomNavDestination(5, "Pengaturan", Icons.Default.Settings, "nav_settings")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0A2540")),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.parseColor("#0A2540"))
        )
        setContent {
            SEWAinAjaFreezerMainApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SEWAinAjaFreezerMainApp(
    vm: FreezerManagementViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val savedAppearance by vm.appAppearanceConfig.collectAsStateWithLifecycle()
    val previewAppearance by vm.previewAppearanceConfig.collectAsStateWithLifecycle()
    val activeAppearance = previewAppearance ?: savedAppearance

    SEWAinAjaFreezerTheme(darkTheme = false, appearanceConfig = activeAppearance) {
    val firebaseAvailable = remember(context) { FirebaseApp.getApps(context).isNotEmpty() }
    val authFlow = remember(firebaseAvailable) {
        if (firebaseAvailable) GoogleAuthManager.authStateFlow(Firebase.auth) else flowOf<FirebaseUser?>(null)
    }
    val initialUser = remember(firebaseAvailable) {
        if (firebaseAvailable) Firebase.auth.currentUser else null
    }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = initialUser)
    val hardResetState by vm.hardResetUiState.collectAsStateWithLifecycle()
    var authLoading by remember { mutableStateOf(firebaseAvailable && initialUser == null) }
    var authError by remember { mutableStateOf<String?>(null) }

    // AUTOMATIC ONE-SHOT STARTUP HARD RESET GATE:
    // Executes immediately on startup BEFORE GoogleAuthGateScreen or the main Dashboard loads,
    // and again right after authentication (for cloud wipe) BEFORE any Firestore listeners attach.
    val startupResetRequired = vm.isStartupHardResetRequired(currentUser?.uid)
    LaunchedEffect(firebaseAvailable, currentUser?.uid, startupResetRequired) {
        if (startupResetRequired) {
            vm.executeStartupHardResetGate(currentUser)
        } else if (currentUser != null) {
            vm.bindAuthenticatedUser(currentUser!!)
        }
    }

    LaunchedEffect(firebaseAvailable, startupResetRequired) {
        if (!startupResetRequired && firebaseAvailable && Firebase.auth.currentUser == null) {
            authLoading = true
            GoogleAuthManager.attemptAutoSignIn(context, Firebase.auth)
            authLoading = false
        } else if (!startupResetRequired) {
            authLoading = false
        }
    }

    if (startupResetRequired || hardResetState is HardResetUiState.Wiping || hardResetState is HardResetUiState.Failed) {
        HardResetBlockingGateScreen(
            state = if (hardResetState is HardResetUiState.Idle) {
                HardResetUiState.Wiping(
                    stepTitle = "Menyiapkan Hard Reset Total",
                    stepDetail = "Menghapus seluruh data lokal dan cloud agar aplikasi kembali kosong 100%...",
                    progressPercent = 5
                )
            } else {
                hardResetState
            },
            onRetry = {
                vm.executeStartupHardResetGate(currentUser)
            }
        )
        return@SEWAinAjaFreezerTheme
    }

    if (firebaseAvailable && currentUser == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppCustomBackgroundLayer(
                appearance = activeAppearance,
                activeTabIndex = -1,
                isLoginScreen = true
            )
            GoogleAuthGateScreen(
                isLoading = authLoading,
                errorMessage = authError,
                onGoogleSignInClick = {
                    coroutineScope.launch {
                        authLoading = true
                        authError = null
                        val res = GoogleAuthManager.onGoogleSignInClicked(context, Firebase.auth)
                        res.onSuccess { user ->
                            // Route through Hard Reset gate FIRST so cloud is wiped before listeners bind
                            vm.executeStartupHardResetGate(user)
                        }.onFailure { err ->
                            authError = err.localizedMessage ?: "Gagal masuk dengan akun Google."
                        }
                        authLoading = false
                    }
                }
            )
        }
        return@SEWAinAjaFreezerTheme
    }

    val sessionKey = currentUser?.uid ?: "offline_local_fallback"
    key(sessionKey) {
        val activeUser = currentUser
        LaunchedEffect(activeUser?.uid) {
            if (activeUser != null && !vm.isStartupHardResetRequired(activeUser.uid)) {
                vm.bindAuthenticatedUser(activeUser)
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        var currentTab by rememberSaveable { mutableIntStateOf(0) }
        var showUsageLocationScreen by rememberSaveable { mutableStateOf(false) }
        var showFreezerReturnScreen by rememberSaveable { mutableStateOf(false) }
        var selectedTxFromDashboard by remember { mutableStateOf<RentalTransactionEntity?>(null) }
        var preselectedCustomerForNewTx by remember { mutableStateOf<CustomerEntity?>(null) }
        var showDraftsManagerDialog by remember { mutableStateOf(false) }
        var pendingDraftToOpen by remember { mutableStateOf<FormDraftEntity?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                com.example.contacts.ContactAccessManager.evaluatePermissionState(context)
                vm.refreshPermissionStatuses()
                vm.onAppForegrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val units by vm.units.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val returns by vm.returns.collectAsStateWithLifecycle()
    val extensions by vm.extensionHistory.collectAsStateWithLifecycle()
    val metrics by vm.dashboardMetrics.collectAsStateWithLifecycle()
    val bannerMsg by vm.statusBannerMessage.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatusIndicator.collectAsStateWithLifecycle()
    val cloudSyncStatus by vm.cloudSyncStatus.collectAsStateWithLifecycle()
    val companyProfile by vm.companyProfile.collectAsStateWithLifecycle()
    val sewaHakMilikPeriods by vm.sewaHakMilikPeriods.collectAsStateWithLifecycle()
    val paymentMethods by vm.paymentMethods.collectAsStateWithLifecycle()
    val paymentProofs by vm.paymentProofs.collectAsStateWithLifecycle()
    val customerPhotos by vm.customerPhotos.collectAsStateWithLifecycle()
    val showOnboarding by vm.showFirstInstallOnboarding.collectAsStateWithLifecycle()
    val permissionStatuses by vm.permissionStatuses.collectAsStateWithLifecycle()
    val transactionUnitItems by vm.transactionUnitItems.collectAsStateWithLifecycle()
    val unitPaymentAllocations by vm.unitPaymentAllocations.collectAsStateWithLifecycle()
    val customerTimelineEvents by vm.customerTimelineEvents.collectAsStateWithLifecycle()
    val drivePhotoMetadataList by vm.drivePhotoMetadataList.collectAsStateWithLifecycle()
    val formDrafts by vm.formDrafts.collectAsStateWithLifecycle()
    val unitRentalPrices by vm.unitRentalPrices.collectAsStateWithLifecycle()
    val transactionOfficerAuditLogs by vm.transactionOfficerAuditLogs.collectAsStateWithLifecycle()
    val loggedInOfficerName by vm.loggedInOfficerName.collectAsStateWithLifecycle()
    val loggedInOfficerAccountId by vm.loggedInOfficerAccountId.collectAsStateWithLifecycle()
    val operationLoadingState by vm.operationLoadingState.collectAsStateWithLifecycle()
    val operationNotificationEvent by vm.operationNotificationEvent.collectAsStateWithLifecycle()
    val globalProcessState by vm.globalProcessState.collectAsStateWithLifecycle()
    val interruptedProcessNotice by vm.interruptedProcessNotice.collectAsStateWithLifecycle()

    val resolvedNavContainerColor = activeAppearance.navBarColor
    val resolvedNavContentColor = AppAppearanceConfig.ensureReadableTextOn(Color.White, resolvedNavContainerColor)
    val resolvedPrimaryIndicatorColor = activeAppearance.primaryColor
    val resolvedActiveNavContentColor = AppAppearanceConfig.ensureReadableTextOn(Color.White, resolvedPrimaryIndicatorColor)
    val resolvedInactiveNavContentColor = resolvedNavContentColor.copy(alpha = 0.72f)

    if (showOnboarding) {
        FirstInstallOnboardingDialog(
            permissionStatuses = permissionStatuses,
            onApproveAndContinue = { vm.completeFirstInstallOnboarding("SETUJUI_LANJUTKAN") },
            onSetupLater = { vm.completeFirstInstallOnboarding("ATUR_NANTI") },
            onRefreshPermissions = { vm.refreshPermissionStatuses() }
        )
    }

    if (showDraftsManagerDialog) {
        FormDraftsManagerDialog(
            drafts = formDrafts,
            onResumeDraft = { draft ->
                showDraftsManagerDialog = false
                pendingDraftToOpen = draft
                when (draft.formType) {
                    FormDraftTypes.UNIT -> {
                        showUsageLocationScreen = false
                        showFreezerReturnScreen = false
                        currentTab = 1
                    }
                    FormDraftTypes.CUSTOMER -> {
                        showUsageLocationScreen = false
                        showFreezerReturnScreen = false
                        currentTab = 2
                    }
                    FormDraftTypes.TRANSACTION -> {
                        showUsageLocationScreen = false
                        showFreezerReturnScreen = false
                        currentTab = 3
                    }
                    FormDraftTypes.RETURN -> {
                        showUsageLocationScreen = false
                        showFreezerReturnScreen = true
                    }
                }
            },
            onDeleteDraft = { draft ->
                vm.deleteFormDraftByUuid(draft.uuid, showToast = true)
            },
            onDismiss = { showDraftsManagerDialog = false }
        )
    }

    if (showFreezerReturnScreen) {
        BackHandler {
            showFreezerReturnScreen = false
        }
    } else if (showUsageLocationScreen) {
        BackHandler {
            showUsageLocationScreen = false
        }
    } else if (currentTab != 0) {
        BackHandler {
            currentTab = 0
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            showUsageLocationScreen = false
                            showFreezerReturnScreen = false
                            currentTab = 5
                        }
                    ) {
                        val hasCustomProfilePic = remember(companyProfile.profilePictureUri) {
                            val uri = companyProfile.profilePictureUri
                            uri.isNotBlank() && (
                                uri.startsWith("content://") ||
                                    uri.startsWith("file://") ||
                                    File(uri).exists()
                                )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(SeaBluePrimary.copy(alpha = 0.25f))
                                .border(1.dp, LightBlueAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (hasCustomProfilePic) {
                                AsyncImage(
                                    model = if (companyProfile.profilePictureUri.startsWith("/")) {
                                        File(companyProfile.profilePictureUri)
                                    } else {
                                        companyProfile.profilePictureUri
                                    },
                                    contentDescription = "Foto Profil Perusahaan",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AcUnit,
                                    contentDescription = "Logo SEWAin Aja Freezer SOC",
                                    tint = LightBlueAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = companyProfile.namaPerusahaan.ifBlank { "SEWAin Aja Freezer SOC" },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitleText = when {
                                showFreezerReturnScreen -> "PENGEMBALIAN FREEZER"
                                showUsageLocationScreen -> "LOKASI PENGGUNAAN FREEZER"
                                else -> MAIN_NAV_DESTINATIONS[currentTab].title.uppercase()
                            }
                            Text(
                                text = "$subtitleText • $cloudSyncStatus",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (cloudSyncStatus) {
                                    CloudSyncAndMigrationManager.STATUS_FAILED -> Color(0xFFFCA5A5)
                                    CloudSyncAndMigrationManager.STATUS_WAITING_NETWORK -> Color(0xFFFDE68A)
                                    else -> LightBlueAccent
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showDraftsManagerDialog = true },
                        modifier = Modifier.testTag("btn_topbar_drafts")
                    ) {
                        BadgedBox(
                            badge = {
                                if (formDrafts.isNotEmpty()) {
                                    Badge {
                                        Text("${formDrafts.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "Daftar Draft Tersimpan",
                                tint = if (formDrafts.isNotEmpty()) LightBlueAccent else Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            showUsageLocationScreen = false
                            showFreezerReturnScreen = !showFreezerReturnScreen
                        },
                        modifier = Modifier.testTag("btn_topbar_freezer_return")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AssignmentReturn,
                            contentDescription = "Serah Terima Pengembalian Freezer",
                            tint = if (showFreezerReturnScreen) LightBlueAccent else Color.White
                        )
                    }
                    IconButton(
                        onClick = {
                            showFreezerReturnScreen = false
                            showUsageLocationScreen = !showUsageLocationScreen
                        },
                        modifier = Modifier.testTag("btn_topbar_usage_location")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Lokasi Penggunaan Freezer",
                            tint = if (showUsageLocationScreen) LightBlueAccent else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = resolvedNavContainerColor,
                    titleContentColor = resolvedNavContentColor
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = resolvedNavContainerColor,
                contentColor = resolvedNavContentColor
            ) {
                MAIN_NAV_DESTINATIONS.forEach { dest ->
                    val isSelected = !showUsageLocationScreen && !showFreezerReturnScreen && currentTab == dest.index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            showUsageLocationScreen = false
                            showFreezerReturnScreen = false
                            currentTab = dest.index
                        },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = dest.title,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = resolvedActiveNavContentColor,
                            selectedTextColor = resolvedNavContentColor,
                            indicatorColor = resolvedPrimaryIndicatorColor,
                            unselectedIconColor = resolvedInactiveNavContentColor,
                            unselectedTextColor = resolvedInactiveNavContentColor
                        ),
                        modifier = Modifier.testTag(dest.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AppCustomBackgroundLayer(
                appearance = activeAppearance,
                activeTabIndex = currentTab,
                isLoginScreen = false
            )

            Column(modifier = Modifier.fillMaxSize()) {
                // Recovery banner if a previous process was interrupted mid-flight (app closed/crash)
                InterruptedProcessRecoveryBanner(
                    interrupted = interruptedProcessNotice,
                    onSyncNow = {
                        vm.dismissInterruptedProcessNotice()
                        vm.syncCloudMirrorNow()
                    },
                    onDismiss = { vm.dismissInterruptedProcessNotice() }
                )

                // Centralized Real-State Loading Banner (suppressed when modal SmartLoadingProcessDialog is open)
                if (globalProcessState?.isVisible != true) {
                    GlobalOperationLoadingBanner(loadingState = operationLoadingState)
                }

                // Centralized Real Completion / Error (+ Retry) Notification Banner
                GlobalOperationNotificationBanner(
                    event = operationNotificationEvent,
                    onDismiss = { vm.dismissOperationNotification() }
                )

                // Legacy Instant Feedback Banner (only shown if no structured notification active)
                AnimatedVisibility(visible = bannerMsg != null && operationNotificationEvent == null) {
                    Surface(
                        color = resolvedPrimaryIndicatorColor,
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = bannerMsg ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = resolvedActiveNavContentColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { vm.clearBannerMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup Pesan", tint = resolvedActiveNavContentColor, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                when {
                    showFreezerReturnScreen -> {
                        FreezerReturnScreen(
                            transactions = transactions,
                            customers = customers,
                            units = units,
                            returnsHistory = returns,
                            loggedInOfficerName = loggedInOfficerName,
                            formDrafts = formDrafts,
                            pendingDraftToOpen = pendingDraftToOpen,
                            onPendingDraftConsumed = { pendingDraftToOpen = null },
                            onSaveDraft = { formType, targetId, title, sub, payload, uuid, onSaved ->
                                vm.saveFormDraft(formType, targetId, title, sub, payload, uuid, onSaved)
                            },
                            onDeleteDraft = { uuid -> vm.deleteFormDraftByUuid(uuid) },
                            onSubmitReturn = { returnEntity, onPdfReady ->
                                vm.submitFreezerReturn(returnEntity = returnEntity, onComplete = onPdfReady)
                            },
                            onGenerateReturnPdf = { ret, onPdfReady ->
                                vm.generateAndGetReturnPdf(ret, onPdfReady)
                            }
                        )
                    }

                    showUsageLocationScreen -> {
                        UsageLocationScreen(
                            transactions = transactions,
                            customers = customers,
                            units = units,
                            onCaptureGpsLocation = { cb ->
                                vm.captureCurrentGpsLocation(cb)
                            },
                            onUpdateTransactionLocation = { updatedTx ->
                                vm.createOrUpdateTransaction(updatedTx, autoGeneratePdf = false)
                            },
                            customerPhotos = customerPhotos,
                            drivePhotoMetadataList = drivePhotoMetadataList,
                            isUploadingPhoto = operationLoadingState.isLoading,
                            onSaveLocationPhoto = { cust, tx, docSlot, source, lat, lon ->
                                val isFreezerSlot = docSlot == LocationDocSlots.SLOT_FREEZER_DI_LOKASI
                                vm.saveCustomerDocumentationPhoto(
                                    customer = cust,
                                    category = CustomerPhotoStorageHelper.KAT_FOTO_LOKASI,
                                    documentationName = if (isFreezerSlot) "Freezer di Lokasi" else "Tampak Depan Lokasi",
                                    source = source,
                                    transactionId = tx.id,
                                    unitId = tx.unitId.takeIf { it > 0L },
                                    latitude = lat,
                                    longitude = lon,
                                    catatan = if (isFreezerSlot) "Dokumentasi freezer setelah ditempatkan di lokasi" else "Dokumentasi tampak depan lokasi usaha"
                                )
                            },
                            onDeleteCustomerPhoto = { photo -> vm.deleteCustomerDocumentationPhoto(photo) },
                            onRetryLocationPhotoUpload = { photo -> vm.retryLocationPhotoUpload(photo) },
                            onBack = { showUsageLocationScreen = false }
                        )
                    }

                    else -> {
                        when (currentTab) {
                            0 -> DashboardScreen(
                                metrics = metrics,
                                syncStatus = syncStatus,
                                transactions = transactions,
                                customers = customers,
                                units = units,
                                onNavigateToUnits = { currentTab = 1 },
                                onNavigateToCustomers = { currentTab = 2 },
                                onNavigateToTransactions = { currentTab = 3 },
                                onNavigateToUsageLocations = { showUsageLocationScreen = true },
                                onNavigateToReturns = { showFreezerReturnScreen = true },
                                onOpenTransactionDetail = { tx ->
                                    selectedTxFromDashboard = tx
                                    currentTab = 3
                                },
                                onTriggerNotificationTest = { vm.triggerImmediateDueNotificationTest() }
                            )

                            1 -> UnitsScreen(
                                units = units,
                                transactions = transactions,
                                unitRentalPrices = unitRentalPrices,
                                formDrafts = formDrafts,
                                pendingDraftToOpen = pendingDraftToOpen,
                                onPendingDraftConsumed = { pendingDraftToOpen = null },
                                onSaveDraft = { formType, targetId, title, sub, payload, uuid, onSaved ->
                                    vm.saveFormDraft(formType, targetId, title, sub, payload, uuid, onSaved)
                                },
                                onDeleteDraft = { uuid -> vm.deleteFormDraftByUuid(uuid) },
                                onSaveUnit = { vm.saveUnit(it) },
                                onSaveUnitWithPrices = { unit, prices -> vm.saveUnitWithRentalPrices(unit, prices) },
                                onDeleteUnit = { vm.deleteUnit(it) }
                            )

                            2 -> CustomersScreen(
                                customers = customers,
                                transactions = transactions,
                                customerPhotos = customerPhotos,
                                paymentProofs = paymentProofs,
                                transactionUnitItems = transactionUnitItems,
                                unitPaymentAllocations = unitPaymentAllocations,
                                customerTimelineEvents = customerTimelineEvents,
                                returnsHistory = returns,
                                paymentMethods = paymentMethods,
                                formDrafts = formDrafts,
                                pendingDraftToOpen = pendingDraftToOpen,
                                onPendingDraftConsumed = { pendingDraftToOpen = null },
                                onSaveDraft = { formType, targetId, title, sub, payload, uuid, onSaved ->
                                    vm.saveFormDraft(formType, targetId, title, sub, payload, uuid, onSaved)
                                },
                                onDeleteDraft = { uuid -> vm.deleteFormDraftByUuid(uuid) },
                                onSaveCustomer = { vm.saveCustomer(it) },
                                onDeleteCustomer = { vm.deleteCustomer(it) },
                                onSaveCustomerPhoto = { customer, cat, docName, source, txId, lat, lon, notes ->
                                    vm.saveCustomerDocumentationPhoto(
                                        customer = customer,
                                        category = cat,
                                        documentationName = docName,
                                        source = source,
                                        transactionId = txId,
                                        latitude = lat,
                                        longitude = lon,
                                        catatan = notes
                                    )
                                },
                                onDeleteCustomerPhoto = { photo ->
                                    vm.deleteCustomerDocumentationPhoto(photo)
                                },
                                onOpenCustomerFolder = { customer ->
                                    vm.openCustomerFolderInGallery(customer)
                                },
                                onAddUnitForCustomer = { cust ->
                                    preselectedCustomerForNewTx = cust
                                    showUsageLocationScreen = false
                                    showFreezerReturnScreen = false
                                    currentTab = 3
                                },
                                onPrintContractPdf = { tx ->
                                    vm.generateAndGetInvoicePdf(tx) { pdfFile ->
                                        InvoicePdfGenerator.printInvoicePdf(
                                            context = context,
                                            pdfFile = pdfFile,
                                            jobName = "Kontrak_${tx.nomorInvoice}"
                                        )
                                    }
                                },
                                onAllocatePartialPayment = { txId, totalPaid, allocMap, payDate, method, sender, notes ->
                                    vm.allocatePartialPaymentToUnits(
                                        transactionId = txId,
                                        totalPaidAmount = totalPaid,
                                        unitAllocationsMap = allocMap,
                                        paymentDate = payDate,
                                        metodePembayaran = method,
                                        namaPengirim = sender,
                                        catatanPembayaran = notes
                                    )
                                }
                            )

                            3 -> TransactionsScreen(
                                transactions = transactions,
                                customers = customers,
                                units = units,
                                unitRentalPrices = unitRentalPrices,
                                loggedInOfficerName = loggedInOfficerName,
                                loggedInOfficerAccountId = loggedInOfficerAccountId,
                                transactionOfficerAuditLogs = transactionOfficerAuditLogs,
                                formDrafts = formDrafts,
                                pendingDraftToOpen = pendingDraftToOpen,
                                onPendingDraftConsumed = { pendingDraftToOpen = null },
                                onSaveDraft = { formType, targetId, title, sub, payload, uuid, onSaved ->
                                    vm.saveFormDraft(formType, targetId, title, sub, payload, uuid, onSaved)
                                },
                                onDeleteDraft = { uuid -> vm.deleteFormDraftByUuid(uuid) },
                                onRecordUnitHakMilikPayment = { txId, unitItemId, nominal, metode, payDate, sender, notes, proofUri, incStep ->
                                    vm.recordUnitHakMilikPayment(
                                        transactionId = txId,
                                        unitItemId = unitItemId,
                                        nominalPembayaran = nominal,
                                        metodePembayaran = metode,
                                        tanggalPembayaran = payDate,
                                        namaPengirim = sender,
                                        catatanPembayaran = notes,
                                        fotoBuktiUri = proofUri,
                                        incrementPaymentSequence = incStep
                                    )
                                },
                                onCorrectOfficerWithAuditLog = { txId, newName, newSigUri, reason ->
                                    vm.correctTransactionOfficerWithAuditLog(
                                        transactionId = txId,
                                        newOfficerName = newName,
                                        newSignatureUri = newSigUri,
                                        correctionReason = reason
                                    )
                                },
                                sewaHakMilikPeriods = sewaHakMilikPeriods,
                                paymentMethods = paymentMethods,
                                paymentProofs = paymentProofs,
                                transactionUnitItems = transactionUnitItems,
                                unitPaymentAllocations = unitPaymentAllocations,
                                externallySelectedTx = selectedTxFromDashboard,
                                preselectedCustomerForNewTx = preselectedCustomerForNewTx,
                                onClearExternalTx = { selectedTxFromDashboard = null },
                                onClearPreselectedCustomer = { preselectedCustomerForNewTx = null },
                                onSaveTransaction = { tx, onComplete ->
                                    vm.createOrUpdateTransaction(tx, autoGeneratePdf = true, onComplete = onComplete)
                                },
                                onSaveMultiUnitTransaction = { tx, unitItems, onComplete ->
                                    vm.createOrUpdateMultiUnitTransaction(tx, unitItems, autoGeneratePdf = true, onComplete = onComplete)
                                },
                                onResolveFreshUnitPrice = { unitId, periodDays, jenisProgram ->
                                    vm.resolveFreshUnitPriceFromDb(unitId, periodDays, jenisProgram)
                                },
                                onExtendRentalByDays = { oldTx, days, biayaSewa, ongkirTambahan, dibayar, catatan ->
                                    vm.extendRentalTransactionByDays(
                                        oldTransaction = oldTx,
                                        additionalDays = days,
                                        biayaSewaPerpanjangan = biayaSewa,
                                        ongkirTambahan = ongkirTambahan,
                                        nominalBayarSekarang = dibayar,
                                        catatan = catatan
                                    )
                                },
                                onGenerateInvoicePdf = { tx, onReady ->
                                    vm.generateAndGetInvoicePdf(tx, onReady)
                                },
                                onCaptureGpsLocation = { cb ->
                                    vm.captureCurrentGpsLocation(cb)
                                },
                                onPostMessage = { vm.postMessage(it) },
                                onSaveCustomerInline = { cust, onSaved ->
                                    vm.saveCustomerInline(cust, onSaved)
                                },
                                onDeleteTransaction = { txToDelete ->
                                    vm.deleteTransaction(txToDelete)
                                },
                                onSavePaymentProof = { proof ->
                                    vm.savePaymentProof(proof)
                                },
                                onDeletePaymentProof = { proofToDelete ->
                                    vm.deletePaymentProof(proofToDelete)
                                },
                                onAllocatePartialPayment = { txId, totalPaid, allocMap, payDate, method, sender, notes ->
                                    vm.allocatePartialPaymentToUnits(
                                        transactionId = txId,
                                        totalPaidAmount = totalPaid,
                                        unitAllocationsMap = allocMap,
                                        paymentDate = payDate,
                                        metodePembayaran = method,
                                        namaPengirim = sender,
                                        catatanPembayaran = notes
                                    )
                                },
                                customerPhotos = customerPhotos,
                                drivePhotoMetadataList = drivePhotoMetadataList,
                                extensions = extensions,
                                isProcessingOperation = operationLoadingState.isLoading,
                                onSaveLocationPhoto = { cust, txId, unitId, docSlot, source, lat, lon ->
                                    val isFreezerSlot = docSlot == LocationDocSlots.SLOT_FREEZER_DI_LOKASI
                                    vm.saveCustomerDocumentationPhoto(
                                        customer = cust,
                                        category = CustomerPhotoStorageHelper.KAT_FOTO_LOKASI,
                                        documentationName = if (isFreezerSlot) "Freezer di Lokasi" else "Tampak Depan Lokasi",
                                        source = source,
                                        transactionId = txId,
                                        unitId = unitId,
                                        latitude = lat,
                                        longitude = lon,
                                        catatan = if (isFreezerSlot) "Dokumentasi freezer di lokasi (Form Penyewaan)" else "Dokumentasi tampak depan lokasi (Form Penyewaan)"
                                    )
                                },
                                onDeleteCustomerPhoto = { photo -> vm.deleteCustomerDocumentationPhoto(photo) },
                                onRetryLocationPhotoUpload = { photo -> vm.retryLocationPhotoUpload(photo) },
                                onSubmitDetailedRenewal = { req, onDone ->
                                    vm.executeDetailedRenewalTransaction(req, onDone)
                                }
                            )

                            4 -> ReportsAndInvestmentScreen(
                                units = units,
                                transactions = transactions,
                                extensions = extensions,
                                metrics = metrics,
                                transactionUnitItems = transactionUnitItems,
                                onUpdateUnitInvestment = { vm.saveUnit(it) },
                                onExportFullXlsx = { vm.exportFullXlsxAndShare() },
                                onExportCategoryCsv = { cat -> vm.exportSpecificCategoryCsvAndShare(cat) }
                            )

                            5 -> SettingsTabContent(
                                vm = vm,
                                firebaseAvailable = firebaseAvailable,
                                savedAppearance = savedAppearance,
                                previewAppearance = previewAppearance
                            )
                        }
                    }
                }
                }
            }
            if (globalProcessState?.isVisible != true) {
                BlockingOperationProgressOverlay(loadingState = operationLoadingState)
            }
            SmartLoadingProcessDialog(
                state = globalProcessState,
                onRetry = { vm.retryCurrentGlobalProcess() },
                onDismiss = { vm.dismissGlobalProcessDialog() },
                onCancel = { vm.cancelCurrentGlobalProcess() }
            )
        }
        }
    }
    }
}

@Composable
private fun SettingsTabContent(
    vm: FreezerManagementViewModel,
    firebaseAvailable: Boolean,
    savedAppearance: AppAppearanceConfig,
    previewAppearance: AppAppearanceConfig?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val syncLogs by vm.syncLogs.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatusIndicator.collectAsStateWithLifecycle()
    val lastSyncTime by vm.lastSyncTime.collectAsStateWithLifecycle()
    val googleSheetUrl by vm.googleSheetUrl.collectAsStateWithLifecycle()
    val webhookUrl by vm.webhookScriptUrl.collectAsStateWithLifecycle()
    val companyProfile by vm.companyProfile.collectAsStateWithLifecycle()
    val perjanjianSewa by vm.perjanjianSewaText.collectAsStateWithLifecycle()
    val autoSync by vm.autoSyncEnabled.collectAsStateWithLifecycle()
    val maskNik by vm.maskNikInPdf.collectAsStateWithLifecycle()
    val importPreview by vm.importPreview.collectAsStateWithLifecycle()
    val lastImportReport by vm.lastImportReport.collectAsStateWithLifecycle()
    val pdfTemplateSettings by vm.pdfTemplateSettings.collectAsStateWithLifecycle()
    val sewaHakMilikPeriods by vm.sewaHakMilikPeriods.collectAsStateWithLifecycle()
    val lastApkBuildDate by vm.lastApkBuildDate.collectAsStateWithLifecycle()
    val isBuildingApk by vm.isBuildingApk.collectAsStateWithLifecycle()
    val apkBuildProgress by vm.apkBuildProgress.collectAsStateWithLifecycle()
    val apkBuildStatusText by vm.apkBuildStatusText.collectAsStateWithLifecycle()
    val apkBuildCompleted by vm.apkBuildCompleted.collectAsStateWithLifecycle()
    val generatedApkSizeMb by vm.generatedApkSizeMb.collectAsStateWithLifecycle()
    val paymentMethods by vm.paymentMethods.collectAsStateWithLifecycle()
    val onboardingDecision by vm.onboardingDecision.collectAsStateWithLifecycle()
    val permissionStatuses by vm.permissionStatuses.collectAsStateWithLifecycle()
    val managedDocuments by vm.managedDocuments.collectAsStateWithLifecycle()
    val storageIntegrityReport by vm.storageIntegrityReport.collectAsStateWithLifecycle()
    val localBackups by vm.localBackups.collectAsStateWithLifecycle()
    val backupSchedule by vm.backupSchedule.collectAsStateWithLifecycle()
    val lastRestoreMessage by vm.lastRestoreMessage.collectAsStateWithLifecycle()
    val deviceStorageSummary by vm.deviceStorageSummary.collectAsStateWithLifecycle()
    val smartNotificationSettings by vm.smartNotificationSettings.collectAsStateWithLifecycle()
    val googleAccountInfo by vm.googleAccountInfo.collectAsStateWithLifecycle()
    val cloudSyncStatus by vm.cloudSyncStatus.collectAsStateWithLifecycle()
    val cloudLastSyncTimestamp by vm.cloudLastSyncTimestamp.collectAsStateWithLifecycle()
    val cloudSyncError by vm.cloudSyncError.collectAsStateWithLifecycle()
    val cloudMigrationState by vm.cloudMigrationState.collectAsStateWithLifecycle()
    val cloudPhotoJobs by vm.cloudPhotoJobs.collectAsStateWithLifecycle()
    val cloudPendingSyncCount by vm.cloudPendingSyncCount.collectAsStateWithLifecycle()
    val imageQualitySettings by vm.imageQualitySettings.collectAsStateWithLifecycle()
    val imageStorageAnalytics by vm.imageStorageAnalytics.collectAsStateWithLifecycle()
    val driveConnectionStatus by vm.driveConnectionStatus.collectAsStateWithLifecycle()
    val driveBackupsList by vm.driveBackupsList.collectAsStateWithLifecycle()
    val backupScheduleConfig by vm.backupScheduleConfig.collectAsStateWithLifecycle()
    val backupAuditLogs by vm.backupAuditLogs.collectAsStateWithLifecycle()
    val restorePreviewSummary by vm.restorePreviewSummary.collectAsStateWithLifecycle()
    val isBackupOrRestoreBusy by vm.isBackupOrRestoreBusy.collectAsStateWithLifecycle()
    val smartImportPreview by vm.smartImportPreview.collectAsStateWithLifecycle()
    val smartImportReport by vm.smartImportReport.collectAsStateWithLifecycle()
    val isSmartImporting by vm.isSmartImporting.collectAsStateWithLifecycle()
    val drivePhotoMetadataList by vm.drivePhotoMetadataList.collectAsStateWithLifecycle()
    val driveStorageStats by vm.driveStorageStats.collectAsStateWithLifecycle()
    val drivePhotoCompressionConfig by vm.drivePhotoCompressionConfig.collectAsStateWithLifecycle()
    val driveFolderTreeSummary by vm.driveFolderTreeSummary.collectAsStateWithLifecycle()
    val drivePhotoLiveProgress by vm.drivePhotoLiveProgress.collectAsStateWithLifecycle()
    val openedDrivePhotoFile by vm.openedDrivePhotoFile.collectAsStateWithLifecycle()
    val openedDrivePhotoMetadata by vm.openedDrivePhotoMetadata.collectAsStateWithLifecycle()
    val driveMirrorDashboardState by vm.driveMirrorDashboardState.collectAsStateWithLifecycle()
    val syncTechnicalLogs by vm.syncTechnicalLogs.collectAsStateWithLifecycle()
    val consistencyAuditReport by vm.consistencyAuditReport.collectAsStateWithLifecycle()
    val isAuditingConsistency by vm.isAuditingConsistency.collectAsStateWithLifecycle()
    val driveQuarantineItems by vm.driveQuarantineItems.collectAsStateWithLifecycle()

    val driveAuthConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        vm.handleDriveConsentIntentResult(res.resultCode, res.data)
    }

    SettingsAndSyncScreen(
        companyProfile = companyProfile,
        perjanjianSewaText = perjanjianSewa,
        pdfTemplateSettings = pdfTemplateSettings,
        sewaHakMilikPeriods = sewaHakMilikPeriods,
        onUpdateSewaHakMilikPeriods = { vm.updateSewaHakMilikPeriods(it) },
        paymentMethods = paymentMethods,
        onSavePaymentMethod = { vm.savePaymentMethod(it) },
        onDeletePaymentMethod = { vm.deletePaymentMethod(it) },
        onTogglePaymentMethodActive = { method, active ->
            vm.savePaymentMethod(method.copy(isAktif = active))
        },
        onGenerateTemplatePreviewPdf = { onReady ->
            vm.generateTemplatePreviewPdf(onPdfReady = onReady)
        },
        appVersionName = vm.appVersionName,
        appBuildNumber = vm.appBuildNumber,
        lastApkBuildDate = lastApkBuildDate,
        isBuildingApk = isBuildingApk,
        apkBuildProgress = apkBuildProgress,
        apkBuildStatusText = apkBuildStatusText,
        apkBuildCompleted = apkBuildCompleted,
        generatedApkSizeMb = generatedApkSizeMb,
        onTriggerGenerateApkBuild = { vm.triggerGenerateApkBuild() },
        onSavePdfTemplateSettings = { vm.savePdfTemplateSettings(it) },
        onResetPdfTemplateSettings = { vm.resetPdfTemplateSettings() },
        onImportTemplateAssetFile = { uri, prefix, ext, cb ->
            vm.importTemplateAssetFile(uri, prefix, ext, cb)
        },
        googleSheetUrl = googleSheetUrl,
        webhookScriptUrl = webhookUrl,
        autoSyncEnabled = autoSync,
        maskNikInPdf = maskNik,
        lastSyncTime = lastSyncTime,
        syncStatusIndicator = syncStatus,
        importPreview = importPreview,
        lastImportReport = lastImportReport,
        syncLogs = syncLogs,
        onLoadFileForPreview = { uri, name, isCsv ->
            vm.loadXlsxOrCsvForPreview(uri, name, isCsv)
        },
        onLoadBuiltInTimelinePreview = { vm.loadBuiltInTimelineTemplateForPreview() },
        onSelectSheetInPreview = { vm.selectSheetInPreview(it) },
        onConfirmImport = { vm.confirmAndExecuteImport() },
        onDismissPreview = { vm.dismissImportPreview() },
        onImportFromGoogleSheets = { vm.importFromGoogleSheets(it) },
        onManualSyncNow = { vm.performManualGoogleSheetsSync() },
        onExportFullXlsx = { vm.exportFullXlsxAndShare() },
        onExportCategoryCsv = { cat -> vm.exportSpecificCategoryCsvAndShare(cat) },
        onExportAndShareApk = { vm.exportAndShareInstalledApk() },
        onSaveApkToUri = { uri -> vm.saveInstalledApkToUri(uri) },
        onSaveCompanyProfile = { profile ->
            vm.updateCompanyProfile(profile)
        },
        onSavePerjanjianSewa = { agreementText ->
            vm.updatePerjanjianSewa(agreementText)
        },
        onResetPerjanjianSewa = {
            vm.resetPerjanjianSewaToDefault()
        },
        onSaveSyncSettings = { sUrl, wUrl, auto, mask ->
            vm.updateSyncSettings(sUrl, wUrl, auto, mask)
        },
        onBackupDatabaseBeforeReset = {
            vm.backupDatabaseBeforeReset()
        },
        onResetSettingsOnly = {
            vm.resetSettingsOnly()
        },
        onResetTransactionsOnly = {
            vm.resetTransactionsOnly()
        },
        onResetToDefaultSoloRayaData = {
            vm.resetAllData(reseedDefaultSoloRaya = true)
        },
        onResetAllDataEmpty = {
            vm.resetAllData(reseedDefaultSoloRaya = false)
        },
        permissionStatuses = permissionStatuses,
        onboardingDecision = onboardingDecision,
        onRefreshPermissions = { vm.refreshPermissionStatuses() },
        onShowOnboardingAgain = { vm.reopenOnboardingDialog() },
        managedDocuments = managedDocuments,
        storageIntegrityReport = storageIntegrityReport,
        onRunIntegrityCheck = { vm.runStorageIntegrityCheck() },
        onUploadNewDocumentBitmap = { cat, bmp ->
            vm.uploadManagedDocumentFromBitmap(cat, bmp)
        },
        onUploadNewDocumentUri = { cat, uri ->
            vm.uploadManagedDocumentFromUri(cat, uri)
        },
        onShareManagedDocument = { doc -> vm.shareManagedDocument(doc) },
        onDeleteManagedDocument = { doc -> vm.deleteManagedDocument(doc) },
        localBackups = localBackups,
        backupSchedule = backupSchedule,
        lastRestoreMessage = lastRestoreMessage,
        onCreateLocalBackup = { scope -> vm.createLocalBackup(scope) },
        onRestoreFromBackupEntity = { entity -> vm.restoreFromLocalBackupEntity(entity) },
        onRestoreFromExternalZipUri = { uri -> vm.restoreFromExternalZipUri(uri) },
        onUpdateBackupSchedule = { sched -> vm.updateBackupSchedule(sched) },
        deviceStorageSummary = deviceStorageSummary,
        onRefreshStorageInfo = { vm.refreshStorageAndDocumentsState() },
        smartNotificationSettings = smartNotificationSettings,
        onSaveSmartNotificationSettings = { vm.updateSmartNotificationSettings(it) },
        onTriggerSmartNotificationCheck = { vm.triggerImmediateDueNotificationTest() },
        googleAccountInfo = googleAccountInfo,
        cloudSyncStatus = cloudSyncStatus,
        cloudLastSyncTimestamp = cloudLastSyncTimestamp,
        cloudSyncError = cloudSyncError,
        cloudMigrationState = cloudMigrationState,
        cloudPhotoJobs = cloudPhotoJobs,
        cloudPendingSyncCount = cloudPendingSyncCount,
        onManualCloudSync = { vm.triggerManualCloudSync() },
        onRetryFailedPhotos = { vm.retryFailedPhotoUploads() },
        onRetrySingleFailedPhoto = { jobId -> vm.retrySingleFailedPhoto(jobId) },
        imageQualitySettings = imageQualitySettings,
        imageStorageAnalytics = imageStorageAnalytics,
        onUpdateImageQualitySettings = { settings -> vm.updateImageQualitySettings(settings) },
        onCleanSmartStorageCache = { vm.cleanSmartStorageCache() },
        onResumeCloudMigration = { vm.startOrResumeCloudMigration() },
        onSwitchGoogleAccount = {
            if (firebaseAvailable) {
                coroutineScope.launch {
                    val res = GoogleAuthManager.switchGoogleAccount(context, Firebase.auth)
                    res.onSuccess { user ->
                        vm.executeStartupHardResetGate(user)
                        vm.postMessage("Berhasil beralih ke akun ${user.email ?: user.uid}.")
                    }.onFailure { err ->
                        vm.postMessage(err.localizedMessage ?: "Batal mengganti akun Google.")
                    }
                }
            }
        },
        onSignOutGoogleAccount = {
            vm.signOutGoogleAccount(context)
        },
        onExportCustomersExcelToUri = { uri -> vm.exportCustomersXlsxToUri(uri) },
        onExportTransactionsPdfToUri = { uri -> vm.exportTransactionsPdfToUri(uri) },
        onBackupToGoogleDriveUri = { uri, scopeName ->
            vm.createAndExportBackupToUri(uri, scopeName)
        },
        onShareBackupToGoogleDrive = { scopeName ->
            vm.shareBackupToGoogleDriveOrApps(scopeName)
        },
        driveConnectionStatus = driveConnectionStatus,
        driveBackupsList = driveBackupsList,
        backupScheduleConfig = backupScheduleConfig,
        backupAuditLogs = backupAuditLogs,
        restorePreviewSummary = restorePreviewSummary,
        isBackupOrRestoreBusy = isBackupOrRestoreBusy,
        smartImportPreview = smartImportPreview,
        smartImportReport = smartImportReport,
        isSmartImporting = isSmartImporting,
        onConnectOrSwitchDrive = { emailHint ->
            vm.authorizeGoogleDrive(emailHint) { intentSender ->
                driveAuthConsentLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
        },
        onDisconnectDrive = { vm.disconnectGoogleDrive() },
        onRunDriveBackupNow = { vm.runManualDriveBackupNow() },
        onRefreshDriveBackups = { vm.refreshDriveStatusAndAuditLogs() },
        onDownloadDriveBackupToUri = { item, uri -> vm.downloadDriveBackupToUri(item, uri) },
        onDeleteDriveBackup = { item -> vm.deleteDriveBackup(item) },
        onDeleteLocalBackup = { entity -> vm.deleteLocalBackupRecord(entity) },
        onInspectDriveBackupForRestore = { item -> vm.inspectDriveBackupForRestore(item) },
        onInspectLocalBackupForRestore = { entity -> vm.inspectLocalBackupForRestore(entity) },
        onInspectExternalZipForRestore = { uri -> vm.inspectExternalZipUriForRestore(uri) },
        onExecuteStructuredRestore = { mode -> vm.executeStructuredRestore(mode) },
        onDismissRestorePreview = { vm.dismissRestorePreview() },
        onSaveAutoBackupScheduleConfig = { cfg -> vm.updateAutoBackupScheduleConfig(cfg) },
        onPickSmartImportFileUri = { uri -> vm.loadSmartImportUri(uri) },
        onChangeSmartImportTargetAndSheet = { tgt, sheet -> vm.changeSmartImportTargetAndSheet(tgt, sheet) },
        onUpdateSmartImportColumnMapping = { idx, key -> vm.updateSmartImportColumnMapping(idx, key) },
        onChangeSmartImportDuplicatePolicy = { pol -> vm.changeSmartImportDuplicatePolicy(pol) },
        onExecuteSmartImport = { vm.executeSmartImport() },
        onDismissSmartImportPreview = { vm.dismissSmartImportPreview() },
        onExportSmartImportErrorReportXlsx = { uri -> vm.exportSmartImportErrorReportXlsx(uri) },
        onExportSmartImportErrorReportCsv = { uri -> vm.exportSmartImportErrorReportCsv(uri) },
        onExportExcelTemplateToUri = { tplType, uri -> vm.exportExcelTemplateToUri(tplType, uri) },
        drivePhotoMetadataList = drivePhotoMetadataList,
        driveStorageStats = driveStorageStats,
        drivePhotoCompressionConfig = drivePhotoCompressionConfig,
        driveFolderTreeSummary = driveFolderTreeSummary,
        drivePhotoLiveProgress = drivePhotoLiveProgress,
        openedDrivePhotoFile = openedDrivePhotoFile,
        openedDrivePhotoMetadata = openedDrivePhotoMetadata,
        onSyncDrivePhotosNow = { vm.syncDrivePhotosNow() },
        onVerifyDriveFoldersNow = { vm.ensureDriveFolderHierarchyNow() },
        onSaveDrivePhotoCompressionConfig = { cfg -> vm.updateDrivePhotoCompressionConfig(cfg) },
        onRetrySingleDrivePhotoUpload = { id -> vm.retrySingleDrivePhotoUpload(id) },
        onOpenDrivePhotoItem = { item -> vm.openDrivePhotoItem(item) },
        onDismissOpenedDrivePhotoDialog = { vm.dismissOpenedDrivePhotoDialog() },
        driveMirrorDashboardState = driveMirrorDashboardState,
        onSyncCloudMirrorNow = { vm.syncCloudMirrorNow() },
        onRetryFailedCloudMirrorSync = { vm.retryFailedCloudMirrorSync() },
        onResetLocalAndCloud = { backupFirst -> vm.resetDataLocalAndCloud(backupFirst) },
        onResetLocalOnly = { vm.resetDataLocalOnly() },
        consistencyAuditReport = consistencyAuditReport,
        isAuditingConsistency = isAuditingConsistency,
        syncTechnicalLogs = syncTechnicalLogs,
        onRunConsistencyAudit = { vm.runDataConsistencyAudit() },
        onCleanOrphanAndResync = { vm.cleanOrphanCloudAndResync() },
        driveQuarantineItems = driveQuarantineItems,
        onScanStaleCloud = { vm.scanStaleCloudFilesNow() },
        onPurgeVerifiedQuarantine = { vm.purgeVerifiedQuarantineNow() },
        appAppearanceConfig = savedAppearance,
        previewAppearanceConfig = previewAppearance,
        onPreviewAppearanceChange = { vm.setPreviewAppearanceConfig(it) },
        onSaveAppearanceConfig = { vm.saveAppAppearanceConfig(it) },
        onResetAppearanceConfig = { vm.resetAppAppearanceConfig() }
    )
}
