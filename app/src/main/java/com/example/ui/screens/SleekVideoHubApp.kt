package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.example.ui.theme.GreyText
import com.example.ui.theme.Primary
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SurfaceVariant
import com.example.ui.theme.AmbientGlassBackground
import com.example.ui.theme.liquidGlass
import com.example.viewmodel.VideoViewModel
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures

val LocalFocusStyle = androidx.compose.runtime.compositionLocalOf { "glow" }
val LocalTvGridColumns = androidx.compose.runtime.compositionLocalOf { 4 }
val LocalTvVideoGridColumns = androidx.compose.runtime.compositionLocalOf { 4 }
val LocalMobileGridColumns = androidx.compose.runtime.compositionLocalOf { 2 }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.sleekTvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    focusColor: Color = MaterialTheme.colorScheme.primary,
    scaleAmount: Float = 1.06f,
    onEnter: (() -> Unit)? = null,
    onLongEnter: (() -> Unit)? = null,
    enabled: Boolean = true
): Modifier = if (!enabled) this else this.composed {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var longPressTriggered by remember { mutableStateOf(false) }

    val focusStyle = LocalFocusStyle.current
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFocused) {
            if (focusStyle == "scale" || focusStyle == "scale_glow") scaleAmount else 1.00f
        } else {
            1.0f
        },
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "focus_scale"
    )

    val focusModifier = Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (isFocused) {
                when (focusStyle) {
                    "scale" -> Modifier
                    "scale_glow" -> Modifier.border(2.5.dp, focusColor, shape)
                    "classic" -> Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f), shape)
                    "tint" -> Modifier.background(focusColor.copy(alpha = 0.12f), shape).border(1.5.dp, focusColor.copy(alpha = 0.6f), shape)
                    else -> Modifier.border(2.5.dp, focusColor.copy(alpha = 0.85f), shape)
                }
            } else {
                Modifier
            }
        )

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { state ->
            isFocused = state.isFocused
            if (!state.isFocused) {
                longPressJob?.cancel()
                longPressJob = null
                longPressTriggered = false
            } else {
                scope.launch { bringIntoViewRequester.bringIntoView() }
            }
        }
        .onKeyEvent { event ->
            if (onEnter == null && onLongEnter == null) {
                return@onKeyEvent false
            }
            if (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter) {
                if (event.type == KeyEventType.KeyDown) {
                    if (longPressJob == null && !longPressTriggered) {
                        longPressJob = scope.launch {
                            kotlinx.coroutines.delay(500)
                            if (onLongEnter != null) {
                                longPressTriggered = true
                                onLongEnter.invoke()
                            }
                        }
                    }
                    true
                } else if (event.type == KeyEventType.KeyUp) {
                    longPressJob?.cancel()
                    longPressJob = null
                    if (!longPressTriggered) {
                        onEnter?.invoke()
                    }
                    longPressTriggered = false
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        .focusable()
        .then(focusModifier)
}

fun Modifier.mouseDragScrollable(state: androidx.compose.foundation.lazy.LazyListState): Modifier = this.pointerInput(state) {
    detectDragGestures(
        onDrag = { change, dragAmount ->
            change.consume()
            state.dispatchRawDelta(-dragAmount.x)
        }
    )
}

@Composable
fun SleekVideoHubApp(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val focusStyle by viewModel.focusStyle.collectAsStateWithLifecycle()
    val tvGridColumns by viewModel.tvGridColumns.collectAsStateWithLifecycle()
    val tvVideoGridColumns by viewModel.tvVideoGridColumns.collectAsStateWithLifecycle()
    val mobileGridColumns by viewModel.mobileGridColumns.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalFocusStyle provides focusStyle,
        LocalTvGridColumns provides tvGridColumns,
        LocalTvVideoGridColumns provides tvVideoGridColumns,
        LocalMobileGridColumns provides mobileGridColumns
    ) {
        val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
        val currentSelectedVideo by viewModel.currentSelectedVideo.collectAsStateWithLifecycle()
        val isMiniPlayer by viewModel.isMiniPlayer.collectAsStateWithLifecycle()
        val isTermsAgreed by viewModel.isTermsAgreed.collectAsStateWithLifecycle()

        val context = LocalContext.current

        if (!isTermsAgreed) {
            TermsAgreementScreen(
                onAgree = { viewModel.agreeToTerms() },
                onDecline = { context.findActivity()?.finish() }
            )
            return@CompositionLocalProvider
        }
        
        GlobalUpdateChecker()

        var lastBackPressTime by remember { mutableStateOf(0L) }

        androidx.activity.compose.BackHandler(enabled = currentSelectedVideo == null) {
            if (viewModel.canNavigateBack()) {
                viewModel.navigateBack()
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000L) {
                    context.findActivity()?.finish()
                } else {
                    lastBackPressTime = currentTime
                    android.widget.Toast.makeText(context, "Нажмите назад ещё раз для выхода", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
        val isTvOptimized by viewModel.isTvOptimized.collectAsStateWithLifecycle()
        val isInPipMode by viewModel.isInPipMode.collectAsStateWithLifecycle()

        if (isInPipMode) {
            currentSelectedVideo?.let { video ->
                SleekPlayerDetailOverlay(
                    video = video,
                    viewModel = viewModel,
                    isDark = isDarkTheme,
                    isTvOptimized = isTvOptimized,
                    isMiniPlayer = false,
                    isInPipMode = true,
                    onDismiss = { viewModel.selectVideo(null) },
                    onRestore = {}
                )
            }
            return@CompositionLocalProvider
        }

    AmbientGlassBackground(isDark = isDarkTheme, isTvOptimized = isTvOptimized, modifier = modifier) {
        if (isTvOptimized) {
            Row(modifier = Modifier.fillMaxSize()) {
                val shouldShowSidebar = currentSelectedVideo == null
                if (shouldShowSidebar) {
                    SleekTvNavigationRail(
                        selectedTab = currentTab,
                        onTabSelected = { viewModel.selectTab(it) },
                        isDark = isDarkTheme,
                        isTvOptimized = isTvOptimized
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Main switcher content based on selected tab
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(120))
                        },
                        label = "tab_fade",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        when (tab) {
                            "home" -> HomeTabScreen(viewModel = viewModel)
                            "explore" -> ExploreTabScreen(viewModel = viewModel)
                            "recents" -> RecentsTabScreen(viewModel = viewModel)
                            "downloads" -> DownloadsTabScreen(viewModel = viewModel)
                            "library" -> LibraryTabScreen(viewModel = viewModel)
                            "settings" -> SettingsTabScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Main switcher content based on selected tab
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                        },
                        label = "tab_fade",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = innerPadding.calculateTopPadding())
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                when (tab) {
                                    "home" -> HomeTabScreen(viewModel = viewModel)
                                    "explore" -> ExploreTabScreen(viewModel = viewModel)
                                    "recents" -> RecentsTabScreen(viewModel = viewModel)
                                    "downloads" -> DownloadsTabScreen(viewModel = viewModel)
                                    "library" -> LibraryTabScreen(viewModel = viewModel)
                                    "settings" -> SettingsTabScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }

                    // Floating dock panel at the bottom center (like macOS Dock)
                    SleekBottomNavigation(
                        selectedTab = currentTab,
                        onTabSelected = { viewModel.selectTab(it) },
                        isDark = isDarkTheme,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 6.dp)
                    )
                }
            }
        }

        // Expanded video player detail overlay - slides from bottom
        AnimatedVisibility(
            visible = currentSelectedVideo != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            currentSelectedVideo?.let { video ->
                if (isTvOptimized) {
                    TvMiniPlayerScreen(viewModel = viewModel)
                } else {
                    SleekPlayerDetailOverlay(
                        video = video,
                        viewModel = viewModel,
                        isDark = isDarkTheme,
                        isTvOptimized = isTvOptimized,
                        isMiniPlayer = isMiniPlayer,
                        isInPipMode = false,
                        onDismiss = {
                            if (isMiniPlayer) viewModel.selectVideo(null)
                            else viewModel.setMiniPlayer(true)
                        },
                        onRestore = { viewModel.setMiniPlayer(false) }
                    )
                }
            }
        }
    }
}
}

@Composable
fun SleekBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth(0.92f)
            .height(60.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .liquidGlass(RoundedCornerShape(24.dp), borderWidth = 1.5.dp, isDark = isDark)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomTabItem(
                label = "Главная",
                icon = Icons.Default.Home,
                isActive = selectedTab == "home",
                onClick = { onTabSelected("home") },
                testTag = "tab_home"
            )
            BottomTabItem(
                label = "Обзор",
                icon = Icons.Default.Explore,
                isActive = selectedTab == "explore",
                onClick = { onTabSelected("explore") },
                testTag = "tab_explore"
            )
            BottomTabItem(
                label = "Недавние",
                icon = Icons.Default.History,
                isActive = selectedTab == "recents",
                onClick = { onTabSelected("recents") },
                testTag = "tab_recents"
            )
            BottomTabItem(
                label = "Загрузки",
                icon = Icons.Default.Download,
                isActive = selectedTab == "downloads",
                onClick = { onTabSelected("downloads") },
                testTag = "tab_downloads"
            )
            BottomTabItem(
                label = "Избранное",
                icon = Icons.Default.Favorite,
                isActive = selectedTab == "library",
                onClick = { onTabSelected("library") },
                testTag = "tab_library"
            )
            BottomTabItem(
                label = "Настройки",
                icon = Icons.Default.Settings,
                isActive = selectedTab == "settings",
                onClick = { onTabSelected("settings") },
                testTag = "tab_settings"
            )
        }
    }
}

@Composable
fun RowScope.BottomTabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .sleekTvFocus(shape = RoundedCornerShape(16.dp), scaleAmount = 1.05f, onEnter = onClick)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(if (isActive) PrimaryContainer else Color.Transparent)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else GreyText,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun SleekTvNavigationRail(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    isDark: Boolean,
    isTvOptimized: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(88.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // App logo or accent
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Scrollable menu items
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                Triple("home", Icons.Default.Home, "Главная"),
                Triple("explore", Icons.Default.Explore, "Обзор"),
                Triple("recents", Icons.Default.History, "Недавние"),
                Triple("downloads", Icons.Default.Download, "Загрузки"),
                Triple("library", Icons.Default.Favorite, "Избранное"),
                Triple("settings", Icons.Default.Settings, "Настройки")
            ).forEach { (tabId, icon, label) ->
                val isActive = selectedTab == tabId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .sleekTvFocus(shape = RoundedCornerShape(10.dp), scaleAmount = 1.05f, onEnter = { onTabSelected(tabId) })
                        .fillMaxWidth()
                        .clickable { onTabSelected(tabId) }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) PrimaryContainer else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else GreyText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) MaterialTheme.colorScheme.onBackground else GreyText,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val context = LocalContext.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .sleekTvFocus(shape = RoundedCornerShape(10.dp), scaleAmount = 1.05f, onEnter = { context.findActivity()?.finishAndRemoveTask() })
                .fillMaxWidth()
                .clickable { context.findActivity()?.finishAndRemoveTask() }
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Red.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Выход",
                    tint = Color.Red,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Выход",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red,
                maxLines = 1
            )
        }
    }
}

