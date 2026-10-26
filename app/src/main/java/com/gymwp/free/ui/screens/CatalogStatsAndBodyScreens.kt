package com.gymwp.free.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.gymwp.free.data.BodyMeasurementEntity
import com.gymwp.free.data.CatalogExercise
import com.gymwp.free.domain.BodyMetricsCalculator
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AnimatedExerciseGif
import com.gymwp.free.ui.components.ExerciseDetailDialog
import com.gymwp.free.ui.components.ProgressionLineChart
import com.gymwp.free.ui.components.StatMiniCard
import com.gymwp.free.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CatalogAndStatsScreen(viewModel: GymWpViewModel) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0: 353 Ejercicios, 1: Estadísticas e Historial
    var search by remember { mutableStateOf("") }
    var selectedMuscleId by remember { mutableStateOf<Int?>(null) }
    var selectedEquipment by remember { mutableStateOf<String?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var inspectingExercise by remember { mutableStateOf<CatalogExercise?>(null) }

    val favorites by viewModel.favoriteIds.collectAsState()
    val finishedWorkouts by viewModel.finishedWorkouts.collectAsState()
    val allFinishedEx by viewModel.allFinishedExercises.collectAsState()

    val equipmentOptions = remember {
        listOf(
            "Mancuernas", "Barra", "Aparato / Máquina", "Polea / Cable",
            "Peso Corporal", "Barra Fija", "Banco", "Pelota Suiza", "Banda Elástica", "Kettlebell"
        )
    }

    val filteredCatalog = remember(
        viewModel.catalogExercises, search, selectedMuscleId, selectedEquipment, onlyFavorites, favorites
    ) {
        viewModel.catalogExercises.filter { ex ->
            val matchSearch = search.isBlank() || ex.name.contains(search, ignoreCase = true)
            val matchMuscle = selectedMuscleId == null ||
                (selectedMuscleId!! < ex.muscles.length && ex.muscles[selectedMuscleId!!] >= '2')
            val matchEquip = selectedEquipment == null || ex.equipment.contains(selectedEquipment)
            val matchFav = !onlyFavorites || favorites.contains(ex.id)
            matchSearch && matchMuscle && matchEquip && matchFav
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
                text = { Text("353 Ejercicios (${filteredCatalog.size})", fontWeight = FontWeight.Bold) }
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
                        placeholder = { Text("Buscar ejercicio...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = onlyFavorites,
                        onClick = { onlyFavorites = !onlyFavorites },
                        label = { Text("★ Favs") }
                    )
                }

                // Muscle filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedMuscleId == null,
                        onClick = { selectedMuscleId = null },
                        label = { Text("Todos los Músculos", fontSize = 11.sp) }
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
                                Text(
                                    text = ex.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
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
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(ex.id) }) {
                                Icon(
                                    imageVector = if (favorites.contains(ex.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorito",
                                    tint = if (favorites.contains(ex.id)) GymOrange else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Global Statistics & Comparative Macro-Muscle Breakdown
            val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
            val totalWorkouts = finishedWorkouts.size
            val totalKcal = finishedWorkouts.sumOf { it.caloriesBurned }
            val totalVolumeTon = finishedWorkouts.sumOf { it.totalVolumeKg.toDouble() }.toFloat()

            // Calculate comparative macro-muscle distribution from allFinishedEx
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniCard("Entrenamientos", "$totalWorkouts", Modifier.weight(1f))
                        StatMiniCard("Volumen Total", "${totalVolumeTon.toInt()} kg", Modifier.weight(1f))
                        StatMiniCard("Calorías", "$totalKcal kcal", Modifier.weight(1f))
                    }
                }

                // Macro-Muscle Comparative Distribution Card (GraphicStatistcsComparativeFragment)
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
                                        Text(macroName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    Text(
                        "Historial de Entrenamientos Finalizados",
                        color = GymOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (finishedWorkouts.isEmpty()) {
                    item {
                        Text(
                            "Aún no has finalizado ningún entrenamiento. Inicia una rutina desde la pestaña Entrenar.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    items(finishedWorkouts, key = { it.id }) { fw ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GymCardBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(fw.dayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(dateFormat.format(Date(fw.timestampMillis)), color = Color.LightGray, fontSize = 11.sp)
                                }
                                Text(fw.planName, color = GymOrange, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "⏱ ${fw.durationSeconds / 60} min • 🔥 ${fw.caloriesBurned} kcal • 🏋️ Vol: ${fw.totalVolumeKg.toInt()} kg • ${fw.totalSets} series (${fw.totalReps} reps)",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
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
}

@Composable
fun MyBodyScreen(viewModel: GymWpViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val measurements by viewModel.bodyMeasurements.collectAsState()
    var showAddMeasurementModal by remember { mutableStateOf(false) }

    val currentWeight = measurements.firstOrNull()?.weightKg ?: profile?.weightKg ?: 75f
    val currentHeight = measurements.firstOrNull()?.heightCm ?: profile?.heightCm ?: 175f
    val birthYear = profile?.birthYear ?: 1996
    val gender = profile?.gender ?: 0

    val bmi = BodyMetricsCalculator.calculateBmi(currentWeight, currentHeight)
    val bmiClass = BodyMetricsCalculator.getBmiClassificationEs(bmi)
    val (idealMin, idealMax) = BodyMetricsCalculator.calculateIdealWeightRangeKg(currentHeight)
    val deurenbergBf = BodyMetricsCalculator.calculateDeurenbergBodyFat(currentWeight, currentHeight, birthYear, gender)

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GymDarkBg)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mi Cuerpo y Antropometría",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = GymOrange
                    )
                    Text(
                        text = "IMC, Peso Ideal, Grasa Corporal, 12 Circunferencias y 9 Pliegues",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = { showAddMeasurementModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Registrar")
                }
            }
        }

        // BMI, Ideal Weight & Deurenberg Body Fat Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Índices Biométricos Actuales", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniCard("Peso Actual", "${"%.1f".format(currentWeight)} kg", Modifier.weight(1f))
                        StatMiniCard("IMC (OMS)", "%.1f".format(bmi), Modifier.weight(1f))
                        StatMiniCard("% Grasa Est.", "${"%.1f".format(deurenbergBf)}%", Modifier.weight(1f))
                    }
                    Text(
                        text = "Clasificación OMS: $bmiClass",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Rango de Peso Ideal para ${currentHeight.toInt()} cm: ${"%.1f".format(idealMin)} kg – ${"%.1f".format(idealMax)} kg",
                        color = GymAccentCyan,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Weight Progression Chart if measurements exist
        if (measurements.isNotEmpty()) {
            item {
                ProgressionLineChart(
                    title = "Evolución de Peso Corporal (kg)",
                    values = measurements.reversed().map { it.weightKg },
                    lineColor = GymOrange
                )
            }
        }

        // Historical Measurements List
        item {
            Text(
                "Registros Antropométricos (${measurements.size})",
                color = GymOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(measurements, key = { it.id }) { m ->
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
                        Text(
                            text = "📅 ${dateFormat.format(Date(m.timestampMillis))} — ${m.weightKg} kg (${m.bodyFatPercent}% grasa)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { viewModel.deleteBodyMeasurement(m.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Borrar", tint = Color.Gray)
                        }
                    }
                    Text(
                        text = "Composición: Músculo ${m.muscleMassPercent}% • Agua ${m.waterPercent}% • Masa Ósea ${m.boneMassKg} kg",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    if (m.chestCm > 0f || m.waistCm > 0f || m.bicepsRightCm > 0f || m.thighRightCm > 0f) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Circunferencias (cm): Pecho ${m.chestCm} | Cintura ${m.waistCm} | Cadera ${m.hipCm} | Bíceps ${m.bicepsRightCm} | Muslo ${m.thighRightCm} | Gemelo ${m.calfRightCm}",
                            color = GymAccentCyan,
                            fontSize = 12.sp
                        )
                    }
                    if (m.skinfoldChestMm > 0f || m.skinfoldAbdominalMm > 0f || m.skinfoldThighMm > 0f) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Pliegues (mm): Pectoral ${m.skinfoldChestMm} | Abdominal ${m.skinfoldAbdominalMm} | Muslo ${m.skinfoldThighMm} | Tríceps ${m.skinfoldTricepsMm} | Subescapular ${m.skinfoldSubscapularMm}",
                            color = GymOrange,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    if (showAddMeasurementModal) {
        AddBodyMeasurementDialog(
            initialWeight = currentWeight,
            initialHeight = currentHeight,
            initialBf = deurenbergBf,
            onDismiss = { showAddMeasurementModal = false },
            onSave = { entity ->
                viewModel.saveBodyMeasurement(entity)
                showAddMeasurementModal = false
            }
        )
    }
}

@Composable
fun AddBodyMeasurementDialog(
    initialWeight: Float,
    initialHeight: Float,
    initialBf: Float,
    onDismiss: () -> Unit,
    onSave: (BodyMeasurementEntity) -> Unit
) {
    var weight by remember { mutableStateOf(initialWeight.toString()) }
    var height by remember { mutableStateOf(initialHeight.toString()) }
    var bf by remember { mutableStateOf("%.1f".format(Locale.US, initialBf)) }
    var muscle by remember { mutableStateOf("42.0") }
    var water by remember { mutableStateOf("60.0") }

    // Key circumferences (cm)
    var neck by remember { mutableStateOf("") }
    var shoulders by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var abdomen by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var biceps by remember { mutableStateOf("") }
    var forearm by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }
    var calf by remember { mutableStateOf("") }

    // Key skinfolds (mm)
    var sfChest by remember { mutableStateOf("") }
    var sfTriceps by remember { mutableStateOf("") }
    var sfSubscapular by remember { mutableStateOf("") }
    var sfAbdominal by remember { mutableStateOf("") }
    var sfSuprailiac by remember { mutableStateOf("") }
    var sfThigh by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
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

                Text("1. Peso y Composición Corporal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Peso (kg)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Altura (cm)") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bf, onValueChange = { bf = it }, label = { Text("% Grasa") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = muscle, onValueChange = { muscle = it }, label = { Text("% Músculo") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = water, onValueChange = { water = it }, label = { Text("% Agua") }, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(4.dp))
                Text("2. Circunferencias Corporales (cm)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = chest, onValueChange = { chest = it }, label = { Text("Pecho") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = shoulders, onValueChange = { shoulders = it }, label = { Text("Hombros") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = neck, onValueChange = { neck = it }, label = { Text("Cuello") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = waist, onValueChange = { waist = it }, label = { Text("Cintura") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = abdomen, onValueChange = { abdomen = it }, label = { Text("Abdomen") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = hip, onValueChange = { hip = it }, label = { Text("Cadera") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = biceps, onValueChange = { biceps = it }, label = { Text("Bíceps") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = forearm, onValueChange = { forearm = it }, label = { Text("Antebrazo") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = thigh, onValueChange = { thigh = it }, label = { Text("Muslo") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = calf, onValueChange = { calf = it }, label = { Text("Gemelo") }, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(4.dp))
                Text("3. Pliegues Cutáneos (mm)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sfChest, onValueChange = { sfChest = it }, label = { Text("Pectoral") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sfAbdominal, onValueChange = { sfAbdominal = it }, label = { Text("Abdominal") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sfThigh, onValueChange = { sfThigh = it }, label = { Text("Muslo") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sfTriceps, onValueChange = { sfTriceps = it }, label = { Text("Tríceps") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sfSubscapular, onValueChange = { sfSubscapular = it }, label = { Text("Subescap.") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sfSuprailiac, onValueChange = { sfSuprailiac = it }, label = { Text("Suprailiaco") }, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                BodyMeasurementEntity(
                                    weightKg = weight.toFloatOrNull() ?: initialWeight,
                                    heightCm = height.toFloatOrNull() ?: initialHeight,
                                    bodyFatPercent = bf.toFloatOrNull() ?: initialBf,
                                    muscleMassPercent = muscle.toFloatOrNull() ?: 42f,
                                    waterPercent = water.toFloatOrNull() ?: 60f,
                                    neckCm = neck.toFloatOrNull() ?: 0f,
                                    shoulderCm = shoulders.toFloatOrNull() ?: 0f,
                                    chestCm = chest.toFloatOrNull() ?: 0f,
                                    waistCm = waist.toFloatOrNull() ?: 0f,
                                    abdomenCm = abdomen.toFloatOrNull() ?: 0f,
                                    hipCm = hip.toFloatOrNull() ?: 0f,
                                    bicepsLeftCm = biceps.toFloatOrNull() ?: 0f,
                                    bicepsRightCm = biceps.toFloatOrNull() ?: 0f,
                                    forearmLeftCm = forearm.toFloatOrNull() ?: 0f,
                                    forearmRightCm = forearm.toFloatOrNull() ?: 0f,
                                    thighLeftCm = thigh.toFloatOrNull() ?: 0f,
                                    thighRightCm = thigh.toFloatOrNull() ?: 0f,
                                    calfLeftCm = calf.toFloatOrNull() ?: 0f,
                                    calfRightCm = calf.toFloatOrNull() ?: 0f,
                                    skinfoldChestMm = sfChest.toFloatOrNull() ?: 0f,
                                    skinfoldTricepsMm = sfTriceps.toFloatOrNull() ?: 0f,
                                    skinfoldSubscapularMm = sfSubscapular.toFloatOrNull() ?: 0f,
                                    skinfoldAbdominalMm = sfAbdominal.toFloatOrNull() ?: 0f,
                                    skinfoldSuprailiacMm = sfSuprailiac.toFloatOrNull() ?: 0f,
                                    skinfoldThighMm = sfThigh.toFloatOrNull() ?: 0f
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                    ) {
                        Text("Guardar Medición")
                    }
                }
            }
        }
    }
}
