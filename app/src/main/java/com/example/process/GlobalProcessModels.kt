package com.example.process

import java.util.UUID

enum class GlobalProcessStatus {
    PENDING,
    PROCESSING,
    VERIFYING,
    COMPLETED,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELLED
}

enum class ProcessStageBadge(val displayLabel: String) {
    PROCESSING("PROCESSING"),
    LOCAL_SAVED("LOCAL SAVED"),
    UPLOADING("UPLOADING"),
    SYNCING("SYNCING"),
    VERIFYING("VERIFYING"),
    COMPLETED("✓ SELESAI"),
    PARTIAL_LOCAL("⚠ TERSIMPAN LOKAL"),
    FAILED("✕ GAGAL"),
    CANCELLED("DIBATALKAN")
}

enum class VerificationItemStatus(val displayLabel: String) {
    PENDING("MENUNGGU"),
    SUCCESS("BERHASIL"),
    SKIPPED_NOT_REQUIRED("TIDAK ADA"),
    PARTIAL_OFFLINE("TERTUNDA"),
    FAILED("GAGAL")
}

data class ProcessVerificationReport(
    val localDatabaseStatus: VerificationItemStatus = VerificationItemStatus.PENDING,
    val localDatabaseDetail: String = "",
    val documentationStatus: VerificationItemStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
    val documentationDetail: String = "",
    val googleDriveStatus: VerificationItemStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
    val googleDriveDetail: String = "",
    val synchronizationStatus: VerificationItemStatus = VerificationItemStatus.SKIPPED_NOT_REQUIRED,
    val synchronizationDetail: String = "",
    val requireCloudForFullCompletion: Boolean = true
) {
    val isLocalVerified: Boolean
        get() = localDatabaseStatus == VerificationItemStatus.SUCCESS

    val isDocumentationAcceptable: Boolean
        get() = documentationStatus == VerificationItemStatus.SUCCESS ||
            documentationStatus == VerificationItemStatus.SKIPPED_NOT_REQUIRED

    val isCloudFullyVerified: Boolean
        get() {
            if (!requireCloudForFullCompletion) return true
            val driveOk = googleDriveStatus == VerificationItemStatus.SUCCESS ||
                googleDriveStatus == VerificationItemStatus.SKIPPED_NOT_REQUIRED
            val syncOk = synchronizationStatus == VerificationItemStatus.SUCCESS ||
                synchronizationStatus == VerificationItemStatus.SKIPPED_NOT_REQUIRED
            return driveOk && syncOk
        }

    /**
     * Strict gate: ONLY true when local database, required documentation, and required cloud/Drive sync
     * have all been actually verified without pending/failed/stale/conflict issues.
     */
    val allRequiredPassed: Boolean
        get() = isLocalVerified && isDocumentationAcceptable && isCloudFullyVerified

    /**
     * True when local database succeeded and is verified safe on device,
     * but Google Drive / Cloud synchronization or cloud photo upload has not yet completed.
     */
    val isLocalSavedCloudIncomplete: Boolean
        get() = isLocalVerified &&
            requireCloudForFullCompletion &&
            documentationStatus != VerificationItemStatus.FAILED &&
            !allRequiredPassed
}

data class GlobalProcessState(
    val processId: String = UUID.randomUUID().toString(),
    val idempotencyKey: String = "",
    val processName: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 1,
    val progressPercentage: Int = 0,
    val isIndeterminate: Boolean = false,
    val currentActivityMessage: String = "",
    val processStatus: GlobalProcessStatus = GlobalProcessStatus.PENDING,
    val stageBadge: ProcessStageBadge = ProcessStageBadge.PROCESSING,
    val failedStageName: String = "",
    val errorMessage: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val completedStageIds: Set<String> = emptySet(),
    val verificationReport: ProcessVerificationReport? = null,
    val canRetry: Boolean = false,
    val canCancel: Boolean = false,
    val autoDismissDelayMillis: Long = 1400L,
    val isVisible: Boolean = false
) {
    val isActive: Boolean
        get() = processStatus == GlobalProcessStatus.PENDING ||
            processStatus == GlobalProcessStatus.PROCESSING ||
            processStatus == GlobalProcessStatus.VERIFYING

    val isTerminal: Boolean
        get() = processStatus == GlobalProcessStatus.COMPLETED ||
            processStatus == GlobalProcessStatus.PARTIAL_SUCCESS ||
            processStatus == GlobalProcessStatus.FAILED ||
            processStatus == GlobalProcessStatus.CANCELLED

    val stepFractionText: String
        get() = "${progressPercentage.coerceIn(0, 100)}/100"
}

data class ProcessExecutionOutcome<T>(
    val result: T? = null,
    val verificationReport: ProcessVerificationReport,
    val partialSummaryMessage: String = "",
    val failureStageName: String = "",
    val failureMessage: String = ""
)
