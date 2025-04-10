package com.razer.neuron.common

fun <T> Result<T>.doOnError(onError: (Throwable) -> Unit): Result<T> {
    exceptionOrNull()?.let { onError(it) }
    return this
}
