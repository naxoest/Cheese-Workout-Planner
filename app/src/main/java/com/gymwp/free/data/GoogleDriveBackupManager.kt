package com.gymwp.free.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GymWpCloudBackupPayload(
    val schemaVersion: Int = 2,
    val appName: String = "Chukaros's GymPlanner",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val activeUserId: Int = 1,
    val userProfiles: List<UserProfileEntity> = emptyList(),
    val userProfile: UserProfileEntity? = null, // Compatibilidad con respaldos v1
    val workoutPlans: List<WorkoutPlanEntity> = emptyList(),
    val workoutDays: List<WorkoutDayEntity> = emptyList(),
    val workoutExercises: List<WorkoutExerciseEntity> = emptyList(),
    val userCustomExercises: List<UserCustomExerciseEntity> = emptyList(),
    val favorites: List<FavoriteExerciseEntity> = emptyList(),
    val muscleFatigue: List<MuscleFatigueEntity> = emptyList(),
    val finishedWorkouts: List<FinishedWorkoutDayEntity> = emptyList(),
    val finishedExercises: List<FinishedExerciseEntity> = emptyList(),
    val bodyMeasurements: List<BodyMeasurementEntity> = emptyList()
)

data class DriveSyncState(
    val linkedUri: String? = null,
    val linkedFileName: String = "Sin vincular",
    val autoBackupEnabled: Boolean = true,
    val lastSyncMillis: Long = 0L,
    val statusMessage: String = "Guardado local multi-usuario activo. Vincula tu archivo en Google Drive para Auto-Sync."
)

object GoogleDriveBackupManager {

    private const val PREFS_NAME = "chukaros_gdrive_sync_prefs"
    private const val KEY_ACTIVE_USER_ID = "active_user_id"
    private const val KEY_LINKED_URI = "linked_drive_uri"
    private const val KEY_LINKED_NAME = "linked_drive_name"
    private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
    private const val KEY_LAST_SYNC_MILLIS = "last_sync_millis"
    private const val KEY_LAST_STATUS = "last_status_msg"

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun getSavedActiveUserId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ACTIVE_USER_ID, 1)
    }

    fun saveActiveUserId(context: Context, userId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ACTIVE_USER_ID, userId).apply()
    }

    fun loadSyncState(context: Context): DriveSyncState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_LINKED_URI, null)
        return DriveSyncState(
            linkedUri = uriStr,
            linkedFileName = prefs.getString(KEY_LINKED_NAME, null) ?: "Sin vincular",
            autoBackupEnabled = prefs.getBoolean(KEY_AUTO_BACKUP, true),
            lastSyncMillis = prefs.getLong(KEY_LAST_SYNC_MILLIS, 0L),
            statusMessage = prefs.getString(
                KEY_LAST_STATUS,
                if (uriStr != null) "Vinculado con Google Drive (Multi-Usuario)" else "Guardado local multi-usuario activo. Vincula tu archivo en Google Drive para Auto-Sync."
            ) ?: ""
        )
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean): DriveSyncState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
        return loadSyncState(context)
    }

    fun unlinkGoogleDrive(context: Context): DriveSyncState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_LINKED_URI)
            .remove(KEY_LINKED_NAME)
            .putString(KEY_LAST_STATUS, "Desvinculado de Google Drive (solo guardado local)")
            .apply()
        return loadSyncState(context)
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            } ?: uri.lastPathSegment ?: "Chukaros_GymPlanner_Backup.json"
        } catch (_: Exception) {
            "Chukaros_GymPlanner_Backup.json"
        }
    }

    fun persistDriveUriPermission(context: Context, uri: Uri): String {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
        }
        val displayName = resolveDisplayName(context, uri)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LINKED_URI, uri.toString())
            .putString(KEY_LINKED_NAME, displayName)
            .putBoolean(KEY_AUTO_BACKUP, true)
            .apply()
        return displayName
    }

    suspend fun exportBackupToUri(context: Context, dao: GymWpDao, targetUri: Uri): Result<DriveSyncState> =
        withContext(Dispatchers.IO) {
            try {
                val activeId = getSavedActiveUserId(context)
                val allUsers = dao.getAllUsersSnapshot()
                val payload = GymWpCloudBackupPayload(
                    schemaVersion = 2,
                    exportedAtMillis = System.currentTimeMillis(),
                    activeUserId = activeId,
                    userProfiles = allUsers,
                    userProfile = allUsers.firstOrNull { it.id == activeId } ?: allUsers.firstOrNull(),
                    workoutPlans = dao.getAllWorkoutPlans(),
                    workoutDays = dao.getAllWorkoutDaysSnapshot(),
                    workoutExercises = dao.getAllWorkoutExercisesSnapshot(),
                    userCustomExercises = dao.getAllUserCustomExercisesSnapshot(),
                    favorites = dao.getAllFavoritesSnapshot(),
                    muscleFatigue = dao.getAllMuscleFatigue(),
                    finishedWorkouts = dao.getAllFinishedWorkoutsSnapshot(),
                    finishedExercises = dao.getAllFinishedExercisesSnapshot(),
                    bodyMeasurements = dao.getAllBodyMeasurementsSnapshot()
                )
                val jsonText = gson.toJson(payload)
                context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                    out.write(jsonText.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("No se pudo abrir el flujo de escritura en Google Drive")

                val now = System.currentTimeMillis()
                val fileName = resolveDisplayName(context, targetUri)
                val msg = "✅ Sincronizado en Google Drive ($fileName • ${allUsers.size} usuarios, ${payload.workoutPlans.size} planes, ${payload.finishedWorkouts.size} sesiones)"
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_LINKED_URI, targetUri.toString())
                    .putString(KEY_LINKED_NAME, fileName)
                    .putLong(KEY_LAST_SYNC_MILLIS, now)
                    .putString(KEY_LAST_STATUS, msg)
                    .apply()
                Result.success(loadSyncState(context))
            } catch (e: Exception) {
                val errMsg = "⚠️ Error al sincronizar con Google Drive: ${e.localizedMessage ?: "Permiso expirado"}"
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_STATUS, errMsg)
                    .apply()
                Result.failure(e)
            }
        }

    suspend fun autoSyncIfEnabled(context: Context, dao: GymWpDao): DriveSyncState? {
        val state = loadSyncState(context)
        if (!state.autoBackupEnabled || state.linkedUri.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(state.linkedUri)
            exportBackupToUri(context, dao, uri).getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun restoreBackupFromUri(
        context: Context,
        dao: GymWpDao,
        sourceUri: Uri,
        linkAsDefaultDriveFile: Boolean = true
    ): Result<DriveSyncState> = withContext(Dispatchers.IO) {
        try {
            if (linkAsDefaultDriveFile) {
                persistDriveUriPermission(context, sourceUri)
            }
            val jsonText = context.contentResolver.openInputStream(sourceUri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("No se pudo leer el archivo seleccionado en Google Drive")

            val payload = gson.fromJson(jsonText, GymWpCloudBackupPayload::class.java)
                ?: error("Formato de respaldo inválido")

            val profilesToRestore = when {
                payload.userProfiles.isNotEmpty() -> payload.userProfiles
                payload.userProfile != null -> listOf(payload.userProfile)
                else -> listOf(UserProfileEntity(id = 1, name = "Ignacio"))
            }

            dao.clearUserProfiles()
            dao.insertUserProfiles(profilesToRestore)

            val restoredActiveId = if (profilesToRestore.any { it.id == payload.activeUserId }) {
                payload.activeUserId
            } else {
                profilesToRestore.first().id
            }
            saveActiveUserId(context, restoredActiveId)

            dao.clearWorkoutExercises()
            dao.clearWorkoutDays()
            dao.clearWorkoutPlans()
            dao.insertWorkoutPlans(payload.workoutPlans)
            dao.insertWorkoutDays(payload.workoutDays)
            dao.insertWorkoutExercises(payload.workoutExercises)

            dao.clearUserCustomExercises()
            dao.insertUserCustomExercises(payload.userCustomExercises)

            dao.clearFavorites()
            dao.insertFavorites(payload.favorites)

            if (payload.muscleFatigue.isNotEmpty()) {
                dao.clearMuscleFatigue()
                dao.upsertMuscleFatigueList(payload.muscleFatigue)
            }

            dao.clearFinishedExercises()
            dao.clearFinishedWorkoutDays()
            dao.insertFinishedWorkoutDays(payload.finishedWorkouts)
            dao.insertFinishedExercises(payload.finishedExercises)

            dao.clearBodyMeasurements()
            dao.insertBodyMeasurements(payload.bodyMeasurements)

            val now = System.currentTimeMillis()
            val fileName = resolveDisplayName(context, sourceUri)
            val msg = "✅ Restaurado desde Google Drive ($fileName • ${profilesToRestore.size} usuarios, ${payload.workoutPlans.size} planes)"
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putLong(KEY_LAST_SYNC_MILLIS, now)
                .putString(KEY_LAST_STATUS, msg)
                .apply()
            Result.success(loadSyncState(context))
        } catch (e: Exception) {
            val errMsg = "⚠️ Error al restaurar desde Google Drive: ${e.localizedMessage ?: "Archivo inválido"}"
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_STATUS, errMsg)
                .apply()
            Result.failure(e)
        }
    }
}
