package com.example.pluginapi

import androidx.annotation.Keep

@Keep
data class CatalogCategory(
    val id: String,
    val title: String,
    val description: String = ""
)