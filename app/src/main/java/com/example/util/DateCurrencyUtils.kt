package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object DateCurrencyUtils {
    private val rupiahFormat: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID"))

    private val isoDateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun formatRupiah(amount: Long): String {
        return rupiahFormat.format(amount)
    }

    fun todayIso(): String = isoDateFormat.format(Date())

    fun nowIsoDateTime(): String = isoDateTimeFormat.format(Date())

    fun nowTime(): String = timeFormat.format(Date())

    fun nowTimeDisplay(): String = nowTime()

    fun nowDateTimeDisplay(): String = dateTimeFormat.format(Date())

    fun formatDisplayDate(isoDate: String): String {
        return try {
            val d = isoDateFormat.parse(isoDate)
            if (d != null) displayDateFormat.format(d) else isoDate
        } catch (e: Exception) {
            isoDate
        }
    }

    fun addMonthsToIsoDate(startIso: String, months: Int): String {
        return try {
            val d = isoDateFormat.parse(startIso) ?: Date()
            val cal = Calendar.getInstance()
            cal.time = d
            cal.add(Calendar.MONTH, months)
            isoDateFormat.format(cal.time)
        } catch (e: Exception) {
            startIso
        }
    }

    fun addDaysToIsoDate(startIso: String, days: Int): String {
        return try {
            val d = isoDateFormat.parse(startIso) ?: Date()
            val cal = Calendar.getInstance()
            cal.time = d
            cal.add(Calendar.DAY_OF_YEAR, days)
            isoDateFormat.format(cal.time)
        } catch (e: Exception) {
            startIso
        }
    }

    /**
     * Returns difference in days from today to targetIsoDate.
     * 0 = due today
     * 1 = due tomorrow
     * < 0 = overdue
     */
    fun daysFromToday(targetIsoDate: String): Long {
        return daysBetweenIsoDates(todayIso(), targetIsoDate)
    }

    fun daysBetweenIsoDates(fromIsoDate: String, toIsoDate: String): Long {
        return try {
            val fromDate = isoDateFormat.parse(fromIsoDate) ?: return 999L
            val toDate = isoDateFormat.parse(toIsoDate) ?: return 999L
            val diffMillis = toDate.time - fromDate.time
            TimeUnit.MILLISECONDS.toDays(diffMillis)
        } catch (e: Exception) {
            999L
        }
    }

    fun parseFlexibleDateToIso(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val patterns = listOf(
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "d/M/yyyy",
            "d-M-yyyy",
            "yyyy/MM/dd",
            "dd MMM yyyy"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale("id", "ID"))
                sdf.isLenient = false
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return isoDateFormat.format(parsed)
            } catch (_: Exception) {
            }
            try {
                val sdfUs = SimpleDateFormat(p, Locale.US)
                sdfUs.isLenient = false
                val parsed = sdfUs.parse(trimmed)
                if (parsed != null) return isoDateFormat.format(parsed)
            } catch (_: Exception) {
            }
        }
        // Check Excel serial date number (e.g., 46000)
        val serial = trimmed.toDoubleOrNull()?.toInt()
        if (serial != null && serial in 35000..60000) {
            val cal = Calendar.getInstance()
            cal.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
            cal.add(Calendar.DAY_OF_YEAR, serial)
            return isoDateFormat.format(cal.time)
        }
        return null
    }

    fun parseNominal(raw: String): Long? {
        val cleaned = raw.replace("Rp", "", ignoreCase = true)
            .replace(".", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()
        if (cleaned.isEmpty() || cleaned == "-") return 0L
        return cleaned.toLongOrNull()
    }

    fun isValidPhone(phone: String): Boolean {
        val cleaned = phone.replace(" ", "").replace("-", "")
        if (cleaned.isEmpty()) return false
        return cleaned.matches(Regex("^(\\+62|62|08)[0-9]{7,13}$"))
    }

    fun maskNik(nik: String, maskMiddle: Boolean = true): String {
        if (!maskMiddle || nik.length < 10) return nik
        return nik.take(6) + "******" + nik.takeLast(4)
    }

    fun calculateRoiMonths(hargaBeli: Long, rataRataSewaBulanan: Long): Double {
        if (hargaBeli <= 0L || rataRataSewaBulanan <= 0L) return 0.0
        return (hargaBeli.toDouble() / rataRataSewaBulanan.toDouble())
    }
}
