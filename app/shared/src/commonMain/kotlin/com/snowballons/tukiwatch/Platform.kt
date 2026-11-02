package com.snowballons.tukiwatch

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform