package com.gymwp.free.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymwp.free.data.*
import com.gymwp.free.domain.MuscleFatigueCalculator
import com.gymwp.free.domain.SmartWorkoutGenerator
import com.gymwp.free.domain.WorkoutMetricsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CompletedExerciseLog(
    val exerciseId: Int,
    val exerciseName: String,
    val muscles: String,
    val loadsPerSet: List<Float>,
    val repsPerSet: List<Int>
)

@OptIn(ExperimentalCoroutinesApi::class)
class GymWpViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = GymWpDatabase.getInstance(application)
    private val dao = db.dao()

    val catalogExercises: List<CatalogExercise> = AssetCatalogRepository.getExercises(application)
    val prebuiltPlans: List<PrebuiltPlan> = AssetCatalogRepository.getPrebuiltPlans(application)
    private val catalogMap: Map<Int, CatalogExercise> = catalogExercises.associateBy { it.id }

    private val _activeUserId = MutableStateFlow(GoogleDriveBackupManager.getSavedActiveUserId(appContext))
    val activeUserId: StateFlow<Int> = _activeUserId.asStateFlow()

    val allUsers: StateFlow<List<UserProfileEntity>> = dao.observeAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _driveSyncState = MutableStateFlow(GoogleDriveBackupManager.loadSyncState(appContext))
    val driveSyncState: StateFlow<DriveSyncState> = _driveSyncState.asStateFlow()

    val userProfile: StateFlow<UserProfileEntity?> = _activeUserId
        .flatMapLatest { uid -> dao.observeUserProfile(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val workoutPlans: StateFlow<List<WorkoutPlanEntity>> = _activeUserId
        .flatMapLatest { uid -> dao.observeWorkoutPlans(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkoutDays: StateFlow<List<WorkoutDayEntity>> = dao.observeAllWorkoutDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkoutExercises: StateFlow<List<WorkoutExerciseEntity>> = dao.observeAllWorkoutExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userCustomExercises: StateFlow<List<UserCustomExerciseEntity>> = _activeUserId
        .flatMapLatest { uid -> dao.observeUserCustomExercises(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<Int>> = _activeUserId
        .flatMapLatest { uid -> dao.observeFavorites(uid) }
        .map { list -> list.map { it.exerciseId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val muscleFatigueList: StateFlow<List<MuscleFatigueEntity>> = _activeUserId
        .flatMapLatest { uid ->
            dao.observeMuscleFatigue(uid).map { list ->
                val now = System.currentTimeMillis()
                val byId = list.associateBy { it.muscleId }
                (0..16).map { mId ->
                    val entity = byId[mId] ?: MuscleFatigueEntity(userId = uid, muscleId = mId, fatiguePercent = 0, lastUpdatedMillis = now)
                    entity.copy(fatiguePercent = MuscleFatigueCalculator.computeCurrentFatigue(entity, now))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finishedWorkouts: StateFlow<List<FinishedWorkoutDayEntity>> = _activeUserId
        .flatMapLatest { uid -> dao.observeFinishedWorkouts(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFinishedExercises: StateFlow<List<FinishedExerciseEntity>> = _activeUserId
        .flatMapLatest { uid -> dao.observeAllFinishedExercises(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyMeasurements: StateFlow<List<BodyMeasurementEntity>> = _activeUserId
        .flatMapLatest { uid -> dao.observeBodyMeasurements(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            var users = dao.getAllUsersSnapshot()
            if (users.isEmpty()) {
                // Crear los 2 perfiles iniciales por defecto (Ignacio y Pareja)
                val id1 = dao.saveUserProfile(
                    UserProfileEntity(
                        id = 1,
                        name = "Ignacio",
                        avatarColorIndex = 9,
                        gender = 0,
                        onboardingDone = false
                    )
                ).toInt()
                users = dao.getAllUsersSnapshot()
                _activeUserId.value = id1
                GoogleDriveBackupManager.saveActiveUserId(appContext, id1)
            } else if (users.none { it.id == _activeUserId.value }) {
                val firstId = users.first().id
                _activeUserId.value = firstId
                GoogleDriveBackupManager.saveActiveUserId(appContext, firstId)
            }

            ensureUserMuscleFatigueInitialized(_activeUserId.value)
        }
    }

    private suspend fun ensureUserMuscleFatigueInitialized(uid: Int) {
        val existingFatigue = dao.getUserMuscleFatigue(uid)
        val now = System.currentTimeMillis()
        if (existingFatigue.isEmpty()) {
            dao.upsertMuscleFatigueList((0..16).map { MuscleFatigueEntity(userId = uid, muscleId = it, fatiguePercent = 0, lastUpdatedMillis = now) })
        } else {
            val decayed = existingFatigue.map {
                it.copy(
                    fatiguePercent = MuscleFatigueCalculator.computeCurrentFatigue(it, now),
                    lastUpdatedMillis = now
                )
            }
            dao.upsertMuscleFatigueList(decayed)
        }
    }

    private suspend fun triggerAutoDriveSync() {
        GoogleDriveBackupManager.autoSyncIfEnabled(appContext, dao)?.let { updated ->
            _driveSyncState.value = updated
        }
    }

    fun selectActiveUser(userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _activeUserId.value = userId
            GoogleDriveBackupManager.saveActiveUserId(appContext, userId)
            ensureUserMuscleFatigueInitialized(userId)
            triggerAutoDriveSync()
        }
    }

    fun createNewUserProfile(
        name: String,
        gender: Int,
        avatarColorIndex: Int,
        onCreated: (UserProfileEntity) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val newProfile = UserProfileEntity(
                name = name.ifBlank { "Usuario ${allUsers.value.size + 1}" },
                avatarColorIndex = avatarColorIndex,
                gender = gender,
                heightCm = if (gender == 0) 175f else 163f,
                weightKg = if (gender == 0) 75f else 60f,
                onboardingDone = false
            )
            val newId = dao.saveUserProfile(newProfile).toInt()
            val saved = newProfile.copy(id = newId)
            _activeUserId.value = newId
            GoogleDriveBackupManager.saveActiveUserId(appContext, newId)
            ensureUserMuscleFatigueInitialized(newId)
            triggerAutoDriveSync()
            launch(Dispatchers.Main) {
                onCreated(saved)
            }
        }
    }

    fun deleteUserProfile(userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUsers = dao.getAllUsersSnapshot()
            if (currentUsers.size <= 1) return@launch
            dao.deleteWorkoutPlansForUser(userId)
            dao.deleteUserProfile(userId)
            val remaining = dao.getAllUsersSnapshot()
            if (remaining.isNotEmpty()) {
                val nextId = remaining.first().id
                _activeUserId.value = nextId
                GoogleDriveBackupManager.saveActiveUserId(appContext, nextId)
            }
            triggerAutoDriveSync()
        }
    }

    fun linkAndExportToGoogleDrive(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            GoogleDriveBackupManager.persistDriveUriPermission(appContext, uri)
            GoogleDriveBackupManager.exportBackupToUri(appContext, dao, uri)
            _driveSyncState.value = GoogleDriveBackupManager.loadSyncState(appContext)
        }
    }

    fun syncNowToGoogleDrive() {
        val currentUriStr = _driveSyncState.value.linkedUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val uri = Uri.parse(currentUriStr)
            GoogleDriveBackupManager.exportBackupToUri(appContext, dao, uri)
            _driveSyncState.value = GoogleDriveBackupManager.loadSyncState(appContext)
        }
    }

    fun restoreFromGoogleDrive(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            GoogleDriveBackupManager.restoreBackupFromUri(appContext, dao, uri, linkAsDefaultDriveFile = true)
            _activeUserId.value = GoogleDriveBackupManager.getSavedActiveUserId(appContext)
            _driveSyncState.value = GoogleDriveBackupManager.loadSyncState(appContext)
        }
    }

    fun toggleGoogleDriveAutoBackup(enabled: Boolean) {
        _driveSyncState.value = GoogleDriveBackupManager.setAutoBackupEnabled(appContext, enabled)
        if (enabled) {
            syncNowToGoogleDrive()
        }
    }

    fun unlinkGoogleDrive() {
        _driveSyncState.value = GoogleDriveBackupManager.unlinkGoogleDrive(appContext)
    }

    fun getCatalogExercise(id: Int): CatalogExercise? = catalogMap[id]

    fun saveProfileAndGenerateSmartPlan(profile: UserProfileEntity, regeneratePlan: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            val updated = profile.copy(id = uid, onboardingDone = true)
            dao.saveUserProfile(updated)
            if (regeneratePlan) {
                val currentPlans = workoutPlans.value.filter { it.isCustomPeriodized && it.userId == uid }
                for (p in currentPlans) {
                    dao.deleteWorkoutPlan(p.id)
                }
                val phases = SmartWorkoutGenerator.generateThreePhaseCustomPlan(updated, catalogExercises)
                for (phase in phases) {
                    val planId = dao.insertWorkoutPlan(
                        WorkoutPlanEntity(
                            userId = uid,
                            name = phase.name,
                            description = phase.description,
                            isCustomPeriodized = true,
                            phaseNumber = phase.phaseNumber,
                            weeksDuration = 4,
                            completedSessionsCount = 0,
                            targetSessionsCount = updated.daysPerWeek * 4,
                            isActive = (phase.phaseNumber == 1)
                        )
                    )
                    phase.days.forEachIndexed { dIdx, daySpec ->
                        val dayId = dao.insertWorkoutDay(
                            WorkoutDayEntity(
                                workoutPlanId = planId,
                                name = daySpec.name,
                                colorIndex = daySpec.colorIndex,
                                orderIndex = dIdx
                            )
                        )
                        daySpec.exercises.forEachIndexed { eIdx, exSpec ->
                            dao.insertWorkoutExercise(
                                WorkoutExerciseEntity(
                                    workoutDayId = dayId,
                                    exerciseId = exSpec.exerciseId,
                                    setsCount = exSpec.sets,
                                    repsPerSetCsv = exSpec.repsCsv,
                                    loadsPerSetCsv = exSpec.loadsCsv,
                                    unitType = exSpec.unitType,
                                    restSeconds = exSpec.restSeconds,
                                    isBiSet = exSpec.isBiSet,
                                    orderIndex = eIdx
                                )
                            )
                        }
                    }
                }
            }
            triggerAutoDriveSync()
        }
    }

    fun importPrebuiltPlan(prebuilt: PrebuiltPlan, onImported: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            val planId = dao.insertWorkoutPlan(
                WorkoutPlanEntity(
                    userId = uid,
                    name = prebuilt.name,
                    description = prebuilt.description,
                    isCustomPeriodized = false,
                    phaseNumber = 1,
                    weeksDuration = 6,
                    completedSessionsCount = 0,
                    targetSessionsCount = (prebuilt.days.size.coerceAtLeast(3)) * 6,
                    isActive = true
                )
            )
            prebuilt.days.forEachIndexed { dIdx, pDay ->
                val dayId = dao.insertWorkoutDay(
                    WorkoutDayEntity(
                        workoutPlanId = planId,
                        name = pDay.name,
                        colorIndex = dIdx % 10,
                        orderIndex = dIdx
                    )
                )
                pDay.exercises.forEachIndexed { eIdx, pEx ->
                    val catEx = catalogMap[pEx.exerciseId]
                    val unitType = catEx?.defaultUnitType ?: 0
                    val sets = pEx.sets.coerceIn(1, 8)
                    val repsList = if (pEx.reps.isNotEmpty()) pEx.reps else List(sets) { 10 }
                    val loadsList = List(sets) { if (unitType == 0) "20" else "0" }
                    dao.insertWorkoutExercise(
                        WorkoutExerciseEntity(
                            workoutDayId = dayId,
                            exerciseId = pEx.exerciseId,
                            setsCount = sets,
                            repsPerSetCsv = repsList.joinToString(","),
                            loadsPerSetCsv = loadsList.joinToString(","),
                            unitType = unitType,
                            restSeconds = pEx.restSeconds,
                            isBiSet = pEx.isBiSet,
                            orderIndex = eIdx
                        )
                    )
                }
            }
            triggerAutoDriveSync()
            launch(Dispatchers.Main) { onImported(planId) }
        }
    }

    fun createManualWorkoutPlan(name: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            val planId = dao.insertWorkoutPlan(
                WorkoutPlanEntity(
                    userId = uid,
                    name = name.ifBlank { "Mi Plan de Entrenamiento" },
                    description = description,
                    isCustomPeriodized = false,
                    isActive = true
                )
            )
            dao.insertWorkoutDay(
                WorkoutDayEntity(
                    workoutPlanId = planId,
                    name = "Día A - Entrenamiento",
                    colorIndex = 0,
                    orderIndex = 0
                )
            )
            triggerAutoDriveSync()
        }
    }

    fun deleteWorkoutPlan(planId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteWorkoutPlan(planId)
            triggerAutoDriveSync()
        }
    }

    fun addWorkoutDay(planId: Long, name: String, colorIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentDays = dao.getWorkoutDays(planId)
            dao.insertWorkoutDay(
                WorkoutDayEntity(
                    workoutPlanId = planId,
                    name = name.ifBlank { "Día ${currentDays.size + 1}" },
                    colorIndex = colorIndex % 10,
                    orderIndex = currentDays.size
                )
            )
            triggerAutoDriveSync()
        }
    }

    fun updateWorkoutDayColorOrName(day: WorkoutDayEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateWorkoutDay(day)
            triggerAutoDriveSync()
        }
    }

    fun deleteWorkoutDay(dayId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteWorkoutDay(dayId)
            triggerAutoDriveSync()
        }
    }

    fun addExerciseToDay(
        dayId: Long,
        exerciseId: Int,
        sets: Int = 4,
        repsPerSet: String = "10,10,10,10",
        loadsPerSet: String = "20,20,20,20",
        restSeconds: Int = 60,
        isBiSet: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.getWorkoutExercises(dayId)
            val catEx = catalogMap[exerciseId]
            val unitType = catEx?.defaultUnitType ?: 0
            dao.insertWorkoutExercise(
                WorkoutExerciseEntity(
                    workoutDayId = dayId,
                    exerciseId = exerciseId,
                    setsCount = sets,
                    repsPerSetCsv = repsPerSet,
                    loadsPerSetCsv = loadsPerSet,
                    unitType = unitType,
                    restSeconds = restSeconds,
                    isBiSet = isBiSet,
                    orderIndex = existing.size
                )
            )
            triggerAutoDriveSync()
        }
    }

    fun updateWorkoutExercise(exercise: WorkoutExerciseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateWorkoutExercise(exercise)
            triggerAutoDriveSync()
        }
    }

    fun deleteWorkoutExercise(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteWorkoutExercise(id)
            triggerAutoDriveSync()
        }
    }

    fun createAndStartFreestyleSession(
        durationMinutes: Int,
        selectedMuscleIds: List<Int>,
        onDayCreated: (Long) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            val profile = dao.getUserProfile(uid) ?: UserProfileEntity(id = uid)
            val fatigueMap = muscleFatigueList.value.associate { it.muscleId to it.fatiguePercent }
            val generatedDay = SmartWorkoutGenerator.generateFreestyleWorkout(
                durationMinutes = durationMinutes,
                selectedMuscleIds = selectedMuscleIds,
                fatigueMap = fatigueMap,
                profile = profile,
                catalog = catalogExercises
            )

            val freestylePlan = workoutPlans.value.firstOrNull { it.name == "Entrenamientos Libres (Freestyle)" && it.userId == uid }
            val planId = freestylePlan?.id ?: dao.insertWorkoutPlan(
                WorkoutPlanEntity(
                    userId = uid,
                    name = "Entrenamientos Libres (Freestyle)",
                    description = "Sesiones generadas inteligentemente según tu recuperación muscular",
                    isCustomPeriodized = false,
                    isActive = true
                )
            )
            val dayId = dao.insertWorkoutDay(
                WorkoutDayEntity(
                    workoutPlanId = planId,
                    name = generatedDay.name,
                    colorIndex = generatedDay.colorIndex,
                    orderIndex = 0
                )
            )
            generatedDay.exercises.forEachIndexed { idx, spec ->
                dao.insertWorkoutExercise(
                    WorkoutExerciseEntity(
                        workoutDayId = dayId,
                        exerciseId = spec.exerciseId,
                        setsCount = spec.sets,
                        repsPerSetCsv = spec.repsCsv,
                        loadsPerSetCsv = spec.loadsCsv,
                        unitType = spec.unitType,
                        restSeconds = spec.restSeconds,
                        isBiSet = spec.isBiSet,
                        orderIndex = idx
                    )
                )
            }
            triggerAutoDriveSync()
            launch(Dispatchers.Main) {
                onDayCreated(dayId)
            }
        }
    }

    fun toggleFavorite(exerciseId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            if (favoriteIds.value.contains(exerciseId)) {
                dao.removeFavorite(uid, exerciseId)
            } else {
                dao.addFavorite(FavoriteExerciseEntity(userId = uid, exerciseId = exerciseId))
            }
            triggerAutoDriveSync()
        }
    }

    fun updateMuscleFatigueManually(muscleId: Int, newPercent: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            dao.upsertMuscleFatigue(
                MuscleFatigueEntity(
                    userId = uid,
                    muscleId = muscleId,
                    fatiguePercent = newPercent.coerceIn(0, 100),
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            )
            triggerAutoDriveSync()
        }
    }

    fun resetAllMuscleFatigue() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            val now = System.currentTimeMillis()
            dao.upsertMuscleFatigueList((0..16).map { MuscleFatigueEntity(userId = uid, muscleId = it, fatiguePercent = 0, lastUpdatedMillis = now) })
            triggerAutoDriveSync()
        }
    }

    fun finishWorkoutSession(
        planId: Long?,
        planName: String,
        dayName: String,
        durationSeconds: Int,
        completedLogs: List<CompletedExerciseLog>,
        onFinished: (Int, Map<Int, Int>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            val now = System.currentTimeMillis()
            val weightKg = userProfile.value?.weightKg ?: 75f
            val totalSets = completedLogs.sumOf { it.repsPerSet.size }
            val totalReps = completedLogs.sumOf { it.repsPerSet.sum() }
            val totalVolume = completedLogs.sumOf { log ->
                log.loadsPerSet.zip(log.repsPerSet).sumOf { (load, rep) -> (load * rep).toDouble() }
            }.toFloat()
            val calories = WorkoutMetricsCalculator.calculateEstimatedCalories(totalSets, weightKg, durationSeconds)

            val finishedDayId = dao.insertFinishedWorkoutDay(
                FinishedWorkoutDayEntity(
                    userId = uid,
                    planName = planName,
                    dayName = dayName,
                    timestampMillis = now,
                    durationSeconds = durationSeconds,
                    caloriesBurned = calories,
                    totalVolumeKg = totalVolume,
                    totalSets = totalSets,
                    totalReps = totalReps,
                    exercisesCount = completedLogs.size
                )
            )

            val finishedExEntities = completedLogs.map { log ->
                val maxLoad = log.loadsPerSet.maxOrNull() ?: 0f
                val avgLoad = if (log.loadsPerSet.isNotEmpty()) log.loadsPerSet.average().toFloat() else 0f
                val vol = log.loadsPerSet.zip(log.repsPerSet).sumOf { (l, r) -> (l * r).toDouble() }.toFloat()
                val repsSum = log.repsPerSet.sum()
                val detail = log.loadsPerSet.zip(log.repsPerSet)
                    .mapIndexed { idx, (l, r) -> "S${idx + 1}: ${r}x${l}kg" }
                    .joinToString(" | ")
                FinishedExerciseEntity(
                    userId = uid,
                    finishedDayId = finishedDayId,
                    exerciseId = log.exerciseId,
                    exerciseName = log.exerciseName,
                    muscles = log.muscles,
                    timestampMillis = now,
                    maxLoadKg = maxLoad,
                    avgLoadKg = avgLoad,
                    totalVolumeKg = vol,
                    totalReps = repsSum,
                    setsCount = log.repsPerSet.size,
                    setsDetailText = detail
                )
            }
            dao.insertFinishedExercises(finishedExEntities)

            if (planId != null) {
                val plan = dao.getWorkoutPlan(planId)
                if (plan != null) {
                    dao.updateWorkoutPlan(plan.copy(completedSessionsCount = plan.completedSessionsCount + 1))
                }
            }

            val currentMap = muscleFatigueList.value.associate { it.muscleId to it.fatiguePercent }
            val updatedMap = MuscleFatigueCalculator.accumulateFatigueForExercises(
                currentFatigueMap = currentMap,
                completedMuscleStrings = completedLogs.map { it.muscles }
            )
            dao.upsertMuscleFatigueList(
                updatedMap.map { (mId, pct) ->
                    MuscleFatigueEntity(userId = uid, muscleId = mId, fatiguePercent = pct, lastUpdatedMillis = now)
                }
            )

            triggerAutoDriveSync()

            launch(Dispatchers.Main) {
                onFinished(calories, updatedMap)
            }
        }
    }

    fun saveBodyMeasurement(measurement: BodyMeasurementEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = _activeUserId.value
            dao.insertBodyMeasurement(measurement.copy(userId = uid))
            val prof = dao.getUserProfile(uid)
            if (prof != null) {
                dao.saveUserProfile(prof.copy(weightKg = measurement.weightKg, heightCm = measurement.heightCm))
            }
            triggerAutoDriveSync()
        }
    }

    fun deleteBodyMeasurement(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteBodyMeasurement(id)
            triggerAutoDriveSync()
        }
    }
}
