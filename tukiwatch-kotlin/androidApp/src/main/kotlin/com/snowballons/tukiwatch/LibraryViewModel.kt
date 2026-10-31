package com.snowballons.tukiwatch

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.snowballons.tukiwatch.ui.state.BackupIo
import com.snowballons.tukiwatch.ui.state.LibraryController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

/**
 * Android wrapper around the shared [LibraryController].
 *
 * The only Android-specific pieces are SAF: export/import go through the
 * picker's `Uri` rather than a FileProvider, so no provider, permission or
 * cache-file lifecycle is involved. Membership, liveness state and the JSON v1
 * backup format are shared with the desktop harness.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TukiWatchApp

    val controller = LibraryController(
        favorites = app.favorites,
        liveness = app.liveness,
        scope = viewModelScope,
        nowIso = { Instant.now().toString() },
    )

    fun onCurrentUrlChanged(url: String) = controller.onCurrentUrlChanged(url)

    fun refresh() = controller.refresh()

    fun toggleFavorite() = controller.toggleFavorite()

    fun addFavorite(streamerName: String, url: String) = controller.addFavorite(streamerName, url)

    fun removeFavorite(id: Long) = controller.removeFavorite(id)

    fun exportTo(uri: Uri) {
        controller.exportTo(SafBackupIo(getApplication(), uri))
    }

    fun importFrom(uri: Uri) {
        controller.importFrom(SafBackupIo(getApplication(), uri))
    }
}

/** Adapts a SAF document `Uri` to the shared backup contract. */
private class SafBackupIo(
    private val context: Context,
    private val uri: Uri,
) : BackupIo {
    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        val sink = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the chosen location for writing.")
        sink.use { it.write(text.toByteArray()) }
    }

    override suspend fun read(): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IOException("Could not read the chosen file.")
    }
}
