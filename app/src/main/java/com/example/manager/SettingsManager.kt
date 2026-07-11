package com.example.manager

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.app.UiModeManager
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(private val application: Application) {
    private val sharedPrefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _appTheme = MutableStateFlow("dark")
    val appTheme = _appTheme.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme = _isDarkTheme.asStateFlow()

    private val _isTvOptimized = MutableStateFlow(false)
    val isTvOptimized = _isTvOptimized.asStateFlow()

    private val _isLargeCardsMode = MutableStateFlow(false)
    val isLargeCardsMode = _isLargeCardsMode.asStateFlow()

    private val _isTermsAgreed = MutableStateFlow(false)
    val isTermsAgreed = _isTermsAgreed.asStateFlow()

    private val _startPageType = MutableStateFlow("default")
    val startPageType = _startPageType.asStateFlow()

    private val _startPageCategory = MutableStateFlow("Фильмы")
    val startPageCategory = _startPageCategory.asStateFlow()

    private val _startPageCustomUrl = MutableStateFlow("")
    val startPageCustomUrl = _startPageCustomUrl.asStateFlow()

    private val _startPageFavoriteId = MutableStateFlow("")
    val startPageFavoriteId = _startPageFavoriteId.asStateFlow()

    private val _startPageFavoriteTitle = MutableStateFlow("")
    val startPageFavoriteTitle = _startPageFavoriteTitle.asStateFlow()

    private val _playerQuality = MutableStateFlow("Авто")
    val playerQuality = _playerQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow("720p")
    val downloadQuality = _downloadQuality.asStateFlow()

    private val _tvGridColumns = MutableStateFlow(4)
    val tvGridColumns = _tvGridColumns.asStateFlow()

    private val _tvVideoGridColumns = MutableStateFlow(4)
    val tvVideoGridColumns = _tvVideoGridColumns.asStateFlow()

    private val _mobileGridColumns = MutableStateFlow(2)
    val mobileGridColumns = _mobileGridColumns.asStateFlow()

    private val _focusStyle = MutableStateFlow("glow")
    val focusStyle = _focusStyle.asStateFlow()

    init {
        val savedTheme = sharedPrefs.getString("app_theme", null)
        if (savedTheme != null) {
            _appTheme.value = savedTheme
            _isDarkTheme.value = (savedTheme == "dark" || savedTheme == "slate")
        } else {
            val legacyIsDark = sharedPrefs.getBoolean("is_dark_theme", true)
            _appTheme.value = if (legacyIsDark) "dark" else "light"
            _isDarkTheme.value = legacyIsDark
        }
        
        _isTermsAgreed.value = sharedPrefs.getBoolean("terms_agreed", false)

        val uiModeManager = application.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isDeviceTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                application.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        _isTvOptimized.value = sharedPrefs.getBoolean("is_tv_optimized", isDeviceTv)
        _isLargeCardsMode.value = sharedPrefs.getBoolean("is_large_cards_mode", false)
        _playerQuality.value = sharedPrefs.getString("player_quality", "Авто") ?: "Авто"
        _downloadQuality.value = sharedPrefs.getString("download_quality", "720p") ?: "720p"

        _startPageType.value = sharedPrefs.getString("start_page_type", "default") ?: "default"
        _startPageCategory.value = sharedPrefs.getString("start_page_category", "Фильмы") ?: "Фильмы"
        _startPageCustomUrl.value = sharedPrefs.getString("start_page_custom_url", "") ?: ""
        _startPageFavoriteId.value = sharedPrefs.getString("start_page_favorite_id", "") ?: ""
        _startPageFavoriteTitle.value = sharedPrefs.getString("start_page_favorite_title", "") ?: ""

        _tvGridColumns.value = sharedPrefs.getInt("tv_grid_columns", 4)
        _tvVideoGridColumns.value = sharedPrefs.getInt("tv_video_grid_columns", 4)
        _mobileGridColumns.value = sharedPrefs.getInt("mobile_grid_columns", 2)
        _focusStyle.value = sharedPrefs.getString("focus_style", "glow") ?: "glow"
    }

    fun toggleTheme() {
        // Toggle is now handled by setAppTheme, but we keep this for compatibility if needed.
        val nextTheme = if (_isDarkTheme.value) "light" else "dark"
        setAppTheme(nextTheme)
    }

    fun setAppTheme(theme: String) {
        _appTheme.value = theme
        val isDark = (theme == "dark" || theme == "slate")
        _isDarkTheme.value = isDark
        sharedPrefs.edit()
            .putString("app_theme", theme)
            .putBoolean("is_dark_theme", isDark)
            .apply()
    }

    fun toggleTvOptimized() {
        val newValue = !_isTvOptimized.value
        _isTvOptimized.value = newValue
        sharedPrefs.edit().putBoolean("is_tv_optimized", newValue).apply()
    }

    fun toggleLargeCardsMode() {
        val newValue = !_isLargeCardsMode.value
        _isLargeCardsMode.value = newValue
        sharedPrefs.edit().putBoolean("is_large_cards_mode", newValue).apply()
    }

    fun agreeToTerms() {
        _isTermsAgreed.value = true
        sharedPrefs.edit().putBoolean("terms_agreed", true).apply()
    }

    fun setStartPageType(type: String) {
        _startPageType.value = type
        sharedPrefs.edit().putString("start_page_type", type).apply()
    }

    fun setStartPageCategory(category: String) {
        _startPageCategory.value = category
        sharedPrefs.edit().putString("start_page_category", category).apply()
    }

    fun setStartPageCustomUrl(url: String) {
        _startPageCustomUrl.value = url
        sharedPrefs.edit().putString("start_page_custom_url", url).apply()
    }

    fun setStartPageFavorite(id: String, title: String) {
        _startPageFavoriteId.value = id
        _startPageFavoriteTitle.value = title
        sharedPrefs.edit()
            .putString("start_page_favorite_id", id)
            .putString("start_page_favorite_title", title)
            .apply()
    }

    fun setPlayerQuality(quality: String) {
        _playerQuality.value = quality
        sharedPrefs.edit().putString("player_quality", quality).apply()
    }

    fun setDownloadQuality(quality: String) {
        _downloadQuality.value = quality
        sharedPrefs.edit().putString("download_quality", quality).apply()
    }

    fun setTvGridColumns(cols: Int) {
        _tvGridColumns.value = cols
        sharedPrefs.edit().putInt("tv_grid_columns", cols).apply()
    }

    fun setTvVideoGridColumns(cols: Int) {
        _tvVideoGridColumns.value = cols
        sharedPrefs.edit().putInt("tv_video_grid_columns", cols).apply()
    }

    fun setMobileGridColumns(cols: Int) {
        _mobileGridColumns.value = cols
        sharedPrefs.edit().putInt("mobile_grid_columns", cols).apply()
    }

    fun setFocusStyle(style: String) {
        _focusStyle.value = style
        sharedPrefs.edit().putString("focus_style", style).apply()
    }
}
