package org.ictye.ictyetools

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ictye.ictyetools.ui.theme.IctyeToolsTheme
import org.ictye.ictyetools.ui.theme.IdleColor
import org.ictye.ictyetools.ui.theme.LongBreakColor
import org.ictye.ictyetools.ui.theme.ShortBreakColor
import org.ictye.ictyetools.ui.theme.WorkColor

object ClockStateHolder {
    var currentTime: Long = ClockService.WORK_DURATION
    var state: PomodoroState = PomodoroState.IDLE
    var completed: Int = 0
}

class MainActivity : ComponentActivity() {
    var clockServiceBinder: ClockService.ClockBinder? = null
    private var isBound = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ClockStateManager.init(this)
        bindServiceIfNeeded()

        enableEdgeToEdge()
        setContent {
            IctyeToolsTheme {
                PomodoroApp()
            }
        }
    }
    
    private fun bindServiceIfNeeded() {
        if (!isBound) {
            try {
                Intent(this, ClockService::class.java).also {
                    intent -> bindService(intent, clockServiceConnection, Context.BIND_AUTO_CREATE)
                }
                isBound = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val clockServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            clockServiceBinder = binder as ClockService.ClockBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            clockServiceBinder = null
            isBound = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startClockService() {
        if (clockServiceBinder == null) {
            val serviceIntent = Intent(this, ClockService::class.java)
            startForegroundService(serviceIntent)
            // 延迟尝试绑定
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                bindServiceIfNeeded()
            }, 500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try {
                unbindService(clockServiceConnection)
            } catch (e: Exception) {
                // 可能已经解绑了
            }
            isBound = false
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@PreviewScreenSizes
@Composable
fun PomodoroApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showSettings by remember { mutableStateOf(false) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = {
                        if (it == AppDestinations.SETTINGS) {
                            showSettings = true
                        } else {
                            currentDestination = it
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            PomodoroTimerScreen(
                modifier = Modifier.padding(innerPadding),
                onSettingsClick = { showSettings = true }
            )
        }
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PomodoroTimerScreen(modifier: Modifier = Modifier, onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    val mainActivity = context as MainActivity
    
    var tick by remember { mutableIntStateOf(0) }
    
    val time = remember(tick) { 
        mainActivity.clockServiceBinder?.service?.currentTime?.value 
            ?: ClockStateManager.getCurrentTime()
            ?: ClockService.WORK_DURATION
    }
    val state = remember(tick) { 
        mainActivity.clockServiceBinder?.service?.pomodoroState?.value 
            ?: ClockStateManager.getState()
            ?: PomodoroState.IDLE
    }
    val completedPomodoros = remember(tick) { 
        mainActivity.clockServiceBinder?.service?.completedPomodoros?.value 
            ?: ClockStateManager.getCompletedPomodoros()
            ?: 0
    }
    
    val isRunning = state == PomodoroState.WORK || state == PomodoroState.SHORT_BREAK || state == PomodoroState.LONG_BREAK
    
    // 定时刷新 tick
    LaunchedEffect(Unit) {
        while (true) {
            tick++
            delay(100)
        }
    }
    
    val totalTime = when (state) {
        PomodoroState.WORK, PomodoroState.PAUSED -> ClockService.WORK_DURATION
        PomodoroState.SHORT_BREAK -> ClockService.SHORT_BREAK_DURATION
        PomodoroState.LONG_BREAK -> ClockService.LONG_BREAK_DURATION
        PomodoroState.IDLE -> ClockService.WORK_DURATION
    }

    val progress = if (totalTime > 0) (totalTime - time).toFloat() / totalTime else 0f
    val stateColor = when (state) {
        PomodoroState.WORK -> WorkColor
        PomodoroState.SHORT_BREAK -> ShortBreakColor
        PomodoroState.LONG_BREAK -> LongBreakColor
        PomodoroState.PAUSED -> WorkColor
        PomodoroState.IDLE -> IdleColor
    }
    val stateText = when (state) {
        PomodoroState.WORK -> "Work Time"
        PomodoroState.SHORT_BREAK -> "Short Break"
        PomodoroState.LONG_BREAK -> "Long Break"
        PomodoroState.PAUSED -> "Paused"
        PomodoroState.IDLE -> "Ready"
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Text(
            text = stateText,
            style = MaterialTheme.typography.headlineMedium,
            color = stateColor,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(280.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = stateColor,
                trackColor = stateColor.copy(alpha = 0.2f),
                strokeWidth = 12.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTime(time),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pomodoros: $completedPomodoros",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = {
                    mainActivity.startClockService()
                    mainActivity.clockServiceBinder?.service?.stopTimer()
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Stop")
            }

            FilledIconButton(
                onClick = {
                    mainActivity.startClockService()
                    val service = mainActivity.clockServiceBinder?.service
                    if (state == PomodoroState.PAUSED) {
                        service?.startTimer()
                    } else if (isRunning) {
                        service?.pauseTimer()
                    } else {
                        service?.startTimer()
                    }
                },
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isRunning) WorkColor else ShortBreakColor
                )
            ) {
                Text(
                    text = if (state == PomodoroState.PAUSED) "▶" else if (isRunning) "||" else "▶",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            FilledIconButton(
                onClick = {
                    mainActivity.startClockService()
                    mainActivity.clockServiceBinder?.service?.resetTimer()
                },
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    var workMinutes by rememberSaveable { mutableStateOf("25") }
    var shortBreakMinutes by rememberSaveable { mutableStateOf("5") }
    var longBreakMinutes by rememberSaveable { mutableStateOf("15") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = workMinutes,
                    onValueChange = { workMinutes = it },
                    label = { Text("Work Duration (minutes)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shortBreakMinutes,
                    onValueChange = { shortBreakMinutes = it },
                    label = { Text("Short Break (minutes)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = longBreakMinutes,
                    onValueChange = { longBreakMinutes = it },
                    label = { Text("Long Break (minutes)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                mainActivity?.clockServiceBinder?.service?.setDurations(
                    (workMinutes.toLongOrNull() ?: 25) * 60 * 1000L,
                    (shortBreakMinutes.toLongOrNull() ?: 5) * 60 * 1000L,
                    (longBreakMinutes.toLongOrNull() ?: 15) * 60 * 1000L
                )
                mainActivity?.clockServiceBinder?.service?.resetTimer()
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@SuppressLint("DefaultLocale")
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Pomodoro", Icons.Default.Refresh),
    SETTINGS("Settings", Icons.Default.Settings)
}
