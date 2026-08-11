package com.zlight106.nvvocab.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zlight106.nvvocab.R
import com.zlight106.nvvocab.ui.icons.NvvIcons
import com.zlight106.nvvocab.ui.screens.DictateScreen
import com.zlight106.nvvocab.ui.screens.DashboardScreen
import com.zlight106.nvvocab.ui.screens.ImportScreen
import com.zlight106.nvvocab.ui.screens.LexiconScreen
import com.zlight106.nvvocab.ui.screens.PracticeSessionRequest
import com.zlight106.nvvocab.ui.screens.PracticeSessionScreen
import com.zlight106.nvvocab.ui.screens.SettingsScreen
import com.zlight106.nvvocab.sync.SyncRuntimeState
import com.zlight106.nvvocab.sync.SyncRuntimeStatus
import com.zlight106.nvvocab.sync.SyncStateMonitor

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("dashboard", "仪表板", NvvIcons.LayoutDashboard),
    LEXICON("lexicon", "词库一览", NvvIcons.BookOpen),
    IMPORT("import", "词库导入", NvvIcons.CirclePlus),
    DICTATE("dictate", "沉浸复习", NvvIcons.BrainCircuit),
    SETTINGS("settings", "设置", NvvIcons.Settings),
}

private const val PRACTICE_SESSION_ROUTE = "practice-session"

@Composable
fun NvvocabApp(viewModel: MainViewModel, state: AppUiState) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val practiceSessionVisible = currentEntry?.destination?.route == PRACTICE_SESSION_ROUTE
    var activePracticeSession by remember { mutableStateOf<PracticeSessionRequest?>(null) }
    var sideNavigationExpanded by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val tags by viewModel.bookTags.collectAsStateWithLifecycle()
    val words by viewModel.words.collectAsStateWithLifecycle()
    val reviewLogs by viewModel.reviewLogs.collectAsStateWithLifecycle()
    val quizBanks by viewModel.quizBanks.collectAsStateWithLifecycle()
    val contrastSessions by viewModel.contrastPracticeSessions.collectAsStateWithLifecycle()
    val dailyPracticeProgress by viewModel.dailyPracticeProgress.collectAsStateWithLifecycle()
    val studyTimeProgress by viewModel.studyTimeProgress.collectAsStateWithLifecycle()
    val syncRuntimeState by SyncStateMonitor.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // HD mode covers the common 16:9 and 16:10 tablet/window shapes. Portrait
        // tablets still use the rail when there is enough independent width.
        val hdLandscape = isHdLandscape(maxWidth.value, maxHeight.value)
        val useSideNavigation = hdLandscape || maxWidth >= 700.dp
        val compactHdNavigation = hdLandscape && maxHeight < 640.dp
        if (useSideNavigation && !practiceSessionVisible) {
            Row(Modifier.fillMaxSize()) {
                WideNavigation(
                    navController = navController,
                    compact = compactHdNavigation,
                    expanded = sideNavigationExpanded,
                    onToggle = { sideNavigationExpanded = !sideNavigationExpanded },
                )
                AppScaffold(
                    modifier = Modifier.weight(1f),
                    navController = navController,
                    snackbarHostState = snackbarHostState,
                    showBottomBar = false,
                    showTopBarBrand = false,
                    viewModel = viewModel,
                    state = state,
                    tags = tags,
                    words = words,
                    reviewLogs = reviewLogs,
                    quizBanks = quizBanks,
                    contrastSessions = contrastSessions,
                    dailyPracticeProgress = dailyPracticeProgress,
                    studyTimeProgress = studyTimeProgress,
                    syncRuntimeState = syncRuntimeState,
                    activePracticeSession = activePracticeSession,
                    onStartPracticeSession = { request ->
                        activePracticeSession = request
                        navController.navigate(PRACTICE_SESSION_ROUTE) { launchSingleTop = true }
                    },
                    onClosePracticeSession = {
                        navController.popBackStack()
                        activePracticeSession = null
                    },
                )
            }
        } else {
            AppScaffold(
                modifier = Modifier.fillMaxSize(),
                navController = navController,
                snackbarHostState = snackbarHostState,
                showBottomBar = !practiceSessionVisible && !useSideNavigation,
                showTopBarBrand = true,
                viewModel = viewModel,
                state = state,
                tags = tags,
                words = words,
                reviewLogs = reviewLogs,
                quizBanks = quizBanks,
                contrastSessions = contrastSessions,
                dailyPracticeProgress = dailyPracticeProgress,
                studyTimeProgress = studyTimeProgress,
                syncRuntimeState = syncRuntimeState,
                activePracticeSession = activePracticeSession,
                onStartPracticeSession = { request ->
                    activePracticeSession = request
                    navController.navigate(PRACTICE_SESSION_ROUTE) { launchSingleTop = true }
                },
                onClosePracticeSession = {
                    navController.popBackStack()
                    activePracticeSession = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    modifier: Modifier,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    showBottomBar: Boolean,
    showTopBarBrand: Boolean,
    viewModel: MainViewModel,
    state: AppUiState,
    tags: List<String>,
    words: List<com.zlight106.nvvocab.data.WordEntry>,
    reviewLogs: List<com.zlight106.nvvocab.data.ReviewLogEntry>,
    quizBanks: List<com.zlight106.nvvocab.data.QuizBank>,
    contrastSessions: List<com.zlight106.nvvocab.data.ContrastPracticeSession>,
    dailyPracticeProgress: com.zlight106.nvvocab.data.DailyPracticeProgress,
    studyTimeProgress: com.zlight106.nvvocab.data.StudyTimeProgress,
    syncRuntimeState: SyncRuntimeState,
    activePracticeSession: PracticeSessionRequest?,
    onStartPracticeSession: (PracticeSessionRequest) -> Unit,
    onClosePracticeSession: () -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: Destination.DASHBOARD.route
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (currentRoute != PRACTICE_SESSION_ROUTE) {
                if (showTopBarBrand) {
                    BrandTopBar(state, syncRuntimeState)
                } else {
                    CompactStatusBar(state, syncRuntimeState)
                }
            }
        },
        bottomBar = {
            if (showBottomBar && currentRoute != PRACTICE_SESSION_ROUTE) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.open(destination.route) },
                            icon = { Icon(destination.icon, null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            NavHost(
                navController = navController,
                startDestination = Destination.DASHBOARD.route,
                modifier = Modifier.fillMaxSize().widthIn(max = 1280.dp),
            enterTransition = {
                fadeIn(tween(200)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220),
                    initialOffset = { it / 18 },
                )
            },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(140)) },
            ) {
            composable(Destination.DASHBOARD.route) {
                DashboardScreen(
                    state = state,
                    words = words,
                    logs = reviewLogs,
                    contrastSessions = contrastSessions,
                    quizBanks = quizBanks,
                    dailyProgress = dailyPracticeProgress,
                    studyTimeProgress = studyTimeProgress,
                    onSaveProgressSettings = viewModel::saveDailyProgressConfiguration,
                    onSaveStudyTimeGoal = viewModel::saveStudyTimeGoal,
                    onSaveDailyMemoSettings = viewModel::saveDailyMemoSettings,
                )
            }
            composable(Destination.LEXICON.route) { LexiconScreen(viewModel, words, tags) }
            composable(Destination.IMPORT.route) { ImportScreen(viewModel, tags) }
            composable(Destination.DICTATE.route) {
                DictateScreen(
                    viewModel = viewModel,
                    tags = tags,
                    quizBanks = quizBanks,
                    words = words,
                    administratorMode = state.administratorMode,
                    onAdministratorModeChange = viewModel::setAdministratorMode,
                    onStartSession = onStartPracticeSession,
                )
            }
            composable(Destination.SETTINGS.route) { SettingsScreen(viewModel, state, quizBanks) }
            composable(PRACTICE_SESSION_ROUTE) {
                val session = activePracticeSession
                if (session == null) {
                    LaunchedEffect(Unit) { onClosePracticeSession() }
                } else {
                    PracticeSessionScreen(
                        request = session,
                        viewModel = viewModel,
                        administratorMode = state.administratorMode,
                        onExit = onClosePracticeSession,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun CompactStatusBar(state: AppUiState, syncRuntimeState: SyncRuntimeState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ServerConnectionNotice(state = state, syncRuntimeState = syncRuntimeState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrandTopBar(state: AppUiState, syncRuntimeState: SyncRuntimeState) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(
                    painter = painterResource(R.drawable.bwolf),
                    contentDescription = "单词速记",
                    modifier = Modifier.width(46.dp),
                )
                Column {
                    Text("单词速记", style = MaterialTheme.typography.titleMedium)
                    Text("非易失性词库", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        actions = {
            ServerConnectionNotice(state = state, syncRuntimeState = syncRuntimeState)
        },
    )
}

@Composable
private fun ServerConnectionNotice(
    state: AppUiState,
    syncRuntimeState: SyncRuntimeState,
) {
    val serverConfigured = state.supabaseConfig.url.isNotBlank() &&
        state.supabaseConfig.publishableKey.isNotBlank()
    val serverConnected = serverConfigured && state.session != null
    var expanded by rememberSaveable { mutableStateOf(!serverConnected) }
    LaunchedEffect(serverConnected) {
        if (!serverConnected) expanded = true
    }
    val status = when {
        !serverConnected -> null
        else -> syncRuntimeState.status
    }
    val statusIcon = when (status) {
        null, SyncRuntimeStatus.FAILED -> NvvIcons.AlertCircle
        SyncRuntimeStatus.RUNNING -> NvvIcons.RefreshCw
        SyncRuntimeStatus.SUCCESS -> NvvIcons.Check
        SyncRuntimeStatus.IDLE -> NvvIcons.Cloud
    }
    val statusColor = when (status) {
        null, SyncRuntimeStatus.FAILED -> MaterialTheme.colorScheme.error
        SyncRuntimeStatus.RUNNING, SyncRuntimeStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        SyncRuntimeStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                statusIcon,
                contentDescription = status.title(),
                tint = statusColor,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Column(
                modifier = Modifier.width(280.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(statusIcon, null, tint = statusColor)
                    Text(status.title(), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    when {
                        !serverConfigured -> "尚未配置 Supabase 连接信息，请前往设置页面完成配置。"
                        state.session == null -> "服务器已配置，但尚未登录账户。当前仍可继续离线使用。"
                        status == SyncRuntimeStatus.RUNNING -> "正在将本地修改与 Supabase 数据进行同步。"
                        status == SyncRuntimeStatus.SUCCESS -> "最近一次同步已经完成，本地与服务器连接正常。"
                        status == SyncRuntimeStatus.FAILED -> "最近一次同步失败，将在网络条件满足后重试。"
                        else -> "服务器连接已配置，当前等待下一次自动或手动同步。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "轻触标题栏警示图标可再次查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun SyncRuntimeStatus?.title(): String = when (this) {
    null -> "未连接到服务器"
    SyncRuntimeStatus.IDLE -> "已连接到服务器"
    SyncRuntimeStatus.RUNNING -> "正在同步"
    SyncRuntimeStatus.SUCCESS -> "同步完成"
    SyncRuntimeStatus.FAILED -> "同步失败"
}

@Composable
private fun WideNavigation(
    navController: NavHostController,
    compact: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: Destination.DASHBOARD.route
    // Animating width forces the complete NavHost to remeasure on every frame, which
    // stutters on some tablets. Snap the width and animate only this draw-only affordance.
    val navigationWidth = when {
        !expanded -> 84.dp
        compact -> 240.dp
        else -> 252.dp
    }
    val toggleRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(140),
        label = "side-navigation-toggle",
    )
    Surface(
        modifier = Modifier.width(navigationWidth).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                .padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = if (compact) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.align(if (expanded) Alignment.End else Alignment.CenterHorizontally),
            ) {
                Icon(
                    NvvIcons.ChevronDown,
                    contentDescription = if (expanded) "折叠侧边栏" else "展开侧边栏",
                    modifier = Modifier.rotate(toggleRotation),
                )
            }
            Image(
                painter = painterResource(R.drawable.bwolf),
                contentDescription = "单词速记",
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(if (!expanded || compact) 48.dp else 72.dp)
                    .padding(bottom = if (!expanded || compact) 2.dp else 8.dp),
            )
            if (expanded) {
                Text("单词速记", style = MaterialTheme.typography.titleMedium)
                Text(
                    "非易失性词库",
                    modifier = Modifier.padding(bottom = if (compact) 6.dp else 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Destination.entries.forEach { destination ->
                if (expanded) {
                    NavigationDrawerItem(
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 44.dp else 56.dp),
                        selected = currentRoute == destination.route,
                        onClick = { navController.open(destination.route) },
                        icon = { Icon(destination.icon, null) },
                        label = {
                            Text(
                                destination.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                } else {
                    NavigationRailItem(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        selected = currentRoute == destination.route,
                        onClick = { navController.open(destination.route) },
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier.size(26.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

internal fun isHdLandscape(widthDp: Float, heightDp: Float): Boolean {
    if (heightDp <= 0f || widthDp < 840f) return false
    return widthDp / heightDp in 1.45f..2f
}

private fun NavHostController.open(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.startDestinationId) { saveState = true }
    }
}
