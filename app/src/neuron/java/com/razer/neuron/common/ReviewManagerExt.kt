package com.razer.neuron.common

import android.app.Activity
import com.razer.neuron.pref.RemotePlaySettingsPref
import com.razer.neuron.utils.now
import timber.log.Timber
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


/**
 * https://razersw.atlassian.net/wiki/spaces/RTWWEAR/pages/3322675218/In-App+Rating+Remote+Play#IAR-3-logic
 */
private val goodSessionMinDurationMs = 10.minutes.inWholeMilliseconds
private val ungracefulTerminationCooldownMs = 1.hours.inWholeMilliseconds

// TODO:
fun isShowInAppReview() = false
suspend fun Activity.requestReview() = Unit

// TODO: replace function for public branch
/*
fun isShowInAppReview() : Boolean {
    val neverReviewBefore = RemotePlaySettingsPref.lastRequestReviewLaunchedAt == 0L
    val noRecentUngracefulTermination = (now() - (RemotePlaySettingsPref.connectionTerminatedUngracefullyAt ?: 0L)) > ungracefulTerminationCooldownMs
    val hadGoodSession = RemotePlaySettingsPref.totalSessionLengthMs >= goodSessionMinDurationMs
    Timber.v("isShowInAppReview: neverReviewBefore=$neverReviewBefore, noRecentUngracefulTermination=$noRecentUngracefulTermination, hadGoodSession=$hadGoodSession")
    // 1. never request before
    // 2. no ungraceful termination recently
    // 3. there was at least once a sufficiently long session
    return neverReviewBefore && noRecentUngracefulTermination && hadGoodSession
}

suspend fun Activity.requestReview() {
    Timber.v("requestReview: ReviewManagerFactory.create")
    val reviewManager = ReviewManagerFactory.create(this)
    reviewManager.requestReview().let {
        RemotePlaySettingsPref.lastRequestReviewLaunchedAt = now()
        val msg = "Requesting app review"
        Timber.v("requestReview: review info obtained and launch. $msg")
        debugToast(msg)
        reviewManager.launchReviewFlow(this, it).addOnCompleteListener {
            Timber.v("requestReview: Completed")
            RemotePlaySettingsPref.lastRequestReviewCompletedAt = now()
        }
    }
}
*/


