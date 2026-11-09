package com.gymwp.free.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ImportedRoutineSummary(
    val planId: Long,
    val planName: String,
    val daysCount: Int,
    val exercisesCount: Int
)

object WorkoutRoutinePortManager {

    private const val FORMAT_TAG = "cheese_wp_routine_v1"

    /**
     * Serializa una rutina completa (WorkoutPlanEntity + todos sus días + ejercicios de cada día +
     * ejercicios personalizados utilizados) a una cadena JSON portable.
     */
    suspend fun exportRoutineToJson(
        dao: GymWpDao,
        planId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val plan = dao.getWorkoutPlan(planId)
                ?: throw IllegalArgumentException("No se encontró la rutina seleccionada.")
            val days = dao.getWorkoutDays(plan.id).sortedBy { it.orderIndex }
            val allCustom = dao.getAllUserCustomExercisesSnapshot().associateBy { it.id }

            val referencedCustomIds = mutableSetOf<Int>()
            val daysArray = JSONArray()

            for ((dIdx, day) in days.withIndex()) {
                val exercises = dao.getWorkoutExercises(day.id).sortedBy { it.orderIndex }
                val exArray = JSONArray()
                for ((eIdx, ex) in exercises.withIndex()) {
                    if (ex.exerciseId >= 10000) {
                        referencedCustomIds.add(ex.exerciseId)
                    }
                    val alts = WorkoutExerciseAlternativesCodec.decodeAlternatives(ex.notes)
                    for (alt in alts) {
                        if (alt.exerciseId >= 10000) {
                            referencedCustomIds.add(alt.exerciseId)
                        }
                    }

                    exArray.put(
                        JSONObject().apply {
                            put("exerciseId", ex.exerciseId)
                            put("isCustomExercise", ex.isCustomExercise)
                            put("customExerciseName", ex.customExerciseName)
                            put("setsCount", ex.setsCount)
                            put("repsPerSetCsv", ex.repsPerSetCsv)
                            put("loadsPerSetCsv", ex.loadsPerSetCsv)
                            put("unitType", ex.unitType)
                            put("restSeconds", ex.restSeconds)
                            put("isBiSet", ex.isBiSet)
                            put("notes", ex.notes)
                            put("orderIndex", eIdx)
                        }
                    )
                }

                daysArray.put(
                    JSONObject().apply {
                        put("name", day.name)
                        put("orderIndex", dIdx)
                        put("exercises", exArray)
                    }
                )
            }

            val customArray = JSONArray()
            for (customId in referencedCustomIds) {
                val customEntity = allCustom[customId] ?: continue
                // Omitir rutas locales de fotos al exportar para mantener el JSON ligero y portable
                val cleanDescription = customEntity.description
                    .lineSequence()
                    .filterNot { it.trim().startsWith("[PHOTO]:") }
                    .joinToString("\n")
                    .trim()
                customArray.put(
                    JSONObject().apply {
                        put("id", customEntity.id)
                        put("name", customEntity.name)
                        put("description", cleanDescription)
                        put("muscles", customEntity.muscles)
                        put("unitType", customEntity.unitType)
                    }
                )
            }

            val root = JSONObject().apply {
                put("format", FORMAT_TAG)
                put("exportedAt", System.currentTimeMillis())
                put(
                    "plan",
                    JSONObject().apply {
                        put("name", plan.name)
                        put("description", plan.description)
                        put("isCustomPeriodized", plan.isCustomPeriodized)
                        put("phaseNumber", plan.phaseNumber)
                        put("weeksDuration", plan.weeksDuration)
                        put("targetSessionsCount", plan.targetSessionsCount)
                    }
                )
                put("customExercises", customArray)
                put("days", daysArray)
            }

            root.toString(2)
        }
    }

    suspend fun exportRoutineToShareableFileUri(
        context: Context,
        dao: GymWpDao,
        planId: Long
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val plan = dao.getWorkoutPlan(planId)
                ?: throw IllegalArgumentException("No se encontró la rutina seleccionada.")
            val jsonText = exportRoutineToJson(dao, planId).getOrThrow()
            val cleanBase = plan.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").trim('_').ifBlank { "Rutina" }
            val fileName = "Rutina_${cleanBase}.json"
            val outFile = File(context.cacheDir, fileName)
            outFile.writeText(jsonText, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            uri to fileName
        }
    }

    suspend fun exportRoutineToUri(
        context: Context,
        dao: GymWpDao,
        planId: Long,
        targetUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        exportRoutineToJson(dao, planId).mapCatching { jsonText ->
            context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(jsonText.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: throw IllegalStateException("No se pudo escribir en el archivo seleccionado.")
            jsonText
        }
    }

    suspend fun importRoutineFromUri(
        context: Context,
        dao: GymWpDao,
        userId: Int,
        sourceUri: Uri
    ): Result<ImportedRoutineSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val rawText = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException("No se pudo leer el archivo seleccionado.")
            importRoutineFromJson(dao, userId, rawText).getOrThrow()
        }
    }

    /**
     * Importa una rutina completa (con todos sus días, programación semanal, ejercicios, series, cargas y parches)
     * para el usuario activo.
     */
    suspend fun importRoutineFromJson(
        dao: GymWpDao,
        userId: Int,
        rawJson: String
    ): Result<ImportedRoutineSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawJson.trim()
            if (trimmed.isBlank()) {
                throw IllegalArgumentException("El contenido de la rutina está vacío.")
            }

            val root = JSONObject(trimmed)
            val planObj = root.optJSONObject("plan") ?: root
            val daysArray = root.optJSONArray("days")
                ?: planObj.optJSONArray("days")
                ?: throw IllegalArgumentException("El formato no contiene días de rutina válidos.")

            val planName = planObj.optString("name", "").trim().ifBlank { "Rutina Importada" }
            val planDesc = planObj.optString("description", "").trim()
            val isPeriodized = planObj.optBoolean("isCustomPeriodized", false)
            val phaseNum = planObj.optInt("phaseNumber", 1).coerceIn(1, 3)
            val weeksDur = planObj.optInt("weeksDuration", 4).coerceAtLeast(1)
            val defaultTarget = (daysArray.length().coerceAtLeast(1)) * weeksDur
            val targetSessions = planObj.optInt("targetSessionsCount", defaultTarget).coerceAtLeast(1)

            // 1. Reconciliar ejercicios personalizados incluidos en la rutina exportada
            val existingCustom = dao.getAllUserCustomExercisesSnapshot()
            val customIdRemap = mutableMapOf<Int, Int>()
            var nextCustomId = ((dao.getMaxCustomExerciseId() ?: 9999).coerceAtLeast(9999)) + 1

            val customArray = root.optJSONArray("customExercises")
            if (customArray != null) {
                for (i in 0 until customArray.length()) {
                    val cObj = customArray.optJSONObject(i) ?: continue
                    val oldId = cObj.optInt("id", 0)
                    val cName = cObj.optString("name", "").trim()
                    if (cName.isBlank()) continue
                    val cDesc = cObj.optString("description", "")
                    val cMuscles = cObj.optString("muscles", "00000000000000000")
                    val cUnit = cObj.optInt("unitType", 0)

                    val matchByName = existingCustom.firstOrNull {
                        it.userId == userId && it.name.equals(cName, ignoreCase = true)
                    } ?: existingCustom.firstOrNull {
                        it.name.equals(cName, ignoreCase = true)
                    }

                    if (matchByName != null) {
                        if (oldId >= 10000) customIdRemap[oldId] = matchByName.id
                    } else {
                        val assignedId = if (oldId >= 10000 && existingCustom.none { it.id == oldId }) {
                            oldId
                        } else {
                            val id = nextCustomId
                            nextCustomId++
                            id
                        }
                        dao.insertUserCustomExercise(
                            UserCustomExerciseEntity(
                                id = assignedId,
                                userId = userId,
                                name = cName,
                                description = cDesc,
                                muscles = cMuscles,
                                unitType = cUnit
                            )
                        )
                        if (oldId >= 10000) customIdRemap[oldId] = assignedId
                    }
                }
            }

            // 2. Desactivar planes anteriores del usuario y crear el nuevo plan activo
            val userPlans = dao.getAllWorkoutPlans().filter { it.userId == userId }
            for (oldPlan in userPlans) {
                if (oldPlan.isActive) {
                    dao.updateWorkoutPlan(oldPlan.copy(isActive = false))
                }
            }

            val newPlanId = dao.insertWorkoutPlan(
                WorkoutPlanEntity(
                    userId = userId,
                    name = planName,
                    description = planDesc,
                    isCustomPeriodized = isPeriodized,
                    phaseNumber = phaseNum,
                    weeksDuration = weeksDur,
                    completedSessionsCount = 0,
                    targetSessionsCount = targetSessions,
                    isActive = true
                )
            )

            var totalExercisesInserted = 0

            // 3. Insertar cada día y sus ejercicios
            for (dIdx in 0 until daysArray.length()) {
                val dObj = daysArray.optJSONObject(dIdx) ?: continue
                val dName = dObj.optString("name", "Día ${dIdx + 1}").trim().ifBlank { "Día ${dIdx + 1}" }
                val newDayId = dao.insertWorkoutDay(
                    WorkoutDayEntity(
                        workoutPlanId = newPlanId,
                        name = dName,
                        colorIndex = 0,
                        orderIndex = dObj.optInt("orderIndex", dIdx)
                    )
                )

                val exArray = dObj.optJSONArray("exercises") ?: JSONArray()
                for (eIdx in 0 until exArray.length()) {
                    val eObj = exArray.optJSONObject(eIdx) ?: continue
                    val rawExId = eObj.optInt("exerciseId", 1)
                    val mappedExId = customIdRemap[rawExId] ?: rawExId
                    val isCustom = eObj.optBoolean("isCustomExercise", mappedExId >= 10000) || mappedExId >= 10000
                    val customName = eObj.optString("customExerciseName", "")
                    val setsCount = eObj.optInt("setsCount", 4).coerceIn(1, 20)
                    val repsCsv = eObj.optString("repsPerSetCsv", List(setsCount) { "10" }.joinToString(","))
                    val loadsCsv = eObj.optString("loadsPerSetCsv", List(setsCount) { "20" }.joinToString(","))
                    val unitType = eObj.optInt("unitType", 0)
                    val restSeconds = eObj.optInt("restSeconds", 60).coerceAtLeast(0)
                    val isBiSet = eObj.optBoolean("isBiSet", false)
                    val rawNotes = eObj.optString("notes", "")

                    // Remapear IDs de ejercicios personalizados en los parches si aplica
                    val decodedAlts = WorkoutExerciseAlternativesCodec.decodeAlternatives(rawNotes)
                    val finalNotes = if (decodedAlts.isNotEmpty() && customIdRemap.isNotEmpty()) {
                        val remappedAlts = decodedAlts.map { alt ->
                            val newAltId = customIdRemap[alt.exerciseId] ?: alt.exerciseId
                            alt.copy(exerciseId = newAltId)
                        }
                        WorkoutExerciseAlternativesCodec.encodeAlternatives(rawNotes, remappedAlts)
                    } else {
                        rawNotes
                    }

                    dao.insertWorkoutExercise(
                        WorkoutExerciseEntity(
                            workoutDayId = newDayId,
                            exerciseId = mappedExId,
                            isCustomExercise = isCustom,
                            customExerciseName = customName,
                            setsCount = setsCount,
                            repsPerSetCsv = repsCsv,
                            loadsPerSetCsv = loadsCsv,
                            unitType = unitType,
                            restSeconds = restSeconds,
                            isBiSet = isBiSet,
                            notes = finalNotes,
                            orderIndex = eObj.optInt("orderIndex", eIdx)
                        )
                    )
                    totalExercisesInserted++
                }
            }

            ImportedRoutineSummary(
                planId = newPlanId,
                planName = planName,
                daysCount = daysArray.length(),
                exercisesCount = totalExercisesInserted
            )
        }
    }
}
