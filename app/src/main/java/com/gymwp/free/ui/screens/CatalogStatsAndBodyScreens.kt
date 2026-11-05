package com.gymwp.free.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gymwp.free.data.BodyMeasurementEntity
import com.gymwp.free.data.CatalogExercise
import com.gymwp.free.data.FinishedExerciseEntity
import com.gymwp.free.data.FinishedWorkoutDayEntity
import com.gymwp.free.data.UserProfileEntity
import com.gymwp.free.data.normalizeForSearch
import com.gymwp.free.domain.BodyMetricsCalculator
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.domain.ProgressiveOverloadAdvisor
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AnimatedExerciseGif
import com.gymwp.free.ui.components.AppThemeSelectorCard
import com.gymwp.free.ui.components.AppUpdateCard
import com.gymwp.free.ui.components.ConfirmDeleteDialog
import com.gymwp.free.ui.components.CreateOrEditCustomExerciseDialog
import com.gymwp.free.ui.components.ExerciseDetailDialog
import com.gymwp.free.ui.components.GoogleDriveSyncCard
import com.gymwp.free.ui.components.ProgressionLineChart
import com.gymwp.free.ui.components.StatMiniCard
import com.gymwp.free.ui.components.UserSwitcherDialog
import com.gymwp.free.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CatalogAndStatsScreen(viewModel: GymWpViewModel) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0: Catálogo + Personalizados, 1: Estadísticas
    var search by remember { mutableStateOf("") }
    var selectedMuscleId by remember { mutableStateOf<Int?>(null) }
    var selectedEquipment by remember { mutableStateOf<String?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var onlyCustom by remember { mutableStateOf(false) }
    var showCreateCustomModal by remember { mutableStateOf(false) }
    var editingCustomExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var customExercisePendingDelete by remember { mutableStateOf<CatalogExercise?>(null) }
    var inspectingExercise by remember { mutableStateOf<CatalogExercise?>(null) }
    var viewingFinishedWorkout by remember { mutableStateOf<FinishedWorkoutDayEntity?>(null) }
    var finishedWorkoutPendingDelete by remember { mutableStateOf<FinishedWorkoutDayEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    val allCatalog by viewModel.allAvailableExercises.collectAsState()
    val allDays by viewModel.allWorkoutDays.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    val finishedWorkouts by viewModel.finishedWorkouts.collectAsState()
    val allFinishedEx by viewModel.allFinishedExercises.collectAsState()

    val equipmentOptions = remember {
        listOf(
            "Mancuernas", "Barra", "Aparato / Máquina", "Polea / Cable", "Máquina Smith",
            "Peso Corporal", "Barra Fija", "Banco", "TRX / Suspensión", "Pelota Suiza", "Tobillera / Peso Libre"
        )
    }

    val filteredCatalog = remember(
        allCatalog, search, selectedMuscleId, selectedEquipment, onlyFavorites, onlyCustom, favorites
    ) {
        val q = search.normalizeForSearch()
        allCatalog.filter { ex ->
            val matchSearch = q.isBlank() || ex.name.normalizeForSearch().contains(q)
            val matchMuscle = selectedMuscleId == null ||
                (selectedMuscleId!! < ex.muscles.length && ex.muscles[selectedMuscleId!!] >= '2')
            val matchEquip = selectedEquipment == null || ex.equipment.contains(selectedEquipment)
            val matchFav = !onlyFavorites || favorites.contains(ex.id)
            val matchCustom = !onlyCustom || ex.id >= 10000
            matchSearch && matchMuscle && matchEquip && matchFav && matchCustom
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GymDarkBg)
    ) {
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = GymSurfaceElevated,
            contentColor = GymOrange
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Ejercicios (${filteredCatalog.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Estadísticas (${finishedWorkouts.size})", fontWeight = FontWeight.Bold) }
            )
        }

        if (activeSubTab == 0) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Buscar ejercicio o máquina...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showCreateCustomModal = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GymOrange,
                            contentColor = GymOnPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Crear", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Muscle & Custom/Favorites filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedMuscleId == null && !onlyCustom && !onlyFavorites,
                        onClick = {
                            selectedMuscleId = null
                            onlyCustom = false
                            onlyFavorites = false
                        },
                        label = { Text("Todos", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = onlyCustom,
                        onClick = { onlyCustom = !onlyCustom },
                        label = { Text("Mis Ejercicios", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { onlyFavorites = !onlyFavorites },
                        label = { Text("Favoritos", fontSize = 11.sp) }
                    )
                    MuscleCatalog.ALL_MUSCLES.forEach { m ->
                        FilterChip(
                            selected = selectedMuscleId == m.id,
                            onClick = { selectedMuscleId = if (selectedMuscleId == m.id) null else m.id },
                            label = { Text(m.nameEs, fontSize = 11.sp) }
                        )
                    }
                }

                // Equipment filter chips
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
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCatalog, key = { it.id }) { ex ->
                    val primaryMuscles = (0..16)
                        .filter { it < ex.muscles.length && ex.muscles[it] >= '2' }
                        .joinToString(", ") { MuscleCatalog.getName(it) }
                    val isCustom = ex.id >= 10000

                    Card(
                        colors = CardDefaults.cardColors(containerColor = GymCardBg),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { inspectingExercise = ex }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedExerciseGif(
                                gifAssetPath = ex.gifAssetPath,
                                contentDescription = ex.name,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = ex.name,
                                        color = GymTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (isCustom) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Personalizado",
                                            color = GymOrange,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                if (primaryMuscles.isNotBlank()) {
                                    Text(
                                        text = primaryMuscles,
                                        color = GymOrange,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (ex.equipment.isNotEmpty()) {
                                    Text(
                                        text = ex.equipment.joinToString(" • "),
                                        color = GymTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (isCustom) {
                                IconButton(
                                    onClick = { editingCustomExercise = ex },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Editar ejercicio propio",
                                        tint = GymOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.toggleFavorite(ex.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (favorites.contains(ex.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = if (favorites.contains(ex.id)) GymOrange else GymTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Global Statistics, Bar Chart & Comparative Macro-Muscle Breakdown
            val totalWorkouts = finishedWorkouts.size
            val totalSetsDone = remember(finishedWorkouts) { finishedWorkouts.sumOf { it.totalSets } }
            val totalMinutes = remember(finishedWorkouts) { finishedWorkouts.sumOf { it.durationSeconds } / 60 }
            val totalKcal = remember(finishedWorkouts) { finishedWorkouts.sumOf { it.caloriesBurned } }
            val totalVolumeKg = remember(finishedWorkouts) { finishedWorkouts.sumOf { it.totalVolumeKg.toDouble() }.toFloat() }
            val uniqueTrainingDays = remember(finishedWorkouts) {
                val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                finishedWorkouts.map { dayKeyFmt.format(Date(it.timestampMillis)) }.distinct().size
            }

            val macroCounts = remember(allFinishedEx) {
                val map = mutableMapOf(
                    "Pecho" to 0,
                    "Espalda" to 0,
                    "Piernas" to 0,
                    "Hombros" to 0,
                    "Brazos" to 0,
                    "Abdomen" to 0
                )
                allFinishedEx.forEach { fEx ->
                    for (m in 0..15) {
                        val ch = fEx.muscles.getOrNull(m) ?: '0'
                        if (ch >= '2') {
                            val macro = MuscleCatalog.ALL_MUSCLES.getOrNull(m)?.macroGroup ?: continue
                            if (map.containsKey(macro)) {
                                map[macro] = (map[macro] ?: 0) + (if (ch == '3') 2 else 1)
                            }
                        }
                    }
                }
                map
            }
            val totalMacroScore = macroCounts.values.sum().coerceAtLeast(1)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatMiniCard(
                                label = "Días Completos",
                                value = "$uniqueTrainingDays días ($totalWorkouts ses.)",
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                label = "Series Totales",
                                value = "$totalSetsDone series",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val volLabel = if (totalVolumeKg >= 10000f) {
                                "${"%.1f".format(totalVolumeKg / 1000f)} t (${totalVolumeKg.toInt()} kg)"
                            } else {
                                "${totalVolumeKg.toInt()} kg"
                            }
                            StatMiniCard(
                                label = "Volumen Acumulado",
                                value = volLabel,
                                modifier = Modifier.weight(1f)
                            )
                            StatMiniCard(
                                label = "Tiempo / Energía",
                                value = "$totalMinutes min • $totalKcal kcal",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Interactive Bar Chart of Completed Days, Sets, Volume & Minutes
                item {
                    WorkoutHistoryBarChartCard(finishedWorkouts = finishedWorkouts)
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GymCardBg),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Distribución Comparativa por Grupo Muscular",
                                color = GymOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            macroCounts.entries.forEachIndexed { idx, (macroName, score) ->
                                val pct = if (allFinishedEx.isEmpty()) 0 else ((score * 100f) / totalMacroScore).toInt()
                                val barColor = DayPalette[idx % DayPalette.size]
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(macroName, color = GymTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("$pct%", color = barColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    LinearProgressIndicator(
                                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                                        color = barColor,
                                        trackColor = GymSurfaceElevated,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            "Historial de Entrenamientos Finalizados",
                            color = GymOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        if (finishedWorkouts.isNotEmpty()) {
                            Text(
                                "Toca un entrenamiento para ver la rutina ejecutada en modo solo lectura.",
                                color = GymTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (finishedWorkouts.isEmpty()) {
                    item {
                        Text(
                            "Aún no has finalizado ningún entrenamiento. Inicia una rutina desde la pestaña Entrenar.",
                            color = GymTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    items(finishedWorkouts, key = { it.id }) { fw ->
                        val exercisesCountForDay = remember(allFinishedEx, fw.id, fw.exercisesCount) {
                            val recorded = allFinishedEx.count { it.finishedDayId == fw.id }
                            if (recorded > 0) recorded else fw.exercisesCount
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GymCardBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewingFinishedWorkout = fw }
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
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(fw.dayName, color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(dateFormat.format(Date(fw.timestampMillis)), color = GymTextSecondary, fontSize = 11.sp)
                                    }
                                    Text(fw.planName, color = GymOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${fw.durationSeconds / 60} min • ${fw.caloriesBurned} kcal • Vol: ${fw.totalVolumeKg.toInt()} kg • ${fw.totalSets} series",
                                        color = GymTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = GymOrange,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Ver rutina realizada ($exercisesCountForDay ejercicios) →",
                                            color = GymOrange,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                IconButton(onClick = { finishedWorkoutPendingDelete = fw }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Eliminar entrenamiento del historial",
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

    viewingFinishedWorkout?.let { fw ->
        val sessionExercises = remember(allFinishedEx, fw.id) {
            allFinishedEx.filter { it.finishedDayId == fw.id }
        }
        val matchedColorIdx = remember(allDays, fw.dayName, fw.id) {
            allDays.firstOrNull { it.name == fw.dayName }?.colorIndex
                ?: ((fw.id.toInt().coerceAtLeast(0)) % DayPalette.size)
        }
        FinishedWorkoutReadOnlyDialog(
            workout = fw,
            exercisesDone = sessionExercises,
            dayColorIndex = matchedColorIdx,
            dateText = dateFormat.format(Date(fw.timestampMillis)),
            viewModel = viewModel,
            onInspectCatalogExercise = { catEx ->
                inspectingExercise = catEx
            },
            onDismiss = { viewingFinishedWorkout = null }
        )
    }

    if (showCreateCustomModal) {
        CreateOrEditCustomExerciseDialog(
            initialExercise = null,
            onDismiss = { showCreateCustomModal = false },
            onSave = { existingId, name, eq, prim, sec, unit, notes, photoPath ->
                viewModel.saveCustomExercise(existingId, name, eq, prim, sec, unit, notes, photoPath) {
                    showCreateCustomModal = false
                }
            }
        )
    }

    editingCustomExercise?.let { targetCustom ->
        CreateOrEditCustomExerciseDialog(
            initialExercise = targetCustom,
            onDismiss = { editingCustomExercise = null },
            onSave = { existingId, name, eq, prim, sec, unit, notes, photoPath ->
                viewModel.saveCustomExercise(existingId, name, eq, prim, sec, unit, notes, photoPath) {
                    editingCustomExercise = null
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

    inspectingExercise?.let { ex ->
        ExerciseDetailDialog(
            exercise = ex,
            isFavorite = favorites.contains(ex.id),
            history = allFinishedEx.filter { it.exerciseId == ex.id },
            onToggleFavorite = { viewModel.toggleFavorite(ex.id) },
            onDismiss = { inspectingExercise = null },
            onEditCustomExercise = { target ->
                inspectingExercise = null
                editingCustomExercise = target
            },
            onDeleteCustomExercise = { target ->
                inspectingExercise = null
                customExercisePendingDelete = target
            }
        )
    }
}

@Composable
private fun FinishedWorkoutReadOnlyDialog(
    workout: FinishedWorkoutDayEntity,
    exercisesDone: List<FinishedExerciseEntity>,
    dayColorIndex: Int,
    dateText: String,
    viewModel: GymWpViewModel,
    onInspectCatalogExercise: (CatalogExercise) -> Unit,
    onDismiss: () -> Unit
) {
    val allCatalog by viewModel.allAvailableExercises.collectAsState()
    val dayColor = DayPalette[dayColorIndex.coerceIn(0, DayPalette.lastIndex)]
    val displayExercisesCount = if (exercisesDone.isNotEmpty()) exercisesDone.size else workout.exercisesCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = GymCardBg,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Colored Day Header Banner (matching Entrenar routine card, in Read-Only mode)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(dayColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "SOLO LECTURA • HISTORIAL",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = workout.dayName,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "${workout.planName} • $dateText",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$displayExercisesCount ejercicios • ${workout.totalSets} series • ${workout.durationSeconds / 60} min • ${workout.caloriesBurned} kcal • Vol: ${workout.totalVolumeKg.toInt()} kg",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp
                    )
                }

                // Scrollable list of performed exercises (Read-Only)
                if (exercisesDone.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Esta sesión registró ${workout.totalSets} series y ${workout.totalVolumeKg.toInt()} kg de volumen total, sin desglose individual de ejercicios.",
                            color = GymTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(exercisesDone, key = { _, item -> item.id }) { idx, fEx ->
                            val catEx = remember(allCatalog, fEx.exerciseId) {
                                viewModel.getCatalogExercise(fEx.exerciseId)
                            }
                            val primaryMuscles = remember(fEx.muscles) {
                                (0..16)
                                    .filter { it < fEx.muscles.length && fEx.muscles[it] >= '2' }
                                    .joinToString(" • ") { MuscleCatalog.getName(it) }
                            }
                            val parsedSets = remember(
                                fEx.setsDetailText,
                                catEx?.defaultUnitType,
                                fEx.setsCount,
                                fEx.totalReps,
                                fEx.maxLoadKg
                            ) {
                                ProgressiveOverloadAdvisor.parseSetsDetail(
                                    setsDetailText = fEx.setsDetailText,
                                    unitType = catEx?.defaultUnitType,
                                    fallbackSetsCount = fEx.setsCount,
                                    fallbackTotalReps = fEx.totalReps,
                                    fallbackMaxLoadKg = fEx.maxLoadKg
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(GymSurfaceElevated)
                                    .padding(11.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(dayColor.copy(alpha = 0.20f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            color = dayColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    AnimatedExerciseGif(
                                        gifAssetPath = catEx?.gifAssetPath.orEmpty(),
                                        contentDescription = fEx.exerciseName,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .let { m ->
                                                if (catEx != null) m.clickable { onInspectCatalogExercise(catEx) } else m
                                            }
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = fEx.exerciseName,
                                            color = GymTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (primaryMuscles.isNotBlank()) {
                                            Text(
                                                text = primaryMuscles,
                                                color = GymOrange,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        val summaryLine = if (fEx.maxLoadKg > 0f) {
                                            "Máx: ${ProgressiveOverloadAdvisor.formatWeight(fEx.maxLoadKg)} kg • Volumen: ${fEx.totalVolumeKg.toInt()} kg"
                                        } else {
                                            "${fEx.setsCount} series completadas"
                                        }
                                        Text(
                                            text = summaryLine,
                                            color = GymTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (catEx != null) {
                                        IconButton(
                                            onClick = { onInspectCatalogExercise(catEx) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = "Ver ficha del ejercicio",
                                                tint = GymOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Read-Only Executed Sets List
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    parsedSets.forEach { s ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(GymCardBg)
                                                .border(
                                                    width = 1.dp,
                                                    color = FatigueGreen.copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(FatigueGreen.copy(alpha = 0.20f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = FatigueGreen,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Serie ${s.setNumber}",
                                                    color = GymTextSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Text(
                                                text = if (s.loadDisplay != null) {
                                                    "${s.repsDisplay}  ×  ${s.loadDisplay}"
                                                } else {
                                                    s.repsDisplay
                                                },
                                                color = GymTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Read-Only Close Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GymSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = dayColor, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cerrar Resumen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private data class WorkoutBarBucket(
    val shortLabel: String,
    val subLabel: String,
    val fullRangeTitle: String,
    val completedDays: Int,
    val sessionsCount: Int,
    val totalSets: Int,
    val totalVolumeKg: Float,
    val totalMinutes: Int,
    val totalKcal: Int
)

@Composable
private fun WorkoutHistoryBarChartCard(finishedWorkouts: List<FinishedWorkoutDayEntity>) {
    var periodMode by remember { mutableIntStateOf(0) } // 0: Últimos 7 días, 1: Últimas 8 semanas
    var metricMode by remember { mutableIntStateOf(0) } // 0: Días/Sesiones, 1: Series, 2: Volumen kg, 3: Minutos

    val buckets = remember(finishedWorkouts, periodMode) {
        val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val shortDateFmt = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dayNamesEs = arrayOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")

        val todayStartCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (periodMode == 0) {
            // Últimos 7 días (día por día)
            (6 downTo 0).map { daysAgo ->
                val startCal = (todayStartCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -daysAgo)
                }
                val startMs = startCal.timeInMillis
                val endMs = startMs + 86_400_000L - 1L
                val itemsInDay = finishedWorkouts.filter { it.timestampMillis in startMs..endMs }
                val dowLabel = if (daysAgo == 0) "Hoy" else dayNamesEs[startCal.get(Calendar.DAY_OF_WEEK) - 1]
                val dateStr = shortDateFmt.format(Date(startMs))
                WorkoutBarBucket(
                    shortLabel = dowLabel,
                    subLabel = dateStr,
                    fullRangeTitle = if (daysAgo == 0) "Hoy ($dateStr)" else "${dayNamesEs[startCal.get(Calendar.DAY_OF_WEEK) - 1]} $dateStr",
                    completedDays = if (itemsInDay.isNotEmpty()) 1 else 0,
                    sessionsCount = itemsInDay.size,
                    totalSets = itemsInDay.sumOf { it.totalSets },
                    totalVolumeKg = itemsInDay.sumOf { it.totalVolumeKg.toDouble() }.toFloat(),
                    totalMinutes = itemsInDay.sumOf { it.durationSeconds } / 60,
                    totalKcal = itemsInDay.sumOf { it.caloriesBurned }
                )
            }
        } else {
            // Últimas 8 semanas (bloques de 7 días terminando hoy)
            (7 downTo 0).map { weeksAgo ->
                val endCal = (todayStartCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -(weeksAgo * 7))
                }
                val startCal = (endCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, -6)
                }
                val startMs = startCal.timeInMillis
                val endMs = endCal.timeInMillis + 86_400_000L - 1L
                val itemsInWeek = finishedWorkouts.filter { it.timestampMillis in startMs..endMs }
                val distinctDays = itemsInWeek.map { dayKeyFmt.format(Date(it.timestampMillis)) }.distinct().size
                val startStr = shortDateFmt.format(Date(startMs))
                val endStr = shortDateFmt.format(Date(endCal.timeInMillis))
                WorkoutBarBucket(
                    shortLabel = if (weeksAgo == 0) "Actual" else "S-$weeksAgo",
                    subLabel = startStr,
                    fullRangeTitle = if (weeksAgo == 0) "Últimos 7 días ($startStr – $endStr)" else "Semana $startStr al $endStr",
                    completedDays = distinctDays,
                    sessionsCount = itemsInWeek.size,
                    totalSets = itemsInWeek.sumOf { it.totalSets },
                    totalVolumeKg = itemsInWeek.sumOf { it.totalVolumeKg.toDouble() }.toFloat(),
                    totalMinutes = itemsInWeek.sumOf { it.durationSeconds } / 60,
                    totalKcal = itemsInWeek.sumOf { it.caloriesBurned }
                )
            }
        }
    }

    var selectedIdx by remember(periodMode, buckets.size) {
        val lastActive = buckets.indexOfLast { it.sessionsCount > 0 }
        mutableIntStateOf(if (lastActive >= 0) lastActive else buckets.lastIndex)
    }
    val safeSelectedIdx = selectedIdx.coerceIn(0, (buckets.size - 1).coerceAtLeast(0))

    val metricValues = remember(buckets, metricMode, periodMode) {
        buckets.map { b ->
            when (metricMode) {
                0 -> if (periodMode == 0) b.sessionsCount.toFloat() else b.completedDays.toFloat()
                1 -> b.totalSets.toFloat()
                2 -> b.totalVolumeKg
                else -> b.totalMinutes.toFloat()
            }
        }
    }

    val maxMetricVal = remember(metricValues, metricMode, periodMode) {
        val rawMax = metricValues.maxOrNull() ?: 0f
        when {
            metricMode == 0 && periodMode == 1 -> rawMax.coerceAtLeast(7f)
            rawMax <= 0f -> 1f
            else -> rawMax
        }
    }

    val formatBarTopLabel: (Float) -> String = { v ->
        if (v <= 0f) {
            "–"
        } else {
            when (metricMode) {
                0 -> "${v.roundToInt()}"
                1 -> "${v.roundToInt()}"
                2 -> if (v >= 1000f) "${"%.1f".format(v / 1000f)}k" else "${v.roundToInt()}"
                else -> "${v.roundToInt()}m"
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = GymCardBg),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header + Period Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Actividad Gráfica del Historial",
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (periodMode == 0) "Desglose diario de los últimos 7 días" else "Días completos y carga en las últimas 8 semanas",
                        color = GymTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Segmented Period Selector (7 días vs 8 semanas)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GymSurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Últimos 7 días", "Últimas 8 semanas").forEachIndexed { idx, label ->
                    val selected = periodMode == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) GymOrange else Color.Transparent)
                            .clickable { periodMode = idx }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) GymOnPrimary else GymTextSecondary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Metric Filter Chips
            val metricOptions = listOf(
                if (periodMode == 0) "Sesiones" else "Días Completos",
                "Series",
                "Volumen (kg)",
                "Minutos"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                metricOptions.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = metricMode == idx,
                        onClick = { metricMode = idx },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (metricMode == idx) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    )
                }
            }

            // Vertical Bar Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GymSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        buckets.forEachIndexed { idx, bucket ->
                            val rawVal = metricValues.getOrElse(idx) { 0f }
                            val ratio = if (rawVal <= 0f) 0f else (rawVal / maxMetricVal).coerceIn(0.10f, 1f)
                            val isSelected = idx == safeSelectedIdx
                            val hasActivity = bucket.sessionsCount > 0

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedIdx = idx }
                                    .padding(vertical = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = formatBarTopLabel(rawVal),
                                    color = when {
                                        isSelected -> GymOrange
                                        hasActivity -> GymTextPrimary
                                        else -> GymTextSecondary.copy(alpha = 0.55f)
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(Modifier.height(4.dp))

                                // Bar Track + Filled Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.74f)
                                        .height(116.dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                        .background(GymCardBg.copy(alpha = 0.65f))
                                        .let { m ->
                                            if (isSelected) {
                                                m.border(
                                                    width = 1.dp,
                                                    color = GymOrange.copy(alpha = 0.85f),
                                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                                )
                                            } else m
                                        },
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    if (ratio > 0f) {
                                        val barColors = if (isSelected) {
                                            listOf(GymOrange, GymOrange.copy(alpha = 0.68f))
                                        } else {
                                            listOf(GymAccentCyan, GymAccentCyan.copy(alpha = 0.52f))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(ratio)
                                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                                .background(Brush.verticalGradient(barColors))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .background(GymTextSecondary.copy(alpha = 0.22f))
                                        )
                                    }
                                }

                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = bucket.shortLabel,
                                    color = if (isSelected) GymOrange else GymTextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = bucket.subLabel,
                                    color = GymTextSecondary,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Selected Bar Detailed Summary Callout
            buckets.getOrNull(safeSelectedIdx)?.let { sel ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GymOrange.copy(alpha = 0.11f))
                        .border(1.dp, GymOrange.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = sel.fullRangeTitle,
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (sel.sessionsCount == 0) {
                            "Día/período de descanso • Sin entrenamientos registrados."
                        } else {
                            "${sel.completedDays} día(s) completo(s) • ${sel.sessionsCount} sesión(es) • ${sel.totalSets} series • ${sel.totalVolumeKg.toInt()} kg • ${sel.totalMinutes} min (${sel.totalKcal} kcal)"
                        },
                        color = GymTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MyBodyScreen(
    viewModel: GymWpViewModel,
    onNavigateToSettings: () -> Unit = {}
) {
    val profile by viewModel.userProfile.collectAsState()
    val measurements by viewModel.bodyMeasurements.collectAsState()
    var showAddMeasurementModal by remember { mutableStateOf(false) }
    var showEditBaseBioModal by remember { mutableStateOf(false) }
    var measurementPendingDelete by remember { mutableStateOf<BodyMeasurementEntity?>(null) }
    var selectedChartMetric by remember { mutableIntStateOf(0) } // 0: Peso, 1: % Grasa, 2: IMC, 3: Masa Magra, 4: Cintura, 5: Bíceps

    val latestM = measurements.firstOrNull()
    val previousM = measurements.getOrNull(1)

    val currentWeight = latestM?.weightKg ?: profile?.weightKg ?: 75f
    val currentHeight = latestM?.heightCm ?: profile?.heightCm ?: 175f
    val birthYear = profile?.birthYear ?: 1996
    val gender = profile?.gender ?: 0
    val goal = profile?.goal ?: 0
    val daysPerWeek = profile?.daysPerWeek ?: 4
    val age = BodyMetricsCalculator.calculateAge(birthYear)

    val weightDelta = remember(latestM, previousM) {
        if (latestM != null && previousM != null) latestM.weightKg - previousM.weightKg else null
    }

    val bmi = BodyMetricsCalculator.calculateBmi(currentWeight, currentHeight)
    val bmiClass = BodyMetricsCalculator.getBmiClassificationEs(bmi)
    val (idealMin, idealMax) = BodyMetricsCalculator.calculateIdealWeightRangeKg(currentHeight)
    val deurenbergBf = BodyMetricsCalculator.calculateDeurenbergBodyFat(currentWeight, currentHeight, birthYear, gender)
    val navyBf = latestM?.let { m ->
        BodyMetricsCalculator.calculateNavyBodyFat(
            gender = gender,
            heightCm = currentHeight,
            neckCm = m.neckCm,
            waistCm = m.waistCm,
            hipCm = m.hipCm
        )
    }

    val effectiveBf = when {
        latestM != null && latestM.bodyFatPercent > 2f -> latestM.bodyFatPercent
        navyBf != null -> navyBf
        else -> deurenbergBf
    }
    val bfSourceLabel = when {
        latestM != null && latestM.bodyFatPercent > 2f -> "Registrado"
        navyBf != null -> "Cinta Marina EE.UU."
        else -> "Est. Deurenberg"
    }
    val bfCategory = BodyMetricsCalculator.getBodyFatCategoryEs(effectiveBf, gender)
    val fatMassKg = BodyMetricsCalculator.calculateFatMassKg(currentWeight, effectiveBf)
    val leanMassKg = BodyMetricsCalculator.calculateLeanMassKg(currentWeight, effectiveBf)
    val (rawFfmi, normFfmi) = BodyMetricsCalculator.calculateFfmi(currentWeight, currentHeight, effectiveBf)
    val ffmiClass = BodyMetricsCalculator.getFfmiClassificationEs(normFfmi, gender)

    val musclePct = latestM?.muscleMassPercent ?: (100f - effectiveBf - 15f).coerceIn(28f, 56f)
    val waterPct = latestM?.waterPercent ?: 60f
    val boneKg = latestM?.boneMassKg ?: (currentWeight * 0.042f)
    val visceralFat = latestM?.visceralFat ?: 5

    val whtr = latestM?.let { BodyMetricsCalculator.calculateWaistToHeightRatio(it.waistCm, currentHeight) }
    val whr = latestM?.let { BodyMetricsCalculator.calculateWaistToHipRatio(it.waistCm, it.hipCm) }
    val vTaper = latestM?.let { BodyMetricsCalculator.calculateShoulderToWaistRatio(it.shoulderCm, it.waistCm) }

    val bmr = BodyMetricsCalculator.calculateBmrMifflin(currentWeight, currentHeight, birthYear, gender)
    val tdee = BodyMetricsCalculator.calculateTdee(bmr, daysPerWeek)
    val nutrition = BodyMetricsCalculator.calculateSuggestedNutrition(currentWeight, tdee, goal, daysPerWeek)

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GymDarkBg)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header "Mi Cuerpo" (exclusivamente biométrico)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Mi Cuerpo",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = GymOrange
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GymSurfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Perfil: ${profile?.name ?: "Usuario"}",
                                    color = GymTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Índices biométricos, composición corporal, metabolismo y medidas",
                            color = GymTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GymSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ir a Configuración",
                            tint = GymTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showEditBaseBioModal = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Datos Base", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { showAddMeasurementModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Nueva Medición", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. Resumen Biométrico Principal (2x2 Grid)
        item {
            val deltaText = when {
                weightDelta == null -> "Base actual"
                abs(weightDelta) < 0.05f -> "= Sin variación"
                weightDelta > 0f -> "▲ +${"%.1f".format(weightDelta)} kg vs ant."
                else -> "▼ ${"%.1f".format(weightDelta)} kg vs ant."
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BiometricHighlightCard(
                        title = "Peso Corporal",
                        mainValue = "${"%.1f".format(currentWeight)} kg",
                        subValue = deltaText,
                        accentColor = GymOrange,
                        modifier = Modifier.weight(1f)
                    )
                    BiometricHighlightCard(
                        title = "Estatura y Edad",
                        mainValue = "${currentHeight.toInt()} cm",
                        subValue = "$age años • ${if (gender == 0) "Masculino" else "Femenino"}",
                        accentColor = GymAccentCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BiometricHighlightCard(
                        title = "IMC (OMS)",
                        mainValue = "%.1f".format(bmi),
                        subValue = bmiClass,
                        accentColor = when {
                            bmi < 18.5f -> GymAccentCyan
                            bmi < 25f -> FatigueGreen
                            bmi < 30f -> GymOrange
                            else -> Color(0xFFEF5350)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    BiometricHighlightCard(
                        title = "% Grasa ($bfSourceLabel)",
                        mainValue = "${"%.1f".format(effectiveBf)}%",
                        subValue = bfCategory,
                        accentColor = GymOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Escala Visual de IMC (OMS) y Rango de Peso Saludable
        item {
            val markerFraction = ((bmi - 15f) / (36f - 15f)).coerceIn(0.03f, 0.97f)
            val weightStatusMsg = when {
                currentWeight < idealMin -> "Estás ${"%.1f".format(idealMin - currentWeight)} kg por debajo del rango OMS."
                currentWeight > idealMax -> "Estás ${"%.1f".format(currentWeight - idealMax)} kg sobre el rango OMS (si entrenas fuerza, revisa tu FFMI abajo)."
                else -> "Te encuentras dentro de tu rango de peso saludable OMS."
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Escala IMC y Peso Saludable",
                            color = GymOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "IMC ${"%.1f".format(bmi)} ($bmiClass)",
                            color = GymTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Pointer indicator positioned proportionally along the scale
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.weight(markerFraction))
                        Text(
                            text = "▼",
                            color = GymTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.weight((1f - markerFraction).coerceAtLeast(0.03f)))
                    }

                    // 4-segment WHO BMI colored bar (15..18.5 | 18.5..25 | 25..30 | 30..36)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                    ) {
                        Box(modifier = Modifier.weight(3.5f).fillMaxHeight().background(GymAccentCyan))
                        Box(modifier = Modifier.weight(6.5f).fillMaxHeight().background(FatigueGreen))
                        Box(modifier = Modifier.weight(5.0f).fillMaxHeight().background(Color(0xFFFFB300)))
                        Box(modifier = Modifier.weight(6.0f).fillMaxHeight().background(Color(0xFFEF5350)))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("<18.5 Bajo", color = GymAccentCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("18.5–24.9 Normal", color = FatigueGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("25–29.9 Sobrepeso", color = Color(0xFFFFB300), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text("30+ Obesidad", color = Color(0xFFEF5350), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(color = GymSurfaceElevated)

                    Text(
                        text = "Peso Saludable (${currentHeight.toInt()} cm): ${"%.1f".format(idealMin)} kg – ${"%.1f".format(idealMax)} kg",
                        color = GymAccentCyan,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = weightStatusMsg,
                        color = GymTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 4. Composición Corporal Avanzada y FFMI (Masa Magra vs Masa Grasa)
        item {
            val leanRatio = (leanMassKg / currentWeight.coerceAtLeast(1f)).coerceIn(0.35f, 0.97f)
            val fatRatio = (1f - leanRatio).coerceAtLeast(0.03f)

            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Composición Corporal y FFMI",
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )

                    // Lean Mass vs Fat Mass Stacked Bar
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Masa Magra: ${"%.1f".format(leanMassKg)} kg (${(leanRatio * 100f).roundToInt()}%)",
                                color = FatigueGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Masa Grasa: ${"%.1f".format(fatMassKg)} kg (${"%.1f".format(effectiveBf)}%)",
                                color = GymOrange,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            Box(modifier = Modifier.weight(leanRatio).fillMaxHeight().background(FatigueGreen))
                            Box(modifier = Modifier.weight(fatRatio).fillMaxHeight().background(GymOrange))
                        }
                    }

                    // FFMI Highlight Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GymSurfaceElevated)
                            .padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Índice Masa Libre de Grasa (FFMI)",
                                color = GymTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${"%.1f".format(normFfmi)} (Norm.)",
                                color = GymAccentCyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Clasificación muscular: $ffmiClass • FFMI base: ${"%.1f".format(rawFfmi)}",
                            color = GymOrange,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (navyBf != null) {
                                "Referencia Marina EE.UU.: ${"%.1f".format(navyBf)}% • Deurenberg (IMC/Edad): ${"%.1f".format(deurenbergBf)}%"
                            } else {
                                "Estimación Deurenberg (IMC/Edad): ${"%.1f".format(deurenbergBf)}% • Añade cuello y cintura para cálculo Marina EE.UU."
                            },
                            color = GymTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    // 4 Secondary Bioimpedance Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatMiniCard("Músculo", "${"%.1f".format(musclePct)}%", Modifier.weight(1f))
                        StatMiniCard("Agua Corp.", "${"%.1f".format(waterPct)}%", Modifier.weight(1f))
                        StatMiniCard("Masa Ósea", "${"%.1f".format(boneKg)} kg", Modifier.weight(1f))
                        StatMiniCard("Visceral", "Niv. $visceralFat", Modifier.weight(1f))
                    }
                }
            }
        }

        // 5. Índices Cardiometabólicos y Proporciones Estéticas
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Proporciones e Índices Cardiometabólicos",
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )

                    if (whtr == null && whr == null && vTaper == null) {
                        Text(
                            text = "Registra tus circunferencias (cintura, cadera y hombros) en «Nueva Medición» para calcular tu Índice Cintura-Altura (ICA), Cintura-Cadera (ICC) y simetría V-Taper.",
                            color = GymTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        whtr?.let { valWhtr ->
                            BiometricRatioRow(
                                label = "Índice Cintura / Altura (ICA)",
                                value = "%.2f".format(valWhtr),
                                status = BodyMetricsCalculator.getWhtrStatusEs(valWhtr),
                                ok = valWhtr < 0.50f
                            )
                        }
                        whr?.let { valWhr ->
                            val healthyLimit = if (gender == 0) 0.90f else 0.80f
                            BiometricRatioRow(
                                label = "Índice Cintura / Cadera (ICC)",
                                value = "%.2f".format(valWhr),
                                status = BodyMetricsCalculator.getWhrStatusEs(valWhr, gender),
                                ok = valWhr < healthyLimit
                            )
                        }
                        vTaper?.let { valVt ->
                            val statusVt = when {
                                valVt >= 1.50f -> "Estructura en V Destacada"
                                valVt >= 1.38f -> "Proporción Atlética"
                                else -> "En Construcción"
                            }
                            BiometricRatioRow(
                                label = "Ratio Hombros / Cintura (V-Taper)",
                                value = "%.2f".format(valVt),
                                status = statusVt,
                                ok = valVt >= 1.38f
                            )
                        }
                    }

                    // Latest key circumferences snapshot if present
                    latestM?.let { m ->
                        val activePerimeters = buildList {
                            if (m.shoulderCm > 0f) add("Hombros ${m.shoulderCm} cm")
                            if (m.chestCm > 0f) add("Pecho ${m.chestCm} cm")
                            if (m.waistCm > 0f) add("Cintura ${m.waistCm} cm")
                            if (m.hipCm > 0f) add("Cadera ${m.hipCm} cm")
                            if (m.bicepsRightCm > 0f) add("Bíceps ${m.bicepsRightCm} cm")
                            if (m.thighRightCm > 0f) add("Muslo ${m.thighRightCm} cm")
                            if (m.calfRightCm > 0f) add("Gemelo ${m.calfRightCm} cm")
                        }
                        if (activePerimeters.isNotEmpty()) {
                            HorizontalDivider(color = GymSurfaceElevated)
                            Text(
                                text = "Últimos perímetros: ${activePerimeters.joinToString(" • ")}",
                                color = GymAccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // 6. Metabolismo Basal (TMB), Gasto Total (TDEE) y Nutrición Orientativa
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        text = "Metabolismo, Energía y Hidratación",
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniCard("TMB (Reposo)", "$bmr kcal", Modifier.weight(1f))
                        StatMiniCard("Gasto (TDEE)", "$tdee kcal", Modifier.weight(1f))
                        StatMiniCard("Agua Diaria", "${"%.1f".format(nutrition.waterLiters)} L", Modifier.weight(1f))
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GymSurfaceElevated)
                            .padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Meta: ${nutrition.goalLabel}",
                                color = GymTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${nutrition.targetKcal} kcal/día",
                                color = GymOrange,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "Proteína: ${nutrition.proteinGrams} g • Carbohidratos: ${nutrition.carbsGrams} g • Grasas: ${nutrition.fatGrams} g",
                            color = GymAccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 7. Gráfico Interactivo de Evolución Corporal
        if (measurements.isNotEmpty()) {
            item {
                val chronological = remember(measurements) { measurements.reversed() }
                val chartMetricLabels = listOf("Peso (kg)", "% Grasa", "IMC", "Masa Magra", "Cintura (cm)", "Bíceps (cm)")
                val chartSeries = remember(chronological, selectedChartMetric, currentHeight) {
                    when (selectedChartMetric) {
                        0 -> chronological.map { it.weightKg }
                        1 -> chronological.map { it.bodyFatPercent }
                        2 -> chronological.map {
                            BodyMetricsCalculator.calculateBmi(
                                it.weightKg,
                                if (it.heightCm > 100f) it.heightCm else currentHeight
                            )
                        }
                        3 -> chronological.map {
                            BodyMetricsCalculator.calculateLeanMassKg(it.weightKg, it.bodyFatPercent)
                        }
                        4 -> chronological.mapNotNull { if (it.waistCm > 0f) it.waistCm else null }
                        else -> chronological.mapNotNull { if (it.bicepsRightCm > 0f) it.bicepsRightCm else null }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chartMetricLabels.forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedChartMetric == idx,
                                onClick = { selectedChartMetric = idx },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    if (chartSeries.isNotEmpty()) {
                        ProgressionLineChart(
                            title = "Evolución Corporal: ${chartMetricLabels[selectedChartMetric]}",
                            values = chartSeries,
                            lineColor = if (selectedChartMetric % 2 == 0) GymOrange else GymAccentCyan
                        )
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GymCardBg),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Aún no hay registros con ${chartMetricLabels[selectedChartMetric]} para graficar.",
                                color = GymTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 8. Historial de Registros Antropométricos
        item {
            Text(
                text = "Registros Antropométricos (${measurements.size})",
                color = GymOrange,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }

        if (measurements.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GymCardBg),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Todavía no has guardado mediciones corporales. Pulsa «Nueva Medición» arriba para llevar el control de tu peso, % de grasa, perímetros y pliegues.",
                        color = GymTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else {
            items(measurements, key = { it.id }) { m ->
                val mLean = BodyMetricsCalculator.calculateLeanMassKg(m.weightKg, m.bodyFatPercent)
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
                                    text = "${dateFormat.format(Date(m.timestampMillis))} — ${"%.1f".format(m.weightKg)} kg",
                                    color = GymTextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Grasa ${"%.1f".format(m.bodyFatPercent)}% • Masa Magra ${"%.1f".format(mLean)} kg",
                                    color = GymOrange,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { measurementPendingDelete = m }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Borrar", tint = GymTextSecondary)
                            }
                        }
                        Text(
                            text = "Músculo ${m.muscleMassPercent}% • Agua ${m.waterPercent}% • Ósea ${m.boneMassKg} kg • Visceral ${m.visceralFat}",
                            color = GymTextSecondary,
                            fontSize = 12.sp
                        )
                        if (m.chestCm > 0f || m.waistCm > 0f || m.shoulderCm > 0f || m.bicepsRightCm > 0f || m.thighRightCm > 0f) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Circunferencias (cm): Hombros ${m.shoulderCm} | Pecho ${m.chestCm} | Cintura ${m.waistCm} | Cadera ${m.hipCm} | Bíceps ${m.bicepsRightCm} | Muslo ${m.thighRightCm} | Gemelo ${m.calfRightCm}",
                                color = GymAccentCyan,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        if (m.skinfoldChestMm > 0f || m.skinfoldAbdominalMm > 0f || m.skinfoldThighMm > 0f) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Pliegues (mm): Pectoral ${m.skinfoldChestMm} | Abdominal ${m.skinfoldAbdominalMm} | Muslo ${m.skinfoldThighMm} | Tríceps ${m.skinfoldTricepsMm} | Subescapular ${m.skinfoldSubscapularMm}",
                                color = GymOrange,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddMeasurementModal) {
        AddBodyMeasurementDialog(
            initialWeight = currentWeight,
            initialHeight = currentHeight,
            initialBf = effectiveBf,
            gender = gender,
            previousMeasurement = latestM,
            onDismiss = { showAddMeasurementModal = false },
            onSave = { entity ->
                viewModel.saveBodyMeasurement(entity)
                showAddMeasurementModal = false
            }
        )
    }

    if (showEditBaseBioModal) {
        EditBaseBiometricsDialog(
            initialProfile = profile ?: UserProfileEntity(),
            currentHeightCm = currentHeight,
            currentWeightKg = currentWeight,
            onDismiss = { showEditBaseBioModal = false },
            onSave = { updatedProfile ->
                viewModel.saveProfileAndGenerateSmartPlan(updatedProfile, regeneratePlan = false)
                showEditBaseBioModal = false
            }
        )
    }

    measurementPendingDelete?.let { targetMeasurement ->
        ConfirmDeleteDialog(
            itemTitle = "el registro corporal del ${dateFormat.format(Date(targetMeasurement.timestampMillis))} (${targetMeasurement.weightKg} kg)",
            onConfirm = {
                viewModel.deleteBodyMeasurement(targetMeasurement.id)
                measurementPendingDelete = null
            },
            onDismiss = { measurementPendingDelete = null }
        )
    }
}

@Composable
private fun BiometricHighlightCard(
    title: String,
    mainValue: String,
    subValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = GymCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = GymTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = mainValue,
                color = GymTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = subValue,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BiometricRatioRow(
    label: String,
    value: String,
    status: String,
    ok: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(GymSurfaceElevated)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                text = status,
                color = if (ok) FatigueGreen else GymOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = value,
            color = if (ok) FatigueGreen else GymOrange,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun EditBaseBiometricsDialog(
    initialProfile: UserProfileEntity,
    currentHeightCm: Float,
    currentWeightKg: Float,
    onDismiss: () -> Unit,
    onSave: (UserProfileEntity) -> Unit
) {
    var gender by remember(initialProfile.id) { mutableIntStateOf(initialProfile.gender) }
    var birthYear by remember(initialProfile.id) { mutableStateOf(initialProfile.birthYear.toString()) }
    var height by remember(initialProfile.id) { mutableStateOf(currentHeightCm.toString()) }
    var weight by remember(initialProfile.id) { mutableStateOf(currentWeightKg.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Datos Biométricos Base", color = GymOrange, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Estos datos calibran tu IMC, FFMI, % grasa y metabolismo basal sin modificar tus rutinas actuales.",
                    color = GymTextSecondary,
                    fontSize = 12.sp
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
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it },
                    label = { Text("Año de Nacimiento") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Estatura (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Peso Base (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialProfile.copy(
                            gender = gender,
                            birthYear = (birthYear.toIntOrNull() ?: initialProfile.birthYear).coerceIn(1930, 2020),
                            heightCm = (height.replace(',', '.').toFloatOrNull() ?: currentHeightCm).coerceIn(110f, 235f),
                            weightKg = (weight.replace(',', '.').toFloatOrNull() ?: currentWeightKg).coerceIn(25f, 300f)
                        )
                    )
                },
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

@Composable
fun AddBodyMeasurementDialog(
    initialWeight: Float,
    initialHeight: Float,
    initialBf: Float,
    gender: Int = 0,
    previousMeasurement: BodyMeasurementEntity? = null,
    onDismiss: () -> Unit,
    onSave: (BodyMeasurementEntity) -> Unit
) {
    val fmtNonZero: (Float?) -> String = { v -> if (v != null && v > 0f) v.toString() else "" }

    var weight by remember { mutableStateOf(initialWeight.toString()) }
    var height by remember { mutableStateOf(initialHeight.toString()) }
    var bf by remember { mutableStateOf("%.1f".format(Locale.US, initialBf)) }
    var muscle by remember { mutableStateOf((previousMeasurement?.muscleMassPercent ?: 42f).toString()) }
    var water by remember { mutableStateOf((previousMeasurement?.waterPercent ?: 60f).toString()) }
    var boneMass by remember { mutableStateOf((previousMeasurement?.boneMassKg ?: 3.2f).toString()) }
    var visceralFat by remember { mutableStateOf((previousMeasurement?.visceralFat ?: 5).toString()) }

    var neck by remember { mutableStateOf(fmtNonZero(previousMeasurement?.neckCm)) }
    var shoulders by remember { mutableStateOf(fmtNonZero(previousMeasurement?.shoulderCm)) }
    var chest by remember { mutableStateOf(fmtNonZero(previousMeasurement?.chestCm)) }
    var waist by remember { mutableStateOf(fmtNonZero(previousMeasurement?.waistCm)) }
    var abdomen by remember { mutableStateOf(fmtNonZero(previousMeasurement?.abdomenCm)) }
    var hip by remember { mutableStateOf(fmtNonZero(previousMeasurement?.hipCm)) }
    var biceps by remember { mutableStateOf(fmtNonZero(previousMeasurement?.bicepsRightCm)) }
    var forearm by remember { mutableStateOf(fmtNonZero(previousMeasurement?.forearmRightCm)) }
    var thigh by remember { mutableStateOf(fmtNonZero(previousMeasurement?.thighRightCm)) }
    var calf by remember { mutableStateOf(fmtNonZero(previousMeasurement?.calfRightCm)) }

    var sfChest by remember { mutableStateOf(fmtNonZero(previousMeasurement?.skinfoldChestMm)) }
    var sfTriceps by remember { mutableStateOf(fmtNonZero(previousMeasurement?.skinfoldTricepsMm)) }
    var sfSubscapular by remember { mutableStateOf(fmtNonZero(previousMeasurement?.skinfoldSubscapularMm)) }
    var sfAbdominal by remember { mutableStateOf(fmtNonZero(previousMeasurement?.skinfoldAbdominalMm)) }
    var sfSuprailiac by remember { mutableStateOf(fmtNonZero(previousMeasurement?.skinfoldSuprailiacMm)) }
    var sfThigh by remember { mutableStateOf(fmtNonZero(previousMeasurement?.skinfoldThighMm)) }
    var navyFeedback by remember { mutableStateOf<String?>(null) }

    val parseF: (String, Float) -> Float = { s, fallback -> s.replace(',', '.').toFloatOrNull() ?: fallback }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = GymCardBg
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Nuevo Registro Antropométrico", color = GymOrange, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)

                Text("1. Peso y Composición Corporal", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Peso (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Altura (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bf,
                        onValueChange = { bf = it },
                        label = { Text("% Grasa") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = muscle,
                        onValueChange = { muscle = it },
                        label = { Text("% Músculo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = water,
                        onValueChange = { water = it },
                        label = { Text("% Agua") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = boneMass,
                        onValueChange = { boneMass = it },
                        label = { Text("Masa Ósea (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = visceralFat,
                        onValueChange = { visceralFat = it },
                        label = { Text("Grasa Visceral") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text("2. Circunferencias Corporales (cm)", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = chest, onValueChange = { chest = it }, label = { Text("Pecho") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = shoulders, onValueChange = { shoulders = it }, label = { Text("Hombros") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = neck, onValueChange = { neck = it }, label = { Text("Cuello") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = waist, onValueChange = { waist = it }, label = { Text("Cintura") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = abdomen, onValueChange = { abdomen = it }, label = { Text("Abdomen") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = hip, onValueChange = { hip = it }, label = { Text("Cadera") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }

                // 1-Tap U.S. Navy Tape Body Fat Calculator
                OutlinedButton(
                    onClick = {
                        val calc = BodyMetricsCalculator.calculateNavyBodyFat(
                            gender = gender,
                            heightCm = parseF(height, initialHeight),
                            neckCm = parseF(neck, 0f),
                            waistCm = parseF(waist, 0f),
                            hipCm = parseF(hip, 0f)
                        )
                        if (calc != null) {
                            bf = "%.1f".format(Locale.US, calc)
                            navyFeedback = "% Grasa calculado con cinta (Marina EE.UU.): ${"%.1f".format(calc)}%"
                        } else {
                            navyFeedback = if (gender == 0) {
                                "Ingresa Altura, Cuello y Cintura en cm para calcular."
                            } else {
                                "Ingresa Altura, Cuello, Cintura y Cadera en cm para calcular."
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Straighten, contentDescription = null, tint = GymAccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Calcular % Grasa con Cinta (Marina EE.UU.)",
                        color = GymAccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                navyFeedback?.let { msg ->
                    Text(msg, color = GymOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = biceps, onValueChange = { biceps = it }, label = { Text("Bíceps") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = forearm, onValueChange = { forearm = it }, label = { Text("Antebrazo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = thigh, onValueChange = { thigh = it }, label = { Text("Muslo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = calf, onValueChange = { calf = it }, label = { Text("Gemelo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(4.dp))
                Text("3. Pliegues Cutáneos (mm)", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sfChest, onValueChange = { sfChest = it }, label = { Text("Pectoral") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sfAbdominal, onValueChange = { sfAbdominal = it }, label = { Text("Abdominal") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sfThigh, onValueChange = { sfThigh = it }, label = { Text("Muslo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sfTriceps, onValueChange = { sfTriceps = it }, label = { Text("Tríceps") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sfSubscapular, onValueChange = { sfSubscapular = it }, label = { Text("Subescap.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sfSuprailiac, onValueChange = { sfSuprailiac = it }, label = { Text("Suprailiaco") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = GymTextSecondary) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                BodyMeasurementEntity(
                                    weightKg = parseF(weight, initialWeight),
                                    heightCm = parseF(height, initialHeight),
                                    bodyFatPercent = parseF(bf, initialBf),
                                    muscleMassPercent = parseF(muscle, 42f),
                                    boneMassKg = parseF(boneMass, 3.2f),
                                    waterPercent = parseF(water, 60f),
                                    visceralFat = (visceralFat.toIntOrNull() ?: 5).coerceIn(1, 30),
                                    neckCm = parseF(neck, 0f),
                                    shoulderCm = parseF(shoulders, 0f),
                                    chestCm = parseF(chest, 0f),
                                    waistCm = parseF(waist, 0f),
                                    abdomenCm = parseF(abdomen, 0f),
                                    hipCm = parseF(hip, 0f),
                                    bicepsLeftCm = parseF(biceps, 0f),
                                    bicepsRightCm = parseF(biceps, 0f),
                                    forearmLeftCm = parseF(forearm, 0f),
                                    forearmRightCm = parseF(forearm, 0f),
                                    thighLeftCm = parseF(thigh, 0f),
                                    thighRightCm = parseF(thigh, 0f),
                                    calfLeftCm = parseF(calf, 0f),
                                    calfRightCm = parseF(calf, 0f),
                                    skinfoldChestMm = parseF(sfChest, 0f),
                                    skinfoldTricepsMm = parseF(sfTriceps, 0f),
                                    skinfoldSubscapularMm = parseF(sfSubscapular, 0f),
                                    skinfoldAbdominalMm = parseF(sfAbdominal, 0f),
                                    skinfoldSuprailiacMm = parseF(sfSuprailiac, 0f),
                                    skinfoldThighMm = parseF(sfThigh, 0f)
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                    ) {
                        Text("Guardar Medición", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: GymWpViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    var showUserSwitcherModal by remember { mutableStateOf(false) }
    var showEditPreferencesModal by remember { mutableStateOf(false) }
    var showSmartWizardModal by remember { mutableStateOf(false) }

    val goalNames = listOf("Ganar Masa Muscular", "Perder Peso / Definición", "Ganar Fuerza Máxima", "Salud y Resistencia")
    val levelNames = listOf("Principiante", "Intermedio", "Avanzado")
    val placeNames = listOf("Gimnasio Completo", "En Casa (Mancuernas/Barra)", "Peso Corporal / Calistenia")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GymDarkBg)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = GymOrange
                )
                Text(
                    text = "Perfiles de usuario, preferencias de entrenamiento, temas de Cheese WP, respaldo en Google Drive y versión",
                    color = GymTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // 1. Gestión de Perfiles y Multi-Usuario
        item {
            val activeUser = profile
            val avatarCol = DayPalette[(activeUser?.avatarColorIndex ?: 0).coerceIn(0, DayPalette.lastIndex)]
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(avatarCol),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (activeUser?.name?.firstOrNull()?.uppercaseChar() ?: 'U').toString(),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Perfil Activo: ${activeUser?.name ?: "Usuario"}",
                                color = GymTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${allUsers.size} usuario(s) independiente(s) en este dispositivo",
                                color = GymTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showUserSwitcherModal = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Cambiar Usuario (${allUsers.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showEditPreferencesModal = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Editar Perfil", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Preferencias de Entrenamiento del Perfil Activo
        item {
            val activeUser = profile ?: UserProfileEntity()
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Preferencias de Entrenamiento",
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Objetivo: ${goalNames.getOrElse(activeUser.goal) { goalNames[0] }}",
                        color = GymTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Nivel: ${levelNames.getOrElse(activeUser.experienceLevel) { levelNames[1] }} • Frecuencia: ${activeUser.daysPerWeek} días/semana",
                        color = GymTextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Equipamiento habitual: ${placeNames.getOrElse(activeUser.trainingPlace) { placeNames[0] }}",
                        color = GymTextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedButton(
                        onClick = { showSmartWizardModal = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GymAccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Asistente Inteligente (Generar Plan 3 Fases)",
                            color = GymAccentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Temas de Aplicación (5 temas)
        item {
            AppThemeSelectorCard(viewModel = viewModel)
        }

        // 4. Respaldo Automático por Usuario en Google Drive
        item {
            GoogleDriveSyncCard(viewModel = viewModel)
        }

        // 5. Actualizaciones OTA desde GitHub sin perder datos
        item {
            AppUpdateCard(viewModel = viewModel)
        }

        // 6. Acerca de Cheese WP (Sin emojis, sin mención a GymWP)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Acerca de Cheese WP",
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Cheese WP es un planificador y asistente de entrenamientos integral, diseñado de forma independiente con arquitectura nativa en Android (Kotlin, Jetpack Compose, Room SQLite). Ofrece gestión de rutinas personalizadas, soporte multi-usuario, sincronización en Google Drive, periodización y cálculo antropométrico avanzado.",
                        color = GymTextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    HorizontalDivider(color = GymSurfaceElevated)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Versión de aplicación", color = GymTextSecondary, fontSize = 12.sp)
                        Text("2.8.0 (Build 208)", color = GymTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Privacidad", color = GymTextSecondary, fontSize = 12.sp)
                        Text("100% Local y Privado", color = GymTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showUserSwitcherModal) {
        UserSwitcherDialog(
            viewModel = viewModel,
            onDismiss = { showUserSwitcherModal = false },
            onOpenWizardForNewUser = {
                showUserSwitcherModal = false
                showSmartWizardModal = true
            }
        )
    }

    if (showEditPreferencesModal) {
        EditProfilePreferencesDialog(
            initialProfile = profile ?: UserProfileEntity(),
            onDismiss = { showEditPreferencesModal = false },
            onSave = { updated ->
                viewModel.saveProfileAndGenerateSmartPlan(updated, regeneratePlan = false)
                showEditPreferencesModal = false
            }
        )
    }

    if (showSmartWizardModal) {
        SmartPlanOnboardingDialog(
            initialProfile = profile ?: UserProfileEntity(),
            onDismiss = { showSmartWizardModal = false },
            onConfirm = { updated ->
                viewModel.saveProfileAndGenerateSmartPlan(updated, regeneratePlan = true)
                showSmartWizardModal = false
            }
        )
    }
}

@Composable
private fun EditProfilePreferencesDialog(
    initialProfile: UserProfileEntity,
    onDismiss: () -> Unit,
    onSave: (UserProfileEntity) -> Unit
) {
    var name by remember(initialProfile.id) { mutableStateOf(initialProfile.name) }
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
                    text = "Editar Perfil y Preferencias",
                    color = GymOrange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                Text(
                    text = "Guarda tus preferencias sin reemplazar tus rutinas actuales.",
                    color = GymTextSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Usuario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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

                Text("Equipamiento Habitual:", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Gimnasio", "En Casa", "Peso Corporal").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = place == idx,
                            onClick = { place = idx },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Text("Frecuencia objetivo: $daysPerWeek días/semana", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = GymTextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                initialProfile.copy(
                                    name = name.trim().ifBlank { initialProfile.name },
                                    goal = goal,
                                    experienceLevel = level,
                                    trainingPlace = place,
                                    daysPerWeek = daysPerWeek
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
