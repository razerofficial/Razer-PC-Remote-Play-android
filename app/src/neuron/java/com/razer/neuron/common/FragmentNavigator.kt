package com.razer.neuron.common

import android.os.Bundle

interface FragmentNavigator {
    /**
     * Add a fragment to back stack
     */
    fun addFragmentToBackStack(navigationId: Int, bundle: Bundle? = null)
}