package top.tpgofighting.voicebrain.service

/**
 * 录音前台服务与 UI 之间的极简事件桥：
 * 通知上的「停止」动作经由这里回调到 MainViewModel，避免服务直接持有录音管线。
 */
object RecordingBus {
    @Volatile
    var onStopRequested: (() -> Unit)? = null

    @Volatile
    var onOpenRequested: (() -> Unit)? = null

    fun requestStop() {
        onStopRequested?.invoke()
    }
}
