package com.nayama.music.model

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val releaseYear: String,
    val artworkUrl: String? = null,
    val trackCount: Int = 0
)

