package com.gymwp.free.domain

import com.gymwp.free.data.CatalogExercise
import com.gymwp.free.data.MuscleFatigueEntity
import com.gymwp.free.data.UserProfileEntity
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class MuscleGroupInfo(
    val id: Int,
    val nameEs: String,
    val macroGroup: String, // Pecho, Espalda, Hombros, Brazos, Piernas, Abdomen
    val isLargeGroup: Boolean
)

object MuscleCatalog {
    val ALL_MUSCLES = listOf(
        MuscleGroupInfo(0, "Hombros (Deltoides)", "Hombros", false),
        MuscleGroupInfo(1, "Pecho (Pectorales)", "Pecho", true),
        MuscleGroupInfo(2, "Bíceps", "Brazos", false),
        MuscleGroupInfo(3, "Antebrazos", "Brazos", false),
        MuscleGroupInfo(4, "Abdomen (Recto)", "Abdomen", false),
        MuscleGroupInfo(5, "Oblicuos", "Abdomen", false),
        MuscleGroupInfo(6, "Cuádriceps", "Piernas", true),
        MuscleGroupInfo(7, "Aductores", "Piernas", false),
        MuscleGroupInfo(8, "Abductores", "Piernas", false),
        MuscleGroupInfo(9, "Trapecio", "Hombros", false),
        MuscleGroupInfo(10, "Tríceps", "Brazos", false),
        MuscleGroupInfo(11, "Dorsales (Espalda Alta)", "Espalda", true),
        MuscleGroupInfo(12, "Lumbares (Espalda Baja)", "Espalda", false),
        MuscleGroupInfo(13, "Glúteos", "Piernas", false),
        MuscleGroupInfo(14, "Isquiotibiales (Femoral)", "Piernas", false),
        MuscleGroupInfo(15, "Gemelos (Pantorrillas)", "Piernas", false),
        MuscleGroupInfo(16, "Cardio / Aeróbico", "Cardio", false)
    )

    fun getName(id: Int): String = ALL_MUSCLES.getOrNull(id)?.nameEs ?: "Músculo $id"
}

/**
 * Clean-room implementation of CalculosFadigaMuscular (Lcl/g;)
 */
object MuscleFatigueCalculator {

    fun getRecoveryRatePerHour(muscleId: Int): Float {
        // Large muscles (Chest=1, Quads=6, Lats=11) recover 25% per 24h; others recover 30% per 24h
        return if (muscleId == 1 || muscleId == 6 || muscleId == 11) {
            25f / 24f
        } else {
            30f / 24f
        }
    }

    fun computeCurrentFatigue(entity: MuscleFatigueEntity, nowMillis: Long = System.currentTimeMillis()): Int {
        if (entity.fatiguePercent <= 0) return 0
        val elapsedHours = max(0L, nowMillis - entity.lastUpdatedMillis) / 3_600_000f
        if (elapsedHours < 0.25f) return entity.fatiguePercent.coerceIn(0, 100)
        val recovered = (elapsedHours * getRecoveryRatePerHour(entity.muscleId)).roundToInt()
        return (entity.fatiguePercent - recovered).coerceIn(0, 100)
    }

    fun hoursUntilFullRecovery(fatiguePercent: Int, muscleId: Int): Int {
        if (fatiguePercent <= 0) return 0
        val rate = getRecoveryRatePerHour(muscleId)
        return (fatiguePercent / rate).roundToInt()
    }

    /**
     * Applies fatigue delta from a completed exercise's 17-char muscle string:
     * '1' -> +10%, '2' -> +20%, '3' -> +30%
     */
    fun accumulateFatigueForExercises(
        currentFatigueMap: Map<Int, Int>,
        completedMuscleStrings: List<String>
    ): Map<Int, Int> {
        val updated = currentFatigueMap.toMutableMap()
        for (m in 0..16) {
            if (!updated.containsKey(m)) updated[m] = 0
        }
        for (musclesStr in completedMuscleStrings) {
            for (m in 0 until min(17, musclesStr.length)) {
                val delta = when (musclesStr[m]) {
                    '3' -> 30
                    '2' -> 20
                    '1' -> 10
                    else -> 0
                }
                if (delta > 0) {
                    updated[m] = ((updated[m] ?: 0) + delta).coerceIn(0, 100)
                }
            }
        }
        return updated
    }

    fun getFatigueStatusLabel(percent: Int): String = when {
        percent < 20 -> "Recuperado"
        percent < 45 -> "Fatiga Leve"
        percent < 70 -> "Fatiga Moderada"
        else -> "Fatiga Alta"
    }
}

/**
 * Clean-room implementation of CalculosInformacoesTreino (Lcl/d;)
 */
object WorkoutMetricsCalculator {

    fun calculateCaloriesPerSet(weightKg: Float): Int = when {
        weightKg < 63f -> 6
        weightKg < 75f -> 7
        weightKg < 87f -> 8
        else -> 9
    }

    fun calculateEstimatedCalories(totalSets: Int, weightKg: Float, durationSeconds: Int = 0): Int {
        val base = totalSets * calculateCaloriesPerSet(weightKg)
        val timeBonus = (durationSeconds / 180)
        return max(15, base + timeBonus)
    }

    fun estimateDurationWindowMinutes(totalSets: Int, exercisesCount: Int): Pair<Int, Int> {
        val minMinutes = max(10, totalSets * 2 + exercisesCount)
        val maxMinutes = (minMinutes * 1.25f).roundToInt() + 5
        return minMinutes to maxMinutes
    }
}

/**
 * Clean-room implementation of CalculosImcPesoIdeal (Lcl/a;) & Body Fat Deurenberg (Ldl/b;)
 */
object BodyMetricsCalculator {

    fun calculateBmi(weightKg: Float, heightCm: Float): Float {
        val hMeters = (heightCm / 100f).coerceAtLeast(0.5f)
        return weightKg / (hMeters * hMeters)
    }

    fun getBmiClassificationEs(bmi: Float): String = when {
        bmi < 16.0f -> "Delgadez Severa"
        bmi < 17.0f -> "Delgadez Moderada"
        bmi < 18.5f -> "Delgadez Leve"
        bmi < 25.0f -> "Peso Normal (Saludable)"
        bmi < 30.0f -> "Sobrepeso"
        bmi < 35.0f -> "Obesidad Clase I"
        bmi < 40.0f -> "Obesidad Clase II"
        else -> "Obesidad Clase III"
    }

    fun calculateIdealWeightRangeKg(heightCm: Float): Pair<Float, Float> {
        val hMeters = (heightCm / 100f).coerceAtLeast(0.5f)
        val h2 = hMeters * hMeters
        return (18.5f * h2) to (24.9f * h2)
    }

    fun calculateDeurenbergBodyFat(weightKg: Float, heightCm: Float, birthYear: Int, gender: Int): Float {
        val bmi = calculateBmi(weightKg, heightCm)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val age = (currentYear - birthYear).coerceIn(14, 95)
        val genderFactor = if (gender == 0) 1f else 0f // 1 = Masculino, 0 = Femenino
        val bf = (1.20f * bmi) - (10.8f * genderFactor) + (0.23f * age) - 8.305328f
        return bf.coerceIn(4f, 55f)
    }
}

/**
 * Clean-room implementation of Smart Workout Generator (Lhh/c; & Lih/d;)
 */
object SmartWorkoutGenerator {

    data class GeneratedExerciseSpec(
        val exerciseId: Int,
        val sets: Int,
        val repsCsv: String,
        val loadsCsv: String,
        val restSeconds: Int,
        val unitType: Int,
        val isBiSet: Boolean = false
    )

    data class GeneratedDaySpec(
        val name: String,
        val colorIndex: Int,
        val exercises: List<GeneratedExerciseSpec>
    )

    data class GeneratedPhaseSpec(
        val phaseNumber: Int,
        val name: String,
        val description: String,
        val days: List<GeneratedDaySpec>
    )

    private fun filterByPlace(catalog: List<CatalogExercise>, trainingPlace: Int): List<CatalogExercise> {
        return when (trainingPlace) {
            2 -> catalog.filter { it.equipment.contains("Peso Corporal") || it.equipment.contains("Barra Fija") || it.equipment.isEmpty() }
            1 -> catalog.filter {
                it.equipment.contains("Mancuernas") ||
                it.equipment.contains("Barra") ||
                it.equipment.contains("Banco") ||
                it.equipment.contains("Peso Corporal")
            }
            else -> catalog
        }.ifEmpty { catalog }
    }

    private fun pickExercisesForMuscles(
        pool: List<CatalogExercise>,
        primaryMuscles: List<Int>,
        countPerMuscle: Int,
        usedIds: MutableSet<Int>
    ): List<CatalogExercise> {
        val result = mutableListOf<CatalogExercise>()
        for (mId in primaryMuscles) {
            val candidates = pool
                .filter { it.id !in usedIds && mId < it.muscles.length && it.muscles[mId] >= '2' }
                .sortedByDescending { ex ->
                    val primaryScore = if (ex.muscles[mId] == '3') 30 else 20
                    val compoundBonus = ex.muscles.count { it >= '1' } * 3
                    primaryScore + compoundBonus - (ex.id / 50)
                }
            val picked = candidates.take(countPerMuscle)
            picked.forEach { usedIds.add(it.id) }
            result.addAll(picked)
        }
        return result
    }

    /**
     * Generates the 3-Phase Periodized Custom Workout Plan (Adaptación, Hipertrofia, Fuerza)
     */
    fun generateThreePhaseCustomPlan(
        profile: UserProfileEntity,
        catalog: List<CatalogExercise>
    ): List<GeneratedPhaseSpec> {
        val pool = filterByPlace(catalog, profile.trainingPlace)
        val daysCount = profile.daysPerWeek.coerceIn(2, 6)

        val splitTemplate: List<Pair<String, List<Int>>> = when (daysCount) {
            2 -> listOf(
                "Día A - Tren Superior" to listOf(1, 11, 0, 2, 10, 4),
                "Día B - Tren Inferior y Core" to listOf(6, 14, 13, 15, 12, 4)
            )
            3 -> listOf(
                "Día A - Empuje (Pecho, Hombros y Tríceps)" to listOf(1, 0, 10, 4),
                "Día B - Tirón (Espalda, Trapecio y Bíceps)" to listOf(11, 9, 12, 2, 3),
                "Día C - Piernas y Glúteos" to listOf(6, 14, 13, 7, 15, 4)
            )
            4 -> listOf(
                "Día A - Pecho y Tríceps" to listOf(1, 10, 4),
                "Día B - Espalda y Bíceps" to listOf(11, 12, 2, 3),
                "Día C - Piernas (Cuádriceps y Gemelos)" to listOf(6, 7, 15, 4),
                "Día D - Hombros, Femoral y Glúteos" to listOf(0, 9, 14, 13)
            )
            else -> listOf(
                "Día A - Pecho y Abdomen" to listOf(1, 4, 5),
                "Día B - Espalda y Lumbares" to listOf(11, 12, 9),
                "Día C - Cuádriceps y Pantorrillas" to listOf(6, 7, 15),
                "Día D - Hombros y Trapecio" to listOf(0, 9, 4),
                "Día E - Bíceps, Tríceps y Femoral/Glúteos" to listOf(2, 10, 14, 13)
            )
        }

        val phaseConfigs = listOf(
            Triple(1, "Fase 1: Adaptación y Resistencia", "Acondicionamiento neuromuscular y técnica (4 semanas)"),
            Triple(2, "Fase 2: Hipertrofia Muscular", "Aumento de volumen y densidad muscular (4 semanas)"),
            Triple(3, "Fase 3: Fuerza y Potencia", "Progresión de cargas máximas y reclutamiento (4 semanas)")
        )

        return phaseConfigs.map { (phaseNum, phaseTitle, phaseDesc) ->
            val usedIds = mutableSetOf<Int>()
            val sets = when (phaseNum) {
                1 -> 3
                2 -> 4
                else -> 4
            }
            val repsStr = when (phaseNum) {
                1 -> List(sets) { "15" }.joinToString(",")
                2 -> List(sets) { "10" }.joinToString(",")
                else -> List(sets) { "6" }.joinToString(",")
            }
            val defaultLoadsStr = when (phaseNum) {
                1 -> List(sets) { "15" }.joinToString(",")
                2 -> List(sets) { "22" }.joinToString(",")
                else -> List(sets) { "30" }.joinToString(",")
            }
            val restSec = when (phaseNum) {
                1 -> 45
                2 -> 60
                else -> 90
            }

            val days = splitTemplate.mapIndexed { idx, (dayTitle, muscleIds) ->
                val countPerMuscle = if (muscleIds.size <= 3) 2 else 1
                val chosen = pickExercisesForMuscles(pool, muscleIds, countPerMuscle, usedIds)
                val specs = chosen.map { ex ->
                    GeneratedExerciseSpec(
                        exerciseId = ex.id,
                        sets = sets,
                        repsCsv = repsStr,
                        loadsCsv = if (ex.defaultUnitType == 0) defaultLoadsStr else List(sets) { "0" }.joinToString(","),
                        restSeconds = restSec,
                        unitType = ex.defaultUnitType
                    )
                }
                GeneratedDaySpec(
                    name = dayTitle,
                    colorIndex = (idx + phaseNum - 1) % 10,
                    exercises = specs
                )
            }

            GeneratedPhaseSpec(
                phaseNumber = phaseNum,
                name = phaseTitle,
                description = phaseDesc,
                days = days
            )
        }
    }

    /**
     * Generates an instant Freestyle Workout (Lih/d;) based on target duration and recovered muscles
     */
    fun generateFreestyleWorkout(
        durationMinutes: Int,
        selectedMuscleIds: List<Int>,
        fatigueMap: Map<Int, Int>,
        profile: UserProfileEntity,
        catalog: List<CatalogExercise>
    ): GeneratedDaySpec {
        val targetExerciseCount = (durationMinutes / 6.0f).roundToInt().coerceIn(3, 12)
        val pool = filterByPlace(catalog, profile.trainingPlace)

        val targetMuscles = if (selectedMuscleIds.isNotEmpty()) {
            selectedMuscleIds
        } else {
            // Auto-select muscles with < 40% fatigue
            (0..15).filter { (fatigueMap[it] ?: 0) < 40 }
                .sortedBy { fatigueMap[it] ?: 0 }
                .take(4)
                .ifEmpty { listOf(1, 11, 6, 0) }
        }

        val usedIds = mutableSetOf<Int>()
        val chosen = mutableListOf<CatalogExercise>()
        var round = 0
        while (chosen.size < targetExerciseCount && round < 4) {
            val batch = pickExercisesForMuscles(pool, targetMuscles, 1, usedIds)
            if (batch.isEmpty()) break
            chosen.addAll(batch)
            round++
        }

        val specs = chosen.take(targetExerciseCount).map { ex ->
            GeneratedExerciseSpec(
                exerciseId = ex.id,
                sets = 4,
                repsCsv = "12,10,10,8",
                loadsCsv = if (ex.defaultUnitType == 0) "20,22,22,25" else "0,0,0,0",
                restSeconds = 60,
                unitType = ex.defaultUnitType
            )
        }

        return GeneratedDaySpec(
            name = "Entrenamiento Libre (${durationMinutes} min)",
            colorIndex = 5,
            exercises = specs
        )
    }
}
