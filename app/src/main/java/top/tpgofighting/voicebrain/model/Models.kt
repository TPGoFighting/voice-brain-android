package top.tpgofighting.voicebrain.model

import kotlinx.serialization.Serializable

@Serializable
data class Utterance(
    val id: Int,
    val sentenceId: String,
    val speaker: String = "发言人",
    val time: String,
    val text: String,
    val rawText: String,
    val isCleansed: Boolean = false,
    val latencyMs: Long = 0
)

data class Persona(
    val id: String,
    val name: String,
    val title: String,
    val badge: String,
    val quote: String,
    val description: String,
    val systemPrompt: String
) {
    companion object {
        val ALL = listOf(
            Persona(
                id = "architect",
                name = "系统架构师",
                title = "模块解耦与服务契约",
                badge = "架构视角",
                quote = "关注高内聚低耦合、技术选型与服务边界划分。",
                description = "从宏观系统拓扑、领域模型边界与架构演进策略审视全场讨论。",
                systemPrompt = "你是一名顶级分布式系统架构师。在提炼纪要时，重点分析系统边界、服务分层、高可用与容灾设计。"
            ),
            Persona(
                id = "scientist",
                name = "算法科学家",
                title = "第一性原理与指标收敛",
                badge = "算法推演",
                quote = "以数学严谨性审视技术假设与指标收敛。",
                description = "深入模型准确率、延迟约束与最优化理论，杜绝经验主义。",
                systemPrompt = "你是一名顶尖算法科学家。关注技术指标、收敛边界、数学逻辑与第一性原理。"
            ),
            Persona(
                id = "core_dev",
                name = "底层工程师",
                title = "内存池与硬核性能",
                badge = "底层硬核",
                quote = "关注指针安全、零拷贝、内存占用与压测瓶颈。",
                description = "从操作系统内核、网络栈调优与字节级数据布局出发，捍卫极限性能。",
                systemPrompt = "你是一名资深底层与性能工程师。重点审视内存逃逸、锁竞争、零拷贝与极致性能。"
            ),
            Persona(
                id = "wizard",
                name = "总结魔法师",
                title = "30秒高管电梯汇报",
                badge = "高层速览",
                quote = "用最短的篇幅提炼商业本质，拒绝冗长流水账。",
                description = "高度凝练，突出投资回报率、商业决断与核心业务杠杆点。",
                systemPrompt = "你是一名麦肯锡级别的高管顾问。产出极高信噪比的30秒电梯纪要，直击核心结论。"
            ),
            Persona(
                id = "astronaut",
                name = "记忆宇航员",
                title = "第二大脑知识星系",
                badge = "知识引力",
                quote = "寻找跨越时间的认知资产与长期记忆碎片。",
                description = "识别对用户具有终生或年度价值的决策经验与原则性洞察。",
                systemPrompt = "你是一名认知科学专家。敏锐捕捉对话中值得沉淀为长期第二大脑记忆的知识碎片。"
            ),
            Persona(
                id = "ninja",
                name = "快轨暗影忍者",
                title = "敏捷交付与DoD验收",
                badge = "极速落地",
                quote = "剔除一切空谈，只保留清晰的责任人、排期与DoD标准。",
                description = "强调颗粒度细致的Action Items与即时交付清单。",
                systemPrompt = "你是一名敏捷交付主管。将所有模糊讨论强硬转化为清晰的待办事项与验收标准。"
            ),
            Persona(
                id = "meditator",
                name = "认知冥想者",
                title = "动机洞察与盲区反思",
                badge = "深度洞察",
                quote = "穿透表面共识，洞悉各方潜台词与决策盲区。",
                description = "从博弈论与组织心理学切入，复盘决策背后的风险假定。",
                systemPrompt = "你是一名组织认知心理学家。反思各方潜意识假设、隐形技术负债与决策盲区。"
            ),
            Persona(
                id = "musician",
                name = "声学音乐家",
                title = "跨职能同频与共识合奏",
                badge = "共识合奏",
                quote = "调和技术与业务的分歧，谱写一致的行动乐章。",
                description = "梳理跨部门争议，求同存异，形成极具向心力的共识结论。",
                systemPrompt = "你是一名跨职能组织协调家。梳理分歧焦点，提炼各方最大公约数共识。"
            ),
            Persona(
                id = "explorer",
                name = "灵感探险家",
                title = "颠覆性脑洞与未知蓝海",
                badge = "新知先锋",
                quote = "踏入未知的随笔领域，让每个灵感火花都被妥善安放。",
                description = "捕捉谈话中不经意流露出的颠覆性产品构想与蓝海机会。",
                systemPrompt = "你是一名极具前瞻性的产品创新探险家。从随想中提炼具有时代拐点意义的新物种机会。"
            )
        )

        val DEFAULT = ALL.last() // 灵感探险家
    }
}

@Serializable
data class ActionItem(
    val id: String,
    val task: String,
    val assignee: String = "待定",
    val priority: String = "P1",
    val status: String = "pending",
    val isCompleted: Boolean = false
)

data class Minutes(
    val title: String,
    val date: String,
    val duration: String,
    val personaId: String,
    val personaName: String,
    val tldr: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val unresolvedQuestions: List<String> = emptyList(),
    val mindmapMarkdown: String = "",
    val polishedDraft: String = ""
)

@Serializable
data class MemoryItem(
    val id: String,
    val category: String, // preference, project, decision, person, fact
    val content: String,
    val tags: List<String> = emptyList(),
    val importance: Int = 3,
    val createdAt: String = ""
)

data class ObsidianStatus(
    val connected: Boolean = true,
    val vaultPath: String = "/home/ubuntu/obsidian-vault",
    val totalNotes: Int = 23,
    val syncedVoiceNotes: Int = 8,
    val hermesAvailable: Boolean = true
)

data class ObsidianNote(
    val path: String,
    val title: String,
    val date: String,
    val persona: String,
    val tags: List<String> = emptyList()
)

data class LmmWikiEntity(
    val name: String,
    val description: String,
    val category: String
)

data class BrainChatMessage(
    val id: String,
    val isUser: Boolean,
    val content: String,
    val timestamp: String,
    val citations: List<String> = emptyList()
)
