import sys

file_path = "app/src/main/java/com/example/ui/screens/HomeTabScreen.kt"
with open(file_path, "r") as f:
    content = f.read()

start_idx = content.find("        val groupedCatalogItems = remember(currentVideos) {")
end_idx = content.find("        }", start_idx) + 9

if start_idx == -1 or end_idx == -1:
    print("Could not find groupedCatalogItems block")
    sys.exit(1)

content = content[:start_idx] + "        val groupedCatalogItems = remember(currentVideos) { com.example.utils.VideoLayoutHelper.groupCatalogItems(currentVideos) }\n" + content[end_idx:]

start_idx2 = content.find("        val folderItemsToRender = remember(currentVideos) {")
end_idx2 = content.find("        }", start_idx2) + 9

if start_idx2 == -1 or end_idx2 == -1:
    print("Could not find folderItemsToRender block")
    sys.exit(1)

content = content[:start_idx2] + "        val folderItemsToRender = remember(currentVideos) { com.example.utils.VideoLayoutHelper.groupFolderItems(currentVideos) }\n" + content[end_idx2:]

with open(file_path, "w") as f:
    f.write(content)

