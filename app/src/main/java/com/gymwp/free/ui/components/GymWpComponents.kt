package com.gymwp.free.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.gymwp.free.data.ExercisePhotoManager
import com.gymwp.free.data.FinishedExerciseEntity
import com.gymwp.free.data.UserProfileEntity
import com.gymwp.free.domain.MuscleCatalog
import com.gymwp.free.ui.GymWpViewModel
import com.gymwp.free.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    val isLocalPhoto = gifAssetPath.startsWith("file://") || gifAssetPath.startsWith("/")
    val localFile = remember(gifAssetPath) {
        if (isLocalPhoto) File(gifAssetPath.removePrefix("file://")) else null
    }

    if (gifAssetPath.isBlank() || (isLocalPhoto && localFile?.exists() != true)) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(GymOrange.copy(alpha = 0.14f))
                .border(1.dp, GymOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = contentDescription,
                    tint = GymOrange,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val imageLoader = rememberGifImageLoader()
    val modelData: Any = if (isLocalPhoto && localFile != null) {
        localFile
    } else {
        "file:///android_asset/$gifAssetPath"
    }
    val effectiveScale = if (isLocalPhoto) ContentScale.Crop else contentScale
    val bgColor = if (isLocalPhoto) GymSurfaceElevated else Color.White

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(modelData)
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = effectiveScale,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
    )
}

/**
 * 100% Anatomically Verified 2D Muscle Fatigue Body Map
 */
@Composable
fun AnatomyFatigueBodyMap(
    fatigueByMuscleId: Map<Int, Int>,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 290.dp,
    highlightOnlyActive: Boolean = false
) {
    // Verified bijection between MuscleCatalog IDs (0..15) and front/back PNG layers
    val frontMuscleDrawables = remember {
        listOf(
            0 to R.drawable.muscle_front_3,   // 0: Hombros (Deltoides anterior/lateral)
            1 to R.drawable.muscle_front_4,   // 1: Pecho (Pectorales)
            2 to R.drawable.muscle_front_5,   // 2: Bíceps
            3 to R.drawable.muscle_front_6,   // 3: Antebrazos
            4 to R.drawable.muscle_front_7,   // 4: Abdomen (Recto)
            5 to R.drawable.muscle_front_8,   // 5: Oblicuos
            6 to R.drawable.muscle_front_15,  // 6: Cuádriceps
            7 to R.drawable.muscle_front_11,  // 7: Aductores
            8 to R.drawable.muscle_front_10,  // 8: Abductores
            10 to R.drawable.muscle_front_0,  // 10: Tríceps (porción medial frontal)
            11 to R.drawable.muscle_front_1,  // 11: Dorsales (vista frontal subaxilar)
            15 to R.drawable.muscle_front_2   // 15: Gemelos (vista frontal interna)
        )
    }

    val backMuscleDrawables = remember {
        listOf(
            0 to R.drawable.muscle_back_9,    // 0: Hombros (Deltoides posterior)
            2 to R.drawable.muscle_back_12,   // 2: Bíceps / Braquial
            3 to R.drawable.muscle_back_13,   // 3: Antebrazos
            5 to R.drawable.muscle_back_10,   // 5: Oblicuos (flanco posterior)
            7 to R.drawable.muscle_back_11,   // 7: Aductores (posterior interno)
            9 to R.drawable.muscle_back_3,    // 9: Trapecio
            10 to R.drawable.muscle_back_2,   // 10: Tríceps
            11 to R.drawable.muscle_back_7,   // 11: Dorsales (Espalda Alta)
            12 to R.drawable.muscle_back_14,  // 12: Lumbares (Espalda Baja)
            13 to R.drawable.muscle_back_5,   // 13: Glúteos
            14 to R.drawable.muscle_back_15,  // 14: Isquiotibiales (Femoral)
            15 to R.drawable.muscle_back_0    // 15: Gemelos (Pantorrillas)
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("Frente", style = MaterialTheme.typography.labelMedium, color = GymTextSecondary)
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.height(mapHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.muscle_front_base),
                    contentDescription = "Cuerpo Frontal",
                    colorFilter = ColorFilter.tint(SkinToneBase),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                for ((muscleId, resId) in frontMuscleDrawables) {
                    val pct = fatigueByMuscleId[muscleId] ?: 0
                    if (highlightOnlyActive && pct <= 0) continue
                    val alpha = if (pct >= 20) 0.86f else 0.45f
                    val tint = getFatigueColor(pct).copy(alpha = alpha)
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
            Text("Espalda", style = MaterialTheme.typography.labelMedium, color = GymTextSecondary)
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.height(mapHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.muscle_back_base),
                    contentDescription = "Cuerpo Posterior",
                    colorFilter = ColorFilter.tint(SkinToneBase),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                for ((muscleId, resId) in backMuscleDrawables) {
                    val pct = fatigueByMuscleId[muscleId] ?: 0
                    if (highlightOnlyActive && pct <= 0) continue
                    val alpha = if (pct >= 20) 0.86f else 0.45f
                    val tint = getFatigueColor(pct).copy(alpha = alpha)
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
    onDismiss: () -> Unit,
    onEditCustomExercise: ((CatalogExercise) -> Unit)? = null,
    onDeleteCustomExercise: ((CatalogExercise) -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isCustom = exercise.id >= 10000

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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GymTextPrimary
                        )
                        if (isCustom) {
                            Text(
                                text = "Ejercicio personalizado",
                                fontSize = 11.sp,
                                color = GymOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (isCustom && onEditCustomExercise != null) {
                        IconButton(onClick = { onEditCustomExercise(exercise) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar ejercicio", tint = GymOrange)
                        }
                    }
                    if (isCustom && onDeleteCustomExercise != null) {
                        IconButton(onClick = { onDeleteCustomExercise(exercise) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar ejercicio", tint = FatigueRed)
                        }
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (isFavorite) GymOrange else GymTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GymTextPrimary)
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
                        if (exercise.gifAssetPath.isNotBlank()) {
                            AnimatedExerciseGif(
                                gifAssetPath = exercise.gifAssetPath,
                                contentDescription = exercise.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        if (exercise.equipment.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
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
                            "Instrucciones / Notas de Ejecución",
                            style = MaterialTheme.typography.titleSmall,
                            color = GymOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = exercise.description.ifBlank { "Ejecute el movimiento de forma controlada manteniendo la postura anatómica correcta." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = GymTextPrimary,
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
                                    Text(MuscleCatalog.getName(mId), color = GymTextPrimary, fontWeight = FontWeight.SemiBold)
                                }
                                Text(roleLabel, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        AnatomyFatigueBodyMap(
                            fatigueByMuscleId = muscleHighlightMap,
                            mapHeight = 220.dp,
                            highlightOnlyActive = true
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
                                    "Aún no hay sesiones registradas para este ejercicio.\nCompleta un entrenamiento en vivo para ver tu progresión.",
                                    color = GymTextSecondary
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
                                StatMiniCard("Récord Carga", "$maxLoadAllTime kg", Modifier.weight(1f))
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
                                            color = GymTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(item.setsDetailText, color = GymTextSecondary, fontSize = 12.sp)
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

/**
 * Dialog to create or edit a Custom Gym Exercise / Machine (with Camera & Gallery Photo Support)
 */
@Composable
fun CreateOrEditCustomExerciseDialog(
    initialExercise: CatalogExercise? = null,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Int?,
        name: String,
        equipment: List<String>,
        primaryMuscles: Set<Int>,
        secondaryMuscles: Set<Int>,
        unitType: Int,
        notes: String,
        photoPath: String?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialExercise?.name ?: "") }
    var unitType by remember { mutableIntStateOf(initialExercise?.defaultUnitType ?: 0) }
    var notes by remember { mutableStateOf(initialExercise?.description ?: "") }
    var photoPath by remember {
        mutableStateOf(
            initialExercise?.gifAssetPath
                ?.takeIf { it.startsWith("file://") || it.startsWith("/") }
                ?.removePrefix("file://")
        )
    }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var pendingCameraTempFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        val tempFile = pendingCameraTempFile
        if (success && capturedUri != null) {
            isProcessingPhoto = true
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    val out = ExercisePhotoManager.importAndSavePhoto(context, capturedUri)
                    try { tempFile?.delete() } catch (_: Exception) {}
                    out
                }
                isProcessingPhoto = false
                if (savedPath != null) {
                    if (photoPath != null && photoPath != initialExercise?.gifAssetPath?.removePrefix("file://")) {
                        ExercisePhotoManager.deletePhotoIfManaged(context, photoPath)
                    }
                    photoPath = savedPath
                } else {
                    Toast.makeText(context, "No se pudo procesar la foto", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            try { tempFile?.delete() } catch (_: Exception) {}
        }
    }

    val launchCameraCapture = {
        try {
            val (tmpFile, uri) = ExercisePhotoManager.createTempCameraUri(context)
            pendingCameraTempFile = tmpFile
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } catch (_: Exception) {
            Toast.makeText(context, "No se pudo abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara para tomar una foto", Toast.LENGTH_SHORT).show()
        }
    }

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { selectedUri: Uri? ->
        if (selectedUri != null) {
            isProcessingPhoto = true
            scope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    ExercisePhotoManager.importAndSavePhoto(context, selectedUri)
                }
                isProcessingPhoto = false
                if (savedPath != null) {
                    if (photoPath != null && photoPath != initialExercise?.gifAssetPath?.removePrefix("file://")) {
                        ExercisePhotoManager.deletePhotoIfManaged(context, photoPath)
                    }
                    photoPath = savedPath
                } else {
                    Toast.makeText(context, "No se pudo cargar la imagen seleccionada", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val allEquipmentOptions = remember {
        listOf(
            "Aparato / Máquina", "Polea / Cable", "Mancuernas", "Barra", "Máquina Smith",
            "Peso Corporal", "Barra Fija", "Banco", "TRX / Suspensión", "Pelota Suiza", "Tobillera / Peso Libre"
        )
    }

    val selectedEquipment = remember {
        mutableStateListOf<String>().apply {
            val initEq = initialExercise?.equipment ?: listOf("Aparato / Máquina")
            addAll(initEq)
        }
    }

    val primaryMuscles = remember {
        mutableStateListOf<Int>().apply {
            initialExercise?.muscles?.forEachIndexed { idx, c ->
                if (c == '3') add(idx)
            }
            if (isEmpty() && initialExercise == null) add(1) // Pecho por defecto si está vacío
        }
    }

    val secondaryMuscles = remember {
        mutableStateListOf<Int>().apply {
            initialExercise?.muscles?.forEachIndexed { idx, c ->
                if (c == '2' || c == '1') add(idx)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(22.dp),
            color = GymCardBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GymSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (initialExercise == null) "Crear Ejercicio / Máquina" else "Editar Ejercicio Propio",
                        color = GymOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GymTextPrimary)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Photo Picker Card (Camera + Gallery)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GymSurfaceElevated),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Foto del Ejercicio / Máquina (Opcional)",
                                    color = GymTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (photoPath != null) {
                                    TextButton(
                                        onClick = { photoPath = null },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = FatigueRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Quitar foto", color = FatigueRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (isProcessingPhoto) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GymCardBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = GymOrange,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text("Optimizando foto...", color = GymTextSecondary, fontSize = 13.sp)
                                    }
                                }
                            } else if (photoPath != null) {
                                AnimatedExerciseGif(
                                    gifAssetPath = "file://$photoPath",
                                    contentDescription = name.ifBlank { "Foto del ejercicio" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(165.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GymCardBg)
                                        .border(1.dp, GymOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AddAPhoto,
                                            contentDescription = null,
                                            tint = GymOrange,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Toma una foto de la máquina de tu gimnasio o elige una imagen de tu galería",
                                            color = GymTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            launchCameraCapture()
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = GymOrange.copy(alpha = 0.16f),
                                        contentColor = GymOrange
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Tomar Foto", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { pickGalleryLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = GymOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Galería", color = GymOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del ejercicio o máquina *") },
                        placeholder = { Text("Ej. Prensa Péndulo, Remo Hammer...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Equipamiento utilizado:", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allEquipmentOptions.forEach { eq ->
                            val selected = selectedEquipment.contains(eq)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected && selectedEquipment.size > 1) {
                                        selectedEquipment.remove(eq)
                                    } else if (!selected) {
                                        selectedEquipment.add(eq)
                                    }
                                },
                                label = { Text(eq, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("Cómo se registra:", color = GymTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            0 to "Reps + Carga (kg)",
                            1 to "Solo Reps (Peso Corporal)",
                            2 to "Segundos (Isométrico)",
                            3 to "Minutos (Cardio)"
                        ).forEach { (uCode, label) ->
                            FilterChip(
                                selected = unitType == uCode,
                                onClick = { unitType = uCode },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text(
                        "Músculos Principales (Impacto directo en Fatiga 2D):",
                        color = GymOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MuscleCatalog.ALL_MUSCLES.forEach { m ->
                            val isPrim = primaryMuscles.contains(m.id)
                            FilterChip(
                                selected = isPrim,
                                onClick = {
                                    if (isPrim && primaryMuscles.size > 1) {
                                        primaryMuscles.remove(m.id)
                                    } else if (!isPrim) {
                                        primaryMuscles.add(m.id)
                                        secondaryMuscles.remove(m.id)
                                    }
                                },
                                label = { Text(m.nameEs, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FatigueRed.copy(alpha = 0.85f),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Text(
                        "Músculos Secundarios (Opcional):",
                        color = GymTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MuscleCatalog.ALL_MUSCLES.forEach { m ->
                            val isSec = secondaryMuscles.contains(m.id)
                            val isPrim = primaryMuscles.contains(m.id)
                            if (!isPrim) {
                                FilterChip(
                                    selected = isSec,
                                    onClick = {
                                        if (isSec) secondaryMuscles.remove(m.id) else secondaryMuscles.add(m.id)
                                    },
                                    label = { Text(m.nameEs, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas / Ajustes de máquina (opcional)") },
                        placeholder = { Text("Ej. Asiento en nivel 3, agarre neutro...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GymSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = GymTextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && !isProcessingPhoto) {
                                onSave(
                                    initialExercise?.id,
                                    name,
                                    selectedEquipment.toList(),
                                    primaryMuscles.toSet(),
                                    secondaryMuscles.toSet(),
                                    unitType,
                                    notes,
                                    photoPath
                                )
                            }
                        },
                        enabled = name.isNotBlank() && !isProcessingPhoto,
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guardar Ejercicio", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Theme Selector Card & Dialog (5 Official Cheese WP Themes)
 */
@Composable
fun AppThemeSelectorCard(viewModel: GymWpViewModel) {
    val currentTheme by viewModel.selectedTheme.collectAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = GymCardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = GymOrange)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Tema de Cheese WP",
                        color = GymTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Actual: ${currentTheme.displayName}",
                        color = GymTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            AppThemeOption.entries.forEach { option ->
                val selected = option == currentTheme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) GymOrange.copy(alpha = 0.16f) else GymSurfaceElevated)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) GymOrange else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.setAppTheme(option) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        // Color Swatch Preview (Bg + Primary + Secondary)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        ) {
                            Box(Modifier.size(16.dp, 28.dp).background(option.previewBg))
                            Box(Modifier.size(16.dp, 28.dp).background(option.previewPrimary))
                            Box(Modifier.size(16.dp, 28.dp).background(option.previewSecondary))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = option.displayName,
                                color = GymTextPrimary,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = option.subtitle,
                                color = GymTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (selected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Seleccionado", tint = GymOrange)
                    }
                }
            }
        }
    }
}

@Composable
fun AppThemeSelectorDialog(
    viewModel: GymWpViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = GymCardBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AppThemeSelectorCard(viewModel = viewModel)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Listo", color = GymOrange, fontWeight = FontWeight.Bold)
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
        Text(label, color = GymTextSecondary, fontSize = 11.sp)
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
        Text(title, color = GymTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                            color = GymTextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isLinked) "Archivo vinculado: ${syncState.linkedFileName}" else "Sin archivo vinculado en Google Drive",
                            color = if (isLinked) FatigueGreen else GymTextSecondary,
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
                color = GymTextSecondary,
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
                            createDriveDocLauncher.launch("Cheese_WP_Backup.json")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
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
                    TextButton(onClick = { createDriveDocLauncher.launch("Cheese_WP_Backup.json") }) {
                        Text("Cambiar carpeta/archivo en Drive", color = GymTextSecondary, fontSize = 11.sp)
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
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = GymTextPrimary)
                    }
                }

                Text(
                    text = "Cada usuario mantiene sus propios planes, cargas, fatiga muscular, tema visual, historial y antropometría de forma independiente.",
                    color = GymTextSecondary,
                    fontSize = 12.sp
                )

                allUsers.forEach { user ->
                    val isSelected = user.id == activeUserId
                    val avatarColor = DayPalette[user.avatarColorIndex.coerceIn(0, DayPalette.lastIndex)]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) GymOrange.copy(alpha = 0.18f) else GymSurfaceElevated,
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
                                    color = GymTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${user.weightKg} kg • ${user.heightCm.toInt()} cm • ${user.daysPerWeek} días/sem",
                                    color = GymTextSecondary,
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
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
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
                            colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                        ) {
                            Text("Crear y Configurar Plan")
                        }
                    }
                } else {
                    Button(
                        onClick = { showCreateForm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary),
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
                color = GymTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            )
        },
        text = {
            Text(
                text = customQuestion ?: "Se eliminará permanentemente $itemTitle. Esta acción no se puede deshacer.",
                color = GymTextSecondary,
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
                Text("Cancelar", color = GymTextSecondary)
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
                            color = GymTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        if (state.remote.releaseNotes.isNotBlank()) {
                            Text(
                                text = state.remote.releaseNotes,
                                color = GymTextSecondary,
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
                        colors = ButtonDefaults.buttonColors(containerColor = GymOrange, contentColor = GymOnPrimary)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Actualizar Ahora")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdatePrompt() }) {
                        Text("Más tarde", color = GymTextSecondary)
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
                            text = "Respaldando datos y descargando el instalador de Cheese WP desde GitHub...",
                            color = GymTextSecondary,
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
                        color = GymTextSecondary,
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
                    text = "Cheese WP • v$installedName (Build $installedCode)",
                    color = GymTextPrimary,
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
                Text(statusText, color = GymTextSecondary, fontSize = 12.sp)
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
