package com.nayama.music.navigation

import androidx.annotation.DrawableRes
import com.nayama.music.R

/**
 * Feature flags for legacy/hidden surfaces.
 */
object RezonaFeatureFlags {
    const val ENABLE_STUDIO_IN_YOU = true
}

/**
 * The 4 Primary Navigation Tabs for Rezona UI V2:
 * Home, Search, Library, You (Studio moved behind feature flag in You > Sound Lab).
 */
enum class RezonaTab(
    val id: String,
    val title: String,
    @param:DrawableRes val iconRes: Int
) {
    Home("home", "Home", R.drawable.ic_rezona_home),
    Search("search", "Search", R.drawable.ic_rezona_search),
    Library("library", "Library", R.drawable.ic_rezona_library),
    You("you", "You", R.drawable.ic_rezona_you);

    companion object {
        val allTabs: List<RezonaTab> = listOf(Home, Search, Library, You)
    }
}
