package com.gymwp.free.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// JSON Catalog Models (353 Internal Exercises & 57 Pre-built Plans)
data class CatalogExercise(
    val id: Int,
    val name: String,
    val nameEn: String = "",
    val namePt: String = "",
    val description: String,
    val muscles: String, // 17 chars '0'..'3'
    val defaultUnitType: Int = 0, // 0: Reps+Carga, 1: Reps, 2: Seg, 3: Min
    val equipment: List<String> = emptyList(),
    val gifAssetPath: String
)

data class PrebuiltPlan(
    val id: Int,
    val name: String,
    val description: String,
    val tags: List<Int> = emptyList(),
    val days: List<PrebuiltDay> = emptyList()
)

data class PrebuiltDay(
    val dayNumber: Int,
    val name: String,
    val exercises: List<PrebuiltExercise> = emptyList()
)

data class PrebuiltExercise(
    val exerciseId: Int,
    val sets: Int,
    val reps: List<Int>,
    val restSeconds: Int = 60,
    val isBiSet: Boolean = false
)

// Room Database Entities

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Atleta",
    val gender: Int = 0, // 0 = Masculino, 1 = Femenino
    val birthYear: Int = 1996,
    val heightCm: Float = 175f,
    val weightKg: Float = 75f,
    val goal: Int = 0, // 0 = Ganar Masa Muscular, 1 = Perder Peso, 2 = Ganar Fuerza, 3 = Salud / Resistencia
    val experienceLevel: Int = 1, // 0 = Principiante, 1 = Intermedio, 2 = Avanzado
    val trainingPlace: Int = 0, // 0 = Gimnasio Completo, 1 = Casa (Mancuernas/Barra), 2 = Peso Corporal / Calistenia
    val daysPerWeek: Int = 4,
    val onboardingDone: Boolean = false
)

@Entity(tableName = "workout_plan")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val isCustomPeriodized: Boolean = false,
    val phaseNumber: Int = 1, // 1: Adaptación/Resistencia, 2: Hipertrofia, 3: Fuerza/Potencia
    val weeksDuration: Int = 4,
    val completedSessionsCount: Int = 0,
    val targetSessionsCount: Int = 16,
    val isActive: Boolean = false
)

@Entity(tableName = "workout_day")
data class WorkoutDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutPlanId: Long,
    val name: String,
    val colorIndex: Int = 0, // 0..9 palette from GymWP
    val orderIndex: Int = 0
)

@Entity(tableName = "workout_exercise")
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutDayId: Long,
    val exerciseId: Int, // 0..352 or >= 10000 for user custom exercises
    val isCustomExercise: Boolean = false,
    val customExerciseName: String = "",
    val setsCount: Int = 4,
    val repsPerSetCsv: String = "10,10,10,10",
    val loadsPerSetCsv: String = "20,20,20,20",
    val unitType: Int = 0, // 0: Reps+Carga, 1: Reps, 2: Segundos, 3: Minutos
    val restSeconds: Int = 60,
    val isBiSet: Boolean = false,
    val notes: String = "",
    val orderIndex: Int = 0
)

@Entity(tableName = "user_exercise")
data class UserCustomExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 10000,
    val name: String,
    val description: String = "",
    val muscles: String = "00000000000000000", // 17 chars '0'..'3'
    val unitType: Int = 0
)

@Entity(tableName = "favorite_exercise")
data class FavoriteExerciseEntity(
    @PrimaryKey val exerciseId: Int
)

@Entity(tableName = "muscle_fatigue")
data class MuscleFatigueEntity(
    @PrimaryKey val muscleId: Int, // 0..16
    val fatiguePercent: Int = 0, // 0..100
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "finished_workout_day")
data class FinishedWorkoutDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planName: String,
    val dayName: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val durationSeconds: Int,
    val caloriesBurned: Int,
    val totalVolumeKg: Float,
    val totalSets: Int,
    val totalReps: Int,
    val exercisesCount: Int
)

@Entity(tableName = "finished_exercise")
data class FinishedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val finishedDayId: Long,
    val exerciseId: Int,
    val exerciseName: String,
    val muscles: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val maxLoadKg: Float,
    val avgLoadKg: Float,
    val totalVolumeKg: Float,
    val totalReps: Int,
    val setsCount: Int,
    val setsDetailText: String
)

@Entity(tableName = "my_body_info")
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    val weightKg: Float = 75f,
    val heightCm: Float = 175f,
    val bodyFatPercent: Float = 15f,
    val muscleMassPercent: Float = 42f,
    val boneMassKg: Float = 3.2f,
    val waterPercent: Float = 60f,
    val visceralFat: Int = 5,
    // 12 Circumferences (cm)
    val neckCm: Float = 0f,
    val shoulderCm: Float = 0f,
    val chestCm: Float = 0f,
    val waistCm: Float = 0f,
    val abdomenCm: Float = 0f,
    val hipCm: Float = 0f,
    val bicepsLeftCm: Float = 0f,
    val bicepsRightCm: Float = 0f,
    val forearmLeftCm: Float = 0f,
    val forearmRightCm: Float = 0f,
    val thighLeftCm: Float = 0f,
    val thighRightCm: Float = 0f,
    val calfLeftCm: Float = 0f,
    val calfRightCm: Float = 0f,
    // 9 Skinfolds (mm)
    val skinfoldChestMm: Float = 0f,
    val skinfoldMidaxillaryMm: Float = 0f,
    val skinfoldTricepsMm: Float = 0f,
    val skinfoldSubscapularMm: Float = 0f,
    val skinfoldAbdominalMm: Float = 0f,
    val skinfoldSuprailiacMm: Float = 0f,
    val skinfoldThighMm: Float = 0f,
    val skinfoldBicepsMm: Float = 0f,
    val skinfoldCalfMm: Float = 0f
)
