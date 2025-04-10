package com.razer.neuron.settings.appearance

import com.razer.neuron.model.AppThemeType

sealed class AppearanceState {

    class ShowLoading(val tag: String) : AppearanceState()

    class HideLoading(val tag: String) : AppearanceState()

    data object ApplyTheme : AppearanceState()

    class ShowSelectTheme(val defaultOption: AppThemeType) : AppearanceState()
}
