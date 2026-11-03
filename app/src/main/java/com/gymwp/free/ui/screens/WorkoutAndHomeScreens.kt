package com.gymwp.free.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gymwp.free.data.*
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.domain.WorkoutMetricsCalculator
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AnimatedExerciseGif
import com.gymwp.free.ui.components.AppThemeSelectorDialog
import com.gymwp.free.ui.components.ConfirmDeleteDialog
import com.gymwp.free.ui.components.CreateOrEditCustomExerciseDialog
import com.gymwp.free.ui.components.ExerciseDetailDialog
import com.gymwp.free.ui.components.GoogleDriveSyncDialog
import com.gymwp.free.ui.components.UserSwitcherDialog
import com.gymwp.free.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeWorkoutsScreen(
    viewModel: GymWpViewModel,
    onStartLiveWorkout: (WorkoutPlanEntity?, WorkoutDayEntity, List<WorkoutExerciseEntity>) -> Unit,
    onNavigateToExplore: () -> Unit
) {
    val profile by viewModel.userProfile.collectAsState()
    val plans by viewModel.workoutPlans.collectAsState()
    val allDays by viewModel.allWorkoutDays.collectAsState()
    val allExercises by viewModel.allWorkoutExercises.collectAsState()
    val allCatalog by viewModel.allAvailableExercises.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val finishedWorkouts by viewModel.finishedWorkouts.collectAsState()
    val allFinishedEx by viewModel.allFinishedExercises.collectAsState()
    val fatigueList by viewModel.muscleFatigueList.collectAsState()
    val driveSyncState by viewModel.driveSyncState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    var selectedPlanId by remember(plans) {
        mutableStateOf(plans.firstOrNull { it.isActive }?.id ?: plans.firstOrNull()?.id)
    }
    var showSmartWizard by remember { mutableStateOf(false) }
    var showFreestyleDialog by remember { mutableStateOf(false) }
    var showDriveSyncDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showUserSwitcherDialog by remember { mutableStateOf(false) }
    var showNewPlanDialog by remember { mutableStateOf(false) }
    var showAddDayDialogForPlan by remember { mutableStateOf<Long?>(null) }
    var showAddExerciseForDay by remember { mutableStateOf<Long?>(null) }
    var replacingWorkoutExercise by remember { mutableStateOf<Pair<WorkoutExerciseEntity, String>?>(null) }
    var editingWorkoutExercise by remember { mutableStateOf<WorkoutExerciseEntity?>(null) }
    var inspectingCatalogExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var editingCustomCatalogExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var customExercisePendingDelete by remember { mutableStateOf<CatalogExercise?>(null) }
    var planPendingDelete by remember { mutableStateOf<WorkoutPlanEntity?>(null) }
    var dayPendingDelete by remember { mutableStateOf<WorkoutDayEntity?>(null) }
    var exercisePendingDelete by remember { mutableStateOf<Pair<WorkoutExerciseEntity, String>?>(null) }
    var finishedWorkoutPendingDelete by remember { mutableStateOf<FinishedWorkoutDayEntity?>(null) }

    // Trigger Smart Plan onboarding wizard automatically if no plans exist yet
    LaunchedEffect(profile, plans) {
        if (profile != null && !profile!!.onboardingDone && plans.isEmpty()) {
            showSmartWizard = true
        }
    }

    val activePlan = plans.firstOrNull { it.id == selectedPlanId } ?: plans.firstOrNull()
    val daysForActivePlan = remember(allDays, activePlan) {
        allDays.filter { it.workoutPlanId == activePlan?.id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GymDarkBg)
            .padding(horizontal = 14.dp)
    ) {
        // Top Header & Quick Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cheese WP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = GymOrange
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GymSurfaceElevated)
                        .clickable { showUserSwitcherDialog = true }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Cambiar Usuario",
                        tint = GymOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${profile?.name ?: "Usuario"} (Cambiar)",
                        style = MaterialTheme.typography.labelSmall,
                        color = GymTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Cambiar Tema",
                        tint = GymOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { showDriveSyncDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    val isLinked = !driveSyncState.linkedUri.isNullOrBlank()
                    Icon(
                        imageVector = if (isLinked) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                        contentDescription = "Google Drive Auto-Sync",
                        tint = if (isLinked) FatigueGreen else GymOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                FilledTonalButton(
                    onClick = { showFreestyleDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = GymSurfaceElevated),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Libre", color = GymTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { showSmartWizard = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Plan IA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Plan Selector Row (3-Phase Periodization & Custom Plans)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            plans.forEach { plan ->
                val isSelected = plan.id == activePlan?.id
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedPlanId = plan.id },
                    label = {
                        Text(
                            text = plan.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = if (plan.isCustomPeriodized) {
                        {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GymOrange,
                        selectedLabelColor = GymOnPrimary,
                        containerColor = GymCardBg,
                        labelColor = GymTextSecondary
                    )
                )
            }
            OutlinedButton(
                onClick = { showNewPlanDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Crear Plan", color = GymOrange, fontSize = 12.sp)
            }
        }

        if (activePlan == null) {
            // Empty state
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "¡Bienvenido a Cheese WP!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GymTextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Genera tu Plan Personalizado de 3 Fases (Adaptación, Hipertrofia y Fuerza) o explora el catálogo de 57 rutinas especializadas.",
                        color = GymTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showSmartWizard = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                        ) {
                            Text("Generar Plan 3 Fases", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = onNavigateToExplore) {
                            Text("Ver 57 Planes", color = GymOrange)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active Plan Overview Banner
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GymCardBg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = activePlan.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = GymTextPrimary
                                    )
                                    if (activePlan.description.isNotBlank()) {
                                        Text(
                                            text = activePlan.description,
                                            color = GymTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                IconButton(onClick = { planPendingDelete = activePlan }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar Plan", tint = GymTextSecondary)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val progress = (activePlan.completedSessionsCount.toFloat() / activePlan.targetSessionsCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                color = GymOrange,
                                trackColor = GymSurfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sesiones completadas: ${activePlan.completedSessionsCount} / ${activePlan.targetSessionsCount}",
                                    color = GymTextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "+ Añadir Día",
                                    color = GymOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable { showAddDayDialogForPlan = activePlan.id }
                                )
                            }
                        }
                    }
                }

                // Workout Days Cards
                items(daysForActivePlan, key = { it.id }) { day ->
                    val dayExercises = remember(allExercises, day.id) {
                        allExercises.filter { it.workoutDayId == day.id }.sortedBy { it.orderIndex }
                    }
                    val dayColor = DayPalette[day.colorIndex.coerceIn(0, DayPalette.lastIndex)]
                    val totalSets = dayExercises.sumOf { it.setsCount }
                    val (minDur, maxDur) = WorkoutMetricsCalculator.estimateDurationWindowMinutes(totalSets, dayExercises.size)
                    val estKcal = WorkoutMetricsCalculator.calculateEstimatedCalories(totalSets, profile?.weightKg ?: 75f)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = GymCardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Colored Day Banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(dayColor)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = day.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "${dayExercises.size} ejercicios • $totalSets series • $minDur-$maxDur min • ~$estKcal kcal",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            viewModel.updateWorkoutDayColorOrName(
                                                day.copy(colorIndex = (day.colorIndex + 1) % DayPalette.size)
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.ColorLens, contentDescription = "Cambiar Color", tint = Color.White)
                                    }
                                    IconButton(onClick = { dayPendingDelete = day }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar Día", tint = Color.White)
                                    }
                                }
                            }

                            // Exercises inside this Day
                            Column(Modifier.padding(12.dp)) {
                                dayExercises.forEachIndexed { idx, wEx ->
                                    val catEx = remember(allCatalog, wEx.exerciseId) {
                                        viewModel.getCatalogExercise(wEx.exerciseId)
                                    }
                                    val exName = catEx?.name ?: wEx.customExerciseName.ifBlank { "Ejercicio #${wEx.exerciseId}" }

                                    if (wEx.isBiSet && idx > 0) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "⚡ BI-SERIE / SUPERSERIE (Sin descanso previo)",
                                                color = GymAccentCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp)
                                            .background(GymSurfaceElevated, RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (catEx != null) inspectingCatalogExercise = catEx
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (catEx != null) {
                                            AnimatedExerciseGif(
                                                gifAssetPath = catEx.gifAssetPath,
                                                contentDescription = exName,
                                                modifier = Modifier.size(54.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = exName,
                                                color = GymTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            val unitSuffix = when (wEx.unitType) {
                                                2 -> "seg"
                                                3 -> "min"
                                                else -> "reps"
                                            }
                                            Text(
                                                text = "${wEx.setsCount} series • [${wEx.repsPerSetCsv}] $unitSuffix",
                                                color = GymOrange,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (wEx.unitType == 0) {
                                                Text(
                                                    text = "Cargas: ${wEx.loadsPerSetCsv} kg • Descanso: ${wEx.restSeconds}s",
                                                    color = GymTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        // Reemplazar ejercicio sin eliminarlo
                                        IconButton(
                                            onClick = { replacingWorkoutExercise = wEx to exName },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SwapHoriz,
                                                contentDescription = "Reemplazar ejercicio",
                                                tint = GymOrange,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { editingWorkoutExercise = wEx },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Editar series/carga",
                                                tint = GymTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { exercisePendingDelete = wEx to exName },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Eliminar ejercicio",
                                                tint = GymTextSecondary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { showAddExerciseForDay = day.id }) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = GymOrange)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Añadir Ejercicio", color = GymOrange, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            onStartLiveWorkout(activePlan, day, dayExercises)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = dayColor, contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Iniciar Entrenamiento", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Historial de Entrenamientos Realizados con opción de eliminar
                if (finishedWorkouts.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Historial de Entrenamientos Realizados (${finishedWorkouts.size})",
                            color = GymOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                    items(finishedWorkouts, key = { "fw_${it.id}" }) { fw ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GymCardBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = fw.dayName,
                                            color = GymTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = dateFormat.format(Date(fw.timestampMillis)),
                                            color = GymTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(fw.planName, color = GymOrange, fontSize = 12.sp)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "⏱ ${fw.durationSeconds / 60} min • 🔥 ${fw.caloriesBurned} kcal • 🏋️ Vol: ${fw.totalVolumeKg.toInt()} kg • ${fw.totalSets} series",
                                        color = GymTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(onClick = { finishedWorkoutPendingDelete = fw }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Eliminar entrenamiento realizado",
                                        tint = GymTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Smart 3-Phase Plan Generator & Onboarding
    if (showSmartWizard) {
        SmartPlanOnboardingDialog(
            initialProfile = profile ?: UserProfileEntity(),
            onDismiss = { showSmartWizard = false },
            onConfirm = { updatedProfile ->
                viewModel.saveProfileAndGenerateSmartPlan(updatedProfile, regeneratePlan = true)
                showSmartWizard = false
            }
        )
    }

    // Dialog: Instant Freestyle Workout Generator
    if (showFreestyleDialog) {
        FreestyleGeneratorDialog(
            fatigueList = fatigueList,
            onDismiss = { showFreestyleDialog = false },
            onGenerate = { minutes, muscleIds ->
                showFreestyleDialog = false
                viewModel.createAndStartFreestyleSession(minutes, muscleIds) { createdDayId ->
                    val createdDay = viewModel.allWorkoutDays.value.firstOrNull { it.id == createdDayId }
                        ?: WorkoutDayEntity(id = createdDayId, workoutPlanId = 0L, name = "Sesión Libre")
                    val createdExs = viewModel.allWorkoutExercises.value.filter { it.workoutDayId == createdDayId }
                    onStartLiveWorkout(null, createdDay, createdExs)
                }
            }
        )
    }

    if (showDriveSyncDialog) {
        GoogleDriveSyncDialog(
            viewModel = viewModel,
            onDismiss = { showDriveSyncDialog = false }
        )
    }

    if (showThemeDialog) {
        AppThemeSelectorDialog(
            viewModel = viewModel,
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showUserSwitcherDialog) {
        UserSwitcherDialog(
            viewModel = viewModel,
            onDismiss = { showUserSwitcherDialog = false },
            onOpenWizardForNewUser = { showSmartWizard = true }
        )
    }

    // Dialog: Create Manual Plan
    if (showNewPlanDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlanDialog = false },
            title = { Text("Nuevo Plan de Entrenamiento", color = GymTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del Plan (ej. Rutina Push/Pull)") }
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Descripción u objetivo") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createManualWorkoutPlan(name, desc)
                        showNewPlanDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                ) {
                    Text("Crear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlanDialog = false }) { Text("Cancelar", color = GymTextSecondary) }
            },
            containerColor = GymCardBg
        )
    }

    // Dialog: Add Day to Plan
    showAddDayDialogForPlan?.let { targetPlanId ->
        var dayName by remember { mutableStateOf("") }
        var chosenColor by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showAddDayDialogForPlan = null },
            title = { Text("Añadir Día de Entrenamiento", color = GymTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dayName,
                        onValueChange = { dayName = it },
                        label = { Text("Nombre (ej. Día A - Pecho y Tríceps)") }
                    )
                    Text("Color de la tarjeta:", fontSize = 13.sp, color = GymTextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DayPalette.take(8).forEachIndexed { idx, col ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (chosenColor == idx) 3.dp else 0.dp,
                                        color = GymTextPrimary,
                                        shape = CircleShape
                                    )
                                    .clickable { chosenColor = idx }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addWorkoutDay(targetPlanId, dayName, chosenColor)
                        showAddDayDialogForPlan = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                ) {
                    Text("Añadir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDayDialogForPlan = null }) { Text("Cancelar", color = GymTextSecondary) }
            },
            containerColor = GymCardBg
        )
    }

    // Dialog: Exercise Picker to add to a Day (supports creating custom exercise right here!)
    showAddExerciseForDay?.let { targetDayId ->
        ExercisePickerModal(
            viewModel = viewModel,
            modalTitle = "Añadir Ejercicio a la Rutina",
            onDismiss = { showAddExerciseForDay = null },
            onSelectExercise = { ex ->
                viewModel.addExerciseToDay(targetDayId, ex.id)
                showAddExerciseForDay = null
            }
        )
    }

    // Dialog: Replace an existing exercise in-place without deleting it
    replacingWorkoutExercise?.let { (targetWEx, oldName) ->
        ExercisePickerModal(
            viewModel = viewModel,
            modalTitle = "Reemplazar: $oldName",
            onDismiss = { replacingWorkoutExercise = null },
            onSelectExercise = { replacementEx ->
                viewModel.replaceWorkoutExercise(targetWEx, replacementEx)
                replacingWorkoutExercise = null
            }
        )
    }

    // Dialog: Edit Sets / Reps / Loads / Replicate Set 1 / BiSet for a WorkoutExerciseEntity
    editingWorkoutExercise?.let { wEx ->
        val exName = viewModel.getCatalogExercise(wEx.exerciseId)?.name
            ?: wEx.customExerciseName.ifBlank { "Ejercicio" }
        EditWorkoutExerciseDialog(
            exercise = wEx,
            exerciseName = exName,
            onDismiss = { editingWorkoutExercise = null },
            onRequestReplace = {
                editingWorkoutExercise = null
                replacingWorkoutExercise = wEx to exName
            },
            onSave = { updated ->
                viewModel.updateWorkoutExercise(updated)
                editingWorkoutExercise = null
            }
        )
    }

    // Dialog: Inspect Exercise Details & GIF & Progression
    inspectingCatalogExercise?.let { ex ->
        ExerciseDetailDialog(
            exercise = ex,
            isFavorite = favorites.contains(ex.id),
            history = allFinishedEx.filter { it.exerciseId == ex.id },
            onToggleFavorite = { viewModel.toggleFavorite(ex.id) },
            onDismiss = { inspectingCatalogExercise = null },
            onEditCustomExercise = { target ->
                inspectingCatalogExercise = null
                editingCustomCatalogExercise = target
            },
            onDeleteCustomExercise = { target ->
                inspectingCatalogExercise = null
                customExercisePendingDelete = target
            }
        )
    }

    editingCustomCatalogExercise?.let { customEx ->
        CreateOrEditCustomExerciseDialog(
            initialExercise = customEx,
            onDismiss = { editingCustomCatalogExercise = null },
            onSave = { id, name, eq, prim, sec, unit, notes ->
                viewModel.saveCustomExercise(id, name, eq, prim, sec, unit, notes) {
                    editingCustomCatalogExercise = null
                }
            }
        )
    }

    customExercisePendingDelete?.let { targetCustom ->
        ConfirmDeleteDialog(
            itemTitle = "el ejercicio personalizado \"${targetCustom.name}\"",
            onConfirm = {
                viewModel.deleteCustomExercise(targetCustom.id)
                customExercisePendingDelete = null
            },
            onDismiss = { customExercisePendingDelete = null }
        )
    }

    planPendingDelete?.let { targetPlan ->
        ConfirmDeleteDialog(
            itemTitle = "el plan \"${targetPlan.name}\" y todos sus días de entrenamiento",
            onConfirm = {
                viewModel.deleteWorkoutPlan(targetPlan.id)
                planPendingDelete = null
            },
            onDismiss = { planPendingDelete = null }
        )
    }

    dayPendingDelete?.let { targetDay ->
        ConfirmDeleteDialog(
            itemTitle = "la rutina \"${targetDay.name}\" y sus ejercicios",
            onConfirm = {
                viewModel.deleteWorkoutDay(targetDay.id)
                dayPendingDelete = null
            },
            onDismiss = { dayPendingDelete = null }
        )
    }

    exercisePendingDelete?.let { (targetEx, targetExName) ->
        ConfirmDeleteDialog(
            itemTitle = "el ejercicio \"$targetExName\" de esta rutina",
            onConfirm = {
                viewModel.deleteWorkoutExercise(targetEx.id)
                exercisePendingDelete = null
            },
            onDismiss = { exercisePendingDelete = null }
        )
    }

    finishedWorkoutPendingDelete?.let { targetFw ->
        ConfirmDeleteDialog(
            itemTitle = "el entrenamiento realizado \"${targetFw.dayName}\" (${targetFw.planName}) del ${dateFormat.format(Date(targetFw.timestampMillis))}",
            onConfirm = {
                viewModel.deleteFinishedWorkoutDay(targetFw.id)
                finishedWorkoutPendingDelete = null
            },
            onDismiss = { finishedWorkoutPendingDelete = null }
        )
    }
}

@Composable
fun SmartPlanOnboardingDialog(
    initialProfile: UserProfileEntity,
    onDismiss: () -> Unit,
    onConfirm: (UserProfileEntity) -> Unit
) {
    var userName by remember(initialProfile.id) { mutableStateOf(initialProfile.name) }
    var gender by remember(initialProfile.id) { mutableIntStateOf(initialProfile.gender) }
    var birthYear by remember(initialProfile.id) { mutableStateOf(initialProfile.birthYear.toString()) }
    var heightCm by remember(initialProfile.id) { mutableStateOf(initialProfile.heightCm.toString()) }
    var weightKg by remember(initialProfile.id) { mutableStateOf(initialProfile.weightKg.toString()) }
    var goal by remember(initialProfile.id) { mutableIntStateOf(initialProfile.goal) }
    var level by remember(initialProfile.id) { mutableIntStateOf(initialProfile.experienceLevel) }
    var place by remember(initialProfile.id) { mutableIntStateOf(initialProfile.trainingPlace) }
    var daysPerWeek by remember(initialProfile.id) { mutableIntStateOf(initialProfile.daysPerWeek.coerceIn(2, 6)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = GymCardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Generador Inteligente de 3 Fases",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = GymOrange
                )
                Text(
                    "Configura el perfil biométrico del usuario para crear su periodización completa (Fase 1: Adaptación, Fase 2: Hipertrofia, Fase 3: Fuerza).",
                    fontSize = 12.sp,
                    color = GymTextSecondary
                )

                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Nombre del Usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gender == 0,
                        onClick = { gender = 0 },
                        label = { Text("Masculino") }
                    )
                    FilterChip(
                        selected = gender == 1,
                        onClick = { gender = 1 },
                        label = { Text("Femenino") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = birthYear,
                        onValueChange = { birthYear = it },
                        label = { Text("Año Nac.") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightCm,
                        onValueChange = { heightCm = it },
                        label = { Text("Altura (cm)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightKg,
                        onValueChange = { weightKg = it },
                        label = { Text("Peso (kg)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Objetivo Principal:", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val goals = listOf("Ganar Masa Muscular", "Perder Peso / Definición", "Ganar Fuerza Máxima", "Salud y Resistencia")
                goals.forEachIndexed { idx, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { goal = idx }
                    ) {
                        RadioButton(selected = goal == idx, onClick = { goal = idx })
                        Text(label, color = GymTextPrimary, fontSize = 13.sp)
                    }
                }

                Text("Nivel de Experiencia:", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Principiante", "Intermedio", "Avanzado").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = level == idx,
                            onClick = { level = idx },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Text("Equipamiento / Lugar:", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Gimnasio", "En Casa", "Peso Corporal").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = place == idx,
                            onClick = { place = idx },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Text("Días por semana: $daysPerWeek días", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Slider(
                    value = daysPerWeek.toFloat(),
                    onValueChange = { daysPerWeek = it.toInt() },
                    valueRange = 2f..6f,
                    steps = 3,
                    colors = SliderDefaults.colors(thumbColor = GymOrange, activeTrackColor = GymOrange)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = GymTextSecondary) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(
                                initialProfile.copy(
                                    name = userName.ifBlank { initialProfile.name },
                                    gender = gender,
                                    birthYear = birthYear.toIntOrNull() ?: 1996,
                                    heightCm = heightCm.toFloatOrNull() ?: 175f,
                                    weightKg = weightKg.toFloatOrNull() ?: 75f,
                                    goal = goal,
                                    experienceLevel = level,
                                    trainingPlace = place,
                                    daysPerWeek = daysPerWeek,
                                    onboardingDone = true
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                    ) {
                        Text("Generar Mis 3 Fases", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FreestyleGeneratorDialog(
    fatigueList: List<MuscleFatigueEntity>,
    onDismiss: () -> Unit,
    onGenerate: (Int, List<Int>) -> Unit
) {
    var durationMinutes by remember { mutableIntStateOf(45) }
    val selectedMuscles = remember { mutableStateListOf<Int>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entrenamiento Libre (Freestyle)", color = GymOrange, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "El motor seleccionará los mejores ejercicios según tu tiempo disponible. Si no marcas músculos específicos, elegirá automáticamente tus músculos más descansados (< 40% fatiga).",
                    fontSize = 12.sp,
                    color = GymTextSecondary
                )
                Text(
                    "Duración objetivo: $durationMinutes minutos (~${(durationMinutes / 6).coerceAtLeast(3)} ejercicios)",
                    color = GymTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = durationMinutes.toFloat(),
                    onValueChange = { durationMinutes = it.toInt() },
                    valueRange = 20f..90f,
                    steps = 6,
                    colors = SliderDefaults.colors(thumbColor = GymOrange, activeTrackColor = GymOrange)
                )
                Text("Músculos a entrenar (Opcional):", color = GymTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Column(
                    modifier = Modifier
                        .height(180.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MuscleCatalog.ALL_MUSCLES.take(16).forEach { m ->
                        val pct = fatigueList.firstOrNull { it.muscleId == m.id }?.fatiguePercent ?: 0
                        val checked = selectedMuscles.contains(m.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedMuscles.remove(m.id) else selectedMuscles.add(m.id)
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selectedMuscles.remove(m.id) else selectedMuscles.add(m.id)
                                }
                            )
                            Text("${m.nameEs} (Fatiga: $pct%)", fontSize = 12.sp, color = getFatigueColor(pct))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(durationMinutes, selectedMuscles.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
            ) {
                Text("Iniciar Ahora", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = GymTextSecondary) }
        },
        containerColor = GymCardBg
    )
}

@Composable
fun ExercisePickerModal(
    viewModel: GymWpViewModel,
    modalTitle: String = "Seleccionar Ejercicio",
    onDismiss: () -> Unit,
    onSelectExercise: (CatalogExercise) -> Unit
) {
    val catalog by viewModel.allAvailableExercises.collectAsState()
    var search by remember { mutableStateOf("") }
    var selectedMuscle by remember { mutableStateOf<Int?>(null) }
    var selectedEquipment by remember { mutableStateOf<String?>(null) }
    var onlyCustom by remember { mutableStateOf(false) }
    var showCreateCustomModal by remember { mutableStateOf(false) }

    val equipmentOptions = remember {
        listOf(
            "Mancuernas", "Barra", "Aparato / Máquina", "Polea / Cable", "Máquina Smith",
            "Peso Corporal", "Barra Fija", "Banco", "TRX / Suspensión", "Pelota Suiza", "Tobillera / Peso Libre"
        )
    }

    val filtered = remember(catalog, search, selectedMuscle, selectedEquipment, onlyCustom) {
        catalog.filter { ex ->
            val matchesQuery = search.isBlank() || ex.name.contains(search, ignoreCase = true)
            val matchesMuscle = selectedMuscle == null || (selectedMuscle!! < ex.muscles.length && ex.muscles[selectedMuscle!!] >= '2')
            val matchesEquip = selectedEquipment == null || ex.equipment.contains(selectedEquipment)
            val matchesCustom = !onlyCustom || ex.id >= 10000
            matchesQuery && matchesMuscle && matchesEquip && matchesCustom
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = GymCardBg
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$modalTitle (${filtered.size})",
                        fontWeight = FontWeight.ExtraBold,
                        color = GymOrange,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GymTextPrimary)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Prominent button to create a missing gym machine/exercise directly inside any routine!
                Button(
                    onClick = { showCreateCustomModal = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GymOrange.copy(alpha = 0.16f),
                        contentColor = GymOrange
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Crear Ejercicio / Máquina Propia y Usar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar ejercicio o máquina...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedMuscle == null && !onlyCustom,
                        onClick = {
                            selectedMuscle = null
                            onlyCustom = false
                        },
                        label = { Text("Todos", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = onlyCustom,
                        onClick = { onlyCustom = !onlyCustom },
                        label = { Text("★ Mis Ejercicios", fontSize = 11.sp) }
                    )
                    MuscleCatalog.ALL_MUSCLES.forEach { m ->
                        FilterChip(
                            selected = selectedMuscle == m.id,
                            onClick = { selectedMuscle = if (selectedMuscle == m.id) null else m.id },
                            label = { Text(m.nameEs, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedEquipment == null,
                        onClick = { selectedEquipment = null },
                        label = { Text("Todo Equipamiento", fontSize = 11.sp) }
                    )
                    equipmentOptions.forEach { eq ->
                        FilterChip(
                            selected = selectedEquipment == eq,
                            onClick = { selectedEquipment = if (selectedEquipment == eq) null else eq },
                            label = { Text(eq, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { ex ->
                        val primaryMuscles = (0..16)
                            .filter { it < ex.muscles.length && ex.muscles[it] >= '2' }
                            .joinToString(", ") { MuscleCatalog.getName(it) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GymSurfaceElevated, RoundedCornerShape(12.dp))
                                .clickable { onSelectExercise(ex) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedExerciseGif(
                                gifAssetPath = ex.gifAssetPath,
                                contentDescription = ex.name,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ex.name,
                                        color = GymTextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    if (ex.id >= 10000) {
                                        Spacer(Modifier.width(6.dp))
                                        Text("★ Propio", color = GymOrange, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                if (primaryMuscles.isNotBlank()) {
                                    Text(primaryMuscles, color = GymOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(ex.equipment.joinToString(" • "), color = GymTextSecondary, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = GymOrange)
                        }
                    }
                }
            }
        }
    }

    if (showCreateCustomModal) {
        CreateOrEditCustomExerciseDialog(
            initialExercise = null,
            onDismiss = { showCreateCustomModal = false },
            onSave = { existingId, name, eq, prim, sec, unit, notes ->
                viewModel.saveCustomExercise(existingId, name, eq, prim, sec, unit, notes) { createdEx ->
                    showCreateCustomModal = false
                    onSelectExercise(createdEx)
                }
            }
        )
    }
}

@Composable
fun EditWorkoutExerciseDialog(
    exercise: WorkoutExerciseEntity,
    exerciseName: String = "Ejercicio",
    onDismiss: () -> Unit,
    onRequestReplace: (() -> Unit)? = null,
    onSave: (WorkoutExerciseEntity) -> Unit
) {
    val initialSets = exercise.setsCount.coerceIn(1, 10)
    val parsedReps = remember(exercise) {
        val raw = exercise.repsPerSetCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        List(initialSets) { i -> raw.getOrElse(i) { raw.lastOrNull() ?: 10 }.toString() }
    }
    val parsedLoads = remember(exercise) {
        val raw = exercise.loadsPerSetCsv.split(",").mapNotNull { it.trim().toFloatOrNull() }
        List(initialSets) { i ->
            val v = raw.getOrElse(i) { raw.lastOrNull() ?: 20f }
            if (v % 1f == 0f) v.toInt().toString() else v.toString()
        }
    }

    val repsList = remember { mutableStateListOf(*parsedReps.toTypedArray()) }
    val loadsList = remember { mutableStateListOf(*parsedLoads.toTypedArray()) }
    var restSeconds by remember { mutableIntStateOf(exercise.restSeconds.coerceIn(15, 600)) }
    var isBiSet by remember { mutableStateOf(exercise.isBiSet) }
    var replicatedBanner by remember { mutableStateOf(false) }

    val unitLabel = when (exercise.unitType) {
        2 -> "Seg"
        3 -> "Min"
        else -> "Reps"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = GymCardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = exerciseName,
                    color = GymOrange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )

                if (onRequestReplace != null) {
                    OutlinedButton(
                        onClick = onRequestReplace,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = GymOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reemplazar por otro ejercicio", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Series Count + Replicate Set 1 row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(
                            onClick = {
                                if (repsList.size > 1) {
                                    repsList.removeAt(repsList.lastIndex)
                                    loadsList.removeAt(loadsList.lastIndex)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Menos series", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = " ${repsList.size} Series ",
                            color = GymTextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        FilledTonalIconButton(
                            onClick = {
                                if (repsList.size < 10) {
                                    repsList.add(repsList.lastOrNull() ?: "10")
                                    loadsList.add(loadsList.lastOrNull() ?: "20")
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Más series", modifier = Modifier.size(16.dp))
                        }
                    }

                    // One-tap Replicate Set 1 button
                    if (repsList.size > 1) {
                        FilledTonalButton(
                            onClick = {
                                val firstR = repsList.firstOrNull() ?: "10"
                                val firstL = loadsList.firstOrNull() ?: "20"
                                for (i in 1 until repsList.size) {
                                    repsList[i] = firstR
                                    loadsList[i] = firstL
                                }
                                replicatedBanner = true
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = GymOrange.copy(alpha = 0.16f),
                                contentColor = GymOrange
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Replicar S1", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                if (replicatedBanner) {
                    Text(
                        text = "✓ Serie 1 copiada al resto de las series",
                        color = FatigueGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Per-set rows
                repsList.forEachIndexed { idx, repVal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GymSurfaceElevated, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "S${idx + 1}",
                            color = GymOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.width(28.dp)
                        )
                        OutlinedTextField(
                            value = repVal,
                            onValueChange = { repsList[idx] = it },
                            label = { Text(unitLabel, fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        if (exercise.unitType == 0) {
                            OutlinedTextField(
                                value = loadsList[idx],
                                onValueChange = { loadsList[idx] = it },
                                label = { Text("Carga (kg)", fontSize = 10.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Rest selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Descanso:", color = GymTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { restSeconds = (restSeconds - 15).coerceAtLeast(15) }) {
                            Text("-15s", color = GymOrange, fontWeight = FontWeight.Bold)
                        }
                        Text("${restSeconds}s", color = GymTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        TextButton(onClick = { restSeconds = (restSeconds + 15).coerceAtMost(600) }) {
                            Text("+15s", color = GymOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isBiSet = !isBiSet }
                ) {
                    Checkbox(checked = isBiSet, onCheckedChange = { isBiSet = it })
                    Text(
                        "Agrupar en Bi-Serie / Superserie con el anterior",
                        color = GymTextPrimary,
                        fontSize = 12.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = GymTextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                exercise.copy(
                                    setsCount = repsList.size,
                                    repsPerSetCsv = repsList.joinToString(",") { it.trim().ifBlank { "10" } },
                                    loadsPerSetCsv = loadsList.joinToString(",") { it.trim().ifBlank { "0" } },
                                    restSeconds = restSeconds,
                                    isBiSet = isBiSet
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
