package top.tpgofighting.voicebrain.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.tpgofighting.voicebrain.ui.components.TPBadge
import top.tpgofighting.voicebrain.ui.components.TPBadgeVariant
import top.tpgofighting.voicebrain.ui.theme.TPMustardYellow
import top.tpgofighting.voicebrain.ui.theme.TPNavyDark
import top.tpgofighting.voicebrain.ui.viewmodel.MainViewModel

enum class NavigationTab(val label: String, val icon: ImageVector) {
    LIVE("实时录音", Icons.Filled.Mic),
    MINUTES("纪要决策", Icons.Filled.Article),
    MEMORY("记忆大脑", Icons.Filled.Psychology),
    OBSIDIAN("Obsidian", Icons.Filled.Hub)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(NavigationTab.LIVE) }
    val context = LocalContext.current

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
                actions = {
                    TPBadge(
                        text = "${uiState.memories.size} 条记忆",
                        variant = TPBadgeVariant.BLUE,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
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
                            selectedTextColor = TPNavyDark,
                            indicatorColor = TPMustardYellow,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    androidx.compose.animation.ContentTransform(
                        androidx.compose.animation.fadeIn(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        ) + androidx.compose.animation.slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { if (targetState.ordinal > initialState.ordinal) 60 else -60 }
                        ),
                        androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(120)
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
