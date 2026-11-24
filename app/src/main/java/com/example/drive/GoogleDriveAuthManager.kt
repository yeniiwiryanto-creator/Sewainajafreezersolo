package com.example.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.example.util.SecurityCryptoUtils
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class DriveConnectionStatus(
    val isConnected: Boolean = false,
    val accountEmail: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val grantedScopes: List<String> = emptyList(),
    val lastDriveSyncAt: String = "",
    val hasPendingOfflineUpload: Boolean = false,
    val requiresReAuth: Boolean = false,
    val authErrorReason: String = "",
    val statusMessage: String = "Belum terhubung ke Google Drive"
)

sealed class DriveAuthorizationOutcome {
    data class Authorized(
        val accessToken: String,
        val grantedScopes: List<String>
    ) : DriveAuthorizationOutcome()

    data class NeedsUserConsent(
        val intentSender: IntentSender
    ) : DriveAuthorizationOutcome()

    data class Failed(
        val message: String
    ) : DriveAuthorizationOutcome()
}

/**
 * Typed result of OAuth 2.0 access token acquisition / refresh.
 */
sealed class DriveTokenResult {
    data class Valid(
        val accessToken: String,
        val expiresAtMs: Long
    ) : DriveTokenResult()

    data class RequiresReAuth(
        val reason: String,
        val intentSender: IntentSender? = null
    ) : DriveTokenResult()

    data class TransientError(
        val reason: String
    ) : DriveTokenResult()
}

/**
 * Internal snapshot of a cached OAuth 2.0 token stored inside Android Keystore AES-256-GCM envelope.
 */
data class CachedDriveTokenEnvelope(
    val accessToken: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val accountEmail: String = "",
    val grantedScopes: List<String> = GoogleDriveAuthManager.MINIMAL_DRIVE_SCOPES
)

/**
 * Transport abstraction over Google Play Services OAuth 2.0 (`Identity.getAuthorizationClient` +
 * `GoogleAuthUtil.clearToken`) so production uses official Android Google OAuth 2.0 APIs and
 * automated tests can deterministically exercise valid, expired, refreshed, revoked, and re-login flows.
 */
interface DriveOAuthTransport {
    suspend fun acquireAccessTokenSilently(
        context: Context,
        accountEmail: String?
    ): DriveTokenResult

    suspend fun clearCachedPlatformToken(
        context: Context,
        staleAccessToken: String
    )
}

internal object PlayServicesDriveOAuthTransport : DriveOAuthTransport {
    override suspend fun acquireAccessTokenSilently(
        context: Context,
        accountEmail: String?
    ): DriveTokenResult = withContext(Dispatchers.IO) {
        try {
            val request = GoogleDriveAuthManager.buildAuthorizationRequest(accountEmail)
            val res: AuthorizationResult = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()

            val pendingIntent = res.pendingIntent
            if (res.hasResolution()) {
                return@withContext DriveTokenResult.RequiresReAuth(
                    reason = "Sesi OAuth Google Drive kedaluwarsa atau dicabut. Silakan login ulang Google Drive.",
                    intentSender = pendingIntent?.intentSender
                )
            }

            val token = res.accessToken?.trim().orEmpty()
            if (token.isBlank()) {
                return@withContext DriveTokenResult.RequiresReAuth(
                    reason = "Google Play Services tidak mengembalikan access token OAuth 2.0 yang valid."
                )
            }

            val now = System.currentTimeMillis()
            // Conservative 45-minute window from fresh Play Services issuance (real Google OAuth TTL is 60 min)
            DriveTokenResult.Valid(
                accessToken = token,
                expiresAtMs = now + GoogleDriveAuthManager.FRESH_TOKEN_TTL_MS
            )
        } catch (e: Exception) {
            val msg = e.localizedMessage.orEmpty()
            val isNetwork = e is java.io.IOException ||
                msg.contains("NETWORK", ignoreCase = true) ||
                msg.contains("TIMEOUT", ignoreCase = true)
            if (isNetwork) {
                DriveTokenResult.TransientError(
                    "Gagal memperbarui token OAuth karena gangguan jaringan: ${msg.ifBlank { "Offline" }}"
                )
            } else {
                DriveTokenResult.RequiresReAuth(
                    reason = "Autentikasi Google Drive perlu login ulang: ${msg.ifBlank { "Izin tidak tersedia" }}"
                )
            }
        }
    }

    override suspend fun clearCachedPlatformToken(
        context: Context,
        staleAccessToken: String
    ) {
        if (staleAccessToken.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                GoogleAuthUtil.clearToken(context, staleAccessToken)
            }
        }
    }
}

/**
 * Unified, Least-Privilege Google Drive OAuth 2.0 Token Manager.
 *
 * Guarantees:
 * - Single source of truth for ALL Google Drive services (SyncRepository, GoogleDriveService,
 *   SyncWorker, GoogleDriveBackupService, GoogleDrivePhotoCenterManager, AutoBackupWorkScheduler).
 * - Strictly requests ONLY minimal scopes: `drive.file` & `drive.appdata`.
 * - Tracks real token expiration (`expiresAtMs`) with a proactive safety buffer; NEVER extends
 *   stale token expiry artificially on reload.
 * - Single-flight token refresh via coroutine `Mutex` so concurrent requests never stampede
 *   OAuth refresh calls.
 * - Evicts both local Keystore-encrypted cache AND Play Services token cache (`GoogleAuthUtil.clearToken`)
 *   when HTTP 401 UNAUTHENTICATED occurs, then acquires a genuinely fresh access token.
 * - Encrypts token envelope with Android Keystore AES-256-GCM inside `noBackupFilesDir`.
 * - NEVER stores client secrets, NEVER fabricates refresh tokens, NEVER logs raw tokens.
 */
object GoogleDriveAuthManager {

    const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    const val PRIMARY_DRIVE_SCOPE = SCOPE_DRIVE_FILE

    val MINIMAL_DRIVE_SCOPES = listOf(SCOPE_DRIVE_FILE, SCOPE_DRIVE_APPDATA)

    fun hasDriveFileScope(grantedScopes: Collection<String>): Boolean {
        return grantedScopes.any { it.equals(SCOPE_DRIVE_FILE, ignoreCase = true) }
    }

    private const val PREFS_NAME = "saf_soc_drive_prefs"
    private const val KEY_CONNECTED = "drive_connected"
    private const val KEY_EMAIL = "drive_account_email"
    private const val KEY_NAME = "drive_account_name"
    private const val KEY_PHOTO = "drive_account_photo"
    private const val KEY_LAST_SYNC = "drive_last_sync"
    private const val KEY_PENDING_OFFLINE = "drive_pending_offline_upload"
    private const val KEY_REQUIRES_REAUTH = "drive_requires_reauth"
    private const val KEY_AUTH_ERROR_REASON = "drive_auth_error_reason"
    private const val ENCRYPTED_TOKEN_FILE = "drive_session_envelope.bin"

    // Real Google OAuth access tokens expire after 60 minutes. We use a 45-min local validity
    // window and proactively refresh 5 minutes prior to expiry.
    internal const val FRESH_TOKEN_TTL_MS = 45L * 60L * 1000L
    internal const val EXPIRY_SAFETY_MARGIN_MS = 5L * 60L * 1000L

    private val tokenRefreshMutex = Mutex()

    @Volatile
    private var inMemoryEnvelope: CachedDriveTokenEnvelope? = null

    @Volatile
    var oauthTransport: DriveOAuthTransport = PlayServicesDriveOAuthTransport

    @Volatile
    var clockMillisProvider: () -> Long = { System.currentTimeMillis() }

    private fun nowMs(): Long = clockMillisProvider()

    fun buildAuthorizationRequest(
        accountEmail: String? = null,
        requireDriveFileScopeOnly: Boolean = false
    ): AuthorizationRequest {
        val scopesToRequest = if (requireDriveFileScopeOnly) {
            listOf(Scope(SCOPE_DRIVE_FILE))
        } else {
            MINIMAL_DRIVE_SCOPES.map { Scope(it) }
        }
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(scopesToRequest)
        if (!accountEmail.isNullOrBlank()) {
            runCatching {
                val account = android.accounts.Account(accountEmail, "com.google")
                builder.setAccount(account)
            }
        }
        return builder.build()
    }

    fun isEnvelopeUsable(envelope: CachedDriveTokenEnvelope?, currentTimeMs: Long = nowMs()): Boolean {
        if (envelope == null) return false
        if (envelope.accessToken.isBlank()) return false
        return currentTimeMs + EXPIRY_SAFETY_MARGIN_MS < envelope.expiresAtMs
    }

    suspend fun authorizeDriveAccess(
        context: Context,
        accountEmail: String? = null
    ): DriveAuthorizationOutcome {
        return try {
            val request = buildAuthorizationRequest(accountEmail)
            val result: AuthorizationResult = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()

            val pendingIntent = result.pendingIntent
            if (result.hasResolution() && pendingIntent != null) {
                return DriveAuthorizationOutcome.NeedsUserConsent(pendingIntent.intentSender)
            }

            val token = result.accessToken?.trim()
            if (!token.isNullOrBlank()) {
                val scopes = result.grantedScopes.takeIf { it.isNotEmpty() } ?: MINIMAL_DRIVE_SCOPES
                cacheEncryptedSessionToken(
                    context = context,
                    accessToken = token,
                    expiresAtMs = nowMs() + FRESH_TOKEN_TTL_MS,
                    accountEmail = accountEmail.orEmpty(),
                    grantedScopes = scopes
                )
                clearReAuthRequiredFlag(context)
                markConnectedMetadata(
                    context = context,
                    email = accountEmail.orEmpty(),
                    displayName = accountEmail?.substringBefore("@").orEmpty(),
                    photoUrl = ""
                )
                DriveAuthorizationOutcome.Authorized(accessToken = token, grantedScopes = scopes)
            } else {
                markReAuthRequired(context, "Google Drive tidak mengembalikan access token yang valid.")
                DriveAuthorizationOutcome.Failed("Google Drive tidak mengembalikan access token.")
            }
        } catch (e: Exception) {
            DriveAuthorizationOutcome.Failed(
                e.localizedMessage ?: "Gagal meminta izin akses Google Drive."
            )
        }
    }

    fun handleAuthorizationResultIntent(
        context: Context,
        resultCode: Int,
        data: Intent?,
        accountEmail: String = "",
        displayName: String = "",
        photoUrl: String = ""
    ): DriveAuthorizationOutcome {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return DriveAuthorizationOutcome.Failed("Izin akses Google Drive dibatalkan oleh pengguna.")
        }
        return try {
            val authResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = authResult.accessToken?.trim()
            if (token.isNullOrBlank()) {
                markReAuthRequired(context, "Token otorisasi Google Drive kosong.")
                DriveAuthorizationOutcome.Failed("Token otorisasi Google Drive kosong.")
            } else {
                val scopes = authResult.grantedScopes.takeIf { it.isNotEmpty() } ?: MINIMAL_DRIVE_SCOPES
                cacheEncryptedSessionToken(
                    context = context,
                    accessToken = token,
                    expiresAtMs = nowMs() + FRESH_TOKEN_TTL_MS,
                    accountEmail = accountEmail,
                    grantedScopes = scopes
                )
                clearReAuthRequiredFlag(context)
                markConnectedMetadata(
                    context = context,
                    email = accountEmail,
                    displayName = displayName,
                    photoUrl = photoUrl
                )
                DriveAuthorizationOutcome.Authorized(token, scopes)
            }
        } catch (e: Exception) {
            DriveAuthorizationOutcome.Failed(
                e.localizedMessage ?: "Gagal memproses hasil otorisasi Google Drive."
            )
        }
    }

    /**
     * Acquires a valid, non-expired OAuth 2.0 access token using single-flight refresh protection.
     * - Checks in-memory envelope against REAL `expiresAtMs` (with safety buffer).
     * - Restores Keystore AES-256-GCM encrypted envelope ONLY if its REAL `expiresAtMs` is still valid.
     * - If missing or near/past expiry, acquires a fresh token under `tokenRefreshMutex` (single-flight).
     * - Never returns a blank, invalid, or expired token.
     */
    suspend fun getValidAccessTokenResult(
        context: Context,
        accountEmail: String? = null
    ): DriveTokenResult {
        val currentMs = nowMs()
        val mem = inMemoryEnvelope
        if (isEnvelopeUsable(mem, currentMs)) {
            return DriveTokenResult.Valid(mem!!.accessToken, mem.expiresAtMs)
        }

        return tokenRefreshMutex.withLock {
            val lockedNow = nowMs()
            val recheckedMem = inMemoryEnvelope
            if (isEnvelopeUsable(recheckedMem, lockedNow)) {
                return@withLock DriveTokenResult.Valid(recheckedMem!!.accessToken, recheckedMem.expiresAtMs)
            }

            val restored = readEncryptedSessionEnvelope(context)
            if (isEnvelopeUsable(restored, lockedNow)) {
                inMemoryEnvelope = restored
                return@withLock DriveTokenResult.Valid(restored!!.accessToken, restored.expiresAtMs)
            }

            // Cached token missing or expired — purge local cache and perform silent OAuth refresh
            val staleToken = restored?.accessToken ?: recheckedMem?.accessToken
            purgeLocalCachedTokenLocked(context)
            if (!staleToken.isNullOrBlank()) {
                oauthTransport.clearCachedPlatformToken(context, staleToken)
            }

            val resolvedEmail = accountEmail?.takeIf { it.isNotBlank() }
                ?: getConnectionStatus(context).accountEmail.takeIf { it.isNotBlank() }

            val refreshed = oauthTransport.acquireAccessTokenSilently(context, resolvedEmail)
            when (refreshed) {
                is DriveTokenResult.Valid -> {
                    if (refreshed.accessToken.isBlank() || refreshed.expiresAtMs <= nowMs()) {
                        markReAuthRequired(context, "Token OAuth baru kosong atau sudah kedaluwarsa.")
                        DriveTokenResult.RequiresReAuth("Token OAuth baru kosong atau sudah kedaluwarsa.")
                    } else {
                        cacheEncryptedSessionTokenLocked(
                            context = context,
                            accessToken = refreshed.accessToken,
                            expiresAtMs = refreshed.expiresAtMs,
                            accountEmail = resolvedEmail.orEmpty()
                        )
                        clearReAuthRequiredFlag(context)
                        refreshed
                    }
                }
                is DriveTokenResult.RequiresReAuth -> {
                    markReAuthRequired(context, refreshed.reason)
                    refreshed
                }
                is DriveTokenResult.TransientError -> refreshed
            }
        }
    }

    /**
     * Single-flight 401 UNAUTHENTICATED recovery:
     * 1. Acquires `tokenRefreshMutex` so concurrent 401s across workers/requests execute ONLY ONE refresh.
     * 2. If another concurrent caller already refreshed a valid token distinct from `rejectedToken`,
     *    returns that fresh token immediately without redundant OAuth calls.
     * 3. Otherwise evicts both local Keystore envelope AND Play Services cached token (`GoogleAuthUtil.clearToken`),
     *    requests a fresh token from Google OAuth 2.0, and rejects returning the same dead `rejectedToken`.
     * 4. If refresh fails because consent was revoked or expired, sets `requiresReAuth = true` so the UI
     *    prompts the user to log in again.
     */
    suspend fun invalidateAndRefreshAccessToken(
        context: Context,
        rejectedToken: String?,
        accountEmail: String? = null
    ): DriveTokenResult = tokenRefreshMutex.withLock {
        val currentMs = nowMs()
        val currentEnv = inMemoryEnvelope
        if (isEnvelopeUsable(currentEnv, currentMs) &&
            !rejectedToken.isNullOrBlank() &&
            currentEnv!!.accessToken != rejectedToken
        ) {
            return@withLock DriveTokenResult.Valid(currentEnv.accessToken, currentEnv.expiresAtMs)
        }

        val tokenToEvict = rejectedToken?.takeIf { it.isNotBlank() }
            ?: currentEnv?.accessToken
            ?: readEncryptedSessionEnvelope(context)?.accessToken

        purgeLocalCachedTokenLocked(context)
        if (!tokenToEvict.isNullOrBlank()) {
            oauthTransport.clearCachedPlatformToken(context, tokenToEvict)
        }

        val resolvedEmail = accountEmail?.takeIf { it.isNotBlank() }
            ?: getConnectionStatus(context).accountEmail.takeIf { it.isNotBlank() }

        val refreshed = oauthTransport.acquireAccessTokenSilently(context, resolvedEmail)
        when (refreshed) {
            is DriveTokenResult.Valid -> {
                val freshToken = refreshed.accessToken.trim()
                if (freshToken.isBlank() || (!rejectedToken.isNullOrBlank() && freshToken == rejectedToken)) {
                    val reason = "Google Play Services masih mengembalikan token yang ditolak (401). Silakan login ulang Google Drive."
                    markReAuthRequired(context, reason)
                    DriveTokenResult.RequiresReAuth(reason)
                } else {
                    cacheEncryptedSessionTokenLocked(
                        context = context,
                        accessToken = freshToken,
                        expiresAtMs = refreshed.expiresAtMs,
                        accountEmail = resolvedEmail.orEmpty()
                    )
                    clearReAuthRequiredFlag(context)
                    DriveTokenResult.Valid(freshToken, refreshed.expiresAtMs)
                }
            }
            is DriveTokenResult.RequiresReAuth -> {
                markReAuthRequired(context, refreshed.reason)
                refreshed
            }
            is DriveTokenResult.TransientError -> refreshed
        }
    }

    suspend fun getValidAccessTokenOrNull(
        context: Context,
        accountEmail: String? = null
    ): String? {
        return when (val res = getValidAccessTokenResult(context, accountEmail)) {
            is DriveTokenResult.Valid -> res.accessToken
            else -> null
        }
    }

    /**
     * Attaches the unified single-flight OAuth 2.0 401 refresh handler to any [GoogleDriveRestClient]
     * that does not already have one installed.
     */
    fun attachAuthRefresh(
        context: Context,
        client: GoogleDriveRestClient,
        accountEmail: String? = null
    ) {
        if (client.tokenRefreshHandler != null) return
        val appContext = context.applicationContext ?: context
        client.tokenRefreshHandler = { rejectedToken ->
            when (val res = invalidateAndRefreshAccessToken(appContext, rejectedToken, accountEmail)) {
                is DriveTokenResult.Valid -> Result.success(res.accessToken)
                is DriveTokenResult.RequiresReAuth -> Result.failure(
                    DriveApiException(401, DriveApiException.ERROR_CODE_FAILED_AUTH, res.reason)
                )
                is DriveTokenResult.TransientError -> Result.failure(IllegalStateException(res.reason))
            }
        }
    }

    suspend fun invalidateToken(context: Context, staleToken: String? = null) {
        tokenRefreshMutex.withLock {
            val toClear = staleToken ?: inMemoryEnvelope?.accessToken
            purgeLocalCachedTokenLocked(context)
            if (!toClear.isNullOrBlank()) {
                oauthTransport.clearCachedPlatformToken(context, toClear)
            }
        }
    }

    fun getConnectionStatus(context: Context): DriveConnectionStatus {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val connected = prefs.getBoolean(KEY_CONNECTED, false)
        val email = prefs.getString(KEY_EMAIL, "").orEmpty()
        val name = prefs.getString(KEY_NAME, "").orEmpty()
        val photo = prefs.getString(KEY_PHOTO, "").orEmpty()
        val lastSync = prefs.getString(KEY_LAST_SYNC, "").orEmpty()
        val pendingOffline = prefs.getBoolean(KEY_PENDING_OFFLINE, false)
        val requiresReAuth = prefs.getBoolean(KEY_REQUIRES_REAUTH, false)
        val authErrorReason = prefs.getString(KEY_AUTH_ERROR_REASON, "").orEmpty()
        return DriveConnectionStatus(
            isConnected = connected && !requiresReAuth,
            accountEmail = email,
            displayName = name.ifBlank { email.substringBefore("@") },
            photoUrl = photo,
            grantedScopes = if (connected && !requiresReAuth) MINIMAL_DRIVE_SCOPES else emptyList(),
            lastDriveSyncAt = lastSync,
            hasPendingOfflineUpload = pendingOffline,
            requiresReAuth = requiresReAuth,
            authErrorReason = authErrorReason,
            statusMessage = when {
                requiresReAuth -> "Autentikasi Google Drive kedaluwarsa/dicabut — perlu login ulang ($email)"
                connected && pendingOffline -> "Terhubung ($email) • Antrian backup offline menunggu internet"
                connected -> "Terhubung ke Google Drive ($email)"
                else -> "Belum terhubung ke Google Drive"
            }
        )
    }

    fun markReAuthRequired(context: Context, reason: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_REQUIRES_REAUTH, true)
            .putString(KEY_AUTH_ERROR_REASON, reason.take(240))
            .apply()
    }

    fun clearReAuthRequiredFlag(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_REQUIRES_REAUTH, false)
            .remove(KEY_AUTH_ERROR_REASON)
            .apply()
    }

    fun markConnectedMetadata(
        context: Context,
        email: String,
        displayName: String,
        photoUrl: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_CONNECTED, true)
            .putBoolean(KEY_REQUIRES_REAUTH, false)
            .remove(KEY_AUTH_ERROR_REASON)
            .apply {
                if (email.isNotBlank()) putString(KEY_EMAIL, email)
                if (displayName.isNotBlank()) putString(KEY_NAME, displayName)
                if (photoUrl.isNotBlank()) putString(KEY_PHOTO, photoUrl)
            }
            .apply()
    }

    fun updateLastDriveSyncAt(context: Context, timestampDisplay: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_SYNC, timestampDisplay)
            .putBoolean(KEY_PENDING_OFFLINE, false)
            .apply()
    }

    fun setPendingOfflineUpload(context: Context, pending: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PENDING_OFFLINE, pending).apply()
    }

    suspend fun disconnectDrive(
        context: Context,
        httpClient: OkHttpClient = OkHttpClient(),
        revokeEndpoint: String = "https://oauth2.googleapis.com/revoke"
    ) {
        val tokenToRevoke = tokenRefreshMutex.withLock {
            val tok = inMemoryEnvelope?.accessToken ?: readEncryptedSessionToken(context)
            purgeLocalCachedTokenLocked(context)
            tok
        }
        if (!tokenToRevoke.isNullOrBlank()) {
            oauthTransport.clearCachedPlatformToken(context, tokenToRevoke)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_CONNECTED, false)
            .putBoolean(KEY_REQUIRES_REAUTH, false)
            .remove(KEY_AUTH_ERROR_REASON)
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .remove(KEY_PHOTO)
            .putBoolean(KEY_PENDING_OFFLINE, false)
            .apply()

        if (!tokenToRevoke.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val body = FormBody.Builder().add("token", tokenToRevoke).build()
                    val req = Request.Builder().url(revokeEndpoint).post(body).build()
                    httpClient.newCall(req).execute().close()
                }
            }
        }
    }

    /**
     * Encrypts ephemeral OAuth access token envelope using Android Keystore AES-256-GCM.
     * Never writes plaintext tokens to SharedPreferences or plain files.
     */
    fun cacheEncryptedSessionToken(
        context: Context,
        accessToken: String,
        expiresAtMs: Long = nowMs() + FRESH_TOKEN_TTL_MS,
        accountEmail: String = "",
        grantedScopes: List<String> = MINIMAL_DRIVE_SCOPES
    ) {
        cacheEncryptedSessionTokenLocked(context, accessToken, expiresAtMs, accountEmail, grantedScopes)
    }

    private fun cacheEncryptedSessionTokenLocked(
        context: Context,
        accessToken: String,
        expiresAtMs: Long,
        accountEmail: String = "",
        grantedScopes: List<String> = MINIMAL_DRIVE_SCOPES
    ) {
        val cleanToken = accessToken.trim()
        if (cleanToken.isBlank()) return
        val issuedAt = nowMs()
        val envelope = CachedDriveTokenEnvelope(
            accessToken = cleanToken,
            issuedAtMs = issuedAt,
            expiresAtMs = expiresAtMs,
            accountEmail = accountEmail,
            grantedScopes = grantedScopes
        )
        inMemoryEnvelope = envelope
        runCatching {
            val payload = JSONObject().apply {
                put("accessToken", cleanToken)
                put("issuedAtMs", issuedAt)
                put("expiresAtMs", expiresAtMs)
                put("accountEmail", accountEmail)
                put("grantedScopes", grantedScopes.joinToString(","))
            }.toString()
            val encrypted = SecurityCryptoUtils.encryptBytesAesGcm(payload.toByteArray(Charsets.UTF_8))
            val dir = runCatching { context.noBackupFilesDir }.getOrElse { context.filesDir }
            if (!dir.exists()) dir.mkdirs()
            File(dir, ENCRYPTED_TOKEN_FILE).writeBytes(encrypted)
        }
    }

    private fun purgeLocalCachedTokenLocked(context: Context) {
        inMemoryEnvelope = null
        runCatching {
            val dir = runCatching { context.noBackupFilesDir }.getOrElse { context.filesDir }
            val tokenFile = File(dir, ENCRYPTED_TOKEN_FILE)
            if (tokenFile.exists()) tokenFile.delete()
        }
    }

    fun readEncryptedSessionEnvelope(context: Context): CachedDriveTokenEnvelope? {
        return runCatching {
            val dir = runCatching { context.noBackupFilesDir }.getOrElse { context.filesDir }
            val file = File(dir, ENCRYPTED_TOKEN_FILE)
            if (!file.exists()) return null
            val decrypted = SecurityCryptoUtils.decryptBytesAesGcm(file.readBytes())
            val json = JSONObject(String(decrypted, Charsets.UTF_8))
            val expiresAt = json.optLong("expiresAtMs", 0L)
            val token = json.optString("accessToken").trim()
            if (token.isBlank() || nowMs() >= expiresAt) {
                file.delete()
                null
            } else {
                val scopesRaw = json.optString("grantedScopes", "")
                val scopes = if (scopesRaw.isNotBlank()) {
                    scopesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                } else {
                    MINIMAL_DRIVE_SCOPES
                }
                CachedDriveTokenEnvelope(
                    accessToken = token,
                    issuedAtMs = json.optLong("issuedAtMs", 0L),
                    expiresAtMs = expiresAt,
                    accountEmail = json.optString("accountEmail", ""),
                    grantedScopes = scopes
                )
            }
        }.getOrNull()
    }

    fun readEncryptedSessionToken(context: Context): String? {
        return readEncryptedSessionEnvelope(context)?.accessToken
    }

    /**
     * Resets in-memory state and test transport back to production defaults.
     */
    fun resetForTesting(context: Context? = null) {
        inMemoryEnvelope = null
        oauthTransport = PlayServicesDriveOAuthTransport
        clockMillisProvider = { System.currentTimeMillis() }
        if (context != null) {
            runCatching {
                val dir = runCatching { context.noBackupFilesDir }.getOrElse { context.filesDir }
                File(dir, ENCRYPTED_TOKEN_FILE).delete()
            }
        }
    }
}
