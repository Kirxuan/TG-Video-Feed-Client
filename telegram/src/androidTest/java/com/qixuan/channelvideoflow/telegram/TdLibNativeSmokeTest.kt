package com.qixuan.channelvideoflow.telegram

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdLibNativeSmokeTest {
    @Test
    fun officialTdLibLoadsAndReportsPinnedVersion() {
        System.loadLibrary("tdjni")
        val value = Client.execute<TdApi.OptionValue>(TdApi.GetOption("version"))

        assertTrue(value is TdApi.OptionValueString)
        assertEquals("1.8.66", (value as TdApi.OptionValueString).value)
    }
}
