package com.example.drive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DriveBackupFileItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdTime: String,
    val modifiedTime: String,
    val sha256Checksum: String = "",
    val backupId: String = "",
    val backupType: String = "FULL_BACKUP",
    val customerCount: Int = 0,
    val unitCount: Int = 0,
    val transactionCount: Int = 0,
    val paymentCount: Int = 0,
    val photoCount: Int = 0,
    val mimeType: String = "",
    val datasetGeneration: String = "",
    val recordUuid: String = "",
    val remoteVersion: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val applicationId: String = "",
    val appId: String = "",
    val datasetId: String = "",
    val parentIds: List<String> = emptyList(),
    val trashed: Boolean = false,
    val entityType: String = "",
    val schemaVersion: Int = 0,
    val syncTimestamp: Long = 0L,
    val rawAppProperties: Map<String, String> = emptyMap()
)

data class DriveRecursiveObject(
    val item: DriveBackupFileItem,
    val parentFolderId: String,
    val isFolder: Boolean,
    val depth: Int
)

data class DriveDeleteFailure(
    val fileId: String,
    val fileName: String,
    val errorCode: String,
    val errorMessage: String
)

/**
 * Typed HTTP/OAuth exception raised by [GoogleDriveRestClient] so callers can reliably
 * distinguish HTTP 401 authentication failures (`FAILED_AUTH`) from HTTP 404 not-found
 * and transient network/5xx errors.
 */
class DriveApiException(
    val httpCode: Int,
    val errorCode: String,
    override val message: String,
    val isAuthError: Boolean = httpCode == 401 || errorCode == ERROR_CODE_FAILED_AUTH,
    cause: Throwable? = null
) : IllegalStateException(message, cause) {
    companion object {
        const val ERROR_CODE_FAILED_AUTH = "FAILED_AUTH"

        fun isAuthFailure(throwable: Throwable?): Boolean {
            var cur = throwable
            while (cur != null) {
                if (cur is DriveApiException && (cur.isAuthError || cur.httpCode == 401 || cur.errorCode == ERROR_CODE_FAILED_AUTH)) {
                    return true
                }
                val msg = cur.message.orEmpty()
                if (msg.contains("HTTP 401", ignoreCase = true) ||
                    msg.contains("HTTP_401", ignoreCase = true) ||
                    msg.contains("UNAUTHENTICATED", ignoreCase = true) ||
                    msg.contains("FAILED_AUTH", ignoreCase = true) ||
                    msg.contains("Invalid Credentials", ignoreCase = true) ||
                    msg.contains("Expected OAuth 2 access token", ignoreCase = true)
                ) {
                    return true
                }
                cur = cur.cause
            }
            return false
        }

        fun isNotFoundFailure(throwable: Throwable?): Boolean {
            var cur = throwable
            while (cur != null) {
                if (cur is DriveApiException && cur.httpCode == 404) {
                    return true
                }
                val msg = cur.message.orEmpty()
                if (msg.contains("HTTP 404", ignoreCase = true) || msg.contains("HTTP_404", ignoreCase = true)) {
                    return true
                }
                cur = cur.cause
            }
            return false
        }
    }
}

/**
 * Production Google Drive REST v3 API client built on OkHttp.
 * - Routes ALL Drive endpoints (CREATE, UPLOAD, DOWNLOAD, UPDATE, DELETE, LIST, READ METADATA,
 *   UPDATE APP PROPERTIES) through a centralized OAuth 2.0 Bearer interceptor.
 * - Never dispatches requests with blank/empty OAuth tokens.
 * - On HTTP 401 UNAUTHENTICATED: stops the failed request, invokes [tokenRefreshHandler] ONCE,
 *   and retries the request ONCE with the fresh token. If refresh fails or retry still returns 401,
 *   fails cleanly with [DriveApiException] (`errorCode = "FAILED_AUTH"`), never looping infinitely.
 * - Base URLs are injectable so unit & integration tests can run deterministically against MockWebServer.
 */
class GoogleDriveRestClient(
    private val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    private val uploadBaseUrl: String = DEFAULT_UPLOAD_BASE_URL,
    private val client: OkHttpClient = defaultHttpClient()
) {
    /**
     * Optional single-flight OAuth 2.0 refresh callback wired by [GoogleDriveService] / backup services.
     * Receives the rejected token that triggered HTTP 401 (or empty string if initial token was blank)
     * and returns a fresh, non-expired access token or failure.
     */
    @Volatile
    var tokenRefreshHandler: (suspend (rejectedToken: String) -> Result<String>)? = null

    /**
     * Optional observer notified whenever a 401 refresh succeeds so callers iterating over batches
     * immediately adopt the newly minted access token for subsequent calls.
     */
    @Volatile
    var onTokenRefreshed: ((freshAccessToken: String) -> Unit)? = null

    companion object {
        const val DEFAULT_API_BASE_URL = "https://www.googleapis.com/drive/v3"
        const val DEFAULT_UPLOAD_BASE_URL = "https://www.googleapis.com/upload/drive/v3"
        const val BACKUP_FOLDER_NAME = "SEWAIN_AJA_FREEZER_SOC"
        const val DOC_ROOT_FOLDER_NAME = "SEWAin Aja Freezer SOC"
        const val LEGACY_BACKUP_FOLDER_NAME = "SEWAinAjaFreezer_Backups"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val ZIP_MIME = "application/zip"
        const val MAX_BACKUP_VERSIONS = 30

        const val REQUEST_TIMEOUT_MILLIS = 15_000L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private suspend inline fun <T> withDriveTimeout(crossinline block: () -> T): T {
        return withTimeout(REQUEST_TIMEOUT_MILLIS) {
            block()
        }
    }

    private fun rethrowIfExternalCancellation(e: Exception) {
        if (e is CancellationException && e !is TimeoutCancellationException) {
            throw e
        }
    }

    @Volatile
    var lastServerTimeMillis: Long = 0L
        private set

    @Volatile
    var serverClockOffsetMillis: Long = 0L
        private set

    fun currentServerReferenceTimeMillis(): Long {
        val localNow = System.currentTimeMillis()
        return if (lastServerTimeMillis > 0L) localNow + serverClockOffsetMillis else localNow
    }

    private fun captureServerDateHeader(resp: Response) {
        val rawDate = resp.header("Date") ?: return
        val parsed = runCatching {
            val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("GMT")
            fmt.parse(rawDate)?.time
        }.getOrNull()
        if (parsed != null && parsed > 0L) {
            lastServerTimeMillis = parsed
            serverClockOffsetMillis = parsed - System.currentTimeMillis()
        } else {
            val now = System.currentTimeMillis()
            if (lastServerTimeMillis == 0L) {
                lastServerTimeMillis = now
            }
        }
    }

    private fun trimmedApiBase(): String = apiBaseUrl.trimEnd('/')
    private fun trimmedUploadBase(): String = uploadBaseUrl.trimEnd('/')

    private fun httpFailureException(operationLabel: String, httpCode: Int, bodyStr: String): DriveApiException {
        val errorCode = if (httpCode == 401) DriveApiException.ERROR_CODE_FAILED_AUTH else "HTTP_$httpCode"
        return DriveApiException(
            httpCode = httpCode,
            errorCode = errorCode,
            message = "$operationLabel HTTP $httpCode: $bodyStr",
            isAuthError = httpCode == 401
        )
    }

    /**
     * Centralized OAuth 2.0 Bearer execution pipeline:
     * 1. Rejects blank/empty tokens before touching the network (or acquires via [tokenRefreshHandler]).
     * 2. Executes [buildRequest] with `Authorization: Bearer <token>`.
     * 3. On HTTP 401: closes response, invokes [tokenRefreshHandler] ONCE, verifies the new token
     *    is non-blank and distinct from the rejected token, and retries ONCE.
     * 4. If refresh fails or retry still returns HTTP 401: returns [DriveApiException] (`FAILED_AUTH`).
     */
    private suspend fun <T> executeAuthenticated(
        accessToken: String,
        operationLabel: String,
        useTimeout: Boolean = false,
        buildRequest: (bearerToken: String) -> Request,
        handleResponse: (Response, String) -> Result<T>
    ): Result<T> {
        var activeToken = accessToken.trim()
        if (activeToken.isBlank()) {
            val refresher = tokenRefreshHandler
                ?: return Result.failure(
                    DriveApiException(
                        httpCode = 401,
                        errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                        message = "$operationLabel gagal: Access token OAuth 2.0 kosong. Request tidak dikirim ke Google Drive."
                    )
                )
            val acquired = refresher.invoke("")
            val validToken = acquired.getOrNull()?.trim().orEmpty()
            if (acquired.isFailure || validToken.isBlank()) {
                return Result.failure(
                    DriveApiException(
                        httpCode = 401,
                        errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                        message = "$operationLabel gagal: ${acquired.exceptionOrNull()?.message ?: "Token OAuth 2.0 tidak tersedia. Silakan login ulang Google Drive."}",
                        cause = acquired.exceptionOrNull()
                    )
                )
            }
            activeToken = validToken
            onTokenRefreshed?.invoke(activeToken)
        }

        suspend fun executeOnce(token: String): Response {
            val req = buildRequest(token)
            return if (useTimeout) {
                withDriveTimeout { client.newCall(req).execute() }
            } else {
                client.newCall(req).execute()
            }
        }

        val firstResp = executeOnce(activeToken)
        if (firstResp.code != 401) {
            return firstResp.use { resp ->
                captureServerDateHeader(resp)
                handleResponse(resp, activeToken)
            }
        }

        // HTTP 401 UNAUTHENTICATED — stop failed request, close response, refresh ONCE, retry ONCE
        val firstErrBody = firstResp.use { resp ->
            captureServerDateHeader(resp)
            resp.body?.string().orEmpty()
        }

        val refresher = tokenRefreshHandler
            ?: return Result.failure(
                DriveApiException(
                    httpCode = 401,
                    errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                    message = "$operationLabel HTTP 401 UNAUTHENTICATED: $firstErrBody"
                )
            )

        val refreshResult = refresher.invoke(activeToken)
        val freshToken = refreshResult.getOrNull()?.trim().orEmpty()
        if (refreshResult.isFailure || freshToken.isBlank() || freshToken == activeToken) {
            return Result.failure(
                DriveApiException(
                    httpCode = 401,
                    errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                    message = "$operationLabel HTTP 401 UNAUTHENTICATED — gagal memperbarui token OAuth 2.0: ${refreshResult.exceptionOrNull()?.message ?: "Login ulang Google Drive diperlukan."}",
                    cause = refreshResult.exceptionOrNull()
                )
            )
        }

        onTokenRefreshed?.invoke(freshToken)

        return executeOnce(freshToken).use { retryResp ->
            captureServerDateHeader(retryResp)
            if (retryResp.code == 401) {
                val retryErrBody = retryResp.body?.string().orEmpty()
                Result.failure(
                    DriveApiException(
                        httpCode = 401,
                        errorCode = DriveApiException.ERROR_CODE_FAILED_AUTH,
                        message = "$operationLabel tetap HTTP 401 UNAUTHENTICATED setelah 1x refresh token OAuth 2.0: $retryErrBody"
                    )
                )
            } else {
                handleResponse(retryResp, freshToken)
            }
        }
    }

    suspend fun ensureNamedFolderId(
        accessToken: String,
        folderName: String,
        parentFolderId: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val safeName = folderName.replace("'", "").trim().ifBlank { DOC_ROOT_FOLDER_NAME }
            val parentClause = if (!parentFolderId.isNullOrBlank()) {
                " and '$parentFolderId' in parents"
            } else {
                ""
            }
            val query = "name = '$safeName'$parentClause and mimeType = '$FOLDER_MIME' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "${trimmedApiBase()}/files?q=$encodedQuery&fields=files(id,name)&spaces=drive"

            var activeToken = accessToken
            val lookupRes = executeAuthenticated(
                accessToken = activeToken,
                operationLabel = "Google Drive folder lookup",
                buildRequest = { tok ->
                    Request.Builder()
                        .url(searchUrl)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, usedTok ->
                    activeToken = usedTok
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Google Drive folder lookup", resp.code, bodyStr))
                    } else {
                        val filesArr = JSONObject(bodyStr).optJSONArray("files") ?: JSONArray()
                        if (filesArr.length() > 0) {
                            Result.success<String?>(filesArr.getJSONObject(0).getString("id"))
                        } else {
                            Result.success<String?>(null)
                        }
                    }
                }
            )
            val existingId = lookupRes.getOrElse { return@withContext Result.failure(it) }
            if (!existingId.isNullOrBlank()) {
                return@withContext Result.success(existingId)
            }

            val createMeta = JSONObject().apply {
                put("name", safeName)
                put("mimeType", FOLDER_MIME)
                if (!parentFolderId.isNullOrBlank()) {
                    put("parents", JSONArray(listOf(parentFolderId)))
                }
            }
            executeAuthenticated(
                accessToken = activeToken,
                operationLabel = "Google Drive create folder",
                buildRequest = { tok ->
                    Request.Builder()
                        .url("${trimmedApiBase()}/files?fields=id,name")
                        .header("Authorization", "Bearer $tok")
                        .post(createMeta.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Google Drive create folder", resp.code, bodyStr))
                    } else {
                        Result.success(JSONObject(bodyStr).getString("id"))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun uploadFileToSpecificFolderId(
        accessToken: String,
        localFile: File,
        targetFileName: String,
        parentFolderId: String,
        mimeType: String = "image/jpeg",
        checksumSha256: String = "",
        description: String = "",
        customAppProperties: Map<String, String> = emptyMap(),
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        try {
            require(localFile.exists() && localFile.length() > 0L) {
                "File dokumentasi tidak ditemukan atau kosong: ${localFile.name}"
            }
            val appProps = JSONObject().apply {
                put(MirrorOutboxCodec.APP_ID_KEY, MirrorOutboxCodec.APP_ID_VALUE)
                if (checksumSha256.isNotBlank()) put("checksumSha256", checksumSha256)
                customAppProperties.forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v.take(120))
                }
            }
            val metadataJson = JSONObject().apply {
                put("name", targetFileName.ifBlank { localFile.name })
                put("mimeType", mimeType)
                put("parents", JSONArray(listOf(parentFolderId)))
                if (description.isNotBlank()) put("description", description)
                put("appProperties", appProps)
            }
            val uploadUrl = "${trimmedUploadBase()}/files?uploadType=multipart&fields=id,name,size,createdTime,modifiedTime,sha256Checksum,appProperties,parents"

            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Upload foto ke Google Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    val fileRequestBody = createProgressRequestBody(localFile, mimeType.toMediaType(), onProgress)
                    val multipartBody = MultipartBody.Builder()
                        .setType("multipart/related".toMediaType())
                        .addPart(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .addPart(fileRequestBody)
                        .build()
                    Request.Builder()
                        .url(uploadUrl)
                        .header("Authorization", "Bearer $tok")
                        .post(multipartBody)
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Upload foto ke Google Drive gagal", resp.code, bodyStr))
                    } else {
                        Result.success(parseDriveFileJson(JSONObject(bodyStr), fallbackSha256 = checksumSha256))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun ensureAppBackupFolderId(accessToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val query = "(name = '$BACKUP_FOLDER_NAME' or name = '$LEGACY_BACKUP_FOLDER_NAME') and mimeType = '$FOLDER_MIME' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "${trimmedApiBase()}/files?q=$encodedQuery&fields=files(id,name)&spaces=drive"

            var activeToken = accessToken
            val lookupRes = executeAuthenticated(
                accessToken = activeToken,
                operationLabel = "Google Drive folder lookup",
                buildRequest = { tok ->
                    Request.Builder()
                        .url(searchUrl)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, usedTok ->
                    activeToken = usedTok
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Google Drive folder lookup", resp.code, bodyStr))
                    } else {
                        val filesArr = JSONObject(bodyStr).optJSONArray("files") ?: JSONArray()
                        if (filesArr.length() > 0) {
                            Result.success<String?>(filesArr.getJSONObject(0).getString("id"))
                        } else {
                            Result.success<String?>(null)
                        }
                    }
                }
            )
            val existingId = lookupRes.getOrElse { return@withContext Result.failure(it) }
            if (!existingId.isNullOrBlank()) {
                return@withContext Result.success(existingId)
            }

            val createMeta = JSONObject().apply {
                put("name", BACKUP_FOLDER_NAME)
                put("mimeType", FOLDER_MIME)
                put("description", "Backup otomatis terenkripsi SEWAin Aja Freezer SOC")
            }
            executeAuthenticated(
                accessToken = activeToken,
                operationLabel = "Google Drive create folder",
                buildRequest = { tok ->
                    Request.Builder()
                        .url("${trimmedApiBase()}/files?fields=id,name")
                        .header("Authorization", "Bearer $tok")
                        .post(createMeta.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Google Drive create folder", resp.code, bodyStr))
                    } else {
                        Result.success(JSONObject(bodyStr).getString("id"))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun ensureSubfolderId(
        accessToken: String,
        parentFolderId: String,
        subfolderName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val safeSubName = subfolderName.replace("'", "").trim().ifBlank { "DOCUMENTS" }
            val query = "name = '$safeSubName' and '$parentFolderId' in parents and mimeType = '$FOLDER_MIME' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "${trimmedApiBase()}/files?q=$encodedQuery&fields=files(id,name)&spaces=drive"

            var activeToken = accessToken
            val lookupRes = executeAuthenticated(
                accessToken = activeToken,
                operationLabel = "Google Drive subfolder lookup",
                buildRequest = { tok ->
                    Request.Builder()
                        .url(searchUrl)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, usedTok ->
                    activeToken = usedTok
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Google Drive subfolder lookup", resp.code, bodyStr))
                    } else {
                        val filesArr = JSONObject(bodyStr).optJSONArray("files") ?: JSONArray()
                        if (filesArr.length() > 0) {
                            Result.success<String?>(filesArr.getJSONObject(0).getString("id"))
                        } else {
                            Result.success<String?>(null)
                        }
                    }
                }
            )
            val existingId = lookupRes.getOrElse { return@withContext Result.failure(it) }
            if (!existingId.isNullOrBlank()) {
                return@withContext Result.success(existingId)
            }

            val createMeta = JSONObject().apply {
                put("name", safeSubName)
                put("mimeType", FOLDER_MIME)
                put("parents", JSONArray(listOf(parentFolderId)))
            }
            executeAuthenticated(
                accessToken = activeToken,
                operationLabel = "Google Drive create subfolder",
                buildRequest = { tok ->
                    Request.Builder()
                        .url("${trimmedApiBase()}/files?fields=id,name")
                        .header("Authorization", "Bearer $tok")
                        .post(createMeta.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Google Drive create subfolder", resp.code, bodyStr))
                    } else {
                        Result.success(JSONObject(bodyStr).getString("id"))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun uploadBackupZip(
        accessToken: String,
        zipFile: File,
        backupId: String,
        backupType: String,
        checksumSha256: String,
        customerCount: Int,
        unitCount: Int,
        transactionCount: Int,
        paymentCount: Int,
        photoCount: Int,
        useAppDataFolder: Boolean = false,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        try {
            require(zipFile.exists() && zipFile.length() > 0L) {
                "File arsip backup tidak ditemukan atau kosong: ${zipFile.name}"
            }

            val parentId = if (useAppDataFolder) {
                "appDataFolder"
            } else {
                ensureAppBackupFolderId(accessToken).getOrElse { return@withContext Result.failure(it) }
            }

            val appProps = JSONObject().apply {
                put("backupId", backupId)
                put("backupType", backupType)
                put("checksumSha256", checksumSha256)
                put("customerCount", customerCount.toString())
                put("unitCount", unitCount.toString())
                put("transactionCount", transactionCount.toString())
                put("paymentCount", paymentCount.toString())
                put("photoCount", photoCount.toString())
            }

            val metadataJson = JSONObject().apply {
                put("name", zipFile.name)
                put("mimeType", ZIP_MIME)
                put("parents", JSONArray(listOf(parentId)))
                put("description", "Backup SEWAin Aja Freezer SOC • SHA256: ${checksumSha256.take(16)}")
                put("appProperties", appProps)
            }
            val uploadUrl = "${trimmedUploadBase()}/files?uploadType=multipart&fields=id,name,size,createdTime,modifiedTime,sha256Checksum,appProperties"

            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Upload Google Drive gagal",
                buildRequest = { tok ->
                    val fileRequestBody = createProgressRequestBody(zipFile, ZIP_MIME.toMediaType(), onProgress)
                    val multipartBody = MultipartBody.Builder()
                        .setType("multipart/related".toMediaType())
                        .addPart(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .addPart(fileRequestBody)
                        .build()
                    Request.Builder()
                        .url(uploadUrl)
                        .header("Authorization", "Bearer $tok")
                        .post(multipartBody)
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Upload Google Drive gagal", resp.code, bodyStr))
                    } else {
                        val item = parseDriveFileJson(JSONObject(bodyStr), fallbackSha256 = checksumSha256)
                        if (item.sha256Checksum.isNotBlank() &&
                            checksumSha256.isNotBlank() &&
                            item.sha256Checksum.length == checksumSha256.length &&
                            !item.sha256Checksum.equals(checksumSha256, ignoreCase = true)
                        ) {
                            Result.failure(
                                IllegalStateException("Checksum SHA-256 di Google Drive tidak cocok dengan file lokal.")
                            )
                        } else {
                            Result.success(item)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun uploadFileToDriveFolder(
        accessToken: String,
        localFile: File,
        mimeType: String,
        subfolderName: String? = null,
        checksumSha256: String = "",
        description: String = "",
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        try {
            require(localFile.exists() && localFile.length() > 0L) {
                "File dokumentasi tidak ditemukan atau kosong: ${localFile.name}"
            }
            val rootFolderId = ensureAppBackupFolderId(accessToken).getOrElse { return@withContext Result.failure(it) }
            val parentId = if (!subfolderName.isNullOrBlank()) {
                val subRes = ensureSubfolderId(accessToken, rootFolderId, subfolderName)
                if (subRes.isFailure && DriveApiException.isAuthFailure(subRes.exceptionOrNull())) {
                    return@withContext Result.failure(subRes.exceptionOrNull()!!)
                }
                subRes.getOrElse { rootFolderId }
            } else {
                rootFolderId
            }

            val appProps = JSONObject().apply {
                put(MirrorOutboxCodec.APP_ID_KEY, MirrorOutboxCodec.APP_ID_VALUE)
                if (checksumSha256.isNotBlank()) put("checksumSha256", checksumSha256)
                put("backupType", subfolderName ?: "DOCUMENTATION")
            }
            val metadataJson = JSONObject().apply {
                put("name", localFile.name)
                put("mimeType", mimeType)
                put("parents", JSONArray(listOf(parentId)))
                if (description.isNotBlank()) put("description", description)
                put("appProperties", appProps)
            }
            val uploadUrl = "${trimmedUploadBase()}/files?uploadType=multipart&fields=id,name,size,createdTime,modifiedTime,sha256Checksum,appProperties,parents"

            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Upload file ke Google Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    val fileRequestBody = createProgressRequestBody(localFile, mimeType.toMediaType(), onProgress)
                    val multipartBody = MultipartBody.Builder()
                        .setType("multipart/related".toMediaType())
                        .addPart(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .addPart(fileRequestBody)
                        .build()
                    Request.Builder()
                        .url(uploadUrl)
                        .header("Authorization", "Bearer $tok")
                        .post(multipartBody)
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Upload file ke Google Drive gagal", resp.code, bodyStr))
                    } else {
                        Result.success(parseDriveFileJson(JSONObject(bodyStr), fallbackSha256 = checksumSha256))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    /**
     * Reads complete metadata & appProperties of a single Drive file by its exact Google Drive `fileId`.
     * Returns `Result.success(null)` if Google Drive reports HTTP 404 (file already deleted).
     */
    suspend fun getFileMetadataById(
        accessToken: String,
        fileId: String
    ): Result<DriveBackupFileItem?> = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext Result.success(null)
        try {
            val fields = URLEncoder.encode(
                "id,name,mimeType,size,createdTime,modifiedTime,sha256Checksum,trashed,appProperties,parents",
                "UTF-8"
            )
            val url = "${trimmedApiBase()}/files/$fileId?fields=$fields"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Baca metadata file Google Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, _ ->
                    if (resp.code == 404) {
                        Result.success(null)
                    } else {
                        val bodyStr = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            Result.failure(httpFailureException("Baca metadata file Google Drive gagal", resp.code, bodyStr))
                        } else {
                            Result.success(parseDriveFileJson(JSONObject(bodyStr)))
                        }
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    /**
     * Updates `appProperties` (and optionally `name`/`description`) of an existing Drive file by `fileId`
     * via `PATCH /drive/v3/files/{fileId}`.
     */
    suspend fun updateFileAppProperties(
        accessToken: String,
        fileId: String,
        appProperties: Map<String, String>,
        newName: String? = null,
        description: String? = null
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        try {
            require(fileId.isNotBlank()) { "Google Drive File ID kosong untuk update appProperties." }
            val appProps = JSONObject().apply {
                put(MirrorOutboxCodec.APP_ID_KEY, MirrorOutboxCodec.APP_ID_VALUE)
                appProperties.forEach { (k, v) ->
                    if (k.isNotBlank()) put(k, v.take(120))
                }
            }
            val metadataJson = JSONObject().apply {
                if (!newName.isNullOrBlank()) put("name", newName)
                if (!description.isNullOrBlank()) put("description", description)
                put("appProperties", appProps)
            }
            val fields = URLEncoder.encode(
                "id,name,mimeType,size,createdTime,modifiedTime,sha256Checksum,trashed,appProperties,parents",
                "UTF-8"
            )
            val url = "${trimmedApiBase()}/files/$fileId?fields=$fields"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Update appProperties file Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .patch(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Update appProperties file Drive gagal", resp.code, bodyStr))
                    } else {
                        Result.success(parseDriveFileJson(JSONObject(bodyStr)))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun verifyRemoteFileIntegrity(
        accessToken: String,
        fileId: String,
        expectedSha256: String = ""
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        try {
            val fields = URLEncoder.encode("id,name,size,createdTime,modifiedTime,sha256Checksum,trashed,appProperties,parents", "UTF-8")
            val url = "${trimmedApiBase()}/files/$fileId?fields=$fields"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Verifikasi file Google Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Verifikasi file Google Drive gagal", resp.code, bodyStr))
                    } else {
                        val obj = JSONObject(bodyStr)
                        if (obj.optBoolean("trashed", false)) {
                            Result.failure(IllegalStateException("File backup di Google Drive berada di Trash."))
                        } else {
                            val item = parseDriveFileJson(obj, fallbackSha256 = expectedSha256)
                            if (expectedSha256.isNotBlank() &&
                                item.sha256Checksum.isNotBlank() &&
                                item.sha256Checksum.length == expectedSha256.length &&
                                !item.sha256Checksum.equals(expectedSha256, ignoreCase = true)
                            ) {
                                Result.failure(
                                    IllegalStateException("Checksum SHA-256 Google Drive (${item.sha256Checksum}) tidak sesuai dengan lokal ($expectedSha256).")
                                )
                            } else {
                                Result.success(item)
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun listBackups(
        accessToken: String,
        pageSize: Int = 50,
        spaces: String = "drive"
    ): Result<List<DriveBackupFileItem>> = withContext(Dispatchers.IO) {
        try {
            val query = "mimeType = '$ZIP_MIME' and trashed = false and name contains 'SEWAIN_'"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val fields = URLEncoder.encode(
                "files(id,name,size,createdTime,modifiedTime,sha256Checksum,appProperties)",
                "UTF-8"
            )
            val url = "${trimmedApiBase()}/files?q=$encodedQuery&orderBy=createdTime%20desc&pageSize=$pageSize&spaces=$spaces&fields=$fields"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Gagal mengambil daftar backup Drive",
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Gagal mengambil daftar backup Drive", resp.code, bodyStr))
                    } else {
                        val filesArr = JSONObject(bodyStr).optJSONArray("files") ?: JSONArray()
                        val items = buildList {
                            for (i in 0 until filesArr.length()) {
                                add(parseDriveFileJson(filesArr.getJSONObject(i)))
                            }
                        }
                        Result.success(items)
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun downloadBackupZip(
        accessToken: String,
        fileId: String,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempFile = File(destinationFile.parentFile ?: File("."), "${destinationFile.name}.download.part")
        try {
            destinationFile.parentFile?.mkdirs()
            val url = "${trimmedApiBase()}/files/$fileId?alt=media"
            val downloadRes = executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Gagal download backup dari Google Drive",
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, _ ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string().orEmpty()
                        Result.failure(httpFailureException("Gagal download backup dari Google Drive", resp.code, err))
                    } else {
                        val bodyStream = resp.body?.byteStream()
                            ?: return@executeAuthenticated Result.failure(IllegalStateException("Stream download Google Drive kosong."))
                        FileOutputStream(tempFile).use { fos ->
                            bodyStream.copyTo(fos)
                            fos.flush()
                        }
                        Result.success(Unit)
                    }
                }
            )
            downloadRes.getOrElse {
                runCatching { tempFile.delete() }
                return@withContext Result.failure(it)
            }
            if (!tempFile.exists() || tempFile.length() <= 0L) {
                return@withContext Result.failure(IllegalStateException("File backup hasil download kosong."))
            }
            if (destinationFile.exists()) destinationFile.delete()
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
            Result.success(destinationFile)
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun deleteBackupFile(
        accessToken: String,
        fileId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${trimmedApiBase()}/files/$fileId"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Gagal menghapus file Drive",
                useTimeout = true,
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .delete()
                        .build()
                },
                handleResponse = { resp, _ ->
                    if (resp.isSuccessful || resp.code == 404) {
                        Result.success(Unit)
                    } else {
                        val err = resp.body?.string().orEmpty()
                        Result.failure(httpFailureException("Gagal menghapus file Drive", resp.code, err))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    /**
     * Enforces max retention (default 30 versions) ONLY AFTER the newest backup succeeded & verified.
     * Never deletes the most recent backup even if retention count is configured unusually low.
     */
    suspend fun pruneOldDriveBackupsAfterVerifiedUpload(
        accessToken: String,
        maxVersionsToKeep: Int = MAX_BACKUP_VERSIONS
    ): Result<Int> {
        val safeMax = maxVersionsToKeep.coerceAtLeast(1)
        val listed = listBackups(accessToken, pageSize = 100).getOrElse { return Result.failure(it) }
        if (listed.size <= safeMax) return Result.success(0)
        val toDelete = listed.drop(safeMax)
        var deletedCount = 0
        for (item in toDelete) {
            if (deleteBackupFile(accessToken, item.id).isSuccess) {
                deletedCount++
            }
        }
        return Result.success(deletedCount)
    }

    suspend fun updateFileInPlace(
        accessToken: String,
        fileId: String,
        localFile: File,
        targetFileName: String = "",
        mimeType: String = "image/jpeg",
        checksumSha256: String = "",
        description: String = "",
        customAppProperties: Map<String, String> = emptyMap(),
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        try {
            require(fileId.isNotBlank()) { "Google Drive File ID kosong untuk files.update." }
            require(localFile.exists() && localFile.length() > 0L) {
                "File lokal tidak ditemukan atau kosong: ${localFile.name}"
            }
            val appProps = JSONObject().apply {
                put(MirrorOutboxCodec.APP_ID_KEY, MirrorOutboxCodec.APP_ID_VALUE)
                if (checksumSha256.isNotBlank()) put("checksumSha256", checksumSha256)
                customAppProperties.forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v.take(120))
                }
            }
            val metadataJson = JSONObject().apply {
                put("name", targetFileName.ifBlank { localFile.name })
                put("mimeType", mimeType)
                if (description.isNotBlank()) put("description", description)
                put("appProperties", appProps)
            }
            val patchUrl = "${trimmedUploadBase()}/files/$fileId?uploadType=multipart&fields=id,name,size,createdTime,modifiedTime,sha256Checksum,appProperties,parents"

            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Google Drive files.update gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    val fileRequestBody = createProgressRequestBody(localFile, mimeType.toMediaType(), onProgress)
                    val multipartBody = MultipartBody.Builder()
                        .setType("multipart/related".toMediaType())
                        .addPart(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .addPart(fileRequestBody)
                        .build()
                    Request.Builder()
                        .url(patchUrl)
                        .header("Authorization", "Bearer $tok")
                        .patch(multipartBody)
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Google Drive files.update gagal", resp.code, bodyStr))
                    } else {
                        Result.success(parseDriveFileJson(JSONObject(bodyStr), fallbackSha256 = checksumSha256))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun uploadTextContentToFolderId(
        accessToken: String,
        parentFolderId: String,
        targetFileName: String,
        jsonOrTextContent: String,
        mimeType: String = "application/json",
        checksumSha256: String = "",
        description: String = "",
        customAppProperties: Map<String, String> = emptyMap()
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        try {
            val appProps = JSONObject().apply {
                put(MirrorOutboxCodec.APP_ID_KEY, MirrorOutboxCodec.APP_ID_VALUE)
                if (checksumSha256.isNotBlank()) put("checksumSha256", checksumSha256)
                customAppProperties.forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v.take(120))
                }
            }
            val metadataJson = JSONObject().apply {
                put("name", targetFileName)
                put("mimeType", mimeType)
                put("parents", JSONArray(listOf(parentFolderId)))
                if (description.isNotBlank()) put("description", description)
                put("appProperties", appProps)
            }
            val uploadUrl = "${trimmedUploadBase()}/files?uploadType=multipart&fields=id,name,size,createdTime,modifiedTime,sha256Checksum,appProperties,parents"

            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Upload JSON ke Google Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    val contentBody = jsonOrTextContent.toRequestBody("$mimeType; charset=utf-8".toMediaType())
                    val multipartBody = MultipartBody.Builder()
                        .setType("multipart/related".toMediaType())
                        .addPart(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .addPart(contentBody)
                        .build()
                    Request.Builder()
                        .url(uploadUrl)
                        .header("Authorization", "Bearer $tok")
                        .post(multipartBody)
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Upload JSON ke Google Drive gagal", resp.code, bodyStr))
                    } else {
                        Result.success(parseDriveFileJson(JSONObject(bodyStr), fallbackSha256 = checksumSha256))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun updateTextContentInPlace(
        accessToken: String,
        fileId: String,
        targetFileName: String,
        jsonOrTextContent: String,
        mimeType: String = "application/json",
        checksumSha256: String = "",
        description: String = "",
        customAppProperties: Map<String, String> = emptyMap()
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        try {
            require(fileId.isNotBlank()) { "Google Drive File ID kosong untuk updateTextContentInPlace." }
            val appProps = JSONObject().apply {
                put(MirrorOutboxCodec.APP_ID_KEY, MirrorOutboxCodec.APP_ID_VALUE)
                if (checksumSha256.isNotBlank()) put("checksumSha256", checksumSha256)
                customAppProperties.forEach { (k, v) ->
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v.take(120))
                }
            }
            val metadataJson = JSONObject().apply {
                put("name", targetFileName)
                put("mimeType", mimeType)
                if (description.isNotBlank()) put("description", description)
                put("appProperties", appProps)
            }
            val patchUrl = "${trimmedUploadBase()}/files/$fileId?uploadType=multipart&fields=id,name,size,createdTime,modifiedTime,sha256Checksum,appProperties,parents"

            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Google Drive files.update JSON gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    val contentBody = jsonOrTextContent.toRequestBody("$mimeType; charset=utf-8".toMediaType())
                    val multipartBody = MultipartBody.Builder()
                        .setType("multipart/related".toMediaType())
                        .addPart(metadataJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .addPart(contentBody)
                        .build()
                    Request.Builder()
                        .url(patchUrl)
                        .header("Authorization", "Bearer $tok")
                        .patch(multipartBody)
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Google Drive files.update JSON gagal", resp.code, bodyStr))
                    } else {
                        Result.success(parseDriveFileJson(JSONObject(bodyStr), fallbackSha256 = checksumSha256))
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun findAllFilesInFolderByName(
        accessToken: String,
        parentFolderId: String,
        fileName: String
    ): Result<List<DriveBackupFileItem>> = withContext(Dispatchers.IO) {
        try {
            val safeName = fileName.replace("'", "").trim()
            val query = "name = '$safeName' and '$parentFolderId' in parents and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val fields = URLEncoder.encode(
                "files(id,name,mimeType,size,createdTime,modifiedTime,sha256Checksum,appProperties,parents)",
                "UTF-8"
            )
            val url = "${trimmedApiBase()}/files?q=$encodedQuery&spaces=drive&fields=$fields"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Lookup file Drive gagal",
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Lookup file Drive gagal", resp.code, bodyStr))
                    } else {
                        val filesArr = JSONObject(bodyStr).optJSONArray("files") ?: JSONArray()
                        val matches = buildList {
                            for (i in 0 until filesArr.length()) {
                                add(parseDriveFileJson(filesArr.getJSONObject(i)))
                            }
                        }
                        Result.success(matches)
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun findFileInFolderByName(
        accessToken: String,
        parentFolderId: String,
        fileName: String
    ): Result<DriveBackupFileItem?> {
        return findAllFilesInFolderByName(accessToken, parentFolderId, fileName).map { it.firstOrNull() }
    }

    suspend fun findFoldersByName(
        accessToken: String,
        folderName: String,
        parentFolderId: String? = null
    ): Result<List<DriveBackupFileItem>> = withContext(Dispatchers.IO) {
        try {
            val safeName = folderName.replace("'", "").trim()
            val parentClause = if (!parentFolderId.isNullOrBlank()) {
                " and '$parentFolderId' in parents"
            } else {
                ""
            }
            val query = "name = '$safeName'$parentClause and mimeType = '$FOLDER_MIME' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val fields = URLEncoder.encode(
                "files(id,name,mimeType,createdTime,modifiedTime,appProperties)",
                "UTF-8"
            )
            val url = "${trimmedApiBase()}/files?q=$encodedQuery&spaces=drive&fields=$fields"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Lookup folder Drive gagal",
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Lookup folder Drive gagal", resp.code, bodyStr))
                    } else {
                        val filesArr = JSONObject(bodyStr).optJSONArray("files") ?: JSONArray()
                        val matches = buildList {
                            for (i in 0 until filesArr.length()) {
                                add(parseDriveFileJson(filesArr.getJSONObject(i)))
                            }
                        }
                        Result.success(matches)
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun listFilesInFolder(
        accessToken: String,
        parentFolderId: String,
        pageSize: Int = 500
    ): Result<List<DriveBackupFileItem>> = withContext(Dispatchers.IO) {
        try {
            val query = "'$parentFolderId' in parents and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val fields = URLEncoder.encode(
                "nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,sha256Checksum,appProperties,parents)",
                "UTF-8"
            )
            val allItems = mutableListOf<DriveBackupFileItem>()
            var pageToken: String? = null
            var pageGuard = 0
            var activeToken = accessToken
            do {
                pageGuard++
                val tokenParam = if (!pageToken.isNullOrBlank()) {
                    "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"
                } else {
                    ""
                }
                val url = "${trimmedApiBase()}/files?q=$encodedQuery&pageSize=$pageSize$tokenParam&spaces=drive&fields=$fields"
                executeAuthenticated(
                    accessToken = activeToken,
                    operationLabel = "List folder Drive gagal",
                    buildRequest = { tok ->
                        Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $tok")
                            .get()
                            .build()
                    },
                    handleResponse = { resp, usedTok ->
                        activeToken = usedTok
                        val bodyStr = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            Result.failure<Unit>(httpFailureException("List folder Drive gagal", resp.code, bodyStr))
                        } else {
                            val obj = JSONObject(bodyStr)
                            val filesArr = obj.optJSONArray("files") ?: JSONArray()
                            for (i in 0 until filesArr.length()) {
                                allItems.add(parseDriveFileJson(filesArr.getJSONObject(i)))
                            }
                            pageToken = obj.optString("nextPageToken", "").takeIf { it.isNotBlank() }
                            Result.success(Unit)
                        }
                    }
                ).getOrElse { return@withContext Result.failure(it) }
            } while (!pageToken.isNullOrBlank() && pageGuard < 50)
            Result.success(allItems)
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun listAllObjectsRecursively(
        accessToken: String,
        rootFolderId: String
    ): Result<List<DriveRecursiveObject>> = withContext(Dispatchers.IO) {
        try {
            val collected = mutableListOf<DriveRecursiveObject>()
            val queue = ArrayDeque<Pair<String, Int>>()
            val visitedFolders = mutableSetOf<String>()
            queue.addLast(rootFolderId to 1)
            visitedFolders.add(rootFolderId)

            while (queue.isNotEmpty()) {
                val (currentParentId, depth) = queue.removeFirst()
                val children = listFilesInFolder(accessToken, currentParentId).getOrElse {
                    return@withContext Result.failure(it)
                }
                for (child in children) {
                    val isFolder = child.mimeType == FOLDER_MIME
                    collected.add(
                        DriveRecursiveObject(
                            item = child,
                            parentFolderId = currentParentId,
                            isFolder = isFolder,
                            depth = depth
                        )
                    )
                    if (isFolder && child.id.isNotBlank() && visitedFolders.add(child.id)) {
                        queue.addLast(child.id to (depth + 1))
                    }
                }
            }
            Result.success(collected.sortedWith(compareByDescending<DriveRecursiveObject> { !it.isFolder }.thenByDescending { it.depth }))
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun listFilesByAppProperty(
        accessToken: String,
        propertyKey: String,
        propertyValue: String,
        pageSize: Int = 500
    ): Result<List<DriveBackupFileItem>> = withContext(Dispatchers.IO) {
        try {
            val safeKey = propertyKey.replace("'", "").trim()
            val safeVal = propertyValue.replace("'", "").trim()
            if (safeKey.isBlank() || safeVal.isBlank()) return@withContext Result.success(emptyList())
            val query = "appProperties has { key='$safeKey' and value='$safeVal' } and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val fields = URLEncoder.encode(
                "nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,sha256Checksum,appProperties,parents)",
                "UTF-8"
            )
            val allItems = mutableListOf<DriveBackupFileItem>()
            var pageToken: String? = null
            var pageGuard = 0
            var activeToken = accessToken
            do {
                pageGuard++
                val tokenParam = if (!pageToken.isNullOrBlank()) {
                    "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"
                } else {
                    ""
                }
                val url = "${trimmedApiBase()}/files?q=$encodedQuery&pageSize=$pageSize$tokenParam&spaces=drive&fields=$fields"
                executeAuthenticated(
                    accessToken = activeToken,
                    operationLabel = "List files by appProperty($safeKey) Drive gagal",
                    useTimeout = true,
                    buildRequest = { tok ->
                        Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer $tok")
                            .get()
                            .build()
                    },
                    handleResponse = { resp, usedTok ->
                        activeToken = usedTok
                        val bodyStr = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            Result.failure<Unit>(httpFailureException("List files by appProperty($safeKey) Drive gagal", resp.code, bodyStr))
                        } else {
                            val obj = JSONObject(bodyStr)
                            val filesArr = obj.optJSONArray("files") ?: JSONArray()
                            for (i in 0 until filesArr.length()) {
                                allItems.add(parseDriveFileJson(filesArr.getJSONObject(i)))
                            }
                            pageToken = obj.optString("nextPageToken", "").takeIf { it.isNotBlank() }
                            Result.success(Unit)
                        }
                    }
                ).getOrElse { return@withContext Result.failure(it) }
            } while (!pageToken.isNullOrBlank() && pageGuard < 50)
            Result.success(allItems)
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun findFilesByRecordUuid(
        accessToken: String,
        recordUuid: String
    ): Result<List<DriveBackupFileItem>> = withContext(Dispatchers.IO) {
        val cleanUuid = recordUuid.trim()
        if (cleanUuid.isBlank()) return@withContext Result.success(emptyList())
        val bySnake = listFilesByAppProperty(accessToken, "record_uuid", cleanUuid)
        if (bySnake.isFailure && DriveApiException.isAuthFailure(bySnake.exceptionOrNull())) {
            return@withContext Result.failure(bySnake.exceptionOrNull()!!)
        }
        val byCamel = listFilesByAppProperty(accessToken, "recordUuid", cleanUuid)
        if (byCamel.isFailure && DriveApiException.isAuthFailure(byCamel.exceptionOrNull())) {
            return@withContext Result.failure(byCamel.exceptionOrNull()!!)
        }
        if (bySnake.isFailure && byCamel.isFailure) {
            return@withContext Result.failure(bySnake.exceptionOrNull() ?: byCamel.exceptionOrNull()!!)
        }
        val combined = linkedMapOf<String, DriveBackupFileItem>()
        bySnake.getOrDefault(emptyList()).forEach { if (!it.trashed && it.recordUuid == cleanUuid) combined[it.id] = it }
        byCamel.getOrDefault(emptyList()).forEach { if (!it.trashed && it.recordUuid == cleanUuid) combined[it.id] = it }
        Result.success(combined.values.toList())
    }

    suspend fun listFilesByAppId(
        accessToken: String,
        appId: String = MirrorOutboxCodec.APP_ID_VALUE,
        pageSize: Int = 500
    ): Result<List<DriveBackupFileItem>> = listFilesByAppProperty(
        accessToken = accessToken,
        propertyKey = MirrorOutboxCodec.APP_ID_KEY,
        propertyValue = appId.ifBlank { MirrorOutboxCodec.APP_ID_VALUE },
        pageSize = pageSize
    )

    suspend fun deleteFileStrict(
        accessToken: String,
        fileId: String,
        fileName: String = ""
    ): DriveDeleteFailure? = withContext(Dispatchers.IO) {
        try {
            val url = "${trimmedApiBase()}/files/$fileId"
            val result = executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Delete file Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .delete()
                        .build()
                },
                handleResponse = { resp, _ ->
                    if (resp.isSuccessful || resp.code == 404) {
                        Result.success<DriveDeleteFailure?>(null)
                    } else {
                        val err = resp.body?.string().orEmpty()
                        Result.success<DriveDeleteFailure?>(
                            DriveDeleteFailure(
                                fileId = fileId,
                                fileName = fileName.ifBlank { fileId },
                                errorCode = "HTTP_${resp.code}",
                                errorMessage = err.ifBlank { "HTTP ${resp.code} saat menghapus file Drive" }
                            )
                        )
                    }
                }
            )
            result.fold(
                onSuccess = { it },
                onFailure = { err ->
                    val isAuth = DriveApiException.isAuthFailure(err)
                    DriveDeleteFailure(
                        fileId = fileId,
                        fileName = fileName.ifBlank { fileId },
                        errorCode = when {
                            isAuth -> DriveApiException.ERROR_CODE_FAILED_AUTH
                            err is DriveApiException -> err.errorCode
                            err is TimeoutCancellationException -> "TIMEOUT_15S"
                            else -> "NETWORK_OR_IO"
                        },
                        errorMessage = err.message ?: "Gagal menghapus file Drive"
                    )
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            DriveDeleteFailure(
                fileId = fileId,
                fileName = fileName.ifBlank { fileId },
                errorCode = if (e is TimeoutCancellationException) "TIMEOUT_15S" else "NETWORK_OR_IO",
                errorMessage = e.message ?: "Gagal menghapus file Drive"
            )
        }
    }

    suspend fun verifyFileDeletedOrTrashedStrict(
        accessToken: String,
        fileId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext Result.success(true)
        try {
            val fields = URLEncoder.encode("id,name,trashed", "UTF-8")
            val url = "${trimmedApiBase()}/files/$fileId?fields=$fields"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Verifikasi penghapusan file Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, _ ->
                    if (resp.code == 404) {
                        Result.success(true)
                    } else {
                        val bodyStr = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            Result.failure(httpFailureException("Verifikasi penghapusan file Drive gagal", resp.code, bodyStr))
                        } else {
                            val obj = JSONObject(bodyStr)
                            if (obj.optBoolean("trashed", false)) {
                                Result.success(true)
                            } else {
                                Result.failure(
                                    IllegalStateException("File Google Drive ${obj.optString("name", fileId)} ($fileId) masih aktif setelah permintaan hapus.")
                                )
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun verifyUploadedJsonContentStrict(
        accessToken: String,
        fileId: String,
        expectedSha256: String,
        expectedUuid: String = "",
        expectedVersion: Long = 0L,
        expectedDatasetGeneration: String = ""
    ): Result<DriveBackupFileItem> = withContext(Dispatchers.IO) {
        val metaItem = verifyRemoteFileIntegrity(accessToken, fileId, expectedSha256)
            .getOrElse { return@withContext Result.failure(it) }
        val downloadedText = downloadFileText(accessToken, fileId)
            .getOrElse { return@withContext Result.failure(it) }

        if (expectedSha256.isNotBlank()) {
            val actualSha256 = MirrorOutboxCodec.computeSha256(downloadedText)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                return@withContext Result.failure(
                    IllegalStateException("Read-after-write SHA-256 tidak cocok untuk $fileId: lokal=$expectedSha256, cloud=$actualSha256")
                )
            }
        }

        val parsed = runCatching { JSONObject(downloadedText) }.getOrElse { err ->
            return@withContext Result.failure(
                IllegalStateException("Read-after-write JSON tidak valid untuk $fileId: ${err.message}", err)
            )
        }
        if (expectedUuid.isNotBlank()) {
            val remoteUuid = parsed.optString("uuid", "")
            if (remoteUuid != expectedUuid) {
                return@withContext Result.failure(
                    IllegalStateException("Read-after-write UUID tidak cocok untuk $fileId: lokal=$expectedUuid, cloud=$remoteUuid")
                )
            }
        }
        if (expectedVersion > 0L && (expectedUuid.isNotBlank() || parsed.has("version"))) {
            val remoteVersion = parsed.optLong("version", -1L)
            if (remoteVersion != expectedVersion) {
                return@withContext Result.failure(
                    IllegalStateException("Read-after-write versi tidak cocok untuk $fileId: lokal=v$expectedVersion, cloud=v$remoteVersion")
                )
            }
        }
        if (expectedDatasetGeneration.isNotBlank() && parsed.has("datasetGeneration")) {
            val remoteGen = parsed.optString("datasetGeneration", "")
            if (remoteGen != expectedDatasetGeneration) {
                return@withContext Result.failure(
                    IllegalStateException("Read-after-write datasetGeneration tidak cocok untuk $fileId: lokal=$expectedDatasetGeneration, cloud=$remoteGen")
                )
            }
        }
        Result.success(metaItem)
    }

    suspend fun findActiveFilesMatchingUuidOrName(
        accessToken: String,
        folderIds: List<String>,
        recordUuid: String,
        deterministicNames: List<String> = emptyList()
    ): Result<List<DriveBackupFileItem>> = withContext(Dispatchers.IO) {
        try {
            val distinctFolders = folderIds.filter { it.isNotBlank() }.distinct()
            val nameSet = deterministicNames.filter { it.isNotBlank() }.toSet()
            val matchedById = linkedMapOf<String, DriveBackupFileItem>()

            for (folderId in distinctFolders) {
                val objects = listAllObjectsRecursively(accessToken, folderId).getOrElse { err ->
                    return@withContext Result.failure(err)
                }
                for (obj in objects) {
                    if (obj.isFolder || obj.item.trashed) continue
                    val matchesUuid = recordUuid.isNotBlank() && obj.item.recordUuid == recordUuid
                    val matchesName = obj.item.name in nameSet
                    if (matchesUuid || matchesName) {
                        matchedById[obj.item.id] = obj.item
                    }
                }
            }
            Result.success(matchedById.values.toList())
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    suspend fun downloadFileText(
        accessToken: String,
        fileId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "${trimmedApiBase()}/files/$fileId?alt=media"
            executeAuthenticated(
                accessToken = accessToken,
                operationLabel = "Download teks Drive gagal",
                useTimeout = true,
                buildRequest = { tok ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $tok")
                        .get()
                        .build()
                },
                handleResponse = { resp, _ ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Result.failure(httpFailureException("Download teks Drive gagal", resp.code, bodyStr))
                    } else {
                        Result.success(bodyStr)
                    }
                }
            )
        } catch (e: Exception) {
            rethrowIfExternalCancellation(e)
            Result.failure(e)
        }
    }

    private fun createProgressRequestBody(
        file: File,
        mediaType: MediaType,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)?
    ): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType = mediaType
            override fun contentLength(): Long = file.length()
            override fun writeTo(sink: BufferedSink) {
                val total = file.length().coerceAtLeast(1L)
                var uploaded = 0L
                file.source().use { source ->
                    var read: Long
                    while (source.read(sink.buffer, 8192L).also { read = it } != -1L) {
                        uploaded += read
                        sink.flush()
                        onProgress?.invoke(uploaded, total)
                    }
                }
            }
        }
    }

    private fun parseDriveFileJson(obj: JSONObject, fallbackSha256: String = ""): DriveBackupFileItem {
        val props = obj.optJSONObject("appProperties") ?: JSONObject()
        val rawPropsMap = buildMap<String, String> {
            val keys = props.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = props.optString(k, "")
                if (k.isNotBlank() && v.isNotBlank()) put(k, v)
            }
        }
        val parentsArr = obj.optJSONArray("parents")
        val parentIds = if (parentsArr != null) {
            buildList(parentsArr.length()) {
                for (i in 0 until parentsArr.length()) {
                    val p = parentsArr.optString(i, "")
                    if (p.isNotBlank()) add(p)
                }
            }
        } else emptyList()
        val checksum = props.optString("checksum", "").ifBlank {
            props.optString("checksumSha256", obj.optString("sha256Checksum", fallbackSha256))
        }
        val gen = props.optString("generation_id", "").ifBlank {
            props.optString("datasetGeneration", "")
        }
        val uuid = props.optString("record_uuid", "").ifBlank {
            props.optString("recordUuid", "")
        }
        val revision = props.optString("revision", "").toLongOrNull()
            ?: props.optString("version", "0").toLongOrNull()
            ?: 0L
        val syncTs = props.optString("sync_timestamp", "").toLongOrNull()
            ?: props.optString("updatedAtMillis", "0").toLongOrNull()
            ?: 0L
        val entityType = props.optString("entity_type", "").ifBlank {
            props.optString("recordCategory", "")
        }
        val schemaVer = props.optString("schema_version", "0").toIntOrNull() ?: 0
        return DriveBackupFileItem(
            id = obj.optString("id", ""),
            name = obj.optString("name", "backup.zip"),
            sizeBytes = obj.optString("size", "0").toLongOrNull() ?: obj.optLong("size", 0L),
            createdTime = obj.optString("createdTime", ""),
            modifiedTime = obj.optString("modifiedTime", ""),
            sha256Checksum = checksum,
            backupId = props.optString("backupId", ""),
            backupType = props.optString("backupType", "FULL_BACKUP"),
            customerCount = props.optString("customerCount", "0").toIntOrNull() ?: 0,
            unitCount = props.optString("unitCount", "0").toIntOrNull() ?: 0,
            transactionCount = props.optString("transactionCount", "0").toIntOrNull() ?: 0,
            paymentCount = props.optString("paymentCount", "0").toIntOrNull() ?: 0,
            photoCount = props.optString("photoCount", "0").toIntOrNull() ?: 0,
            mimeType = obj.optString("mimeType", ""),
            datasetGeneration = gen,
            recordUuid = uuid,
            remoteVersion = revision,
            updatedAtMillis = syncTs,
            applicationId = props.optString("applicationId", ""),
            appId = props.optString(MirrorOutboxCodec.APP_ID_KEY, ""),
            datasetId = props.optString("datasetId", ""),
            parentIds = parentIds,
            trashed = obj.optBoolean("trashed", false),
            entityType = entityType,
            schemaVersion = schemaVer,
            syncTimestamp = syncTs,
            rawAppProperties = rawPropsMap
        )
    }
}
