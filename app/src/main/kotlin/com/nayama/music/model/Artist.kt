package com.nayama.music.model

data class Artist(
    val id: String,
    val name: String,
    val genre: String,
    val monthlyListeners: String,
    val avatarUrl: String? = null
)

