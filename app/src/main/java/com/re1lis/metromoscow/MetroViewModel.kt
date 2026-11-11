package com.re1lis.metromoscow

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.re1lis.metromoscow.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class MetroUiState(
    val lines: List<MetroLine> = MetroSeed.lines,
    val progress: Map<String, StationProgress> = emptyMap(),
    val settings: UserSettings = UserSettings(),
    val search: String = "",
    val filter: StationFilter = StationFilter.ALL,
    val message: String? = null
) {
    val allStations get() = lines.flatMap { it.stations }
    val visitedCount get() = progress.values.count { it.visited }
    val favoriteCount get() = progress.values.count { it.favorite }
    val totalCount get() = allStations.size
}

class MetroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MetroRepository(application)
    private val _state = MutableStateFlow(
        MetroUiState(progress = repository.loadProgress(), settings = repository.loadSettings())
    )
    val state = _state.asStateFlow()

    fun setSearch(value: String) = _state.update { it.copy(search = value) }
    fun setFilter(value: StationFilter) = _state.update { it.copy(filter = value) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun toggleVisited(station: Station) {
        mutate(station.id) { current ->
            val nowVisited = !current.visited
            current.copy(
                visited = nowVisited,
                visitedAt = if (nowVisited) current.visitedAt ?: LocalDate.now().toString() else null
            )
        }
    }

    fun toggleFavorite(station: Station) = mutate(station.id) { it.copy(favorite = !it.favorite) }

    fun updateStation(station: Station, date: String?, note: String) = mutate(station.id) {
        it.copy(visitedAt = date?.takeIf(String::isNotBlank), note = note.trim())
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val value = transform(_state.value.settings)
        repository.saveSettings(value)
        _state.update { it.copy(settings = value) }
    }

    fun resetProgress() {
        repository.resetProgress()
        _state.update { it.copy(progress = emptyMap(), message = "Прогресс сброшен") }
    }

    fun exportJson(): String = repository.exportJson(_state.value.progress, _state.value.settings)

    fun importJson(raw: String) {
        runCatching { repository.importJson(raw) }
            .onSuccess { (progress, settings) ->
                _state.update { it.copy(progress = progress, settings = settings, message = "Прогресс восстановлен") }
            }
            .onFailure { error -> _state.update { it.copy(message = error.message ?: "Не удалось импортировать файл") } }
    }

    private fun mutate(id: String, transform: (StationProgress) -> StationProgress) {
        val next = _state.value.progress.toMutableMap().apply {
            put(id, transform(get(id) ?: StationProgress()))
        }
        repository.saveProgress(next)
        _state.update { it.copy(progress = next) }
    }
}
