package com.qixuan.channelvideoflow.feature.video

import android.view.Window
import android.view.WindowManager

/**
 * App-layer owner for the protected-content window flag.
 *
 * Telegram protection metadata stays outside Compose and is reduced to a boolean before this
 * controller touches the Android window.
 */
internal class WindowSecurityController(
    private val window: Window,
) {
    fun setProtectedContent(isProtected: Boolean) {
        if (isProtected) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
