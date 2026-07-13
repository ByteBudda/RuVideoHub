package com.example.manager

import com.example.data.VideoRepository
import com.example.data.SavedVideo
import com.example.ui.theme.CustomTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupRestoreManager(
    private val repository: VideoRepository,
    private val settingsManager: SettingsManager,
    private val libraryManager: LibraryManager
) {
    fun exportBackupToJson(): String {
        try {
            val root = JSONObject()

            // 1. Settings
            val settingsObj = JSONObject()
            settingsObj.put("is_dark_theme", settingsManager.isDarkTheme.value)
            settingsObj.put("is_tv_optimized", settingsManager.isTvOptimized.value)
            settingsObj.put("is_large_cards_mode", settingsManager.isLargeCardsMode.value)
            settingsObj.put("start_page_type", settingsManager.startPageType.value)
            settingsObj.put("start_page_category", settingsManager.startPageCategory.value)
            settingsObj.put("start_page_custom_url", settingsManager.startPageCustomUrl.value)
            settingsObj.put("player_quality", settingsManager.playerQuality.value)
            settingsObj.put("download_quality", settingsManager.downloadQuality.value)
            settingsObj.put("tv_grid_columns", settingsManager.tvGridColumns.value)
            settingsObj.put("mobile_grid_columns", settingsManager.mobileGridColumns.value)
            settingsObj.put("focus_style", settingsManager.focusStyle.value)
            settingsObj.put("app_theme", settingsManager.appTheme.value)
            root.put("settings", settingsObj)

            // 1b. Custom Themes
            val customThemesArray = JSONArray()
            for (theme in settingsManager.customThemes.value) {
                customThemesArray.put(JSONObject(theme.toJsonString()))
            }
            root.put("custom_themes", customThemesArray)

            // 2. Bookmarks
            val bookmarksArray = JSONArray()
            for (video in libraryManager.bookmarkedVideos.value) {
                val obj = JSONObject()
                obj.put("id", video.id)
                obj.put("title", video.title)
                obj.put("channel", video.channel)
                obj.put("views", video.views)
                obj.put("timeAgo", video.timeAgo)
                obj.put("duration", video.duration)
                obj.put("isPro", video.isPro)
                obj.put("category", video.category)
                obj.put("thumbnailUrl", video.thumbnailUrl ?: "")
                obj.put("savedAt", video.savedAt)
                bookmarksArray.put(obj)
            }
            root.put("bookmarks", bookmarksArray)

            // 3. Recents
            val recentsArray = JSONArray()
            for (video in libraryManager.recentVideos.value) {
                val obj = JSONObject()
                obj.put("id", video.id)
                obj.put("title", video.title)
                obj.put("channel", video.channel)
                obj.put("views", video.views)
                obj.put("timeAgo", video.timeAgo)
                obj.put("duration", video.duration)
                obj.put("isPro", video.isPro)
                obj.put("category", video.category)
                obj.put("thumbnailUrl", video.thumbnailUrl ?: "")
                obj.put("savedAt", video.savedAt)
                recentsArray.put(obj)
            }
            root.put("recents", recentsArray)

            return root.toString(4)
        } catch (e: Exception) {
            e.printStackTrace()
            return "{}"
        }
    }

    suspend fun importBackupFromJson(jsonStr: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmed = jsonStr.trim()
            if (trimmed.isBlank()) return@withContext Result.failure(Exception("Пустая строка"))
            
            val root = JSONObject(trimmed)
            
            // 1. Settings
            val settingsObj = root.optJSONObject("settings")
            var importedSettingsCount = 0
            if (settingsObj != null) {
                if (settingsObj.has("is_dark_theme")) {
                    val dark = settingsObj.getBoolean("is_dark_theme")
                    if (dark != settingsManager.isDarkTheme.value) settingsManager.toggleTheme()
                    importedSettingsCount++
                }
                if (settingsObj.has("is_tv_optimized")) {
                    val tv = settingsObj.getBoolean("is_tv_optimized")
                    if (tv != settingsManager.isTvOptimized.value) settingsManager.toggleTvOptimized()
                    importedSettingsCount++
                }
                if (settingsObj.has("is_large_cards_mode")) {
                    val large = settingsObj.getBoolean("is_large_cards_mode")
                    if (large != settingsManager.isLargeCardsMode.value) settingsManager.toggleLargeCardsMode()
                    importedSettingsCount++
                }
                if (settingsObj.has("start_page_type")) {
                    settingsManager.setStartPageType(settingsObj.getString("start_page_type"))
                    importedSettingsCount++
                }
                if (settingsObj.has("start_page_category")) {
                    settingsManager.setStartPageCategory(settingsObj.getString("start_page_category"))
                    importedSettingsCount++
                }
                if (settingsObj.has("start_page_custom_url")) {
                    settingsManager.setStartPageCustomUrl(settingsObj.getString("start_page_custom_url"))
                    importedSettingsCount++
                }
                if (settingsObj.has("player_quality")) {
                    settingsManager.setPlayerQuality(settingsObj.getString("player_quality"))
                    importedSettingsCount++
                }
                if (settingsObj.has("download_quality")) {
                    settingsManager.setDownloadQuality(settingsObj.getString("download_quality"))
                    importedSettingsCount++
                }
                if (settingsObj.has("tv_grid_columns")) {
                    settingsManager.setTvGridColumns(settingsObj.getInt("tv_grid_columns"))
                    importedSettingsCount++
                }
                if (settingsObj.has("mobile_grid_columns")) {
                    settingsManager.setMobileGridColumns(settingsObj.getInt("mobile_grid_columns"))
                    importedSettingsCount++
                }
                if (settingsObj.has("focus_style")) {
                    settingsManager.setFocusStyle(settingsObj.getString("focus_style"))
                    importedSettingsCount++
                }
                if (settingsObj.has("app_theme")) {
                    settingsManager.setAppTheme(settingsObj.getString("app_theme"))
                    importedSettingsCount++
                }
            }

            // 1b. Custom Themes
            val customThemesArray = root.optJSONArray("custom_themes")
            var importedThemesCount = 0
            if (customThemesArray != null) {
                for (i in 0 until customThemesArray.length()) {
                    try {
                        val themeObj = customThemesArray.getJSONObject(i)
                        val theme = CustomTheme.fromJson(themeObj.toString())
                        settingsManager.addCustomTheme(theme)
                        importedThemesCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 2. Bookmarks
            val bookmarksArray = root.optJSONArray("bookmarks")
            var importedBookmarksCount = 0
            if (bookmarksArray != null) {
                for (i in 0 until bookmarksArray.length()) {
                    val obj = bookmarksArray.getJSONObject(i)
                    val id = obj.optString("id") ?: continue
                    if (id.isBlank()) continue
                    
                    val existing = repository.getVideoById(id)
                    val imported = SavedVideo(
                        id = id,
                        title = obj.optString("title", "Без названия"),
                        channel = obj.optString("channel", "Rutube"),
                        views = obj.optString("views", ""),
                        timeAgo = obj.optString("timeAgo", ""),
                        duration = obj.optString("duration", "00:00"),
                        isPro = obj.optBoolean("isPro", false),
                        category = obj.optString("category", "Разное"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        isBookmarked = true,
                        isDownloaded = existing?.isDownloaded ?: false,
                        isWatched = existing?.isWatched ?: false,
                        savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                    )
                    repository.insertOrUpdate(imported)
                    importedBookmarksCount++
                }
            }

            // 3. Recents
            val recentsArray = root.optJSONArray("recents")
            var importedRecentsCount = 0
            if (recentsArray != null) {
                for (i in 0 until recentsArray.length()) {
                    val obj = recentsArray.getJSONObject(i)
                    val id = obj.optString("id") ?: continue
                    if (id.isBlank()) continue
                    
                    val existing = repository.getVideoById(id)
                    val imported = SavedVideo(
                        id = id,
                        title = obj.optString("title", "Без названия"),
                        channel = obj.optString("channel", "Rutube"),
                        views = obj.optString("views", ""),
                        timeAgo = obj.optString("timeAgo", ""),
                        duration = obj.optString("duration", "00:00"),
                        isPro = obj.optBoolean("isPro", false),
                        category = obj.optString("category", "Разное"),
                        thumbnailUrl = obj.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                        isBookmarked = existing?.isBookmarked ?: false,
                        isDownloaded = existing?.isDownloaded ?: false,
                        isWatched = true,
                        savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                    )
                    repository.insertOrUpdate(imported)
                    importedRecentsCount++
                }
            }

            val customThemeMsg = if (importedThemesCount > 0) ", тем - $importedThemesCount" else ""
            Result.success("Импортировано: настроек - $importedSettingsCount, закладок - $importedBookmarksCount, истории - $importedRecentsCount$customThemeMsg")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
