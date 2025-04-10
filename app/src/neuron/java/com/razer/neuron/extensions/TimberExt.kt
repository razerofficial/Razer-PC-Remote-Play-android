package com.razer.neuron.extensions

import org.jetbrains.annotations.NonNls
import timber.log.Timber
/**
 * If true, then [Timber.Forest.vv] will print to Timber
 */
const val isVeryVerboseEnabled = false // BuildConfig.DEBUG

/**
 * Very verbose log. It is for things that are very spammy
 * Only log to [Timber.v] if [BuildConfig.DEBUG]
 */
@Suppress("NOTHING_TO_INLINE")
inline fun Timber.Forest.vv(msg: String, vararg args: Any?) {
    if (isVeryVerboseEnabled) {
        v(msg, *args)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Timber.Forest.vv(t: Throwable) {
    if (isVeryVerboseEnabled) {
        v(t)
    }
}