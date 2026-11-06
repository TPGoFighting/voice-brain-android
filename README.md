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

## v1.1.2 (2026-11-06) 产品审查修复 · 第 2/6/7 项

- **状态卡不再展示服务器绝对路径**：Obsidian 状态卡默认只显示「我的 Obsidian 仓库 · 与服务器实时同步」，`/home/ubuntu/...` 这类部署细节收进点按展开，避免把服务器目录结构直接印在界面上。
- **麦克风权限改为按需申请 + 前置说明**：启动 App 不再立刻弹系统权限框；首次点「开始录音」时先弹半屏说明（录音只发到自己的服务器做转写、不经过第三方），确认后才调系统框；通知权限跟随首次录音一并申请。若被拒绝，录音页顶部出现恢复卡，一键跳系统设置开启，不再只剩一句 Toast。
- **记忆页补齐空状态**：记忆库为空时不再是一片空白——说明记忆从何而来（录音纪要自动提炼）并提供「去录一段」直达录音页；筛选无结果时给出对应提示；问答分栏无对话时也补了一句能力说明。

## v1.1.1 (2026-11-06) 产品审查修复 · 第 1 项

- **Obsidian 状态卡数据真伪**：状态接口改按服务端真实字段解析——笔记总数取 `agentStatus.notes`、录音归档取 `voiceNotesCount`、是否挂载取 `vaultExists` + Agent 状态。此前客户端读的是服务端不存在的字段名，读不到便用写死的默认值（23 / 8 / 已挂载）冒充真实数据；现请求失败或字段缺失时如实显示「状态未知」并可点按重试，不再编造数字。解析逻辑已抽为独立函数并加 JVM 单元测试（用服务端真实响应样本锁定映射），构建流水线每次都会跑。

## v1.1.0 (2026-11-06) 全量升级

与 Web 端三期升级对齐，并针对 Android 设备体验做系统性优化：

- **契约修复（P0）**：纪要接口对齐服务端——请求改发 `persona` + `template` + 原文，响应从 `data` 层解析，未决事项读 `unresolved_risks`；此前安卓纪要因字段错位一直渲染为空壳，现已修复。移除全部假数据种子与假纪要兜底、假引用，改为真实空状态。
- **流式问答**：第二大脑问答接 `/api/memory/query/stream` SSE 逐字生成，final 事件落真实来源（可点开对应历史会话）；流式失败自动回退非流式。
- **全文检索**：新增「全文检索」子页，接 `/api/search` 检索全部录音原文与长期记忆，命中可跳转工作台回看。
- **纪要模板**：通用 / 会议 / 课程 / 访谈四模板可选，与服务端 MINUTES_TEMPLATES 对齐。
- **说话人分离**：本地 WAV 上传离线精转写（paraformer-v2 + diarization），结果按说话人着色回填逐字稿。
- **历史归档**：工作台可打开服务端历史会话回看。
- **DJI Mic Mini 优化**：枚举音频输入设备，自动优选 USB 接收器（DJI Mic Mini RX），支持手动切换音源，录音中监听接收器插拔并提示；逐字稿默认展示原文、清洗稿可切换。
- **后台保活**：录音前台服务（microphone 类型）+ WakeLock + 常驻通知（计时/停止动作）；Android 16 设备启用 Live Updates 提升（ProgressStyle + Promoted Ongoing），适配 ColorOS 17 流体云；内置电池豁免与 ColorOS 自启动设置引导。
- **UI/UX 与动效**：波形改为真实音量历史驱动 + 弹簧动画；录音按钮按压弹簧反馈；标签页切换弹簧转场；流式回答闪烁光标；录音时屏幕常亮；断句默认 1200ms 与服务端对齐。
