package com.example

import android.app.Application
import android.os.Build
import org.conscrypt.Conscrypt
import java.security.Security

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Fix for Android 7 and older devices with outdated SSL certificates
        if (Build.VERSION.SDK_INT <= 28) {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
                android.util.Log.d("MyApplication", "Conscrypt provider installed successfully.")
            } catch (e: Exception) {
                android.util.Log.e("MyApplication", "Failed to install Conscrypt provider", e)
            }
        }
    }
}
