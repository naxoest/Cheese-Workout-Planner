package com.gymwp.free.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gymwp.free.data.*
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.domain.OverloadSuggestion
import com.gymwp.free.domain.ProgressiveOverloadAdvisor
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
import java.util.Locale

@Composable
fun HomeWorkoutsScreen(
    viewModel: GymWpViewModel,
    onStartLiveWorkout: (WorkoutPlanEntity?, WorkoutDayEntity, List<WorkoutExerciseEntity>) -> Unit,
    onNavigateToExplore: () -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
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

    var selectedPlanId by remember(plans) {
        mutableStateOf(plans.firstOrNull { it.isActive }?.id ?: plans.firstOrNull()?.id)
    }
    var showSmartWizard by remember { mutableStateOf(false) }
    var showFreestyleDialog by remember { mutableStateOf(false) }
    var showDriveSyncDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showUserSwitcherDialog by remember { mutableStateOf(false) }
    var showNewPlanDialog by remember { mutableStateOf(false) }
    var showExportRoutineDialog by remember { mutableStateOf<WorkoutPlanEntity?>(null) }
    var showImportRoutineDialog by remember { mutableStateOf(false) }
    var routinePortMessage by remember { mutableStateOf<String?>(null) }
    var pendingExportPlanId by remember { mutableStateOf<Long?>(null) }
    var showAddDayDialogForPlan by remember { mutableStateOf<Long?>(null) }
    var editingDayTarget by remember { mutableStateOf<WorkoutDayEntity?>(null) }
    val collapsedDayIds = remember { mutableStateListOf<Long>() }
    var showAddExerciseForDay by remember { mutableStateOf<Long?>(null) }
    var reorderingDayId by remember { mutableStateOf<Long?>(null) }
    var addingAlternativeForWEx by remember { mutableStateOf<Pair<WorkoutExerciseEntity, CatalogExercise?>?>(null) }
    var replacingWorkoutExercise by remember { mutableStateOf<Pair<WorkoutExerciseEntity, String>?>(null) }
    var editingWorkoutExercise by remember { mutableStateOf<WorkoutExerciseEntity?>(null) }
    var inspectingCatalogExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var editingCustomCatalogExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var customExercisePendingDelete by remember { mutableStateOf<CatalogExercise?>(null) }
    var planPendingDelete by remember { mutableStateOf<WorkoutPlanEntity?>(null) }
    var dayPendingDelete by remember { mutableStateOf<WorkoutDayEntity?>(null) }
    var exercisePendingDelete by remember { mutableStateOf<Pair<WorkoutExerciseEntity, String>?>(null) }

    val exportRoutineFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { targetUri ->
        val planIdToExport = pendingExportPlanId
        pendingExportPlanId = null
        if (targetUri != null && planIdToExport != null) {
            viewModel.exportRoutineToUri(planIdToExport, targetUri) { res ->
                routinePortMessage = res.fold(
                    onSuccess = { "Rutina exportada correctamente como archivo." },
                    onFailure = { "Error al exportar: ${it.message}" }
                )
            }
        }
    }

    val importRoutineFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {
            viewModel.importRoutineFromUri(sourceUri) { res ->
                res.onSuccess { summary ->
                    selectedPlanId = summary.planId
                    showImportRoutineDialog = false
                    routinePortMessage = "Rutina \"${summary.planName}\" importada (${summary.daysCount} días, ${summary.exercisesCount} ejercicios)."
                }.onFailure { err ->
                    routinePortMessage = "No se pudo importar el archivo: ${err.message}"
                }
            }
        }
    }

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
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = GymOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = { showDriveSyncDialog = true },
                    modifier = Modifier.size(34.dp)
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
                    onClick = {
                        selectedPlanId = plan.id
                        viewModel.setActiveWorkoutPlan(plan.id)
                    },
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
            OutlinedButton(
                onClick = { showImportRoutineDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Importar Rutina", color = GymOrange, fontSize = 12.sp)
            }
        }

        routinePortMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GymSurfaceElevated)
                    .border(1.dp, GymBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = msg,
                    color = GymTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { routinePortMessage = null },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar aviso", tint = GymTextSecondary, modifier = Modifier.size(16.dp))
                }
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showImportRoutineDialog = true }) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importar Rutina", color = GymOrange)
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
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
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
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showExportRoutineDialog = activePlan },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = GymTextPrimary, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Exportar Rutina", color = GymTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = { showImportRoutineDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = GymTextPrimary, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Importar Rutina", color = GymTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Modern Routine Day Showcase Cards
                itemsIndexed(daysForActivePlan, key = { _, d -> d.id }) { dayIdx, day ->
                    val dayExercises = remember(allExercises, day.id) {
                        allExercises.filter { it.workoutDayId == day.id }.sortedBy { it.orderIndex }
                    }
                    val totalSets = dayExercises.sumOf { it.setsCount }
                    val (minDur, maxDur) = WorkoutMetricsCalculator.estimateDurationWindowMinutes(totalSets, dayExercises.size)
                    val estKcal = WorkoutMetricsCalculator.calculateEstimatedCalories(totalSets, profile?.weightKg ?: 75f)
                    val isCollapsed = collapsedDayIds.contains(day.id)
                    val isReorderingThisDay = !isCollapsed && reorderingDayId == day.id && dayExercises.size > 1

                    val targetedMuscles = remember(dayExercises, allCatalog) {
                        val counts = linkedMapOf<Int, Int>()
                        dayExercises.forEach { wEx ->
                            val mStr = viewModel.getCatalogExercise(wEx.exerciseId)?.muscles ?: return@forEach
                            for (mId in 0..15) {
                                val ch = mStr.getOrNull(mId) ?: '0'
                                if (ch >= '2') {
                                    counts[mId] = (counts[mId] ?: 0) + (if (ch == '3') 3 else 1)
                                }
                            }
                        }
                        counts.entries
                            .sortedByDescending { it.value }
                            .take(4)
                            .map { (mId, _) ->
                                MuscleCatalog.getName(mId).substringBefore(" (")
                            }
                    }

                    val cleanDayName = remember(day.name) {
                        WorkoutDayScheduleCodec.getCleanName(day.name)
                    }
                    val assignedDays = remember(day.name) {
                        WorkoutDayScheduleCodec.getAssignedDays(day.name)
                    }
                    val assignedDaysSummary = remember(assignedDays) {
                        if (assignedDays.isNotEmpty()) WorkoutDayScheduleCodec.formatDaysSummary(assignedDays) else ""
                    }
                    val isScheduledForToday = remember(assignedDays) {
                        WorkoutDayScheduleCodec.isToday(assignedDays)
                    }

                    val lastTrainedLabel = remember(finishedWorkouts, cleanDayName) {
                        val lastSession = finishedWorkouts
                            .filter { it.dayName.equals(cleanDayName, ignoreCase = true) || it.dayName.equals(day.name, ignoreCase = true) }
                            .maxByOrNull { it.timestampMillis }
                        if (lastSession == null) {
                            "Listo para hoy"
                        } else {
                            val daysAgo = ((System.currentTimeMillis() - lastSession.timestampMillis).coerceAtLeast(0L) / 86_400_000L).toInt()
                            when {
                                daysAgo <= 0 -> "Hecho hoy"
                                daysAgo == 1 -> "Hace 1 día"
                                else -> "Hace $daysAgo días"
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = GymCardBg),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = GymBorder,
                                shape = RoundedCornerShape(22.dp)
                            )
                    ) {
                        Column {
                            // 1. Clean Neutral Day Header
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GymSurfaceElevated)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(GymCardBg)
                                                .border(1.dp, GymBorder, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "DÍA ${dayIdx + 1}",
                                                color = GymTextPrimary,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(GymCardBg)
                                                .border(1.dp, GymBorder, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = lastTrainedLabel,
                                                color = GymTextSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (assignedDaysSummary.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isScheduledForToday) GymOrange.copy(alpha = 0.18f) else GymCardBg)
                                                    .border(
                                                        1.dp,
                                                        if (isScheduledForToday) GymOrange.copy(alpha = 0.5f) else GymBorder,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = if (isScheduledForToday) "Hoy • $assignedDaysSummary" else assignedDaysSummary,
                                                    color = if (isScheduledForToday) GymOrange else GymTextSecondary,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (dayExercises.size > 1 && !isCollapsed) {
                                            Surface(
                                                onClick = {
                                                    reorderingDayId = if (isReorderingThisDay) null else day.id
                                                },
                                                shape = RoundedCornerShape(9.dp),
                                                color = if (isReorderingThisDay) GymOrange else GymCardBg
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isReorderingThisDay) Icons.Default.Check else Icons.Default.SwapVert,
                                                        contentDescription = if (isReorderingThisDay) "Terminar orden" else "Ordenar ejercicios",
                                                        tint = if (isReorderingThisDay) GymOnPrimary else GymTextSecondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = if (isReorderingThisDay) "Listo" else "Ordenar",
                                                        color = if (isReorderingThisDay) GymOnPrimary else GymTextSecondary,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(GymCardBg)
                                                .clickable { editingDayTarget = day },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Editar nombre o programación del día",
                                                tint = GymTextSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(GymCardBg)
                                                .clickable { dayPendingDelete = day },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Eliminar día",
                                                tint = GymTextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(GymCardBg)
                                                .clickable {
                                                    if (isCollapsed) collapsedDayIds.remove(day.id)
                                                    else {
                                                        if (reorderingDayId == day.id) reorderingDayId = null
                                                        collapsedDayIds.add(day.id)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                                contentDescription = if (isCollapsed) "Ver ejercicios" else "Contraer día",
                                                tint = GymTextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = cleanDayName,
                                    color = GymTextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.clickable { editingDayTarget = day }
                                )

                                Spacer(Modifier.height(8.dp))

                                // Frosted KPI Strip inside Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DayHeaderKpiPill("${dayExercises.size} ejerc.", Modifier.weight(1f))
                                    DayHeaderKpiPill("$totalSets series", Modifier.weight(1f))
                                    DayHeaderKpiPill("$minDur-$maxDur m", Modifier.weight(1f))
                                    DayHeaderKpiPill("~$estKcal kcal", Modifier.weight(1f))
                                }
                            }

                            // 2. Targeted Muscle Focus Strip
                            if (targetedMuscles.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(GymCardBg.copy(alpha = 0.5f))
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Enfoque:",
                                        color = GymTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        targetedMuscles.forEach { mLabel ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(GymSurfaceElevated)
                                                    .border(1.dp, GymBorder, RoundedCornerShape(7.dp))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = mLabel,
                                                    color = GymTextPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Exercises inside this Day (when expanded)
                            Column(Modifier.padding(12.dp)) {
                                if (!isCollapsed) {
                                    if (isReorderingThisDay) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .background(GymOrange.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SwapVert,
                                                contentDescription = null,
                                                tint = GymOrange,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Usa ↑ / ↓ para cambiar el orden y toca Listo al terminar.",
                                                color = GymTextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (dayExercises.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(GymSurfaceElevated.copy(alpha = 0.6f))
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Este día aún no tiene ejercicios. Toca \"+ Ejercicio\" abajo para armar tu sesión.",
                                                color = GymTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    dayExercises.forEachIndexed { idx, wEx ->
                                        val catEx = remember(allCatalog, wEx.exerciseId) {
                                            viewModel.getCatalogExercise(wEx.exerciseId)
                                        }
                                        val exName = catEx?.name ?: wEx.customExerciseName.ifBlank { "Ejercicio #${wEx.exerciseId}" }
                                        val exPrimaryMuscle = remember(catEx?.muscles) {
                                            if (catEx == null) ""
                                            else (0..15)
                                                .filter { it < catEx.muscles.length && catEx.muscles[it] == '3' }
                                                .joinToString(" • ") { MuscleCatalog.getName(it).substringBefore(" (") }
                                        }

                                        if (wEx.isBiSet && idx > 0) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(GymCardBg)
                                                        .border(1.dp, GymBorder, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "SUPERSERIE CON EL ANTERIOR",
                                                        color = GymTextSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        val repsTokens = remember(wEx.repsPerSetCsv) {
                                            wEx.repsPerSetCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                        }
                                        val loadsTokens = remember(wEx.loadsPerSetCsv) {
                                            wEx.loadsPerSetCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                        }
                                        val unitSuffix = when (wEx.unitType) {
                                            2 -> "seg"
                                            3 -> "min"
                                            else -> "reps"
                                        }
                                        val formattedSeriesText = if (repsTokens.distinct().size == 1 && repsTokens.isNotEmpty()) {
                                            "${wEx.setsCount} × ${repsTokens.first()} $unitSuffix"
                                        } else {
                                            "${wEx.setsCount} series • ${repsTokens.joinToString("-")} $unitSuffix"
                                        }
                                        val formattedLoadsText = if (loadsTokens.distinct().size == 1 && loadsTokens.isNotEmpty()) {
                                            "${loadsTokens.first()} kg"
                                        } else {
                                            "${loadsTokens.joinToString("-")} kg"
                                        }

                                        val overloadSuggestion = remember(allFinishedEx, wEx.exerciseId, wEx.unitType) {
                                            ProgressiveOverloadAdvisor.computeSuggestion(
                                                unitType = wEx.unitType,
                                                historyForExercise = allFinishedEx.filter { it.exerciseId == wEx.exerciseId }
                                            )
                                        }
                                        val isOverloadApplied = remember(overloadSuggestion, loadsTokens, repsTokens) {
                                            overloadSuggestion != null && ProgressiveOverloadAdvisor.isAlreadyApplied(
                                                overloadSuggestion,
                                                loadsTokens,
                                                repsTokens
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 5.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(GymSurfaceElevated)
                                                .border(
                                                    width = 1.dp,
                                                    color = GymBorder,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .let { mod ->
                                                    if (!isReorderingThisDay) mod.clickable { editingWorkoutExercise = wEx } else mod
                                                }
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Step Number Badge (01, 02...)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(GymCardBg),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "%02d".format(idx + 1),
                                                            color = GymTextSecondary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Spacer(Modifier.width(8.dp))

                                                    AnimatedExerciseGif(
                                                        gifAssetPath = catEx?.gifAssetPath.orEmpty(),
                                                        contentDescription = exName,
                                                        modifier = Modifier
                                                            .size(56.dp)
                                                            .clickable {
                                                                if (catEx != null) inspectingCatalogExercise = catEx
                                                                else if (!isReorderingThisDay) editingWorkoutExercise = wEx
                                                            }
                                                    )
                                                    Spacer(Modifier.width(10.dp))

                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            text = exName,
                                                            color = GymTextPrimary,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 14.sp,
                                                            lineHeight = 18.sp
                                                        )
                                                        if (exPrimaryMuscle.isNotBlank()) {
                                                            Text(
                                                                text = exPrimaryMuscle,
                                                                color = GymTextSecondary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                        Spacer(Modifier.height(4.dp))
                                                        // Structured Metric Badges
                                                        Row(
                                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            DayExerciseMetricBadge(
                                                                text = formattedSeriesText,
                                                                bgColor = GymCardBg,
                                                                textColor = GymTextPrimary
                                                            )
                                                            if (wEx.unitType == 0) {
                                                                DayExerciseMetricBadge(
                                                                    text = formattedLoadsText,
                                                                    bgColor = GymCardBg,
                                                                    textColor = GymTextPrimary
                                                                )
                                                            }
                                                            DayExerciseMetricBadge(
                                                                text = "${wEx.restSeconds}s",
                                                                bgColor = GymCardBg,
                                                                textColor = GymTextSecondary
                                                            )
                                                        }
                                                    }

                                                    if (isReorderingThisDay) {
                                                        val canUp = idx > 0
                                                        val canDown = idx < dayExercises.lastIndex
                                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(34.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(GymCardBg)
                                                                    .let { m ->
                                                                        if (canUp) {
                                                                            m.clickable {
                                                                                viewModel.moveWorkoutExercise(day.id, wEx.id, moveUp = true)
                                                                            }
                                                                        } else m
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.KeyboardArrowUp,
                                                                    contentDescription = "Subir ejercicio",
                                                                    tint = if (canUp) GymTextPrimary else GymTextSecondary.copy(alpha = 0.35f),
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(34.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .background(GymCardBg)
                                                                    .let { m ->
                                                                        if (canDown) {
                                                                            m.clickable {
                                                                                viewModel.moveWorkoutExercise(day.id, wEx.id, moveUp = false)
                                                                            }
                                                                        } else m
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.KeyboardArrowDown,
                                                                    contentDescription = "Bajar ejercicio",
                                                                    tint = if (canDown) GymTextPrimary else GymTextSecondary.copy(alpha = 0.35f),
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                            }
                                                        }
                                                    } else {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(30.dp)
                                                                        .clip(CircleShape)
                                                                        .background(GymCardBg)
                                                                        .clickable { editingWorkoutExercise = wEx },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Tune,
                                                                        contentDescription = "Editar series/carga",
                                                                        tint = GymTextSecondary,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(30.dp)
                                                                        .clip(CircleShape)
                                                                        .background(GymCardBg)
                                                                        .clickable { replacingWorkoutExercise = wEx to exName },
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.SwapHoriz,
                                                                        contentDescription = "Reemplazar ejercicio",
                                                                        tint = GymTextSecondary,
                                                                        modifier = Modifier.size(16.dp)
                                                                    )
                                                                }
                                                            }
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(28.dp)
                                                                    .clip(CircleShape)
                                                                    .clickable { exercisePendingDelete = wEx to exName },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.DeleteOutline,
                                                                    contentDescription = "Eliminar ejercicio",
                                                                    tint = GymTextSecondary.copy(alpha = 0.7f),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // Parches (Secundario / Terciario / Opcional) — Full Width without clipping!
                                                if (!isReorderingThisDay) {
                                                    val alternatives = remember(wEx.notes) {
                                                        WorkoutExerciseAlternativesCodec.decodeAlternatives(wEx.notes)
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                    if (alternatives.isNotEmpty()) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            alternatives.forEachIndexed { altIdx, altSpec ->
                                                                val altCat = remember(allCatalog, altSpec.exerciseId) {
                                                                    viewModel.getCatalogExercise(altSpec.exerciseId)
                                                                }
                                                                val altFullName = altCat?.name
                                                                    ?: altSpec.customExerciseName.ifBlank { "Ej. #${altSpec.exerciseId}" }
                                                                Row(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                        .background(GymCardBg)
                                                                        .border(
                                                                            width = 1.dp,
                                                                            color = GymBorder,
                                                                            shape = RoundedCornerShape(8.dp)
                                                                        )
                                                                        .clickable { editingWorkoutExercise = wEx }
                                                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.CallSplit,
                                                                        contentDescription = null,
                                                                        tint = GymTextSecondary,
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                    Text(
                                                                        text = "${WorkoutExerciseAlternativesCodec.roleShortChipLabel(altIdx)}:",
                                                                        color = GymTextSecondary,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                    Text(
                                                                        text = altFullName,
                                                                        color = GymTextPrimary,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        lineHeight = 14.sp,
                                                                        modifier = Modifier.weight(1f)
                                                                    )
                                                                }
                                                            }
                                                            if (alternatives.size < 4) {
                                                                Row(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                        .clickable { addingAlternativeForWEx = wEx to catEx }
                                                                        .padding(vertical = 2.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                ) {
                                                                    Icon(Icons.Default.Add, contentDescription = null, tint = GymTextSecondary, modifier = Modifier.size(13.dp))
                                                                    Text(
                                                                        text = "Añadir otro ejercicio parche",
                                                                        color = GymTextSecondary,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        Row(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable { addingAlternativeForWEx = wEx to catEx }
                                                                .padding(vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.CallSplit,
                                                                contentDescription = null,
                                                                tint = GymTextSecondary,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Text(
                                                                text = "+ Añadir ejercicio parche (si máquina está ocupada)",
                                                                color = GymTextSecondary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }
                                                }

                                                if (!isReorderingThisDay && overloadSuggestion != null) {
                                                    Spacer(Modifier.height(7.dp))
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                if (isOverloadApplied) FatigueGreen.copy(alpha = 0.13f)
                                                                else GymOrange.copy(alpha = 0.12f)
                                                            )
                                                            .padding(horizontal = 9.dp, vertical = 6.dp),
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
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Spacer(Modifier.width(5.dp))
                                                            Text(
                                                                text = overloadSuggestion.summaryText,
                                                                color = GymTextPrimary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                lineHeight = 14.sp
                                                            )
                                                        }
                                                        Spacer(Modifier.width(6.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                    if (isOverloadApplied) FatigueGreen.copy(alpha = 0.22f)
                                                                    else GymOrange
                                                                )
                                                                .clickable {
                                                                    if (!isOverloadApplied) {
                                                                        viewModel.applyOverloadSuggestionToWorkoutExercise(wEx, overloadSuggestion)
                                                                    }
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isOverloadApplied) "Meta activa" else overloadSuggestion.buttonLabel,
                                                                color = if (isOverloadApplied) FatigueGreen else GymOnPrimary,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(10.dp))
                                }

                                // 4. Prominent Action Footer
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            collapsedDayIds.remove(day.id)
                                            showAddExerciseForDay = day.id
                                        },
                                        shape = RoundedCornerShape(13.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = GymOrange, modifier = Modifier.size(17.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Ejercicio", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            reorderingDayId = null
                                            onStartLiveWorkout(activePlan, day, dayExercises)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
                                        shape = RoundedCornerShape(13.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Iniciar Entrenamiento", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
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

    // Dialog: Export Routine (contains all days & exercises)
    showExportRoutineDialog?.let { targetPlan ->
        val planDaysCount = remember(allDays, targetPlan.id) {
            allDays.count { it.workoutPlanId == targetPlan.id }
        }
        val planExCount = remember(allDays, allExercises, targetPlan.id) {
            val dayIds = allDays.filter { it.workoutPlanId == targetPlan.id }.map { it.id }.toSet()
            allExercises.count { it.workoutDayId in dayIds }
        }
        AlertDialog(
            onDismissRequest = { showExportRoutineDialog = null },
            title = {
                Text(
                    text = "Exportar Rutina",
                    color = GymTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Rutina: ${targetPlan.name}",
                        color = GymOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Incluye $planDaysCount días de entrenamiento y $planExCount ejercicios con sus series, cargas, descansos, programación semanal y ejercicios parche.",
                        color = GymTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val cleanFileName = "Rutina_${targetPlan.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.json"
                            pendingExportPlanId = targetPlan.id
                            showExportRoutineDialog = null
                            exportRoutineFileLauncher.launch(cleanFileName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guardar archivo (.json)", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.exportRoutineToJson(targetPlan.id) { res ->
                                res.onSuccess { jsonText ->
                                    showExportRoutineDialog = null
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Rutina Cheese WP - ${targetPlan.name}")
                                        putExtra(Intent.EXTRA_TEXT, jsonText)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Compartir rutina"))
                                }.onFailure { err ->
                                    routinePortMessage = "Error al exportar: ${err.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = GymTextPrimary, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir rutina", color = GymTextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.exportRoutineToJson(targetPlan.id) { res ->
                                res.onSuccess { jsonText ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Rutina Cheese WP", jsonText))
                                    showExportRoutineDialog = null
                                    routinePortMessage = "Código de la rutina \"${targetPlan.name}\" copiado al portapapeles."
                                }.onFailure { err ->
                                    routinePortMessage = "Error al copiar rutina: ${err.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GymTextPrimary, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar código al portapapeles", color = GymTextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportRoutineDialog = null }) {
                    Text("Cerrar", color = GymTextSecondary)
                }
            },
            containerColor = GymCardBg
        )
    }

    // Dialog: Import Routine (contains all days & exercises)
    if (showImportRoutineDialog) {
        var pastedRoutineJson by remember { mutableStateOf("") }
        var importErrorText by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showImportRoutineDialog = false },
            title = {
                Text(
                    text = "Importar Rutina",
                    color = GymTextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Importa una rutina completa con todos sus días de entrenamiento, programación semanal, ejercicios, series y cargas.",
                        color = GymTextSecondary,
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = {
                            importRoutineFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Seleccionar archivo (.json)", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = GymBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "O pega el código de la rutina:",
                            color = GymTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                if (clipText.isNotBlank()) {
                                    pastedRoutineJson = clipText
                                    importErrorText = null
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Pegar", color = GymOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = pastedRoutineJson,
                        onValueChange = {
                            pastedRoutineJson = it
                            importErrorText = null
                        },
                        label = { Text("Código JSON de la rutina") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    importErrorText?.let { err ->
                        Text(
                            text = err,
                            color = FatigueRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pastedRoutineJson.isBlank()) {
                            importErrorText = "Selecciona un archivo .json o pega el código de la rutina."
                        } else {
                            viewModel.importRoutineFromJson(pastedRoutineJson) { res ->
                                res.onSuccess { summary ->
                                    selectedPlanId = summary.planId
                                    showImportRoutineDialog = false
                                    routinePortMessage = "Rutina \"${summary.planName}\" importada (${summary.daysCount} días, ${summary.exercisesCount} ejercicios)."
                                }.onFailure { err ->
                                    importErrorText = "Formato inválido: ${err.message}"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                ) {
                    Text("Importar código", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportRoutineDialog = false }) {
                    Text("Cancelar", color = GymTextSecondary)
                }
            },
            containerColor = GymCardBg
        )
    }

    // Dialog: Add Day to Plan
    showAddDayDialogForPlan?.let { targetPlanId ->
        var dayName by remember { mutableStateOf("") }
        var selectedDays by remember { mutableStateOf(emptyList<Int>()) }
        AlertDialog(
            onDismissRequest = { showAddDayDialogForPlan = null },
            title = { Text("Añadir Día de Entrenamiento", color = GymTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = dayName,
                        onValueChange = { dayName = it },
                        label = { Text("Nombre (ej. Día A - Pecho y Tríceps)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Días de la semana programados:", fontSize = 13.sp, color = GymTextSecondary, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WorkoutDayScheduleCodec.DAY_LABELS.forEachIndexed { idx, shortLabel ->
                            val dayNum = idx + 1
                            val isSelected = dayNum in selectedDays
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) GymOrange else GymSurfaceElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) GymOrange else GymBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedDays = if (isSelected) selectedDays.filter { it != dayNum } else (selectedDays + dayNum).sorted()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortLabel,
                                    color = if (isSelected) GymOnPrimary else GymTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val encodedName = WorkoutDayScheduleCodec.encodeName(dayName.trim().ifBlank { "Nuevo Día" }, selectedDays)
                        viewModel.addWorkoutDay(targetPlanId, encodedName)
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

    // Dialog: Edit Day Name & Weekdays
    editingDayTarget?.let { targetDay ->
        var editedName by remember(targetDay.id) {
            mutableStateOf(WorkoutDayScheduleCodec.getCleanName(targetDay.name))
        }
        var selectedDays by remember(targetDay.id) {
            mutableStateOf(WorkoutDayScheduleCodec.getAssignedDays(targetDay.name))
        }
        AlertDialog(
            onDismissRequest = { editingDayTarget = null },
            title = { Text("Personalizar Día de Rutina", color = GymTextPrimary, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Nombre del día") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Días de la semana programados:", fontSize = 13.sp, color = GymTextSecondary, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        WorkoutDayScheduleCodec.DAY_LABELS.forEachIndexed { idx, shortLabel ->
                            val dayNum = idx + 1
                            val isSelected = dayNum in selectedDays
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) GymOrange else GymSurfaceElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) GymOrange else GymBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedDays = if (isSelected) selectedDays.filter { it != dayNum } else (selectedDays + dayNum).sorted()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortLabel,
                                    color = if (isSelected) GymOnPrimary else GymTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val encodedName = WorkoutDayScheduleCodec.encodeName(
                            editedName.trim().ifBlank { WorkoutDayScheduleCodec.getCleanName(targetDay.name) },
                            selectedDays
                        )
                        viewModel.updateWorkoutDayColorOrName(
                            targetDay.copy(
                                name = encodedName,
                                colorIndex = 0
                            )
                        )
                        editingDayTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDayTarget = null }) { Text("Cancelar", color = GymTextSecondary) }
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

    // Dialog: Add a Secondary / Tertiary / Optional ("Parche") backup exercise to a routine slot
    addingAlternativeForWEx?.let { (targetWEx, mainCatEx) ->
        val existingAlts = WorkoutExerciseAlternativesCodec.decodeAlternatives(targetWEx.notes)
        val roleTitle = WorkoutExerciseAlternativesCodec.roleFullTitle(existingAlts.size)
        val excludedIds = setOf(targetWEx.exerciseId) + existingAlts.map { it.exerciseId }.toSet()
        val defaultMuscle = WorkoutExerciseAlternativesCodec.primaryMuscleForFilter(mainCatEx?.muscles)
        ExercisePickerModal(
            viewModel = viewModel,
            modalTitle = "Añadir $roleTitle",
            initialMuscleFilter = defaultMuscle,
            excludeExerciseIds = excludedIds,
            helperBannerText = "Si la máquina principal está ocupada, podrás usar este ejercicio parche en 1 toque sin modificar tu rutina titular.",
            onDismiss = { addingAlternativeForWEx = null },
            onSelectExercise = { altEx ->
                viewModel.addAlternativeToWorkoutExercise(targetWEx, altEx)
                addingAlternativeForWEx = null
            }
        )
    }

    // Dialog: Replace an existing exercise in-place without deleting it
    replacingWorkoutExercise?.let { (targetWEx, oldName) ->
        ExercisePickerModal(
            viewModel = viewModel,
            modalTitle = "Reemplazar titular: $oldName",
            onDismiss = { replacingWorkoutExercise = null },
            onSelectExercise = { replacementEx ->
                viewModel.replaceWorkoutExercise(targetWEx, replacementEx)
                replacingWorkoutExercise = null
            }
        )
    }

    // Dialog: Edit Sets / Reps / Loads / Replicate Set 1 / Parches / BiSet for a WorkoutExerciseEntity
    editingWorkoutExercise?.let { selectedWEx ->
        val wEx = allExercises.firstOrNull { it.id == selectedWEx.id } ?: selectedWEx
        val catEx = viewModel.getCatalogExercise(wEx.exerciseId)
        val exName = catEx?.name ?: wEx.customExerciseName.ifBlank { "Ejercicio" }
        val suggestion = ProgressiveOverloadAdvisor.computeSuggestion(
            unitType = wEx.unitType,
            historyForExercise = allFinishedEx.filter { it.exerciseId == wEx.exerciseId }
        )
        EditWorkoutExerciseDialog(
            exercise = wEx,
            exerciseName = exName,
            catalogExercise = catEx,
            overloadSuggestion = suggestion,
            viewModel = viewModel,
            allFinishedExercises = allFinishedEx,
            onDismiss = { editingWorkoutExercise = null },
            onRequestReplace = {
                editingWorkoutExercise = null
                replacingWorkoutExercise = wEx to exName
            },
            onInspectGuide = if (catEx != null) {
                {
                    editingWorkoutExercise = null
                    inspectingCatalogExercise = catEx
                }
            } else null,
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
            onSave = { id, name, eq, prim, sec, unit, notes, photoPath ->
                viewModel.saveCustomExercise(id, name, eq, prim, sec, unit, notes, photoPath) {
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
    initialMuscleFilter: Int? = null,
    excludeExerciseIds: Set<Int> = emptySet(),
    helperBannerText: String? = null,
    onDismiss: () -> Unit,
    onSelectExercise: (CatalogExercise) -> Unit
) {
    val catalog by viewModel.allAvailableExercises.collectAsState()
    var search by remember { mutableStateOf("") }
    var selectedMuscle by remember(initialMuscleFilter) { mutableStateOf(initialMuscleFilter) }
    var selectedEquipment by remember { mutableStateOf<String?>(null) }
    var onlyCustom by remember { mutableStateOf(false) }
    var showCreateCustomModal by remember { mutableStateOf(false) }

    val equipmentOptions = remember {
        listOf(
            "Mancuernas", "Barra", "Aparato / Máquina", "Polea / Cable", "Máquina Smith",
            "Peso Corporal", "Barra Fija", "Banco", "TRX / Suspensión", "Pelota Suiza", "Tobillera / Peso Libre"
        )
    }

    val filtered = remember(catalog, search, selectedMuscle, selectedEquipment, onlyCustom, excludeExerciseIds) {
        val queryNorm = search.normalizeForSearch()
        catalog.filter { ex ->
            val notExcluded = ex.id !in excludeExerciseIds
            val matchesQuery = queryNorm.isBlank() || ex.name.normalizeForSearch().contains(queryNorm)
            val matchesMuscle = selectedMuscle == null || (selectedMuscle!! < ex.muscles.length && ex.muscles[selectedMuscle!!] >= '2')
            val matchesEquip = selectedEquipment == null || ex.equipment.contains(selectedEquipment)
            val matchesCustom = !onlyCustom || ex.id >= 10000
            notExcluded && matchesQuery && matchesMuscle && matchesEquip && matchesCustom
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

                if (!helperBannerText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(GymAccentCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.CallSplit,
                            contentDescription = null,
                            tint = GymAccentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = helperBannerText,
                            color = GymTextPrimary,
                            fontSize = 11.sp
                        )
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
                        label = { Text("Mis Ejercicios", fontSize = 11.sp) }
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
                                        Text("Propio", color = GymOrange, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
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
            onSave = { existingId, name, eq, prim, sec, unit, notes, photoPath ->
                viewModel.saveCustomExercise(existingId, name, eq, prim, sec, unit, notes, photoPath) { createdEx ->
                    showCreateCustomModal = false
                    onSelectExercise(createdEx)
                }
            }
        )
    }
}

private data class EditorExactFieldTarget(
    val setIndex: Int,
    val isLoadField: Boolean,
    val currentValue: String,
    val unitLabel: String
)

private fun formatEditorLoad(value: Float): String {
    val safe = value.coerceAtLeast(0f)
    val rounded = (kotlin.math.round(safe * 100f) / 100f)
    return if (rounded % 1f == 0f) {
        rounded.toInt().toString()
    } else if ((rounded * 10f) % 1f == 0f) {
        "%.1f".format(Locale.US, rounded)
    } else {
        "%.2f".format(Locale.US, rounded)
    }
}

private class EditableAlternativeOption(
    val exerciseId: Int,
    val customExerciseName: String,
    val unitType: Int,
    val repsList: SnapshotStateList<String>,
    val loadsList: SnapshotStateList<String>
)

@Composable
fun EditWorkoutExerciseDialog(
    exercise: WorkoutExerciseEntity,
    exerciseName: String = "Ejercicio",
    catalogExercise: CatalogExercise? = null,
    overloadSuggestion: OverloadSuggestion? = null,
    viewModel: GymWpViewModel? = null,
    allFinishedExercises: List<FinishedExerciseEntity> = emptyList(),
    onDismiss: () -> Unit,
    onRequestReplace: (() -> Unit)? = null,
    onInspectGuide: (() -> Unit)? = null,
    onSave: (WorkoutExerciseEntity) -> Unit
) {
    val initialSets = exercise.setsCount.coerceIn(1, 12)
    val parsedMainReps = remember(exercise.id) {
        val raw = exercise.repsPerSetCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        List(initialSets) { i -> raw.getOrElse(i) { raw.lastOrNull() ?: 10 }.toString() }
    }
    val parsedMainLoads = remember(exercise.id) {
        val raw = exercise.loadsPerSetCsv.split(",").mapNotNull { it.trim().toFloatOrNull() }
        List(initialSets) { i ->
            formatEditorLoad(raw.getOrElse(i) { raw.lastOrNull() ?: 20f })
        }
    }

    val mainRepsList = remember(exercise.id) { mutableStateListOf(*parsedMainReps.toTypedArray()) }
    val mainLoadsList = remember(exercise.id) { mutableStateListOf(*parsedMainLoads.toTypedArray()) }

    val altOptions = remember(exercise.id) {
        val decoded = WorkoutExerciseAlternativesCodec.decodeAlternatives(exercise.notes)
        mutableStateListOf(
            *decoded.map { spec ->
                val cnt = spec.setsCount.coerceIn(1, 12)
                val rRaw = spec.repsPerSetCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
                val lRaw = spec.loadsPerSetCsv.split(",").mapNotNull { it.trim().toFloatOrNull() }
                EditableAlternativeOption(
                    exerciseId = spec.exerciseId,
                    customExerciseName = spec.customExerciseName,
                    unitType = spec.unitType,
                    repsList = mutableStateListOf(
                        *List(cnt) { i -> rRaw.getOrElse(i) { rRaw.lastOrNull() ?: 10 }.toString() }.toTypedArray()
                    ),
                    loadsList = mutableStateListOf(
                        *List(cnt) { i -> formatEditorLoad(lRaw.getOrElse(i) { lRaw.lastOrNull() ?: 15f }) }.toTypedArray()
                    )
                )
            }.toTypedArray()
        )
    }

    var selectedOptionIdx by remember { mutableIntStateOf(0) } // 0 = Principal, 1..N = Parches
    var showAddAltModal by remember { mutableStateOf(false) }
    var altPendingDelete by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val safeSelectedIdx = if (selectedOptionIdx in 0..altOptions.size) selectedOptionIdx else 0
    val activeAlt = if (safeSelectedIdx > 0) altOptions.getOrNull(safeSelectedIdx - 1) else null

    val activeCatEx: CatalogExercise? = if (activeAlt == null) {
        catalogExercise
    } else {
        viewModel?.getCatalogExercise(activeAlt.exerciseId)
    }
    val activeExName: String = if (activeAlt == null) {
        exerciseName
    } else {
        activeCatEx?.name ?: activeAlt.customExerciseName.ifBlank { "Ejercicio #${activeAlt.exerciseId}" }
    }
    val activeUnitType: Int = activeAlt?.unitType ?: exercise.unitType
    val repsList: SnapshotStateList<String> = activeAlt?.repsList ?: mainRepsList
    val loadsList: SnapshotStateList<String> = activeAlt?.loadsList ?: mainLoadsList

    val activeOverloadSuggestion: OverloadSuggestion? = if (activeAlt == null) {
        overloadSuggestion
    } else {
        ProgressiveOverloadAdvisor.computeSuggestion(
            unitType = activeAlt.unitType,
            historyForExercise = allFinishedExercises.filter { it.exerciseId == activeAlt.exerciseId }
        )
    }

    var restSeconds by remember { mutableIntStateOf(exercise.restSeconds.coerceIn(15, 600)) }
    var isBiSet by remember { mutableStateOf(exercise.isBiSet) }
    var replicatedBanner by remember { mutableStateOf<String?>(null) }
    var exactFieldTarget by remember { mutableStateOf<EditorExactFieldTarget?>(null) }

    val unitLabel = when (activeUnitType) {
        2 -> "seg"
        3 -> "min"
        else -> "reps"
    }
    val repStepDelta = if (activeUnitType == 2) 5 else 1

    val primaryMusclesLabel = remember(activeCatEx) {
        activeCatEx?.let { cat ->
            (0..16)
                .filter { it < cat.muscles.length && cat.muscles[it] >= '2' }
                .joinToString(" • ") { MuscleCatalog.getName(it) }
        }.orEmpty()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = GymCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Friendly Gym WP Hero Exercise Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GymSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedExerciseGif(
                            gifAssetPath = activeCatEx?.gifAssetPath.orEmpty(),
                            contentDescription = activeExName,
                            modifier = Modifier
                                .size(64.dp)
                                .let { mod ->
                                    if (activeAlt == null && onInspectGuide != null) mod.clickable { onInspectGuide() } else mod
                                }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (activeAlt != null) {
                                Text(
                                    text = WorkoutExerciseAlternativesCodec.roleFullTitle(safeSelectedIdx - 1),
                                    color = GymAccentCyan,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            } else {
                                Text(
                                    text = "Ejercicio Principal (Titular)",
                                    color = GymOrange,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = activeExName,
                                color = GymTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            if (primaryMusclesLabel.isNotBlank()) {
                                Text(
                                    text = primaryMusclesLabel,
                                    color = GymOrange,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            if (!activeCatEx?.equipment.isNullOrEmpty()) {
                                Text(
                                    text = activeCatEx!!.equipment.joinToString(" • "),
                                    color = GymTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GymTextPrimary)
                        }
                    }

                    if (activeAlt == null && (onRequestReplace != null || onInspectGuide != null)) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onRequestReplace != null) {
                                FilledTonalButton(
                                    onClick = onRequestReplace,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = GymOrange.copy(alpha = 0.15f),
                                        contentColor = GymOrange
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cambiar Titular", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            if (onInspectGuide != null) {
                                OutlinedButton(
                                    onClick = onInspectGuide,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = GymTextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("Guía", color = GymTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Scrollable Body: Parches Selector + Overload + Series Toolbar + Tactile Set Steppers + Rest Chips + Bi-Serie Switch
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ejercicios Parche (Secundario / Terciario / Opcional) Card
                    if (viewModel != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(GymSurfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = GymAccentCyan.copy(alpha = 0.30f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CallSplit,
                                        contentDescription = null,
                                        tint = GymAccentCyan,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Ejercicios Parche (Si la máquina está ocupada)",
                                        color = GymTextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Text(
                                text = "Toca una opción para configurar sus series/peso propios sin alterar tu ejercicio principal:",
                                color = GymTextSecondary,
                                fontSize = 11.sp
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Opción 1: Principal (Full width, never cuts letters)
                                val isMainSelected = safeSelectedIdx == 0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isMainSelected) GymOrange.copy(alpha = 0.18f) else GymCardBg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isMainSelected) GymOrange else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedOptionIdx = 0 }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isMainSelected) GymOrange else GymOrange.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Principal",
                                            color = if (isMainSelected) GymOnPrimary else GymOrange,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = exerciseName,
                                        color = GymTextPrimary,
                                        fontWeight = if (isMainSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Opciones 2..N: Secundario / Terciario / Opcional (Full width, never cuts letters)
                                altOptions.forEachIndexed { aIdx, altOpt ->
                                    val optTabIdx = aIdx + 1
                                    val isAltSelected = safeSelectedIdx == optTabIdx
                                    val altName = (viewModel.getCatalogExercise(altOpt.exerciseId)?.name
                                        ?: altOpt.customExerciseName.ifBlank { "Ej. #${altOpt.exerciseId}" })
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isAltSelected) GymAccentCyan.copy(alpha = 0.18f) else GymCardBg)
                                            .border(
                                                width = 1.dp,
                                                color = if (isAltSelected) GymAccentCyan else GymAccentCyan.copy(alpha = 0.30f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { selectedOptionIdx = optTabIdx }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isAltSelected) GymAccentCyan else GymAccentCyan.copy(alpha = 0.16f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = WorkoutExerciseAlternativesCodec.roleBadgeLabel(aIdx),
                                                color = if (isAltSelected) Color.Black else GymAccentCyan,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Text(
                                            text = altName,
                                            color = GymTextPrimary,
                                            fontWeight = if (isAltSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(GymSurfaceElevated)
                                                .clickable {
                                                    altPendingDelete = altOpt.exerciseId to altName
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Eliminar ejercicio parche",
                                                tint = GymTextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }

                                if (altOptions.size < 4) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(GymAccentCyan.copy(alpha = 0.14f))
                                            .border(1.dp, GymAccentCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                            .clickable { showAddAltModal = true }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = GymAccentCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = "Añadir ${WorkoutExerciseAlternativesCodec.roleBadgeLabel(altOptions.size)}",
                                            color = GymAccentCyan,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (activeOverloadSuggestion != null) {
                        val isAppliedInEditor = ProgressiveOverloadAdvisor.isAlreadyApplied(
                            activeOverloadSuggestion,
                            loadsList.toList(),
                            repsList.toList()
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isAppliedInEditor) FatigueGreen.copy(alpha = 0.14f)
                                    else GymOrange.copy(alpha = 0.14f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isAppliedInEditor) FatigueGreen.copy(alpha = 0.45f)
                                    else GymOrange.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                    tint = if (isAppliedInEditor) FatigueGreen else GymOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = activeOverloadSuggestion.summaryText,
                                    color = GymTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isAppliedInEditor) FatigueGreen.copy(alpha = 0.22f)
                                        else GymOrange
                                    )
                                    .clickable {
                                        when {
                                            activeOverloadSuggestion.suggestedLoadKg != null -> {
                                                val clean = formatEditorLoad(activeOverloadSuggestion.suggestedLoadKg)
                                                for (i in loadsList.indices) {
                                                    loadsList[i] = clean
                                                }
                                            }
                                            activeOverloadSuggestion.suggestedRepsOrTime != null -> {
                                                val clean = activeOverloadSuggestion.suggestedRepsOrTime.coerceAtLeast(1).toString()
                                                for (i in repsList.indices) {
                                                    repsList[i] = clean
                                                }
                                            }
                                        }
                                        replicatedBanner = "Meta aplicada: ${activeOverloadSuggestion.buttonLabel}"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isAppliedInEditor) "Aplicado" else activeOverloadSuggestion.buttonLabel,
                                    color = if (isAppliedInEditor) FatigueGreen else GymOnPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Series Count + One-Tap Replicate S1 Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GymSurfaceElevated, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GymCardBg)
                                    .clickable {
                                        if (repsList.size > 1) {
                                            repsList.removeAt(repsList.lastIndex)
                                            loadsList.removeAt(loadsList.lastIndex)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos series", tint = GymOrange, modifier = Modifier.size(17.dp))
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = "${repsList.size}",
                                    color = GymTextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = if (repsList.size == 1) "Serie" else "Series",
                                    color = GymTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GymCardBg)
                                    .clickable {
                                        if (repsList.size < 12) {
                                            repsList.add(repsList.lastOrNull() ?: "10")
                                            loadsList.add(loadsList.lastOrNull() ?: "20")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Más series", tint = GymOrange, modifier = Modifier.size(17.dp))
                            }
                        }

                        if (repsList.size > 1) {
                            FilledTonalButton(
                                onClick = {
                                    val firstR = repsList.firstOrNull() ?: "10"
                                    val firstL = loadsList.firstOrNull() ?: "20"
                                    for (i in 1 until repsList.size) {
                                        repsList[i] = firstR
                                        loadsList[i] = firstL
                                    }
                                    replicatedBanner = "Serie 1 copiada a las ${repsList.size} series"
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = GymOrange,
                                    contentColor = GymOnPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Replicar Serie 1", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    replicatedBanner?.let { bannerMsg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FatigueGreen.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = bannerMsg,
                                color = FatigueGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "Toca - / + para ajustar rápido o toca el número central para escribir el valor exacto:",
                        color = GymTextSecondary,
                        fontSize = 11.sp
                    )

                    // Tactile Set Cards (Serie 1..N)
                    repsList.forEachIndexed { idx, repVal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GymSurfaceElevated, RoundedCornerShape(14.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Set Badge
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GymOrange.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = GymOrange,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }

                            // Reps / Duration Stepper Pill
                            EditorTactileStepperPill(
                                valueText = repVal,
                                unitText = unitLabel,
                                onMinus = {
                                    val cur = repsList[idx].toIntOrNull() ?: 10
                                    repsList[idx] = (cur - repStepDelta).coerceAtLeast(1).toString()
                                },
                                onPlus = {
                                    val cur = repsList[idx].toIntOrNull() ?: 10
                                    repsList[idx] = (cur + repStepDelta).coerceAtLeast(1).toString()
                                },
                                onTapCenter = {
                                    exactFieldTarget = EditorExactFieldTarget(
                                        setIndex = idx,
                                        isLoadField = false,
                                        currentValue = repsList[idx],
                                        unitLabel = unitLabel
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Load (kg) Stepper Pill when applicable
                            if (activeUnitType == 0) {
                                EditorTactileStepperPill(
                                    valueText = loadsList[idx],
                                    unitText = "kg",
                                    onMinus = {
                                        val cur = loadsList[idx].replace(',', '.').toFloatOrNull() ?: 20f
                                        loadsList[idx] = formatEditorLoad(cur - 2.5f)
                                    },
                                    onPlus = {
                                        val cur = loadsList[idx].replace(',', '.').toFloatOrNull() ?: 20f
                                        loadsList[idx] = formatEditorLoad(cur + 2.5f)
                                    },
                                    onTapCenter = {
                                        exactFieldTarget = EditorExactFieldTarget(
                                            setIndex = idx,
                                            isLoadField = true,
                                            currentValue = loadsList[idx],
                                            unitLabel = "kg"
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (repsList.size > 1) {
                                IconButton(
                                    onClick = {
                                        repsList.removeAt(idx)
                                        loadsList.removeAt(idx)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar serie",
                                        tint = GymTextSecondary.copy(alpha = 0.75f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Add Set Button
                    if (repsList.size < 12) {
                        OutlinedButton(
                            onClick = {
                                repsList.add(repsList.lastOrNull() ?: "10")
                                loadsList.add(loadsList.lastOrNull() ?: "20")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = GymOrange, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Añadir Serie", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Visual Rest Timer Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GymSurfaceElevated),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = GymOrange, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Descanso entre series",
                                        color = GymTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(GymCardBg)
                                            .clickable { restSeconds = (restSeconds - 15).coerceAtLeast(15) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("-15s", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Text(
                                        text = " ${restSeconds}s ",
                                        color = GymTextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(GymCardBg)
                                            .clickable { restSeconds = (restSeconds + 15).coerceAtMost(600) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("+15s", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(30, 45, 60, 90, 120, 180).forEach { presetSec ->
                                    val selected = restSeconds == presetSec
                                    FilterChip(
                                        selected = selected,
                                        onClick = { restSeconds = presetSec },
                                        label = {
                                            Text(
                                                text = if (presetSec >= 120 && presetSec % 60 == 0) "${presetSec / 60} min" else "${presetSec}s",
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GymOrange,
                                            selectedLabelColor = GymOnPrimary,
                                            containerColor = GymCardBg,
                                            labelColor = GymTextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Modern Bi-Serie / Superserie Switch Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isBiSet) GymAccentCyan.copy(alpha = 0.14f) else GymSurfaceElevated)
                            .border(
                                width = if (isBiSet) 1.dp else 0.dp,
                                color = if (isBiSet) GymAccentCyan.copy(alpha = 0.6f) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { isBiSet = !isBiSet }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isBiSet) GymAccentCyan else GymOrange,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Bi-Serie / Superserie",
                                    color = GymTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Ejecutar seguido del ejercicio anterior sin descanso previo",
                                    color = GymTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = isBiSet,
                            onCheckedChange = { isBiSet = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GymOrange,
                                checkedTrackColor = GymOrange.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                // 3. Fixed Bottom Save Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GymSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = GymTextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val builtAltSpecs = altOptions.map { altOpt ->
                                AlternativeExerciseSpec(
                                    exerciseId = altOpt.exerciseId,
                                    customExerciseName = altOpt.customExerciseName,
                                    unitType = altOpt.unitType,
                                    setsCount = altOpt.repsList.size,
                                    repsPerSetCsv = altOpt.repsList.joinToString(",") { it.trim().ifBlank { "10" } },
                                    loadsPerSetCsv = altOpt.loadsList.joinToString(",") { it.trim().ifBlank { "0" } }
                                )
                            }
                            val encodedNotes = WorkoutExerciseAlternativesCodec.encodeAlternatives(
                                exercise.notes,
                                builtAltSpecs
                            )
                            onSave(
                                exercise.copy(
                                    setsCount = mainRepsList.size,
                                    repsPerSetCsv = mainRepsList.joinToString(",") { it.trim().ifBlank { "10" } },
                                    loadsPerSetCsv = mainLoadsList.joinToString(",") { it.trim().ifBlank { "0" } },
                                    restSeconds = restSeconds,
                                    isBiSet = isBiSet,
                                    notes = encodedNotes
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guardar Cambios", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }

    // Exact Value Input Popup when tapping the center of any stepper pill
    exactFieldTarget?.let { target ->
        var typedText by remember(target) { mutableStateOf(target.currentValue) }
        val popupTitle = if (target.isLoadField) {
            "Serie ${target.setIndex + 1} • Carga (${target.unitLabel})"
        } else {
            "Serie ${target.setIndex + 1} • ${target.unitLabel.replaceFirstChar { it.uppercase() }}"
        }

        val applyValue: (Boolean) -> Unit = { replicateRemaining ->
            val cleanVal = if (target.isLoadField) {
                formatEditorLoad(typedText.replace(',', '.').toFloatOrNull() ?: 0f)
            } else {
                (typedText.toIntOrNull() ?: 10).coerceAtLeast(1).toString()
            }
            if (target.setIndex in repsList.indices) {
                if (target.isLoadField) {
                    loadsList[target.setIndex] = cleanVal
                    if (replicateRemaining) {
                        for (i in (target.setIndex + 1) until loadsList.size) {
                            loadsList[i] = cleanVal
                        }
                    }
                } else {
                    repsList[target.setIndex] = cleanVal
                    if (replicateRemaining) {
                        for (i in (target.setIndex + 1) until repsList.size) {
                            repsList[i] = cleanVal
                        }
                    }
                }
                if (replicateRemaining) {
                    replicatedBanner = "Valor replicado a las series siguientes"
                }
            }
            exactFieldTarget = null
        }

        AlertDialog(
            onDismissRequest = { exactFieldTarget = null },
            title = {
                Text(popupTitle, color = GymOrange, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = typedText,
                        onValueChange = { typedText = it },
                        label = { Text("Valor exacto (${target.unitLabel})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (target.isLoadField) KeyboardType.Decimal else KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (target.isLoadField) {
                        Text("Ajuste rápido:", color = GymTextSecondary, fontSize = 11.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(-5f, -2.5f, +1.25f, +2.5f, +5f, +10f).forEach { delta ->
                                val lbl = if (delta < 0f) "-${formatEditorLoad(kotlin.math.abs(delta))} kg" else "+${formatEditorLoad(delta)} kg"
                                AssistChip(
                                    onClick = {
                                        val cur = typedText.replace(',', '.').toFloatOrNull() ?: 0f
                                        typedText = formatEditorLoad(cur + delta)
                                    },
                                    label = { Text(lbl, fontSize = 11.sp) }
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
                                    onClick = { typedText = preset.toString() },
                                    label = { Text("$preset", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }

                    if (target.setIndex < repsList.lastIndex) {
                        OutlinedButton(
                            onClick = { applyValue(true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Guardar y replicar en siguientes",
                                color = GymOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { applyValue(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                ) {
                    Text("Aplicar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exactFieldTarget = null }) {
                    Text("Cancelar", color = GymTextSecondary)
                }
            },
            containerColor = GymCardBg
        )
    }

    if (showAddAltModal && viewModel != null) {
        val excludedIds = setOf(exercise.exerciseId) + altOptions.map { it.exerciseId }.toSet()
        val defaultMuscle = WorkoutExerciseAlternativesCodec.primaryMuscleForFilter(catalogExercise?.muscles)
        val roleTitle = WorkoutExerciseAlternativesCodec.roleFullTitle(altOptions.size)
        ExercisePickerModal(
            viewModel = viewModel,
            modalTitle = "Añadir $roleTitle",
            initialMuscleFilter = defaultMuscle,
            excludeExerciseIds = excludedIds,
            helperBannerText = "Elige un ejercicio parche para cuando la máquina o implemento principal esté ocupado.",
            onDismiss = { showAddAltModal = false },
            onSelectExercise = { chosenAlt ->
                val lastMaxLoad = allFinishedExercises
                    .filter { it.exerciseId == chosenAlt.id }
                    .maxByOrNull { it.timestampMillis }
                    ?.maxLoadKg
                val defaultSpec = WorkoutExerciseAlternativesCodec.buildDefaultSpec(
                    catEx = chosenAlt,
                    parentSetsCount = mainRepsList.size,
                    parentUnitType = exercise.unitType,
                    parentRepsCsv = mainRepsList.joinToString(","),
                    historicalMaxLoadKg = lastMaxLoad
                )
                val cnt = defaultSpec.setsCount.coerceIn(1, 12)
                val rRaw = defaultSpec.repsPerSetCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
                val lRaw = defaultSpec.loadsPerSetCsv.split(",").mapNotNull { it.trim().toFloatOrNull() }
                altOptions.add(
                    EditableAlternativeOption(
                        exerciseId = defaultSpec.exerciseId,
                        customExerciseName = defaultSpec.customExerciseName,
                        unitType = defaultSpec.unitType,
                        repsList = mutableStateListOf(
                            *List(cnt) { i -> rRaw.getOrElse(i) { 10 }.toString() }.toTypedArray()
                        ),
                        loadsList = mutableStateListOf(
                            *List(cnt) { i -> formatEditorLoad(lRaw.getOrElse(i) { 15f }) }.toTypedArray()
                        )
                    )
                )
                selectedOptionIdx = altOptions.size
                showAddAltModal = false
                replicatedBanner = "${chosenAlt.name} añadido como ${WorkoutExerciseAlternativesCodec.roleBadgeLabel(altOptions.size - 1)}"
            }
        )
    }

    altPendingDelete?.let { (delExId, delName) ->
        ConfirmDeleteDialog(
            itemTitle = "el ejercicio parche \"$delName\" de este bloque",
            onConfirm = {
                val removeIdx = altOptions.indexOfFirst { it.exerciseId == delExId }
                if (removeIdx >= 0) {
                    altOptions.removeAt(removeIdx)
                    if (selectedOptionIdx > altOptions.size || selectedOptionIdx == removeIdx + 1) {
                        selectedOptionIdx = 0
                    } else if (selectedOptionIdx > removeIdx + 1) {
                        selectedOptionIdx -= 1
                    }
                }
                altPendingDelete = null
            },
            onDismiss = { altPendingDelete = null }
        )
    }
}

@Composable
private fun EditorTactileStepperPill(
    valueText: String,
    unitText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(GymCardBg)
            .padding(horizontal = 5.dp, vertical = 5.dp),
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
private fun DayHeaderKpiPill(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .padding(horizontal = 4.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DayExerciseMetricBadge(
    text: String,
    bgColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
