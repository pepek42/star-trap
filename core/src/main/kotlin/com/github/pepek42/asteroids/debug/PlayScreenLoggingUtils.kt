package com.github.pepek42.asteroids.debug

import com.github.pepek42.asteroids.IS_DEBUG

/**
 * Logging util. Should be
 */
object PlayScreenLoggingUtils {
    /**
     * Should be only called at the beginning of the main render loop in play screen. After that call [tryLogging] with logg message.
     */
    @JvmStatic
    fun handlePlayScreenLogging(delta: Float) {
        logEnabled = false
        if (IS_DEBUG) {
            accumulator += delta
            if (accumulator > LOG_EVERY_NTH_SECONDS) {
                logEnabled = true
                accumulator %= LOG_EVERY_NTH_SECONDS
            }
        }
    }

    @JvmStatic
    fun tryLogging(logMethod: () -> Unit) = if (logEnabled) logMethod() else Unit


    @JvmStatic
    var logEnabled = false

    @JvmStatic
    var accumulator = 0f

    private const val LOG_EVERY_NTH_SECONDS = 5

}
