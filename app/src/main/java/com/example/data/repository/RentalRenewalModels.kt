package com.example.data.repository

import com.example.data.local.CustomerEntity
import com.example.data.local.FreezerUnitEntity
import com.example.data.local.RentalExtensionHistoryEntity
import com.example.data.local.RentalTransactionEntity
import com.example.data.local.TransactionUnitItemEntity
import com.example.data.local.UnitPaymentAllocationEntity
import com.example.data.local.UnitRentalPriceEntity
import com.example.util.DateCurrencyUtils

/**
 * Informasi lengkap satu unit aktif yang sedang disewa oleh pelanggan,
 * termasuk riwayat perpanjangan per unit dan riwayat pembayaran Program Hak Milik per unit.
 */
data class CustomerActiveUnitRenewalInfo(
    val unitId: Long,
    val unitItemId: Long,
    val sourceTransactionId: Long,
    val sourceInvoiceNumber: String,
    val kodeUnit: String,
    val namaUnit: String,
    val merek: String,
    val kapasitasLiter: Int,
    val nomorSeri: String,
    val jenisProgram: String, // "Sewa Reguler" | "Sewa Hak Milik"
    val tanggalMulaiSewa: String,
    val tanggalAkhirSewa: String,
    val tanggalJatuhTempo: String,
    val statusPembayaran: String,
    val hargaSewaMasterBulanan: Long,
    val hargaCicilanHakMilikMaster: Long,
    val masterPricesByPeriodDays: Map<Int, Long>,
    val totalPeriodeHakMilik: Int,
    val sudahDibayarHakMilik: Int,
    val sisaPeriodeHakMilik: Int,
    val pembayaranBerikutnyaHakMilik: Int,
    val historiTanggalPembayaran: List<String>,
    val historiPerpanjanganUnit: List<RentalExtensionHistoryEntity>
) {
    val isHakMilik: Boolean
        get() = jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)

    fun resolveMasterPriceForPeriod(periodDays: Int): Long {
        if (isHakMilik) {
            return hargaCicilanHakMilikMaster.takeIf { it > 0L }
                ?: hargaSewaMasterBulanan.takeIf { it > 0L }
                ?: 300_000L
        }
        return RentalRenewalCalculator.resolveMasterRegulerPrice(
            periodDays = periodDays,
            masterPricesByPeriodDays = masterPricesByPeriodDays,
            fallbackMonthlyPrice = hargaSewaMasterBulanan
        )
    }
}

/**
 * Profil lengkap pelanggan saat membuka form Perpanjangan Sewa.
 */
data class CustomerRenewalProfile(
    val customer: CustomerEntity,
    val activeUnits: List<CustomerActiveUnitRenewalInfo>,
    val rentalHistory: List<RentalTransactionEntity>,
    val extensionHistory: List<RentalExtensionHistoryEntity>
)

/**
 * Konfigurasi perpanjangan untuk satu unit yang dipilih pengguna.
 */
data class UnitRenewalSelection(
    val unitId: Long,
    val unitItemId: Long,
    val sourceTransactionId: Long,
    val sourceInvoiceNumber: String,
    val kodeUnit: String,
    val namaUnit: String,
    val nomorSeri: String,
    val jenisProgram: String,
    val periodDays: Int = 30,
    val tanggalMulaiBaru: String,
    val tanggalAkhirBaru: String,
    val tanggalJatuhTempoBaru: String = tanggalAkhirBaru,
    val hargaSewa: Long,
    val diskon: Long = 0L,
    val nominalDibayar: Long,
    val totalPeriodeHakMilik: Int = 0,
    val pembayaranKeSebelumnya: Int = 0,
    val pembayaranKeBaru: Int = 1,
    val catatan: String = ""
) {
    val isHakMilik: Boolean
        get() = jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)

    val tagihanBersih: Long
        get() = (hargaSewa - diskon).coerceAtLeast(0L)

    val sisaTagihan: Long
        get() = (tagihanBersih - nominalDibayar).coerceAtLeast(0L)

    val statusPembayaran: String
        get() = when {
            tagihanBersih <= 0L || nominalDibayar >= tagihanBersih -> "Lunas"
            nominalDibayar > 0L -> "Dibayar Sebagian"
            else -> "Belum Bayar"
        }
}

/**
 * Konfigurasi penambahan unit sewa baru yang digabung dalam satu transaksi perpanjangan.
 */
data class NewUnitDuringRenewalSelection(
    val unitId: Long,
    val kodeUnit: String,
    val namaUnit: String,
    val merek: String,
    val kapasitasLiter: Int,
    val nomorSeri: String,
    val jenisProgram: String = "Sewa Reguler", // "Sewa Reguler" | "Sewa Hak Milik"
    val periodDays: Int = 30,
    val totalPeriodeHakMilik: Int = 12,
    val hargaSewaMaster: Long,
    val diskon: Long = 0L,
    val nominalDibayar: Long = hargaSewaMaster,
    val tanggalMulaiSewa: String = DateCurrencyUtils.todayIso(),
    val tanggalAkhirSewa: String = DateCurrencyUtils.addDaysToIsoDate(DateCurrencyUtils.todayIso(), 30),
    val catatan: String = ""
) {
    val isHakMilik: Boolean
        get() = jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)

    val tagihanBersih: Long
        get() = (hargaSewaMaster - diskon).coerceAtLeast(0L)

    val sisaTagihan: Long
        get() = (tagihanBersih - nominalDibayar).coerceAtLeast(0L)

    val statusPembayaran: String
        get() = when {
            tagihanBersih <= 0L || nominalDibayar >= tagihanBersih -> "Lunas"
            nominalDibayar > 0L -> "Dibayar Sebagian"
            else -> "Belum Bayar"
        }
}

data class DetailedRenewalRequest(
    val customerId: Long,
    val selectedRenewals: List<UnitRenewalSelection>,
    val additionalNewUnits: List<NewUnitDuringRenewalSelection> = emptyList(),
    val ongkirTambahan: Long = 0L,
    val diskonTambahan: Long = 0L,
    val metodePembayaran: String = "Transfer Bank",
    val namaPetugas: String = "",
    val officerAccountId: String = "",
    val catatanUmum: String = ""
)

data class IntegratedRenewalSummary(
    val totalRenewalGross: Long,
    val totalNewUnitsGross: Long,
    val totalItemDiscounts: Long,
    val globalDiscount: Long,
    val ongkirTambahan: Long,
    val grandTotal: Long,
    val totalPaid: Long,
    val remainingBalance: Long,
    val paymentStatus: String
)

data class DetailedRenewalResult(
    val transaction: RentalTransactionEntity,
    val renewedUnitsCount: Int,
    val addedNewUnitsCount: Int,
    val fulfilledHakMilikUnitNames: List<String> = emptyList()
)

object RentalRenewalCalculator {

    fun resolveMasterRegulerPrice(
        periodDays: Int,
        masterPricesByPeriodDays: Map<Int, Long>,
        fallbackMonthlyPrice: Long
    ): Long {
        val exact = masterPricesByPeriodDays[periodDays]
        if (exact != null && exact > 0L) return exact
        val monthly = masterPricesByPeriodDays[30]?.takeIf { it > 0L }
            ?: fallbackMonthlyPrice.takeIf { it > 0L }
            ?: 300_000L
        return when (periodDays) {
            30 -> monthly
            else -> ((monthly.toDouble() / 30.0) * periodDays.coerceAtLeast(1)).toLong()
                .coerceAtLeast(10_000L)
        }
    }

    fun resolveMasterPriceForUnit(
        unit: FreezerUnitEntity,
        unitPrices: List<UnitRentalPriceEntity>,
        jenisProgram: String,
        periodDays: Int = 30
    ): Long {
        val map = unitPrices.filter { it.unitId == unit.id && it.price > 0L }
            .associate { it.periodDays to it.price }
        if (jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)) {
            return unit.cicilanPerBulan.takeIf { it > 0L }
                ?: map[30]?.takeIf { it > 0L }
                ?: 300_000L
        }
        val fallbackMonthly = map[30]?.takeIf { it > 0L }
            ?: unit.cicilanPerBulan.takeIf { it > 0L }
            ?: 300_000L
        return resolveMasterRegulerPrice(
            periodDays = periodDays,
            masterPricesByPeriodDays = map,
            fallbackMonthlyPrice = fallbackMonthly
        )
    }

    fun calculateIntegratedSummary(
        renewals: List<UnitRenewalSelection>,
        newUnits: List<NewUnitDuringRenewalSelection>,
        ongkirTambahan: Long = 0L,
        globalDiscount: Long = 0L
    ): IntegratedRenewalSummary {
        val renewalGross = renewals.sumOf { it.hargaSewa.coerceAtLeast(0L) }
        val newUnitsGross = newUnits.sumOf { it.hargaSewaMaster.coerceAtLeast(0L) }
        val itemDiscounts = renewals.sumOf { it.diskon.coerceAtLeast(0L) } +
            newUnits.sumOf { it.diskon.coerceAtLeast(0L) }
        val safeGlobalDiscount = globalDiscount.coerceAtLeast(0L)
        val safeOngkir = ongkirTambahan.coerceAtLeast(0L)
        val grandTotal = (renewalGross + newUnitsGross + safeOngkir - itemDiscounts - safeGlobalDiscount)
            .coerceAtLeast(0L)
        val totalPaid = (renewals.sumOf { it.nominalDibayar.coerceAtLeast(0L) } +
            newUnits.sumOf { it.nominalDibayar.coerceAtLeast(0L) }).coerceAtLeast(0L)
        val remaining = (grandTotal - totalPaid).coerceAtLeast(0L)
        val status = when {
            grandTotal <= 0L || totalPaid >= grandTotal -> "Lunas"
            totalPaid > 0L -> "Dibayar Sebagian"
            else -> "Belum Bayar"
        }
        return IntegratedRenewalSummary(
            totalRenewalGross = renewalGross,
            totalNewUnitsGross = newUnitsGross,
            totalItemDiscounts = itemDiscounts,
            globalDiscount = safeGlobalDiscount,
            ongkirTambahan = safeOngkir,
            grandTotal = grandTotal,
            totalPaid = totalPaid,
            remainingBalance = remaining,
            paymentStatus = status
        )
    }

    /**
     * Resolves active units and independent Hak Milik payment histories for a customer
     * from persisted SQLite records (transactions, unit items, allocations, extension history).
     */
    fun buildCustomerProfile(
        customer: CustomerEntity,
        allUnits: List<FreezerUnitEntity>,
        allUnitPrices: List<UnitRentalPriceEntity>,
        allTransactions: List<RentalTransactionEntity>,
        allUnitItems: List<TransactionUnitItemEntity>,
        allAllocations: List<UnitPaymentAllocationEntity>,
        allExtensions: List<RentalExtensionHistoryEntity>
    ): CustomerRenewalProfile {
        val custTxs = allTransactions
            .filter { !it.isDeleted && it.customerId == customer.id }
            .sortedByDescending { it.id }

        val activeTxs = custTxs.filter { tx ->
            !tx.statusPenyewaan.equals("Selesai", ignoreCase = true) &&
                !tx.statusPenyewaan.equals("Dibatalkan", ignoreCase = true)
        }
        val activeTxById = activeTxs.associateBy { it.id }
        val custTxIds = custTxs.map { it.id }.toSet()

        val custExtensions = allExtensions.filter { ext ->
            ext.customerId == customer.id ||
                ext.originalTransactionId in custTxIds ||
                ext.newTransactionId in custTxIds
        }.sortedByDescending { it.id }

        val unitById = allUnits.associateBy { it.id }
        val pricesByUnitId = allUnitPrices.groupBy { it.unitId }
            .mapValues { (_, list) -> list.filter { it.price > 0L }.associate { it.periodDays to it.price } }

        // Group active TransactionUnitItemEntity per physical unitId, picking the latest active record per unit
        val activeItemsByUnit = allUnitItems
            .filter { item ->
                item.customerId == customer.id &&
                    item.transactionId in activeTxById &&
                    !item.statusPenyewaan.equals("Selesai", ignoreCase = true) &&
                    !item.statusPenyewaan.equals("Dibatalkan", ignoreCase = true)
            }
            .groupBy { it.unitId }

        val activeInfos = mutableListOf<CustomerActiveUnitRenewalInfo>()
        val coveredUnitIds = mutableSetOf<Long>()

        activeItemsByUnit.forEach { (unitId, itemsForUnit) ->
            val latestItem = itemsForUnit.maxByOrNull { it.id } ?: return@forEach
            val sourceTx = activeTxById[latestItem.transactionId] ?: return@forEach
            val masterUnit = unitById[unitId]
            // Only treat as actively rented if unit hasn't been marked Hilang/Tersedia/Perbaikan (unless still active item)
            val isHakMilik = latestItem.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)

            val unitAllItemsAcrossHistory = allUnitItems.filter { it.customerId == customer.id && it.unitId == unitId }
            val unitAllocations = allAllocations
                .filter { it.customerId == customer.id && it.unitId == unitId && it.allocatedAmount > 0L }
                .sortedBy { it.paymentDate }
            val unitExtensions = custExtensions.filter { ext ->
                ext.unitId == unitId ||
                    (ext.unitId == 0L && (ext.originalTransactionId == sourceTx.id || ext.newTransactionId == sourceTx.id))
            }

            val totalPeriode = if (isHakMilik) {
                listOf(
                    latestItem.totalPeriodeHakMilik,
                    unitAllItemsAcrossHistory.maxOfOrNull { it.totalPeriodeHakMilik } ?: 0,
                    unitExtensions.maxOfOrNull { it.totalPeriodeHakMilik } ?: 0,
                    sourceTx.totalPeriodeHakMilik,
                    masterUnit?.jumlahCicilanTotal ?: 0,
                    12
                ).firstOrNull { it > 0 } ?: 12
            } else 0

            val paidCountFromHistory = if (isHakMilik) {
                val maxItemStep = unitAllItemsAcrossHistory.maxOfOrNull { it.pembayaranKeHakMilik } ?: 0
                val maxExtStep = unitExtensions.maxOfOrNull { it.pembayaranKeHakMilik } ?: 0
                val allocPaidCount = unitAllocations.size
                val txStep = if (sourceTx.unitId == unitId && sourceTx.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)) {
                    sourceTx.pembayaranKeHakMilik
                } else 0
                maxOf(maxItemStep, maxExtStep, allocPaidCount, txStep, 1).coerceAtMost(totalPeriode)
            } else 0

            val remainingHm = if (isHakMilik) (totalPeriode - paidCountFromHistory).coerceAtLeast(0) else 0
            val nextHm = if (isHakMilik) (paidCountFromHistory + 1).coerceAtMost(totalPeriode) else 1

            val paymentDates = buildList {
                if (latestItem.tanggalMulaiSewa.isNotBlank() && latestItem.nominalPembayaran > 0L) {
                    add(latestItem.tanggalMulaiSewa)
                }
                unitAllocations.forEach { if (it.paymentDate.isNotBlank()) add(it.paymentDate) }
                unitExtensions.forEach { if (it.tanggalPerpanjangan.isNotBlank()) add(it.tanggalPerpanjangan) }
            }.distinct().sorted()

            val masterUnitMonthly = pricesByUnitId[unitId]?.get(30)?.takeIf { it > 0L }
                ?: masterUnit?.cicilanPerBulan?.takeIf { it > 0L }
                ?: latestItem.hargaSewa
            activeInfos.add(
                CustomerActiveUnitRenewalInfo(
                    unitId = unitId,
                    unitItemId = latestItem.id,
                    sourceTransactionId = sourceTx.id,
                    sourceInvoiceNumber = sourceTx.nomorInvoice,
                    kodeUnit = masterUnit?.kodeUnit ?: latestItem.kodeUnitSnapshot,
                    namaUnit = masterUnit?.namaUnit ?: latestItem.namaUnitSnapshot,
                    merek = masterUnit?.merek ?: latestItem.merekSnapshot,
                    kapasitasLiter = masterUnit?.kapasitasLiter ?: latestItem.kapasitasLiterSnapshot,
                    nomorSeri = masterUnit?.nomorSeri.orEmpty(),
                    jenisProgram = latestItem.jenisProgram,
                    tanggalMulaiSewa = latestItem.tanggalMulaiSewa.ifBlank { sourceTx.tanggalMulaiSewa },
                    tanggalAkhirSewa = latestItem.tanggalAkhirSewa.ifBlank { sourceTx.tanggalAkhirSewa },
                    tanggalJatuhTempo = latestItem.tanggalJatuhTempo.ifBlank { latestItem.tanggalAkhirSewa.ifBlank { sourceTx.tanggalJatuhTempo } },
                    statusPembayaran = latestItem.statusPembayaran.ifBlank { sourceTx.statusPembayaran },
                    hargaSewaMasterBulanan = masterUnitMonthly,
                    hargaCicilanHakMilikMaster = masterUnit?.cicilanPerBulan?.takeIf { it > 0L } ?: masterUnitMonthly,
                    masterPricesByPeriodDays = pricesByUnitId[unitId].orEmpty(),
                    totalPeriodeHakMilik = totalPeriode,
                    sudahDibayarHakMilik = paidCountFromHistory,
                    sisaPeriodeHakMilik = remainingHm,
                    pembayaranBerikutnyaHakMilik = nextHm,
                    historiTanggalPembayaran = paymentDates,
                    historiPerpanjanganUnit = unitExtensions
                )
            )
            coveredUnitIds.add(unitId)
        }

        // Fallback for legacy single-unit transactions without TransactionUnitItemEntity rows
        activeTxs.forEach { tx ->
            if (tx.unitId > 0L && tx.unitId !in coveredUnitIds) {
                val masterUnit = unitById[tx.unitId]
                val isHakMilik = tx.jenisProgram.equals("Sewa Hak Milik", ignoreCase = true)
                val unitExtensions = custExtensions.filter {
                    it.unitId == tx.unitId || it.originalTransactionId == tx.id || it.newTransactionId == tx.id
                }
                val totalPeriode = if (isHakMilik) tx.totalPeriodeHakMilik.takeIf { it > 0 } ?: masterUnit?.jumlahCicilanTotal ?: 12 else 0
                val paidHm = if (isHakMilik) {
                    maxOf(tx.pembayaranKeHakMilik, unitExtensions.maxOfOrNull { it.pembayaranKeHakMilik } ?: 0, 1)
                        .coerceAtMost(totalPeriode)
                } else 0
                val legacyMonthly = pricesByUnitId[tx.unitId]?.get(30)?.takeIf { it > 0L }
                    ?: masterUnit?.cicilanPerBulan?.takeIf { it > 0L }
                    ?: tx.hargaSewa
                activeInfos.add(
                    CustomerActiveUnitRenewalInfo(
                        unitId = tx.unitId,
                        unitItemId = 0L,
                        sourceTransactionId = tx.id,
                        sourceInvoiceNumber = tx.nomorInvoice,
                        kodeUnit = masterUnit?.kodeUnit ?: tx.kodeUnitSnapshot,
                        namaUnit = masterUnit?.namaUnit ?: tx.namaUnitSnapshot,
                        merek = masterUnit?.merek ?: "",
                        kapasitasLiter = masterUnit?.kapasitasLiter ?: 0,
                        nomorSeri = masterUnit?.nomorSeri.orEmpty(),
                        jenisProgram = tx.jenisProgram,
                        tanggalMulaiSewa = tx.tanggalMulaiSewa,
                        tanggalAkhirSewa = tx.tanggalAkhirSewa,
                        tanggalJatuhTempo = tx.tanggalJatuhTempo.ifBlank { tx.tanggalAkhirSewa },
                        statusPembayaran = tx.statusPembayaran,
                        hargaSewaMasterBulanan = legacyMonthly,
                        hargaCicilanHakMilikMaster = masterUnit?.cicilanPerBulan?.takeIf { it > 0L } ?: legacyMonthly,
                        masterPricesByPeriodDays = pricesByUnitId[tx.unitId].orEmpty(),
                        totalPeriodeHakMilik = totalPeriode,
                        sudahDibayarHakMilik = paidHm,
                        sisaPeriodeHakMilik = if (isHakMilik) (totalPeriode - paidHm).coerceAtLeast(0) else 0,
                        pembayaranBerikutnyaHakMilik = if (isHakMilik) (paidHm + 1).coerceAtMost(totalPeriode) else 1,
                        historiTanggalPembayaran = buildList {
                            if (tx.tanggalMulaiSewa.isNotBlank()) add(tx.tanggalMulaiSewa)
                            unitExtensions.forEach { if (it.tanggalPerpanjangan.isNotBlank()) add(it.tanggalPerpanjangan) }
                        }.distinct().sorted(),
                        historiPerpanjanganUnit = unitExtensions
                    )
                )
                coveredUnitIds.add(tx.unitId)
            }
        }

        return CustomerRenewalProfile(
            customer = customer,
            activeUnits = activeInfos.sortedBy { it.kodeUnit },
            rentalHistory = custTxs,
            extensionHistory = custExtensions
        )
    }
}
