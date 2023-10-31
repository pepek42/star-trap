package com.github.pepek42.asteroids.debug

import com.github.pepek42.asteroids.IS_DEBUG

/**
 * Logging util for logging every X seconds.
 */
class LoggingUtils(
    private val logTimeIntervalSeconds: Float = DEFAULT_LOG_INTERVAL_SECONDS
) {
    private var logEnabled = false
    private var accumulatorSeconds = 0f

    /**
     * Should be only called at the beginning of the main render loop. After that call [tryLogging] with logg message.
     */
    fun onGameLoopTick(deltaSeconds: Float) {
        if (IS_DEBUG) {
            logEnabled = false
            accumulatorSeconds += deltaSeconds
            if (accumulatorSeconds > logTimeIntervalSeconds) {
                logEnabled = true
                accumulatorSeconds %= logTimeIntervalSeconds
            }
        }
    }

    /**
     * Pass logging calls here. They will be triggered every n seconds if [onGameLoopTick] is called properly at the
     * beginning of render loop.
     */
    fun tryLogging(logMethod: () -> Unit) = if (logEnabled) logMethod() else Unit


    companion object {
        private const val DEFAULT_LOG_INTERVAL_SECONDS = 30f
        val defaultLoggingUtils = LoggingUtils()
    }
}
