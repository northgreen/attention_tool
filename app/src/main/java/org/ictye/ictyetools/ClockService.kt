package org.ictye.ictyetools

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
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat.Builder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

enum class PomodoroState {
    IDLE,
    WORK,
    SHORT_BREAK,
    LONG_BREAK
}

class ClockService : Service() {
    companion object{
        private const val CHANNEL_ID = "pomodoro_service_channel"
        private const val NOTIFICATION_ID = 1024
        const val WORK_DURATION = 25 * 60 * 1000L
        const val SHORT_BREAK_DURATION = 5 * 60 * 1000L
        const val LONG_BREAK_DURATION = 15 * 60 * 1000L
        const val POMODOROS_BEFORE_LONG_BREAK = 4
    }

    var timer: CountDownTimer? = null

    val binder: ClockBinder = ClockBinder()
    private val _currentTime = MutableLiveData<Long>()
    val currentTime: LiveData<Long> get() = _currentTime

    private val _pomodoroState = MutableLiveData(PomodoroState.IDLE)
    val pomodoroState: LiveData<PomodoroState> get() = _pomodoroState

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
        createNotificationChannel()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Builder( this, CHANNEL_ID )
            .setContentTitle("Pomodoro Timer")
            .setContentText("Ready to start")
            .setSmallIcon(R.drawable.ic_clock)

        startForeground(NOTIFICATION_ID, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Pomodoro Timer Channel",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            description = "Notifications for Pomodoro Timer"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    fun setDurations(work: Long, shortBreak: Long, longBreak: Long) {
        workDuration = work
        shortBreakDuration = shortBreak
        longBreakDuration = longBreak
    }

    fun startTimer() {
        val state = _pomodoroState.value
        if (state == PomodoroState.IDLE || state == null || state == PomodoroState.WORK) {
            startWork()
        } else {
            resumeTimer()
        }
    }

    fun pauseTimer() {
        timer?.cancel()
        timer = null
        updateNotification("Paused - ${formatTime(_currentTime.value ?: 0)}")
    }

    private fun resumeTimer() {
        val remainingTime = _currentTime.value ?: return
        if (remainingTime > 0) {
            timer = object : CountDownTimer(remainingTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    updateTimer(millisUntilFinished)
                }
                override fun onFinish() {
                    onTimerFinished()
                }
            }
            timer?.start()
        }
    }

    fun stopTimer() {
        timer?.cancel()
        timer = null
        _currentTime.postValue(0L)
        _pomodoroState.postValue(PomodoroState.IDLE)
        updateNotification("Timer stopped")
    }

    fun resetTimer() {
        timer?.cancel()
        timer = null
        _completedPomodoros.postValue(0)
        _pomodoroState.postValue(PomodoroState.IDLE)
        _currentTime.postValue(workDuration)
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
            override fun onFinish() {
                onTimerFinished()
            }
        }
        timer?.start()
    }

    private fun updateTimer(millisUntilFinished: Long) {
        _currentTime.postValue(millisUntilFinished)
        val state = _pomodoroState.value
        val stateText = when(state) {
            PomodoroState.WORK -> "Work"
            PomodoroState.SHORT_BREAK -> "Short Break"
            PomodoroState.LONG_BREAK -> "Long Break"
            else -> "Ready"
        }
        updateNotification("$stateText - ${formatTime(millisUntilFinished)}")
    }

    private fun onTimerFinished() {
        val state = _pomodoroState.value
        playNotificationSound()

        when (state) {
            PomodoroState.WORK -> {
                val completed = (_completedPomodoros.value ?: 0) + 1
                _completedPomodoros.postValue(completed)
                
                if (completed % POMODOROS_BEFORE_LONG_BREAK == 0) {
                    updateNotification("Work finished! Time for a long break")
                    startLongBreak()
                } else {
                    updateNotification("Work finished! Time for a short break")
                    startShortBreak()
                }
            }
            PomodoroState.SHORT_BREAK, PomodoroState.LONG_BREAK -> {
                updateNotification("Break finished! Ready to work")
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

    @SuppressLint("DefaultLocale")
    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val notification = Builder( this, CHANNEL_ID )
            .setContentTitle("Pomodoro Timer")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_clock)
            .setOngoing(true)
            .setSilent(true)
        return notification.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}