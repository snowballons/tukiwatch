package com.snowballons.tukiwatch.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.util.concurrent.atomic.AtomicReference

/**
 * Exactly one store per process: two `DataStore`s over the same file corrupt
 * each other, so the instance is memoized rather than rebuilt per call.
 * (Kotlin forbids `by lazy` on an extension property — no backing field.)
 */
private val storeRef = AtomicReference<DataStore<Preferences>?>()

fun createPrefsDataStore(context: Context): DataStore<Preferences> {
    storeRef.get()?.let { return it }
    val app = context.applicationContext
    val created = PreferenceDataStoreFactory.create {
        app.preferencesDataStoreFile("tukiwatch_prefs")
    }
    return if (storeRef.compareAndSet(null, created)) created else checkNotNull(storeRef.get())
}
