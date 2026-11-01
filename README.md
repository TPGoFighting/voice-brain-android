# TP Voice Brain Android (Jetpack Compose)

> 基于 **Kotlin 2.0 + Jetpack Compose + Material 3** 构建的个人长期语音录音与认知第二大脑 Android 原生客户端。
>
> 遵循 [haidrrrry/compose-kotlin-agent-skills](https://github.com/haidrrrry/compose-kotlin-agent-skills) 与 [almasumdev/awesome-kotlin-android-agent-skills](https://github.com/almasumdev/awesome-kotlin-android-agent-skills) 的现代 Android 架构规范，采用严格 MVI 模式、Atomic StateFlow 更新、Lifecycle-aware flow 收集及 TP Brand VI v2.4 新野兽派设计系统。

---

## 核心架构与功能分层

```
+-------------------------------------------------------------------------+
|                        TP Voice Brain Android Native                    |
+--------------------+--------------------+--------------------+----------+
| 1. 实时流式工作台   | 2. 纪要与决策对账   | 3. 第二大脑记忆库   | 4. Obsidian 知识库 |
| (Live Workbench)   | (Minutes & Personas)| (Second Brain Bank)| (Hermes & LMMWiki)|
+--------------------+--------------------+--------------------+----------+
| - AudioRecord 16k  | - 9 种 Koboyo 人设  | - 5 维记忆资产沉淀 | - Vault 状态与同步 |
| - WebSocket 双工流 | - 30s TL;DR 速览    | - 关键词与分类检索 | - 双链实体网络图谱 |
| - Qwen-flash 快修  | - 核心决议对账表    | - 第二大脑对话流   | - Hermes 问答对账  |
| - 动态声波可视化   | - Action Items 待办 | - 关联记忆引用徽标 | - 自动反向写回笔记 |
| - 本地无损 WAV 导出| - 排版润色正式稿   | - 自动静默沉淀入库 | - 手动追记至指定页 |
+--------------------+--------------------+--------------------+----------+
```

---

## 移动端原生优化特性

1. **分层语音实时流 (Tier 1 & Tier 2)**：
   - 使用 Android 原生 `AudioRecord`（`VOICE_RECOGNITION` 声学滤波与降噪）采集 16000Hz 16-bit Mono PCM，切块为 100ms 帧；
   - 配合 WebSocket 全双工长连接（`fun-asr-realtime`），支持 15 秒环形音频缓冲与 300ms 自动断线自愈重连；
   - 句级由 `qwen-flash` 原地快修错别字与口语语气助词。
2. **9 种 Koboyo 认知角色人设深度提炼**：
   - 系统架构师、算法科学家、底层工程师、总结魔法师、记忆宇航员、快轨暗影忍者、认知冥想者、声学音乐家、灵感探险家；
   - 支持移动端 BottomSheet 极速切换，一键以选定人设视角重构纪要。
3. **双视图模式切换**：
   - `[结构化纪要与决策对账]`：30秒速览、决策清单、待办项交互核对；
   - `[排版润色正式稿]`：深度重构长流水账为正式汇报稿，原生 Markdown 排版，支持一键全文复制。
4. **移动端第二大脑双栏设计**：
   - **记忆资产库**：偏好画像（preference）、项目方案（project）、重大决策（decision）、人物承诺（person）、客观经验（fact）；
   - **第二大脑 Agent**：内置快捷提示词胶囊，支持跨会话自然语言问答与溯源引用。
5. **Obsidian Vault & Hermes Agent 反向追记闭环**：
   - 直连云端知识库，查看已归档录音笔记与 LMMWiki 核心实体；
   - Hermes 多跳推理问答，开启 `自动反向追记` 即可将 AI 思考结果自动追加写入指定 Obsidian Markdown 笔记。
6. **TP 品牌 VI v2.4 规范**：
   - 高对比度新野兽派边框（1.5dp）与复古科技胶囊标签（TP Mustard Yellow `#F9D949` 与 Deep Navy `#0F172A`）；
   - **Zero Unicode Emoji 准则**：全面采用 Android 原生矢量图标（Vector Drawables / Material Icons）。

---

## 本地构建与打包

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK (默认回退使用内置 Debug 签名以确保零障碍安装)
./gradlew assembleRelease
```

构建生成的 APK 位于：
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
