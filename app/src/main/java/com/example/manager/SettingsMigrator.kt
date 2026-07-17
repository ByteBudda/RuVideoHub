package com.example.manager

import android.content.SharedPreferences
import android.util.Log

class SettingsMigrator(private val sharedPrefs: SharedPreferences) {

    fun migrate() {
        val currentVersion = sharedPrefs.getInt("settings_version", 0)
        Log.d("SettingsMigrator", "Current settings version: $currentVersion")
        
        if (currentVersion < 1) {
            migrateToV1()
            sharedPrefs.edit().putInt("settings_version", 1).apply()
        }
        // Add future migrations here
    }

    private fun migrateToV1() {
        Log.d("SettingsMigrator", "Migrating to version 1")
        // Add specific migration logic if needed
    }
}
