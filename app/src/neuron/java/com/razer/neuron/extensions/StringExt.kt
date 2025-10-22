package com.razer.neuron.extensions

import android.net.Uri
import android.text.Html
import android.text.Spanned
import timber.log.Timber
import java.util.regex.Pattern


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



/**
 * Validates if the given string is a correctly formatted IP address (IPv4 or IPv6),
 * and not a domain name.
 *
 * This function first checks the format for IPv6, and if it does not match,
 * it then checks the format for IPv4. It uses separate regular expressions
 * to ensure that only valid IP address literals are accepted.
 *
 * @return `true` if the string is a valid IPv4 or IPv6 address, `false` otherwise.
 */
fun String.isValidIpAddressFormat(): Boolean {
    val ipAddress = this
    // If the address contains a colon, it must be an IPv6 address.
    return if (ipAddress.contains(':')) {
        isValidIpv6Format()
    } else {
        // Otherwise, it must be an IPv4 address.
        isValidIpv4Format()
    }
}

/**
 * Validates an IPv4 address using a regular expression.
 * @return `true` if the string is a valid IPv4 address, `false` otherwise.
 */
fun String.isValidIpv4Format(): Boolean {
    val ip = this
    // Regex for a standard IPv4 address, with each octet between 0 and 255.
    val ipv4Pattern = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )
    return ipv4Pattern.matcher(ip).matches()
}

/**
 * Validates an IPv6 address using a complex regular expression that handles
 * multiple formats, including full form, compressed form, and mixed notation.
 * @return `true` if the string is a valid IPv6 address, `false` otherwise.
 */
fun String.isValidIpv6Format(): Boolean {
    val ip = this
    // This is a comprehensive regex that accounts for:
    // - Full IPv6 addresses (8 hextets separated by colons).
    // - Compressed forms using '::' to represent consecutive zero hextets.
    // - Mixed notation (IPv4-mapped IPv6 addresses).
    val ipv6Pattern = Pattern.compile(
        "^([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4})|" + // Full form
                "^([0-9a-fA-F]{1,4}:){1,7}:|" + // Compressed form starting with '::'
                "^:((:[0-9a-fA-F]{1,4}){1,7})|" + // Compressed form ending with '::'
                "^([0-9a-fA-F]{1,4}:){1,6}:([0-9a-fA-F]{1,4})|" + // General compressed form
                "^([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" + // More specific compressed forms
                "^([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
                "^([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +
                "^([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
                "^([0-9a-fA-F]{1,4}:)(:[0-9a-fA-F]{1,4}){1,6}|" +
                "^:((:[0-9a-fA-F]{1,4}){1,6})|" +
                "^([0-9a-fA-F]{1,4}:){1,5}:([0-9a-fA-F]{1,4})\\.?([0-9a-fA-F]{1,4}){1,2}|" +
                "^::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])" // Mixed notation with IPv4
    )
    return ipv6Pattern.matcher(ip).matches()
}