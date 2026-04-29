package com.slumdog88.dictationkeyboardai.navigation

import com.slumdog88.dictationkeyboardai.R

sealed class Screen(val route: String, val label: String, val icon: Int) {
    object Settings : Screen("settings", "HOME", R.drawable.ic_home)
    object History : Screen("history", "HISTORY", R.drawable.ic_history)
    object Notepad : Screen("notepad", "NOTEPAD", R.drawable.ic_edit_note)
    object HowToGuide : Screen("how_to_guide", "HOW-TO", R.drawable.ic_help)
}

val navigationItems = listOf(
    Screen.Settings,
    Screen.History,
    Screen.Notepad
)