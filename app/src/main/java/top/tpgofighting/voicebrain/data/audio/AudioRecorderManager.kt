package top.tpgofighting.voicebrain.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
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

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording) return true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, CHUNK_SIZE_BYTES * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return false
            }

            audioRecord?.startRecording()
            isRecording = true
            recordedSamples.clear()
            totalBytesSent = 0
            totalChunksSent = 0

            recordingJob = coroutineScope.launch {
                val readBuffer = ShortArray(1600) // 100ms = 1600 shorts
                val byteBuffer = ByteArray(CHUNK_SIZE_BYTES)

                while (isActive && isRecording) {
                    val readResult = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (readResult > 0) {
                        // 1. 计算音量幅值 (RMS)
                        var sum = 0.0
                        for (i in 0 until readResult) {
                            val sample = readBuffer[i]
                            sum += sample * sample
                            synchronized(recordedSamples) {
                                recordedSamples.add(sample)
                            }
                        }
                        val rms = sqrt(sum / readResult)
                        val normalizedVol = (rms / 32768.0f * 5.0f).coerceIn(0.0, 1.0).toFloat()
                        withContext(Dispatchers.Main) {
                            onVolume(normalizedVol)
                        }

                        // 2. 转为 16-bit Little-Endian 字节并切片推送
                        ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(readBuffer, 0, readResult)
                        val chunkBytes = if (readResult * 2 == CHUNK_SIZE_BYTES) {
                            byteBuffer
                        } else {
                            byteBuffer.copyOf(readResult * 2)
                        }

                        totalBytesSent += chunkBytes.size
                        totalChunksSent++
                        onChunk(chunkBytes)
                    }
                }
            }

            Log.i(TAG, "Audio recording started successfully at 16000Hz")
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
     * 导出本地全量录音为标准 WAV 格式文件
     */
    fun exportWavFile(customName: String? = null): File? {
        val samplesCopy: List<Short>
        synchronized(recordedSamples) {
            if (recordedSamples.isEmpty()) return null
            samplesCopy = recordedSamples.toList()
        }

        val filename = customName ?: "voice_brain_${System.currentTimeMillis()}.wav"
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
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
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export WAV file: ${e.message}", e)
            return null
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
        header[32] = (channels * (bitsPerSample / 8)).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
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
