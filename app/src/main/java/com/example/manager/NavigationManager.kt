package com.example.manager

import com.example.data.Video
import com.example.data.rutube.SmartRutubeParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Stack

data class NavigationSnapshot(
    val tab: String,
    val category: String,
    val feedTab: SmartRutubeParser.TabInfo?,
    val subfolderName: String?,
    val searchQuery: String,
    val selectedVideo: Video?,
    val isChannelView: Boolean = false,
    val currentChannelVideo: Video? = null,
    val channelVideos: List<Video> = emptyList(),
    val channelPlaylists: List<Video> = emptyList(),
    val channelActiveTab: String = "Видео",
    val dynamicVideos: List<Video> = emptyList(),
    val currentPage: Int = 1,
    val isEndReached: Boolean = false,
    val currentQuery: String? = null,
    val currentCategory: String? = null,
    val currentActiveApiEndpoint: String? = null,
    val currentSubfolderVideo: Video? = null
)

class NavigationManager {
    private val _currentTab = MutableStateFlow("home")
    val currentTab = _currentTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Фильмы")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFeedTab = MutableStateFlow<SmartRutubeParser.TabInfo?>(null)
    val selectedFeedTab = _selectedFeedTab.asStateFlow()

    private val _selectedSubfolderName = MutableStateFlow<String?>(null)
    val selectedSubfolderName = _selectedSubfolderName.asStateFlow()

    private val _isChannelView = MutableStateFlow(false)
    val isChannelView = _isChannelView.asStateFlow()

    private val _isTvMiniFullscreen = MutableStateFlow(false)
    val isTvMiniFullscreen = _isTvMiniFullscreen.asStateFlow()

    private val _channelActiveTab = MutableStateFlow("Видео")
    val channelActiveTab = _channelActiveTab.asStateFlow()

    private val navHistory = Stack<NavigationSnapshot>()

    fun selectTab(tab: String) {
        if (_currentTab.value != tab) {
            _currentTab.value = tab
        }
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFeedTab(tab: SmartRutubeParser.TabInfo?) {
        _selectedFeedTab.value = tab
    }

    fun setSubfolderName(name: String?) {
        _selectedSubfolderName.value = name
    }

    fun setChannelView(isView: Boolean) {
        _isChannelView.value = isView
    }

    fun setTvMiniFullscreen(fullscreen: Boolean) {
        _isTvMiniFullscreen.value = fullscreen
    }

    fun setChannelActiveTab(tab: String) {
        _channelActiveTab.value = tab
    }

    fun pushToHistory(snapshot: NavigationSnapshot) {
        if (navHistory.isEmpty() || navHistory.peek() != snapshot) {
            navHistory.push(snapshot)
        }
    }

    fun canNavigateBack(): Boolean = !navHistory.isEmpty()

    fun navigateBack(): NavigationSnapshot? {
        return if (!navHistory.isEmpty()) navHistory.pop() else null
    }

    fun restoreFromSnapshot(snapshot: NavigationSnapshot) {
        _currentTab.value = snapshot.tab
        _selectedCategory.value = snapshot.category
        _selectedFeedTab.value = snapshot.feedTab
        _selectedSubfolderName.value = snapshot.subfolderName
        _searchQuery.value = snapshot.searchQuery
        _isChannelView.value = snapshot.isChannelView
        _channelActiveTab.value = snapshot.channelActiveTab
    }

    fun clearHistory() {
        navHistory.clear()
    }

    fun isHistoryEmpty(): Boolean = navHistory.isEmpty()
}
