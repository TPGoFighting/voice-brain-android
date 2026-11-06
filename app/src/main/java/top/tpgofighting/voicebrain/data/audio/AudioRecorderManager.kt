package top.tpgofighting.voicebrain.data.audio

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioRecorderManager(
    private val context: Context,
    private val onChunk: (ByteArray) -> Unit,
    private val onVolume: (Float) -> Unit,
    private val onDeviceEvent: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "AudioRecorderManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_SIZE_BYTES = 3200 // 100ms at 16kHz 16-bit mono

        /** 枚举当前可用的录音输入设备（内置麦 / USB 接收器 / 蓝牙 / 有线耳麦） */
        fun listInputDevices(context: Context): List<top.tpgofighting.voicebrain.model.AudioInputDevice> {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                ?: return emptyList()
            val devices = am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
            val seen = mutableSetOf<String>()
            val result = mutableListOf<top.tpgofighting.voicebrain.model.AudioInputDevice>()
            for (d in devices) {
                if (!d.isSource) continue
                val kind = when (d.type) {
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "builtin"
                    android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                    android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb"
                    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    @Suppress("DEPRECATION") android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth"
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
                    else -> "other"
                }
                if (kind == "other") continue
                val rawName = try {
                    d.productName?.toString()?.trim().orEmpty()
                } catch (e: Exception) {
                    ""
                }
                val isDji = rawName.contains("DJI", ignoreCase = true) ||
                    rawName.contains("Mic Mini", ignoreCase = true) ||
                    rawName.contains("Wireless Mic", ignoreCase = true)
                val friendly = when {
                    isDji -> "DJI Mic Mini 接收器"
                    kind == "builtin" -> "手机内置麦克风"
                    kind == "usb" -> rawName.ifBlank { "USB 音频设备" }
                    kind == "bluetooth" -> rawName.ifBlank { "蓝牙麦克风" }
                    kind == "wired" -> "有线耳机麦克风"
                    else -> rawName.ifBlank { "外部麦克风" }
                }
                val key = "$kind-$friendly"
                if (!seen.add(key)) continue
                result.add(
                    top.tpgofighting.voicebrain.model.AudioInputDevice(
                        id = d.id,
                        name = friendly,
                        kind = kind,
                        isDji = isDji
                    )
                )
            }
            // 排序：DJI/USB 优先，其次蓝牙/有线，最后内置
            val order = mapOf("usb" to 0, "bluetooth" to 1, "wired" to 2, "builtin" to 3, "other" to 4)
            return result.sortedBy { order[it.kind] ?: 4 }
        }

        fun findDeviceInfo(context: Context, deviceId: Int): android.media.AudioDeviceInfo? {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                ?: return null
            return am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == deviceId }
        }
    }

    /** 用户选定的输入设备 id；null = 自动（优先 DJI/USB 接收器，否则系统默认） */
    @Volatile
    var preferredDeviceId: Int? = null

    /** 本次录音实际使用的设备名（UI 展示） */
    @Volatile
    var activeDeviceLabel: String = "手机内置麦克风"
        private set

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isRecording: Boolean = false
        private set

    // 全量保留本次录音的 Short 采样点，用于本地导出无损 WAV 文件（Zero Audio Loss）
    private val recordedSamples = mutableListOf<Short>()
    var totalBytesSent: Long = 0
        private set
    var totalChunksSent: Long = 0
        private set

    var lastExportedWavFile: File? = null
        private set

    fun hasRecordedData(): Boolean {
        synchronized(recordedSamples) {
            return recordedSamples.isNotEmpty()
        }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording) return true

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Cannot start recording: RECORD_AUDIO permission not granted")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = if (minBufferSize > 0) maxOf(minBufferSize, CHUNK_SIZE_BYTES * 4) else CHUNK_SIZE_BYTES * 4

        // 优先使用标准系统默认源与麦克风源，若不初始化则逐级降级尝试
        val audioSources = listOf(
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        )

        var initializedRecord: AudioRecord? = null
        for (source in audioSources) {
            try {
                val record = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    initializedRecord = record
                    Log.i(TAG, "AudioRecord initialized successfully with source: $source, bufferSize: $bufferSize")
                    break
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize AudioRecord with source $source: ${e.message}")
            }
        }

        if (initializedRecord == null) {
            Log.e(TAG, "AudioRecord initialization failed on all audio sources")
            return false
        }

        audioRecord = initializedRecord

        // 输入设备路由：用户指定优先；未指定时自动优选 DJI/USB 接收器（DJI Mic Mini RX 走 USB 音频类）
        try {
            val inputs = listInputDevices(context)
            val chosen = when {
                preferredDeviceId != null -> inputs.firstOrNull { it.id == preferredDeviceId }
                else -> inputs.firstOrNull { it.isDji } ?: inputs.firstOrNull { it.kind == "usb" }
            }
            if (chosen != null) {
                val info = findDeviceInfo(context, chosen.id)
                if (info != null) {
                    val ok = initializedRecord.setPreferredDevice(info)
                    if (ok) {
                        activeDeviceLabel = chosen.name
                        Log.i(TAG, "Preferred input device applied: ${chosen.name} (id=${chosen.id}, kind=${chosen.kind})")
                        if (chosen.isDji) onDeviceEvent?.invoke("已接入 DJI Mic Mini 接收器，录音将使用领夹麦音源")
                    } else {
                        Log.w(TAG, "setPreferredDevice returned false for ${chosen.name}")
                    }
                }
            } else {
                activeDeviceLabel = "手机内置麦克风"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Apply preferred input device failed: ${e.message}")
        }

        registerDeviceWatch()

        try {
            audioRecord?.startRecording()
            isRecording = true
            synchronized(recordedSamples) {
                recordedSamples.clear()
            }
            totalBytesSent = 0
            totalChunksSent = 0

            recordingJob = coroutineScope.launch {
                val readBuffer = ShortArray(1600) // 100ms = 1600 shorts (3200 bytes)
                var logCount = 0

                while (isActive && isRecording) {
                    val record = audioRecord ?: break
                    val readResult = record.read(readBuffer, 0, readBuffer.size)
                    if (readResult > 0) {
                        // 1. 计算音量幅值 (RMS) 并线程安全缓存采样点
                        var sum = 0.0
                        synchronized(recordedSamples) {
                            for (i in 0 until readResult) {
                                val sample = readBuffer[i]
                                sum += sample * sample
                                recordedSamples.add(sample)
                            }
                        }
                        val rms = sqrt(sum / readResult)
                        val normalizedVol = (rms / 32768.0f * 5.0f).coerceIn(0.0, 1.0).toFloat()

                        // 异步派发音量回调，绝不阻塞硬件音频读取循环
                        coroutineScope.launch(Dispatchers.Main) {
                            onVolume(normalizedVol)
                        }

                        // 2. 直接构造 16-bit Little-Endian 字节数组，杜绝 NIO 视图偏移异常
                        val chunkBytes = ByteArray(readResult * 2)
                        for (i in 0 until readResult) {
                            val sample = readBuffer[i].toInt()
                            chunkBytes[i * 2] = (sample and 0xFF).toByte()
                            chunkBytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                        }

                        totalBytesSent += chunkBytes.size
                        totalChunksSent++
                        onChunk(chunkBytes)

                        if (++logCount % 50 == 0) {
                            Log.d(TAG, "ASR audio stream active: chunks=$totalChunksSent, bytes=$totalBytesSent, rms=${String.format("%.2f", rms)}")
                        }
                    } else {
                        // 兜底保护：当底层返回 0 或负数错误码时休眠 20ms，防止 CPU 100% 紧凑死循环
                        if (readResult < 0) {
                            Log.w(TAG, "AudioRecord read returned error code: $readResult")
                        }
                        delay(20)
                    }
                }
            }

            Log.i(TAG, "Audio recording started successfully at 16000Hz mono")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
            stop()
            return false
        }
    }

    private var deviceCallback: android.media.AudioDeviceCallback? = null

    /** 录音期间监听外接麦克风插拔：DJI 接收器插入/拔出时给出提示（系统会自动回退内置麦） */
    private fun registerDeviceWatch() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            if (deviceCallback != null) return
            val cb = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    val inputs = listInputDevices(context)
                    val dji = inputs.firstOrNull { it.isDji }
                    if (dji != null) {
                        onDeviceEvent?.invoke("检测到 DJI Mic Mini 接收器，下次录音将优先使用；当前录音如需切换请重开")
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    removedDevices ?: return
                    val removedActive = removedDevices.any { it.id == preferredDeviceId }
                    if (removedActive || activeDeviceLabel.contains("DJI") || activeDeviceLabel.contains("USB")) {
                        onDeviceEvent?.invoke("外接麦克风已断开，系统已自动回退到手机内置麦克风")
                        activeDeviceLabel = "手机内置麦克风"
                    }
                }
            }
            am.registerAudioDeviceCallback(cb, null)
            deviceCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "registerDeviceWatch failed: ${e.message}")
        }
    }

    private fun unregisterDeviceWatch() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            deviceCallback?.let { am.unregisterAudioDeviceCallback(it) }
            deviceCallback = null
        } catch (e: Exception) {
            Log.w(TAG, "unregisterDeviceWatch failed: ${e.message}")
        }
    }

    fun stop() {
        isRecording = false
        unregisterDeviceWatch()
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        } finally {
            audioRecord = null
        }
    }

    /**
     * 导出本地全量录音为标准 WAV 格式文件，并同步保存至系统公用下载目录
     */
    fun exportWavFile(customName: String? = null): File? {
        val samplesCopy: List<Short>
        synchronized(recordedSamples) {
            if (recordedSamples.isEmpty()) return lastExportedWavFile
            samplesCopy = recordedSamples.toList()
        }

        val filename = customName ?: "VoiceBrain_${System.currentTimeMillis()}.wav"
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val outputFile = File(outputDir, filename)

        try {
            FileOutputStream(outputFile).use { fos ->
                writeWavHeader(fos, samplesCopy.size * 2, SAMPLE_RATE, 1, 16)
                val byteBuffer = ByteBuffer.allocate(samplesCopy.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (s in samplesCopy) {
                    byteBuffer.putShort(s)
                }
                fos.write(byteBuffer.array())
            }
            Log.i(TAG, "WAV exported to ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            
            // 同步写入系统公用 Downloads 目录，让用户在系统文件管理中能直接看到
            saveToPublicDownloads(outputFile, filename)

            lastExportedWavFile = outputFile
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export WAV file: ${e.message}", e)
            return null
        }
    }

    private fun saveToPublicDownloads(sourceFile: File, filename: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val mimeTypes = listOf("audio/wav", "audio/x-wav", "audio/*")
                var uri: android.net.Uri? = null
                val resolver = context.contentResolver

                for (mime in mimeTypes) {
                    try {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                            put(MediaStore.MediaColumns.MIME_TYPE, mime)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        if (uri != null) break
                    } catch (e: Exception) {
                        Log.w(TAG, "MediaStore insert failed for MIME $mime: ${e.message}")
                    }
                }

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        sourceFile.inputStream().use { input -> input.copyTo(os) }
                    }
                    Log.i(TAG, "WAV successfully synced to MediaStore.Downloads: $uri")
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDir.exists() || publicDir.mkdirs()) {
                    val publicFile = File(publicDir, filename)
                    sourceFile.copyTo(publicFile, overwrite = true)
                    Log.i(TAG, "WAV copied to public downloads: ${publicFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not save copy to public Downloads: ${e.message}")
        }
    }

    private fun writeWavHeader(out: FileOutputStream, pcmDataLength: Int, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte() // 'data' chunk
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataLength and 0xff).toByte()
        header[41] = (pcmDataLength shr 8 and 0xff).toByte()
        header[42] = (pcmDataLength shr 16 and 0xff).toByte()
        header[43] = (pcmDataLength shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
