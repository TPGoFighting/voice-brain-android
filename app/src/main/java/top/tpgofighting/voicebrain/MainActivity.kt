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
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import top.tpgofighting.voicebrain.ui.screens.MainScreen
import top.tpgofighting.voicebrain.ui.theme.TPVoiceBrainTheme
import top.tpgofighting.voicebrain.ui.viewmodel.MainUiEvent
import top.tpgofighting.voicebrain.ui.viewmodel.MainViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingStartRecordingAfterPermission = false

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.showToast("已获取麦克风录音权限")
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestAudioPermission()
        checkAndRequestNotificationPermission()
        observeUiEvents()
        observeRecordingKeepScreenOn()

        setContent {
            TPVoiceBrainTheme {
                MainScreen(viewModel = viewModel)
            }
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

    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
