package top.tpgofighting.voicebrain.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import top.tpgofighting.voicebrain.data.api.VoiceBrainApiClient
import top.tpgofighting.voicebrain.data.audio.AudioRecorderManager
import top.tpgofighting.voicebrain.data.websocket.AsrEvent
import top.tpgofighting.voicebrain.data.websocket.AsrWebSocketManager
import top.tpgofighting.voicebrain.model.*
import java.io.File

data class MainUiState(
    // 录音与实时流状态 (Tier 1 & Tier 2)
    val isRecording: Boolean = false,
    val recordDurationSeconds: Int = 0,
    val currentVolume: Float = 0f,
    val wsStatusText: String = "未连接",
    val wsStatusConnected: Boolean = false,
    val interimText: String = "",
    val utterances: List<Utterance> = emptyList(),
    val totalFrames: Long = 0,
    val totalBytes: Long = 0,
    val exportedWavFile: File? = null,

    // 纪要决策与人设 (Tier 3)
    val currentPersona: Persona = Persona.DEFAULT,
    val minutes: Minutes? = null,
    val isGeneratingMinutes: Boolean = false,
    val isMinutesPolishView: Boolean = false,
    val polishedDraft: String = "",
    val isPolishing: Boolean = false,

    // 长期第二大脑记忆库
    val memories: List<MemoryItem> = emptyList(),
    val selectedMemoryCategory: String = "全部",
    val memorySearchQuery: String = "",
    val brainChatHistory: List<BrainChatMessage> = emptyList(),
    val isBrainThinking: Boolean = false,
    val memorySubTab: Int = 0, // 0: 记忆库卡片, 1: 问问第二大脑 Agent

    // Obsidian 知识库与 Hermes 联动
    val obsidianStatus: ObsidianStatus = ObsidianStatus(),
    val obsidianNotes: List<ObsidianNote> = emptyList(),
    val obsidianEntities: List<LmmWikiEntity> = emptyList(),
    val obsidianChatHistory: List<BrainChatMessage> = emptyList(),
    val isObsidianThinking: Boolean = false,
    val autoWriteBackEnabled: Boolean = true,

    // 全局提示信息 (Toast / Banner)
    val toastMessage: String? = null
)

sealed class MainUiEvent {
    data class ShareWavFile(val file: File, val message: String) : MainUiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val apiClient = VoiceBrainApiClient()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<MainUiEvent> = _uiEvents.asSharedFlow()

    private var audioRecorder: AudioRecorderManager? = null
    private var webSocketManager: AsrWebSocketManager? = null
    private var timerJob: Job? = null

    init {
        initDefaultData()
        fetchRemoteData()
    }

    private fun initDefaultData() {
        _uiState.update { state ->
            state.copy(
                memories = listOf(
                    MemoryItem(
                        id = "mem-1",
                        category = "preference",
                        content = "语音交互优先追求极速跟手感，句级快修控制在 500ms 内，单页式 100dvh 防全局滚动，排版必须符合 TP 品牌规范。",
                        tags = listOf("架构设计", "交互原则", "TP品牌"),
                        importance = 5
                    ),
                    MemoryItem(
                        id = "mem-2",
                        category = "project",
                        content = "TP Voice Brain 架构演进：第一层 fun-asr-realtime 感知层，第二层 qwen-flash 交互层，第三层 deepseek-flash 认知沉淀。",
                        tags = listOf("全双工语音", "分层架构", "DeepSeek"),
                        importance = 5
                    ),
                    MemoryItem(
                        id = "mem-3",
                        category = "decision",
                        content = "拆掉单一 Session 围墙，将录音材料无缝同步至 Obsidian Vault 并构建 LMMWiki 双链图谱与 Hermes 思考反向追记闭环。",
                        tags = listOf("PersonalAgent", "Obsidian", "Hermes"),
                        importance = 5
                    )
                ),
                obsidianEntities = listOf(
                    LmmWikiEntity("Personal Agent", "持续在线、事件驱动、深度接入个人工作流与第二大脑的常驻智能体", "概念"),
                    LmmWikiEntity("单一Session模式", "传统Agent以独立Session为单位的架构局限，阻碍多任务协同规模化", "架构瓶颈"),
                    LmmWikiEntity("Session Mesh", "将上下文与状态外置于总线的可编排临时会话调度架构", "蓝海方案"),
                    LmmWikiEntity("Hermes Agent", "具备自主规划与反向追记能力的本地知识库推理智能体", "执行引擎")
                ),
                obsidianNotes = listOf(
                    ObsidianNote("01-Raw/录音材料/2026-11/2026-11-02-拆掉Session围墙.md", "拆掉Session围墙：Personal Agent涌现与协作拐点", "2026-11-02", "灵感探险家", listOf("知识库", "语音纪要")),
                    ObsidianNote("01-Raw/录音材料/2026-11/2026-11-01-全双工架构研讨.md", "全双工语音架构与反向追记闭环研讨", "2026-11-01", "系统架构师", listOf("架构设计", "全双工"))
                ),
                brainChatHistory = listOf(
                    BrainChatMessage(
                        id = "bc-0",
                        isUser = false,
                        content = "你好！我是你的个人第二大脑助手。我已经载入了你的长期记忆库与历史会话，你可以随时问我任何过去讨论过的方案或决策。",
                        timestamp = "刚刚"
                    )
                ),
                obsidianChatHistory = listOf(
                    BrainChatMessage(
                        id = "oc-0",
                        isUser = false,
                        content = "欢迎来到 Obsidian 知识库问答。结合 Hermes Agent 引擎与本地 RAG，支持对全部笔记和录音材料进行多跳问答，并支持将思考结果反向追记回指定笔记。",
                        timestamp = "刚刚"
                    )
                )
            )
        }
    }

    private fun fetchRemoteData() {
        viewModelScope.launch {
            val memRes = apiClient.fetchMemories()
            if (memRes.isSuccess && !memRes.getOrNull().isNullOrEmpty()) {
                _uiState.update { it.copy(memories = memRes.getOrNull()!!) }
            }

            val obsRes = apiClient.fetchObsidianStatus()
            if (obsRes.isSuccess) {
                _uiState.update { it.copy(obsidianStatus = obsRes.getOrNull()!!) }
            }
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return

        _uiState.update {
            it.copy(
                isRecording = true,
                recordDurationSeconds = 0,
                wsStatusText = "正在连接...",
                wsStatusConnected = false,
                interimText = "fun-asr-realtime 感知层监听中..."
            )
        }

        // 启动秒表
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRecording) {
                delay(1000)
                _uiState.update { it.copy(recordDurationSeconds = it.recordDurationSeconds + 1) }
            }
        }

        // 启动 WebSocket
        webSocketManager = AsrWebSocketManager { event ->
            handleAsrEvent(event)
        }
        webSocketManager?.start()

        // 启动录音机
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
                _uiState.update { it.copy(currentVolume = vol) }
            }
        )

        val started = audioRecorder?.start() ?: false
        if (!started) {
            stopRecording()
            showToast("麦克风启动失败，请检查录音权限！")
        }
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

        // 缓存本次无损 WAV 文件
        val wav = audioRecorder?.exportWavFile()
        _uiState.update { it.copy(exportedWavFile = wav) }

        // 若录音字数充沛，提示可生成纪要
        if (_uiState.value.utterances.isNotEmpty()) {
            showToast("录音已完成并固化，可立即生成人设纪要！")
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
                        text = event.text,
                        rawText = event.rawText,
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
                    _uiState.update { state ->
                        val updated = state.utterances.map { u ->
                            if (u.sentenceId == event.sentenceId) {
                                u.copy(text = event.text, isCleansed = true, latencyMs = event.latencyMs)
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

    fun selectPersona(persona: Persona) {
        _uiState.update { it.copy(currentPersona = persona) }
        showToast("已切换人设为: ${persona.name} (${persona.badge})")
    }

    fun generateMinutes() {
        val utterances = _uiState.value.utterances
        if (utterances.isEmpty()) {
            showToast("请先录音一段发言再生成纪要")
            return
        }

        val persona = _uiState.value.currentPersona
        _uiState.update { it.copy(isGeneratingMinutes = true) }

        viewModelScope.launch {
            val result = apiClient.generateMinutes(utterances, persona)
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
                // 刷新记忆库
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
        val text = _uiState.value.utterances.joinToString("\n") { "${it.speaker}: ${it.text}" }
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

    fun askSecondBrain(question: String) {
        val q = question.trim()
        if (q.isBlank()) return

        val userMsg = BrainChatMessage(
            id = "user-${System.currentTimeMillis()}",
            isUser = true,
            content = q,
            timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        )

        _uiState.update {
            it.copy(
                brainChatHistory = it.brainChatHistory + userMsg,
                isBrainThinking = true
            )
        }

        viewModelScope.launch {
            val result = apiClient.querySecondBrain(q)
            val answerText = result.getOrNull() ?: "抱歉，检索记忆库遇到网络问题，请稍后重试。"
            val assistantMsg = BrainChatMessage(
                id = "brain-${System.currentTimeMillis()}",
                isUser = false,
                content = answerText,
                timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                citations = listOf("全双工语音架构研讨", "Personal Agent 讨论")
            )
            _uiState.update {
                it.copy(
                    brainChatHistory = it.brainChatHistory + assistantMsg,
                    isBrainThinking = false
                )
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
