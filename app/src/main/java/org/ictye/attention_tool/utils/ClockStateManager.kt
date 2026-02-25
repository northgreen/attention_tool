package org.ictye.attention_tool.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.ictye.attention_tool.ClockService
import org.ictye.attention_tool.PomodoroState

object ClockStateManager {
    private const val PREFS_NAME = "clock_state"
    private const val KEY_CURRENT_TIME = "current_time"
    private const val KEY_STATE = "state"
    private const val KEY_COMPLETED_POMODOROS = "completed_pomodoros"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_IS_RUNNING = "is_running"
    private const val KEY_FIRST_LAUNCH = "first_launch"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveState(
        currentTime: Long,
        state: PomodoroState,
        completedPomodoros: Int,
        isRunning: Boolean
    ) {
        if (!::prefs.isInitialized) return
        prefs.edit().apply {
            putLong(KEY_CURRENT_TIME, currentTime)
            putString(KEY_STATE, state.name)
            putInt(KEY_COMPLETED_POMODOROS, completedPomodoros)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            putBoolean(KEY_IS_RUNNING, isRunning)
            commit()
        }
    }

    fun getCurrentTime(): Long {
        if (!::prefs.isInitialized) return ClockService.Companion.WORK_DURATION
        val savedState = prefs.getString(KEY_STATE, PomodoroState.IDLE.name)
        
        if (savedState == PomodoroState.IDLE.name) {
            return ClockService.Companion.WORK_DURATION
        }

        val savedTime = prefs.getLong(KEY_CURRENT_TIME, ClockService.Companion.WORK_DURATION)
        val isRunning = prefs.getBoolean(KEY_IS_RUNNING, false)
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0)

        if (isRunning && savedState != PomodoroState.PAUSED.name) {
            val elapsed = System.currentTimeMillis() - timestamp
            val adjustedTime = savedTime - elapsed
            return maxOf(adjustedTime, 0)
        }

        return savedTime
    }

    fun getState(): PomodoroState {
        if (!::prefs.isInitialized) return PomodoroState.IDLE
        val stateName = prefs.getString(KEY_STATE, PomodoroState.IDLE.name)
        return try {
            PomodoroState.valueOf(stateName ?: PomodoroState.IDLE.name)
        } catch (e: Exception) {
            PomodoroState.IDLE
        }
    }

    fun getCompletedPomodoros(): Int {
        if (!::prefs.isInitialized) return 0
        return prefs.getInt(KEY_COMPLETED_POMODOROS, 0)
    }

    fun isRunning(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getBoolean(KEY_IS_RUNNING, false)
    }

    fun clearState() {
        if (!::prefs.isInitialized) return
        prefs.edit { clear() }
    }

    fun hasSavedState(): Boolean {
        if (!::prefs.isInitialized) return false
        val state = prefs.getString(KEY_STATE, PomodoroState.IDLE.name)
        return state != null && state != PomodoroState.IDLE.name
    }

    fun isFirstLaunch(): Boolean {
        if (!::prefs.isInitialized) return true
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirst) {
            prefs.edit { putBoolean(KEY_FIRST_LAUNCH, false) }
        }
        return isFirst
    }
}
