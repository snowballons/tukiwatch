package com.snowballons.tukiwatch.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Snapshot of everything the app persists outside the favorites DB. */
data class Prefs(
    /** Theme id resolved by `tukiPaletteFor`; "system" follows the device. */
    val themeId: String = "system",
    val onboardingCompleted: Boolean = false,
    /** versionCode the user chose to skip; drives the update prompt (plan §6.2). */
    val updateSkipVersion: Long? = null,
    val helixClientId: String? = null,
    val helixAccessToken: String? = null,
) {
    /** BYO keys only — plan §9.2 forbids any bundled secret, so absence is normal. */
    val hasHelixCredentials: Boolean
        get() = !helixClientId.isNullOrBlank() && !helixAccessToken.isNullOrBlank()
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
        val UPDATE_SKIP_VERSION = longPreferencesKey("update_skip_version")
        val HELIX_CLIENT_ID = stringPreferencesKey("helix_client_id")
        val HELIX_ACCESS_TOKEN = stringPreferencesKey("helix_access_token")
    }
}
