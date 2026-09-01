package com.qixuan.channelvideoflow.feature.video

import android.app.Activity
import android.view.WindowManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WindowSecurityControllerTest {
    @Test
    fun protectedContentSetsFlagAndLeavingContentClearsIt() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val controller = WindowSecurityController(activity.window)

        controller.setProtectedContent(true)
        assertTrue(activity.window.hasSecureFlag())

        controller.setProtectedContent(false)
        assertFalse(activity.window.hasSecureFlag())
    }

    private fun android.view.Window.hasSecureFlag(): Boolean =
        attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}
