package com.example.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Менеджер для управления видео-плагинами
 */
object PluginManager {
    
    private val plugins = mutableListOf<VideoPlugin>()
    
    init {
        // Регистрируем плагины
        registerPlugin(VKVideoPlugin())
        registerPlugin(DzenPlugin())
    }
    
    fun registerPlugin(plugin: VideoPlugin) {
        plugins.add(plugin)
    }
    
    fun getPlugins(): List<VideoPlugin> = plugins
    
    fun getPluginForUrl(url: String): VideoPlugin? {
        return plugins.firstOrNull { it.isSupported(url) }
    }
    
    suspend fun searchAll(query: String, limit: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VideoItem>()
        for (plugin in plugins) {
            try {
                results.addAll(plugin.search(query, limit))
            } catch (e: Exception) {
                // Логируем ошибку, но продолжаем с другими плагинами
                android.util.Log.e("PluginManager", "Error in ${plugin.name}: ${e.message}")
            }
        }
        return@withContext results
    }
    
    suspend fun resolveStream(url: String, audioOnly: Boolean = false): StreamInfo? = withContext(Dispatchers.IO) {
        val plugin = getPluginForUrl(url) ?: return@withContext null
        return@withContext plugin.resolveStream(url, audioOnly)
    }
    
    suspend fun getVideoInfo(url: String): VideoItem? = withContext(Dispatchers.IO) {
        val plugin = getPluginForUrl(url) ?: return@withContext null
        return@withContext plugin.getVideoInfo(url)
    }
}
