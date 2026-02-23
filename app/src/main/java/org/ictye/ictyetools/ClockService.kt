package org.ictye.ictyetools

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat.Builder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class ClockService : Service() {
    companion object{
        private const val CHANNEL_ID = "clock_service_channel"
        private const val NOTIFICATION_ID = 1024
    }

    var timer: CountDownTimer? = null

    val binder: ClockBinder = ClockBinder()
    private val _currentTime = MutableLiveData<Long>()
    val currentTime: LiveData<Long> get() = _currentTime

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
            .setContentTitle("Clock Service")
            .setContentText("Clock Service is running")
            .setSmallIcon(R.drawable.ic_clock)

        startForeground(NOTIFICATION_ID, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Clock Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    public fun startTimer() {
        println("startTimer")
        if (timer == null) {
            timer = object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    println("onTick: $millisUntilFinished")
                    updateNotification("${millisUntilFinished / 1000} seconds left")
                    _currentTime.postValue(millisUntilFinished)
                }
                override fun onFinish() {
                    println("onFinish")
                    updateNotification("Timer finished")
                }
            }
            timer?.start()
        } else{
            timer!!.cancel()
            timer!!.start()
        }
    }

    public fun stopTimer() {
        println("stopTimer")
        timer?.cancel()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val notification = Builder( this, CHANNEL_ID )
            .setContentTitle("Clock Service")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_clock)
        return notification.build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}