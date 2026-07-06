import re
with open("app/src/main/java/com/example/ui/screens/SleekPlayerDetailOverlay.kt", "r") as f:
    lines = f.readlines()

for i, l in enumerate(lines):
    if "if (isLandscape && !isFullscreen) {" in l:
        print("Landscape if is at line", i)
    if "fun SleekPlayerDetailOverlay(" in l:
        print("Function starts at line", i)

