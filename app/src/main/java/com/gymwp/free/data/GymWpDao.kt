package com.gymwp.free.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GymWpDao {

    // User Profile
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfileEntity)

    // Workout Plans & Days & Exercises
    @Query("SELECT * FROM workout_plan ORDER BY isCustomPeriodized DESC, phaseNumber ASC, id DESC")
    fun observeWorkoutPlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plan WHERE id = :planId")
    suspend fun getWorkoutPlan(planId: Long): WorkoutPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutPlan(plan: WorkoutPlanEntity): Long

    @Update
    suspend fun updateWorkoutPlan(plan: WorkoutPlanEntity)

    @Query("DELETE FROM workout_plan WHERE id = :planId")
    suspend fun deleteWorkoutPlan(planId: Long)

    @Query("SELECT * FROM workout_day WHERE workoutPlanId = :planId ORDER BY orderIndex ASC, id ASC")
    fun observeWorkoutDays(planId: Long): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_day ORDER BY workoutPlanId ASC, orderIndex ASC, id ASC")
    fun observeAllWorkoutDays(): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_day WHERE workoutPlanId = :planId ORDER BY orderIndex ASC, id ASC")
    suspend fun getWorkoutDays(planId: Long): List<WorkoutDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutDay(day: WorkoutDayEntity): Long

    @Update
    suspend fun updateWorkoutDay(day: WorkoutDayEntity)

    @Query("DELETE FROM workout_day WHERE id = :dayId")
    suspend fun deleteWorkoutDay(dayId: Long)

    @Query("SELECT * FROM workout_exercise WHERE workoutDayId = :dayId ORDER BY orderIndex ASC, id ASC")
    fun observeWorkoutExercises(dayId: Long): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercise ORDER BY workoutDayId ASC, orderIndex ASC, id ASC")
    fun observeAllWorkoutExercises(): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercise WHERE workoutDayId = :dayId ORDER BY orderIndex ASC, id ASC")
    suspend fun getWorkoutExercises(dayId: Long): List<WorkoutExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(exercise: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateWorkoutExercise(exercise: WorkoutExerciseEntity)

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    // User Custom Exercises & Favorites
    @Query("SELECT * FROM user_exercise ORDER BY name ASC")
    fun observeUserCustomExercises(): Flow<List<UserCustomExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserCustomExercise(ex: UserCustomExerciseEntity): Long

    @Query("SELECT * FROM favorite_exercise")
    fun observeFavorites(): Flow<List<FavoriteExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteExerciseEntity)

    @Query("DELETE FROM favorite_exercise WHERE exerciseId = :exerciseId")
    suspend fun removeFavorite(exerciseId: Int)

    // Muscle Fatigue (17 muscles: 0..16)
    @Query("SELECT * FROM muscle_fatigue ORDER BY muscleId ASC")
    fun observeMuscleFatigue(): Flow<List<MuscleFatigueEntity>>

    @Query("SELECT * FROM muscle_fatigue ORDER BY muscleId ASC")
    suspend fun getAllMuscleFatigue(): List<MuscleFatigueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMuscleFatigueList(items: List<MuscleFatigueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMuscleFatigue(item: MuscleFatigueEntity)

    // Finished Workouts & Exercise Statistics
    @Query("SELECT * FROM finished_workout_day ORDER BY timestampMillis DESC")
    fun observeFinishedWorkouts(): Flow<List<FinishedWorkoutDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinishedWorkoutDay(day: FinishedWorkoutDayEntity): Long

    @Query("SELECT * FROM finished_exercise ORDER BY timestampMillis ASC")
    fun observeAllFinishedExercises(): Flow<List<FinishedExerciseEntity>>

    @Query("SELECT * FROM finished_exercise WHERE exerciseId = :exerciseId ORDER BY timestampMillis ASC")
    fun observeFinishedExercisesForId(exerciseId: Int): Flow<List<FinishedExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinishedExercises(items: List<FinishedExerciseEntity>)

    // My Body Measurements
    @Query("SELECT * FROM my_body_info ORDER BY timestampMillis DESC")
    fun observeBodyMeasurements(): Flow<List<BodyMeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMeasurement(measurement: BodyMeasurementEntity): Long

    @Query("DELETE FROM my_body_info WHERE id = :id")
    suspend fun deleteBodyMeasurement(id: Long)
}
