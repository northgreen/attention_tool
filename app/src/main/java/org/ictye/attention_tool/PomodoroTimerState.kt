package org.ictye.attention_tool

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import org.ictye.attention_tool.utils.ClockStateManager
import org.ictye.attention_tool.ui.theme.IdleColor
import org.ictye.attention_tool.ui.theme.LongBreakColor
import org.ictye.attention_tool.ui.theme.ShortBreakColor
import org.ictye.attention_tool.ui.theme.WorkColor

data class PomodoroTimerState(
    val time: Long = ClockService.WORK_DURATION,
    val state: PomodoroState = PomodoroState.IDLE,
    val completedPomodoros: Int = 0,
    val isRunning: Boolean = false,
    val totalTime: Long = ClockService.WORK_DURATION,
    val progress: Float = 0f,
    val stateColor: Color = IdleColor,
    val stateText: String = "Ready",
    val stateColorRaw: Color = IdleColor
)

@Composable
fun rememberPomodoroTimerState(
    tick: Int,
    getTime: () -> Long = { ClockStateManager.getCurrentTime() },
    getState: () -> PomodoroState = { ClockStateManager.getState() },
    getCompletedPomodoros: () -> Int = { ClockStateManager.getCompletedPomodoros() }
): PomodoroTimerState {
    val time = remember(tick) { getTime() }
    val state = remember(tick) { getState() }
    val completedPomodoros = remember(tick) { getCompletedPomodoros() }
    
    val isRunning = state == PomodoroState.WORK || state == PomodoroState.SHORT_BREAK || state == PomodoroState.LONG_BREAK
    
    val totalTime = when (state) {
        PomodoroState.WORK, PomodoroState.PAUSED -> ClockService.WORK_DURATION
        PomodoroState.SHORT_BREAK -> ClockService.SHORT_BREAK_DURATION
        PomodoroState.LONG_BREAK -> ClockService.LONG_BREAK_DURATION
        PomodoroState.IDLE -> ClockService.WORK_DURATION
    }
    
    val progress = if (totalTime > 0) (totalTime - time).toFloat() / totalTime else 0f
    
    val stateColorRaw = when (state) {
        PomodoroState.WORK -> WorkColor
        PomodoroState.SHORT_BREAK -> ShortBreakColor
        PomodoroState.LONG_BREAK -> LongBreakColor
        PomodoroState.PAUSED -> WorkColor
        PomodoroState.IDLE -> IdleColor
    }
    
    val stateColor by animateColorAsState(
        targetValue = stateColorRaw,
        animationSpec = tween(durationMillis = 500),
        label = "stateColor"
    )
    
    val stateText = when (state) {
        PomodoroState.WORK -> "Work Time"
        PomodoroState.SHORT_BREAK -> "Short Break"
        PomodoroState.LONG_BREAK -> "Long Break"
        PomodoroState.PAUSED -> "Paused"
        PomodoroState.IDLE -> "Ready"
    }
    
    return PomodoroTimerState(
        time = time,
        state = state,
        completedPomodoros = completedPomodoros,
        isRunning = isRunning,
        totalTime = totalTime,
        progress = progress,
        stateColor = stateColor,
        stateText = stateText,
        stateColorRaw = stateColorRaw
    )
}

@Composable
fun usePomodoroTimerTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(100)
        }
    }
    return tick
}
