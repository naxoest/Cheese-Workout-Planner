package com.gymwp.free.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Database(
    entities = [
        UserProfileEntity::class,
        WorkoutPlanEntity::class,
        WorkoutDayEntity::class,
        WorkoutExerciseEntity::class,
        UserCustomExerciseEntity::class,
        FavoriteExerciseEntity::class,
        MuscleFatigueEntity::class,
        FinishedWorkoutDayEntity::class,
        FinishedExerciseEntity::class,
        BodyMeasurementEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GymWpDatabase : RoomDatabase() {
    abstract fun dao(): GymWpDao

    companion object {
        @Volatile
        private var INSTANCE: GymWpDatabase? = null

        fun getInstance(context: Context): GymWpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GymWpDatabase::class.java,
                    "chukaros_gymplanner.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object AssetCatalogRepository {
    private var cachedExercises: List<CatalogExercise>? = null
    private var cachedPrebuiltPlans: List<PrebuiltPlan>? = null

    fun getExercises(context: Context): List<CatalogExercise> {
        cachedExercises?.let { return it }
        return try {
            val json = context.assets.open("exercises_es.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<CatalogExercise>>() {}.type
            val list: List<CatalogExercise> = Gson().fromJson(json, type)
            cachedExercises = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getPrebuiltPlans(context: Context): List<PrebuiltPlan> {
        cachedPrebuiltPlans?.let { return it }
        return try {
            val json = context.assets.open("prebuilt_plans_es.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<PrebuiltPlan>>() {}.type
            val list: List<PrebuiltPlan> = Gson().fromJson(json, type)
            cachedPrebuiltPlans = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
