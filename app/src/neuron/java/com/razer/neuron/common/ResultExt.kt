package com.razer.neuron.common

import timber.log.Timber

fun <T> Result<T>.doOnError(onError: (Throwable) -> Unit): Result<T> {
    exceptionOrNull()?.let { onError(it) }
    return this
}


inline fun <T> Result<T>.logWarningOnError() = doOnError {
    Timber.w(it)
}
