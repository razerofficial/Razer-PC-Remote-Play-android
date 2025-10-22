package com.razer.neuron.common

import android.graphics.Color
import androidx.core.graphics.toColor

fun Color.addRgb(add: Int) = addRgb(add.toFloat(), add.toFloat(), add.toFloat())

fun Color.multiplyRgb(percent: Float) = multiplyRgb(percent, percent, percent)

fun Color.multiplyRgb(r: Float, g: Float, b: Float) = Color.argb(
    alpha(),
    (red() * r).coerceIn(0f, 1f),
    (green() * g).coerceIn(0f, 1f),
    (blue() * b).coerceIn(0f, 1f),
).toColor()

fun Color.addRgb(r: Float, g: Float, b: Float) = Color.argb(
    alpha(),
    (red() + r).coerceIn(0f, 1f),
    (green() + g).coerceIn(0f, 1f),
    (blue() + b).coerceIn(0f, 1f),
).toColor()