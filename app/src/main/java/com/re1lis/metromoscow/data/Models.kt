package com.re1lis.metromoscow.data

import androidx.compose.ui.graphics.Color

data class MetroLine(
    val id: String,
    val name: String,
    val number: String,
    val color: Long,
    val lengthKm: String,
    val travelTime: String,
    val stations: List<Station>
) {
    val composeColor: Color get() = Color(color)
}

data class Station(
    val id: String,
    val name: String,
    val openingYear: Int,
    val depth: String?
)

data class StationProgress(
    val visited: Boolean = false,
    val favorite: Boolean = false,
    val visitedAt: String? = null,
    val note: String = ""
)

data class Train(
    val id: String,
    val name: String,
    val year: Int,
    val description: String,
    val images: List<Int>
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0,
    val reduceMotion: Boolean = false,
    val confirmUnvisit: Boolean = true
)

enum class StationFilter { ALL, VISITED, UNVISITED, FAVORITES }
