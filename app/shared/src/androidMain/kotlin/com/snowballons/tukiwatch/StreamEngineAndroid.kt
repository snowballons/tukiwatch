package com.snowballons.tukiwatch

actual fun createStreamEngine(): StreamEngine {
    throw IllegalStateException("Use ChaquopyStreamEngine(context) on Android")
}
