const { describe, it, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs, Timestamp, serverTimestamp } = require("firebase/firestore");

const PROJECT_ID = "gen-lang-client-0797449126";
const DATABASE_ID = "ai-studio-android-sewainaj-88979dd1-0d1b-4010-a521-86d8f350a330";
const USER_A = "uid_user_a";
const USER_B = "uid_user_b";

let testEnv;

function validCustomer(uid = USER_A) {
  return {
    userId: uid,
    localId: 1,
    kodePelanggan: "CUST/SAF/0001",
    namaLengkap: "Budi Santoso",
    nik: "3372010101900001",
    nomorHp: "081234567890",
    alamatLengkap: "Jl. Slamet Riyadi No. 10, Surakarta",
    statusPelanggan: "Pelanggan Aktif",
    clientUpdatedAtMillis: 1730000000000,
    version: 1,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validUnit(uid = USER_A) {
  return {
    userId: uid,
    localId: 10,
    kodeUnit: "FRZ-100-01",
    namaUnit: "Chest Freezer 100L RSA",
    jenisUnit: "Chest Freezer",
    kapasitasLiter: 100,
    statusUnit: "Tersedia",
    clientUpdatedAtMillis: 1730000000000,
    version: 1,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validTransaction(uid = USER_A) {
  return {
    userId: uid,
    localId: 100,
    nomorInvoice: "INV/SAF/2026/0001",
    customerId: 1,
    unitId: 10,
    totalTagihan: 250000,
    nominalPembayaran: 100000,
    statusPembayaran: "Dibayar Sebagian",
    statusPenyewaan: "Dalam Penyewaan",
    jenisProgram: "Sewa Reguler",
    clientUpdatedAtMillis: 1730000000000,
    version: 1,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

describe("SEWAin Aja Freezer SOC Firestore Security Rules", () => {
  before(async () => {
    const rules = fs.readFileSync("firestore.rules", "utf8");
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        databaseId: DATABASE_ID,
        rules,
        host: "127.0.0.1",
        port: 8085,
      },
    });
  });

  after(async () => {
    if (testEnv) await testEnv.cleanup();
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
  });

  it("denies unauthenticated reads and writes across all paths", async () => {
    const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
    await assertFails(getDoc(doc(unauthDb, "users", USER_A)));
    await assertFails(getDoc(doc(unauthDb, "users", USER_A, "customers", "cust_1")));
    await assertFails(setDoc(doc(unauthDb, "users", USER_A, "customers", "cust_1"), validCustomer(USER_A)));
    await assertFails(getDoc(doc(unauthDb, "random_root_collection", "doc1")));
  });

  it("allows owner CRUD on UserProfile and denies cross-user profile access", async () => {
    const dbA = testEnv.authenticatedContext(USER_A, { email: "a@gmail.com" }).firestore(DATABASE_ID);
    const dbB = testEnv.authenticatedContext(USER_B, { email: "b@gmail.com" }).firestore(DATABASE_ID);

    const profileRefA = doc(dbA, "users", USER_A);
    await assertSucceeds(setDoc(profileRefA, {
      userId: USER_A,
      displayName: "Admin A",
      email: "a@gmail.com",
      photoUrl: "https://example.com/a.jpg",
      lastActiveDevice: "Pixel",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));

    await assertSucceeds(getDoc(profileRefA));
    await assertFails(getDoc(doc(dbB, "users", USER_A)));
    await assertFails(setDoc(doc(dbB, "users", USER_A), {
      userId: USER_A,
      displayName: "Hijack",
      email: "b@gmail.com",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));
    await assertFails(deleteDoc(doc(dbB, "users", USER_A)));
    await assertSucceeds(deleteDoc(profileRefA));
  });

  it("enforces user isolation and CRUD on customers, units, and transactions", async () => {
    const dbA = testEnv.authenticatedContext(USER_A).firestore(DATABASE_ID);
    const dbB = testEnv.authenticatedContext(USER_B).firestore(DATABASE_ID);

    const custRefA = doc(dbA, "users", USER_A, "customers", "cust_1");
    const unitRefA = doc(dbA, "users", USER_A, "freezer_units", "unit_10");
    const txRefA = doc(dbA, "users", USER_A, "transactions", "tx_100");

    await assertSucceeds(setDoc(custRefA, validCustomer(USER_A)));
    await assertSucceeds(setDoc(unitRefA, validUnit(USER_A)));
    await assertSucceeds(setDoc(txRefA, validTransaction(USER_A)));

    const listSnap = await assertSucceeds(getDocs(collection(dbA, "users", USER_A, "customers")));
    assert.equal(listSnap.size, 1);

    await assertFails(getDoc(doc(dbB, "users", USER_A, "customers", "cust_1")));
    await assertFails(getDocs(collection(dbB, "users", USER_A, "customers")));
    await assertFails(updateDoc(doc(dbB, "users", USER_A, "freezer_units", "unit_10"), { statusUnit: "Hilang" }));
    await assertFails(deleteDoc(doc(dbB, "users", USER_A, "transactions", "tx_100")));

    await assertSucceeds(updateDoc(unitRefA, {
      statusUnit: "Disewa",
      version: 2,
      clientUpdatedAtMillis: 1730000005000,
      updatedAt: serverTimestamp(),
    }));

    await assertSucceeds(deleteDoc(txRefA));
  });

  it("rejects shadow/ghost fields, missing required fields, and invalid doc IDs", async () => {
    const dbA = testEnv.authenticatedContext(USER_A).firestore(DATABASE_ID);

    await assertFails(setDoc(doc(dbA, "users", USER_A, "customers", "cust_bad_field"), {
      ...validCustomer(USER_A),
      injectedAdminFlag: true,
    }));

    const missingName = validCustomer(USER_A);
    delete missingName.namaLengkap;
    await assertFails(setDoc(doc(dbA, "users", USER_A, "customers", "cust_missing"), missingName));

    await assertFails(setDoc(doc(dbA, "users", USER_A, "customers", "bad.id!with#symbols"), validCustomer(USER_A)));
  });

  it("enforces immutable createdAt and rejects future timestamps", async () => {
    const dbA = testEnv.authenticatedContext(USER_A).firestore(DATABASE_ID);
    const custRef = doc(dbA, "users", USER_A, "customers", "cust_ts");
    await assertSucceeds(setDoc(custRef, validCustomer(USER_A)));

    // Future createdAt rejected
    const futureTs = Timestamp.fromMillis(Date.now() + 3600 * 1000);
    await assertFails(setDoc(doc(dbA, "users", USER_A, "customers", "cust_future"), {
      ...validCustomer(USER_A),
      createdAt: futureTs,
      updatedAt: futureTs,
    }));

    // Tampering with createdAt on update rejected
    await assertFails(updateDoc(custRef, {
      createdAt: Timestamp.fromMillis(1700000000000),
      updatedAt: serverTimestamp(),
      version: 2,
    }));
  });

  it("validates supporting cloud collections (payment_proofs, photo_metadata, company_settings, migration_state)", async () => {
    const dbA = testEnv.authenticatedContext(USER_A).firestore(DATABASE_ID);
    const dbB = testEnv.authenticatedContext(USER_B).firestore(DATABASE_ID);

    await assertSucceeds(setDoc(doc(dbA, "users", USER_A, "payment_proofs", "proof_1"), {
      userId: USER_A,
      localId: 1,
      transactionId: 100,
      nominalPembayaran: 150000,
      metodePembayaran: "QRIS",
      fotoBuktiUrl: "https://firebasestorage.example.com/proof_1.jpg",
      clientUpdatedAtMillis: 1730000000000,
      version: 1,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));

    await assertSucceeds(setDoc(doc(dbA, "users", USER_A, "photo_metadata", "photo_cust_1_ktp"), {
      userId: USER_A,
      ownerType: "CUSTOMER",
      ownerLocalId: 1,
      ownerCloudId: "cust_1",
      photoSlot: "KTP_DEPAN",
      storagePath: `users/${USER_A}/customers/cust_1/ktp/ktp_depan.jpg`,
      downloadUrl: "https://firebasestorage.example.com/ktp.jpg",
      uploadStatus: "UPLOADED",
      clientUpdatedAtMillis: 1730000000000,
      version: 1,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));

    await assertSucceeds(setDoc(doc(dbA, "users", USER_A, "company_settings", "primary"), {
      userId: USER_A,
      namaPerusahaan: "SEWAin Aja Freezer SOC",
      clientUpdatedAtMillis: 1730000000000,
      version: 1,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));

    await assertSucceeds(setDoc(doc(dbA, "users", USER_A, "migration_state", "sqlite_v14"), {
      userId: USER_A,
      currentStep: 10,
      stepLabel: "Verifikasi jumlah data",
      status: "COMPLETED",
      localCustomersCount: 5,
      cloudCustomersCount: 5,
      clientUpdatedAtMillis: 1730000000000,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));

    await assertFails(getDoc(doc(dbB, "users", USER_A, "migration_state", "sqlite_v14")));
  });

  it("enforces owner isolation and immutability on backup_metadata and backup_audit_logs", async () => {
    const dbA = testEnv.authenticatedContext(USER_A, { email: "a@gmail.com" }).firestore(DATABASE_ID);
    const dbB = testEnv.authenticatedContext(USER_B, { email: "b@gmail.com" }).firestore(DATABASE_ID);

    const bkpRefA = doc(dbA, "users", USER_A, "backup_metadata", "bkp_20261115_0100");
    await assertSucceeds(setDoc(bkpRefA, {
      userId: USER_A,
      backupId: "bkp_20261115_0100",
      fileName: "SEWAIN_BACKUP_20261115.zip",
      backupType: "FULL_BACKUP",
      locationType: "LOKAL_DAN_DRIVE",
      driveFileId: "drive_file_123",
      verificationStatus: "TERVERIFIKASI",
      checksumSha256: "a".repeat(64),
      fileSizeBytes: 204800,
      isEncrypted: true,
      customerCount: 15,
      unitCount: 20,
      transactionCount: 18,
      paymentCount: 25,
      photoCount: 40,
      databaseVersion: 15,
      appVersion: "2.7.0",
      createdAtDisplay: "15 Nov 2026 01:00",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));

    await assertFails(getDoc(doc(dbB, "users", USER_A, "backup_metadata", "bkp_20261115_0100")));
    await assertFails(deleteDoc(doc(dbB, "users", USER_A, "backup_metadata", "bkp_20261115_0100")));

    const auditRefA = doc(dbA, "users", USER_A, "backup_audit_logs", "audit_001");
    await assertSucceeds(setDoc(auditRefA, {
      userId: USER_A,
      actionType: "BACKUP_DRIVE",
      status: "SUKSES",
      fileName: "SEWAIN_BACKUP_20261115.zip",
      checksum: "a".repeat(64),
      recordCount: 53,
      actorEmail: "a@gmail.com",
      detailMessage: "Auto backup uploaded to Google Drive",
      timestampDisplay: "15 Nov 2026 01:00",
      timestampMillis: 1731632400000,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }));

    await assertSucceeds(getDoc(auditRefA));
    await assertFails(getDoc(doc(dbB, "users", USER_A, "backup_audit_logs", "audit_001")));
    // Audit logs cannot be mutated; only owner may purge during verified hard reset
    await assertFails(updateDoc(auditRefA, { status: "TAMPERED" }));
    await assertFails(deleteDoc(doc(dbB, "users", USER_A, "backup_audit_logs", "audit_001")));
    await assertSucceeds(deleteDoc(auditRefA));
  });
});
