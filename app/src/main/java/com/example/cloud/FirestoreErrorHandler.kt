package com.example.cloud

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreError"

    fun handleFirestoreError(
        error: Throwable,
        operationType: OperationType,
        path: String?,
        auth: FirebaseAuth
    ): String {
        val user = auth.currentUser
        val payload = JSONObject().apply {
            put("error", error.message ?: error.toString())
            put("operationType", operationType.name.lowercase())
            put("path", path ?: "")
            put("authInfo", JSONObject().apply {
                put("userId", user?.uid ?: "")
                put("email", user?.email ?: "")
                put("emailVerified", user?.isEmailVerified ?: false)
                put("isAnonymous", user?.isAnonymous ?: false)
                put("providerId", user?.providerData?.firstOrNull()?.providerId ?: "")
            })
        }
        val json = payload.toString()
        Log.e(TAG, "Firestore Error: $json", error)
        return json
    }
}
