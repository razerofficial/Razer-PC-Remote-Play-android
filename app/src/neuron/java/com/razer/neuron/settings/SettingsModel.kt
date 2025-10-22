import android.os.Parcelable
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.limelight.R
import kotlinx.parcelize.Parcelize

sealed class SettingsState {
    object Finish : SettingsState()
}

abstract class SettingsGroup {
    abstract val groupId: Int
    abstract val titleId: Int
    abstract val items: List<SettingsItem>

    class AppSettingsGroup(override val items: List<SettingsItem>) : SettingsGroup() {

        override val groupId = R.id.menu_group_app_settings

        @StringRes
        override val titleId = R.string.rn_app_settings
    }


    class RemotePlayGroup(override val items: List<SettingsItem>) : SettingsGroup() {

        override val groupId = R.id.menu_group_remote_play

        @StringRes
        override val titleId = R.string.rn_remote_play_settings_localized
    }

    class HelpGroup(override val items: List<SettingsItem>) : SettingsGroup() {

        override val groupId = R.id.menu_group_help

        @StringRes
        override val titleId = R.string.rn_settings_help
    }
}

@Parcelize
sealed class SettingsItem(
    val itemId: Int,
    val titleId: Int,
    val iconId: Int,
    @IdRes val navigationId: Int,
    open val titleText: String? = null,
) : Parcelable {
    data object COMPUTERS : SettingsItem(R.id.menu_item_computers, R.string.rn_settings_computers, R.drawable.ic_neuron_computer_offline_padding, R.id.computers)
    data object APPEARANCE : SettingsItem(R.id.menu_item_appearance, R.string.rn_settings_appearance, R.drawable.ic_appearance_padding, R.id.appearance)
    data object STREAMING_OPTIONS : SettingsItem(R.id.menu_item_streaming_options, R.string.rn_settings_streaming_options, R.drawable.ic_remote_controller_padding, R.id.streaming_options)
    data object ABOUT : SettingsItem(R.id.menu_item_about, R.string.rn_settings_about, R.drawable.ic_circled_info_padding, R.id.about)
    data object DEV_OPTIONS : SettingsItem(R.id.menu_item_dev_options, R.string.settings_dev_options, R.drawable.ic_settings, R.id.dev_options)


    /**
     * A function to check if this [SettingsItem] is the same as [that].
     * For now it only checks [itemId]
     */
    fun isSameContent(that : SettingsItem?) : Boolean {
        return that != null
                && itemId == that.itemId
                && titleId == that.titleId
                && titleText == that.titleText
    }

    companion object {
        val defaultItem get() = COMPUTERS
    }
}