package com.re1lis.metromoscow.data

import android.content.Context
import org.json.JSONObject

class MetroRepository(context: Context) {
    private val prefs = context.getSharedPreferences("metro_moscow", Context.MODE_PRIVATE)

    fun loadProgress(): Map<String, StationProgress> = runCatching {
        val root = JSONObject(prefs.getString("progress", "{}") ?: "{}")
        root.keys().asSequence().associateWith { id ->
            val value = root.getJSONObject(id)
            StationProgress(
                visited = value.optBoolean("visited"),
                favorite = value.optBoolean("favorite"),
                visitedAt = value.optString("visitedAt").takeIf { it.isNotBlank() },
                note = value.optString("note")
            )
        }
    }.getOrDefault(emptyMap())

    fun saveProgress(progress: Map<String, StationProgress>) {
        val root = JSONObject()
        progress.forEach { (id, value) ->
            if (value != StationProgress()) {
                root.put(id, JSONObject().apply {
                    put("visited", value.visited)
                    put("favorite", value.favorite)
                    put("visitedAt", value.visitedAt ?: "")
                    put("note", value.note)
                })
            }
        }
        prefs.edit().putString("progress", root.toString()).apply()
    }

    fun loadSettings(): UserSettings = UserSettings(
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM),
        accentIndex = prefs.getInt("accent", 0),
        reduceMotion = prefs.getBoolean("reduce_motion", false),
        confirmUnvisit = prefs.getBoolean("confirm_unvisit", true)
    )

    fun saveSettings(settings: UserSettings) {
        prefs.edit()
            .putString("theme", settings.themeMode.name)
            .putInt("accent", settings.accentIndex)
            .putBoolean("reduce_motion", settings.reduceMotion)
            .putBoolean("confirm_unvisit", settings.confirmUnvisit)
            .apply()
    }

    fun exportJson(progress: Map<String, StationProgress>, settings: UserSettings): String =
        JSONObject().apply {
            put("format", "metro-moscow-android")
            put("version", 1)
            put("progress", JSONObject().apply {
                progress.forEach { (id, value) ->
                    put(id, JSONObject().apply {
                        put("visited", value.visited)
                        put("favorite", value.favorite)
                        put("visitedAt", value.visitedAt ?: "")
                        put("note", value.note)
                    })
                }
            })
            put("settings", JSONObject().apply {
                put("theme", settings.themeMode.name)
                put("accent", settings.accentIndex)
                put("reduceMotion", settings.reduceMotion)
                put("confirmUnvisit", settings.confirmUnvisit)
            })
        }.toString(2)

    fun importJson(raw: String): Pair<Map<String, StationProgress>, UserSettings> {
        val root = JSONObject(raw)
        require(root.optString("format") == "metro-moscow-android") { "Неизвестный формат файла" }
        val progressRoot = root.getJSONObject("progress")
        val validIds = MetroSeed.lines.flatMap { it.stations }.map { it.id }.toSet()
        val progress = progressRoot.keys().asSequence()
            .filter { it in validIds }
            .associateWith { id ->
                val value = progressRoot.getJSONObject(id)
                StationProgress(
                    visited = value.optBoolean("visited"),
                    favorite = value.optBoolean("favorite"),
                    visitedAt = value.optString("visitedAt").takeIf { it.isNotBlank() },
                    note = value.optString("note")
                )
            }
        val s = root.optJSONObject("settings")
        val settings = if (s == null) loadSettings() else UserSettings(
            themeMode = runCatching { ThemeMode.valueOf(s.optString("theme")) }.getOrDefault(ThemeMode.SYSTEM),
            accentIndex = s.optInt("accent").coerceIn(0, 4),
            reduceMotion = s.optBoolean("reduceMotion"),
            confirmUnvisit = s.optBoolean("confirmUnvisit", true)
        )
        saveProgress(progress)
        saveSettings(settings)
        return progress to settings
    }

    fun resetProgress() = prefs.edit().remove("progress").apply()
}
