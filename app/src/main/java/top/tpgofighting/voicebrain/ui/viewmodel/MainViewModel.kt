package top.tpgofighting.voicebrain.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import top.tpgofighting.voicebrain.data.api.VoiceBrainApiClient
import top.tpgofighting.voicebrain.data.audio.AudioRecorderManager
import top.tpgofighting.voicebrain.data.websocket.AsrEvent
import top.tpgofighting.voicebrain.data.websocket.AsrWebSocketManager
import top.tpgofighting.voicebrain.model.*
import top.tpgofighting.voicebrain.service.KeepAliveHelper
import top.tpgofighting.voicebrain.service.RecordingBus
import top.tpgofighting.voicebrain.service.RecordingService
import java.io.File

data class MainUiState(
    // 录音与实时流状态 (Tier 1 & Tier 2)
    val isRecording: Boolean = false,
    val recordDurationSeconds: Int = 0,
    val currentVolume: Float = 0f,
    val volumeHistory: List<Float> = emptyList(),
    val wsStatusText: String = "未连接",
    val wsStatusConnected: Boolean = false,
    val interimText: String = "",
    val utterances: List<Utterance> = emptyList(),
    val totalFrames: Long = 0,
    val totalBytes: Long = 0,
    val exportedWavFile: File? = null,
    val sentenceSilenceMs: Int = 1200,

    // 输入设备（DJI Mic Mini 接收器等外接麦克风）
    val audioDevices: List<AudioInputDevice> = emptyList(),
    val selectedDeviceId: Int? = null, // null = 自动优选
    val activeDeviceLabel: String = "手机内置麦克风",

    // 逐字稿展示：默认原文，清洗稿单独切换（与 Web Fix 2 对齐）
    val showCleansed: Boolean = false,

    // 历史会话归档
    val sessions: List<SessionSummary> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val viewingSessionTitle: String? = null,

    // 纪要决策与人设 (Tier 3)
    val currentPersona: Persona = Persona.DEFAULT,
    val minutesTemplate: String = "general",
    val minutes: Minutes? = null,
    val isGeneratingMinutes: Boolean = false,
    val isMinutesPolishView: Boolean = false,
    val polishedDraft: String = "",
    val isPolishing: Boolean = false,

    // 说话人分离
    val isDiarizing: Boolean = false,
    val diarizeStatus: String = "",

    // 长期第二大脑记忆库
    val memories: List<MemoryItem> = emptyList(),
    val selectedMemoryCategory: String = "全部",
    val memorySearchQuery: String = "",
    val brainChatHistory: List<BrainChatMessage> = emptyList(),
    val isBrainThinking: Boolean = false,
    val memorySubTab: Int = 0, // 0: 记忆库卡片, 1: 问问第二大脑, 2: 全文检索

    // 全文检索
    val searchQuery: String = "",
    val searchHits: List<SearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val searchDone: Boolean = false,

    // Obsidian 知识库与 Hermes 联动
    val obsidianStatus: ObsidianStatus = ObsidianStatus(),
    val obsidianNotes: List<ObsidianNote> = emptyList(),
    val obsidianEntities: List<LmmWikiEntity> = emptyList(),
    val obsidianChatHistory: List<BrainChatMessage> = emptyList(),
    val isObsidianThinking: Boolean = false,
    val autoWriteBackEnabled: Boolean = true,

    // 麦克风权限（按需申请：首次点开始先弹用途说明，被拒后给出恢复入口）
    val micRationaleVisible: Boolean = false,
    val micPermissionDenied: Boolean = false,

    // 后台保活状态
    val batteryExempt: Boolean = true,
    // 电池保活提示只出现一次：首次录音后才提示，处理过（去设置/知道了）不再打扰
    val batteryHintHandled: Boolean = false,
    val hasRecordedOnce: Boolean = false,

    // 全局提示信息 (Toast / Banner)
    val toastMessage: String? = null
)

sealed class MainUiEvent {
    object RequestAudioPermission : MainUiEvent()
    data class ShareWavFile(val file: File, val message: String) : MainUiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
        private const val VOLUME_HISTORY_MAX = 48
        private const val PREF_BATTERY_HINT_HANDLED = "battery_hint_handled"
        private const val PREF_HAS_RECORDED_ONCE = "has_recorded_once"
    }

    private val apiClient = VoiceBrainApiClient()

    private val prefs by lazy {
        getApplication<Application>().getSharedPreferences("voicebrain_prefs", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<MainUiEvent> = _uiEvents.asSharedFlow()

    private var audioRecorder: AudioRecorderManager? = null
    private var webSocketManager: AsrWebSocketManager? = null
    private var timerJob: Job? = null

    init {
        initWelcomeMessages()
        fetchRemoteData()
        refreshAudioDevices()
        refreshBatteryState()
        _uiState.update {
            it.copy(
                batteryHintHandled = prefs.getBoolean(PREF_BATTERY_HINT_HANDLED, false),
                hasRecordedOnce = prefs.getBoolean(PREF_HAS_RECORDED_ONCE, false)
            )
        }
    }

    private fun initWelcomeMessages() {
        _uiState.update { state ->
            state.copy(
                brainChatHistory = listOf(
                    BrainChatMessage(
                        id = "bc-0",
                        isUser = false,
                        content = "你好！我是你的个人第二大脑助手。你的长期记忆和历史录音都可以问我，问答会逐字引用原文出处；还没有录音的话先去录一段，决策、承诺和偏好会自动沉淀进来。",
                        timestamp = "刚刚"
                    )
                ),
                obsidianChatHistory = listOf(
                    BrainChatMessage(
                        id = "oc-0",
                        isUser = false,
                        content = "这里查的是你的 Obsidian 仓库笔记正文：概念、人物、项目都可以直接问，回答会标注来源笔记。录音里说过的内容不在这里查，去「记忆大脑」页问。开启反向追记后，回答可以追加写回指定笔记。",
                        timestamp = "刚刚"
                    )
                )
            )
        }
    }

    private fun fetchRemoteData() {
        viewModelScope.launch {
            val memRes = apiClient.fetchMemories()
            if (memRes.isSuccess) {
                _uiState.update { it.copy(memories = memRes.getOrNull() ?: emptyList()) }
            }

            val obsRes = apiClient.fetchObsidianStatus()
            if (obsRes.isSuccess) {
                _uiState.update { it.copy(obsidianStatus = obsRes.getOrNull() ?: ObsidianStatus()) }
            }

            refreshSessions()
        }
    }

    /** 重新拉取 Obsidian 状态；失败时保持 loaded=false，界面显示状态未知。 */
    fun refreshObsidianStatus() {
        viewModelScope.launch {
            val res = apiClient.fetchObsidianStatus()
            _uiState.update { it.copy(obsidianStatus = res.getOrNull() ?: ObsidianStatus()) }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true) }
            val res = apiClient.fetchSessions()
            _uiState.update {
                it.copy(
                    sessions = res.getOrNull() ?: it.sessions,
                    isLoadingSessions = false
                )
            }
        }
    }

    fun refreshAudioDevices() {
        val devices = AudioRecorderManager.listInputDevices(getApplication())
        _uiState.update { state ->
            val stillThere = state.selectedDeviceId?.let { id -> devices.any { d -> d.id == id } } ?: true
            state.copy(
                audioDevices = devices,
                selectedDeviceId = if (stillThere) state.selectedDeviceId else null
            )
        }
    }

    fun selectAudioDevice(deviceId: Int?) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
        val label = if (deviceId == null) {
            "自动优选（DJI/USB 接收器优先）"
        } else {
            _uiState.value.audioDevices.firstOrNull { it.id == deviceId }?.name ?: "指定设备"
        }
        audioRecorder?.preferredDeviceId = deviceId
        showToast("录音音源：$label" + if (_uiState.value.isRecording) "（下段录音生效）" else "")
    }

    /** 电池保活提示处理完毕（点了去设置或「知道了」）：持久化，之后不再展示 */
    fun onBatteryHintHandled() {
        _uiState.update { it.copy(batteryHintHandled = true) }
        prefs.edit().putBoolean(PREF_BATTERY_HINT_HANDLED, true).apply()
    }

    fun refreshBatteryState() {
        _uiState.update {
            it.copy(batteryExempt = KeepAliveHelper.isBatteryOptimizationIgnored(getApplication()))
        }
    }

    fun requestBatteryExemption() {
        KeepAliveHelper.requestBatteryExemption(getApplication())
    }

    fun openAutoStartSettings() {
        KeepAliveHelper.openAutoStartSettings(getApplication())
    }

    /** 用户在用途说明半屏上确认后，才真正调起系统麦克风权限框 */
    fun confirmMicPermissionRequest() {
        _uiState.update { it.copy(micRationaleVisible = false) }
        viewModelScope.launch {
            _uiEvents.emit(MainUiEvent.RequestAudioPermission)
        }
    }

    fun dismissMicRationale() {
        _uiState.update { it.copy(micRationaleVisible = false) }
    }

    /** 系统权限框结果回告：被拒后录音页顶部显示「去设置开启」恢复卡 */
    fun onMicPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(micPermissionDenied = !granted) }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // 不直接调系统权限框：先在录音页弹用途说明，用户确认后再申请
            _uiState.update { it.copy(micRationaleVisible = true) }
            return
        }

        refreshAudioDevices()

        _uiState.update {
            it.copy(
                isRecording = true,
                recordDurationSeconds = 0,
                wsStatusText = "正在连接...",
                wsStatusConnected = false,
                interimText = "正在聆听，开始说话吧…",
                viewingSessionTitle = null,
                volumeHistory = emptyList(),
                micPermissionDenied = false,
                hasRecordedOnce = true
            )
        }
        prefs.edit().putBoolean(PREF_HAS_RECORDED_ONCE, true).apply()

        // 启动秒表
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRecording) {
                delay(1000)
                _uiState.update { it.copy(recordDurationSeconds = it.recordDurationSeconds + 1) }
            }
        }

        // 启动 WebSocket
        webSocketManager = AsrWebSocketManager(
            sentenceSilenceMs = _uiState.value.sentenceSilenceMs
        ) { event ->
            handleAsrEvent(event)
        }
        webSocketManager?.start()

        // 启动录音机（带设备路由与插拔监听）
        audioRecorder = AudioRecorderManager(
            context = getApplication(),
            onChunk = { chunk ->
                webSocketManager?.sendChunk(chunk)
                _uiState.update {
                    it.copy(
                        totalFrames = audioRecorder?.totalChunksSent ?: 0,
                        totalBytes = audioRecorder?.totalBytesSent ?: 0
                    )
                }
            },
            onVolume = { vol ->
                _uiState.update {
                    val history = (it.volumeHistory + vol).takeLast(VOLUME_HISTORY_MAX)
                    it.copy(currentVolume = vol, volumeHistory = history)
                }
            },
            onDeviceEvent = { msg -> showToast(msg) }
        ).apply {
            preferredDeviceId = _uiState.value.selectedDeviceId
        }

        val started = audioRecorder?.start() ?: false
        if (!started) {
            stopRecording()
            showToast("麦克风启动失败，请检查录音权限！")
            return
        }

        val deviceLabel = audioRecorder?.activeDeviceLabel ?: "手机内置麦克风"
        _uiState.update { it.copy(activeDeviceLabel = deviceLabel) }

        // 前台服务保活 + 常驻通知（锁屏/切后台继续录）
        try {
            val intent = RecordingService.startIntent(getApplication(), deviceLabel)
            ContextCompat.startForegroundService(getApplication(), intent)
        } catch (e: Exception) {
            Log.w(TAG, "start RecordingService failed: ${e.message}")
        }
        RecordingBus.onStopRequested = { stopRecording() }
    }

    fun stopRecording() {
        _uiState.update {
            it.copy(
                isRecording = false,
                currentVolume = 0f,
                wsStatusText = "录音结束",
                interimText = ""
            )
        }
        timerJob?.cancel()
        timerJob = null

        audioRecorder?.stop()
        webSocketManager?.stop()

        // 停止前台服务
        try {
            getApplication<Application>().stopService(
                Intent(getApplication(), RecordingService::class.java)
            )
        } catch (e: Exception) {
            Log.w(TAG, "stop RecordingService failed: ${e.message}")
        }
        RecordingBus.onStopRequested = null

        // 缓存本次无损 WAV 文件
        val wav = audioRecorder?.exportWavFile()
        _uiState.update { it.copy(exportedWavFile = wav) }

        if (_uiState.value.utterances.isNotEmpty()) {
            showToast("录音已完成并固化，可立即生成纪要！")
            refreshSessions()
        }
    }

    private fun handleAsrEvent(event: AsrEvent) {
        viewModelScope.launch(Dispatchers.Main) {
            when (event) {
                is AsrEvent.Started -> {
                    _uiState.update {
                        it.copy(
                            wsStatusText = "双工就绪",
                            wsStatusConnected = true,
                            interimText = "正在聆听..."
                        )
                    }
                }
                is AsrEvent.Interim -> {
                    _uiState.update { it.copy(interimText = event.text) }
                }
                is AsrEvent.Committed -> {
                    if (event.text.isBlank()) return@launch
                    val newUtterance = Utterance(
                        id = _uiState.value.utterances.size + 1,
                        sentenceId = event.sentenceId,
                        speaker = "发言人",
                        time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        text = event.rawText.ifBlank { event.text },
                        rawText = event.rawText.ifBlank { event.text },
                        isCleansed = false
                    )
                    _uiState.update {
                        it.copy(
                            utterances = it.utterances + newUtterance,
                            interimText = ""
                        )
                    }
                }
                is AsrEvent.Cleansed -> {
                    // 清洗稿只进 cleansedText，展示默认仍为原文（与 Web Fix 2 一致）
                    _uiState.update { state ->
                        val updated = state.utterances.map { u ->
                            if (u.sentenceId == event.sentenceId) {
                                u.copy(cleansedText = event.text, isCleansed = true, latencyMs = event.latencyMs)
                            } else u
                        }
                        state.copy(utterances = updated)
                    }
                }
                is AsrEvent.Reconnecting -> {
                    _uiState.update {
                        it.copy(
                            wsStatusText = "平滑续连中",
                            wsStatusConnected = false,
                            interimText = event.message
                        )
                    }
                }
                is AsrEvent.Error -> {
                    _uiState.update { it.copy(wsStatusText = "识别异常") }
                    Log.w(TAG, "ASR Error: ${event.message}")
                }
                is AsrEvent.Finished -> {
                    _uiState.update { it.copy(wsStatusText = "已完成", wsStatusConnected = false) }
                }
            }
        }
    }

    fun exportCurrentWav(): File? {
        val file = audioRecorder?.exportWavFile() ?: _uiState.value.exportedWavFile
        if (file != null && file.exists() && file.length() > 44) {
            _uiState.update { it.copy(exportedWavFile = file) }
            val msg = "无损音频已保存至下载目录: ${file.name}"
            showToast(msg)
            viewModelScope.launch {
                _uiEvents.emit(MainUiEvent.ShareWavFile(file, msg))
            }
        } else {
            showToast("当前暂无录音音频数据，请先录制一段语音")
        }
        return file
    }

    fun setSentenceSilence(silenceMs: Int) {
        _uiState.update { it.copy(sentenceSilenceMs = silenceMs) }
        webSocketManager?.updateSentenceSilence(silenceMs)
        val label = when (silenceMs) {
            500 -> "紧凑 (500ms)"
            1200 -> "从容 (1200ms)"
            else -> "标准 (800ms)"
        }
        showToast("已设置断句判定: $label")
    }

    fun toggleShowCleansed(show: Boolean) {
        _uiState.update { it.copy(showCleansed = show) }
    }

    /** 从归档打开一个历史会话到工作台（只读回看，可继续做分离/纪要） */
    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            val res = apiClient.fetchSession(sessionId)
            if (res.isSuccess) {
                val utterances = res.getOrNull() ?: emptyList()
                val summary = _uiState.value.sessions.firstOrNull { it.id == sessionId }
                _uiState.update {
                    it.copy(
                        utterances = utterances,
                        viewingSessionTitle = summary?.title ?: "历史会话",
                        exportedWavFile = null,
                        wsStatusText = "归档回看"
                    )
                }
                showToast("已载入历史会话：${summary?.title ?: sessionId}")
            } else {
                showToast("载入历史会话失败，请检查网络")
            }
        }
    }

    /** 说话人分离：本地 WAV → 上传 → 离线精转写轮询 → 结果回填逐字稿 */
    fun diarizeCurrentRecording() {
        if (_uiState.value.isDiarizing) return
        val wav = _uiState.value.exportedWavFile ?: audioRecorder?.exportWavFile()
        if (wav == null || !wav.exists() || wav.length() <= 44) {
            showToast("暂无可用录音文件，请先完成一段录音")
            return
        }
        _uiState.update { it.copy(exportedWavFile = wav, isDiarizing = true, diarizeStatus = "正在上传音频…") }
        viewModelScope.launch {
            try {
                val audioId = apiClient.diarizeUpload(wav).getOrElse {
                    throw IllegalStateException("上传失败：${it.message}")
                }
                _uiState.update { it.copy(diarizeStatus = "已提交精转写，识别说话人中…") }
                val taskId = apiClient.diarizeStart(audioId).getOrElse {
                    throw IllegalStateException("启动失败：${it.message}")
                }
                var sentences: List<top.tpgofighting.voicebrain.data.api.DiarizedSentence> = emptyList()
                var finished = false
                repeat(48) { attempt ->
                    delay(2500)
                    val (status, list) = apiClient.diarizeStatus(taskId).getOrElse {
                        throw IllegalStateException("查询进度失败：${it.message}")
                    }
                    if (status == "SUCCEEDED") {
                        sentences = list
                        finished = true
                        return@repeat
                    }
                    _uiState.update { it.copy(diarizeStatus = "精转写进行中…（${attempt + 1}）") }
                }
                if (!finished || sentences.isEmpty()) {
                    showToast("说话人分离未完成或无结果，请稍后重试")
                    return@launch
                }
                val speakers = sentences.map { s -> s.speakerId }.distinct().sorted()
                val diarized = sentences.mapIndexed { idx, s ->
                    val beginSec = s.beginMs / 1000
                    val timeLabel = String.format("%02d:%02d", beginSec / 60, beginSec % 60)
                    Utterance(
                        id = idx + 1,
                        sentenceId = "diar-${s.beginMs}-$idx",
                        speaker = "发言人 ${s.speakerId + 1}",
                        time = timeLabel,
                        text = s.text,
                        rawText = s.text,
                        beginMs = s.beginMs
                    )
                }
                _uiState.update {
                    it.copy(
                        utterances = diarized,
                        viewingSessionTitle = "说话人分离结果"
                    )
                }
                showToast("分离完成：共识别出 ${speakers.size} 位说话人、${sentences.size} 句")
            } catch (e: Exception) {
                showToast("说话人分离失败：${e.message}")
            } finally {
                _uiState.update { it.copy(isDiarizing = false, diarizeStatus = "") }
            }
        }
    }

    fun selectPersona(persona: Persona) {
        _uiState.update { it.copy(currentPersona = persona) }
        showToast("已切换人设为: ${persona.name} (${persona.badge})")
    }

    fun setMinutesTemplate(templateId: String) {
        _uiState.update { it.copy(minutesTemplate = templateId) }
        val label = MINUTES_TEMPLATES.firstOrNull { it.id == templateId }?.label ?: "通用"
        showToast("纪要模板：$label")
    }

    fun generateMinutes() {
        val utterances = _uiState.value.utterances
        if (utterances.isEmpty()) {
            showToast("请先录音一段发言再生成纪要")
            return
        }

        val persona = _uiState.value.currentPersona
        val template = _uiState.value.minutesTemplate
        _uiState.update { it.copy(isGeneratingMinutes = true) }

        viewModelScope.launch {
            val result = apiClient.generateMinutes(utterances, persona, template = template)
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        minutes = result.getOrNull(),
                        isGeneratingMinutes = false,
                        isMinutesPolishView = false
                    )
                } else {
                    state.copy(isGeneratingMinutes = false)
                }
            }

            if (result.isSuccess) {
                showToast("成功生成【${persona.name}】结构化纪要！已自动沉淀记忆。")
                fetchRemoteData()
            } else {
                showToast("生成纪要失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun toggleMinutesViewMode(isPolishView: Boolean) {
        _uiState.update { it.copy(isMinutesPolishView = isPolishView) }
        if (isPolishView && _uiState.value.polishedDraft.isBlank()) {
            polishCurrentMinutes()
        }
    }

    fun polishCurrentMinutes() {
        val text = _uiState.value.utterances.joinToString("\n") { "${it.speaker}: ${it.rawText.ifBlank { it.text }}" }
        if (text.isBlank()) return

        _uiState.update { it.copy(isPolishing = true) }
        viewModelScope.launch {
            val result = apiClient.polishDraft(text)
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(
                        polishedDraft = result.getOrNull() ?: "",
                        isPolishing = false
                    )
                } else {
                    state.copy(isPolishing = false)
                }
            }
            if (result.isSuccess) {
                showToast("排版润色正式稿整理完毕")
            }
        }
    }

    fun setMemoryCategory(cat: String) {
        _uiState.update { it.copy(selectedMemoryCategory = cat) }
    }

    fun setMemorySearchQuery(query: String) {
        _uiState.update { it.copy(memorySearchQuery = query) }
    }

    fun setMemorySubTab(tab: Int) {
        _uiState.update { it.copy(memorySubTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun runSearch() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isBlank()) return
        _uiState.update { it.copy(isSearching = true, searchDone = false) }
        viewModelScope.launch {
            val res = apiClient.search(q)
            _uiState.update {
                it.copy(
                    searchHits = res.getOrNull() ?: emptyList(),
                    isSearching = false,
                    searchDone = true
                )
            }
            if (res.isFailure) showToast("检索失败，请检查网络")
        }
    }

    fun askSecondBrain(question: String) {
        val q = question.trim()
        if (q.isBlank()) return

        val userMsg = BrainChatMessage(
            id = "user-${System.currentTimeMillis()}",
            isUser = true,
            content = q,
            timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        )
        val assistantId = "brain-${System.currentTimeMillis()}"
        val placeholder = BrainChatMessage(
            id = assistantId,
            isUser = false,
            content = "",
            timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                brainChatHistory = it.brainChatHistory + userMsg + placeholder,
                isBrainThinking = true
            )
        }

        viewModelScope.launch {
            // 优先流式：逐字追加；final 事件落真实来源
            val streamResult = apiClient.querySecondBrainStream(q) { delta ->
                _uiState.update { state ->
                    val updated = state.brainChatHistory.map { msg ->
                        if (msg.id == assistantId) msg.copy(content = msg.content + delta) else msg
                    }
                    state.copy(brainChatHistory = updated)
                }
            }
            val answer = if (streamResult.isSuccess) {
                streamResult.getOrNull()
            } else {
                // 流式在首字前失败时回退非流式
                apiClient.querySecondBrain(q).getOrNull()
            }
            _uiState.update { state ->
                val updated = state.brainChatHistory.map { msg ->
                    if (msg.id == assistantId) {
                        if (answer != null) {
                            msg.copy(
                                content = answer.answer.ifBlank { msg.content },
                                sources = answer.sources,
                                citations = answer.sources.map { s ->
                                    s.sessionTitle.ifBlank { "历史录音" }
                                }.distinct(),
                                isStreaming = false
                            )
                        } else {
                            msg.copy(
                                content = if (msg.content.isBlank()) "抱歉，检索记忆库遇到网络问题，请稍后重试。" else msg.content,
                                isStreaming = false
                            )
                        }
                    } else msg
                }
                state.copy(brainChatHistory = updated, isBrainThinking = false)
            }
        }
    }

    fun toggleAutoWriteBack() {
        _uiState.update {
            val next = !it.autoWriteBackEnabled
            it.copy(autoWriteBackEnabled = next)
        }
        showToast(if (_uiState.value.autoWriteBackEnabled) "已开启 Hermes 自动反向追记" else "已关闭自动追记")
    }

    fun askObsidian(question: String) {
        val q = question.trim()
        if (q.isBlank()) return

        val userMsg = BrainChatMessage(
            id = "obs-user-${System.currentTimeMillis()}",
            isUser = true,
            content = q,
            timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        )

        _uiState.update {
            it.copy(
                obsidianChatHistory = it.obsidianChatHistory + userMsg,
                isObsidianThinking = true
            )
        }

        viewModelScope.launch {
            val targetNote = _uiState.value.obsidianNotes.firstOrNull()?.path
            val result = apiClient.askObsidianVault(
                query = q,
                targetNotePath = targetNote,
                autoWriteBack = _uiState.value.autoWriteBackEnabled
            )

            val (ans, sources) = result.getOrNull() ?: Pair("连接知识库异常，请确保网络通畅", emptyList())
            val assistantMsg = BrainChatMessage(
                id = "obs-hermes-${System.currentTimeMillis()}",
                isUser = false,
                content = ans,
                timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                citations = sources
            )

            _uiState.update {
                it.copy(
                    obsidianChatHistory = it.obsidianChatHistory + assistantMsg,
                    isObsidianThinking = false
                )
            }

            if (_uiState.value.autoWriteBackEnabled && result.isSuccess) {
                showToast("Hermes 思考已自动追加写回 Obsidian 笔记！")
            }
        }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
