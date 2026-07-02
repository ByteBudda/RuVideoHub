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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.sleekTvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    focusColor: Color = MaterialTheme.colorScheme.primary,
    onEnter: (() -> Unit)? = null
): Modifier = this.composed {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { state ->
            isFocused = state.isFocused
            if (state.isFocused) {
                scope.launch { bringIntoViewRequester.bringIntoView() }
            }
        }
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && 
                (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter)
            ) {
                onEnter?.invoke()
                true
            } else {
                false
            }
        }
        .focusable()
        .then(
            if (isFocused) {
                Modifier.border(2.dp, focusColor.copy(alpha = 0.8f), shape)
            } else {
                Modifier
            }
        )
}

@Composable
fun SleekVideoHubApp(
    viewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val currentSelectedVideo by viewModel.currentSelectedVideo.collectAsStateWithLifecycle()
    val isTermsAgreed by viewModel.isTermsAgreed.collectAsStateWithLifecycle()

    val context = LocalContext.current

    if (!isTermsAgreed) {
        TermsAgreementScreen(
            onAgree = { viewModel.agreeToTerms() },
            onDecline = { context.findActivity()?.finish() }
        )
        return
    }

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

    AmbientGlassBackground(isDark = isDarkTheme, isTvOptimized = isTvOptimized, modifier = modifier) {
        if (isTvOptimized) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (currentSelectedVideo == null || currentTab == "tv_mini") {
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
                            "tv_mini" -> TvMiniPlayerScreen(viewModel = viewModel)
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
            visible = currentSelectedVideo != null && currentTab != "tv_mini",
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
                SleekPlayerDetailOverlay(
                    video = video,
                    viewModel = viewModel,
                    onDismiss = { viewModel.selectVideo(null) }
                )
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
            .height(68.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDark) {
                        listOf(
                            Color(0xCC221F2E),
                            Color(0xD9161421)
                        )
                    } else {
                        listOf(
                            Color(0xF2FFFFFF),
                            Color(0xE6EAE6F3)
                        )
                    }
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
            .sleekTvFocus(shape = RoundedCornerShape(16.dp), onEnter = onClick)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(if (isActive) PrimaryContainer else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else GreyText,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) MaterialTheme.colorScheme.onBackground else GreyText
        )
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
            .background(if (isDark) Color(0xFF14131A) else Color(0xFFF0EDF5))
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
                Triple("tv_mini", Icons.Default.Tv, "ТВ Плеер"),
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
                        .fillMaxWidth()
                        .sleekTvFocus(shape = RoundedCornerShape(10.dp), onEnter = { onTabSelected(tabId) })
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
    }
}

