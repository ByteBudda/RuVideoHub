import re
with open("app/src/main/java/com/example/ui/screens/SleekPlayerDetailOverlay.kt", "r") as f:
    text = f.read()

start_idx = text.find("    if (isLandscape && !isFullscreen) {")
if start_idx == -1:
    print("Not found start")
    exit(1)

# Find the end of the overlay function. It's probably the end of the file or near it.
# We know the dialog is at the end.
end_idx = text.find("    if (showDownloadOptionsDialog) {", start_idx)
if end_idx == -1:
    end_idx = text.find("        if (showDownloadOptionsDialog) {", start_idx)

if end_idx == -1:
    print("Not found dialog")
    exit(1)

print("Found block from", start_idx, "to", end_idx)

replacement = """    if (isLandscape && !isFullscreen) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .focusGroup()
        ) {
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
                    .focusGroup()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .testTag("dismiss_player")
                            .focusRequester(closeButtonFocusRequester)
                            .sleekTvFocus(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть плеер",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Text(
                        text = "${video.category} • Воспроизведение",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        letterSpacing = 0.5.sp
                    )

                    Box(modifier = Modifier.size(24.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlaying && isPlayerActive) {
                        RutubeVideoPlayer(
                            videoId = video.id,
                            viewModel = viewModel,
                            videoTitle = video.title,
                            aspectMode = selectedAspectRatio,
                            isFullscreen = false,
                            onToggleFullscreen = { isFullscreen = true },
                            onChangeAspectRatio = { selectedAspectRatio = it },
                            onShare = { shareVideo(context, video) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            VideoThumbnail(
                                id = video.id,
                                duration = video.duration,
                                thumbnailUrl = video.thumbnailUrl,
                                hasPlayOverlay = !isPlaying,
                                isPlaying = false,
                                onPlayClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isPlaying && !isPlayerActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = Primary,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Подготовка проигрывателя...",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .focusGroup()
            ) {
                PlayerDetailsPanel(
                    video = video,
                    viewModel = viewModel,
                    currentEpList = currentEpList,
                    context = context,
                    isDark = isDark,
                    isTvOptimized = isTvOptimized,
                    onDismiss = onDismiss,
                    showDownloadOptionsDialog = { showDownloadOptionsDialog = it }
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFullscreen) 0.dp else 56.dp)
             ) {
                if (!isFullscreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .testTag("dismiss_player")
                                .focusRequester(closeButtonFocusRequester)
                                .sleekTvFocus(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть плеер",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Text(
                            text = "${video.category} • Воспроизведение",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            letterSpacing = 0.5.sp
                        )

                        Box(modifier = Modifier.size(24.dp))
                    }
                }
            }

            Box(
                modifier = if (isFullscreen) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }.background(Color.Black)
            ) {
                if (isPlaying && isPlayerActive) {
                    RutubeVideoPlayer(
                        videoId = video.id,
                        viewModel = viewModel,
                        videoTitle = video.title,
                        aspectMode = selectedAspectRatio,
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = { isFullscreen = !isFullscreen },
                        onChangeAspectRatio = { selectedAspectRatio = it },
                        onShare = { shareVideo(context, video) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        VideoThumbnail(
                            id = video.id,
                            duration = video.duration,
                            thumbnailUrl = video.thumbnailUrl,
                            hasPlayOverlay = !isPlaying,
                            isPlaying = false,
                            onPlayClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isPlaying && !isPlayerActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = Primary,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Подготовка проигрывателя...",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isFullscreen) {
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .focusGroup()
                ) {
                    PlayerDetailsPanel(
                        video = video,
                        viewModel = viewModel,
                        currentEpList = currentEpList,
                        context = context,
                        isDark = isDark,
                        isTvOptimized = isTvOptimized,
                        onDismiss = onDismiss,
                        showDownloadOptionsDialog = { showDownloadOptionsDialog = it }
                    )
                }
            }
        }
    }

"""

new_text = text[:start_idx] + replacement + text[end_idx:]

with open("app/src/main/java/com/example/ui/screens/SleekPlayerDetailOverlay.kt", "w") as f:
    f.write(new_text)

print("Done replacing.")

