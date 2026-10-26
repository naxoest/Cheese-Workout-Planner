package com.gymwp.free.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.gymwp.free.data.MuscleFatigueEntity
import com.gymwp.free.data.PrebuiltPlan
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.domain.MuscleFatigueCalculator
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.components.AnatomyFatigueBodyMap
import com.gymwp.free.ui.components.AnimatedExerciseGif
import com.gymwp.free.ui.theme.*

@Composable
fun ExplorePrebuiltPlansScreen(
    viewModel: GymWpViewModel,
    onPlanImported: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var previewingPlan by remember { mutableStateOf<PrebuiltPlan?>(null) }

    val filteredPlans = remember(viewModel.prebuiltPlans, search) {
        viewModel.prebuiltPlans.filter { plan ->
            search.isBlank() ||
            plan.name.contains(search, ignoreCase = true) ||
            plan.description.contains(search, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GymDarkBg)
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Catálogo de 57 Rutinas",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = GymOrange
        )
        Text(
            text = "Planes estructurados por nivel, objetivo y días de entrenamiento.",
            color = Color.LightGray,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Buscar por objetivo (Hipertrofia, Fuerza, Piernas, Casa...)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredPlans, key = { it.id }) { plan ->
                val totalExercises = plan.days.sumOf { it.exercises.size }
                val accentColor = DayPalette[plan.id % DayPalette.size]

                Card(
                    colors = CardDefaults.cardColors(containerColor = GymCardBg),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { previewingPlan = plan }
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(accentColor)
                        )
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = plan.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = GymOrange.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "RUTINA COMPLETA",
                                        color = GymOrange,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            if (plan.description.isNotBlank()) {
                                Text(
                                    text = plan.description,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 3
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📅 ${plan.days.size} días/semana • 🏋️ $totalExercises ejercicios",
                                    color = GymAccentCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Button(
                                    onClick = {
                                        viewModel.importPrebuiltPlan(plan) {
                                            onPlanImported()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Usar Plan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Preview Modal for PrebuiltPlan
    previewingPlan?.let { plan ->
        Dialog(onDismissRequest = { previewingPlan = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                color = GymCardBg
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(plan.name, color = GymOrange, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(plan.description, color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        plan.days.forEach { d ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = GymSurfaceElevated),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(d.name, color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(Modifier.height(6.dp))
                                    d.exercises.forEach { pEx ->
                                        val catEx = viewModel.getCatalogExercise(pEx.exerciseId)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            if (catEx != null) {
                                                AnimatedExerciseGif(
                                                    gifAssetPath = catEx.gifAssetPath,
                                                    contentDescription = catEx.name,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Column {
                                                Text(
                                                    text = catEx?.name ?: "Ejercicio #${pEx.exerciseId}",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "${pEx.sets} series • Reps: ${pEx.reps.joinToString("-")}",
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { previewingPlan = null }) { Text("Cerrar") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.importPrebuiltPlan(plan) {
                                    previewingPlan = null
                                    onPlanImported()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                        ) {
                            Text("Importar a Mis Rutinas")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MuscleFatigueScreen(viewModel: GymWpViewModel) {
    val fatigueList by viewModel.muscleFatigueList.collectAsState()
    val fatigueMap = remember(fatigueList) {
        fatigueList.associate { it.muscleId to it.fatiguePercent }
    }
    var editingMuscle by remember { mutableStateOf<MuscleFatigueEntity?>(null) }

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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Mapa de Fatiga Muscular 2D",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = GymOrange
                    )
                    Text(
                        text = "Recuperación horaria automática • Toca un músculo para ajustar su fatiga",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = { viewModel.resetAllMuscleFatigue() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Recuperar 100%", color = GymOrange, fontSize = 12.sp)
                }
            }
        }

        // 2D Front & Back Anatomy Map Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    AnatomyFatigueBodyMap(
                        fatigueByMuscleId = fatigueMap,
                        mapHeight = 290.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    // 4-Band Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FatigueLegendDot("0-19% Óptimo", FatigueGreen)
                        FatigueLegendDot("20-44% Leve", FatigueBlue)
                        FatigueLegendDot("45-69% Media", FatigueYellow)
                        FatigueLegendDot("70-100% Alta", FatigueRed)
                    }
                }
            }
        }

        // All 17 Muscle Groups List with Recovery Hours Countdown
        items(MuscleCatalog.ALL_MUSCLES, key = { it.id }) { mInfo ->
            val entity = fatigueList.firstOrNull { it.muscleId == mInfo.id } ?: MuscleFatigueEntity(mInfo.id, 0)
            val pct = entity.fatiguePercent
            val color = getFatigueColor(pct)
            val statusLabel = MuscleFatigueCalculator.getFatigueStatusLabel(pct)
            val hoursRemaining = MuscleFatigueCalculator.hoursUntilFullRecovery(pct, mInfo.id)

            Card(
                colors = CardDefaults.cardColors(containerColor = GymCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingMuscle = entity }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = mInfo.nameEs,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (hoursRemaining > 0) {
                                    "$statusLabel • Recuperación total en ~${hoursRemaining}h"
                                } else {
                                    "100% Recuperado y listo para entrenar"
                                },
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "$pct%",
                            color = color,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        color = color,
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

    // Dialog: Edit Muscle Fatigue Manually (DialogEditMuscleFatigue equivalent)
    editingMuscle?.let { item ->
        var sliderVal by remember { mutableFloatStateOf(item.fatiguePercent.toFloat()) }
        val mName = MuscleCatalog.getName(item.muscleId)
        AlertDialog(
            onDismissRequest = { editingMuscle = null },
            title = { Text("Ajustar Fatiga: $mName") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nivel de fatiga actual: ${sliderVal.toInt()}% (${MuscleFatigueCalculator.getFatigueStatusLabel(sliderVal.toInt())})",
                        color = getFatigueColor(sliderVal.toInt()),
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = sliderVal,
                        onValueChange = { sliderVal = it },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = getFatigueColor(sliderVal.toInt()),
                            activeTrackColor = getFatigueColor(sliderVal.toInt())
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateMuscleFatigueManually(item.muscleId, sliderVal.toInt())
                        editingMuscle = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMuscle = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun FatigueLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = Color.LightGray, fontSize = 11.sp)
    }
}
