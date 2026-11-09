package com.gymwp.free.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PersistedOptionProgress(
    val exerciseId: Int,
    val unitType: Int,
    val done: List<Boolean>,
    val reps: List<String>,
    val loads: List<String>
)

data class PersistedExerciseProgress(
    val workoutExerciseId: Long,
    val activeExerciseId: Int,
    val options: Map<Int, PersistedOptionProgress>
)

data class PersistedWorkoutSession(
    val userId: Int,
    val planId: Long?,
    val planName: String,
    val dayId: Long,
    val fallbackDayName: String,
    val startedWallClockMs: Long,
    val accumulatedPausedMs: Long = 0L,
    val pauseStartedWallClockMs: Long = 0L,
    val isPaused: Boolean = false,
    val restEndWallClockMs: Long = 0L,
    val restTotalSeconds: Int = 60,
    val focusedMode: Boolean = true,
    val focusedExerciseIndex: Int = 0,
    val exerciseStates: Map<Long, PersistedExerciseProgress> = emptyMap()
)

object ActiveWorkoutSessionStore {

    private const val PREFS_NAME = "cheese_wp_active_workout_session"
    private const val KEY_PREFIX = "active_session_user_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(context: Context, session: PersistedWorkoutSession) {
        runCatching {
            val root = JSONObject().apply {
                put("userId", session.userId)
                if (session.planId != null) put("planId", session.planId)
                put("planName", session.planName)
                put("dayId", session.dayId)
                put("fallbackDayName", session.fallbackDayName)
                put("startedWallClockMs", session.startedWallClockMs)
                put("accumulatedPausedMs", session.accumulatedPausedMs)
                put("pauseStartedWallClockMs", session.pauseStartedWallClockMs)
                put("isPaused", session.isPaused)
                put("restEndWallClockMs", session.restEndWallClockMs)
                put("restTotalSeconds", session.restTotalSeconds)
                put("focusedMode", session.focusedMode)
                put("focusedExerciseIndex", session.focusedExerciseIndex)

                val exStatesArr = JSONArray()
                session.exerciseStates.values.forEach { exProg ->
                    val optsArr = JSONArray()
                    exProg.options.values.forEach { opt ->
                        optsArr.put(
                            JSONObject().apply {
                                put("exerciseId", opt.exerciseId)
                                put("unitType", opt.unitType)
                                put("done", JSONArray(opt.done))
                                put("reps", JSONArray(opt.reps))
                                put("loads", JSONArray(opt.loads))
                            }
                        )
                    }
                    exStatesArr.put(
                        JSONObject().apply {
                            put("workoutExerciseId", exProg.workoutExerciseId)
                            put("activeExerciseId", exProg.activeExerciseId)
                            put("options", optsArr)
                        }
                    )
                }
                put("exerciseStates", exStatesArr)
            }
            prefs(context).edit()
                .putString("$KEY_PREFIX${session.userId}", root.toString())
                .apply()
        }
    }

    fun loadSession(context: Context, userId: Int): PersistedWorkoutSession? {
        return runCatching {
            val raw = prefs(context).getString("$KEY_PREFIX$userId", null)
                ?.takeIf { it.isNotBlank() } ?: return null
            val root = JSONObject(raw)
            val dayId = root.optLong("dayId", -1L)
            if (dayId <= 0L) return null

            val planId = if (root.has("planId") && !root.isNull("planId")) root.optLong("planId") else null
            val exStatesMap = mutableMapOf<Long, PersistedExerciseProgress>()
            val exStatesArr = root.optJSONArray("exerciseStates")
            if (exStatesArr != null) {
                for (i in 0 until exStatesArr.length()) {
                    val exObj = exStatesArr.optJSONObject(i) ?: continue
                    val wExId = exObj.optLong("workoutExerciseId", -1L)
                    if (wExId <= 0L) continue
                    val activeExId = exObj.optInt("activeExerciseId", 0)
                    val optsMap = mutableMapOf<Int, PersistedOptionProgress>()
                    val optsArr = exObj.optJSONArray("options")
                    if (optsArr != null) {
                        for (j in 0 until optsArr.length()) {
                            val optObj = optsArr.optJSONObject(j) ?: continue
                            val exId = optObj.optInt("exerciseId", 0)
                            val uType = optObj.optInt("unitType", 0)
                            val doneArr = optObj.optJSONArray("done") ?: JSONArray()
                            val repsArr = optObj.optJSONArray("reps") ?: JSONArray()
                            val loadsArr = optObj.optJSONArray("loads") ?: JSONArray()
                            val doneList = List(doneArr.length()) { k -> doneArr.optBoolean(k, false) }
                            val repsList = List(repsArr.length()) { k -> repsArr.optString(k, "10") }
                            val loadsList = List(loadsArr.length()) { k -> loadsArr.optString(k, "20") }
                            optsMap[exId] = PersistedOptionProgress(
                                exerciseId = exId,
                                unitType = uType,
                                done = doneList,
                                reps = repsList,
                                loads = loadsList
                            )
                        }
                    }
                    exStatesMap[wExId] = PersistedExerciseProgress(
                        workoutExerciseId = wExId,
                        activeExerciseId = activeExId,
                        options = optsMap
                    )
                }
            }

            PersistedWorkoutSession(
                userId = root.optInt("userId", userId),
                planId = planId,
                planName = root.optString("planName", "Entrenamiento Libre"),
                dayId = dayId,
                fallbackDayName = root.optString("fallbackDayName", "Entrenamiento"),
                startedWallClockMs = root.optLong("startedWallClockMs", System.currentTimeMillis()),
                accumulatedPausedMs = root.optLong("accumulatedPausedMs", 0L),
                pauseStartedWallClockMs = root.optLong("pauseStartedWallClockMs", 0L),
                isPaused = root.optBoolean("isPaused", false),
                restEndWallClockMs = root.optLong("restEndWallClockMs", 0L),
                restTotalSeconds = root.optInt("restTotalSeconds", 60),
                focusedMode = root.optBoolean("focusedMode", true),
                focusedExerciseIndex = root.optInt("focusedExerciseIndex", 0),
                exerciseStates = exStatesMap
            )
        }.getOrNull()
    }

    fun clearSession(context: Context, userId: Int) {
        prefs(context).edit().remove("$KEY_PREFIX$userId").apply()
    }
}
