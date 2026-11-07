package top.tpgofighting.voicebrain

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import top.tpgofighting.voicebrain.ui.screens.MainScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.tpgofighting.voicebrain.ui.theme.TPVoiceBrainTheme
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiEvent
import top.tpgofighting.voicebrain.ui.viewmodel.MainViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        /** 桌面小组件 / 快捷磁贴的直达录音标记：打开界面后立即开始录音 */
        const val EXTRA_START_RECORDING = "top.tpgofighting.voicebrain.START_RECORDING"
    }

    private val viewModel: MainViewModel by viewModels()
    private var pendingStartRecordingAfterPermission = false

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onMicPermissionResult(isGranted)
        if (isGranted) {
            viewModel.showToast("已获取麦克风录音权限")
            // 通知权限只为录音前台服务通知服务，跟随首次录音一并申请，不在启动时打扰
            checkAndRequestNotificationPermission()
            if (pendingStartRecordingAfterPermission) {
                pendingStartRecordingAfterPermission = false
                viewModel.startRecording()
            }
        } else {
            pendingStartRecordingAfterPermission = false
            viewModel.showToast("需要麦克风录音权限才能进行实时流式语音转写")
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 结果不阻断功能：无通知权限时前台服务照常运行，仅通知不进通知栏 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动屏（规格 §2.7）：保持到首批状态（会话列表或 Vault 状态）就绪，硬顶 800ms，不造假停留
        val splashStartMs = SystemClock.uptimeMillis()
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            val s = viewModel.uiState.value
            (s.isLoadingSessions || !s.obsidianStatus.loaded) &&
                SystemClock.uptimeMillis() - splashStartMs < 800
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 权限一律按需申请，启动时不索权：麦克风在首次点「开始录音」时先弹
        // 用途说明再调系统框；通知权限在首次录音获批后顺带申请。
        observeUiEvents()
        observeRecordingKeepScreenOn()

        setContent {
            // 主题模式（设置页可改）：system 跟随系统 / light 浅色 / dark 深色，持久化在 voicebrain_prefs
            val prefs = remember { getSharedPreferences("voicebrain_prefs", MODE_PRIVATE) }
            var themeMode by remember {
                androidx.compose.runtime.mutableStateOf(prefs.getString("theme_mode", "system") ?: "system")
            }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            TPVoiceBrainTheme(darkTheme = darkTheme) {
                MainScreen(
                    viewModel = viewModel,
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        prefs.edit().putString("theme_mode", mode).apply()
                    }
                )
            }
        }

        handleStartRecordingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStartRecordingIntent(intent)
    }

    /** 小组件/磁贴直达：权限未授予时 startRecording 内部会先弹用途说明，不绕过确认 */
    private fun handleStartRecordingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_RECORDING, false) == true) {
            intent.removeExtra(EXTRA_START_RECORDING)
            viewModel.startRecording()
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置页返回时刷新保活状态与外接设备清单（DJI 接收器插拔）
        viewModel.refreshBatteryState()
        viewModel.refreshAudioDevices()
    }

    /** 录音期间保持屏幕常亮（Activity 可见时生效），停止后自动恢复系统休眠 */
    private fun observeRecordingKeepScreenOn() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.isRecording) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun observeUiEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        is MainUiEvent.RequestAudioPermission -> {
                            pendingStartRecordingAfterPermission = true
                            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        is MainUiEvent.ShareWavFile -> {
                            shareAudioFile(event.file)
                        }
                    }
                }
            }
        }
    }

    private fun shareAudioFile(file: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "TP Voice Brain 录音导出: ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "导出/分享无损录音文件")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "调起系统导出分享失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

}
