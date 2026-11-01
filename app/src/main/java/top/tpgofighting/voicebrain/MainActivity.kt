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

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.showToast("已获取麦克风录音权限")
        } else {
            viewModel.showToast("需要麦克风权限才能进行实时流式语音转写")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestAudioPermission()
        observeUiEvents()

        setContent {
            TPVoiceBrainTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private fun observeUiEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
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
                type = "audio/wav"
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
