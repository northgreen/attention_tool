package org.ictye.ictyetools

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ictye.ictyetools.ui.theme.IctyeToolsTheme
import org.ictye.ictyetools.ui.theme.IdleColor
import org.ictye.ictyetools.ui.theme.LongBreakColor
import org.ictye.ictyetools.ui.theme.ShortBreakColor
import org.ictye.ictyetools.ui.theme.WorkColor
import androidx.core.net.toUri


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
            IctyeToolsTheme {
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
                bindService(serviceIntent, clockServiceConnection, Context.BIND_AUTO_CREATE)
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
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        
        startClockServiceInternal()
    }

    private fun checkBackgroundRestriction() {
        if (hasShownBackgroundRestrictionDialog) return
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        
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
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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
                // 可能已经解绑了
                println("onDestroy: 尝试解绑服务时发生异常")
                e.printStackTrace()
            }
            isBound = false
        }
    }
}

@SuppressLint("BatteryLife")
@RequiresApi(Build.VERSION_CODES.O)
@PreviewScreenSizes
@Composable
fun PomodoroApp(
    showBackgroundDialog: Boolean = false,
    onBackgroundDialogDismiss: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showBackgroundDialog) {
        AlertDialog(
            onDismissRequest = onBackgroundDialogDismiss,
            title = { Text("Background Restriction Detected") },
            text = { Text("To keep the timer running in the background, please disable battery optimization for this app.") },
            confirmButton = {
                TextButton(onClick = {
                    val packageName = context.packageName
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = "package:$packageName".toUri()
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = "package:$packageName".toUri()
                            }
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            e2.printStackTrace()
                        }
                    }
                    onBackgroundDialogDismiss()
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = onBackgroundDialogDismiss) {
                    Text("Cancel")
                }
            }
        )
    }

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
                        currentDestination = it
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> PomodoroTimerScreen(
                    modifier = Modifier.padding(innerPadding),
                    onSettingsClick = { currentDestination = AppDestinations.SETTINGS }
                )
                AppDestinations.TODO -> TodoScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateBack = { currentDestination = AppDestinations.HOME }
                )
            }
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
    }
    val state = remember(tick) {
        mainActivity.clockServiceBinder?.service?.pomodoroState?.value 
            ?: ClockStateManager.getState()
    }
    val completedPomodoros = remember(tick) {
        mainActivity.clockServiceBinder?.service?.completedPomodoros?.value 
            ?: ClockStateManager.getCompletedPomodoros()
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

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    var showPomodoroSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var workMinutes by rememberSaveable { mutableStateOf("25") }
    var shortBreakMinutes by rememberSaveable { mutableStateOf("5") }
    var longBreakMinutes by rememberSaveable { mutableStateOf("15") }
    
    if (showPomodoroSettings) {
        AlertDialog(
            onDismissRequest = { showPomodoroSettings = false },
            title = { Text("Pomodoro Settings") },
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
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("About") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("IctyeTools", fontWeight = FontWeight.Bold)
                    Text("Version 1.0.0")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A Pomodoro Timer with Todo list functionality.")
                }
            },
            confirmButton = {
                TextButton(onClick = { }) {
                    Text("OK")
                }
            }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        ListItem(
            headlineContent = { Text("Pomodoro Timer") },
            supportingContent = { Text("Configure work/break durations") },
            leadingContent = {
                Icon(Icons.Default.Refresh, contentDescription = null)
            },
            modifier = Modifier.clickable { }
        )
        
        HorizontalDivider()
        
        ListItem(
            headlineContent = { Text("Export Todo") },
            supportingContent = { Text("Share todo.txt file") },
            leadingContent = {
                Icon(Icons.Default.Share, contentDescription = null)
            },
            modifier = Modifier.clickable {
                val file = java.io.File(TodoManager.getTodoFilePath())
                if (file.exists()) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Todo"))
                }
            }
        )
        
        HorizontalDivider()
        
        ListItem(
            headlineContent = { Text("About") },
            supportingContent = { Text("App information") },
            leadingContent = {
                Icon(Icons.Default.Info, contentDescription = null)
            },
            modifier = Modifier.clickable { }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(modifier: Modifier = Modifier) {
    var todos by remember { mutableStateOf(TodoManager.loadTodos()) }
    var newTodoText by remember { mutableStateOf("") }
    var newTodoPriority by remember { mutableStateOf<Char?>(null) }
    var newTodoCreationDate by remember { mutableStateOf("") }
    var newTodoDueDate by remember { mutableStateOf("") }
    var newTodoTags by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTodoIndex by remember { mutableStateOf<Int?>(null) }
    var editingTodoText by remember { mutableStateOf("") }
    var editingTodoPriority by remember { mutableStateOf<Char?>(null) }
    var editingTodoCreationDate by remember { mutableStateOf("") }
    var editingTodoDueDate by remember { mutableStateOf("") }
    var editingTodoTags by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("priority") }
    var ascending by remember { mutableStateOf(true) }
    
    val sortedTodos = remember(todos, sortBy, ascending) {
        when (sortBy) {
            "priority" -> {
                val uncompleted = todos.filter { !it.isCompleted }.sortedWith(
                    compareBy { if (it.priority == null) 0 else ('Z' - it.priority + 1) }
                )
                val completed = todos.filter { it.isCompleted }
                if (ascending) uncompleted + completed else uncompleted.reversed() + completed
            }
            "alpha" -> {
                val uncompleted = todos.filter { !it.isCompleted }.sortedBy { it.text.lowercase() }
                val completed = todos.filter { it.isCompleted }
                if (ascending) uncompleted + completed else uncompleted.reversed() + completed
            }
            else -> {
                val uncompleted = todos.filter { !it.isCompleted }
                val completed = todos.filter { it.isCompleted }
                uncompleted + completed
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Todo List",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = sortBy == "priority",
                    onClick = { sortBy = "priority" },
                    label = { Text("Priority") }
                )
                Spacer(modifier = Modifier.size(8.dp))
                FilterChip(
                    selected = sortBy == "alpha",
                    onClick = { sortBy = "alpha" },
                    label = { Text("A-Z") }
                )
                Spacer(modifier = Modifier.size(8.dp))
                IconButton(onClick = { ascending = !ascending }) {
                    Icon(
                        imageVector = if (ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (ascending) "Ascending" else "Descending"
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(sortedTodos) { _, todo ->
                val originalIndex = todos.indexOf(todo)
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart && originalIndex >= 0) {
                            TodoManager.deleteTodo(originalIndex)
                            todos = TodoManager.loadTodos()
                            true
                        } else {
                            false
                        }
                    }
                )
                
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    },
                    content = {
                        ListItem(
                            modifier = Modifier.clickable {
                                if (originalIndex >= 0) {
                                    val item = todos[originalIndex]
                                    editingTodoIndex = originalIndex
                                    editingTodoText = item.text
                                    editingTodoPriority = item.priority
                                    editingTodoCreationDate = item.creationDate ?: ""
                                    editingTodoDueDate = item.dueDate ?: ""
                                    editingTodoTags = item.tags.joinToString(", ")
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = todo.text,
                                    color = if (todo.isCompleted) 
                                        MaterialTheme.colorScheme.onSurfaceVariant 
                                    else MaterialTheme.colorScheme.onSurface,
                                    style = if (todo.isCompleted)
                                        MaterialTheme.typography.bodyLarge.copy(
                                            textDecoration = TextDecoration.LineThrough
                                        )
                                    else MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Column {
                                    if (todo.priority != null || todo.projects.isNotEmpty() || todo.creationDate != null || todo.dueDate != null || todo.tags.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            todo.priority?.let {
                                                Text(
                                                    text = "($it)",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            todo.projects.forEach { project ->
                                                Text(
                                                    text = "+$project",
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            todo.creationDate?.let {
                                                Text(
                                                    text = it,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            todo.dueDate?.let {
                                                Text(
                                                    text = "Due: $it",
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            todo.tags.forEach { tag ->
                                                Text(
                                                    text = "#$tag",
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = todo.isCompleted,
                                    onCheckedChange = {
                                        if (originalIndex >= 0) {
                                            TodoManager.toggleComplete(originalIndex)
                                            todos = TodoManager.loadTodos()
                                        }
                                    }
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    if (originalIndex >= 0) {
                                        TodoManager.deleteTodo(originalIndex)
                                        todos = TodoManager.loadTodos()
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FilledIconButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.End)
                .size(64.dp)
        ) {
            Icon(
                Icons.Default.Add, 
                contentDescription = "Add todo",
                modifier = Modifier.size(32.dp)
            )
        }
    }
    
    if (showAddDialog) {
        var expanded by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Add Todo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        label = { Text("Task") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = newTodoPriority?.toString() ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Priority") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    newTodoPriority = null
                                    expanded = false
                                }
                            )
                            listOf("A", "B", "C", "D", "E").forEach { priority ->
                                DropdownMenuItem(
                                    text = { Text(priority) },
                                    onClick = {
                                        newTodoPriority = priority.first()
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newTodoCreationDate,
                        onValueChange = { newTodoCreationDate = it },
                        label = { Text("Creation Date (YYYY-MM-DD)") },
                        placeholder = { Text("Leave empty for today") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTodoDueDate,
                        onValueChange = { newTodoDueDate = it },
                        label = { Text("Due Date (YYYY-MM-DD)") },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTodoTags,
                        onValueChange = { newTodoTags = it },
                        label = { Text("Tags (comma separated)") },
                        placeholder = { Text("e.g. work, urgent") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTodoText.isNotBlank()) {
                        val creationDate = newTodoCreationDate.takeIf { it.isNotBlank() }
                        val dueDate = newTodoDueDate.takeIf { it.isNotBlank() }
                        val tags = newTodoTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        TodoManager.addTodo(newTodoText, newTodoPriority, creationDate, dueDate, tags)
                        todos = TodoManager.loadTodos()
                        newTodoText = ""
                        newTodoPriority = null
                        newTodoCreationDate = ""
                        newTodoDueDate = ""
                        newTodoTags = ""
                        showAddDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (editingTodoIndex != null) {
        var expanded by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { editingTodoIndex = null },
            title = { Text("Edit Todo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editingTodoText,
                        onValueChange = { editingTodoText = it },
                        label = { Text("Task") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = editingTodoPriority?.toString() ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Priority") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
                                onClick = {
                                    editingTodoPriority = null
                                    expanded = false
                                }
                            )
                            listOf("A", "B", "C", "D", "E").forEach { priority ->
                                DropdownMenuItem(
                                    text = { Text(priority) },
                                    onClick = {
                                        editingTodoPriority = priority.first()
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editingTodoCreationDate,
                        onValueChange = { editingTodoCreationDate = it },
                        label = { Text("Creation Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editingTodoDueDate,
                        onValueChange = { editingTodoDueDate = it },
                        label = { Text("Due Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editingTodoTags,
                        onValueChange = { editingTodoTags = it },
                        label = { Text("Tags (comma separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editingTodoText.isNotBlank() && editingTodoIndex != null) {
                        val creationDate = editingTodoCreationDate.takeIf { it.isNotBlank() }
                        val dueDate = editingTodoDueDate.takeIf { it.isNotBlank() }
                        val tags = editingTodoTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        TodoManager.updateTodo(editingTodoIndex!!, editingTodoText, editingTodoPriority, creationDate, dueDate, tags)
                        todos = TodoManager.loadTodos()
                        editingTodoIndex = null
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTodoIndex = null }) {
                    Text("Cancel")
                }
            }
        )
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
    TODO("Todo", Icons.Default.List),
    SETTINGS("Settings", Icons.Default.Settings)
}
