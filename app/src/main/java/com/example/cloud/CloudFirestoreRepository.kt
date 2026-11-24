package com.example.cloud

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

data class FirestorePageResult<T>(
    val items: List<T>,
    val lastSnapshot: DocumentSnapshot?,
    val hasMore: Boolean
)

data class CloudFirestoreWipeReport(
    val deletedDocumentCount: Int,
    val remainingDocumentCount: Int,
    val wipedCollections: List<String>,
    val nonFatalWarnings: List<String> = emptyList()
)

open class CloudFirestoreRepository {
    companion object {
        val ALL_USER_SUBCOLLECTIONS: List<String> = listOf(
            "customers",
            "freezer_units",
            "transactions",
            "transaction_unit_items",
            "payment_proofs",
            "unit_payment_allocations",
            "transaction_signatures",
            "freezer_returns",
            "photo_metadata",
            "company_settings",
            "migration_state",
            "backup_metadata",
            "backup_audit_logs"
        )
    }
    private val db: FirebaseFirestore?
    private val auth: FirebaseAuth?

    protected constructor() {
        this.db = null
        this.auth = null
    }

    constructor(
        db: FirebaseFirestore,
        auth: FirebaseAuth = Firebase.auth
    ) {
        this.db = db
        this.auth = auth
    }

    constructor(context: Context) : this(
        db = Firebase.firestore(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = Firebase.auth
    )

    private fun requireDb(): FirebaseFirestore =
        db ?: throw IllegalStateException("FirebaseFirestore belum diinisialisasi.")

    private fun requireAuth(): FirebaseAuth =
        auth ?: throw IllegalStateException("FirebaseAuth belum diinisialisasi.")

    private fun requireUserId(): String =
        requireAuth().currentUser?.uid ?: throw IllegalStateException("Pengguna belum masuk dengan akun Google.")

    private fun userDocRef(uid: String): DocumentReference =
        requireDb().collection("users").document(uid)

    private fun customersCol(uid: String) = userDocRef(uid).collection("customers")
    private fun unitsCol(uid: String) = userDocRef(uid).collection("freezer_units")
    private fun transactionsCol(uid: String) = userDocRef(uid).collection("transactions")
    private fun unitItemsCol(uid: String) = userDocRef(uid).collection("transaction_unit_items")
    private fun paymentProofsCol(uid: String) = userDocRef(uid).collection("payment_proofs")
    private fun allocationsCol(uid: String) = userDocRef(uid).collection("unit_payment_allocations")
    private fun signaturesCol(uid: String) = userDocRef(uid).collection("transaction_signatures")
    private fun returnsCol(uid: String) = userDocRef(uid).collection("freezer_returns")
    private fun photoMetaCol(uid: String) = userDocRef(uid).collection("photo_metadata")
    private fun companySettingsCol(uid: String) = userDocRef(uid).collection("company_settings")
    private fun migrationStateCol(uid: String) = userDocRef(uid).collection("migration_state")
    private fun backupMetadataCol(uid: String) = userDocRef(uid).collection("backup_metadata")
    private fun backupAuditLogsCol(uid: String) = userDocRef(uid).collection("backup_audit_logs")

    private suspend fun upsertPreservingCreatedAt(
        docRef: DocumentReference,
        opPath: String,
        payloadBuilder: (existingCreatedAt: Timestamp?) -> Map<String, Any>
    ) {
        try {
            val snap = docRef.get().await()
            val existingCreatedAt = snap.getTimestamp("createdAt")
            val opType = if (snap.exists()) OperationType.UPDATE else OperationType.CREATE
            val payload = payloadBuilder(existingCreatedAt)
            try {
                docRef.set(payload).await()
            } catch (e: Exception) {
                FirestoreErrorHandler.handleFirestoreError(e, opType, opPath, requireAuth())
                throw e
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, opPath, requireAuth())
            throw e
        }
    }

    open suspend fun upsertUserProfile(profile: CloudUserProfile) {
        val uid = requireUserId()
        require(profile.userId == uid) { "User ID mismatch" }
        upsertPreservingCreatedAt(userDocRef(uid), "users/$uid") { existingCreatedAt ->
            profile.toMap(existingCreatedAt)
        }
    }

    open suspend fun upsertCustomer(customer: CloudCustomer): String {
        val uid = requireUserId()
        val docId = customer.id.ifBlank { CloudIdMapper.customerDocId(customer.localId) }
        val normalized = customer.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            customersCol(uid).document(docId),
            "users/$uid/customers/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun getCustomer(docId: String): CloudCustomer? {
        val uid = requireUserId()
        val path = "users/$uid/customers/$docId"
        return try {
            customersCol(uid).document(docId).get().await().toCloudCustomerEstimated()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.GET, path, requireAuth())
            throw e
        }
    }

    open suspend fun upsertFreezerUnit(unit: CloudFreezerUnit): String {
        val uid = requireUserId()
        val docId = unit.id.ifBlank { CloudIdMapper.unitDocId(unit.localId) }
        val normalized = unit.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            unitsCol(uid).document(docId),
            "users/$uid/freezer_units/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun getFreezerUnit(docId: String): CloudFreezerUnit? {
        val uid = requireUserId()
        val path = "users/$uid/freezer_units/$docId"
        return try {
            unitsCol(uid).document(docId).get().await().toCloudUnitEstimated()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.GET, path, requireAuth())
            throw e
        }
    }

    open suspend fun upsertRentalTransaction(tx: CloudRentalTransaction): String {
        val uid = requireUserId()
        val docId = tx.id.ifBlank { CloudIdMapper.transactionDocId(tx.localId) }
        val normalized = tx.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            transactionsCol(uid).document(docId),
            "users/$uid/transactions/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun getRentalTransaction(docId: String): CloudRentalTransaction? {
        val uid = requireUserId()
        val path = "users/$uid/transactions/$docId"
        return try {
            transactionsCol(uid).document(docId).get().await().toCloudTransactionEstimated()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.GET, path, requireAuth())
            throw e
        }
    }

    open suspend fun upsertTransactionUnitItem(item: CloudTransactionUnitItem): String {
        val uid = requireUserId()
        val docId = item.id.ifBlank { CloudIdMapper.transactionUnitItemDocId(item.localId) }
        val normalized = item.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            unitItemsCol(uid).document(docId),
            "users/$uid/transaction_unit_items/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun upsertPaymentProof(proof: CloudPaymentProof): String {
        val uid = requireUserId()
        val docId = proof.id.ifBlank { CloudIdMapper.paymentProofDocId(proof.localId) }
        val normalized = proof.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            paymentProofsCol(uid).document(docId),
            "users/$uid/payment_proofs/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun getPaymentProof(docId: String): CloudPaymentProof? {
        val uid = requireUserId()
        val path = "users/$uid/payment_proofs/$docId"
        return try {
            paymentProofsCol(uid).document(docId).get().await().toCloudPaymentProofEstimated()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.GET, path, requireAuth())
            throw e
        }
    }

    open suspend fun upsertUnitPaymentAllocation(allocation: CloudUnitPaymentAllocation): String {
        val uid = requireUserId()
        val docId = allocation.id.ifBlank { CloudIdMapper.allocationDocId(allocation.localId) }
        val normalized = allocation.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            allocationsCol(uid).document(docId),
            "users/$uid/unit_payment_allocations/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun upsertTransactionSignature(signature: CloudTransactionSignature): String {
        val uid = requireUserId()
        val docId = signature.id.ifBlank { CloudIdMapper.signatureDocId(signature.localId) }
        val normalized = signature.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            signaturesCol(uid).document(docId),
            "users/$uid/transaction_signatures/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun upsertFreezerReturn(ret: CloudFreezerReturn): String {
        val uid = requireUserId()
        val docId = ret.id.ifBlank { CloudIdMapper.returnDocId(ret.localId) }
        val normalized = ret.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            returnsCol(uid).document(docId),
            "users/$uid/freezer_returns/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun upsertPhotoMetadata(meta: CloudPhotoMetadata): String {
        val uid = requireUserId()
        val docId = meta.id.ifBlank {
            CloudIdMapper.photoMetaDocId(meta.ownerType, meta.ownerLocalId, meta.photoSlot)
        }
        val normalized = meta.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            photoMetaCol(uid).document(docId),
            "users/$uid/photo_metadata/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    open suspend fun upsertCompanySettings(settings: CloudCompanySettings): String {
        val uid = requireUserId()
        val docId = settings.id.ifBlank { "primary" }
        val normalized = settings.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            companySettingsCol(uid).document(docId),
            "users/$uid/company_settings/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    suspend fun upsertMigrationState(state: CloudMigrationState): String {
        val uid = requireUserId()
        val docId = state.id.ifBlank { "sqlite_v14" }
        val normalized = state.copy(id = docId, userId = uid)
        upsertPreservingCreatedAt(
            migrationStateCol(uid).document(docId),
            "users/$uid/migration_state/$docId"
        ) { normalized.toMap(it) }
        return docId
    }

    suspend fun upsertBackupMetadata(
        backupId: String,
        fileName: String,
        backupType: String,
        locationType: String,
        driveFileId: String,
        verificationStatus: String,
        checksumSha256: String,
        fileSizeBytes: Long,
        isEncrypted: Boolean,
        customerCount: Int,
        unitCount: Int,
        transactionCount: Int,
        paymentCount: Int,
        photoCount: Int,
        createdAtDisplay: String
    ): String {
        val uid = requireUserId()
        val safeDocId = backupId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(120).ifBlank {
            "bkp_${System.currentTimeMillis()}"
        }
        val path = "users/$uid/backup_metadata/$safeDocId"
        upsertPreservingCreatedAt(backupMetadataCol(uid).document(safeDocId), path) { existingCreatedAt ->
            val nowTs = com.google.firebase.firestore.FieldValue.serverTimestamp()
            buildMap {
                put("userId", uid)
                put("backupId", safeDocId)
                put("fileName", fileName.take(280))
                put("backupType", backupType.take(50))
                put("locationType", locationType.take(36))
                put("driveFileId", driveFileId.take(240))
                put("verificationStatus", verificationStatus.take(36))
                put("checksumSha256", checksumSha256.take(128))
                put("fileSizeBytes", fileSizeBytes.coerceAtLeast(0L))
                put("isEncrypted", isEncrypted)
                put("customerCount", customerCount.coerceAtLeast(0))
                put("unitCount", unitCount.coerceAtLeast(0))
                put("transactionCount", transactionCount.coerceAtLeast(0))
                put("paymentCount", paymentCount.coerceAtLeast(0))
                put("photoCount", photoCount.coerceAtLeast(0))
                put("databaseVersion", 15)
                put("appVersion", "2.7.0")
                put("createdAtDisplay", createdAtDisplay.take(56))
                put("createdAt", existingCreatedAt ?: nowTs)
                put("updatedAt", nowTs)
            }
        }
        return safeDocId
    }

    suspend fun appendBackupAuditLog(
        actionType: String,
        status: String,
        fileName: String = "",
        checksum: String = "",
        recordCount: Int = 0,
        actorEmail: String = "",
        detailMessage: String = "",
        timestampDisplay: String = "",
        timestampMillis: Long = System.currentTimeMillis()
    ): String {
        val uid = requireUserId()
        val docId = "audit_${timestampMillis}_${(1000..9999).random()}"
        val path = "users/$uid/backup_audit_logs/$docId"
        val nowTs = com.google.firebase.firestore.FieldValue.serverTimestamp()
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("actionType", actionType.take(56))
            put("status", status.take(36))
            put("fileName", fileName.take(280))
            put("checksum", checksum.take(128))
            put("recordCount", recordCount.coerceAtLeast(0))
            put("actorEmail", actorEmail.take(300))
            put("detailMessage", detailMessage.take(2000))
            put("timestampDisplay", timestampDisplay.take(56))
            put("timestampMillis", timestampMillis.coerceAtLeast(0L))
            put("createdAt", nowTs)
            put("updatedAt", nowTs)
        }
        try {
            backupAuditLogsCol(uid).document(docId).set(payload).await()
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.CREATE, path, requireAuth())
            throw e
        }
    }

    open suspend fun deleteDocument(collectionName: String, docId: String) {
        val uid = requireUserId()
        val path = "users/$uid/$collectionName/$docId"
        try {
            userDocRef(uid).collection(collectionName).document(docId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.DELETE, path, requireAuth())
            throw e
        }
    }

    open suspend fun deletePhotoMetadata(docId: String) {
        deleteDocument("photo_metadata", docId)
    }

    // --- Paginated Queries for Large-Scale Datasets ---

    suspend fun fetchCustomersPage(
        pageSize: Long = 50L,
        startAfter: DocumentSnapshot? = null
    ): FirestorePageResult<CloudCustomer> {
        val uid = requireUserId()
        val path = "users/$uid/customers"
        return try {
            var q = customersCol(uid)
                .orderBy("clientUpdatedAtMillis", Query.Direction.DESCENDING)
                .limit(pageSize)
            if (startAfter != null) q = q.startAfter(startAfter)
            val snap = q.get().await()
            val items = snap.documents.mapNotNull { it.toCloudCustomerEstimated() }
            FirestorePageResult(
                items = items,
                lastSnapshot = snap.documents.lastOrNull(),
                hasMore = snap.documents.size >= pageSize
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.LIST, path, requireAuth())
            throw e
        }
    }

    suspend fun fetchUnitsPage(
        pageSize: Long = 50L,
        startAfter: DocumentSnapshot? = null
    ): FirestorePageResult<CloudFreezerUnit> {
        val uid = requireUserId()
        val path = "users/$uid/freezer_units"
        return try {
            var q = unitsCol(uid)
                .orderBy("clientUpdatedAtMillis", Query.Direction.DESCENDING)
                .limit(pageSize)
            if (startAfter != null) q = q.startAfter(startAfter)
            val snap = q.get().await()
            val items = snap.documents.mapNotNull { it.toCloudUnitEstimated() }
            FirestorePageResult(
                items = items,
                lastSnapshot = snap.documents.lastOrNull(),
                hasMore = snap.documents.size >= pageSize
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.LIST, path, requireAuth())
            throw e
        }
    }

    suspend fun fetchTransactionsPage(
        pageSize: Long = 50L,
        startAfter: DocumentSnapshot? = null
    ): FirestorePageResult<CloudRentalTransaction> {
        val uid = requireUserId()
        val path = "users/$uid/transactions"
        return try {
            var q = transactionsCol(uid)
                .orderBy("clientUpdatedAtMillis", Query.Direction.DESCENDING)
                .limit(pageSize)
            if (startAfter != null) q = q.startAfter(startAfter)
            val snap = q.get().await()
            val items = snap.documents.mapNotNull { it.toCloudTransactionEstimated() }
            FirestorePageResult(
                items = items,
                lastSnapshot = snap.documents.lastOrNull(),
                hasMore = snap.documents.size >= pageSize
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.LIST, path, requireAuth())
            throw e
        }
    }

    // --- Collection Count Queries for Migration Verification ---

    suspend fun countCollection(collectionName: String): Int {
        val uid = requireUserId()
        val path = "users/$uid/$collectionName"
        return try {
            val countSnap = userDocRef(uid).collection(collectionName)
                .count()
                .get(AggregateSource.SERVER)
                .await()
            countSnap.count.toInt()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FirestoreErrorHandler.handleFirestoreError(e, OperationType.LIST, path, requireAuth())
            throw e
        }
    }

    // --- Real-Time Snapshot Listeners (Multi-Device Sync) ---

    fun observeCustomers(uid: String): Flow<List<CloudCustomer>> {
        val path = "users/$uid/customers"
        return callbackFlow {
            val reg = customersCol(uid).addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toCloudCustomerEstimated() }.orEmpty()
                trySend(list)
            }
            awaitClose { reg.remove() }
        }.catch { err ->
            if (auth?.currentUser?.uid == uid) {
                FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, requireAuth())
            }
            throw err
        }
    }

    fun observeUnits(uid: String): Flow<List<CloudFreezerUnit>> {
        val path = "users/$uid/freezer_units"
        return callbackFlow {
            val reg = unitsCol(uid).addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toCloudUnitEstimated() }.orEmpty()
                trySend(list)
            }
            awaitClose { reg.remove() }
        }.catch { err ->
            if (auth?.currentUser?.uid == uid) {
                FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, requireAuth())
            }
            throw err
        }
    }

    fun observeTransactions(uid: String): Flow<List<CloudRentalTransaction>> {
        val path = "users/$uid/transactions"
        return callbackFlow {
            val reg = transactionsCol(uid).addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toCloudTransactionEstimated() }.orEmpty()
                trySend(list)
            }
            awaitClose { reg.remove() }
        }.catch { err ->
            if (auth?.currentUser?.uid == uid) {
                FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, requireAuth())
            }
            throw err
        }
    }

    // --- Incremental Real-Time Document Change Listeners (ADDED / MODIFIED / REMOVED) ---

    private fun mapChangeType(type: DocumentChange.Type): CloudChangeType = when (type) {
        DocumentChange.Type.ADDED -> CloudChangeType.ADDED
        DocumentChange.Type.MODIFIED -> CloudChangeType.MODIFIED
        DocumentChange.Type.REMOVED -> CloudChangeType.REMOVED
    }

    fun observeCustomerChanges(uid: String): Flow<List<CloudDocumentChange<CloudCustomer>>> {
        val path = "users/$uid/customers"
        return callbackFlow {
            val reg = customersCol(uid).addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val hasPendingWrites = snap.metadata.hasPendingWrites()
                val isFromCache = snap.metadata.isFromCache()
                val changes = snap.documentChanges.map { dc ->
                    CloudDocumentChange(
                        type = mapChangeType(dc.type),
                        docId = dc.document.id,
                        data = dc.document.toCloudCustomerEstimated(),
                        hasPendingWrites = hasPendingWrites,
                        isFromCache = isFromCache
                    )
                }
                if (changes.isNotEmpty()) {
                    trySend(changes)
                }
            }
            awaitClose { reg.remove() }
        }.catch { err ->
            if (auth?.currentUser?.uid == uid) {
                FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, requireAuth())
            }
            throw err
        }
    }

    fun observeUnitChanges(uid: String): Flow<List<CloudDocumentChange<CloudFreezerUnit>>> {
        val path = "users/$uid/freezer_units"
        return callbackFlow {
            val reg = unitsCol(uid).addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val hasPendingWrites = snap.metadata.hasPendingWrites()
                val isFromCache = snap.metadata.isFromCache()
                val changes = snap.documentChanges.map { dc ->
                    CloudDocumentChange(
                        type = mapChangeType(dc.type),
                        docId = dc.document.id,
                        data = dc.document.toCloudUnitEstimated(),
                        hasPendingWrites = hasPendingWrites,
                        isFromCache = isFromCache
                    )
                }
                if (changes.isNotEmpty()) {
                    trySend(changes)
                }
            }
            awaitClose { reg.remove() }
        }.catch { err ->
            if (auth?.currentUser?.uid == uid) {
                FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, requireAuth())
            }
            throw err
        }
    }

    fun observeTransactionChanges(uid: String): Flow<List<CloudDocumentChange<CloudRentalTransaction>>> {
        val path = "users/$uid/transactions"
        return callbackFlow {
            val reg = transactionsCol(uid).addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val hasPendingWrites = snap.metadata.hasPendingWrites()
                val isFromCache = snap.metadata.isFromCache()
                val changes = snap.documentChanges.map { dc ->
                    CloudDocumentChange(
                        type = mapChangeType(dc.type),
                        docId = dc.document.id,
                        data = dc.document.toCloudTransactionEstimated(),
                        hasPendingWrites = hasPendingWrites,
                        isFromCache = isFromCache
                    )
                }
                if (changes.isNotEmpty()) {
                    trySend(changes)
                }
            }
            awaitClose { reg.remove() }
        }.catch { err ->
            if (auth?.currentUser?.uid == uid) {
                FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, requireAuth())
            }
            throw err
        }
    }

    fun observePaymentProofChanges(uid: String): Flow<List<CloudDocumentChange<CloudPaymentProof>>> {
        val path = "users/$uid/payment_proofs"
        return callbackFlow {
            val reg = paymentProofsCol(uid).addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val hasPendingWrites = snap.metadata.hasPendingWrites()
                val isFromCache = snap.metadata.isFromCache()
                val changes = snap.documentChanges.map { dc ->
                    CloudDocumentChange(
                        type = mapChangeType(dc.type),
                        docId = dc.document.id,
                        data = dc.document.toCloudPaymentProofEstimated(),
                        hasPendingWrites = hasPendingWrites,
                        isFromCache = isFromCache
                    )
                }
                if (changes.isNotEmpty()) {
                    trySend(changes)
                }
            }
            awaitClose { reg.remove() }
        }.catch { err ->
            if (auth?.currentUser?.uid == uid) {
                FirestoreErrorHandler.handleFirestoreError(err, OperationType.LIST, path, requireAuth())
            }
            throw err
        }
    }

    /**
     * Deep-clean hard reset routine for Cloud Firestore:
     * Queries and batch-deletes ALL documents across every subcollection under users/{uid}
     * (in chunks of up to 400 per WriteBatch) and deletes the users/{uid} root document,
     * then verifies that 0 documents remain across every collection.
     */
    open suspend fun wipeAllUserCollectionsAndVerifyEmpty(
        uid: String,
        batchSize: Int = 400
    ): CloudFirestoreWipeReport {
        require(uid.isNotBlank()) { "UID tidak boleh kosong saat melakukan hard reset Firestore." }
        val firestore = requireDb()
        val effectiveBatchSize = batchSize.coerceIn(1, 450).toLong()
        var totalDeleted = 0
        val wipedCollections = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for (subName in ALL_USER_SUBCOLLECTIONS) {
            val colRef = userDocRef(uid).collection(subName)
            var subLoops = 0
            val maxLoops = 200 // up to 90,000 docs per collection safeguard
            while (subLoops < maxLoops) {
                subLoops++
                val snap = try {
                    colRef.limit(effectiveBatchSize).get().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    warnings += "Read $subName warning: ${e.localizedMessage}"
                    break
                }
                if (snap.isEmpty) break
                val batch = firestore.batch()
                snap.documents.forEach { docSnap ->
                    batch.delete(docSnap.reference)
                }
                try {
                    batch.commit().await()
                    totalDeleted += snap.size()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Fallback per-document delete so a single blocked doc never stalls the rest
                    var deletedInFallback = 0
                    for (docSnap in snap.documents) {
                        try {
                            docSnap.reference.delete().await()
                            totalDeleted++
                            deletedInFallback++
                        } catch (docErr: CancellationException) {
                            throw docErr
                        } catch (docErr: Exception) {
                            warnings += "Delete $subName/${docSnap.id} warning: ${docErr.localizedMessage}"
                        }
                    }
                    if (deletedInFallback == 0) break
                }
                if (snap.size() < effectiveBatchSize) break
            }
            wipedCollections += subName
        }

        // Delete parent user profile document users/{uid}
        try {
            val rootSnap = userDocRef(uid).get().await()
            if (rootSnap.exists()) {
                userDocRef(uid).delete().await()
                totalDeleted++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            warnings += "Delete users/$uid root warning: ${e.localizedMessage}"
        }

        // Strict verification pass: count any remaining documents across every subcollection + root
        var remaining = 0
        for (subName in ALL_USER_SUBCOLLECTIONS) {
            val checkSnap = try {
                userDocRef(uid).collection(subName).limit(10).get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                warnings += "Verify $subName warning: ${e.localizedMessage}"
                null
            }
            if (checkSnap != null && !checkSnap.isEmpty) {
                remaining += checkSnap.size()
            }
        }
        val rootCheck = try {
            userDocRef(uid).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (rootCheck != null && rootCheck.exists()) {
            remaining++
        }

        return CloudFirestoreWipeReport(
            deletedDocumentCount = totalDeleted,
            remainingDocumentCount = remaining,
            wipedCollections = wipedCollections,
            nonFatalWarnings = warnings
        )
    }
}
