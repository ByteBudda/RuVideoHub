import sys

file_path = "app/src/main/java/com/example/ui/screens/SleekPlayerDetailOverlay.kt"
with open(file_path, "r") as f:
    content = f.read()

# Replace first chunk
start_idx = content.find("Column(\n                modifier = Modifier\n                    .weight(1f)\n                    .fillMaxHeight()\n                    .verticalScroll(rememberScrollState())")
if start_idx == -1:
    print("Could not find first block")
    sys.exit(1)
end_idx = content.find("    } else {", start_idx)
if end_idx == -1:
    print("Could not find end of first block")
    sys.exit(1)

# Ensure to leave the `    } else {` part alone.
# Notice that `    } else {` has a corresponding closing brace earlier.
# The `Column` block ends before `    } else {`. Wait, `    } else {` is the `if (isLandscape && !isFullscreen)` else branch.
chunk1 = """Column(
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
"""

content = content[:start_idx] + chunk1 + content[end_idx:]


start_idx2 = content.find("            // Detail descriptions and channels metadata")
if start_idx2 == -1:
    print("Could not find second block")
    sys.exit(1)

end_idx2 = content.find("        if (showDownloadOptionsDialog) {", start_idx2)
if end_idx2 == -1:
    print("Could not find end of second block")
    sys.exit(1)

# Need to preserve the spacing / closing braces before `if (show...)`
chunk2 = """            // Detail descriptions and channels metadata
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
"""

# Let's verify end_idx2 has a `        }` before `        if (showDownload...`
# We will just replace start_idx2 to end_idx2.
content = content[:start_idx2] + chunk2 + content[end_idx2:]

with open(file_path, "w") as f:
    f.write(content)

