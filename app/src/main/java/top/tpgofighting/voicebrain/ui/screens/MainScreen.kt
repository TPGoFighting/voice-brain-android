package top.tpgofighting.voicebrain.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.Network
import com.composables.icons.lucide.NotebookText
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Settings2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeEffect
import top.tpgofighting.voicebrain.ui.components.TPBadge
import top.tpgofighting.voicebrain.ui.components.TPBadgeVariant
import top.tpgofighting.voicebrain.ui.components.TPLogoTile
import top.tpgofighting.voicebrain.ui.theme.TPMustardYellow
import top.tpgofighting.voicebrain.ui.theme.TPNavyDark
import top.tpgofighting.voicebrain.ui.viewmodel.MainViewModel

// 主导航图标统一用 Lucide 线性图标（简笔画线条风），不用实心图标
enum class NavigationTab(val label: String, val icon: ImageVector) {
    LIVE("实时录音", Lucide.Mic),
    MINUTES("纪要决策", Lucide.NotebookText),
    MEMORY("记忆大脑", Lucide.Brain),
    OBSIDIAN("Obsidian", Lucide.Network)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    themeMode: String = "system",
    onThemeModeChange: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(NavigationTab.LIVE) }
    val context = LocalContext.current
    // 底部导航栏毛玻璃：内容层是 haze 源，导航栏是 haze 子层（采样身后内容做高斯模糊）
    val hazeState = remember { HazeState() }
    val barSurface = MaterialTheme.colorScheme.surface
    // 批次 J 布局：侧边抽屉（导航 + 历史会话 + 设置入口）与设置页
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) viewModel.refreshSessions()
    }

    // 监听全局 Toast
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    if (showSettings) {
        SettingsScreen(
            state = uiState,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onSetSilence = { viewModel.setSentenceSilence(it) },
            onToggleCleansed = { viewModel.toggleShowCleansed(it) },
            onBatteryExemption = { viewModel.requestBatteryExemption() },
            onAutoStartSettings = { viewModel.openAutoStartSettings() },
            onBack = { showSettings = false }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerSheet(
                state = uiState,
                currentTab = currentTab,
                onSelectTab = { tab ->
                    currentTab = tab
                    scope.launch { drawerState.close() }
                },
                onOpenSession = { sessionId ->
                    viewModel.loadSession(sessionId)
                    currentTab = NavigationTab.LIVE
                    scope.launch { drawerState.close() }
                },
                onRefreshSessions = { viewModel.refreshSessions() },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    showSettings = true
                }
            )
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Lucide.Menu, contentDescription = "打开菜单", modifier = Modifier.size(20.dp))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TPLogoTile(size = 30.dp)
                        Text("Voice Brain", fontSize = 15.sp, fontWeight = FontWeight.Black)
                        TPBadge(text = uiState.currentPersona.badge, variant = TPBadgeVariant.YELLOW)
                    }
                },
                // 顶栏不再放「N 条记忆」计数：与记忆页的计数重复，属于噪音
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = barSurface,
                        tint = HazeTint(barSurface.copy(alpha = 0.58f)),
                        blurRadius = 22.dp,
                        noiseFactor = 0f,
                        fallbackTint = HazeTint(barSurface.copy(alpha = 0.92f))
                    )
                ),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                NavigationTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TPNavyDark,
                            // 选中文字在胶囊下方的栏面上：深色模式下深藏青会没入深底，改跟随主题正文色
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = TPMustardYellow,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        // 内容层只避让顶栏、向下通铺到屏幕底边：底部导航栏悬浮其上做毛玻璃，
        // 各页内部滚动列表自带底部留白（96dp），固定底栏（问答输入框）自带底部间距，不会被遮住。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState)
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    // 方向感知的共享轴：新页从滑动方向一侧以 1/4 屏宽滑入并微微放大，旧页反向滑出淡出
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    val springSpec = androidx.compose.animation.core.spring<Float>(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                    val slideSpring = androidx.compose.animation.core.spring<IntOffset>(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    )
                    androidx.compose.animation.ContentTransform(
                        androidx.compose.animation.fadeIn(animationSpec = springSpec) +
                            androidx.compose.animation.slideInHorizontally(
                                animationSpec = slideSpring,
                                initialOffsetX = { full -> direction * full / 4 }
                            ) +
                            androidx.compose.animation.scaleIn(
                                initialScale = 0.985f,
                                animationSpec = springSpec
                            ),
                        androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(140)
                        ) + androidx.compose.animation.slideOutHorizontally(
                            animationSpec = slideSpring,
                            targetOffsetX = { full -> -direction * full / 6 }
                        )
                    )
                },
                label = "tabTransition"
            ) { tab ->
            when (tab) {
                NavigationTab.LIVE -> {
                    LiveWorkbenchTab(
                        state = uiState,
                        onToggleRecord = {
                            if (uiState.isRecording) viewModel.stopRecording() else viewModel.startRecording()
                        },
                        onExportWav = { viewModel.exportCurrentWav() },
                        onGenerateMinutes = {
                            viewModel.generateMinutes()
                            currentTab = NavigationTab.MINUTES
                        },
                        onSetSilence = { viewModel.setSentenceSilence(it) },
                        onSelectDevice = { viewModel.selectAudioDevice(it) },
                        onToggleCleansed = { viewModel.toggleShowCleansed(it) },
                        onDiarize = { viewModel.diarizeCurrentRecording() },
                        onOpenSession = { viewModel.loadSession(it) },
                        onRefreshSessions = { viewModel.refreshSessions() },
                        onBatteryExemption = { viewModel.requestBatteryExemption() },
                        onAutoStartSettings = { viewModel.openAutoStartSettings() },
                        onConfirmMicPermission = { viewModel.confirmMicPermissionRequest() },
                        onDismissMicRationale = { viewModel.dismissMicRationale() },
                        onBatteryHintHandled = { viewModel.onBatteryHintHandled() }
                    )
                }
                NavigationTab.MINUTES -> {
                    MinutesTab(
                        state = uiState,
                        onSelectPersona = { viewModel.selectPersona(it) },
                        onGenerateMinutes = { viewModel.generateMinutes() },
                        onToggleViewMode = { viewModel.toggleMinutesViewMode(it) },
                        onPolishDraft = { viewModel.polishCurrentMinutes() },
                        onShowToast = { viewModel.showToast(it) },
                        onSetTemplate = { viewModel.setMinutesTemplate(it) },
                        onGoRecord = { currentTab = NavigationTab.LIVE }
                    )
                }
                NavigationTab.MEMORY -> {
                    MemoryBrainTab(
                        state = uiState,
                        onSetSubTab = { viewModel.setMemorySubTab(it) },
                        onSetCategory = { viewModel.setMemoryCategory(it) },
                        onSetSearchQuery = { viewModel.setMemorySearchQuery(it) },
                        onAskSecondBrain = { viewModel.askSecondBrain(it) },
                        onSetFullTextQuery = { viewModel.setSearchQuery(it) },
                        onRunSearch = { viewModel.runSearch() },
                        onOpenSession = {
                            viewModel.loadSession(it)
                            currentTab = NavigationTab.LIVE
                        },
                        onGoRecord = { currentTab = NavigationTab.LIVE }
                    )
                }
                NavigationTab.OBSIDIAN -> {
                    ObsidianWikiTab(
                        state = uiState,
                        onToggleAutoWriteBack = { viewModel.toggleAutoWriteBack() },
                        onAskObsidian = { viewModel.askObsidian(it) },
                        onRefreshObsidianStatus = { viewModel.refreshObsidianStatus() }
                    )
                }
            }
            }
        }
    }
    }
}

/**
 * 侧边抽屉（批次 J/K）：主流软件式布局——品牌头 + 主导航 + 历史会话 + 设置入口。
 * 批次 K 按 TP 的间距规范重排：一套统一标尺——两侧留白 20dp 对称、行高至少 52dp
 * （点击区足够大）、图标 16dp 略小于文字、图文间距 12dp 全栏一致、圆角 10dp 与
 * TPCard 同级一致、组内行距 2dp 而组间 24dp（组间比组内松）。
 */
@Composable
private fun AppDrawerSheet(
    state: top.tpgofighting.voicebrain.ui.viewmodel.MainUiState,
    currentTab: NavigationTab,
    onSelectTab: (NavigationTab) -> Unit,
    onOpenSession: (String) -> Unit,
    onRefreshSessions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // 品牌头：TP 正式线稿 Logo + 名称
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                TPLogoTile(size = 40.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Voice Brain", fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text("AI 录音豆", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(24.dp))

            // 主导航：每行 52dp 高，图标 16dp、图文距 12dp、选中胶囊圆角 10dp（与卡片同级）
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NavigationTab.values().forEach { tab ->
                    val selected = tab == currentTab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .background(
                                if (selected) TPMustardYellow else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectTab(tab) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selected) TPNavyDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            tab.label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                            color = if (selected) TPNavyDark else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Spacer(Modifier.height(24.dp))

            // 历史会话
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Lucide.History, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("历史会话", fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = onRefreshSessions, modifier = Modifier.size(32.dp)) {
                    Icon(Lucide.RefreshCw, contentDescription = "刷新会话", modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.sessions.isEmpty()) {
                Text(
                    "暂无历史会话，录一段就会出现在这里",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.sessions.take(8).forEach { session ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { onOpenSession(session.id) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                session.title.ifBlank { "未命名会话" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val metaParts = mutableListOf<String>()
                            if (session.stamp.isNotBlank()) metaParts += session.stamp
                            if (session.durationLabel.isNotBlank()) metaParts += session.durationLabel
                            if (session.utteranceCount > 0) metaParts += "${session.utteranceCount} 句"
                            val meta = metaParts.joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(meta, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Spacer(Modifier.height(24.dp))

            // 设置入口：与导航行同一标尺
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Lucide.Settings2, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("设置", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "voice.tpgofighting.top",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
