package top.tpgofighting.voicebrain.ui.screens

import top.tpgofighting.voicebrain.model.Utterance

/**
 * 将服务端会话来源字段映射为紧凑的多端徽章文案（Mac / Web / Android）。
 */
fun sourceBadgeLabel(source: String): String = when (source.trim().lowercase()) {
    "desktop", "mac", "macos" -> "Mac"
    "web", "browser" -> "Web"
    "android", "mobile", "" -> "Android"
    else -> source.trim()
}

/**
 * 根据双轨字段与说话人名称解析逐字稿展示用说话人标签。
 * - track == "me" 时将空值/默认占位归一为 "我"
 * - track == "them" 时将空值/默认占位归一为 "对方"
 * - 其余情况保留显式说话人名称（如说话人分离后的 "发言人 1"）
 */
fun utteranceDisplaySpeaker(u: Utterance): String {
    val raw = u.speaker.trim()
    return when (u.track.trim().lowercase()) {
        "me" -> if (raw.isEmpty() || raw == "发言人" || raw.equals("me", ignoreCase = true)) "我" else raw
        "them" -> if (raw.isEmpty() || raw == "发言人" || raw.equals("them", ignoreCase = true)) "对方" else raw
        else -> raw.ifEmpty { "发言人" }
    }
}

/**
 * 合并服务端笔记与本地未同步草稿：弱网离线草稿优先，避免丢字。
 * 返回 Pair(展示文案, 状态标识)。
 */
internal fun resolveLoadedMyNotes(serverNotes: String, localDraft: String?): Pair<String, String> = when {
    localDraft != null -> localDraft to "离线暂存"
    serverNotes.isNotBlank() -> serverNotes to "已保存"
    else -> "" to ""
}

internal data class StartRecordingNotesPlan(
    val initialNotes: String,
    val initialSaveState: String,
    val preservePreRecordingDraft: Boolean
)

/**
 * 决定按下「开始录音」时的手写笔记迁移策略：
 * - 若离开已有会话（prevSessionId 非空），清空新录音的笔记区（旧会话笔记由调用方补推）。
 * - 若在录音前（prevSessionId 为空）已写下会前要点，则保留并准备迁移到新录音会话。
 */
internal fun resolveStartRecordingNotes(
    prevSessionId: String,
    prevNotes: String
): StartRecordingNotesPlan {
    val preserve = prevSessionId.isBlank() && prevNotes.isNotBlank()
    return if (preserve) {
        StartRecordingNotesPlan(
            initialNotes = prevNotes,
            initialSaveState = "保存中…",
            preservePreRecordingDraft = true
        )
    } else {
        StartRecordingNotesPlan(
            initialNotes = "",
            initialSaveState = "",
            preservePreRecordingDraft = false
        )
    }
}
