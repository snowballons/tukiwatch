package com.snowballons.tukiwatch

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snowballons.tukiwatch.db.FavoritesBackup
import com.snowballons.tukiwatch.liveness.LiveStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

/**
 * Drives the My List surface: favorites CRUD, the JSON v1 migration vehicle
 * (plan §5), and the liveness ticker's state.
 *
 * Export/import go through SAF Uris rather than a FileProvider: the picker
 * grants write/read on the chosen document, so no provider, no permission, and
 * no cache-file lifecycle to clean up.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TukiWatchApp

    val streams: StateFlow<List<LiveStream>> = app.liveness.streams
    val loading: StateFlow<Boolean> = app.liveness.loading
    val degraded: StateFlow<Boolean> = app.liveness.tickDegraded

    var currentUrl by mutableStateOf("")
        private set

    /** Null while unknown/loading, so the button never lies about membership. */
    var isFavorite by mutableStateOf<Boolean?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    fun setCurrentUrl(url: String) {
        if (url.trim() == currentUrl) return
        currentUrl = url.trim()
        isFavorite = null
        viewModelScope.launch {
            runCatching { app.favorites.isFavorite(currentUrl) }
                .onSuccess { isFavorite = it }
                .onFailure { message = "Could not read My List: ${it.message}" }
        }
    }

    fun refresh() {
        viewModelScope.launch { app.liveness.refresh() }
    }

    /**
     * @param displayName channel name when the caller knows it (Add screen);
     *   otherwise derived from the URL's last path segment, which is what a
     *   pasted Twitch/Kick/YouTube URL actually carries.
     */
    fun toggleFavorite(displayName: String? = null) {
        val url = currentUrl
        if (url.isEmpty()) {
            message = "Resolve a URL first."
            return
        }
        viewModelScope.launch {
            busy = true
            try {
                if (isFavorite == true) {
                    app.favorites.idForUrl(url)?.let { app.favorites.removeFavorite(it) }
                    isFavorite = false
                    message = "Removed from My List."
                } else {
                    val name = displayName?.takeIf { it.isNotBlank() } ?: deriveName(url)
                    val added = app.favorites.addFavorite(name, url)
                    isFavorite = true
                    message = if (added) "Saved to My List." else "Already in My List."
                }
                app.liveness.refresh()
            } catch (t: Throwable) {
                message = "Could not update My List: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            busy = true
            try {
                val favorites = app.favorites.getFavorites()
                val payload = FavoritesBackup.serialize(favorites, Instant.now().toString())
                withContext(Dispatchers.IO) { writeText(uri, payload) }
                message = "Exported ${favorites.size} favorite(s)."
            } catch (t: Throwable) {
                message = "Export failed: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            busy = true
            try {
                val raw = withContext(Dispatchers.IO) { readText(uri) }
                val outcome = app.favorites.importAll(FavoritesBackup.parse(raw))
                message = "Imported ${outcome.imported}, skipped ${outcome.skipped}."
                app.liveness.refresh()
            } catch (t: Throwable) {
                message = "Import failed: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    private suspend fun writeText(uri: Uri, text: String) {
        val sink = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the chosen location for writing.")
        sink.use { it.write(text.toByteArray()) }
    }

    private suspend fun readText(uri: Uri): String =
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IOException("Could not read the chosen file.")

    private fun deriveName(url: String): String =
        url.substringBefore('?').substringBefore('#').trimEnd('/')
            .substringAfterLast('/').ifEmpty { url }
}
