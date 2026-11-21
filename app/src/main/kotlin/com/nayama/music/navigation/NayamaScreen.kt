package com.nayama.music.navigation

import androidx.annotation.DrawableRes
import com.nayama.music.R

enum class NayamaScreen(
    val route: String,
    val title: String,
    val iconSymbol: String,
    @param:DrawableRes val iconResId: Int
) {
    Home("home", "Home", "⌂", R.drawable.ic_nav_home),
    Explore("explore", "Explore", "✦", R.drawable.ic_nav_explore),
    Search("search", "Search", "⚲", R.drawable.ic_nav_search),
    Library("library", "Library", "☰", R.drawable.ic_nav_library),
    Profile("profile", "Profile", "●", R.drawable.ic_nav_profile);

    companion object {
        // Matches the 3-tab layout from reference video & photos: Home, Search, Library
        val bottomNavItems: List<NayamaScreen> = listOf(Home, Search, Library)
    }
}
