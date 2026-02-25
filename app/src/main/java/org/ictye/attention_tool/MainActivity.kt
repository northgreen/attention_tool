package org.ictye.attention_tool

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ictye.attention_tool.dialogs.BackgroundDialog
import org.ictye.attention_tool.ui.theme.AttentionToolTheme
import org.ictye.attention_tool.ui.theme.ShortBreakColor
import org.ictye.attention_tool.ui.theme.WorkColor
import org.ictye.attention_tool.utils.ClockStateManager
import org.ictye.attention_tool.utils.TodoManager


class MainActivity : ComponentActivity() {
    var clockServiceBinder: ClockService.ClockBinder? = null
    private var isBound = false
    var showBackgroundDialog by mutableStateOf(false)
        private set

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ClockStateManager.init(this)
        TodoManager.init(this)

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            AttentionToolTheme {
                PomodoroApp(
                    showBackgroundDialog = showBackgroundDialog,
                    onBackgroundDialogDismiss = { showBackgroundDialog = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val shouldRestore = intent?.action == "FROM_NOTIFICATION"
        bindServiceIfNeeded(restore = shouldRestore)
    }

    private fun bindServiceIfNeeded(restore: Boolean = false) {
        if (!isBound) {
            try {
                val serviceIntent = Intent(this, ClockService::class.java).apply {
                    action = if (restore) "RESTORE" else null
                }
                startService(serviceIntent)
                bindService(serviceIntent, clockServiceConnection, BIND_AUTO_CREATE)
                isBound = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun bindServiceWithoutRestore() {
        if (!isBound) {
            try {
                val serviceIntent = Intent(this, ClockService::class.java)
                startService(serviceIntent)
                bindService(serviceIntent, clockServiceConnection, BIND_AUTO_CREATE)
                isBound = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val clockServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            clockServiceBinder = binder as ClockService.ClockBinder
            ClockStateManager.init(this@MainActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            clockServiceBinder = null
            isBound = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startClockServiceInternal()
        }
    }

    private var hasShownBackgroundRestrictionDialog = false

    @RequiresApi(Build.VERSION_CODES.O)
    fun startClockService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        startClockServiceInternal()
    }

    private fun checkBackgroundRestriction() {
        if (hasShownBackgroundRestrictionDialog) return

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val packageName = packageName

        val isIgnoringBatteryOptimizations =
            powerManager.isIgnoringBatteryOptimizations(packageName)

        if (!isIgnoringBatteryOptimizations) {
            hasShownBackgroundRestrictionDialog = true
            showBackgroundDialog = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startClockServiceInternal() {
        if (clockServiceBinder == null) {
            checkBackgroundRestriction()
            val serviceIntent = Intent(this, ClockService::class.java)
            startForegroundService(serviceIntent)
            Handler(Looper.getMainLooper()).postDelayed({
                bindServiceIfNeeded(restore = true)
            }, 500)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            try {
                unbindService(clockServiceConnection)
            } catch (e: Exception) {
                println("onDestroy: 尝试解绑服务时发生异常")
                e.printStackTrace()
            }
            isBound = false
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview
@Composable
fun PomdoroAppPreview() {
    PomodoroApp(
        showBackgroundDialog = false,
        onBackgroundDialogDismiss = {}
    )
}

@SuppressLint("BatteryLife")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PomodoroApp(
    showBackgroundDialog: Boolean = false,
    onBackgroundDialogDismiss: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mainActivity = context as? MainActivity

    val tick = usePomodoroTimerTick()

    val timerState = rememberPomodoroTimerState(
        tick = tick,
        getTime = {
            mainActivity?.clockServiceBinder?.service?.currentTime?.value
                ?: ClockStateManager.getCurrentTime()
        },
        getState = {
            mainActivity?.clockServiceBinder?.service?.pomodoroState?.value
                ?: ClockStateManager.getState()
        },
        getCompletedPomodoros = {
            mainActivity?.clockServiceBinder?.service?.completedPomodoros?.value
                ?: ClockStateManager.getCompletedPomodoros()
        }
    )

    if (showBackgroundDialog) {
        BackgroundDialog(context = context, onBackgroundDialogDismiss = onBackgroundDialogDismiss)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        if (it.imageVector != null) Icon(
                            it.imageVector,
                            contentDescription = it.label
                        )
                        else if (it.icon != null) Icon(
                            painter = painterResource(it.icon),
                            contentDescription = it.label
                        )
                        else Icon(
                            Icons.Default.Warning,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = {
                        currentDestination = it
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(300)
                    ))
                        .togetherWith(
                            fadeOut(animationSpec = tween(300)) + scaleOut(
                                targetScale = 0.92f,
                                animationSpec = tween(300)
                            )
                        )
                },
                label = "pageTransition"
            ) { destination ->
                when (destination) {
                    AppDestinations.HOME -> PomodoroTimerScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSettingsClick = { currentDestination = AppDestinations.SETTINGS },
                        timerState = timerState,
                        onStartTimer = {
                            mainActivity?.startClockService()
                            mainActivity?.clockServiceBinder?.service?.startTimer()
                        },
                        onPauseTimer = {
                            mainActivity?.startClockService()
                            mainActivity?.clockServiceBinder?.service?.pauseTimer()
                        },
                        onStopTimer = {
                            mainActivity?.startClockService()
                            mainActivity?.clockServiceBinder?.service?.stopTimer()
                        },
                        onResetTimer = {
                            mainActivity?.bindServiceWithoutRestore()
                            mainActivity?.clockServiceBinder?.service?.resetTimer()
                        }
                    )

                    AppDestinations.TODO -> TodoScreen(modifier = Modifier.padding(innerPadding))
                    AppDestinations.SETTINGS -> SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        onNavigateBack = { currentDestination = AppDestinations.HOME }
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PomodoroTimerScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit,
    timerState: PomodoroTimerState,
    onStartTimer: () -> Unit = {},
    onPauseTimer: () -> Unit = {},
    onStopTimer: () -> Unit = {},
    onResetTimer: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(timerState.stateColor.copy(alpha = 0.1f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TimerHeader(onSettingsClick = onSettingsClick)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = timerState.stateText,
            style = MaterialTheme.typography.headlineMedium,
            color = timerState.stateColor,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        TimerDisplay(
            time = timerState.time,
            completedPomodoros = timerState.completedPomodoros,
            progress = timerState.progress,
            stateColor = timerState.stateColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        TimerControls(
            isRunning = timerState.isRunning,
            state = timerState.state,
            onStartTimer = onStartTimer,
            onPauseTimer = onPauseTimer,
            onStopTimer = onStopTimer,
            onResetTimer = onResetTimer
        )
    }
}

@SuppressLint("DefaultLocale")
private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun TimerHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun TimerDisplay(
    time: Long,
    completedPomodoros: Int,
    progress: Float,
    stateColor: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(280.dp)
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = stateColor,
            trackColor = stateColor.copy(alpha = 0.2f),
            strokeWidth = 20.dp
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
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun TimerControls(
    isRunning: Boolean,
    state: PomodoroState,
    onStartTimer: () -> Unit = {},
    onPauseTimer: () -> Unit = {},
    onStopTimer: () -> Unit = {},
    onResetTimer: () -> Unit = {}
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) WorkColor else ShortBreakColor,
        animationSpec = tween(durationMillis = 500),
        label = "buttonColor"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = onStopTimer,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Icon(
                painterResource(R.drawable.stop_light),
                contentDescription = "Stop",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        FilledIconButton(
            onClick = {
                if (state == PomodoroState.PAUSED) {
                    onStartTimer()
                } else if (isRunning) {
                    onPauseTimer()
                } else {
                    onStartTimer()
                }
            },
            modifier = Modifier.size(80.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = buttonColor
            )
        ) {
            Icon(
                painterResource(if (isRunning) R.drawable.pause_light else R.drawable.play_light),
                contentDescription = "Play/Pause",
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        FilledIconButton(
            onClick = onResetTimer,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset")
        }
    }
}

enum class AppDestinations(
    val label: String,
    val imageVector: ImageVector?,
    val icon: Int? = null
) {
    HOME("Pomodoro", null, R.drawable.clock),
    TODO("Todo", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Default.Settings)
}
