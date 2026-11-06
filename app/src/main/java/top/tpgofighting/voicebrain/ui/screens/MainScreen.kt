package top.tpgofighting.voicebrain.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeEffect
import top.tpgofighting.voicebrain.ui.components.TPBadge
import top.tpgofighting.voicebrain.ui.components.TPBadgeVariant
import top.tpgofighting.voicebrain.ui.theme.TPMustardYellow
import top.tpgofighting.voicebrain.ui.theme.TPNavyDark
import top.tpgofighting.voicebrain.ui.viewmodel.MainViewModel

// 主导航图标统一用线性（Outlined）风格，与 TP 的线绘设计语言一致，不用实心图标
enum class NavigationTab(val label: String, val icon: ImageVector) {
    LIVE("实时录音", Icons.Outlined.Mic),
    MINUTES("纪要决策", Icons.Outlined.Article),
    MEMORY("记忆大脑", Icons.Outlined.Psychology),
    OBSIDIAN("Obsidian", Icons.Outlined.Hub)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(NavigationTab.LIVE) }
    val context = LocalContext.current
    // 底部导航栏毛玻璃：内容层是 haze 源，导航栏是 haze 子层（采样身后内容做高斯模糊）
    val hazeState = remember { HazeState() }
    val barSurface = MaterialTheme.colorScheme.surface

    // 监听全局 Toast
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .border(1.5.dp, TPNavyDark, RoundedCornerShape(6.dp))
                                .background(TPMustardYellow, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("TP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = TPNavyDark)
                        }
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
                        onSetTemplate = { viewModel.setMinutesTemplate(it) }
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
