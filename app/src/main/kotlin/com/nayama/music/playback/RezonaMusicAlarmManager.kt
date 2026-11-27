package com.nayama.music.playback

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.nayama.music.MainActivity
import com.nayama.music.NayamaApplication
import com.nayama.music.R
import java.util.Calendar
import java.util.Locale

/**
 * Rezona Music Alarm Manager & BroadcastReceiver:
 * - Schedules an in-app Rezona wake-up alarm via Android [AlarmManager] that automatically
 *   launches Rezona Smart Playlist playback at the chosen time.
 * - Simultaneously supports setting the alarm in the device's built-in Clock / Alarm app
 *    across all Android OEMs (Samsung, Xiaomi, Realme, Oppo, Vivo, Pixel) via [AlarmClock.ACTION_SET_ALARM].
 */
object RezonaMusicAlarmManager {

    const val ACTION_TRIGGER_MUSIC_ALARM = "com.nayama.music.action.TRIGGER_MUSIC_ALARM"
    private const val ALARM_REQUEST_CODE = 4409

    fun formatAlarmTime(hour: Int, minute: Int): String =
        String.format(Locale.ENGLISH, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    fun scheduleInAppMusicAlarm(context: Context, hour: Int, minute: Int) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val triggerCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val receiverIntent = Intent(appCtx, RezonaAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_MUSIC_ALARM
        }
        val pi = PendingIntent.getBroadcast(
            appCtx,
            ALARM_REQUEST_CODE,
            receiverIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val showAppIntent = PendingIntent.getActivity(
            appCtx,
            ALARM_REQUEST_CODE + 1,
            Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ACTION_TRIGGER_MUSIC_ALARM, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        runCatching {
            val info = AlarmManager.AlarmClockInfo(triggerCal.timeInMillis, showAppIntent)
            am.setAlarmClock(info, pi)
        }.onFailure {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pi)
                }
            }
        }
    }

    fun cancelInAppMusicAlarm(context: Context) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val receiverIntent = Intent(appCtx, RezonaAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_MUSIC_ALARM
        }
        val pi = PendingIntent.getBroadcast(
            appCtx,
            ALARM_REQUEST_CODE,
            receiverIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        runCatching { am.cancel(pi) }
    }

    /**
     * Exports/creates the alarm directly in the device's system Clock / Alarm app (works on all Android phones)
     * AND schedules Rezona's own Music Alarm playback at that time.
     */
    fun openSystemAlarmApp(context: Context, hour: Int, minute: Int, label: String = "Rezona Music Alarm") {
        scheduleInAppMusicAlarm(context, hour, minute)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (!launched) {
            Toast.makeText(
                context,
                "Rezona Music Alarm scheduled for ${formatAlarmTime(hour, minute)}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

class RezonaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        runCatching {
            val controller = RezonaPlayerController.getInstance(appCtx)
            controller.playSmartPlaylist(customToast = "Rezona Music Alarm · Playing Smart Mix")
        }
        runCatching {
            val openAppIntent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(RezonaMusicAlarmManager.ACTION_TRIGGER_MUSIC_ALARM, true)
            }
            val pi = PendingIntent.getActivity(
                appCtx,
                4410,
                openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notif = NotificationCompat.Builder(appCtx, NayamaApplication.ANNOUNCEMENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Rezona Music Alarm")
                .setContentText("Waking up with your Smart Playlist (Liked & Most Played)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(4410, notif)
        }
    }
}
