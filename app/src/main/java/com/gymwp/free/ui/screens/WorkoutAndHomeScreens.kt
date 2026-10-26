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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gymwp.free.data.*
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.domain.WorkoutMetricsCalculator
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AnimatedExerciseGif
import com.gymwp.free.ui.components.ExerciseDetailDialog
import com.gymwp.free.ui.components.GoogleDriveSyncDialog
import com.gymwp.free.ui.components.UserSwitcherDialog
import com.gymwp.free.ui.theme.*

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
    val favorites by viewModel.favoriteIds.collectAsState()
    val allFinishedEx by viewModel.allFinishedExercises.collectAsState()
    val fatigueList by viewModel.muscleFatigueList.collectAsState()
    val driveSyncState by viewModel.driveSyncState.collectAsState()

    var selectedPlanId by remember(plans) {
        mutableStateOf(plans.firstOrNull { it.isActive }?.id ?: plans.firstOrNull()?.id)
    }
    var showSmartWizard by remember { mutableStateOf(false) }
    var showFreestyleDialog by remember { mutableStateOf(false) }
    var showDriveSyncDialog by remember { mutableStateOf(false) }
    var showUserSwitcherDialog by remember { mutableStateOf(false) }
    var showNewPlanDialog by remember { mutableStateOf(false) }
    var showAddDayDialogForPlan by remember { mutableStateOf<Long?>(null) }
    var showAddExerciseForDay by remember { mutableStateOf<Long?>(null) }
    var editingWorkoutExercise by remember { mutableStateOf<WorkoutExerciseEntity?>(null) }
    var inspectingCatalogExercise by remember { mutableStateOf<CatalogExercise?>(null) }

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
                    text = "Chukaros's GymPlanner",
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showDriveSyncDialog = true }) {
                    val isLinked = !driveSyncState.linkedUri.isNullOrBlank()
                    Icon(
                        imageVector = if (isLinked) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                        contentDescription = "Google Drive Auto-Sync",
                        tint = if (isLinked) FatigueGreen else GymOrange
                    )
                }
                FilledTonalButton(
                    onClick = { showFreestyleDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = GymSurfaceElevated)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = GymOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Libre", color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = { showSmartWizard = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Plan IA", fontSize = 12.sp)
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
                        selectedLabelColor = Color.White,
                        containerColor = GymCardBg,
                        labelColor = Color.LightGray
                    )
                )
            }
            OutlinedButton(
                onClick = { showNewPlanDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Crear Plan", fontSize = 12.sp)
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
                        "¡Bienvenido a Chukaros's GymPlanner!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Genera tu Plan Personalizado de 3 Fases (Adaptación, Hipertrofia y Fuerza) o explora el catálogo de 57 rutinas especializadas.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showSmartWizard = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                        ) {
                            Text("Generar Plan 3 Fases")
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
                                        color = Color.White
                                    )
                                    if (activePlan.description.isNotBlank()) {
                                        Text(
                                            text = activePlan.description,
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.deleteWorkoutPlan(activePlan.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar Plan", tint = Color.Gray)
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
                                    color = Color.LightGray,
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
                    val dayExercises = allExercises.filter { it.workoutDayId == day.id }
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
                            // Colored Day Banner (GymWP signature style)
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
                                    // Cycle Day Color button
                                    IconButton(
                                        onClick = {
                                            viewModel.updateWorkoutDayColorOrName(
                                                day.copy(colorIndex = (day.colorIndex + 1) % DayPalette.size)
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = "Cambiar Color", tint = Color.White)
                                    }
                                    IconButton(onClick = { viewModel.deleteWorkoutDay(day.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar Día", tint = Color.White)
                                    }
                                }
                            }

                            // Exercises inside this Day
                            Column(Modifier.padding(12.dp)) {
                                dayExercises.forEachIndexed { idx, wEx ->
                                    val catEx = viewModel.getCatalogExercise(wEx.exerciseId)
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
                                            .padding(vertical = 6.dp)
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
                                                modifier = Modifier.size(56.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = exName,
                                                color = Color.White,
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
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        IconButton(onClick = { editingWorkoutExercise = wEx }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Editar series/carga", tint = Color.LightGray)
                                        }
                                        IconButton(onClick = { viewModel.deleteWorkoutExercise(wEx.id) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Quitar", tint = Color.Gray)
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
                                            if (dayExercises.isNotEmpty()) {
                                                onStartLiveWorkout(activePlan, day, dayExercises)
                                            }
                                        },
                                        enabled = dayExercises.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(containerColor = dayColor)
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
                    val createdExs = viewModel.allWorkoutExercises.value.filter { it.workoutDayId == createdDayId }
                    if (createdDay != null && createdExs.isNotEmpty()) {
                        onStartLiveWorkout(null, createdDay, createdExs)
                    }
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
            title = { Text("Nuevo Plan de Entrenamiento") },
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
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPlanDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Dialog: Add Day to Plan
    showAddDayDialogForPlan?.let { targetPlanId ->
        var dayName by remember { mutableStateOf("") }
        var chosenColor by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { showAddDayDialogForPlan = null },
            title = { Text("Añadir Día de Entrenamiento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dayName,
                        onValueChange = { dayName = it },
                        label = { Text("Nombre (ej. Día A - Pecho y Tríceps)") }
                    )
                    Text("Color de la tarjeta:", fontSize = 13.sp, color = Color.LightGray)
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
                                        color = Color.White,
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
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                ) {
                    Text("Añadir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDayDialogForPlan = null }) { Text("Cancelar") }
            }
        )
    }

    // Dialog: Exercise Picker to add to a Day
    showAddExerciseForDay?.let { targetDayId ->
        ExercisePickerModal(
            catalog = viewModel.catalogExercises,
            onDismiss = { showAddExerciseForDay = null },
            onSelectExercise = { ex ->
                viewModel.addExerciseToDay(targetDayId, ex.id)
                showAddExerciseForDay = null
            }
        )
    }

    // Dialog: Edit Sets / Reps / Loads / BiSet for a WorkoutExerciseEntity
    editingWorkoutExercise?.let { wEx ->
        EditWorkoutExerciseDialog(
            exercise = wEx,
            onDismiss = { editingWorkoutExercise = null },
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
            onDismiss = { inspectingCatalogExercise = null }
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
                    color = Color.LightGray
                )

                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Nombre del Usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Gender
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

                Text("Objetivo Principal:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val goals = listOf("Ganar Masa Muscular", "Perder Peso / Definición", "Ganar Fuerza Máxima", "Salud y Resistencia")
                goals.forEachIndexed { idx, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { goal = idx }
                    ) {
                        RadioButton(selected = goal == idx, onClick = { goal = idx })
                        Text(label, fontSize = 13.sp)
                    }
                }

                Text("Nivel de Experiencia:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Principiante", "Intermedio", "Avanzado").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = level == idx,
                            onClick = { level = idx },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Text("Equipamiento / Lugar:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Gimnasio", "En Casa", "Peso Corporal").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = place == idx,
                            onClick = { place = idx },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Text("Días por semana: $daysPerWeek días", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
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
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                    ) {
                        Text("Generar Mis 3 Fases")
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
                    color = Color.LightGray
                )
                Text("Duración objetivo: $durationMinutes minutos (~${(durationMinutes / 6).coerceAtLeast(3)} ejercicios)", fontWeight = FontWeight.Bold)
                Slider(
                    value = durationMinutes.toFloat(),
                    onValueChange = { durationMinutes = it.toInt() },
                    valueRange = 20f..90f,
                    steps = 6,
                    colors = SliderDefaults.colors(thumbColor = GymOrange, activeTrackColor = GymOrange)
                )
                Text("Músculos a entrenar (Opcional):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
            ) {
                Text("Iniciar Ahora")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ExercisePickerModal(
    catalog: List<CatalogExercise>,
    onDismiss: () -> Unit,
    onSelectExercise: (CatalogExercise) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedMuscle by remember { mutableStateOf<Int?>(null) }

    val filtered = remember(catalog, search, selectedMuscle) {
        catalog.filter { ex ->
            val matchesQuery = search.isBlank() || ex.name.contains(search, ignoreCase = true)
            val matchesMuscle = selectedMuscle == null || (selectedMuscle!! < ex.muscles.length && ex.muscles[selectedMuscle!!] >= '2')
            matchesQuery && matchesMuscle
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(18.dp),
            color = GymCardBg
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Seleccionar Ejercicio (${filtered.size})", fontWeight = FontWeight.Bold, color = GymOrange)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar entre los 353 ejercicios...") },
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
                        selected = selectedMuscle == null,
                        onClick = { selectedMuscle = null },
                        label = { Text("Todos") }
                    )
                    MuscleCatalog.ALL_MUSCLES.take(16).forEach { m ->
                        FilterChip(
                            selected = selectedMuscle == m.id,
                            onClick = { selectedMuscle = if (selectedMuscle == m.id) null else m.id },
                            label = { Text(m.nameEs, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GymSurfaceElevated, RoundedCornerShape(10.dp))
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
                                Text(ex.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(ex.equipment.joinToString(" • "), color = GymOrange, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.Add, contentDescription = null, tint = GymOrange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditWorkoutExerciseDialog(
    exercise: WorkoutExerciseEntity,
    onDismiss: () -> Unit,
    onSave: (WorkoutExerciseEntity) -> Unit
) {
    var setsText by remember { mutableStateOf(exercise.setsCount.toString()) }
    var repsCsv by remember { mutableStateOf(exercise.repsPerSetCsv) }
    var loadsCsv by remember { mutableStateOf(exercise.loadsPerSetCsv) }
    var restText by remember { mutableStateOf(exercise.restSeconds.toString()) }
    var isBiSet by remember { mutableStateOf(exercise.isBiSet) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Series, Reps y Carga") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = setsText,
                    onValueChange = { setsText = it },
                    label = { Text("Número de Series") }
                )
                OutlinedTextField(
                    value = repsCsv,
                    onValueChange = { repsCsv = it },
                    label = { Text("Repeticiones por serie (ej. 12,10,10,8)") }
                )
                OutlinedTextField(
                    value = loadsCsv,
                    onValueChange = { loadsCsv = it },
                    label = { Text("Cargas en kg por serie (ej. 20,22,25,25)") }
                )
                OutlinedTextField(
                    value = restText,
                    onValueChange = { restText = it },
                    label = { Text("Descanso entre series (segundos)") }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isBiSet = !isBiSet }
                ) {
                    Checkbox(checked = isBiSet, onCheckedChange = { isBiSet = it })
                    Text("Agrupar en Bi-Serie / Superserie con el anterior", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sets = (setsText.toIntOrNull() ?: exercise.setsCount).coerceIn(1, 10)
                    onSave(
                        exercise.copy(
                            setsCount = sets,
                            repsPerSetCsv = repsCsv,
                            loadsPerSetCsv = loadsCsv,
                            restSeconds = (restText.toIntOrNull() ?: 60).coerceIn(10, 600),
                            isBiSet = isBiSet
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
