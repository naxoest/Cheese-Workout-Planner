package com.gymwp.free.domain

import com.gymwp.free.data.CatalogExercise
import com.gymwp.free.data.FinishedExerciseEntity
import com.gymwp.free.data.MuscleFatigueEntity
import com.gymwp.free.data.UserProfileEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
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

data class SuggestedNutritionTargets(
    val goalLabel: String,
    val targetKcal: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val waterLiters: Float
)

/**
 * Clean-room implementation of CalculosImcPesoIdeal (Lcl/a;), Body Fat (Deurenberg & U.S. Navy),
 * FFMI (Fat-Free Mass Index), BMR/TDEE (Mifflin-St Jeor) & Cardiometabolic Ratios
 */
object BodyMetricsCalculator {

    fun calculateAge(birthYear: Int): Int {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        return (currentYear - birthYear).coerceIn(14, 95)
    }

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
        val age = calculateAge(birthYear)
        val genderFactor = if (gender == 0) 1f else 0f // 1 = Masculino, 0 = Femenino
        val bf = (1.20f * bmi) - (10.8f * genderFactor) + (0.23f * age) - 8.305328f
        return bf.coerceIn(4f, 55f)
    }

    /**
     * Fórmula antropométrica de la Marina de EE.UU. (U.S. Navy) con cinta métrica
     */
    fun calculateNavyBodyFat(
        gender: Int,
        heightCm: Float,
        neckCm: Float,
        waistCm: Float,
        hipCm: Float = 0f
    ): Float? {
        if (heightCm < 100f || neckCm < 15f || waistCm <= neckCm) return null
        return if (gender == 0) {
            val diff = waistCm - neckCm
            if (diff <= 1f) null
            else {
                val denom = 1.0324f - 0.19077f * log10(diff) + 0.15456f * log10(heightCm)
                if (denom <= 0.1f) null else ((495f / denom) - 450f).coerceIn(3f, 55f)
            }
        } else {
            if (hipCm < 40f) return null
            val sum = waistCm + hipCm - neckCm
            if (sum <= 1f) null
            else {
                val denom = 1.29579f - 0.35004f * log10(sum) + 0.22100f * log10(heightCm)
                if (denom <= 0.1f) null else ((495f / denom) - 450f).coerceIn(8f, 60f)
            }
        }
    }

    fun getBodyFatCategoryEs(bfPercent: Float, gender: Int): String {
        return if (gender == 0) {
            when {
                bfPercent < 6f -> "Grasa Esencial"
                bfPercent < 14f -> "Nivel Atlético"
                bfPercent < 18f -> "Fitness / Definido"
                bfPercent < 25f -> "Rango Saludable"
                else -> "Porcentaje Elevado"
            }
        } else {
            when {
                bfPercent < 14f -> "Grasa Esencial"
                bfPercent < 21f -> "Nivel Atlético"
                bfPercent < 25f -> "Fitness / Definida"
                bfPercent < 32f -> "Rango Saludable"
                else -> "Porcentaje Elevado"
            }
        }
    }

    fun calculateFatMassKg(weightKg: Float, bfPercent: Float): Float {
        return (weightKg * (bfPercent.coerceIn(3f, 65f) / 100f)).coerceAtLeast(1f)
    }

    fun calculateLeanMassKg(weightKg: Float, bfPercent: Float): Float {
        return (weightKg - calculateFatMassKg(weightKg, bfPercent)).coerceAtLeast(15f)
    }

    /**
     * Retorna Pair(FFMI crudo, FFMI normalizado a 1.80 m)
     */
    fun calculateFfmi(weightKg: Float, heightCm: Float, bfPercent: Float): Pair<Float, Float> {
        val hMeters = (heightCm / 100f).coerceAtLeast(1.0f)
        val leanKg = calculateLeanMassKg(weightKg, bfPercent)
        val rawFfmi = leanKg / (hMeters * hMeters)
        val normalizedFfmi = rawFfmi + 6.1f * (1.80f - hMeters)
        return rawFfmi to normalizedFfmi
    }

    fun getFfmiClassificationEs(normalizedFfmi: Float, gender: Int): String {
        return if (gender == 0) {
            when {
                normalizedFfmi < 18f -> "Complexión Delgada"
                normalizedFfmi < 20f -> "Masa Muscular Promedio"
                normalizedFfmi < 22f -> "Atlético / Buena Masa"
                normalizedFfmi < 25f -> "Musculatura Avanzada"
                else -> "Musculatura Nivel Élite"
            }
        } else {
            when {
                normalizedFfmi < 14f -> "Complexión Delgada"
                normalizedFfmi < 16f -> "Masa Muscular Promedio"
                normalizedFfmi < 18f -> "Atlética / Buena Masa"
                normalizedFfmi < 21f -> "Musculatura Avanzada"
                else -> "Musculatura Nivel Élite"
            }
        }
    }

    fun calculateWaistToHeightRatio(waistCm: Float, heightCm: Float): Float? {
        if (waistCm <= 20f || heightCm <= 100f) return null
        return waistCm / heightCm
    }

    fun getWhtrStatusEs(ratio: Float): String = when {
        ratio < 0.40f -> "Delgado"
        ratio < 0.50f -> "Óptimo Cardiovascular"
        ratio < 0.60f -> "Riesgo Moderado"
        else -> "Riesgo Elevado"
    }

    fun calculateWaistToHipRatio(waistCm: Float, hipCm: Float): Float? {
        if (waistCm <= 20f || hipCm <= 30f) return null
        return waistCm / hipCm
    }

    fun getWhrStatusEs(whr: Float, gender: Int): String {
        return if (gender == 0) {
            when {
                whr < 0.90f -> "Saludable (< 0.90)"
                whr < 0.99f -> "Moderado"
                else -> "Elevado"
            }
        } else {
            when {
                whr < 0.80f -> "Saludable (< 0.80)"
                whr < 0.85f -> "Moderado"
                else -> "Elevado"
            }
        }
    }

    fun calculateShoulderToWaistRatio(shoulderCm: Float, waistCm: Float): Float? {
        if (shoulderCm <= 40f || waistCm <= 20f) return null
        return shoulderCm / waistCm
    }

    fun calculateBmrMifflin(weightKg: Float, heightCm: Float, birthYear: Int, gender: Int): Int {
        val age = calculateAge(birthYear)
        val sexOffset = if (gender == 0) 5f else -161f
        return (10f * weightKg + 6.25f * heightCm - 5f * age + sexOffset).roundToInt().coerceAtLeast(950)
    }

    fun calculateTdee(bmr: Int, daysPerWeek: Int): Int {
        val factor = when {
            daysPerWeek <= 1 -> 1.25f
            daysPerWeek == 2 -> 1.375f
            daysPerWeek in 3..4 -> 1.50f
            daysPerWeek == 5 -> 1.62f
            else -> 1.725f
        }
        return (bmr * factor).roundToInt()
    }

    fun calculateSuggestedNutrition(
        weightKg: Float,
        tdee: Int,
        goal: Int,
        daysPerWeek: Int
    ): SuggestedNutritionTargets {
        val (goalLabel, targetKcal, proteinPerKg) = when (goal) {
            0 -> Triple("Superávit Limpio (Hipertrofia)", tdee + 280, 2.0f)
            1 -> Triple("Déficit Moderado (Definición)", (tdee - 400).coerceAtLeast(1200), 2.1f)
            2 -> Triple("Fuerza y Rendimiento", tdee + 180, 1.9f)
            else -> Triple("Mantenimiento y Salud", tdee, 1.7f)
        }
        val proteinG = (weightKg * proteinPerKg).roundToInt().coerceAtLeast(70)
        val fatG = (weightKg * 0.85f).roundToInt().coerceAtLeast(40)
        val remainingKcal = (targetKcal - (proteinG * 4) - (fatG * 9)).coerceAtLeast(240)
        val carbsG = (remainingKcal / 4f).roundToInt()
        val waterL = (weightKg * 0.035f) + (daysPerWeek.coerceIn(0, 7) * 0.08f)

        return SuggestedNutritionTargets(
            goalLabel = goalLabel,
            targetKcal = targetKcal,
            proteinGrams = proteinG,
            carbsGrams = carbsG,
            fatGrams = fatG,
            waterLiters = waterL
        )
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

data class OverloadSuggestion(
    val summaryText: String,
    val buttonLabel: String,
    val suggestedLoadKg: Float?,
    val suggestedRepsOrTime: Int?
)

data class ParsedFinishedSet(
    val setNumber: Int,
    val repsDisplay: String,
    val loadDisplay: String?
)

/**
 * Sugeridor de Sobrecarga Progresiva gradual y parser de series ejecutadas.
 */
object ProgressiveOverloadAdvisor {

    private val setLoadRegex = Regex("""^(\d+)\s*[x×]\s*([0-9.,]+)\s*kg$""", RegexOption.IGNORE_CASE)
    private val setUnitOnlyRegex = Regex("""^(\d+)\s*(reps?|seg|s|min)$""", RegexOption.IGNORE_CASE)
    private val leadingIntRegex = Regex("""^(\d+)""")

    fun formatWeight(value: Float): String {
        val clamped = value.coerceAtLeast(0f)
        val rounded = kotlin.math.round(clamped * 100f) / 100f
        return if (rounded % 1f == 0f) {
            rounded.toInt().toString()
        } else if ((rounded * 10f) % 1f == 0f) {
            String.format(Locale.US, "%.1f", rounded)
        } else {
            String.format(Locale.US, "%.2f", rounded)
        }
    }

    private fun computeRelativeDateLabel(timestampMillis: Long, nowMillis: Long): String {
        val calThen = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calNow = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((calNow.timeInMillis - calThen.timeInMillis) / 86_400_000L).toInt().coerceAtLeast(0)
        return when {
            diffDays == 0 -> "hoy"
            diffDays == 1 -> "ayer"
            diffDays in 2..4 -> "hace $diffDays días"
            diffDays in 5..11 -> "sem. pasada"
            else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(timestampMillis))
        }
    }

    private fun extractBestSetReps(entity: FinishedExerciseEntity, fallbackDefault: Int): Int {
        if (entity.setsDetailText.isNotBlank()) {
            val parsedVals = entity.setsDetailText.split("|").mapNotNull { chunk ->
                val body = chunk.substringAfter(":", chunk).trim()
                leadingIntRegex.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            val best = parsedVals.maxOrNull()
            if (best != null && best > 0) return best
        }
        if (entity.setsCount > 0 && entity.totalReps > 0) {
            return (entity.totalReps.toFloat() / entity.setsCount).roundToInt().coerceAtLeast(1)
        }
        return fallbackDefault
    }

    fun computeSuggestion(
        unitType: Int,
        historyForExercise: List<FinishedExerciseEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): OverloadSuggestion? {
        val latest = historyForExercise.maxByOrNull { it.timestampMillis } ?: return null
        val whenLbl = computeRelativeDateLabel(latest.timestampMillis, nowMillis)
        val prefix = if (whenLbl == "sem. pasada") "Sem. pasada" else "Última vez ($whenLbl)"

        return when (unitType) {
            0 -> {
                if (latest.maxLoadKg > 0f) {
                    val lastKg = formatWeight(latest.maxLoadKg)
                    val nextLoad = latest.maxLoadKg + 2.5f
                    val nextKg = formatWeight(nextLoad)
                    OverloadSuggestion(
                        summaryText = "$prefix: $lastKg kg → Prueba $nextKg kg",
                        buttonLabel = "Usar $nextKg kg",
                        suggestedLoadKg = nextLoad,
                        suggestedRepsOrTime = null
                    )
                } else {
                    val lastReps = extractBestSetReps(latest, 10)
                    val nextReps = lastReps + 2
                    OverloadSuggestion(
                        summaryText = "$prefix: $lastReps reps → Prueba $nextReps reps",
                        buttonLabel = "Usar $nextReps reps",
                        suggestedLoadKg = null,
                        suggestedRepsOrTime = nextReps
                    )
                }
            }
            2 -> {
                val lastSec = extractBestSetReps(latest, 30)
                val nextSec = lastSec + 10
                OverloadSuggestion(
                    summaryText = "$prefix: ${lastSec}s → Prueba ${nextSec}s",
                    buttonLabel = "Usar ${nextSec}s",
                    suggestedLoadKg = null,
                    suggestedRepsOrTime = nextSec
                )
            }
            3 -> {
                val lastMin = extractBestSetReps(latest, 10)
                val nextMin = lastMin + 2
                OverloadSuggestion(
                    summaryText = "$prefix: $lastMin min → Prueba $nextMin min",
                    buttonLabel = "Usar $nextMin min",
                    suggestedLoadKg = null,
                    suggestedRepsOrTime = nextMin
                )
            }
            else -> {
                val lastReps = extractBestSetReps(latest, 10)
                val nextReps = lastReps + 2
                OverloadSuggestion(
                    summaryText = "$prefix: $lastReps reps → Prueba $nextReps reps",
                    buttonLabel = "Usar $nextReps reps",
                    suggestedLoadKg = null,
                    suggestedRepsOrTime = nextReps
                )
            }
        }
    }

    fun isAlreadyApplied(
        suggestion: OverloadSuggestion,
        loadsTokens: List<String>,
        repsTokens: List<String>
    ): Boolean {
        return when {
            suggestion.suggestedLoadKg != null -> {
                loadsTokens.isNotEmpty() && loadsTokens.all { token ->
                    val parsed = token.trim().replace(',', '.').toFloatOrNull() ?: -999f
                    abs(parsed - suggestion.suggestedLoadKg) < 0.05f
                }
            }
            suggestion.suggestedRepsOrTime != null -> {
                repsTokens.isNotEmpty() && repsTokens.all { token ->
                    (token.trim().toIntOrNull() ?: -999) == suggestion.suggestedRepsOrTime
                }
            }
            else -> false
        }
    }

    fun parseSetsDetail(
        setsDetailText: String,
        unitType: Int?,
        fallbackSetsCount: Int,
        fallbackTotalReps: Int,
        fallbackMaxLoadKg: Float
    ): List<ParsedFinishedSet> {
        val defaultUnitSuffix = when (unitType) {
            2 -> "seg"
            3 -> "min"
            else -> "reps"
        }

        if (setsDetailText.isNotBlank()) {
            val chunks = setsDetailText.split("|").map { it.trim() }.filter { it.isNotBlank() }
            if (chunks.isNotEmpty()) {
                return chunks.mapIndexed { idx, chunk ->
                    val setNum = chunk.substringBefore(":", "")
                        .trim()
                        .removePrefix("S")
                        .removePrefix("s")
                        .toIntOrNull() ?: (idx + 1)
                    val body = chunk.substringAfter(":", chunk).trim()

                    val loadMatch = setLoadRegex.matchEntire(body)
                    val unitOnlyMatch = setUnitOnlyRegex.matchEntire(body)

                    when {
                        loadMatch != null -> {
                            val r = loadMatch.groupValues[1].toIntOrNull() ?: 0
                            val l = loadMatch.groupValues[2].replace(',', '.').toFloatOrNull() ?: 0f
                            if ((unitType != null && unitType != 0 && l <= 0f) || (unitType == 1 && l <= 0f)) {
                                ParsedFinishedSet(
                                    setNumber = setNum,
                                    repsDisplay = "$r $defaultUnitSuffix",
                                    loadDisplay = null
                                )
                            } else if (l <= 0f) {
                                ParsedFinishedSet(
                                    setNumber = setNum,
                                    repsDisplay = "$r $defaultUnitSuffix",
                                    loadDisplay = null
                                )
                            } else {
                                ParsedFinishedSet(
                                    setNumber = setNum,
                                    repsDisplay = "$r $defaultUnitSuffix",
                                    loadDisplay = "${formatWeight(l)} kg"
                                )
                            }
                        }
                        unitOnlyMatch != null -> {
                            val r = unitOnlyMatch.groupValues[1]
                            val uRaw = unitOnlyMatch.groupValues[2].lowercase(Locale.ROOT)
                            val uClean = when (uRaw) {
                                "s", "seg" -> "seg"
                                "min" -> "min"
                                else -> "reps"
                            }
                            ParsedFinishedSet(
                                setNumber = setNum,
                                repsDisplay = "$r $uClean",
                                loadDisplay = null
                            )
                        }
                        else -> {
                            ParsedFinishedSet(
                                setNumber = setNum,
                                repsDisplay = body,
                                loadDisplay = null
                            )
                        }
                    }
                }
            }
        }

        val count = fallbackSetsCount.coerceAtLeast(1)
        val avgReps = (fallbackTotalReps.toFloat() / count).roundToInt().coerceAtLeast(1)
        val loadStr = if (fallbackMaxLoadKg > 0f && (unitType == null || unitType == 0)) {
            "${formatWeight(fallbackMaxLoadKg)} kg"
        } else null
        return List(count) { idx ->
            ParsedFinishedSet(
                setNumber = idx + 1,
                repsDisplay = "$avgReps $defaultUnitSuffix",
                loadDisplay = loadStr
            )
        }
    }
}
