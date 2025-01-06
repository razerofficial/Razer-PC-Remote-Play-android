package com.razer.neuron.pref

import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.core.content.edit
import com.limelight.preferences.PreferenceConfiguration
import com.razer.neuron.RnApp
import com.razer.neuron.extensions.enumPref2
import com.razer.neuron.model.AppThemeType
import com.razer.neuron.model.DisplayModeOption
import com.razer.neuron.model.SessionStats
import com.razer.neuron.model.SessionStats.Companion.fromJson
import com.razer.neuron.shared.RazerRemotePlaySettingsKey
import com.razer.neuron.shared.SharedConstants.DEFAULT_LIST_FPS
import com.razer.neuron.shared.SharedConstants.DEFAULT_LIST_RESOLUTION
import com.razer.neuron.shared.SharedConstants.LIST_FPS
import com.razer.neuron.shared.SharedConstants.LIST_RESOLUTION
import com.razer.neuron.shared.SharedConstants.REDUCE_REFRESH_RATE_PREF_STRING
import com.razer.neuron.shared.SharedConstants.REMOTE_PLAY_SETTINGS
import hu.autsoft.krate.Krate
import hu.autsoft.krate.booleanPref
import hu.autsoft.krate.default.withDefault
import hu.autsoft.krate.intPref
import hu.autsoft.krate.longPref
import hu.autsoft.krate.stringPref

object RemotePlaySettingsPref : Krate {
    const val REMOTE_PLAY_SETTINGS_NAME = REMOTE_PLAY_SETTINGS
    private const val NEURON = "neuron"
    private const val NEXUS = "nexus"
    /**
     * Any key that starts with any of these prefixes should not
     * be saved via [com.razer.neuron.nexus.sources.NexusRemotePlaySettingsSource.sync]
     */
    private val READ_ONLY_PREFIXES = setOf(NEURON, NEXUS)

    private val KEY_IS_NEURON_OOBE_COMPLETED = "${NEURON}_KEY_IS_OOBE_COMPLETED"

    private val KEY_IS_USE_DEFAULT_RESOLUTION = "${NEURON}_KEY_IS_USE_DEFAULT_RESOLUTION"
    /**
     * To read when Nexus was last foreground/background
     */
    const val NEXUS_LAST_ACTIVE_TIMESTAMP = "${NEXUS}_last_active_at"

    /**
     * Only Neuron can write to this
     */
    private const val NEURON_LAST_SYNC_TIMESTAMP = "${NEURON}_last_sync_at"
    /**
     * Only Neuron can write to this
     */
    private const val NEURON_LAST_ACTIVE_TIMESTAMP = "${NEURON}_last_active_at"

    const val APP_THEME_TYPE = "${NEURON}_app_theme_type"

    private const val MANUALLY_UNPAIRED = "manually_unpaired_computers_v2"
    const val LAST_SESSION_STATS_JSON = "${NEURON}_last_session_stats_json_v2"

    private const val SEPARATE_SCREEN_DISPLAY = "separate_screen_display"

    private const val NEURON_COMPLETED_OOBE_STEPS = "${NEURON}_completed_oobe_steps"

    private const val NEURON_TOS_ACCEPTED = "${NEURON}_tos_accepted"
    private const val NEXUS_UPDATE_REJECTED = "${NEURON}_nexus_update_rejected"

    private const val NEURON_DEV_MODE = "${NEURON}_dev_mode"

    private const val AUTO_CLOSE_RUNNING_GAME_COUNTDOWN_IN_SEC = "auto_close_running_game_countdown_sec"

    override val sharedPreferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(RnApp.appContext)
    }

    var neuronLastSyncAt by longPref(NEURON_LAST_SYNC_TIMESTAMP).withDefault(0L)
    var neuronLastActiveAt by longPref(NEURON_LAST_ACTIVE_TIMESTAMP).withDefault(0L)

    var isTosAccepted by booleanPref(NEURON_TOS_ACCEPTED).withDefault(false)
    var isOobeCompleted by booleanPref(KEY_IS_NEURON_OOBE_COMPLETED).withDefault(false)

    var isUseDefaultResolution by booleanPref(KEY_IS_USE_DEFAULT_RESOLUTION).withDefault(false)

    private var manuallyUnpairedRaw by stringPref(MANUALLY_UNPAIRED)

    private var lastSessionStatsJson by stringPref(LAST_SESSION_STATS_JSON)
    var savedLastSessionStats : SessionStats?
        get() = lastSessionStatsJson?.fromJson()
        set(value) {
            lastSessionStatsJson = value?.toJson()
        }

    var isSeparateScreenDisplayEnabled by booleanPref(SEPARATE_SCREEN_DISPLAY).withDefault(false)

    var autoCloseGameCountDown by intPref(AUTO_CLOSE_RUNNING_GAME_COUNTDOWN_IN_SEC).withDefault(30)

    var hasUserRejectedNexusUpdate by booleanPref(NEXUS_UPDATE_REJECTED).withDefault(false)

    var appThemeType by enumPref2(APP_THEME_TYPE, AppThemeType::class, AppThemeType.default())

    var manuallyUnpaired : Set<String>
        get() = manuallyUnpairedRaw?.split(",")?.toSet() ?: emptySet()
        set(value) {
            manuallyUnpairedRaw = value.joinToString(",")
        }


    private var completedOobeStepsRaw by stringPref(NEURON_COMPLETED_OOBE_STEPS)
    var completedOobeStates : Set<String>
        get() = completedOobeStepsRaw?.split(",")?.toSet() ?: emptySet()
        set(value) {
            completedOobeStepsRaw = value.joinToString(",")
        }

    /**
     * If true, then the pref should not be allowed to be written unless directly by this
     * class (i.e do not use [SharedPreferences.Editor] to indirectly change the value via Source
     * class from ContentProvider)
     */
    fun String.isReadOnlyKey() = READ_ONLY_PREFIXES.any { this.startsWith(it) }

    var isDevModeEnabled by booleanPref(NEURON_DEV_MODE).withDefault(false)


    var isPerfOverlayEnabled by booleanPref(PreferenceConfiguration.ENABLE_PERF_OVERLAY_STRING).withDefault(false)

    var isCropDisplaySafeArea by booleanPref(RazerRemotePlaySettingsKey.PREF_VIRTUAL_DISPLAY_CROP_TO_SAFE_AREA).withDefault(false)

    private var displayModeRaw by stringPref(RazerRemotePlaySettingsKey.PREF_DISPLAY_MODE)

    var displayMode : DisplayModeOption
        get() = displayModeRaw?.let { DisplayModeOption.findByDisplayModeName(it) } ?: DisplayModeOption.default
        set(value) {
            displayModeRaw = value.displayModeName
        }



    val isLimitRefreshRate by booleanPref(REDUCE_REFRESH_RATE_PREF_STRING).withDefault(false)

}


