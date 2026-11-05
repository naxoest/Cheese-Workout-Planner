package com.gymwp.free.data

import com.google.gson.Gson
import java.util.Locale

/**
 * Especificación de un ejercicio "parche" (Secundario, Terciario u Opcional)
 * vinculado a un ejercicio principal de la rutina para usar cuando una máquina
 * o implemento esté ocupado, sin sobrescribir el ejercicio titular.
 */
data class AlternativeExerciseSpec(
    val exerciseId: Int,
    val customExerciseName: String = "",
    val unitType: Int = 0,
    val setsCount: Int = 4,
    val repsPerSetCsv: String = "10,10,10,10",
    val loadsPerSetCsv: String = "20,20,20,20"
)

data class WorkoutExerciseNotesPayload(
    val alternatives: List<AlternativeExerciseSpec> = emptyList(),
    val userNote: String = ""
)

object WorkoutExerciseAlternativesCodec {

    private const val PREFIX = "[ALT_JSON]:"
    private val gson = Gson()

    fun decodePayload(rawNotes: String?): WorkoutExerciseNotesPayload {
        if (rawNotes.isNullOrBlank()) return WorkoutExerciseNotesPayload()
        val trimmed = rawNotes.trim()
        if (!trimmed.startsWith(PREFIX)) {
            return WorkoutExerciseNotesPayload(alternatives = emptyList(), userNote = rawNotes)
        }
        val jsonPart = trimmed.removePrefix(PREFIX).trim()
        return try {
            val parsed = gson.fromJson(jsonPart, WorkoutExerciseNotesPayload::class.java)
            parsed?.copy(
                alternatives = parsed.alternatives ?: emptyList(),
                userNote = parsed.userNote ?: ""
            ) ?: WorkoutExerciseNotesPayload()
        } catch (_: Exception) {
            WorkoutExerciseNotesPayload()
        }
    }

    fun decodeAlternatives(rawNotes: String?): List<AlternativeExerciseSpec> {
        return decodePayload(rawNotes).alternatives
    }

    fun encodeAlternatives(
        existingRawNotes: String?,
        alternatives: List<AlternativeExerciseSpec>
    ): String {
        val currentPayload = decodePayload(existingRawNotes)
        val cleanList = alternatives.distinctBy { it.exerciseId }
        if (cleanList.isEmpty() && currentPayload.userNote.isBlank()) {
            return ""
        }
        if (cleanList.isEmpty()) {
            return currentPayload.userNote
        }
        return PREFIX + gson.toJson(
            WorkoutExerciseNotesPayload(
                alternatives = cleanList,
                userNote = currentPayload.userNote
            )
        )
    }

    fun roleBadgeLabel(altIndex: Int): String = when (altIndex) {
        0 -> "2° Secundario"
        1 -> "3° Terciario"
        else -> "${altIndex + 2}° Opcional"
    }

    fun roleShortChipLabel(altIndex: Int): String = when (altIndex) {
        0 -> "2° Parche"
        1 -> "3° Parche"
        else -> "${altIndex + 2}° Parche"
    }

    fun roleFullTitle(altIndex: Int): String = when (altIndex) {
        0 -> "Ejercicio Secundario (Opción 2)"
        1 -> "Ejercicio Terciario (Opción 3)"
        else -> "Ejercicio Opcional (Opción ${altIndex + 2})"
    }

    fun primaryMuscleForFilter(musclesStr: String?): Int? {
        if (musclesStr.isNullOrBlank()) return null
        val primaryIdx = (0..15).firstOrNull { it < musclesStr.length && musclesStr[it] == '3' }
        if (primaryIdx != null) return primaryIdx
        return (0..15).firstOrNull { it < musclesStr.length && musclesStr[it] == '2' }
    }

    private fun formatCleanLoad(value: Float): String {
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

    fun buildDefaultSpec(
        catEx: CatalogExercise,
        parentSetsCount: Int,
        parentUnitType: Int,
        parentRepsCsv: String,
        historicalMaxLoadKg: Float? = null
    ): AlternativeExerciseSpec {
        val sets = parentSetsCount.coerceIn(1, 12)
        val newUnit = catEx.defaultUnitType
        val repsCsv = if (newUnit == parentUnitType && parentRepsCsv.isNotBlank()) {
            val raw = parentRepsCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
            List(sets) { i -> raw.getOrElse(i) { raw.lastOrNull() ?: 10 }.toString() }.joinToString(",")
        } else {
            val defaultVal = when (newUnit) {
                2 -> "45"
                3 -> "15"
                else -> "10"
            }
            List(sets) { defaultVal }.joinToString(",")
        }

        val loadsCsv = if (newUnit == 0) {
            val initialKg = if (historicalMaxLoadKg != null && historicalMaxLoadKg > 0f) {
                formatCleanLoad(historicalMaxLoadKg)
            } else {
                "15"
            }
            List(sets) { initialKg }.joinToString(",")
        } else {
            List(sets) { "0" }.joinToString(",")
        }

        return AlternativeExerciseSpec(
            exerciseId = catEx.id,
            customExerciseName = if (catEx.id >= 10000) catEx.name else "",
            unitType = newUnit,
            setsCount = sets,
            repsPerSetCsv = repsCsv,
            loadsPerSetCsv = loadsCsv
        )
    }
}

/**
 * Codificador y decodificador de días de la semana asignados a un día de rutina (1=Lun .. 7=Dom).
 * Almacena la programación de forma segura sin requerir migraciones destructivas de base de datos.
 */
object WorkoutDayScheduleCodec {
    private const val PREFIX = "[DIAS:"
    private const val SUFFIX = "]"

    val DAY_LABELS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    val DAY_FULL_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    fun getCleanName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""
        val idx = rawName.indexOf(PREFIX)
        return if (idx >= 0) rawName.substring(0, idx).trim() else rawName.trim()
    }

    fun getAssignedDays(rawName: String?): List<Int> {
        if (rawName.isNullOrBlank()) return emptyList()
        val start = rawName.indexOf(PREFIX)
        if (start < 0) return emptyList()
        val end = rawName.indexOf(SUFFIX, start)
        if (end <= start) return emptyList()
        val content = rawName.substring(start + PREFIX.length, end)
        return content.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .distinct()
            .sorted()
    }

    fun encodeName(cleanName: String, assignedDays: List<Int>): String {
        val base = getCleanName(cleanName)
        val validDays = assignedDays.distinct().filter { it in 1..7 }.sorted()
        if (validDays.isEmpty()) return base
        val csv = validDays.joinToString(",")
        return "$base $PREFIX$csv$SUFFIX"
    }

    fun formatDaysSummary(assignedDays: List<Int>): String {
        if (assignedDays.isEmpty()) return "Sin día asignado"
        return assignedDays.mapNotNull { DAY_LABELS.getOrNull(it - 1) }.joinToString(", ")
    }

    fun isToday(assignedDays: List<Int>): Boolean {
        if (assignedDays.isEmpty()) return false
        val cal = java.util.Calendar.getInstance()
        val currentDay = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            java.util.Calendar.SUNDAY -> 7
            else -> 1
        }
        return currentDay in assignedDays
    }
}

/**
 * Normaliza cadenas eliminando tildes y diacríticos (ej. "Press Francés" -> "press frances").
 * Permite que las búsquedas y coincidencias de ejercicios sean completamente insensibles a tildes.
 */
fun String.normalizeForSearch(): String {
    val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
    return "\\p{InCombiningDiacriticalMarks}+".toRegex().replace(normalized, "").lowercase(Locale.ROOT).trim()
}
