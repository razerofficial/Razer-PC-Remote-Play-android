package com.limelight

import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.razer.neuron.utils.elapsedRealtime


/**
 * Used by [throttle]
 */
private var lastThrottled = mutableMapOf<String, Long>()

/**
 * Call [runnable] if the last use of [key] was more than [minIntervalMs] ago
 *
 * @param key a unique key for [runnable]
 * @param minIntervalMs the duration that needs to pass before [runnable] will not be ignored
 * @param runnable the function that you want to run
 * @return true if [runnable] was invoked
 */
@CanIgnoreReturnValue
fun throttle(key : String, minIntervalMs : Long, runnable : () -> Unit) : Boolean{
    return if((elapsedRealtime() - (lastThrottled[key] ?: 0L)) > minIntervalMs) {
        lastThrottled[key] = elapsedRealtime()
        runnable()
        true
    } else {
        false
    }
}