package com.example.viewmodel

enum class OperationNotificationType {
    SUCCESS,
    ERROR,
    INFO
}

data class AppLoadingState(
    val isLoading: Boolean = false,
    val message: String = "",
    val progress: Float? = null, // 0f..1f for staged tasks (e.g. Restore Backup), null for indeterminate
    val isBlocking: Boolean = false
)

data class AppNotificationEvent(
    val id: Long = System.currentTimeMillis(),
    val type: OperationNotificationType = OperationNotificationType.INFO,
    val message: String,
    val detail: String = "",
    val retryLabel: String? = null,
    val onRetry: (() -> Unit)? = null
)
