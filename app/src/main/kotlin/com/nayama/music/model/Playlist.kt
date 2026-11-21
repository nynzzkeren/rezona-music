package com.nayama.music.model

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val trackCount: Int,
    val artworkUrl: String? = null,
    val isUserCreated: Boolean = false
)

