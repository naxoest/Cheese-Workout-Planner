package com.gymwp.free.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymwp.free.data.*
import com.gymwp.free.domain.MuscleFatigueCalculator
import com.gymwp.free.domain.SmartWorkoutGenerator
import com.gymwp.free.domain.WorkoutMetricsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CompletedExerciseLog(
    val exerciseId: Int,
    val exerciseName: String,
    val muscles: String,
    val loadsPerSet: List<Float>,
    val repsPerSet: List<Int>
)

class GymWpViewModel(application: Application) : AndroidViewModel(application) {

    private val db = GymWpDatabase.getInstance(application)
    private val dao = db.dao()

    val catalogExercises: List<CatalogExercise> = AssetCatalogRepository.getExercises(application)
    val prebuiltPlans: List<PrebuiltPlan> = AssetCatalogRepository.getPrebuiltPlans(application)
    private val catalogMap: Map<Int, CatalogExercise> = catalogExercises.associateBy { it.id }

    val userProfile: StateFlow<UserProfileEntity?> = dao.observeUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val workoutPlans: StateFlow<List<WorkoutPlanEntity>> = dao.observeWorkoutPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkoutDays: StateFlow<List<WorkoutDayEntity>> = dao.observeAllWorkoutDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkoutExercises: StateFlow<List<WorkoutExerciseEntity>> = dao.observeAllWorkoutExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userCustomExercises: StateFlow<List<UserCustomExerciseEntity>> = dao.observeUserCustomExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<Int>> = dao.observeFavorites()
        .map { list -> list.map { it.exerciseId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val muscleFatigueList: StateFlow<List<MuscleFatigueEntity>> = dao.observeMuscleFatigue()
        .map { list ->
            val now = System.currentTimeMillis()
            val byId = list.associateBy { it.muscleId }
            (0..16).map { mId ->
                val entity = byId[mId] ?: MuscleFatigueEntity(mId, 0, now)
                entity.copy(fatiguePercent = MuscleFatigueCalculator.computeCurrentFatigue(entity, now))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finishedWorkouts: StateFlow<List<FinishedWorkoutDayEntity>> = dao.observeFinishedWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFinishedExercises: StateFlow<List<FinishedExerciseEntity>> = dao.observeAllFinishedExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyMeasurements: StateFlow<List<BodyMeasurementEntity>> = dao.observeBodyMeasurements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Seed 17 muscles if empty
            val existingFatigue = dao.getAllMuscleFatigue()
            if (existingFatigue.isEmpty()) {
                val now = System.currentTimeMillis()
                dao.upsertMuscleFatigueList((0..16).map { MuscleFatigueEntity(it, 0, now) })
            } else {
                // Persist hourly recovery decay
                val now = System.currentTimeMillis()
                val decayed = existingFatigue.map {
                    it.copy(
                        fatiguePercent = MuscleFatigueCalculator.computeCurrentFatigue(it, now),
                        lastUpdatedMillis = now
                    )
                }
                dao.upsertMuscleFatigueList(decayed)
            }

            // Ensure default profile exists
            if (dao.getUserProfile() == null) {
                dao.saveUserProfile(UserProfileEntity())
            }
        }
    }

    fun getCatalogExercise(id: Int): CatalogExercise? = catalogMap[id]

    fun saveProfileAndGenerateSmartPlan(profile: UserProfileEntity, regeneratePlan: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = profile.copy(onboardingDone = true)
            dao.saveUserProfile(updated)
            if (regeneratePlan) {
                // Remove old custom periodized plans
                val currentPlans = workoutPlans.value.filter { it.isCustomPeriodized }
                for (p in currentPlans) {
                    dao.deleteWorkoutPlan(p.id)
                }
                val phases = SmartWorkoutGenerator.generateThreePhaseCustomPlan(updated, catalogExercises)
                for (phase in phases) {
                    val planId = dao.insertWorkoutPlan(
                        WorkoutPlanEntity(
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
        }
    }

    fun importPrebuiltPlan(prebuilt: PrebuiltPlan, onImported: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val planId = dao.insertWorkoutPlan(
                WorkoutPlanEntity(
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
            launch(Dispatchers.Main) { onImported(planId) }
        }
    }

    fun createManualWorkoutPlan(name: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val planId = dao.insertWorkoutPlan(
                WorkoutPlanEntity(
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
        }
    }

    fun deleteWorkoutPlan(planId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteWorkoutPlan(planId)
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
        }
    }

    fun updateWorkoutDayColorOrName(day: WorkoutDayEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateWorkoutDay(day)
        }
    }

    fun deleteWorkoutDay(dayId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteWorkoutDay(dayId)
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
        }
    }

    fun updateWorkoutExercise(exercise: WorkoutExerciseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateWorkoutExercise(exercise)
        }
    }

    fun deleteWorkoutExercise(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteWorkoutExercise(id)
        }
    }

    fun createAndStartFreestyleSession(
        durationMinutes: Int,
        selectedMuscleIds: List<Int>,
        onDayCreated: (Long) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = dao.getUserProfile() ?: UserProfileEntity()
            val fatigueMap = muscleFatigueList.value.associate { it.muscleId to it.fatiguePercent }
            val generatedDay = SmartWorkoutGenerator.generateFreestyleWorkout(
                durationMinutes = durationMinutes,
                selectedMuscleIds = selectedMuscleIds,
                fatigueMap = fatigueMap,
                profile = profile,
                catalog = catalogExercises
            )

            // Find or create Freestyle container plan
            var freestylePlan = workoutPlans.value.firstOrNull { it.name == "Entrenamientos Libres (Freestyle)" }
            val planId = freestylePlan?.id ?: dao.insertWorkoutPlan(
                WorkoutPlanEntity(
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
            launch(Dispatchers.Main) {
                onDayCreated(dayId)
            }
        }
    }

    fun toggleFavorite(exerciseId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (favoriteIds.value.contains(exerciseId)) {
                dao.removeFavorite(exerciseId)
            } else {
                dao.addFavorite(FavoriteExerciseEntity(exerciseId))
            }
        }
    }

    fun updateMuscleFatigueManually(muscleId: Int, newPercent: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.upsertMuscleFatigue(
                MuscleFatigueEntity(
                    muscleId = muscleId,
                    fatiguePercent = newPercent.coerceIn(0, 100),
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun resetAllMuscleFatigue() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.upsertMuscleFatigueList((0..16).map { MuscleFatigueEntity(it, 0, now) })
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

            // Increment plan completed count if applicable
            if (planId != null) {
                val plan = dao.getWorkoutPlan(planId)
                if (plan != null) {
                    dao.updateWorkoutPlan(plan.copy(completedSessionsCount = plan.completedSessionsCount + 1))
                }
            }

            // Update Muscle Fatigue (+10% / +20% / +30%)
            val currentMap = muscleFatigueList.value.associate { it.muscleId to it.fatiguePercent }
            val updatedMap = MuscleFatigueCalculator.accumulateFatigueForExercises(
                currentFatigueMap = currentMap,
                completedMuscleStrings = completedLogs.map { it.muscles }
            )
            dao.upsertMuscleFatigueList(
                updatedMap.map { (mId, pct) ->
                    MuscleFatigueEntity(muscleId = mId, fatiguePercent = pct, lastUpdatedMillis = now)
                }
            )

            launch(Dispatchers.Main) {
                onFinished(calories, updatedMap)
            }
        }
    }

    fun saveBodyMeasurement(measurement: BodyMeasurementEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertBodyMeasurement(measurement)
            // Also sync latest weight & height to UserProfile
            val prof = dao.getUserProfile()
            if (prof != null) {
                dao.saveUserProfile(prof.copy(weightKg = measurement.weightKg, heightCm = measurement.heightCm))
            }
        }
    }

    fun deleteBodyMeasurement(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteBodyMeasurement(id)
        }
    }
}
