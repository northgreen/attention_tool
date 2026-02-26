package org.ictye.attention_tool

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.ictye.attention_tool.utils.ClockStateManager
import org.ictye.attention_tool.ui.theme.LongBreakNotificationColor
import org.ictye.attention_tool.ui.theme.PausedNotificationColor
import org.ictye.attention_tool.ui.theme.ShortBreakNotificationColor
import org.ictye.attention_tool.ui.theme.WorkNotificationColor
import org.ictye.attention_tool.ui.theme.IdleNotificationColor
import androidx.compose.ui.graphics.toArgb

enum class PomodoroState {
    IDLE,
    WORK,
    SHORT_BREAK,
    LONG_BREAK,
    PAUSED
}

class ClockService : Service() {
    companion object{
        private const val CHANNEL_ID = "pomodoro_service_channel"
        private const val NOTIFICATION_ID = 1024
        const val WORK_DURATION = 25 * 60 * 1000L
        const val SHORT_BREAK_DURATION = 5 * 60 * 1000L
        const val LONG_BREAK_DURATION = 15 * 60 * 1000L
        const val POMODOROS_BEFORE_LONG_BREAK = 4
        
        const val ACTION_START = "org.ictye.attention_tool.ACTION_START"
        const val ACTION_PAUSE = "org.ictye.attention_tool.ACTION_PAUSE"
        const val ACTION_STOP = "org.ictye.attention_tool.ACTION_STOP"
    }

    /// The timer that counts down the time.
    var timer: CountDownTimer? = null

    val binder: ClockBinder = ClockBinder()
    private val _currentTime = MutableLiveData(WORK_DURATION)
    val currentTime: LiveData<Long> get() = _currentTime

    private val _pomodoroState = MutableLiveData(PomodoroState.IDLE)
    val pomodoroState: LiveData<PomodoroState> get() = _pomodoroState

    private var _previousState: PomodoroState? = null
    
    private val _completedPomodoros = MutableLiveData(0)
    val completedPomodoros: LiveData<Int> get() = _completedPomodoros

    private var workDuration = WORK_DURATION
    private var shortBreakDuration = SHORT_BREAK_DURATION
    private var longBreakDuration = LONG_BREAK_DURATION

    inner class ClockBinder : Binder() {
        val service: ClockService
            get() = this@ClockService
    }

    override fun onBind(intent: Intent): IBinder = binder

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        ClockStateManager.init(this)
        createNotificationChannel()
    }

    private fun restoreState() {
        val state = ClockStateManager.getState()
        val currentTime = ClockStateManager.getCurrentTime()
        val completed = ClockStateManager.getCompletedPomodoros()
        val wasRunning = ClockStateManager.isRunning()

        _pomodoroState.postValue(state)
        _currentTime.postValue(currentTime)
        _completedPomodoros.postValue(completed)

        if (wasRunning && currentTime > 0 && state != PomodoroState.PAUSED && state != PomodoroState.IDLE) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startTimer()
            }, 1000)
        }
    }

    private fun saveState() {
        val isRunning = _pomodoroState.value == PomodoroState.WORK ||
                _pomodoroState.value == PomodoroState.SHORT_BREAK ||
                _pomodoroState.value == PomodoroState.LONG_BREAK
        ClockStateManager.saveState(
            currentTime = _currentTime.value ?: workDuration,
            state = _pomodoroState.value ?: PomodoroState.IDLE,
            completedPomodoros = _completedPomodoros.value ?: 0,
            isRunning = isRunning
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationBuilder = Builder(this, CHANNEL_ID)
            .setContentTitle("Pomodoro Timer")
            .setContentText("Ready to start")
            .setSmallIcon(R.drawable.clock)
            .setOngoing(true)
            .setSilent(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            notificationBuilder.setRequestPromotedOngoing(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + workDuration)
                .setStyle(NotificationCompat.ProgressStyle().setProgress(0))
                .setShortCriticalText("Ready - 25:00")
        }

        startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        
        when (intent?.action) {
            "RESTORE" -> restoreState()
            ACTION_START -> startTimer()
            ACTION_PAUSE -> pauseTimer()
            ACTION_STOP -> stopTimer()
        }
        
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Pomodoro Timer Channel",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            description = "Notifications for Pomodoro Timer"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    fun setDurations(work: Long, shortBreak: Long, longBreak: Long) {
        workDuration = work
        shortBreakDuration = shortBreak
        longBreakDuration = longBreak
    }

    fun startTimer() = when (_pomodoroState.value) {
            PomodoroState.PAUSED -> {
                resumeTimer()
            }
            PomodoroState.IDLE, null -> {
                startWork()
            }
            PomodoroState.WORK, PomodoroState.SHORT_BREAK, PomodoroState.LONG_BREAK -> {
                resumeTimer()
            }
        }

    private fun resumeTimer() {
        val remainingTime = _currentTime.value
        if (remainingTime == null || remainingTime <= 0) {
            startWork()
            return
        }
        
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
        
        val currentState = _pomodoroState.value
        val previousState = _previousState ?: PomodoroState.WORK
        
        if (currentState == PomodoroState.PAUSED) {
            _pomodoroState.postValue(previousState)
        }
        
        timer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateTimer(millisUntilFinished)
            }
            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onFinish() {
                onTimerFinished()
            }
        }
        timer?.start()
    }

    fun pauseTimer() {
        val currentState = _pomodoroState.value
        if (currentState != PomodoroState.IDLE && currentState != PomodoroState.PAUSED) {
            _previousState = currentState
        }
        
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
        
        _pomodoroState.postValue(PomodoroState.PAUSED)
        ClockStateManager.saveState(
            currentTime = _currentTime.value ?: workDuration,
            state = PomodoroState.PAUSED,
            completedPomodoros = _completedPomodoros.value ?: 0,
            isRunning = false
        )
        updateNotification("Paused - ${formatTime(_currentTime.value ?: 0)}")
    }

    fun stopTimer() {
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
        _currentTime.postValue(workDuration)
        _pomodoroState.postValue(PomodoroState.IDLE)
        ClockStateManager.saveState(
            currentTime = workDuration,
            state = PomodoroState.IDLE,
            completedPomodoros = _completedPomodoros.value ?: 0,
            isRunning = false
        )
        updateNotification("Timer stopped")
    }

    fun resetTimer() {
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
        _completedPomodoros.postValue(0)
        _pomodoroState.postValue(PomodoroState.IDLE)
        _currentTime.postValue(workDuration)
        ClockStateManager.saveState(
            currentTime = workDuration,
            state = PomodoroState.IDLE,
            completedPomodoros = 0,
            isRunning = false
        )
        updateNotification("Ready to start")
    }

    private fun startWork() {
        timer?.cancel()
        _pomodoroState.postValue(PomodoroState.WORK)
        _currentTime.postValue(workDuration)
        timer = object : CountDownTimer(workDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateTimer(millisUntilFinished)
            }
            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onFinish() {
                onTimerFinished()
            }
        }
        timer?.start()
    }

    private fun startShortBreak() {
        timer?.cancel()
        _pomodoroState.postValue(PomodoroState.SHORT_BREAK)
        _currentTime.postValue(shortBreakDuration)
        timer = object : CountDownTimer(shortBreakDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateTimer(millisUntilFinished)
            }
            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onFinish() {
                onTimerFinished()
            }
        }
        timer?.start()
    }

    private fun startLongBreak() {
        timer?.cancel()
        _pomodoroState.postValue(PomodoroState.LONG_BREAK)
        _currentTime.postValue(longBreakDuration)
        timer = object : CountDownTimer(longBreakDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateTimer(millisUntilFinished)
            }
            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onFinish() {
                onTimerFinished()
            }
        }
        timer?.start()
    }

    private fun updateTimer(millisUntilFinished: Long) {
        _currentTime.postValue(millisUntilFinished)
        saveState()
        val state = _pomodoroState.value
        val stateText = when(state) {
            PomodoroState.WORK -> "Work"
            PomodoroState.SHORT_BREAK -> "Short Break"
            PomodoroState.LONG_BREAK -> "Long Break"
            else -> "Ready"
        }
        updateNotification("$stateText - ${formatTime(millisUntilFinished)}", silent = true)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun onTimerFinished() {
        val state = _pomodoroState.value
        playNotificationSound()
        vibrate()

        when (state) {
            PomodoroState.WORK -> {
                val completed = (_completedPomodoros.value ?: 0) + 1
                _completedPomodoros.postValue(completed)
                
                if (completed % POMODOROS_BEFORE_LONG_BREAK == 0) {
                    updateNotification("Work finished! Time for a long break", silent = false)
                    startLongBreak()
                } else {
                    updateNotification("Work finished! Time for a short break", silent = false)
                    startShortBreak()
                }
            }
            PomodoroState.SHORT_BREAK, PomodoroState.LONG_BREAK -> {
                updateNotification("Break finished! Ready to work", silent = false)
                startWork()
            }
            else -> {}
        }
    }

    private fun playNotificationSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateNotification(text: String, silent: Boolean = true) {
        val notification = buildNotification(text, silent)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String, silent: Boolean = true): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "FROM_NOTIFICATION"
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val state = _pomodoroState.value
        val remainingTime = _currentTime.value ?: 0
        val totalDuration = when (state) {
            PomodoroState.WORK, PomodoroState.PAUSED -> workDuration
            PomodoroState.SHORT_BREAK -> shortBreakDuration
            PomodoroState.LONG_BREAK -> longBreakDuration
            else -> workDuration
        }
        
        val progress = if (totalDuration > 0) {
            ((totalDuration - remainingTime).toFloat() / totalDuration * 100).toInt()
        } else 0
        
        val stateColor = when (state) {
            PomodoroState.WORK -> WorkNotificationColor
            PomodoroState.SHORT_BREAK -> ShortBreakNotificationColor
            PomodoroState.LONG_BREAK -> LongBreakNotificationColor
            PomodoroState.PAUSED -> PausedNotificationColor
            else -> IdleNotificationColor
        }
        
        val statusText = when (state) {
            PomodoroState.WORK -> "Working"
            PomodoroState.SHORT_BREAK -> "Short Break"
            PomodoroState.LONG_BREAK -> "Long Break"
            PomodoroState.PAUSED -> "Paused"
            else -> "Ready"
        }

        val builder = Builder(this, CHANNEL_ID)
            .setContentTitle("Pomodoro Timer")
            .setContentText(text)
            .setSmallIcon(R.drawable.clock)
            .setOngoing(true)
            .setSilent(silent)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(stateColor.toArgb())
            .setColorized(true)

        if (!silent) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        val startPauseIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = if (state == PomodoroState.WORK || state == PomodoroState.SHORT_BREAK || state == PomodoroState.LONG_BREAK) {
                ACTION_PAUSE
            } else {
                ACTION_START
            }
        }
        val startPausePendingIntent = PendingIntent.getBroadcast(
            this, 1, startPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isRunning = state == PomodoroState.WORK || state == PomodoroState.SHORT_BREAK || state == PomodoroState.LONG_BREAK
        
        if (isRunning) {
            builder.addAction(R.drawable.pause_light, "Pause", startPausePendingIntent)
        } else {
            builder.addAction(R.drawable.play_light, "Start", startPausePendingIntent)
        }
        builder.addAction(R.drawable.stop_light, "Stop", stopPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setRequestPromotedOngoing(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + remainingTime)
            
            builder.setStyle(NotificationCompat.ProgressStyle().setProgress(progress))
            builder.setShortCriticalText("$statusText - ${formatTime(remainingTime)}")
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
