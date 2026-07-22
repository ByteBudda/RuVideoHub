package com.example.di

import com.example.data.AppDatabase
import com.example.data.VideoRepository
import com.example.manager.*
import com.example.viewmodel.VideoViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val appModule = module {
    // Database
    single { AppDatabase.getDatabase(androidApplication()) }
    single { get<AppDatabase>().savedVideoDao() }

    // Repository
    single { VideoRepository(get()) }

    // Managers that are Singletons (no CoroutineScope parameter required)
    single { SettingsManager(androidApplication()) }
    single { NavigationManager() }
    single { PlayerManager() }

    // Managers that depend on CoroutineScope
    factory { (scope: CoroutineScope) -> LibraryManager(get(), scope) }
    factory { (scope: CoroutineScope) -> DownloadManager(androidApplication(), get(), scope) }
    factory { (scope: CoroutineScope) -> RutubeMediaResolver(get(), scope) }
    factory { (scope: CoroutineScope, resolver: RutubeMediaResolver) ->
        RutubeFeedManager(
            repository = get(),
            playerManager = get(),
            navigationManager = get(),
            settingsManager = get(),
            mediaResolver = resolver,
            coroutineScope = scope
        )
    }

    // BackupRestoreManager (depends on LibraryManager which needs scope)
    factory { (libraryManager: LibraryManager) ->
        BackupRestoreManager(get(), get(), libraryManager)
    }

    // ViewModel
    viewModel {
        VideoViewModel(
            application = androidApplication(),
            repository = get(),
            settingsManager = get(),
            navigationManager = get(),
            playerManager = get(),
            libraryManagerFactory = { scope -> get { parametersOf(scope) } },
            downloadManagerFactory = { scope -> get { parametersOf(scope) } },
            backupRestoreManagerFactory = { libManager -> get { parametersOf(libManager) } },
            mediaResolverFactory = { scope -> get { parametersOf(scope) } },
            feedManagerFactory = { scope, resolver -> get { parametersOf(scope, resolver) } }
        )
    }
}
