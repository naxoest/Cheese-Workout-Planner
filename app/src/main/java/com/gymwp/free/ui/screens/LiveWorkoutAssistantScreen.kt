package com.gymwp.free.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymwp.free.data.CatalogExercise
import com.gymwp.free.data.WorkoutDayEntity
import com.gymwp.free.data.WorkoutExerciseEntity
import com.gymwp.free.data.WorkoutPlanEntity
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.ui.CompletedExerciseLog
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AnatomyFatigueBodyMap
import com.gymwp.free.ui.components.AnimatedExerciseGif
import com.gymwp.free.ui.components.ConfirmDeleteDialog
import com.gymwp.free.ui.components.ExerciseDetailDialog
import com.gymwp.free.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun LiveWorkoutAssistantScreen(
    viewModel: GymWpViewModel,
    plan: WorkoutPlanEntity?,
    day: WorkoutDayEntity,
    exercises: List<WorkoutExerciseEntity>,
    onExitSession: () -> Unit
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var confirmingExitSession by remember { mutableStateOf(false) }

    // Rest timer state
    var restRemainingSeconds by remember { mutableIntStateOf(0) }
    var restTotalSeconds by remember { mutableIntStateOf(60) }

    // Per-exercise per-set state
    val setsDoneState = remember(exercises) {
        exercises.map { ex ->
            mutableStateListOf(*Array(ex.setsCount.coerceAtLeast(1)) { false })
        }
    }
    val repsState = remember(exercises) {
        exercises.map { ex ->
            val parsed = ex.repsPerSetCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
            val list = List(ex.setsCount.coerceAtLeast(1)) { i -> parsed.getOrElse(i) { parsed.lastOrNull() ?: 10 }.toString() }
            mutableStateListOf(*list.toTypedArray())
        }
    }
    val loadsState = remember(exercises) {
        exercises.map { ex ->
            val parsed = ex.loadsPerSetCsv.split(",").mapNotNull { it.trim().toFloatOrNull() }
            val list = List(ex.setsCount.coerceAtLeast(1)) { i -> parsed.getOrElse(i) { parsed.lastOrNull() ?: 20f }.toString() }
            mutableStateListOf(*list.toTypedArray())
        }
    }

    var inspectingExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var completedSummary by remember { mutableStateOf<Pair<Int, Map<Int, Int>>?>(null) }
    val favorites by viewModel.favoriteIds.collectAsState()
    val allFinishedEx by viewModel.allFinishedExercises.collectAsState()

    // Global workout stopwatch
    LaunchedEffect(isPaused, completedSummary) {
        while (!isPaused && completedSummary == null) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    // Rest countdown timer with beep when finished
    LaunchedEffect(restRemainingSeconds) {
        if (restRemainingSeconds > 0) {
            delay(1000L)
            restRemainingSeconds--
            if (restRemainingSeconds == 0) {
                try {
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                        .startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                } catch (_: Exception) {
                }
            }
        }
    }

    val totalSetsCount = setsDoneState.sumOf { it.size }
    val completedSetsCount = setsDoneState.sumOf { list -> list.count { it } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GymDarkBg)
    ) {
        // Top Live Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(GymSurfaceElevated)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { confirmingExitSession = true }) {
                Icon(Icons.Default.Close, contentDescription = "Salir", tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = day.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                val mins = elapsedSeconds / 60
                val secs = elapsedSeconds % 60
                Text(
                    text = "⏱ %02d:%02d • %d/%d series".format(mins, secs, completedSetsCount, totalSetsCount),
                    color = GymOrange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
            IconButton(onClick = { isPaused = !isPaused }) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = "Pausar",
                    tint = GymOrange
                )
            }
        }

        LinearProgressIndicator(
            progress = { (completedSetsCount.toFloat() / totalSetsCount.coerceAtLeast(1)).coerceIn(0f, 1f) },
            color = GymOrange,
            trackColor = GymCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                itemsIndexed(exercises, key = { _, item -> item.id }) { exIdx, wEx ->
                    val catEx = viewModel.getCatalogExercise(wEx.exerciseId)
                    val exName = catEx?.name ?: wEx.customExerciseName.ifBlank { "Ejercicio #${wEx.exerciseId}" }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = GymCardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            if (wEx.isBiSet && exIdx > 0) {
                                Text(
                                    text = "⚡ SUPERSERIE / BI-SERIE CON EL ANTERIOR",
                                    color = GymAccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { if (catEx != null) inspectingExercise = catEx }
                            ) {
                                if (catEx != null) {
                                    AnimatedExerciseGif(
                                        gifAssetPath = catEx.gifAssetPath,
                                        contentDescription = exName,
                                        modifier = Modifier.size(68.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = exName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Descanso sugerido: ${wEx.restSeconds}s • Toca para ver instrucciones",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Set rows
                            val exSetsDone = setsDoneState[exIdx]
                            val exReps = repsState[exIdx]
                            val exLoads = loadsState[exIdx]

                            exSetsDone.forEachIndexed { setIdx, isDone ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                            if (isDone) FatigueGreen.copy(alpha = 0.18f) else GymSurfaceElevated,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Serie ${setIdx + 1}",
                                        color = if (isDone) FatigueGreen else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.width(62.dp)
                                    )

                                    OutlinedTextField(
                                        value = exReps[setIdx],
                                        onValueChange = { exReps[setIdx] = it },
                                        label = {
                                            Text(
                                                when (wEx.unitType) {
                                                    2 -> "Seg"
                                                    3 -> "Min"
                                                    else -> "Reps"
                                                },
                                                fontSize = 10.sp
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                    )

                                    if (wEx.unitType == 0) {
                                        OutlinedTextField(
                                            value = exLoads[setIdx],
                                            onValueChange = { exLoads[setIdx] = it },
                                            label = { Text("Carga (kg)", fontSize = 10.sp) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp)
                                        )
                                    }

                                    Checkbox(
                                        checked = isDone,
                                        onCheckedChange = { checked ->
                                            exSetsDone[setIdx] = checked
                                            // Also persist updated loads/reps into DB automatically
                                            viewModel.updateWorkoutExercise(
                                                wEx.copy(
                                                    repsPerSetCsv = exReps.joinToString(","),
                                                    loadsPerSetCsv = exLoads.joinToString(",")
                                                )
                                            )
                                            if (checked) {
                                                restTotalSeconds = wEx.restSeconds.coerceAtLeast(15)
                                                restRemainingSeconds = restTotalSeconds
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = FatigueGreen,
                                            uncheckedColor = Color.LightGray
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Docked Rest Timer + Finish Workout Button (inside safe Column)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GymSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (restRemainingSeconds > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GymOrangeDark),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("CRONÓMETRO DE DESCANSO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "%02d:%02d".format(restRemainingSeconds / 60, restRemainingSeconds % 60),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { restRemainingSeconds = (restRemainingSeconds - 15).coerceAtLeast(0) },
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("-15s")
                            }
                            FilledTonalButton(
                                onClick = { restRemainingSeconds += 15 },
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("+15s")
                            }
                            Button(
                                onClick = { restRemainingSeconds = 0 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.35f)),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Saltar")
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val logs = exercises.mapIndexedNotNull { idx, wEx ->
                        val doneList = setsDoneState[idx]
                        val anyDone = doneList.any { it }
                        if (!anyDone && completedSetsCount > 0) return@mapIndexedNotNull null
                        val catEx = viewModel.getCatalogExercise(wEx.exerciseId)
                        val exName = catEx?.name ?: wEx.customExerciseName.ifBlank { "Ejercicio #${wEx.exerciseId}" }
                        val muscles = catEx?.muscles ?: "00000000000000000"
                        val repsDone = repsState[idx].map { it.toIntOrNull() ?: 10 }
                        val loadsDone = loadsState[idx].map { it.toFloatOrNull() ?: 0f }
                        CompletedExerciseLog(
                            exerciseId = wEx.exerciseId,
                            exerciseName = exName,
                            muscles = muscles,
                            loadsPerSet = loadsDone,
                            repsPerSet = repsDone
                        )
                    }
                    viewModel.finishWorkoutSession(
                        planId = plan?.id,
                        planName = plan?.name ?: "Entrenamiento Libre",
                        dayName = day.name,
                        durationSeconds = elapsedSeconds.coerceAtLeast(60),
                        completedLogs = logs
                    ) { calories, updatedFatigue ->
                        completedSummary = calories to updatedFatigue
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GymOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Finalizar y Registrar Entrenamiento", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }

    inspectingExercise?.let { ex ->
        ExerciseDetailDialog(
            exercise = ex,
            isFavorite = favorites.contains(ex.id),
            history = allFinishedEx.filter { it.exerciseId == ex.id },
            onToggleFavorite = { viewModel.toggleFavorite(ex.id) },
            onDismiss = { inspectingExercise = null }
        )
    }

    // Post-Workout Celebration & Muscle Fatigue Impact Summary
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Estado de Fatiga Muscular Actualizado:",
                        color = Color.LightGray,
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
                            Text(MuscleCatalog.getName(mId), fontSize = 12.sp, color = Color.White)
                            Text("$pct% fatiga", fontSize = 12.sp, color = getFatigueColor(pct), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onExitSession,
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                ) {
                    Text("Volver a Mis Rutinas")
                }
            }
        )
    }

    if (confirmingExitSession) {
        ConfirmDeleteDialog(
            itemTitle = "la sesión en curso de \"${day.name}\"",
            customQuestion = "Salir ahora cerrará el entrenamiento en curso de \"${day.name}\" sin registrar la sesión finalizada.",
            confirmButtonText = "Sí, Salir",
            onConfirm = {
                confirmingExitSession = false
                onExitSession()
            },
            onDismiss = { confirmingExitSession = false }
        )
    }
}
