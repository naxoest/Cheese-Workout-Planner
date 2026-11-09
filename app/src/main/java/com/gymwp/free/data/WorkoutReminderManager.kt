package com.gymwp.free.data

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RawRes
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

data class NotificationSoundOption(
    val id: String,
    val label: String,
    val category: String,
    @RawRes val rawResId: Int?
)

data class ReminderSettingsState(
    val enabled: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0,
    val selectedSoundId: String = WorkoutReminderManager.DEFAULT_SOUND_ID,
    val todayScheduledSummary: String = ""
)

object WorkoutReminderManager {

    private const val PREFS_NAME = "cheese_wp_workout_reminders"
    private const val KEY_ENABLED = "reminders_enabled"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"
    private const val KEY_SOUND_ID = "notification_sound_id"
    private const val KEY_LAST_NOTIFIED_SIG = "last_notified_signature"

    const val ACTION_DAILY_WORKOUT_REMINDER = "com.gymwp.free.ACTION_DAILY_WORKOUT_REMINDER"
    const val ACTION_REST_TIMER_FINISHED = "com.gymwp.free.ACTION_REST_TIMER_FINISHED"
    private const val EXTRA_REST_DAY_NAME = "extra_rest_day_name"

    private const val CHANNEL_ID_DAILY_WORKOUT = "cheese_wp_daily_workout_v3"
    private const val CHANNEL_ID_REST_TIMER = "cheese_wp_rest_timer_v3"
    private const val NOTIF_ID_DAILY_WORKOUT = 4201
    private const val NOTIF_ID_REST_TIMER = 9002
    private const val ALARM_REQUEST_CODE_DAILY = 4201
    private const val ALARM_REQUEST_CODE_REST = 4202

    const val DEFAULT_SOUND_ID = "sound_04_notification"

    val AVAILABLE_SOUNDS: List<NotificationSoundOption> = listOf(
        NotificationSoundOption("sound_04_notification", "Notificación Clásica", "Clásico", R.raw.sound_04_notification),
        NotificationSoundOption("sound_11_boxing_bell", "Campana de Gimnasio", "Deportivo", R.raw.sound_11_boxing_bell),
        NotificationSoundOption("sound_12_double_chime", "Doble Cristal (Chime)", "Suave", R.raw.sound_12_double_chime),
        NotificationSoundOption("sound_13_power_up", "Impulso de Energía", "Deportivo", R.raw.sound_13_power_up),
        NotificationSoundOption("sound_14_radar_pulse", "Pulso Deportivo", "Intenso", R.raw.sound_14_radar_pulse),
        NotificationSoundOption("sound_15_victory_fanfare", "Acorde de Victoria", "Musical", R.raw.sound_15_victory_fanfare),
        NotificationSoundOption("sound_03_ding_ding_ding", "Triple Campana (Ding-Ding)", "Intenso", R.raw.sound_03_ding_ding_ding),
        NotificationSoundOption("sound_01_alert_tone", "Tono de Alerta", "Intenso", R.raw.sound_01_alert_tone),
        NotificationSoundOption("sound_02_cool_tone", "Tono Fresco", "Suave", R.raw.sound_02_cool_tone),
        NotificationSoundOption("sound_00_short_beep_1", "Pitido Corto Deportivo", "Deportivo", R.raw.sound_00_short_beep_1),
        NotificationSoundOption("sound_05_machine_beep", "Beep de Máquina", "Deportivo", R.raw.sound_05_machine_beep),
        NotificationSoundOption("sound_06_musical", "Melodía Musical", "Musical", R.raw.sound_06_musical),
        NotificationSoundOption("sound_07_guitar", "Acorde de Guitarra", "Musical", R.raw.sound_07_guitar),
        NotificationSoundOption("sound_08_marimba", "Marimba", "Musical", R.raw.sound_08_marimba),
        NotificationSoundOption("sound_09_whistle", "Silbato de Entrenador", "Deportivo", R.raw.sound_09_whistle),
        NotificationSoundOption("sound_10_cat", "Maullido Curioso", "Especial", R.raw.sound_10_cat),
        NotificationSoundOption("system_default", "Predeterminado del teléfono", "Sistema", null)
    )

    // Strong references so Android Runtime GC never collects MediaPlayer mid-playback on Motorola / AOSP
    @Volatile
    private var activeMediaPlayer: MediaPlayer? = null

    @Volatile
    private var lastRestAlertWallMs: Long = 0L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun getReminderHour(context: Context): Int =
        prefs(context).getInt(KEY_HOUR, 9).coerceIn(0, 23)

    fun getReminderMinute(context: Context): Int =
        prefs(context).getInt(KEY_MINUTE, 0).coerceIn(0, 59)

    fun getSelectedSoundId(context: Context): String {
        val saved = prefs(context).getString(KEY_SOUND_ID, DEFAULT_SOUND_ID) ?: DEFAULT_SOUND_ID
        return if (AVAILABLE_SOUNDS.any { it.id == saved }) saved else DEFAULT_SOUND_ID
    }

    fun getSelectedSoundOption(context: Context): NotificationSoundOption {
        val id = getSelectedSoundId(context)
        return AVAILABLE_SOUNDS.firstOrNull { it.id == id } ?: AVAILABLE_SOUNDS.first()
    }

    fun setSelectedSoundId(context: Context, soundId: String) {
        val validId = if (AVAILABLE_SOUNDS.any { it.id == soundId }) soundId else DEFAULT_SOUND_ID
        prefs(context).edit().putString(KEY_SOUND_ID, validId).apply()
        ensureChannels(context)
    }

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

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Delete legacy channels so settings are clean
            runCatching {
                nm.deleteNotificationChannel("cheese_wp_daily_workout_channel")
                nm.deleteNotificationChannel("cheese_wp_rest_timer")
            }

            // Channels use IMPORTANCE_HIGH for heads-up banner & status bar icon, with sound=null
            // because sound is played directly by playSelectedNotificationSound() via MediaPlayer
            // so it works reliably on Motorola Edge, Samsung OneUI, Xiaomi, Pixel, etc. without double audio.
            val dailyChannel = NotificationChannel(
                CHANNEL_ID_DAILY_WORKOUT,
                "Recordatorios de Entrenamiento",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa en la barra de notificaciones cuando tienes un día de rutina programado para hoy."
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 280L, 140L, 380L)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }

            val restChannel = NotificationChannel(
                CHANNEL_ID_REST_TIMER,
                "Cronómetro de Descanso (Cheese WP)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa cuando finaliza el tiempo de descanso entre series."
                enableVibration(true)
                vibrationPattern = longArrayOf(0L, 280L, 140L, 380L)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }

            nm.createNotificationChannel(dailyChannel)
            nm.createNotificationChannel(restChannel)
        }
    }

    /**
     * Reproduce el sonido configurado por el usuario de forma compatible con todos los modelos Android
     * (Samsung, Motorola Edge, Xiaomi, Pixel, etc.), manteniendo un WakeLock temporal y referencia fuerte
     * al MediaPlayer para evitar cortes por el recolector de basura o suspensión de CPU.
     */
    fun playSelectedNotificationSound(
        context: Context,
        soundIdOverride: String? = null,
        forceAlarmStream: Boolean = false,
        vibrate: Boolean = true
    ) {
        val appCtx = context.applicationContext
        if (vibrate) {
            triggerVibration(appCtx)
        }

        val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (!forceAlarmStream && audioManager != null && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CheeseWP::SoundPlaybackWakeLock")?.apply {
            setReferenceCounted(false)
        }
        runCatching { wakeLock?.acquire(4500L) }

        val targetId = soundIdOverride ?: getSelectedSoundId(appCtx)
        val option = AVAILABLE_SOUNDS.firstOrNull { it.id == targetId } ?: AVAILABLE_SOUNDS.first()

        // Use USAGE_ALARM if requested or if STREAM_NOTIFICATION volume is 0 on devices like Motorola Edge
        val notifVol = audioManager?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) ?: 1
        val useAlarmUsage = forceAlarmStream || notifVol <= 0

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(if (useAlarmUsage) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        try {
            stopActiveSoundPlayer()

            val rawResId = option.rawResId
            if (rawResId != null) {
                val afd = appCtx.resources.openRawResourceFd(rawResId)
                if (afd != null) {
                    val mp = MediaPlayer()
                    activeMediaPlayer = mp
                    mp.setAudioAttributes(audioAttrs)
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    mp.isLooping = false
                    mp.setOnCompletionListener { player ->
                        runCatching { player.release() }
                        if (activeMediaPlayer === player) activeMediaPlayer = null
                        runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                    }
                    mp.setOnErrorListener { player, _, _ ->
                        runCatching { player.release() }
                        if (activeMediaPlayer === player) activeMediaPlayer = null
                        runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                        true
                    }
                    mp.prepare()
                    mp.start()
                    return
                }
            }

            // Fallback or "system_default"
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (defaultUri != null) {
                val mp = MediaPlayer()
                activeMediaPlayer = mp
                mp.setAudioAttributes(audioAttrs)
                mp.setDataSource(appCtx, defaultUri)
                mp.isLooping = false
                mp.setOnCompletionListener { player ->
                    runCatching { player.release() }
                    if (activeMediaPlayer === player) activeMediaPlayer = null
                    runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
                }
                mp.prepare()
                mp.start()
            } else {
                runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
            }
        } catch (_: Exception) {
            runCatching {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(appCtx, fallbackUri)?.play()
            }
            runCatching { if (wakeLock?.isHeld == true) wakeLock.release() }
        }
    }

    fun stopActiveSoundPlayer() {
        val current = activeMediaPlayer
        activeMediaPlayer = null
        if (current != null) {
            runCatching {
                if (current.isPlaying) current.stop()
            }
            runCatching { current.release() }
        }
    }

    fun triggerVibration(context: Context) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibAttrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0L, 280L, 140L, 380L), -1),
                        vibAttrs
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500L)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Programa una alarma exacta para el fin del descanso entre series, asegurando que suene y notifique
     * aunque el teléfono tenga la pantalla bloqueada o el sistema pause la app en segundo plano.
     */
    fun scheduleRestTimerAlarm(context: Context, triggerAtElapsedMs: Long, dayName: String) {
        val appCtx = context.applicationContext
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val cleanDay = WorkoutDayScheduleCodec.getCleanName(dayName).ifBlank { "tu entrenamiento" }

        val intent = Intent(appCtx, WorkoutReminderReceiver::class.java).apply {
            action = ACTION_REST_TIMER_FINISHED
            putExtra(EXTRA_REST_DAY_NAME, cleanDay)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appCtx,
            ALARM_REQUEST_CODE_REST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtElapsedMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAtElapsedMs,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsedMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtElapsedMs,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedMs,
                pendingIntent
            )
        }
    }

    fun cancelRestTimerAlarm(context: Context) {
        val appCtx = context.applicationContext
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appCtx, WorkoutReminderReceiver::class.java).apply {
            action = ACTION_REST_TIMER_FINISHED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appCtx,
            ALARM_REQUEST_CODE_REST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { alarmManager.cancel(pendingIntent) }
    }

    /**
     * Se invoca cuando termina el cronómetro de descanso (ya sea desde la UI en vivo o desde AlarmManager).
     * Incluye debounce de 2.5s para evitar duplicados si ambos disparan casi al mismo tiempo.
     */
    @Synchronized
    fun onRestTimerFinished(context: Context, dayName: String, showStatusNotification: Boolean = true) {
        val nowWall = System.currentTimeMillis()
        if (nowWall - lastRestAlertWallMs < 2500L) return
        lastRestAlertWallMs = nowWall

        val appCtx = context.applicationContext
        cancelRestTimerAlarm(appCtx)
        playSelectedNotificationSound(appCtx, forceAlarmStream = true, vibrate = true)

        if (showStatusNotification) {
            postRestFinishedNotification(appCtx, WorkoutDayScheduleCodec.getCleanName(dayName))
        }
    }

    fun postRestFinishedNotification(context: Context, dayName: String) {
        try {
            val appCtx = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    appCtx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            ensureChannels(appCtx)
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cleanDay = WorkoutDayScheduleCodec.getCleanName(dayName).ifBlank { "tu rutina" }
            val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID_REST_TIMER)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("¡Descanso terminado!")
                .setContentText("Es hora de tu siguiente serie en \"$cleanDay\"")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            nm.notify(NOTIF_ID_REST_TIMER, notification)
        } catch (_: Exception) {
        }
    }

    fun cancelRestNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID_REST_TIMER)
        } catch (_: Exception) {
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
            ALARM_REQUEST_CODE_DAILY,
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
     * y lanza la notificación en la barra de estado de Android junto con el sonido configurado.
     * Si forceNotify = true (botón "Probar notificación ahora"), envía siempre la notificación y el sonido
     * aunque el usuario aún no haya asignado un día específico para hoy.
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
            if (!granted) {
                if (forceNotify) {
                    // Aún reproducir sonido si está probando, pero indicar que falta permiso de notificación
                    playSelectedNotificationSound(appCtx, forceAlarmStream = true, vibrate = true)
                }
                return@withContext false
            }
        }

        val dao = GymWpDatabase.getInstance(appCtx).dao()
        val users = dao.getAllUsersSnapshot()
        val savedUid = GoogleDriveBackupManager.getSavedActiveUserId(appCtx)
        val activeUser = users.firstOrNull { it.id == savedUid } ?: users.firstOrNull()

        val userPlans = if (activeUser != null) {
            dao.getAllWorkoutPlans().filter { it.userId == activeUser.id }
        } else {
            emptyList()
        }

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

        if (!forceNotify) {
            val targetPlan = matchedPlan ?: return@withContext false
            if (matchedDays.isEmpty() || activeUser == null) return@withContext false

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
        } else {
            // Modo prueba: si no hay día asignado hoy, usar el primer día de su plan activo o texto de demostración
            if (matchedPlan == null) {
                matchedPlan = prioritizedPlans.firstOrNull()
                if (matchedPlan != null) {
                    matchedDays = allDays
                        .filter { it.workoutPlanId == matchedPlan.id }
                        .sortedBy { it.orderIndex }
                        .take(1)
                }
            }
        }

        val todayIso = getTodayIsoWeekday()
        val weekdayFull = WorkoutDayScheduleCodec.DAY_FULL_NAMES.getOrElse(todayIso - 1) { "Hoy" }
        val planTitle = matchedPlan?.name ?: "Mi Rutina"
        val dayNamesJoined = if (matchedDays.isNotEmpty()) {
            matchedDays.joinToString(" / ") { WorkoutDayScheduleCodec.getCleanName(it.name) }
        } else {
            "Sesión de Entrenamiento"
        }

        ensureChannels(appCtx)

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
        val shortText = "$weekdayFull • Rutina $planTitle"
        val bigText = "Es $weekdayFull y tienes programado \"$dayNamesJoined\" en tu rutina \"$planTitle\". Toca para abrir Cheese WP e iniciar tu sesión."

        val notification = NotificationCompat.Builder(appCtx, CHANNEL_ID_DAILY_WORKOUT)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_DAILY_WORKOUT, notification)
        playSelectedNotificationSound(appCtx, forceAlarmStream = forceNotify, vibrate = true)
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
        val action = intent?.action
        if (action == WorkoutReminderManager.ACTION_REST_TIMER_FINISHED) {
            val dayName = intent.getStringExtra("extra_rest_day_name") ?: "tu entrenamiento"
            WorkoutReminderManager.onRestTimerFinished(context, dayName, showStatusNotification = true)
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkoutReminderManager.scheduleNextAlarm(context)
                val now = Calendar.getInstance()
                val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val targetMinutes = WorkoutReminderManager.getReminderHour(context) * 60 +
                    WorkoutReminderManager.getReminderMinute(context)

                val isAlarmTrigger = action == WorkoutReminderManager.ACTION_DAILY_WORKOUT_REMINDER
                if (isAlarmTrigger || currentMinutes >= targetMinutes) {
                    WorkoutReminderManager.checkAndNotifyTodayWorkout(context, forceNotify = false)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
