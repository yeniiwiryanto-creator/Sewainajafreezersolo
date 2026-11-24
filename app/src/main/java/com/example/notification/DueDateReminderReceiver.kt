package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.SmartNotificationSettings
import com.example.drive.SyncWorker
import com.example.util.DateCurrencyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DueDateReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.getSharedPreferences("saf_soc_prefs", Context.MODE_PRIVATE)
                val settings = loadSmartNotificationSettings(prefs)

                val db = AppDatabase.getInstance(context)
                val dao = db.freezerDao()

                // Recovery after device restart: reset stuck UPLOADING items & resume pending sync queue
                if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                    runCatching {
                        dao.resetStuckUploadingDriveSyncQueueToPending()
                        val pendingItems = dao.getRunnableDriveSyncQueueItemsOnce()
                        if (pendingItems.isNotEmpty()) {
                            SyncWorker.enqueueImmediateSync(context)
                        }
                    }
                }

                val transactions = dao.getAllTransactionsOnce()
                val unitItems = dao.getAllTransactionUnitItemsOnce()

                val activeTx = transactions.filter {
                    it.statusPenyewaan == "Dalam Penyewaan" || it.statusPenyewaan == "Booking"
                }

                val activeItems = unitItems.filter {
                    it.statusPenyewaan == "Dalam Penyewaan" || it.statusPenyewaan == "Booking"
                }

                val dueToday = if (activeItems.isNotEmpty()) {
                    activeItems.count { DateCurrencyUtils.daysFromToday(it.tanggalJatuhTempo) == 0L && it.sisaTagihanUnit > 0L }
                } else {
                    activeTx.count { DateCurrencyUtils.daysFromToday(it.tanggalJatuhTempo) == 0L }
                }

                val dueSoon = if (settings.enableDueSoonReminder) {
                    val window = settings.dueReminderDaysBefore.coerceIn(1, 7).toLong()
                    if (activeItems.isNotEmpty()) {
                        activeItems.count { DateCurrencyUtils.daysFromToday(it.tanggalJatuhTempo) in 1L..window && it.sisaTagihanUnit > 0L }
                    } else {
                        activeTx.count { DateCurrencyUtils.daysFromToday(it.tanggalJatuhTempo) in 1L..window }
                    }
                } else 0

                val overdue = if (settings.enableOverdueReminder) {
                    if (activeItems.isNotEmpty()) {
                        activeItems.count { DateCurrencyUtils.daysFromToday(it.tanggalJatuhTempo) < 0L && it.sisaTagihanUnit > 0L }
                    } else {
                        activeTx.count { DateCurrencyUtils.daysFromToday(it.tanggalJatuhTempo) < 0L && it.statusPembayaran != "Lunas" }
                    }
                } else 0

                val incompleteDocsCount = if (settings.enableIncompletePhotoReminder) {
                    activeItems.count { !it.isDocumentationComplete }
                } else 0

                val unreturnedDueCount = if (settings.enableUnreturnedUnitReminder) {
                    activeTx.count {
                        it.statusPenyewaan == "Dalam Penyewaan" &&
                            DateCurrencyUtils.daysFromToday(it.tanggalAkhirSewa) < 0L
                    }
                } else 0

                val parts = buildList {
                    if (settings.enableDueSoonReminder && (dueToday > 0 || dueSoon > 0)) {
                        add("Hari ini: $dueToday unit | Segera (${settings.dueReminderDaysBefore} hari): $dueSoon unit")
                    }
                    if (overdue > 0) add("Terlambat bayar: $overdue tagihan")
                    if (incompleteDocsCount > 0) add("Foto belum lengkap: $incompleteDocsCount unit")
                    if (unreturnedDueCount > 0) add("Jatuh tempo belum kembali: $unreturnedDueCount unit")
                }

                if (parts.isNotEmpty()) {
                    showReminderNotification(
                        context = context,
                        title = "Pengingat Pintar SEWAin Aja Freezer SOC",
                        message = parts.joinToString(" • ") + ". Ketuk untuk kelola."
                    )
                }

                DueNotificationScheduler.scheduleDailyDueCheck(context)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "saf_due_reminder_channel"

        fun loadSmartNotificationSettings(prefs: android.content.SharedPreferences): SmartNotificationSettings {
            return SmartNotificationSettings(
                enableDueSoonReminder = prefs.getBoolean("notif_due_soon", true),
                dueReminderDaysBefore = prefs.getInt("notif_due_days_before", 3).coerceIn(1, 7),
                enableOverdueReminder = prefs.getBoolean("notif_overdue", true),
                enableUnitAddedNotification = prefs.getBoolean("notif_unit_added", true),
                enableContractCompletedNotification = prefs.getBoolean("notif_contract_completed", true),
                enableIncompletePhotoReminder = prefs.getBoolean("notif_incomplete_photo", true),
                enableUnreturnedUnitReminder = prefs.getBoolean("notif_unreturned_unit", true)
            )
        }

        fun showReminderNotification(
            context: Context,
            title: String,
            message: String,
            notificationId: Int = 2026
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Pengingat Jatuh Tempo & Smart Rental",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifikasi otomatis jadwal jatuh tempo, tunggakan, foto unit, dan kontrak SEWAin Aja Freezer SOC"
                }
                manager.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(notificationId, notification)
        }
    }
}

object DueNotificationScheduler {
    fun scheduleDailyDueCheck(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DueDateReminderReceiver::class.java).apply {
            action = "com.example.ACTION_DUE_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2026,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_DAY
        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                AlarmManager.INTERVAL_HALF_DAY,
                pendingIntent
            )
        } catch (_: SecurityException) {
        }
    }
}

