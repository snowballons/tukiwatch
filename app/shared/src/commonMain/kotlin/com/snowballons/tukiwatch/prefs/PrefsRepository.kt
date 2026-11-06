package com.snowballons.tukiwatch.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Snapshot of everything the app persists outside the favorites DB. */
data class Prefs(
    /** Theme id resolved by `tukiPaletteFor`; "system" follows the device. */
    val themeId: String = "system",
    val onboardingCompleted: Boolean = false,

    // Playback
    /** Keep the stream playing when the app is backgrounded (best-effort). */
    val backgroundPlayback: Boolean = false,
    /** Enter picture-in-picture automatically when leaving the player. */
    val autoPip: Boolean = true,
    /** "auto" | "source" | "1080" | "720" | "480" — the rendition picked first. */
    val preferredQuality: String = QUALITY_AUTO,
    /** Hold the display awake while a stream is playing. */
    val keepScreenAwake: Boolean = true,

    // Live updates
    val autoCheckFavorites: Boolean = true,
    /** Minutes between favourite checks; 0 means manual only. */
    val checkIntervalMinutes: Int = 5,

    // Notifications
    val updateReminders: Boolean = true,

    // My List
    val showOfflineChannels: Boolean = true,
    /** "live" | "recent" | "name". */
    val myListSort: String = SORT_LIVE,

    // Interaction
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,

    /** versionCode the user chose to skip; drives the update prompt (plan §6.2). */
    val updateSkipVersion: Long? = null,
    val helixClientId: String? = null,
    val helixAccessToken: String? = null,
) {
    /** BYO keys only — plan §9.2 forbids any bundled secret, so absence is normal. */
    val hasHelixCredentials: Boolean
        get() = !helixClientId.isNullOrBlank() && !helixAccessToken.isNullOrBlank()

    companion object {
        const val QUALITY_AUTO = "auto"
        const val SORT_LIVE = "live"
        const val SORT_RECENT = "recent"
        const val SORT_NAME = "name"
        val QUALITY_OPTIONS = listOf("auto", "source", "1080", "720", "480")
        val CHECK_INTERVAL_OPTIONS = listOf(2, 5, 10, 0)
    }
}

/**
 * DataStore-backed prefs (plan §5). Keys are the survivors of the server kill:
 * the Connection tab's server URL, license and session token are all gone.
 *
 * The store instance is supplied by the platform layer
 * (`createPrefsDataStore` on Android) so common code depends only on the
 * public `DataStore<Preferences>` contract.
 */
class PrefsRepository(private val store: DataStore<Preferences>) {

    val prefs: Flow<Prefs> = store.data.map { entries ->
        Prefs(
            themeId = entries[THEME_ID]
                ?: when (entries[THEME_MODE]) {
                    // One-time migration from the retired ThemeMode enum.
                    "light" -> "tukiwatch-light"
                    "dark" -> "tukiwatch-dark"
                    else -> "system"
                },
            onboardingCompleted = entries[ONBOARDING_COMPLETED] ?: false,
            backgroundPlayback = entries[BACKGROUND_PLAYBACK] ?: false,
            autoPip = entries[AUTO_PIP] ?: true,
            preferredQuality = entries[PREFERRED_QUALITY]
                ?.takeIf { it in Prefs.QUALITY_OPTIONS } ?: Prefs.QUALITY_AUTO,
            keepScreenAwake = entries[KEEP_SCREEN_AWAKE] ?: true,
            autoCheckFavorites = entries[AUTO_CHECK_FAVORITES] ?: true,
            checkIntervalMinutes = entries[CHECK_INTERVAL_MINUTES]
                ?.takeIf { it in Prefs.CHECK_INTERVAL_OPTIONS } ?: 5,
            updateReminders = entries[UPDATE_REMINDERS] ?: true,
            showOfflineChannels = entries[SHOW_OFFLINE_CHANNELS] ?: true,
            myListSort = entries[MY_LIST_SORT]
                ?.takeIf { it in listOf(Prefs.SORT_LIVE, Prefs.SORT_RECENT, Prefs.SORT_NAME) }
                ?: Prefs.SORT_LIVE,
            hapticsEnabled = entries[HAPTICS_ENABLED] ?: true,
            reduceMotion = entries[REDUCE_MOTION] ?: false,
            updateSkipVersion = entries[UPDATE_SKIP_VERSION],
            helixClientId = entries[HELIX_CLIENT_ID]?.takeIf { it.isNotBlank() },
            helixAccessToken = entries[HELIX_ACCESS_TOKEN]?.takeIf { it.isNotBlank() },
        )
    }

    /** Persists the chosen theme id and folds away the legacy mode key. */
    suspend fun setThemeId(id: String) = store.edit {
        it[THEME_ID] = id
        it.remove(THEME_MODE)
    }

    suspend fun setOnboardingCompleted(completed: Boolean) =
        store.edit { it[ONBOARDING_COMPLETED] = completed }

    suspend fun setBackgroundPlayback(enabled: Boolean) =
        store.edit { it[BACKGROUND_PLAYBACK] = enabled }

    suspend fun setAutoPip(enabled: Boolean) = store.edit { it[AUTO_PIP] = enabled }

    suspend fun setPreferredQuality(quality: String) = store.edit {
        it[PREFERRED_QUALITY] = if (quality in Prefs.QUALITY_OPTIONS) quality else Prefs.QUALITY_AUTO
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) =
        store.edit { it[KEEP_SCREEN_AWAKE] = enabled }

    suspend fun setAutoCheckFavorites(enabled: Boolean) =
        store.edit { it[AUTO_CHECK_FAVORITES] = enabled }

    suspend fun setCheckIntervalMinutes(minutes: Int) = store.edit {
        it[CHECK_INTERVAL_MINUTES] = if (minutes in Prefs.CHECK_INTERVAL_OPTIONS) minutes else 5
    }

    suspend fun setUpdateReminders(enabled: Boolean) =
        store.edit { it[UPDATE_REMINDERS] = enabled }

    suspend fun setShowOfflineChannels(enabled: Boolean) =
        store.edit { it[SHOW_OFFLINE_CHANNELS] = enabled }

    suspend fun setMyListSort(sort: String) = store.edit {
        it[MY_LIST_SORT] =
            if (sort == Prefs.SORT_RECENT || sort == Prefs.SORT_NAME) sort else Prefs.SORT_LIVE
    }

    suspend fun setHapticsEnabled(enabled: Boolean) =
        store.edit { it[HAPTICS_ENABLED] = enabled }

    suspend fun setReduceMotion(enabled: Boolean) =
        store.edit { it[REDUCE_MOTION] = enabled }

    /** null clears the skip (Settings → "check again"). */
    suspend fun setUpdateSkipVersion(versionCode: Long?) = store.edit { block ->
        if (versionCode == null) block.remove(UPDATE_SKIP_VERSION)
        else block[UPDATE_SKIP_VERSION] = versionCode
    }

    /** Stored as user-supplied values only; never seeded from build config. */
    suspend fun setHelixCredentials(clientId: String?, accessToken: String?) = store.edit { block ->
        if (clientId.isNullOrBlank()) block.remove(HELIX_CLIENT_ID) else block[HELIX_CLIENT_ID] = clientId
        if (accessToken.isNullOrBlank()) block.remove(HELIX_ACCESS_TOKEN)
        else block[HELIX_ACCESS_TOKEN] = accessToken
    }

    companion object {
        val THEME_ID = stringPreferencesKey("theme_id")

        /** Retired `ThemeMode` key; read once to migrate, removed on next write. */
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val AUTO_CHECK_FAVORITES = booleanPreferencesKey("auto_check_favorites")
        val CHECK_INTERVAL_MINUTES = intPreferencesKey("check_interval_minutes")
        val UPDATE_REMINDERS = booleanPreferencesKey("update_reminders")
        val SHOW_OFFLINE_CHANNELS = booleanPreferencesKey("show_offline_channels")
        val MY_LIST_SORT = stringPreferencesKey("my_list_sort")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val UPDATE_SKIP_VERSION = longPreferencesKey("update_skip_version")
        val HELIX_CLIENT_ID = stringPreferencesKey("helix_client_id")
        val HELIX_ACCESS_TOKEN = stringPreferencesKey("helix_access_token")
    }
}
