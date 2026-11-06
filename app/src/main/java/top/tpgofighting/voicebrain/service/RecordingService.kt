package top.tpgofighting.voicebrain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import top.tpgofighting.voicebrain.MainActivity
import top.tpgofighting.voicebrain.R

/**
 * 录音前台服务（v1.1.0）：录音期间把进程抬到前台优先级，配合 WakeLock
 * 保证锁屏/切后台不被系统杀；常驻通知带计时与停止动作。
 * Android 16（ColorOS 17）设备上通过反射启用 Live Updates 提升
 * （Promoted Ongoing + ProgressStyle + 短临界文本），系统会把它渲染到
 * 状态栏胶囊 / 流体云区域；低版本自动回退普通常驻通知。
 */
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        const val CHANNEL_ID = "voicebrain_recording"
        const val NOTIFICATION_ID = 4201
        const val ACTION_START = "top.tpgofighting.voicebrain.action.REC_START"
        const val ACTION_STOP = "top.tpgofighting.voicebrain.action.REC_STOP"
        const val EXTRA_DEVICE_LABEL = "extra_device_label"
        const val EXTRA_STARTED_AT = "extra_started_at"

        fun startIntent(context: Context, deviceLabel: String): Intent {
            return Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
                putExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var deviceLabel: String = "手机内置麦克风"
    private var startedAt: Long = System.currentTimeMillis()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // 来自通知「停止」：通知 UI 层先行止录，随后服务自停
                RecordingBus.requestStop()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                deviceLabel = intent?.getStringExtra(EXTRA_DEVICE_LABEL) ?: deviceLabel
                startedAt = intent?.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
                    ?: System.currentTimeMillis()
                val notification = buildNotification()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground failed: ${e.message}", e)
                }
                acquireWakeLock()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceBrain:Recording").apply {
                setReferenceCounted(false)
                // 最长 6 小时兜底，避免异常情况下无限持锁
                acquire(6 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "acquireWakeLock failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "releaseWakeLock failed: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "录音状态",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "录音进行中的常驻通知（保活 + 快速停止）"
                setShowBadge(false)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 16+：走平台 Builder 启用 Live Updates 提升，失败回退 Compat 普通常驻通知
        if (Build.VERSION.SDK_INT >= 36) {
            buildLiveUpdateNotification(openPending, stopPending)?.let { return it }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Voice Brain 正在录音")
            .setContentText("音源：$deviceLabel · 点按返回应用")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPending)
            .addAction(0, "停止录音", stopPending)
            .build()
    }

    /**
     * Android 16 Live Updates：ProgressStyle + Promoted Ongoing + 短临界文本。
     * 这些 API 在 compileSdk 35 尚不存在，全部走反射，失败返回 null 回退。
     * ColorOS 17（基于 Android 16）的流体云会承接被提升的进行中通知。
     */
    private fun buildLiveUpdateNotification(
        openPending: PendingIntent,
        stopPending: PendingIntent
    ): Notification? {
        return try {
            val icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_mic)
            val stopAction = Notification.Action.Builder(0, "停止录音", stopPending).build()
            val b = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle("Voice Brain 正在录音")
                .setContentText("音源：$deviceLabel · 点按返回应用")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(startedAt)
                .setUsesChronometer(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openPending)
                .addAction(stopAction)

            val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
            val style = progressStyleClass.getDeclaredConstructor().newInstance()
            progressStyleClass.getMethod("setProgress", Int::class.javaPrimitiveType)
                .invoke(style, 35)
            try {
                progressStyleClass.getMethod(
                    "setProgressTrackerIcon",
                    android.graphics.drawable.Icon::class.java
                ).invoke(style, icon)
            } catch (e: Exception) {
                // 追踪图标可选，失败不阻断
            }
            b.setStyle(style as Notification.Style)
            b.setRequestPromotedOngoing(true)
            b.setShortCriticalText("录音中")
            Log.i(TAG, "Live Updates promotion applied (ProgressStyle + promoted ongoing)")
            b.build()
        } catch (e: Exception) {
            Log.w(TAG, "Live Updates promotion unavailable, fallback: ${e.message}")
            null
        }
    }
}
