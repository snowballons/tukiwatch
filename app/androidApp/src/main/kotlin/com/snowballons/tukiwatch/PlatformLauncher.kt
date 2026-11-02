package com.snowballons.tukiwatch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object PlatformLauncher {

    /**
     * Constructs a native app deep link URL for supported platforms, or returns null.
     */
    fun constructNativeUrl(platform: String?, originalUrl: String): String? {
        if (platform == null || originalUrl.isBlank()) return null
        val uri = runCatching { Uri.parse(originalUrl) }.getOrNull() ?: return null
        val pathSegments = uri.pathSegments ?: emptyList()

        return when (platform.lowercase()) {
            "twitch" -> {
                val channel = pathSegments.firstOrNull { it.isNotBlank() }
                if (channel != null) "twitch://stream/$channel" else "twitch://"
            }
            "youtube", "youtu-be" -> {
                val videoId = uri.getQueryParameter("v")
                    ?: pathSegments.firstOrNull { it.isNotBlank() && it != "watch" }
                if (videoId != null) "vnd.youtube:$videoId" else "vnd.youtube://"
            }
            "tiktok" -> "tiktok://"
            "instagram" -> {
                val user = pathSegments.firstOrNull { it.isNotBlank() }
                if (user != null) "instagram://user?username=$user" else "instagram://"
            }
            "facebook" -> "fb://"
            else -> null
        }
    }

    /**
     * Opens stream in the official native app if installed, falling back to browser.
     */
    fun openInOfficialApp(context: Context, platform: String?, originalUrl: String) {
        if (originalUrl.isBlank()) return

        val nativeSchemeUrl = constructNativeUrl(platform, originalUrl)
        if (nativeSchemeUrl != null) {
            try {
                val nativeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(nativeSchemeUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(nativeIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Official app not installed; fall through to browser intent
            } catch (_: Exception) {
                // Other intent failure; fall through to browser intent
            }
        }

        // Web fallback
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (_: Exception) {
            // Cannot launch browser
        }
    }
}
