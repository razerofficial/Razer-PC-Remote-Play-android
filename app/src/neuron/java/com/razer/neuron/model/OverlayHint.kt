package com.razer.neuron.model

import android.view.KeyEvent
import androidx.annotation.DrawableRes
import com.limelight.R

enum class OverlayHint(
    val keyCode: Int,
    @DrawableRes val iconRes: Int,
    val isToggle: Boolean
) {
    Windows(KeyEvent.KEYCODE_META_LEFT, R.drawable.ic_win, true),
    Esc(KeyEvent.KEYCODE_ESCAPE, R.drawable.ic_esc, false),
    Tab(KeyEvent.KEYCODE_TAB, R.drawable.ic_tab, false),
    Shift(KeyEvent.KEYCODE_SHIFT_LEFT, R.drawable.ic_shift, true),
    Ctrl(KeyEvent.KEYCODE_CTRL_LEFT, R.drawable.ic_ctrl, true),
    Alt(KeyEvent.KEYCODE_ALT_LEFT, R.drawable.ic_alt, true),
    DEL(KeyEvent.KEYCODE_FORWARD_DEL, R.drawable.ic_del, false),
}
