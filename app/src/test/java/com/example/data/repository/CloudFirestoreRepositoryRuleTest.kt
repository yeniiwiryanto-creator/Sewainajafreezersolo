package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.cloud.CloudCustomer
import com.example.cloud.CloudFirestoreRepository
import com.example.cloud.CloudFreezerUnit
import com.example.cloud.CloudRentalTransaction
import com.example.cloud.CloudUserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CloudFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ownerCanWriteReadAndObserveCustomersUnitsAndTransactions() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudFirestoreRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertUserProfile(
                CloudUserProfile(
                    id = uid,
                    userId = uid,
                    displayName = "Admin Alice",
                    email = ALICE_EMAIL
                )
            )

            val custId = repo.upsertCustomer(
                CloudCustomer(
                    localId = 101L,
                    kodePelanggan = "CUST/SAF/0101",
                    namaLengkap = "Budi Santoso",
                    nomorHp = "6281234567890",
                    clientUpdatedAtMillis = System.currentTimeMillis()
                )
            )
            assertEquals("cust_101", custId)

            val unitId = repo.upsertFreezerUnit(
                CloudFreezerUnit(
                    localId = 201L,
                    kodeUnit = "FRZ/SAF/0201",
                    namaUnit = "GEA AB-226R",
                    kapasitasLiter = 220,
                    statusUnit = "Tersedia",
                    clientUpdatedAtMillis = System.currentTimeMillis()
                )
            )
            assertEquals("unit_201", unitId)

            val txId = repo.upsertRentalTransaction(
                CloudRentalTransaction(
                    localId = 301L,
                    nomorInvoice = "INV/SAF/2026/0301",
                    customerId = 101L,
                    customerCloudId = "cust_101",
                    namaPelangganSnapshot = "Budi Santoso",
                    unitId = 201L,
                    unitCloudId = "unit_201",
                    namaUnitSnapshot = "GEA AB-226R",
                    hargaSewa = 200000L,
                    totalTagihan = 200000L,
                    jenisProgram = "Sewa Reguler",
                    clientUpdatedAtMillis = System.currentTimeMillis()
                )
            )
            assertEquals("tx_301", txId)

            val fetchedCust = repo.getCustomer("cust_101")
            assertNotNull(fetchedCust)
            assertEquals("Budi Santoso", fetchedCust?.namaLengkap)

            val fetchedUnit = repo.getFreezerUnit("unit_201")
            assertNotNull(fetchedUnit)
            assertEquals("FRZ/SAF/0201", fetchedUnit?.kodeUnit)
        }

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCustomers(uid).first { list -> list.any { it.id == "cust_101" } }
        }
        assertTrue(observed.any { it.namaLengkap == "Budi Santoso" })
    }

    @Test
    fun ownerIdempotentUpdatePreservesImmutableCreatedAt() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = CloudFirestoreRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertCustomer(
                CloudCustomer(
                    localId = 102L,
                    kodePelanggan = "CUST/SAF/0102",
                    namaLengkap = "Siti Aminah",
                    nomorHp = "6281111111111",
                    clientUpdatedAtMillis = 1000L
                )
            )
            val first = repo.getCustomer("cust_102")
            assertNotNull(first?.createdAt)

            repo.upsertCustomer(
                CloudCustomer(
                    localId = 102L,
                    kodePelanggan = "CUST/SAF/0102",
                    namaLengkap = "Siti Aminah Updated",
                    nomorHp = "6281111111111",
                    clientUpdatedAtMillis = 2000L,
                    version = 2L
                )
            )
            val second = repo.getCustomer("cust_102")
            assertEquals("Siti Aminah Updated", second?.namaLengkap)
            assertEquals(first?.createdAt, second?.createdAt)
        }
    }

    @Test
    fun paginatedQueriesAndCollectionCountsWorkWithFirestoreRules() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = CloudFirestoreRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            for (i in 1..5) {
                repo.upsertCustomer(
                    CloudCustomer(
                        localId = 400L + i,
                        kodePelanggan = "CUST/SAF/040$i",
                        namaLengkap = "Pelanggan Page $i",
                        nomorHp = "62812000000$i",
                        clientUpdatedAtMillis = 5000L + i
                    )
                )
            }
            val page1 = repo.fetchCustomersPage(pageSize = 3L)
            assertEquals(3, page1.items.size)
            assertTrue(page1.hasMore)

            val page2 = repo.fetchCustomersPage(pageSize = 3L, startAfter = page1.lastSnapshot)
            assertTrue(page2.items.size >= 2)

            val totalCustomers = repo.countCollection("customers")
            assertTrue(totalCustomers >= 5)
        }
    }

    @Test
    fun crossUserReadAndWriteDeniedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudFirestoreRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertCustomer(
                CloudCustomer(
                    localId = 501L,
                    kodePelanggan = "CUST/SAF/0501",
                    namaLengkap = "Private Alice Customer",
                    nomorHp = "628999999999",
                    clientUpdatedAtMillis = System.currentTimeMillis()
                )
            )
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid)
                    .collection("customers").document("cust_501")
                    .get().await()
            }
            fail("Cross-user Firestore read should fail with PERMISSION_DENIED")
        } catch (e: Throwable) {
            val ffe = e.unwrapFirestoreException()
                ?: error("Expected FirebaseFirestoreException, got $e")
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
        }
    }

    @Test
    fun unauthenticatedObserveCustomersFailsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repo = CloudFirestoreRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCustomers(aliceUid).first()
            }
            fail("Unauthenticated observeCustomers should fail with PERMISSION_DENIED")
        } catch (e: Throwable) {
            val ffe = e.unwrapFirestoreException()
                ?: error("Expected nested FirebaseFirestoreException, got $e")
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_saf@test.com"
        const val BOB_EMAIL = "bob_saf@test.com"
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
