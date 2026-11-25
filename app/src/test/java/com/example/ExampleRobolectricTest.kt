package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FlashShare", appName)
  }

  @Test
  fun `qr payload encodes and decodes cleanly`() {
    val original = com.example.data.model.QrConnectionPayload(
      ssid = "FlashShare-5G",
      passphrase = "FS123456",
      hostIp = "192.168.43.1",
      port = 8988,
      deviceName = "Pixel Test",
      mode = "HOTSPOT",
      is5Ghz = true
    )
    val encoded = com.example.transfer.QrCodec.encodePayload(original)
    val decoded = com.example.transfer.QrCodec.decodePayload(encoded)
    assertEquals(original, decoded)
  }
}
