package com.hilotrainer

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdaterTest {
    @Test fun parsesManifest() {
        val r = Updater.parse("""{"versionCode":12,"versionName":"8.1","apkUrl":"https://example.com/a.apk","sha256":"ABC","notes":"Hi","sizeBytes":5}""")
        assertEquals(12L, r.versionCode); assertEquals("8.1", r.versionName); assertEquals("abc", r.sha256); assertEquals(5L, r.sizeBytes)
    }
}
