package com.gymwp.free.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gymwp.free.MainActivity
import com.gymwp.free.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

data class ReminderSettingsState(
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0,
    val todayScheduledSummary: String = ""
)

object WorkoutReminderManager {

    private const val PREFS_NAME = "cheese_wp_workout_reminders"
    private const val KEY_ENABLED = "reminders_enabled"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"
    private const val KEY_LAST_NOTIFIED_SIG = "last_notified_signature"

    const val ACTION_DAILY_WORKOUT_REMINDER = "com.gymwp.free.ACTION_DAILY_WORKOUT_REMINDER"
    private const val CHANNEL_ID = "cheese_wp_daily_workout_channel"
    private const val NOTIF_ID_DAILY_WORKOUT = 4201
    private const val ALARM_REQUEST_CODE = 4201

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun getReminderHour(context: Context): Int =
        prefs(context).getInt(KEY_HOUR, 9).coerceIn(0, 23)

    fun getReminderMinute(context: Context): Int =
        prefs(context).getInt(KEY_MINUTE, 0).coerceIn(0, 59)

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        scheduleNextAlarm(context)
    }

    fun setReminderTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .remove(KEY_LAST_NOTIFIED_SIG)
            .apply()
        scheduleNextAlarm(context)
    }

    fun formatTime(hour: Int, minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorios de Entrenamiento",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avulsa en la barra de notificaciones cuando tienes un día de rutina programado para hoy."
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Programa la siguiente alarma diaria en AlarmManager para que funcione incluso con la app cerrada.
     */
    fun scheduleNextAlarm(context: Context) {
        val appCtx = context.applicationContext
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(appCtx, WorkoutReminderReceiver::class.java).apply {
            action = ACTION_DAILY_WORKOUT_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appCtx,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!isReminderEnabled(appCtx)) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val hour = getReminderHour(appCtx)
        val minute = getReminderMinute(appCtx)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        target.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        target.timeInMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                target.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun getTodayIsoWeekday(): Int {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    private fun getTodayDateKey(): String {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    private fun isSameDay(timestampMillis: Long): Boolean {
        val nowCal = Calendar.getInstance()
        val logCal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return nowCal.get(Calendar.YEAR) == logCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == logCal.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Evalúa si hoy corresponde entrenar según los días asignados (Lun-Dom) de la rutina del usuario
     * y lanza la notificación en la barra de estado de Android.
     */
    suspend fun checkAndNotifyTodayWorkout(
        context: Context,
        forceNotify: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!forceNotify && !isReminderEnabled(appCtx)) return@withContext false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return@withContext false
        }

        val dao = GymWpDatabase.getInstance(appCtx).dao()
        val users = dao.getAllUsersSnapshot()
        if (users.isEmpty()) return@withContext false

        val savedUid = GoogleDriveBackupManager.getSavedActiveUserId(appCtx)
        val activeUser = users.firstOrNull { it.id == savedUid } ?: users.first()

        val userPlans = dao.getAllWorkoutPlans().filter { it.userId == activeUser.id }
        if (userPlans.isEmpty()) return@withContext false

        val allDays = dao.getAllWorkoutDaysSnapshot()
        val prioritizedPlans = userPlans.sortedByDescending { it.isActive }

        var matchedPlan: WorkoutPlanEntity? = null
        var matchedDays: List<WorkoutDayEntity> = emptyList()

        for (plan in prioritizedPlans) {
            val daysForPlan = allDays
                .filter { it.workoutPlanId == plan.id }
                .sortedBy { it.orderIndex }
            val scheduledToday = daysForPlan.filter { d ->
                val assigned = WorkoutDayScheduleCodec.getAssignedDays(d.name)
                WorkoutDayScheduleCodec.isToday(assigned)
            }
            if (scheduledToday.isNotEmpty()) {
                matchedPlan = plan
                matchedDays = scheduledToday
                break
            }
        }

        val targetPlan = matchedPlan ?: return@withContext false
        if (matchedDays.isEmpty()) return@withContext false

        // Si no es prueba forzada, verificar si ya entrenó hoy ese día o si ya fue notificado hoy
        if (!forceNotify) {
            val finishedToday = dao.getAllFinishedWorkoutsSnapshot().filter {
                it.userId == activeUser.id && isSameDay(it.timestampMillis)
            }
            val allCompletedToday = matchedDays.all { d ->
                val clean = WorkoutDayScheduleCodec.getCleanName(d.name)
                finishedToday.any { fw ->
                    WorkoutDayScheduleCodec.getCleanName(fw.dayName).equals(clean, ignoreCase = true)
                }
            }
            if (allCompletedToday) return@withContext false

            val sig = "${getTodayDateKey()}_u${activeUser.id}_d${matchedDays.joinToString("-") { "${it.id}:${it.name}" }}"
            val lastSig = prefs(appCtx).getString(KEY_LAST_NOTIFIED_SIG, "")
            if (sig == lastSig) return@withContext false

            prefs(appCtx).edit().putString(KEY_LAST_NOTIFIED_SIG, sig).apply()
        }

        val todayIso = getTodayIsoWeekday()
        val weekdayFull = WorkoutDayScheduleCodec.DAY_FULL_NAMES.getOrElse(todayIso - 1) { "Hoy" }
        val cleanNames = matchedDays.map { WorkoutDayScheduleCodec.getCleanName(it.name) }
        val dayNamesJoined = cleanNames.joinToString(" / ")

        ensureChannel(appCtx)

        val openIntent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appCtx,
            NOTIF_ID_DAILY_WORKOUT,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Hoy toca entrenar: $dayNamesJoined"
        val shortText = "$weekdayFull • Rutina ${targetPlan.name}"
        val bigText = "Es $weekdayFull y tienes programado \"$dayNamesJoined\" en tu rutina \"${targetPlan.name}\". Toca para abrir Cheese WP e iniciar tu sesión."

        val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_DAILY_WORKOUT, notification)
        true
    }

    suspend fun getTodayScheduledSummary(context: Context): String = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val dao = GymWpDatabase.getInstance(appCtx).dao()
        val savedUid = GoogleDriveBackupManager.getSavedActiveUserId(appCtx)
        val userPlans = dao.getAllWorkoutPlans().filter { it.userId == savedUid }
        val allDays = dao.getAllWorkoutDaysSnapshot()
        val todayIso = getTodayIsoWeekday()
        val weekdayFull = WorkoutDayScheduleCodec.DAY_FULL_NAMES.getOrElse(todayIso - 1) { "Hoy" }

        for (plan in userPlans.sortedByDescending { it.isActive }) {
            val scheduledToday = allDays
                .filter { it.workoutPlanId == plan.id }
                .sortedBy { it.orderIndex }
                .filter { WorkoutDayScheduleCodec.isToday(WorkoutDayScheduleCodec.getAssignedDays(it.name)) }
            if (scheduledToday.isNotEmpty()) {
                val names = scheduledToday.joinToString(", ") { WorkoutDayScheduleCodec.getCleanName(it.name) }
                return@withContext "Programado para hoy ($weekdayFull): $names (${plan.name})"
            }
        }
        "Sin días asignados para hoy ($weekdayFull)"
    }
}

class WorkoutReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkoutReminderManager.scheduleNextAlarm(context)
                val now = Calendar.getInstance()
                val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val targetMinutes = WorkoutReminderManager.getReminderHour(context) * 60 +
                    WorkoutReminderManager.getReminderMinute(context)

                val isAlarmTrigger = intent?.action == WorkoutReminderManager.ACTION_DAILY_WORKOUT_REMINDER
                if (isAlarmTrigger || currentMinutes >= targetMinutes) {
                    WorkoutReminderManager.checkAndNotifyTodayWorkout(context, forceNotify = false)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
