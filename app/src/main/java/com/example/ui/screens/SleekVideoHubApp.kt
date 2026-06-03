package com.example.sleekvideohub.ui

import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.pm.ActivityInfo
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioWidget
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * 1. СОВРЕМЕННЫЙ И БЕЗОПАСНЫЙ ПЛЕЕР НА MEDIA3 (EXOPLAYER)
 * Никаких бесконечных релейаутов и проблем с масштабированием HLS.
 */
@Composable
fun SleekMedia3Player(
    videoUrl: String,
    isPlaying: Boolean,
    playbackProgress: Float,
    onProgressChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioWidget.RESIZE_MODE_FIT
) {
    val context = LocalContext.current

    // Инициализируем ExoPlayer один раз за жизненный цикл компонента
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = isPlaying
        }
    }

    // Следим за изменением URL видео
    LaunchedEffect(videoUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        exoPlayer.prepare()
    }

    // Синхронизируем состояние паузы/воспроизведения
    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    // Трэкинг прогресса (аккуратный опрос раз в полсекунды)
    LaunchedEffect(isPlaying, videoUrl) {
        while (isPlaying) {
            val duration = exoPlayer.duration.coerceAtLeast(1L)
            val current = exoPlayer.currentPosition
            onProgressChanged(current.toFloat() / duration.toFloat())
            delay(500)
        }
    }

    // Освобождаем ресурсы плеера при уничтожении Composable-элемента
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Нативный контейнер для отображения видео
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // Используем свой кастомный UI-слой поверх
                setResizeMode(resizeMode)
            }
        },
        update = { playerView ->
            if (playerView.resizeMode != resizeMode) {
                playerView.setResizeMode(resizeMode)
            }
        },
        modifier = modifier
    )
}

/**
 * 2. ЭКРАН БИБЛИОТЕКИ С БЕЗОПАСНЫМ WEBVIEW
 * Исправлена утечка памяти через явный метод onDestroy в блоке onRelease.
 */
@Composable
fun LibraryTabScreen(
    showAuthDialog: Boolean,
    onAuthSuccess: (String, String) -> Unit,
    onCloseDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Внешний модификатор идет строго первым
    Box(modifier = modifier.fillMaxSize()) {
        if (showAuthDialog) {
            AlertDialog(
                onDismissRequest = onCloseDialog,
                title = { Text("Авторизация через Rutube") },
                text = {
                    Box(modifier = Modifier.height(400.dp).fillMaxWidth()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    
                                    webViewClient = object : android.webkit.WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                                            if (!cookies.isNullOrEmpty()) {
                                                val sessionid = extractCookie(cookies, "sessionid")
                                                val csrftoken = extractCookie(cookies, "csrftoken")
                                                if (sessionid != null && csrftoken != null) {
                                                    onAuthSuccess(sessionid, csrftoken)
                                                }
                                            }
                                        }
                                    }
                                    loadUrl("https://rutube.ru/user/login/")
                                }
                            },
                            // Блок onRelease гарантирует, что WebView не останется в памяти после закрытия диалога
                            onRelease = { webView ->
                                webView.stopLoading()
                                webView.webViewClient = null
                                webView.clearHistory()
                                webView.destroy()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCloseDialog) { Text("Закрыть") }
                }
            )
        }
    }
}

/**
 * Helper для безопасного парсинга кук
 */
private fun extractCookie(cookieString: String, cookieName: String): String? {
    return cookieString.split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("$cookieName=") }
        ?.substringAfter("=")
}

/**
 * 3. КОРРЕКТНЫЙ ИММЕРСИВНЫЙ РЕЖИМ (FULLSCREEN)
 * С использованием WindowInsetsControllerCompat и восстановлением ориентации.
 */
@Composable
fun FullscreenPlayerWrapper(
    videoUrl: String,
    isPlaying: Boolean,
    onCloseFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? android.app.Activity }

    DisposableEffect(Unit) {
        activity?.let { act ->
            // Фиксируем landscape
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            
            // Прячем системные бары (Статус-бар и Навигейшн)
            val window = act.window
            val decorView = window.decorView
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        onDispose {
            activity?.let { act ->
                // Возвращаем portrait при выходе из полноэкранного режима
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                
                // Возвращаем системные бары обратно
                val window = act.window
                val decorView = window.decorView
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {
        var progress by remember { mutableStateOf(0f) }
        
        SleekMedia3Player(
            videoUrl = videoUrl,
            isPlaying = isPlaying,
            playbackProgress = progress,
            onProgressChanged = { progress = it },
            resizeMode = AspectRatioWidget.RESIZE_MODE_ZOOM,
            modifier = Modifier.fillMaxSize()
        )
        
        // Тут можно нарисовать кастомный оверлей управления плеером (кнопка назад, пауза, слайдер)
    }
}

/**
 * 4. СПИСОК С ПЛЕЙСХОЛДЕРАМИ ДЛЯ COIL (БЕЗ ДЕРГАНИЙ) И БЕЗОПАСНЫЙ INFINITE SCROLL
 */
@Composable
fun VideoFeedList(
    videos: List<String>, // Для примера просто строки урлов картинок
    onLoadMore: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Оптимизированный триггер пагинации
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf false
            // Триггеримся за 3 элемента до конца списка
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore, isLoading) {
        if (shouldLoadMore && !isLoading) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        items(videos) { videoThumbnailUrl ->
            AsyncImage(
                model = videoThumbnailUrl,
                contentDescription = "Обложка видео",
                // Избегаем прыжков интерфейса: задаем фиксированный аспект и цвет-заглушку
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
