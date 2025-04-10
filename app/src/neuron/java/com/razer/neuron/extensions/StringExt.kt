package com.razer.neuron.extensions

import android.net.Uri
import android.text.Html
import android.text.Spanned
import timber.log.Timber



/**
 * Get the UTF-8 encoded byte array of the String and convert it to hash MD5
 *
 * If there is any error (no algorithm or bad encoding) then it returns null
 */
fun String.MD5(): ByteArray? {
    return try {
        toByteArray(charset("utf-8")).MD5()
    } catch (t: Throwable) {
        Timber.w(t)
        null
    }
}


/**
 * Opposite of [convertToHex]
 */
fun String.hexToByteArray(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }

    val byteIterator = chunkedSequence(2)
        .map { it.toInt(16).toByte() }
        .iterator()

    return ByteArray(length / 2) { byteIterator.next() }
}



/**
 * Returns the String HEX representation of the byte array
 */
@JvmOverloads
fun ByteArray.convertToHex(lowercase: Boolean = true): String {
    val buf = StringBuilder()
    for (b in this) {
        buf.append(String.format("%02X", b))
    }
    return if (lowercase) buf.toString().lowercase() else buf.toString().uppercase()
}


/**
 * Convert the [String] to [Uri] or return null
 */
fun String.toUriOrNull(): Uri? {
    return try {
        Uri.parse(this)
    } catch (t: Throwable) {
        Timber.w(t)
        null
    }
}


fun String.toHtmlSpanned(): Spanned {
    return Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
}