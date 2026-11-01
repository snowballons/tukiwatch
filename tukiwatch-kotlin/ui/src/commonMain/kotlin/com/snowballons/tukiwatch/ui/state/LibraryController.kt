package com.snowballons.tukiwatch.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snowballons.tukiwatch.db.FavoritesBackup
import com.snowballons.tukiwatch.db.FavoritesRepository
import com.snowballons.tukiwatch.liveness.LiveStream
import com.snowballons.tukiwatch.liveness.LivenessRepository
import com.snowballons.tukiwatch.ui.StreamUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Where a JSON backup payload is read from or written to.
 *
 * Android adapts a SAF `Uri` (the picker already grants read/write, so no
 * FileProvider or permission is involved). Desktop can back this with a file.
 */
interface BackupIo {
    suspend fun write(text: String)
    suspend fun read(): String
}

/**
 * My List state: favorites membership, the JSON v1 migration vehicle, and the
 * liveness ticker's published state.
 *
 * Plain class so Android can wrap it in a ViewModel and desktop can own it
 * from a composition scope. [nowIso] supplies the export timestamp because
 * common code has no wall clock.
 */
class LibraryController(
    private val favorites: FavoritesRepository,
    private val liveness: LivenessRepository,
    private val scope: CoroutineScope,
    private val nowIso: () -> String,
) {
    val streams: StateFlow<List<LiveStream>> = liveness.streams
    val loading: StateFlow<Boolean> = liveness.loading
    val degraded: StateFlow<Boolean> = liveness.tickDegraded

    var currentUrl by mutableStateOf("")
        private set

    /** Null while unknown/loading, so the button never lies about membership. */
    var isFavorite by mutableStateOf<Boolean?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /** Consume the current feedback message (called after Snackbar has shown it). */
    fun clearMessage() { message = null }

    var busy by mutableStateOf(false)
        private set

    fun onCurrentUrlChanged(url: String) {
        if (url.trim() == currentUrl) return
        currentUrl = url.trim()
        isFavorite = null
        scope.launch {
            runCatching { favorites.isFavorite(currentUrl) }
                .onSuccess { isFavorite = it }
                .onFailure { message = "Could not read My List: ${it.message}" }
        }
    }

    fun refresh() {
        scope.launch { liveness.refresh() }
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
        scope.launch {
            busy = true
            try {
                if (isFavorite == true) {
                    favorites.idForUrl(url)?.let { favorites.removeFavorite(it) }
                    isFavorite = false
                    message = "Removed from My List."
                } else {
                    val name = displayName?.takeIf { it.isNotBlank() } ?: deriveName(url)
                    val added = favorites.addFavorite(name, url)
                    isFavorite = true
                    message = if (added) "Saved to My List." else "Already in My List."
                }
                liveness.refresh()
            } catch (t: Throwable) {
                message = "Could not update My List: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    /** Explicitly add a favorite by URL and display name (Add screen). */
    fun addFavorite(displayName: String, url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        scope.launch {
            busy = true
            try {
                val name = displayName.takeIf { it.isNotBlank() } ?: deriveName(trimmedUrl)
                val added = favorites.addFavorite(name, trimmedUrl)
                if (trimmedUrl == currentUrl) isFavorite = true
                message = if (added) "Saved '$name' to My List." else "'$name' is already in My List."
                liveness.refresh()
            } catch (t: Throwable) {
                message = "Could not add to My List: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    /** Row-level removal, used by the My List surface. */
    fun removeFavorite(id: Long) {
        scope.launch {
            busy = true
            try {
                favorites.removeFavorite(id)
                if (currentUrl.isNotEmpty()) isFavorite = favorites.isFavorite(currentUrl)
                message = "Removed from My List."
                liveness.refresh()
            } catch (t: Throwable) {
                message = "Could not update My List: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    fun exportTo(io: BackupIo) {
        scope.launch {
            busy = true
            try {
                val saved = favorites.getFavorites()
                val payload = FavoritesBackup.serialize(saved, nowIso())
                io.write(payload)
                message = "Exported ${saved.size} favorite(s)."
            } catch (t: Throwable) {
                message = "Export failed: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    fun importFrom(io: BackupIo) {
        scope.launch {
            busy = true
            try {
                val outcome = favorites.importAll(FavoritesBackup.parse(io.read()))
                message = "Imported ${outcome.imported}, skipped ${outcome.skipped}."
                liveness.refresh()
            } catch (t: Throwable) {
                message = "Import failed: ${t.message}"
            } finally {
                busy = false
            }
        }
    }

    /** Channel name fallback derived cleanly via [StreamUtils.deriveStreamerName]. */
    fun deriveName(url: String): String =
        StreamUtils.deriveStreamerName(url)
}
