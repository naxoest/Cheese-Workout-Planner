package com.gymwp.free.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GymWpDao {

    // Multi-User Profiles
    @Query("SELECT * FROM user_profile ORDER BY id ASC")
    fun observeAllUsers(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profile ORDER BY id ASC")
    suspend fun getAllUsersSnapshot(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profile WHERE id = :userId")
    fun observeUserProfile(userId: Int): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = :userId")
    suspend fun getUserProfile(userId: Int): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserProfile(profile: UserProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfiles(profiles: List<UserProfileEntity>)

    @Query("DELETE FROM user_profile WHERE id = :userId")
    suspend fun deleteUserProfile(userId: Int)

    @Query("DELETE FROM user_profile")
    suspend fun clearUserProfiles()

    // Workout Plans & Days & Exercises
    @Query("SELECT * FROM workout_plan WHERE userId = :userId ORDER BY isCustomPeriodized DESC, phaseNumber ASC, id DESC")
    fun observeWorkoutPlans(userId: Int): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plan")
    suspend fun getAllWorkoutPlans(): List<WorkoutPlanEntity>

    @Query("SELECT * FROM workout_plan WHERE id = :planId")
    suspend fun getWorkoutPlan(planId: Long): WorkoutPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutPlan(plan: WorkoutPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutPlans(plans: List<WorkoutPlanEntity>)

    @Update
    suspend fun updateWorkoutPlan(plan: WorkoutPlanEntity)

    @Query("DELETE FROM workout_plan WHERE id = :planId")
    suspend fun deleteWorkoutPlan(planId: Long)

    @Query("DELETE FROM workout_plan WHERE userId = :userId")
    suspend fun deleteWorkoutPlansForUser(userId: Int)

    @Query("DELETE FROM workout_plan")
    suspend fun clearWorkoutPlans()

    @Query("SELECT * FROM workout_day WHERE workoutPlanId = :planId ORDER BY orderIndex ASC, id ASC")
    fun observeWorkoutDays(planId: Long): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_day ORDER BY workoutPlanId ASC, orderIndex ASC, id ASC")
    fun observeAllWorkoutDays(): Flow<List<WorkoutDayEntity>>

    @Query("SELECT * FROM workout_day")
    suspend fun getAllWorkoutDaysSnapshot(): List<WorkoutDayEntity>

    @Query("SELECT * FROM workout_day WHERE workoutPlanId = :planId ORDER BY orderIndex ASC, id ASC")
    suspend fun getWorkoutDays(planId: Long): List<WorkoutDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutDay(day: WorkoutDayEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutDays(days: List<WorkoutDayEntity>)

    @Update
    suspend fun updateWorkoutDay(day: WorkoutDayEntity)

    @Query("DELETE FROM workout_day WHERE id = :dayId")
    suspend fun deleteWorkoutDay(dayId: Long)

    @Query("DELETE FROM workout_day")
    suspend fun clearWorkoutDays()

    @Query("SELECT * FROM workout_exercise WHERE workoutDayId = :dayId ORDER BY orderIndex ASC, id ASC")
    fun observeWorkoutExercises(dayId: Long): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercise ORDER BY workoutDayId ASC, orderIndex ASC, id ASC")
    fun observeAllWorkoutExercises(): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercise")
    suspend fun getAllWorkoutExercisesSnapshot(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercise WHERE workoutDayId = :dayId ORDER BY orderIndex ASC, id ASC")
    suspend fun getWorkoutExercises(dayId: Long): List<WorkoutExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(exercise: WorkoutExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>)

    @Update
    suspend fun updateWorkoutExercise(exercise: WorkoutExerciseEntity)

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    @Query("DELETE FROM workout_exercise")
    suspend fun clearWorkoutExercises()

    // User Custom Exercises & Favorites
    @Query("SELECT * FROM user_exercise WHERE userId = :userId ORDER BY name ASC")
    fun observeUserCustomExercises(userId: Int): Flow<List<UserCustomExerciseEntity>>

    @Query("SELECT * FROM user_exercise")
    suspend fun getAllUserCustomExercisesSnapshot(): List<UserCustomExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserCustomExercise(ex: UserCustomExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserCustomExercises(items: List<UserCustomExerciseEntity>)

    @Query("DELETE FROM user_exercise")
    suspend fun clearUserCustomExercises()

    @Query("SELECT * FROM favorite_exercise WHERE userId = :userId")
    fun observeFavorites(userId: Int): Flow<List<FavoriteExerciseEntity>>

    @Query("SELECT * FROM favorite_exercise")
    suspend fun getAllFavoritesSnapshot(): List<FavoriteExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(fav: FavoriteExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(items: List<FavoriteExerciseEntity>)

    @Query("DELETE FROM favorite_exercise WHERE userId = :userId AND exerciseId = :exerciseId")
    suspend fun removeFavorite(userId: Int, exerciseId: Int)

    @Query("DELETE FROM favorite_exercise")
    suspend fun clearFavorites()

    // Muscle Fatigue (17 muscles: 0..16 per user)
    @Query("SELECT * FROM muscle_fatigue WHERE userId = :userId ORDER BY muscleId ASC")
    fun observeMuscleFatigue(userId: Int): Flow<List<MuscleFatigueEntity>>

    @Query("SELECT * FROM muscle_fatigue WHERE userId = :userId ORDER BY muscleId ASC")
    suspend fun getUserMuscleFatigue(userId: Int): List<MuscleFatigueEntity>

    @Query("SELECT * FROM muscle_fatigue ORDER BY userId ASC, muscleId ASC")
    suspend fun getAllMuscleFatigue(): List<MuscleFatigueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMuscleFatigueList(items: List<MuscleFatigueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMuscleFatigue(item: MuscleFatigueEntity)

    @Query("DELETE FROM muscle_fatigue")
    suspend fun clearMuscleFatigue()

    // Finished Workouts & Exercise Statistics
    @Query("SELECT * FROM finished_workout_day WHERE userId = :userId ORDER BY timestampMillis DESC")
    fun observeFinishedWorkouts(userId: Int): Flow<List<FinishedWorkoutDayEntity>>

    @Query("SELECT * FROM finished_workout_day")
    suspend fun getAllFinishedWorkoutsSnapshot(): List<FinishedWorkoutDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinishedWorkoutDay(day: FinishedWorkoutDayEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinishedWorkoutDays(days: List<FinishedWorkoutDayEntity>)

    @Query("DELETE FROM finished_workout_day")
    suspend fun clearFinishedWorkoutDays()

    @Query("SELECT * FROM finished_exercise WHERE userId = :userId ORDER BY timestampMillis ASC")
    fun observeAllFinishedExercises(userId: Int): Flow<List<FinishedExerciseEntity>>

    @Query("SELECT * FROM finished_exercise")
    suspend fun getAllFinishedExercisesSnapshot(): List<FinishedExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinishedExercises(items: List<FinishedExerciseEntity>)

    @Query("DELETE FROM finished_exercise")
    suspend fun clearFinishedExercises()

    // My Body Measurements
    @Query("SELECT * FROM my_body_info WHERE userId = :userId ORDER BY timestampMillis DESC")
    fun observeBodyMeasurements(userId: Int): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM my_body_info")
    suspend fun getAllBodyMeasurementsSnapshot(): List<BodyMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMeasurement(measurement: BodyMeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMeasurements(items: List<BodyMeasurementEntity>)

    @Query("DELETE FROM my_body_info WHERE id = :id")
    suspend fun deleteBodyMeasurement(id: Long)

    @Query("DELETE FROM my_body_info")
    suspend fun clearBodyMeasurements()
}
