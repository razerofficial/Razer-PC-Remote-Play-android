package com.razer.neuron.settings

interface SettingsStackFragment {
    /**
     * The title of the fragment, which will show at the right-middle tool bar title.
     *
     * @return the string of the [com.google.android.material.appbar.MaterialToolbar]. Returning null
     * will hide the bar all together giving the fragment more space
     */
    val fragmentTitle : String?
}