package com.gymwp.free.ui.components

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.gymwp.free.R
import com.gymwp.free.data.AppUpdateManager
import com.gymwp.free.data.AppUpdateState
import com.gymwp.free.data.CatalogExercise
import com.gymwp.free.data.DriveSyncState
import com.gymwp.free.data.FinishedExerciseEntity
import com.gymwp.free.data.UserProfileEntity
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberGifImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

@Composable
fun AnimatedExerciseGif(
    gifAssetPath: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val imageLoader = rememberGifImageLoader()
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/$gifAssetPath")
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
    )
}

@Composable
fun AnatomyFatigueBodyMap(
    fatigueByMuscleId: Map<Int, Int>,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 290.dp
) {
    val frontMuscleDrawables = remember {
        listOf(
            0 to R.drawable.muscle_front_0,
            1 to R.drawable.muscle_front_1,
            2 to R.drawable.muscle_front_2,
            3 to R.drawable.muscle_front_3,
            4 to R.drawable.muscle_front_4,
            5 to R.drawable.muscle_front_5,
            6 to R.drawable.muscle_front_6,
            7 to R.drawable.muscle_front_7,
            8 to R.drawable.muscle_front_8,
            10 to R.drawable.muscle_front_10,
            11 to R.drawable.muscle_front_11,
            15 to R.drawable.muscle_front_15
        )
    }

    val backMuscleDrawables = remember {
        listOf(
            0 to R.drawable.muscle_back_0,
            2 to R.drawable.muscle_back_2,
            3 to R.drawable.muscle_back_3,
            5 to R.drawable.muscle_back_5,
            7 to R.drawable.muscle_back_7,
            9 to R.drawable.muscle_back_9,
            10 to R.drawable.muscle_back_10,
            11 to R.drawable.muscle_back_11,
            12 to R.drawable.muscle_back_12,
            13 to R.drawable.muscle_back_13,
            14 to R.drawable.muscle_back_14,
            15 to R.drawable.muscle_back_15
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("Frente", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.height(mapHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.muscle_front_base),
                    contentDescription = "Cuerpo Frontal",
                    colorFilter = ColorFilter.tint(SkinToneBase.copy(alpha = 0.9f)),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                for ((muscleId, resId) in frontMuscleDrawables) {
                    val pct = fatigueByMuscleId[muscleId] ?: 0
                    val tint = getFatigueColor(pct).copy(alpha = 0.62f)
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(tint),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("Espalda", style = MaterialTheme.typography.labelMedium, color = Color.LightGray)
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.height(mapHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.muscle_back_base),
                    contentDescription = "Cuerpo Posterior",
                    colorFilter = ColorFilter.tint(SkinToneBase.copy(alpha = 0.9f)),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                for ((muscleId, resId) in backMuscleDrawables) {
                    val pct = fatigueByMuscleId[muscleId] ?: 0
                    val tint = getFatigueColor(pct).copy(alpha = 0.62f)
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(tint),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseDetailDialog(
    exercise: CatalogExercise,
    isFavorite: Boolean,
    history: List<FinishedExerciseEntity>,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val muscleHighlightMap = remember(exercise.muscles) {
        (0..16).associateWith { mId ->
            when (exercise.muscles.getOrNull(mId)) {
                '3' -> 85
                '2' -> 55
                '1' -> 30
                else -> 0
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = GymCardBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GymSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) GymOrange else Color.LightGray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = GymSurfaceElevated,
                    contentColor = GymOrange
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Ejecución y Músculos") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Estadísticas (${history.size})") }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (selectedTab == 0) {
                        AnimatedExerciseGif(
                            gifAssetPath = exercise.gifAssetPath,
                            contentDescription = exercise.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        if (exercise.equipment.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                exercise.equipment.forEach { eq ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(eq, fontSize = 12.sp) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = GymSurfaceElevated,
                                            labelColor = GymOrange
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        Text(
                            "Instrucciones de Ejecución",
                            style = MaterialTheme.typography.titleSmall,
                            color = GymOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = exercise.description.ifBlank { "Ejecute el movimiento de forma controlada manteniendo la postura anatómica correcta." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE0E0E0),
                            lineHeight = 20.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Grupos Musculares Reclutados",
                            style = MaterialTheme.typography.titleSmall,
                            color = GymOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))

                        val involved = (0..16).mapNotNull { mId ->
                            val lvl = exercise.muscles.getOrNull(mId) ?: '0'
                            if (lvl > '0') mId to lvl else null
                        }.sortedByDescending { it.second }

                        involved.forEach { (mId, lvl) ->
                            val roleLabel = when (lvl) {
                                '3' -> "Primario (Agonista - 100%)"
                                '2' -> "Secundario (Sinergista - 65%)"
                                else -> "Estabilizador (35%)"
                            }
                            val badgeColor = when (lvl) {
                                '3' -> FatigueRed
                                '2' -> FatigueYellow
                                else -> FatigueBlue
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(GymSurfaceElevated, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(badgeColor, CircleShape)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(MuscleCatalog.getName(mId), color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                                Text(roleLabel, color = badgeColor, fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        AnatomyFatigueBodyMap(
                            fatigueByMuscleId = muscleHighlightMap,
                            mapHeight = 220.dp
                        )
                    } else {
                        if (history.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Aún no hay sesiones registradas para este ejercicio.\nCompleta un entrenamiento con el Asistente en Vivo para ver tu progresión.",
                                    color = Color.Gray
                                )
                            }
                        } else {
                            val maxLoadAllTime = history.maxOf { it.maxLoadKg }
                            val maxVolAllTime = history.maxOf { it.totalVolumeKg }
                            val totalRepsAllTime = history.sumOf { it.totalReps }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatMiniCard("Récord Carga", "${maxLoadAllTime} kg", Modifier.weight(1f))
                                StatMiniCard("Máx Volumen", "${maxVolAllTime.toInt()} kg", Modifier.weight(1f))
                                StatMiniCard("Total Reps", "$totalRepsAllTime", Modifier.weight(1f))
                            }

                            Spacer(Modifier.height(16.dp))
                            ProgressionLineChart(
                                title = "Progresión de Carga Máxima (kg)",
                                values = history.map { it.maxLoadKg },
                                lineColor = GymOrange
                            )
                            Spacer(Modifier.height(12.dp))
                            ProgressionLineChart(
                                title = "Progresión de Volumen Total (kg)",
                                values = history.map { it.totalVolumeKg },
                                lineColor = GymAccentCyan
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Historial de Sesiones",
                                style = MaterialTheme.typography.titleSmall,
                                color = GymOrange,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            history.reversed().forEach { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = GymSurfaceElevated),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Máx: ${item.maxLoadKg} kg | Vol: ${item.totalVolumeKg.toInt()} kg | Reps: ${item.totalReps}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(item.setsDetailText, color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatMiniCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(GymSurfaceElevated, RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = Color.LightGray, fontSize = 11.sp)
    }
}

@Composable
fun ProgressionLineChart(
    title: String,
    values: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GymSurfaceElevated, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        if (values.isEmpty()) return@Column
        val minVal = (values.minOrNull() ?: 0f) * 0.9f
        val maxVal = ((values.maxOrNull() ?: 1f) * 1.1f).coerceAtLeast(minVal + 1f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val w = size.width
            val h = size.height
            val stepX = if (values.size > 1) w / (values.size - 1) else w / 2f
            val path = Path()
            values.forEachIndexed { idx, v ->
                val x = if (values.size > 1) idx * stepX else w / 2f
                val normY = ((v - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val y = h - (normY * (h - 24f)) - 12f
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))
            }
            if (values.size > 1) {
                drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

/**
 * Reusable Google Drive SAF Auto-Sync Card & Dialog
 */
@Composable
fun GoogleDriveSyncCard(
    viewModel: GymWpViewModel,
    modifier: Modifier = Modifier
) {
    val syncState by viewModel.driveSyncState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    var showConfirmUnlink by remember { mutableStateOf(false) }

    val createDriveDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.linkAndExportToGoogleDrive(uri)
        }
    }

    val openDriveDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromGoogleDrive(uri)
        }
    }

    val isLinked = !syncState.linkedUri.isNullOrBlank()

    Card(
        colors = CardDefaults.cardColors(containerColor = GymCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLinked) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = if (isLinked) FatigueGreen else GymOrange
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Google Drive • Auto-Sync",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isLinked) "Archivo vinculado: ${syncState.linkedFileName}" else "Sin archivo vinculado en Google Drive",
                            color = if (isLinked) FatigueGreen else Color.LightGray,
                            fontSize = 12.sp
                        )
                    }
                }
                if (isLinked) {
                    Switch(
                        checked = syncState.autoBackupEnabled,
                        onCheckedChange = { viewModel.toggleGoogleDriveAutoBackup(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = GymOrange, checkedTrackColor = GymOrange.copy(alpha = 0.4f))
                    )
                }
            }

            Text(
                text = syncState.statusMessage,
                color = Color.LightGray,
                fontSize = 12.sp
            )

            if (syncState.lastSyncMillis > 0L) {
                Text(
                    text = "Última sincronización: ${dateFormat.format(Date(syncState.lastSyncMillis))}",
                    color = GymAccentCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isLinked) {
                            viewModel.syncNowToGoogleDrive()
                        } else {
                            createDriveDocLauncher.launch("Chukaros_GymPlanner_Backup.json")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isLinked) "Sincronizar Ahora" else "Vincular Google Drive", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { openDriveDocLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restaurar Drive", color = GymOrange, fontSize = 12.sp)
                }
            }

            if (isLinked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { createDriveDocLauncher.launch("Chukaros_GymPlanner_Backup.json") }) {
                        Text("Cambiar carpeta/archivo en Drive", color = Color.LightGray, fontSize = 11.sp)
                    }
                    TextButton(onClick = { showConfirmUnlink = true }) {
                        Text("Desvincular", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    if (showConfirmUnlink) {
        ConfirmDeleteDialog(
            itemTitle = "vínculo con Google Drive (${syncState.linkedFileName})",
            customQuestion = "¿Estás seguro que quieres desvincular el guardado automático en Google Drive?",
            confirmButtonText = "Sí, Desvincular",
            onConfirm = {
                viewModel.unlinkGoogleDrive()
                showConfirmUnlink = false
            },
            onDismiss = { showConfirmUnlink = false }
        )
    }
}

@Composable
fun GoogleDriveSyncDialog(
    viewModel: GymWpViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = GymCardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                GoogleDriveSyncCard(viewModel = viewModel)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", color = GymOrange)
                    }
                }
            }
        }
    }
}

@Composable
fun UserSwitcherDialog(
    viewModel: GymWpViewModel,
    onDismiss: () -> Unit,
    onOpenWizardForNewUser: () -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsState()
    val activeUserId by viewModel.activeUserId.collectAsState()
    var showCreateForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newGender by remember { mutableIntStateOf(1) }
    var userPendingDelete by remember { mutableStateOf<UserProfileEntity?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = GymCardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Perfiles de Usuario",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = GymOrange
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Text(
                    text = "Cada usuario mantiene sus propios planes, cargas, fatiga muscular, historial y antropometría de forma independiente (sincronizados en Google Drive).",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                allUsers.forEach { user ->
                    val isSelected = user.id == activeUserId
                    val avatarColor = DayPalette[user.avatarColorIndex.coerceIn(0, DayPalette.lastIndex)]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) GymOrange.copy(alpha = 0.2f) else GymSurfaceElevated,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                viewModel.selectActiveUser(user.id)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = user.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${user.weightKg} kg • ${user.heightCm.toInt()} cm • ${user.daysPerWeek} días/sem",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Text("Activo", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            if (allUsers.size > 1) {
                                IconButton(onClick = { userPendingDelete = user }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar usuario", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }

                if (showCreateForm) {
                    HorizontalDivider(color = Color.DarkGray)
                    Text("Nuevo Perfil de Usuario", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre (ej. Mi Novia, Invitado...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = newGender == 1,
                            onClick = { newGender = 1 },
                            label = { Text("Femenino") }
                        )
                        FilterChip(
                            selected = newGender == 0,
                            onClick = { newGender = 0 },
                            label = { Text("Masculino") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateForm = false }) { Text("Cancelar") }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                viewModel.createNewUserProfile(
                                    name = newName,
                                    gender = newGender,
                                    avatarColorIndex = (allUsers.size * 2 + 1) % DayPalette.size
                                ) {
                                    showCreateForm = false
                                    onDismiss()
                                    onOpenWizardForNewUser()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                        ) {
                            Text("Crear y Configurar Plan")
                        }
                    }
                } else {
                    Button(
                        onClick = { showCreateForm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Añadir Nuevo Usuario")
                    }
                }
            }
        }
    }

    userPendingDelete?.let { targetUser ->
        ConfirmDeleteDialog(
            itemTitle = "el usuario \"${targetUser.name}\" y todas sus rutinas",
            onConfirm = {
                viewModel.deleteUserProfile(targetUser.id)
                userPendingDelete = null
            },
            onDismiss = { userPendingDelete = null }
        )
    }
}

@Composable
fun ConfirmDeleteDialog(
    itemTitle: String,
    customQuestion: String? = null,
    confirmButtonText: String = "Sí, Eliminar",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = FatigueRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "¿Estás seguro que lo quieres eliminar?",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            )
        },
        text = {
            Text(
                text = customQuestion ?: "Se eliminará permanentemente $itemTitle. Esta acción no se puede deshacer.",
                color = Color.LightGray,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FatigueRed)
            ) {
                Text(confirmButtonText, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.LightGray)
            }
        },
        containerColor = GymCardBg
    )
}

@Composable
fun AppUpdateDialogIfNeeded(viewModel: GymWpViewModel) {
    val updateState by viewModel.appUpdateState.collectAsState()
    val context = LocalContext.current

    when (val state = updateState) {
        is AppUpdateState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdatePrompt() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = GymOrange,
                        modifier = Modifier.size(34.dp)
                    )
                },
                title = {
                    Text(
                        text = "¡Nueva Versión Disponible (v${state.remote.versionName})!",
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Versión instalada: v${state.currentVersionName} → Nueva: v${state.remote.versionName}",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        if (state.remote.releaseNotes.isNotBlank()) {
                            Text(
                                text = state.remote.releaseNotes,
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "✅ Tus usuarios, rutinas, cargas, fatiga muscular, historial y mediciones se conservarán intactos al actualizar.",
                            color = FatigueGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.downloadAndInstallUpdate(state.remote) },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Actualizar Ahora")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdatePrompt() }) {
                        Text("Más tarde", color = Color.LightGray)
                    }
                },
                containerColor = GymCardBg
            )
        }
        is AppUpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(
                        text = "Descargando v${state.remote.versionName} (${state.progressPercent}%)",
                        color = GymOrange,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Respaldando datos y descargando el instalador desde GitHub...",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        LinearProgressIndicator(
                            progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                            color = GymOrange,
                            trackColor = GymSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                },
                confirmButton = {},
                containerColor = GymCardBg
            )
        }
        is AppUpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdatePrompt() },
                title = {
                    Text("Instalador Listo (v${state.remote.versionName})", color = FatigueGreen, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "Si el instalador de Android no se abrió automáticamente, toca \"Instalar APK\" para aplicar la actualización sin perder tus datos.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { AppUpdateManager.launchApkInstaller(context, state.apkFile) },
                        colors = ButtonDefaults.buttonColors(containerColor = FatigueGreen)
                    ) {
                        Text("Instalar APK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdatePrompt() }) {
                        Text("Cerrar")
                    }
                },
                containerColor = GymCardBg
            )
        }
        else -> {}
    }
}

@Composable
fun AppUpdateCard(viewModel: GymWpViewModel) {
    val updateState by viewModel.appUpdateState.collectAsState()
    val context = LocalContext.current
    val (installedCode, installedName) = remember { AppUpdateManager.getInstalledVersion(context) }

    Card(
        colors = CardDefaults.cardColors(containerColor = GymCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Versión de la App: v$installedName (Build $installedCode)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                val statusText = when (val s = updateState) {
                    is AppUpdateState.Checking -> "Buscando actualizaciones en GitHub..."
                    is AppUpdateState.UpToDate -> "✅ Tienes la última versión instalada"
                    is AppUpdateState.UpdateAvailable -> "🚀 Nueva versión v${s.remote.versionName} disponible"
                    is AppUpdateState.Error -> s.message
                    else -> "Actualizaciones automáticas OTA desde GitHub activas"
                }
                Text(statusText, color = Color.LightGray, fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { viewModel.checkForAppUpdates(notifyIfAvailable = true) }
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = GymOrange, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Buscar Update", color = GymOrange, fontSize = 12.sp)
            }
        }
    }
}
