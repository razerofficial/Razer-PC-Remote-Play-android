package com.limelight

import android.os.Build
import timber.log.Timber
import timber.log.Timber.DebugTree
import timber.log.Timber.Tree
import java.util.regex.Pattern

/**
 * This is a rewrite of the original LimeLog.java.
 *
 * Method signatures remain the same to avoid code change
 *
 * Additionally, it has other functions like:
 * - [LimeLog.verbose], [LimeLog.debug] for debugging at other levels
 * - [LimeLog.throttle] that uses [throttle] (avoid spam)
 * - [currentTag] to set [Timber.tag]. (Previously it was always "LimeLog")
 */
object LimeLog {
    private const val MAX_LOG_LENGTH = 4000
    private const val MAX_TAG_LENGTH = 23
    private val ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$")

    private val tagIgnoreList = listOf(
        Timber::class.java.name,
        LimeLog::class.java.name,
        Timber.Forest::class.java.name,
        Tree::class.java.name,
        DebugTree::class.java.name
    )

    private fun createStackElementTag(element: StackTraceElement): String? {
        var tag = element.className.substringAfterLast('.')
        val m = ANONYMOUS_CLASS.matcher(tag)
        if (m.find()) {
            tag = m.replaceAll("")
        }
        // Tag length limit was removed in API 26.
        return if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= 26) {
            tag
        } else {
            tag.substring(0, MAX_TAG_LENGTH)
        }
    }

    private fun currentTag() = Throwable().stackTrace
        .first { it.className !in tagIgnoreList }
        .let(::createStackElementTag)

    private fun timberWithTag() = Timber.tag(currentTag() ?: "LimeLog")

    @JvmStatic
    fun verbose(msg: String?) = timberWithTag().v(msg)

    @JvmStatic
    fun debug(msg: String?) = timberWithTag().d(msg)

    @JvmStatic
    fun info(msg: String?) = timberWithTag().i(msg)

    @JvmStatic
    fun warning(msg: String?) = timberWithTag().w(msg)

    @JvmStatic
    fun error(msg: String?) = timberWithTag().e(msg)

    @JvmStatic
    fun severe(msg: String?) = timberWithTag().e(msg)

    @JvmStatic
    fun throttle(priority: Int, tag: String, key: String, minIntervalMs: Long, any: Any?) =
        timberThrottle(
            priority = priority,
            tag = tag,
            key = key,
            minIntervalMs = minIntervalMs,
            any = any
        )
}

/**
 * A call to [Timber.v] or [Timber.e] that uses [throttle]
 */
private fun timberThrottle(priority: Int, tag: String, key: String, minIntervalMs: Long, any: Any?) =
    throttle(key, minIntervalMs) {
        if (any is Throwable) {
            Timber.tag(tag).log(priority, any)
        } else {
            Timber.tag(tag).log(priority, any?.toString())
        }
    }


//
//object LimeLog {
//    private val LOGGER: Logger = Logger.getLogger(LimeLog::class.java.name)
//
//    fun info(msg: String?) {
//        LimeLog.LOGGER.info(msg)
//    }
//
//    fun warning(msg: String?) {
//        LimeLog.LOGGER.warning(msg)
//    }
//
//    fun severe(msg: String?) {
//        LimeLog.LOGGER.severe(msg)
//    }
//
//    @Throws(IOException::class)
//    fun setFileHandler(fileName: String) {
//        LimeLog.LOGGER.addHandler(FileHandler(fileName))
//    }
//}
