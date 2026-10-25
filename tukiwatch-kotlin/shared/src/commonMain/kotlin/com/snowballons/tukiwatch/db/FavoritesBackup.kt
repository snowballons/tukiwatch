package com.snowballons.tukiwatch.db

import com.snowballons.tukiwatch.model.Favorite
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One entry of the export payload — db.ts emits only these two fields. */
@Serializable
data class ExportedFavorite(
    @SerialName("streamer_name") val streamerName: String,
    @SerialName("original_url") val originalUrl: String,
)

/** The v1 envelope. Declaration order is the key order db.ts writes. */
@Serializable
data class FavoritesExport(
    val app: String = APP,
    val version: Int = VERSION,
    @SerialName("exportedAt") val exportedAt: String,
    val favorites: List<ExportedFavorite>,
) {
    companion object {
        const val APP = "tukiwatch"
        const val VERSION = 1
    }
}

/**
 * JSON export format v1 — the migration vehicle from the Expo app (plan §5/§8).
 * The old app is the reference implementation: same keys, same 2-space pretty
 * print, and import accepts both shapes it accepted (bare array or `{favorites}`).
 */
object FavoritesBackup {
    private val json = Json {
        prettyPrint = true
        // Default indent is two spaces, matching db.ts's
        // JSON.stringify(payload, null, 2). Setting it explicitly would mean
        // opting into an experimental serialization API for no behavior change.
        encodeDefaults = true
    }

    fun serialize(favorites: List<Favorite>, exportedAt: String): String =
        json.encodeToString(
            FavoritesExport.serializer(),
            FavoritesExport(
                exportedAt = exportedAt,
                favorites = favorites.map { ExportedFavorite(it.streamerName, it.originalUrl) },
            ),
        )

    /** Malformed JSON throws, as db.ts's `JSON.parse` did. */
    fun parse(raw: String): List<ExportedFavorite> {
        val array = when (val root = json.parseToJsonElement(raw)) {
            is JsonArray -> root
            is JsonObject -> root["favorites"] as? JsonArray
                ?: throw IllegalArgumentException("Invalid backup file: no favorites array found.")
            else -> throw IllegalArgumentException("Invalid backup file: no favorites array found.")
        }
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.text("streamer_name")?.trim()
            val url = obj.text("original_url")?.trim()
            // db.ts: `item.streamer_name?.trim()` → null/blank skips the row.
            if (name.isNullOrEmpty() || url.isNullOrEmpty()) null
            else ExportedFavorite(name, url)
        }
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}
