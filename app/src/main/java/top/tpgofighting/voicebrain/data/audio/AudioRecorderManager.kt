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
    private val onVolume: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecorderManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_SIZE_BYTES = 3200 // 100ms at 16kHz 16-bit mono
    }

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

    fun stop() {
        isRecording = false
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
