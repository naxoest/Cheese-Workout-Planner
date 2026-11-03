package com.gymwp.free.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gymwp.free.data.AppUpdateManager
import com.gymwp.free.data.CatalogExercise
import com.gymwp.free.data.WorkoutExerciseEntity
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.domain.OverloadSuggestion
import com.gymwp.free.domain.ProgressiveOverloadAdvisor
import com.gymwp.free.ui.CompletedExerciseLog
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AnatomyFatigueBodyMap
import com.gymwp.free.ui.components.AnimatedExerciseGif
import com.gymwp.free.ui.components.ConfirmDeleteDialog
import com.gymwp.free.ui.components.CreateOrEditCustomExerciseDialog
import com.gymwp.free.ui.components.ExerciseDetailDialog
import com.gymwp.free.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private class LiveExerciseRuntimeState(
    val done: SnapshotStateList<Boolean>,
    val reps: SnapshotStateList<String>,
    val loads: SnapshotStateList<String>,
    var syncedExerciseId: Int,
    var syncedUnitType: Int
)

private fun formatWeightClean(value: Float): String {
    val clamped = value.coerceAtLeast(0f)
    return if (clamped % 1f == 0f) {
        clamped.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", clamped).trimEnd('0').trimEnd('.')
    }
}

private fun createRuntimeStateFromEntity(wEx: WorkoutExerciseEntity): LiveExerciseRuntimeState {
    val count = wEx.setsCount.coerceAtLeast(1)
    val parsedReps = wEx.repsPerSetCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
    val repsList = List(count) { i ->
        parsedReps.getOrElse(i) { parsedReps.lastOrNull() ?: 10 }.toString()
    }
    val parsedLoads = wEx.loadsPerSetCsv.split(",").mapNotNull { it.trim().toFloatOrNull() }
    val loadsList = List(count) { i ->
        val v = parsedLoads.getOrElse(i) { parsedLoads.lastOrNull() ?: if (wEx.unitType == 0) 20f else 0f }
        formatWeightClean(v)
    }
    return LiveExerciseRuntimeState(
        done = mutableStateListOf(*Array(count) { false }),
        reps = mutableStateListOf(*repsList.toTypedArray()),
        loads = mutableStateListOf(*loadsList.toTypedArray()),
        syncedExerciseId = wEx.exerciseId,
        syncedUnitType = wEx.unitType
    )
}

private fun triggerRestAlertFeedback(context: Context) {
    try {
        ToneGenerator(AudioManager.STREAM_ALARM, 95)
            .startTone(ToneGenerator.TONE_PROP_BEEP2, 550)
    } catch (_: Exception) {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 95)
                .startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
        } catch (_: Exception) {
        }
    }

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
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0L, 280L, 140L, 380L), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500L)
            }
        }
    } catch (_: Exception) {
    }
}

private data class SetFieldEditTarget(
    val workoutExercise: WorkoutExerciseEntity,
    val setIndex: Int,
    val isLoadField: Boolean,
    val currentValue: String,
    val unitLabel: String
)

@Composable
fun LiveWorkoutAssistantScreen(
    viewModel: GymWpViewModel,
    planId: Long?,
    planName: String,
    dayId: Long,
    fallbackDayName: String,
    onExitSession: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Live Room flows so any routine modification updates immediately mid-workout!
    val allDays by viewModel.allWorkoutDays.collectAsState()
    val allWorkoutExercises by viewModel.allWorkoutExercises.collectAsState()
    val allCatalog by viewModel.allAvailableExercises.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val allFinishedEx by viewModel.allFinishedExercises.collectAsState()

    val liveDay = remember(allDays, dayId) { allDays.firstOrNull { it.id == dayId } }
    val currentDayName = liveDay?.name ?: fallbackDayName
    val liveExercises = remember(allWorkoutExercises, dayId) {
        allWorkoutExercises.filter { it.workoutDayId == dayId }.sortedBy { it.orderIndex }
    }

    // Screen-lock-proof elapsed stopwatch state (SystemClock.elapsedRealtime() ticks during deep sleep & lock!)
    var sessionStartElapsedMs by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var accumulatedPausedMs by rememberSaveable { mutableLongStateOf(0L) }
    var pauseStartedAtMs by rememberSaveable { mutableLongStateOf(0L) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    // Screen-lock-proof Rest Countdown state
    var restEndElapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var restTotalSeconds by rememberSaveable { mutableIntStateOf(60) }
    var restRemainingSeconds by remember { mutableIntStateOf(0) }

    // UI mode: Gym WP Focused Card Mode vs Full List Mode
    var focusedMode by rememberSaveable { mutableStateOf(true) }
    var focusedExerciseIndex by rememberSaveable { mutableIntStateOf(0) }
    var feedbackBannerText by remember { mutableStateOf<String?>(null) }

    // Dialog states for mid-workout routine editing
    var confirmingExitSession by remember { mutableStateOf(false) }
    var showAddExerciseModal by remember { mutableStateOf(false) }
    var replacingExerciseTarget by remember { mutableStateOf<Pair<WorkoutExerciseEntity, String>?>(null) }
    var exercisePendingDelete by remember { mutableStateOf<Pair<WorkoutExerciseEntity, String>?>(null) }
    var restPickerTarget by remember { mutableStateOf<WorkoutExerciseEntity?>(null) }
    var editingDayNameDialog by remember { mutableStateOf(false) }
    var activeSetFieldTarget by remember { mutableStateOf<SetFieldEditTarget?>(null) }
    var inspectingExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var editingCustomCatalogExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var completedSummary by remember { mutableStateOf<Pair<Int, Map<Int, Int>>?>(null) }

    // Per-exercise runtime state map keyed by WorkoutExerciseEntity.id
    val perExerciseState = remember { mutableStateMapOf<Long, LiveExerciseRuntimeState>() }

    // Keep focused index in bounds when exercises are added/removed
    LaunchedEffect(liveExercises.size) {
        if (liveExercises.isNotEmpty() && focusedExerciseIndex !in liveExercises.indices) {
            focusedExerciseIndex = liveExercises.lastIndex.coerceAtLeast(0)
        }
    }

    // Auto-hide feedback toast pill
    LaunchedEffect(feedbackBannerText) {
        if (feedbackBannerText != null) {
            delay(2200L)
            feedbackBannerText = null
        }
    }

    // Sync runtime map whenever exercises are added, replaced, or resized
    LaunchedEffect(liveExercises) {
        liveExercises.forEach { wEx ->
            val existing = perExerciseState[wEx.id]
            if (existing == null) {
                perExerciseState[wEx.id] = createRuntimeStateFromEntity(wEx)
            } else {
                // If the exercise was replaced in-place
                if (existing.syncedExerciseId != wEx.exerciseId) {
                    val fresh = createRuntimeStateFromEntity(wEx)
                    if (existing.syncedUnitType != wEx.unitType) {
                        existing.reps.clear()
                        existing.reps.addAll(fresh.reps)
                        existing.loads.clear()
                        existing.loads.addAll(fresh.loads)
                    }
                    existing.syncedExerciseId = wEx.exerciseId
                    existing.syncedUnitType = wEx.unitType
                }
                // Sync set count if changed externally
                val targetCount = wEx.setsCount.coerceAtLeast(1)
                while (existing.done.size < targetCount) {
                    existing.done.add(false)
                    existing.reps.add(existing.reps.lastOrNull() ?: "10")
                    existing.loads.add(existing.loads.lastOrNull() ?: "20")
                }
                while (existing.done.size > targetCount && existing.done.size > 1) {
                    existing.done.removeAt(existing.done.lastIndex)
                    existing.reps.removeAt(existing.reps.lastIndex)
                    existing.loads.removeAt(existing.loads.lastIndex)
                }
            }
        }
    }

    fun computeCurrentElapsedSeconds(): Int {
        val now = SystemClock.elapsedRealtime()
        val currentPauseDelta = if (isPaused && pauseStartedAtMs > 0L) {
            (now - pauseStartedAtMs).coerceAtLeast(0L)
        } else 0L
        val activeMs = (now - sessionStartElapsedMs - accumulatedPausedMs - currentPauseDelta).coerceAtLeast(0L)
        return (activeMs / 1000L).toInt()
    }

    fun computeCurrentRestSeconds(): Int {
        if (restEndElapsedMs <= 0L) return 0
        val remMs = restEndElapsedMs - SystemClock.elapsedRealtime()
        return if (remMs <= 0L) 0 else ((remMs + 999L) / 1000L).toInt()
    }

    // Refresh timers immediately on screen unlock / resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (completedSummary == null) {
                    elapsedSeconds = computeCurrentElapsedSeconds()
                }
                restRemainingSeconds = computeCurrentRestSeconds()
                AppUpdateManager.cancelRestNotification(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AppUpdateManager.cancelRestNotification(context)
        }
    }

    // Global workout stopwatch ticker (real monotonic clock)
    LaunchedEffect(isPaused, completedSummary, sessionStartElapsedMs, accumulatedPausedMs) {
        elapsedSeconds = computeCurrentElapsedSeconds()
        while (!isPaused && completedSummary == null) {
            delay(500L)
            elapsedSeconds = computeCurrentElapsedSeconds()
        }
    }

    // Lockscreen-safe Rest Countdown Timer using bounded PARTIAL_WAKE_LOCK + Dispatchers.Default
    LaunchedEffect(restEndElapsedMs) {
        val targetEnd = restEndElapsedMs
        if (targetEnd <= 0L) {
            restRemainingSeconds = 0
            return@LaunchedEffect
        }
        val initialRemMs = targetEnd - SystemClock.elapsedRealtime()
        if (initialRemMs <= 0L) {
            restEndElapsedMs = 0L
            restRemainingSeconds = 0
            return@LaunchedEffect
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CheeseWP::RestTimerWakeLock")?.apply {
            setReferenceCounted(false)
        }

        try {
            wakeLock?.acquire((initialRemMs + 4000L).coerceAtMost(610_000L))
            withContext(Dispatchers.Default) {
                while (true) {
                    val remMs = targetEnd - SystemClock.elapsedRealtime()
                    if (remMs <= 0L) break
                    val sec = ((remMs + 999L) / 1000L).toInt()
                    withContext(Dispatchers.Main) {
                        restRemainingSeconds = sec
                    }
                    delay(250L)
                }
            }
            // Timer reached 0! Alert user even if phone screen is locked!
            restRemainingSeconds = 0
            restEndElapsedMs = 0L
            triggerRestAlertFeedback(context)
            val isScreenInteractive = pm?.isInteractive ?: true
            val isAppVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!isScreenInteractive || !isAppVisible) {
                AppUpdateManager.postRestFinishedNotification(context, currentDayName)
            }
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun startRestCountdown(seconds: Int) {
        val safeSec = seconds.coerceIn(10, 600)
        restTotalSeconds = safeSec
        restRemainingSeconds = safeSec
        restEndElapsedMs = SystemClock.elapsedRealtime() + safeSec * 1000L
        AppUpdateManager.cancelRestNotification(context)
    }

    fun adjustRestCountdown(deltaSec: Int) {
        val now = SystemClock.elapsedRealtime()
        val baseEnd = if (restEndElapsedMs > now) restEndElapsedMs else now
        val updatedEnd = baseEnd + deltaSec * 1000L
        if (updatedEnd <= now) {
            restEndElapsedMs = 0L
            restRemainingSeconds = 0
            AppUpdateManager.cancelRestNotification(context)
        } else {
            val newRem = ((updatedEnd - now + 999L) / 1000L).toInt().coerceAtMost(600)
            restRemainingSeconds = newRem
            if (newRem > restTotalSeconds) restTotalSeconds = newRem
            restEndElapsedMs = now + newRem * 1000L
        }
    }

    fun persistExerciseState(wEx: WorkoutExerciseEntity, st: LiveExerciseRuntimeState) {
        viewModel.updateWorkoutExercise(
            wEx.copy(
                setsCount = st.done.size.coerceAtLeast(1),
                repsPerSetCsv = st.reps.joinToString(",") { it.trim().ifBlank { "10" } },
                loadsPerSetCsv = st.loads.joinToString(",") { it.trim().ifBlank { "0" } }
            )
        )
    }

    // Ensure immediate runtime state entries for visible exercises
    val stateByExerciseId = liveExercises.associate { wEx ->
        val existing = perExerciseState[wEx.id]
        val state = existing ?: createRuntimeStateFromEntity(wEx).also { perExerciseState[wEx.id] = it }
        wEx.id to state
    }

    val totalSetsCount = liveExercises.sumOf { stateByExerciseId[it.id]?.done?.size ?: it.setsCount }
    val completedSetsCount = liveExercises.sumOf { wEx ->
        stateByExerciseId[wEx.id]?.done?.count { it } ?: 0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GymDarkBg)
    ) {
        // 1. Friendly Gym WP Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GymCardBg)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalIconButton(
                onClick = { confirmingExitSession = true },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = GymSurfaceElevated,
                    contentColor = GymTextPrimary
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar sesión", modifier = Modifier.size(20.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
                    .clickable { if (liveDay != null) editingDayNameDialog = true }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentDayName,
                        color = GymTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Renombrar rutina",
                        tint = GymTextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                }
                val mins = elapsedSeconds / 60
                val secs = elapsedSeconds % 60
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⏱ %02d:%02d".format(mins, secs),
                        color = if (isPaused) FatigueYellow else GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "• $completedSetsCount/$totalSetsCount series",
                        color = GymTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    if (isPaused) {
                        Text(
                            text = "PAUSA",
                            color = FatigueYellow,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = {
                        if (!isPaused) {
                            pauseStartedAtMs = SystemClock.elapsedRealtime()
                            isPaused = true
                        } else {
                            if (pauseStartedAtMs > 0L) {
                                accumulatedPausedMs += (SystemClock.elapsedRealtime() - pauseStartedAtMs).coerceAtLeast(0L)
                            }
                            pauseStartedAtMs = 0L
                            isPaused = false
                        }
                    },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = GymSurfaceElevated,
                        contentColor = GymOrange
                    ),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (isPaused) "Reanudar" else "Pausar",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Add exercise directly into the active routine mid-workout!
                Button(
                    onClick = { showAddExerciseModal = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GymOrange,
                        contentColor = GymOnPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Ejercicio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Smooth Workout Progress Bar
        LinearProgressIndicator(
            progress = { (completedSetsCount.toFloat() / totalSetsCount.coerceAtLeast(1)).coerceIn(0f, 1f) },
            color = FatigueGreen,
            trackColor = GymSurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
        )

        // 2. Horizontal Exercise Selector Strip + Focused/List Toggle
        if (liveExercises.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GymCardBg.copy(alpha = 0.65f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View Mode Toggle Chip
                AssistChip(
                    onClick = { focusedMode = !focusedMode },
                    leadingIcon = {
                        Icon(
                            imageVector = if (focusedMode) Icons.Default.ViewStream else Icons.Default.ViewCarousel,
                            contentDescription = null,
                            tint = GymOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (focusedMode) "Ver Lista" else "Modo Tarjeta",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = GymSurfaceElevated,
                        labelColor = GymTextPrimary
                    )
                )

                liveExercises.forEachIndexed { idx, wEx ->
                    val st = stateByExerciseId[wEx.id]
                    val doneCnt = st?.done?.count { it } ?: 0
                    val totCnt = st?.done?.size ?: wEx.setsCount
                    val allDone = totCnt > 0 && doneCnt == totCnt
                    val isFocused = focusedMode && idx == focusedExerciseIndex
                    val shortName = remember(allCatalog, wEx.exerciseId, wEx.customExerciseName) {
                        (viewModel.getCatalogExercise(wEx.exerciseId)?.name
                            ?: wEx.customExerciseName.ifBlank { "Ej. ${idx + 1}" })
                            .take(18)
                    }

                    val pillBg = when {
                        isFocused -> GymOrange
                        allDone -> FatigueGreen.copy(alpha = 0.20f)
                        else -> GymSurfaceElevated
                    }
                    val pillText = when {
                        isFocused -> GymOnPrimary
                        allDone -> FatigueGreen
                        else -> GymTextPrimary
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillBg)
                            .border(
                                width = if (isFocused || allDone) 1.dp else 0.dp,
                                color = when {
                                    isFocused -> GymOrange
                                    allDone -> FatigueGreen.copy(alpha = 0.6f)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                focusedExerciseIndex = idx
                                focusedMode = true
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "${idx + 1}. $shortName",
                            color = pillText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (allDone) "✓" else "$doneCnt/$totCnt",
                            color = pillText.copy(alpha = 0.9f),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Optional toast banner (e.g. "Serie 1 replicada")
        feedbackBannerText?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FatigueGreen.copy(alpha = 0.18f))
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = msg,
                    color = FatigueGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // 3. Main Workout Execution Body
        if (liveExercises.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GymCardBg),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = GymOrange,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            "Esta rutina aún no tiene ejercicios",
                            color = GymTextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Añade un ejercicio del catálogo o crea una máquina propia de tu gimnasio para comenzar.",
                            color = GymTextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { showAddExerciseModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Añadir Ejercicio / Máquina", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (focusedMode) {
            val safeIdx = focusedExerciseIndex.coerceIn(0, liveExercises.lastIndex)
            val currentWEx = liveExercises[safeIdx]
            val currentSt = stateByExerciseId[currentWEx.id]!!
            val catEx = remember(allCatalog, currentWEx.exerciseId) {
                viewModel.getCatalogExercise(currentWEx.exerciseId)
            }
            val exName = catEx?.name ?: currentWEx.customExerciseName.ifBlank { "Ejercicio #${currentWEx.exerciseId}" }
            val focusedOverload = remember(allFinishedEx, currentWEx.exerciseId, currentWEx.unitType) {
                ProgressiveOverloadAdvisor.computeSuggestion(
                    unitType = currentWEx.unitType,
                    historyForExercise = allFinishedEx.filter { it.exerciseId == currentWEx.exerciseId }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "focused_${currentWEx.id}") {
                    LiveGymWpExerciseCard(
                        exerciseIndex = safeIdx,
                        totalExercises = liveExercises.size,
                        wEx = currentWEx,
                        catEx = catEx,
                        exName = exName,
                        state = currentSt,
                        isHeroFocused = true,
                        overloadSuggestion = focusedOverload,
                        onApplyOverloadSuggestion = if (focusedOverload != null) {
                            {
                                val targetIndices = currentSt.done.indices.filter { !currentSt.done[it] }
                                    .ifEmpty { currentSt.done.indices.toList() }
                                when {
                                    focusedOverload.suggestedLoadKg != null -> {
                                        val clean = formatWeightClean(focusedOverload.suggestedLoadKg)
                                        targetIndices.forEach { i -> currentSt.loads[i] = clean }
                                    }
                                    focusedOverload.suggestedRepsOrTime != null -> {
                                        val clean = focusedOverload.suggestedRepsOrTime.coerceAtLeast(1).toString()
                                        targetIndices.forEach { i -> currentSt.reps[i] = clean }
                                    }
                                }
                                persistExerciseState(currentWEx, currentSt)
                                feedbackBannerText = "✓ Meta aplicada: ${focusedOverload.buttonLabel}"
                            }
                        } else null,
                        onInspectGuide = { if (catEx != null) inspectingExercise = catEx },
                        onReplaceExercise = { replacingExerciseTarget = currentWEx to exName },
                        onReplicateFirstSet = {
                            if (currentSt.reps.size > 1) {
                                val r0 = currentSt.reps.firstOrNull() ?: "10"
                                val l0 = currentSt.loads.firstOrNull() ?: "20"
                                for (i in 1 until currentSt.reps.size) {
                                    currentSt.reps[i] = r0
                                    currentSt.loads[i] = l0
                                }
                                persistExerciseState(currentWEx, currentSt)
                                feedbackBannerText = "✓ Serie 1 replicada en las ${currentSt.reps.size} series"
                            }
                        },
                        onEditRestSeconds = { restPickerTarget = currentWEx },
                        onMoveUp = if (safeIdx > 0) {
                            {
                                viewModel.moveWorkoutExercise(dayId, currentWEx.id, moveUp = true)
                                focusedExerciseIndex = (safeIdx - 1).coerceAtLeast(0)
                            }
                        } else null,
                        onMoveDown = if (safeIdx < liveExercises.lastIndex) {
                            {
                                viewModel.moveWorkoutExercise(dayId, currentWEx.id, moveUp = false)
                                focusedExerciseIndex = (safeIdx + 1).coerceAtMost(liveExercises.lastIndex)
                            }
                        } else null,
                        onToggleBiSet = {
                            viewModel.updateWorkoutExercise(currentWEx.copy(isBiSet = !currentWEx.isBiSet))
                        },
                        onDeleteExercise = { exercisePendingDelete = currentWEx to exName },
                        onStepReps = { setIdx, delta ->
                            val cur = currentSt.reps[setIdx].toIntOrNull() ?: 10
                            currentSt.reps[setIdx] = (cur + delta).coerceAtLeast(1).toString()
                            persistExerciseState(currentWEx, currentSt)
                        },
                        onStepLoad = { setIdx, delta ->
                            val cur = currentSt.loads[setIdx].toFloatOrNull() ?: 20f
                            currentSt.loads[setIdx] = formatWeightClean(cur + delta)
                            persistExerciseState(currentWEx, currentSt)
                        },
                        onTapExactValue = { setIdx, isLoad, unitLbl ->
                            activeSetFieldTarget = SetFieldEditTarget(
                                workoutExercise = currentWEx,
                                setIndex = setIdx,
                                isLoadField = isLoad,
                                currentValue = if (isLoad) currentSt.loads[setIdx] else currentSt.reps[setIdx],
                                unitLabel = unitLbl
                            )
                        },
                        onToggleSetDone = { setIdx, checked ->
                            currentSt.done[setIdx] = checked
                            persistExerciseState(currentWEx, currentSt)
                            if (checked) {
                                startRestCountdown(currentWEx.restSeconds.coerceAtLeast(15))
                            }
                        },
                        onAddSet = {
                            if (currentSt.done.size < 12) {
                                currentSt.done.add(false)
                                currentSt.reps.add(currentSt.reps.lastOrNull() ?: "10")
                                currentSt.loads.add(currentSt.loads.lastOrNull() ?: "20")
                                persistExerciseState(currentWEx, currentSt)
                            }
                        },
                        onRemoveLastSet = {
                            if (currentSt.done.size > 1) {
                                currentSt.done.removeAt(currentSt.done.lastIndex)
                                currentSt.reps.removeAt(currentSt.reps.lastIndex)
                                currentSt.loads.removeAt(currentSt.loads.lastIndex)
                                persistExerciseState(currentWEx, currentSt)
                            }
                        }
                    )
                }

                // Previous / Next Exercise Navigation Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (safeIdx > 0) focusedExerciseIndex = safeIdx - 1 },
                            enabled = safeIdx > 0,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Anterior", fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { if (safeIdx < liveExercises.lastIndex) focusedExerciseIndex = safeIdx + 1 },
                            enabled = safeIdx < liveExercises.lastIndex,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = GymOrange.copy(alpha = 0.18f),
                                contentColor = GymOrange
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Siguiente Ejercicio", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else {
            // Full Scrollable List View
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(liveExercises, key = { _, item -> item.id }) { exIdx, wEx ->
                    val st = stateByExerciseId[wEx.id]!!
                    val catEx = remember(allCatalog, wEx.exerciseId) {
                        viewModel.getCatalogExercise(wEx.exerciseId)
                    }
                    val exName = catEx?.name ?: wEx.customExerciseName.ifBlank { "Ejercicio #${wEx.exerciseId}" }
                    val itemOverload = remember(allFinishedEx, wEx.exerciseId, wEx.unitType) {
                        ProgressiveOverloadAdvisor.computeSuggestion(
                            unitType = wEx.unitType,
                            historyForExercise = allFinishedEx.filter { it.exerciseId == wEx.exerciseId }
                        )
                    }

                    LiveGymWpExerciseCard(
                        exerciseIndex = exIdx,
                        totalExercises = liveExercises.size,
                        wEx = wEx,
                        catEx = catEx,
                        exName = exName,
                        state = st,
                        isHeroFocused = false,
                        overloadSuggestion = itemOverload,
                        onApplyOverloadSuggestion = if (itemOverload != null) {
                            {
                                val targetIndices = st.done.indices.filter { !st.done[it] }
                                    .ifEmpty { st.done.indices.toList() }
                                when {
                                    itemOverload.suggestedLoadKg != null -> {
                                        val clean = formatWeightClean(itemOverload.suggestedLoadKg)
                                        targetIndices.forEach { i -> st.loads[i] = clean }
                                    }
                                    itemOverload.suggestedRepsOrTime != null -> {
                                        val clean = itemOverload.suggestedRepsOrTime.coerceAtLeast(1).toString()
                                        targetIndices.forEach { i -> st.reps[i] = clean }
                                    }
                                }
                                persistExerciseState(wEx, st)
                                feedbackBannerText = "✓ Meta aplicada: ${itemOverload.buttonLabel}"
                            }
                        } else null,
                        onInspectGuide = { if (catEx != null) inspectingExercise = catEx },
                        onReplaceExercise = { replacingExerciseTarget = wEx to exName },
                        onReplicateFirstSet = {
                            if (st.reps.size > 1) {
                                val r0 = st.reps.firstOrNull() ?: "10"
                                val l0 = st.loads.firstOrNull() ?: "20"
                                for (i in 1 until st.reps.size) {
                                    st.reps[i] = r0
                                    st.loads[i] = l0
                                }
                                persistExerciseState(wEx, st)
                                feedbackBannerText = "✓ Serie 1 replicada en $exName"
                            }
                        },
                        onEditRestSeconds = { restPickerTarget = wEx },
                        onMoveUp = if (exIdx > 0) {
                            { viewModel.moveWorkoutExercise(dayId, wEx.id, moveUp = true) }
                        } else null,
                        onMoveDown = if (exIdx < liveExercises.lastIndex) {
                            { viewModel.moveWorkoutExercise(dayId, wEx.id, moveUp = false) }
                        } else null,
                        onToggleBiSet = {
                            viewModel.updateWorkoutExercise(wEx.copy(isBiSet = !wEx.isBiSet))
                        },
                        onDeleteExercise = { exercisePendingDelete = wEx to exName },
                        onStepReps = { setIdx, delta ->
                            val cur = st.reps[setIdx].toIntOrNull() ?: 10
                            st.reps[setIdx] = (cur + delta).coerceAtLeast(1).toString()
                            persistExerciseState(wEx, st)
                        },
                        onStepLoad = { setIdx, delta ->
                            val cur = st.loads[setIdx].toFloatOrNull() ?: 20f
                            st.loads[setIdx] = formatWeightClean(cur + delta)
                            persistExerciseState(wEx, st)
                        },
                        onTapExactValue = { setIdx, isLoad, unitLbl ->
                            activeSetFieldTarget = SetFieldEditTarget(
                                workoutExercise = wEx,
                                setIndex = setIdx,
                                isLoadField = isLoad,
                                currentValue = if (isLoad) st.loads[setIdx] else st.reps[setIdx],
                                unitLabel = unitLbl
                            )
                        },
                        onToggleSetDone = { setIdx, checked ->
                            st.done[setIdx] = checked
                            persistExerciseState(wEx, st)
                            if (checked) {
                                startRestCountdown(wEx.restSeconds.coerceAtLeast(15))
                            }
                        },
                        onAddSet = {
                            if (st.done.size < 12) {
                                st.done.add(false)
                                st.reps.add(st.reps.lastOrNull() ?: "10")
                                st.loads.add(st.loads.lastOrNull() ?: "20")
                                persistExerciseState(wEx, st)
                            }
                        },
                        onRemoveLastSet = {
                            if (st.done.size > 1) {
                                st.done.removeAt(st.done.lastIndex)
                                st.reps.removeAt(st.reps.lastIndex)
                                st.loads.removeAt(st.loads.lastIndex)
                                persistExerciseState(wEx, st)
                            }
                        }
                    )
                }
            }
        }

        // 4. Docked Rest Countdown Banner + Finish Session Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GymCardBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (restRemainingSeconds > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GymOrangeDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "TIEMPO DE DESCANSO",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "%02d:%02d".format(restRemainingSeconds / 60, restRemainingSeconds % 60),
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = { adjustRestCountdown(-15) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("-15s", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                FilledTonalButton(
                                    onClick = { adjustRestCountdown(+15) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+15s", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        restEndElapsedMs = 0L
                                        restRemainingSeconds = 0
                                        AppUpdateManager.cancelRestNotification(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Black.copy(alpha = 0.35f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Saltar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                (restRemainingSeconds.toFloat() / restTotalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val finalElapsed = computeCurrentElapsedSeconds().coerceAtLeast(elapsedSeconds).coerceAtLeast(60)
                    elapsedSeconds = finalElapsed
                    restEndElapsedMs = 0L
                    restRemainingSeconds = 0
                    AppUpdateManager.cancelRestNotification(context)

                    val logs = liveExercises.mapNotNull { wEx ->
                        val st = stateByExerciseId[wEx.id] ?: return@mapNotNull null
                        val doneIndices = st.done.indices.filter { st.done[it] }
                        if (doneIndices.isEmpty() && completedSetsCount > 0) return@mapNotNull null
                        val activeIndices = if (doneIndices.isNotEmpty()) doneIndices else st.done.indices.toList()
                        val catEx = viewModel.getCatalogExercise(wEx.exerciseId)
                        val exName = catEx?.name ?: wEx.customExerciseName.ifBlank { "Ejercicio #${wEx.exerciseId}" }
                        val muscles = catEx?.muscles ?: "00000000000000000"
                        val repsDone = activeIndices.map { i -> st.reps.getOrNull(i)?.toIntOrNull() ?: 10 }
                        val loadsDone = activeIndices.map { i -> st.loads.getOrNull(i)?.toFloatOrNull() ?: 0f }
                        CompletedExerciseLog(
                            exerciseId = wEx.exerciseId,
                            exerciseName = exName,
                            muscles = muscles,
                            loadsPerSet = loadsDone,
                            repsPerSet = repsDone,
                            unitType = wEx.unitType
                        )
                    }
                    viewModel.finishWorkoutSession(
                        planId = planId,
                        planName = planName,
                        dayName = currentDayName,
                        durationSeconds = finalElapsed,
                        completedLogs = logs
                    ) { calories, updatedFatigue ->
                        completedSummary = calories to updatedFatigue
                    }
                },
                enabled = liveExercises.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GymOrange,
                    contentColor = GymOnPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Finalizar y Registrar Entrenamiento",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }

    // Dialog: Add Exercise to Live Routine (or create a custom gym machine right here!)
    if (showAddExerciseModal) {
        ExercisePickerModal(
            viewModel = viewModel,
            modalTitle = "Añadir Ejercicio al Entrenamiento",
            onDismiss = { showAddExerciseModal = false },
            onSelectExercise = { chosen ->
                val newIdx = liveExercises.size
                viewModel.addExerciseToDay(dayId, chosen.id)
                showAddExerciseModal = false
                focusedExerciseIndex = newIdx
                feedbackBannerText = "✓ ${chosen.name} añadido a la rutina"
            }
        )
    }

    // Dialog: Replace Exercise in-place during Live Workout
    replacingExerciseTarget?.let { (targetWEx, oldName) ->
        ExercisePickerModal(
            viewModel = viewModel,
            modalTitle = "Reemplazar: $oldName",
            onDismiss = { replacingExerciseTarget = null },
            onSelectExercise = { replacement ->
                viewModel.replaceWorkoutExercise(targetWEx, replacement)
                replacingExerciseTarget = null
                feedbackBannerText = "⇄ Reemplazado por ${replacement.name}"
            }
        )
    }

    // Dialog: Quick Rest Duration Selector
    restPickerTarget?.let { targetWEx ->
        AlertDialog(
            onDismissRequest = { restPickerTarget = null },
            title = {
                Text("Tiempo de Descanso", color = GymOrange, fontWeight = FontWeight.ExtraBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Selecciona el descanso automático al completar cada serie:",
                        color = GymTextSecondary,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 45, 60, 90, 120, 180).forEach { sec ->
                            FilterChip(
                                selected = targetWEx.restSeconds == sec,
                                onClick = {
                                    viewModel.updateWorkoutExercise(targetWEx.copy(restSeconds = sec))
                                    restPickerTarget = null
                                },
                                label = { Text("${sec}s", fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { restPickerTarget = null }) {
                    Text("Cerrar", color = GymOrange)
                }
            },
            containerColor = GymCardBg
        )
    }

    // Dialog: Rename Routine Day mid-workout
    if (editingDayNameDialog && liveDay != null) {
        var updatedName by remember(liveDay.name) { mutableStateOf(liveDay.name) }
        AlertDialog(
            onDismissRequest = { editingDayNameDialog = false },
            title = { Text("Editar Nombre de la Rutina", color = GymTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("Nombre del día") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedName.isNotBlank()) {
                            viewModel.updateWorkoutDayColorOrName(liveDay.copy(name = updatedName.trim()))
                        }
                        editingDayNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDayNameDialog = false }) {
                    Text("Cancelar", color = GymTextSecondary)
                }
            },
            containerColor = GymCardBg
        )
    }

    // Dialog: Exact Number Input for Reps or Weight (+ option to replicate to following sets!)
    activeSetFieldTarget?.let { target ->
        val st = stateByExerciseId[target.workoutExercise.id]
        if (st != null) {
            SetExactValueDialog(
                target = target,
                onDismiss = { activeSetFieldTarget = null },
                onConfirm = { newVal, replicateToRemaining ->
                    val cleaned = if (target.isLoadField) {
                        formatWeightClean(newVal.replace(',', '.').toFloatOrNull() ?: 0f)
                    } else {
                        (newVal.toIntOrNull() ?: 10).coerceAtLeast(1).toString()
                    }
                    if (target.setIndex in st.reps.indices) {
                        if (target.isLoadField) {
                            st.loads[target.setIndex] = cleaned
                            if (replicateToRemaining) {
                                for (i in (target.setIndex + 1) until st.loads.size) {
                                    st.loads[i] = cleaned
                                }
                            }
                        } else {
                            st.reps[target.setIndex] = cleaned
                            if (replicateToRemaining) {
                                for (i in (target.setIndex + 1) until st.reps.size) {
                                    st.reps[i] = cleaned
                                }
                            }
                        }
                        persistExerciseState(target.workoutExercise, st)
                        if (replicateToRemaining) {
                            feedbackBannerText = "✓ Valor replicado al resto de las series"
                        }
                    }
                    activeSetFieldTarget = null
                }
            )
        }
    }

    // Dialog: Confirm removing an exercise mid-workout
    exercisePendingDelete?.let { (targetEx, targetName) ->
        ConfirmDeleteDialog(
            itemTitle = "el ejercicio \"$targetName\" de esta rutina",
            onConfirm = {
                viewModel.deleteWorkoutExercise(targetEx.id)
                exercisePendingDelete = null
            },
            onDismiss = { exercisePendingDelete = null }
        )
    }

    // Dialog: Inspect Exercise Details & 2D Muscle Map
    inspectingExercise?.let { ex ->
        ExerciseDetailDialog(
            exercise = ex,
            isFavorite = favorites.contains(ex.id),
            history = allFinishedEx.filter { it.exerciseId == ex.id },
            onToggleFavorite = { viewModel.toggleFavorite(ex.id) },
            onDismiss = { inspectingExercise = null },
            onEditCustomExercise = { target ->
                inspectingExercise = null
                editingCustomCatalogExercise = target
            }
        )
    }

    editingCustomCatalogExercise?.let { customEx ->
        CreateOrEditCustomExerciseDialog(
            initialExercise = customEx,
            onDismiss = { editingCustomCatalogExercise = null },
            onSave = { id, name, eq, prim, sec, unit, notes, photoPath ->
                viewModel.saveCustomExercise(id, name, eq, prim, sec, unit, notes, photoPath) {
                    editingCustomCatalogExercise = null
                }
            }
        )
    }

    // Post-Workout Celebration & 2D Muscle Fatigue Impact Summary
    completedSummary?.let { (calories, updatedFatigueMap) ->
        AlertDialog(
            onDismissRequest = onExitSession,
            title = {
                Text(
                    "¡Entrenamiento Completado! 🏆",
                    color = GymOrange,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Duración: ${elapsedSeconds / 60} min • Calorías quemadas: ~$calories kcal",
                        color = GymTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Estado de Fatiga Muscular 2D Actualizado:",
                        color = GymTextSecondary,
                        fontSize = 12.sp
                    )
                    AnatomyFatigueBodyMap(
                        fatigueByMuscleId = updatedFatigueMap,
                        mapHeight = 200.dp
                    )
                    val topFatigued = updatedFatigueMap.entries
                        .filter { it.key in 0..15 && it.value > 0 }
                        .sortedByDescending { it.value }
                        .take(4)
                    topFatigued.forEach { (mId, pct) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(MuscleCatalog.getName(mId), fontSize = 12.sp, color = GymTextPrimary)
                            Text("$pct% fatiga", fontSize = 12.sp, color = getFatigueColor(pct), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onExitSession,
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                ) {
                    Text("Volver a Mis Rutinas", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = GymCardBg
        )
    }

    if (confirmingExitSession) {
        ConfirmDeleteDialog(
            itemTitle = "la sesión en curso de \"$currentDayName\"",
            customQuestion = "Salir ahora cerrará el entrenamiento en curso de \"$currentDayName\" sin registrar la sesión finalizada (los cambios hechos a la rutina sí se conservan).",
            confirmButtonText = "Sí, Salir",
            onConfirm = {
                confirmingExitSession = false
                AppUpdateManager.cancelRestNotification(context)
                onExitSession()
            },
            onDismiss = { confirmingExitSession = false }
        )
    }
}

@Composable
private fun LiveGymWpExerciseCard(
    exerciseIndex: Int,
    totalExercises: Int,
    wEx: WorkoutExerciseEntity,
    catEx: CatalogExercise?,
    exName: String,
    state: LiveExerciseRuntimeState,
    isHeroFocused: Boolean,
    overloadSuggestion: OverloadSuggestion? = null,
    onApplyOverloadSuggestion: (() -> Unit)? = null,
    onInspectGuide: () -> Unit,
    onReplaceExercise: () -> Unit,
    onReplicateFirstSet: () -> Unit,
    onEditRestSeconds: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onToggleBiSet: () -> Unit,
    onDeleteExercise: () -> Unit,
    onStepReps: (Int, Int) -> Unit,
    onStepLoad: (Int, Float) -> Unit,
    onTapExactValue: (Int, Boolean, String) -> Unit,
    onToggleSetDone: (Int, Boolean) -> Unit,
    onAddSet: () -> Unit,
    onRemoveLastSet: () -> Unit
) {
    val primaryMuscleNames = remember(catEx?.muscles) {
        if (catEx == null) ""
        else (0..15)
            .filter { it < catEx.muscles.length && catEx.muscles[it] == '3' }
            .joinToString(" • ") { MuscleCatalog.getName(it) }
    }
    val equipmentLabel = remember(catEx?.equipment) {
        catEx?.equipment?.joinToString(" • ") ?: ""
    }
    val unitLabel = when (wEx.unitType) {
        2 -> "seg"
        3 -> "min"
        else -> "reps"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = GymCardBg),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            if (wEx.isBiSet && exerciseIndex > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(GymAccentCyan.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = GymAccentCyan, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "BI-SERIE / SUPERSERIE CON EL ANTERIOR",
                        color = GymAccentCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }

            // Hero Exercise Showcase (GIF + Name + Muscles + Equipment)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInspectGuide() }
            ) {
                if (catEx != null) {
                    AnimatedExerciseGif(
                        gifAssetPath = catEx.gifAssetPath,
                        contentDescription = exName,
                        modifier = Modifier.size(if (isHeroFocused) 96.dp else 74.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Ejercicio ${exerciseIndex + 1} de $totalExercises",
                        color = GymTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = exName,
                        color = GymTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isHeroFocused) 17.sp else 15.sp,
                        lineHeight = 21.sp
                    )
                    if (primaryMuscleNames.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = primaryMuscleNames,
                            color = GymOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    if (equipmentLabel.isNotBlank()) {
                        Text(
                            text = equipmentLabel,
                            color = GymTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Friendly Gym WP Quick Action Pills Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Replicate Set 1 to all sets
                if (state.reps.size > 1) {
                    LiveActionPill(
                        icon = Icons.Default.ContentCopy,
                        label = "Replicar S1",
                        highlighted = true,
                        onClick = onReplicateFirstSet
                    )
                }

                // 2. Replace Exercise in-place
                LiveActionPill(
                    icon = Icons.Default.SwapHoriz,
                    label = "Reemplazar",
                    highlighted = false,
                    onClick = onReplaceExercise
                )

                // 3. Rest Seconds Picker
                LiveActionPill(
                    icon = Icons.Default.Timer,
                    label = "${wEx.restSeconds}s",
                    highlighted = false,
                    onClick = onEditRestSeconds
                )

                // 4. Technique / Stats Guide
                if (catEx != null) {
                    LiveActionPill(
                        icon = Icons.Default.Info,
                        label = "Guía",
                        highlighted = false,
                        onClick = onInspectGuide
                    )
                }

                // 5. Reorder Up / Down
                if (onMoveUp != null) {
                    LiveActionPill(
                        icon = Icons.Default.KeyboardArrowUp,
                        label = "Subir",
                        highlighted = false,
                        onClick = onMoveUp
                    )
                }
                if (onMoveDown != null) {
                    LiveActionPill(
                        icon = Icons.Default.KeyboardArrowDown,
                        label = "Bajar",
                        highlighted = false,
                        onClick = onMoveDown
                    )
                }

                // 6. Toggle Bi-Set
                LiveActionPill(
                    icon = Icons.Default.Bolt,
                    label = if (wEx.isBiSet) "Quitar Bi-Serie" else "Bi-Serie",
                    highlighted = false,
                    onClick = onToggleBiSet
                )

                // 7. Remove Exercise (with confirmation)
                LiveActionPill(
                    icon = Icons.Default.DeleteOutline,
                    label = "Quitar",
                    highlighted = false,
                    tintOverride = FatigueRed,
                    onClick = onDeleteExercise
                )
            }

            if (overloadSuggestion != null) {
                val isOverloadApplied = ProgressiveOverloadAdvisor.isAlreadyApplied(
                    overloadSuggestion,
                    state.loads.toList(),
                    state.reps.toList()
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isOverloadApplied) FatigueGreen.copy(alpha = 0.14f)
                            else GymOrange.copy(alpha = 0.13f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isOverloadApplied) FatigueGreen.copy(alpha = 0.45f)
                            else GymOrange.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = if (isOverloadApplied) FatigueGreen else GymOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = overloadSuggestion.summaryText,
                            color = GymTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (onApplyOverloadSuggestion != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (isOverloadApplied) FatigueGreen.copy(alpha = 0.22f)
                                    else GymOrange
                                )
                                .clickable {
                                    if (!isOverloadApplied) {
                                        onApplyOverloadSuggestion()
                                    }
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isOverloadApplied) "✓ Meta activa" else overloadSuggestion.buttonLabel,
                                color = if (isOverloadApplied) FatigueGreen else GymOnPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tactile Set Rows (Gym WP style — clean steppers + tap-to-type + circular check!)
            state.done.forEachIndexed { setIdx, isDone ->
                val repVal = state.reps.getOrElse(setIdx) { "10" }
                val loadVal = state.loads.getOrElse(setIdx) { "20" }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isDone) FatigueGreen.copy(alpha = 0.14f) else GymSurfaceElevated
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDone) FatigueGreen.copy(alpha = 0.55f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Set Number Badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isDone) FatigueGreen else GymOrange.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${setIdx + 1}",
                            color = if (isDone) Color.White else GymOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }

                    // Reps / Time Stepper Control
                    TactileStepperPill(
                        valueText = repVal,
                        unitText = unitLabel,
                        onMinus = { onStepReps(setIdx, -1) },
                        onPlus = { onStepReps(setIdx, +1) },
                        onTapCenter = { onTapExactValue(setIdx, false, unitLabel) },
                        modifier = Modifier.weight(1f)
                    )

                    // Weight (kg) Stepper Control
                    if (wEx.unitType == 0) {
                        TactileStepperPill(
                            valueText = loadVal,
                            unitText = "kg",
                            onMinus = { onStepLoad(setIdx, -2.5f) },
                            onPlus = { onStepLoad(setIdx, +2.5f) },
                            onTapCenter = { onTapExactValue(setIdx, true, "kg") },
                            modifier = Modifier.weight(1.08f)
                        )
                    }

                    // Big Circular Check Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDone) FatigueGreen else GymCardBg
                            )
                            .border(
                                width = if (isDone) 0.dp else 1.5.dp,
                                color = if (isDone) Color.Transparent else GymOrange.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { onToggleSetDone(setIdx, !isDone) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = if (isDone) "Desmarcar serie" else "Completar serie",
                            tint = if (isDone) Color.White else GymTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Inline Add / Remove Set Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onRemoveLastSet,
                    enabled = state.done.size > 1,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Quitar Serie", fontSize = 12.sp)
                }

                TextButton(
                    onClick = onAddSet,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir Serie", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LiveActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean,
    tintOverride: Color? = null,
    onClick: () -> Unit
) {
    val fg = tintOverride ?: if (highlighted) GymOrange else GymTextPrimary
    val bg = if (highlighted) GymOrange.copy(alpha = 0.16f) else GymSurfaceElevated

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                width = if (highlighted) 1.dp else 0.dp,
                color = if (highlighted) GymOrange.copy(alpha = 0.45f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(15.dp))
        Text(
            text = label,
            color = fg,
            fontWeight = if (highlighted) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TactileStepperPill(
    valueText: String,
    unitText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(GymCardBg)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(GymSurfaceElevated)
                .clickable(onClick = onMinus),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Disminuir",
                tint = GymOrange,
                modifier = Modifier.size(15.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onTapCenter)
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = valueText,
                color = GymTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1
            )
            Text(
                text = unitText,
                color = GymTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(GymSurfaceElevated)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Aumentar",
                tint = GymOrange,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun SetExactValueDialog(
    target: SetFieldEditTarget,
    onDismiss: () -> Unit,
    onConfirm: (newValue: String, replicateToRemaining: Boolean) -> Unit
) {
    var textValue by remember(target) { mutableStateOf(target.currentValue) }
    val titleText = if (target.isLoadField) {
        "Serie ${target.setIndex + 1} • Carga (${target.unitLabel})"
    } else {
        "Serie ${target.setIndex + 1} • ${target.unitLabel.replaceFirstChar { it.uppercase() }}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(titleText, color = GymOrange, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Valor exacto (${target.unitLabel})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (target.isLoadField) KeyboardType.Decimal else KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick presets
                if (target.isLoadField) {
                    Text("Ajuste rápido:", color = GymTextSecondary, fontSize = 11.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(-5f, -2.5f, +1.25f, +2.5f, +5f, +10f).forEach { delta ->
                            val chipLabel = if (delta < 0f) {
                                "-${formatWeightClean(kotlin.math.abs(delta))} kg"
                            } else {
                                "+${formatWeightClean(delta)} kg"
                            }
                            AssistChip(
                                onClick = {
                                    val cur = textValue.replace(',', '.').toFloatOrNull() ?: 0f
                                    textValue = formatWeightClean(cur + delta)
                                },
                                label = { Text(chipLabel, fontSize = 11.sp) }
                            )
                        }
                    }
                } else {
                    Text("Valores frecuentes:", color = GymTextSecondary, fontSize = 11.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(6, 8, 10, 12, 15, 20).forEach { preset ->
                            AssistChip(
                                onClick = { textValue = preset.toString() },
                                label = { Text("$preset", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onConfirm(textValue, true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Guardar y Replicar en series siguientes",
                        color = GymOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(textValue, false) },
                colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = GymTextSecondary)
            }
        },
        containerColor = GymCardBg
    )
}
