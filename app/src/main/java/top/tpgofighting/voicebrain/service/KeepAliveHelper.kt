package top.tpgofighting.voicebrain.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 后台保活引导（v1.1.0）：国产 ROM（ColorOS / realme UI 等）默认对后台应用
 * 冻结与杀进程，需要用户手动给三件套：电池不优化、自启动允许、后台运行允许。
 * 这里只负责检测与跳转，不做任何静默提权。
 */
object KeepAliveHelper {
    private const val TAG = "KeepAliveHelper"

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            true
        }
    }

    /** 跳转系统电池优化豁免申请页（需 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限声明） */
    fun requestBatteryExemption(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "battery exemption intent failed: ${e.message}")
            openAppDetails(context)
        }
    }

    /**
     * 跳转 ColorOS / realme UI 的自启动管理页；不同版本组件名不一，逐个尝试，
     * 全部失败时回退到本应用详情页（用户可手动开「允许自启动/后台运行」）。
     */
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
            )
        )
        for (component in candidates) {
            try {
                val intent = Intent().apply {
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "autostart candidate failed: $component ${e.message}")
            }
        }
        openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "open app details failed: ${e.message}")
        }
    }

    /** Android 13+ 通知权限是否已授予（常驻通知/流体云的前提） */
    fun areNotificationsEnabled(context: Context): Boolean {
        return try {
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (e: Exception) {
            true
        }
    }

    fun sdkAtLeast(sdk: Int): Boolean = Build.VERSION.SDK_INT >= sdk
}
