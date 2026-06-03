package com.example.data

data class SearchChannel(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val subscribers: String,
    val description: String,
    val isSubscribed: Boolean = false
)
