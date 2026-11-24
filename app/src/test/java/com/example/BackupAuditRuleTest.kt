package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.cloud.CloudFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupAuditRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ownerCanCreateReadBackupMetadataAndAuditLogsAreAppendOnlyAndIsolated() = runBlocking {
        val ownerUid = signInTestUser("backup_owner_a@test.com")
        val cloudRepo = CloudFirestoreRepository(firestore, auth)

        val uniqueId = "bkp_test_${System.currentTimeMillis()}"
        val validSha256 = "a1b2c3d4e5f60123456789abcdef0123456789abcdef0123456789abcdef0123"
        val savedBackupId = withTimeout(10000L) {
            cloudRepo.upsertBackupMetadata(
                backupId = uniqueId,
                fileName = "SEWAIN_BACKUP_FULL_BACKUP_20261115.zip",
                backupType = "FULL_BACKUP",
                locationType = "LOKAL_DAN_DRIVE",
                driveFileId = "drv_file_999",
                verificationStatus = "TERVERIFIKASI",
                checksumSha256 = validSha256,
                fileSizeBytes = 20480L,
                isEncrypted = true,
                customerCount = 10,
                unitCount = 12,
                transactionCount = 8,
                paymentCount = 5,
                photoCount = 4,
                createdAtDisplay = "15 Nov 2026 01:00"
            )
        }
        assertEquals(uniqueId, savedBackupId)

        val backupSnap = withTimeout(10000L) {
            firestore.collection("users").document(ownerUid)
                .collection("backup_metadata").document(savedBackupId)
                .get().await()
        }
        assertTrue(backupSnap.exists())
        assertEquals("LOKAL_DAN_DRIVE", backupSnap.getString("locationType"))

        val auditDocId = withTimeout(10000L) {
            cloudRepo.appendBackupAuditLog(
                actionType = "BACKUP_DRIVE",
                status = "SUKSES",
                fileName = "SEWAIN_BACKUP_FULL_BACKUP_20261115.zip",
                checksum = validSha256,
                recordCount = 30,
                actorEmail = "backup_owner_a@test.com",
                detailMessage = "Audit log test",
                timestampDisplay = "15 Nov 2026 01:00"
            )
        }

        val auditRef = firestore.collection("users").document(ownerUid)
            .collection("backup_audit_logs").document(auditDocId)

        val createdAudit = withTimeout(10000L) { auditRef.get().await() }
        assertTrue(createdAudit.exists())

        // Append-only: update and delete MUST be denied by Firestore rules
        val updateErr = runCatching {
            withTimeout(8000L) { auditRef.update("status", "TAMPERED").await() }
        }.exceptionOrNull()
        assertNotNull("Audit log update must be denied", updateErr)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            updateErr?.unwrapFirestoreException()?.code
        )

        val deleteErr = runCatching {
            withTimeout(8000L) { auditRef.delete().await() }
        }.exceptionOrNull()
        assertNotNull("Audit log delete must be denied", deleteErr)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            deleteErr?.unwrapFirestoreException()?.code
        )

        // Cross-user isolation check: User B must not read User A's backup metadata
        signInTestUser("backup_attacker_b@test.com")
        val crossReadErr = runCatching {
            withTimeout(8000L) {
                firestore.collection("users").document(ownerUid)
                    .collection("backup_metadata").document(savedBackupId)
                    .get().await()
            }
        }.exceptionOrNull()
        assertNotNull("Cross-user backup_metadata read must be denied", crossReadErr)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossReadErr?.unwrapFirestoreException()?.code
        )
    }
}
