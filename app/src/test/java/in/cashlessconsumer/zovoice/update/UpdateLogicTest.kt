package `in`.cashlessconsumer.zovoice.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    @Test
    fun `isNewer across patch minor major`() {
        assertTrue(UpdateLogic.isNewer("0.3.1", "0.3.2"))
        assertTrue(UpdateLogic.isNewer("0.3.1", "0.4.0"))
        assertTrue(UpdateLogic.isNewer("0.9.9", "1.0.0"))
        assertFalse(UpdateLogic.isNewer("0.3.1", "0.3.1"))
        assertFalse(UpdateLogic.isNewer("0.3.2", "0.3.1"))
        assertFalse(UpdateLogic.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun `isNewer tolerates v prefix and padding`() {
        assertTrue(UpdateLogic.isNewer("0.3.1", "v0.3.2"))
        assertTrue(UpdateLogic.isNewer("0.3", "0.3.1"))
        assertFalse(UpdateLogic.isNewer("v0.3.1", "0.3.1"))
    }

    @Test
    fun `isNewer rejects garbage`() {
        assertFalse(UpdateLogic.isNewer("abc", "0.4.0"))
        assertFalse(UpdateLogic.isNewer("0.3.1", ""))
    }

    @Test
    fun `parses latest release json with debug apk preference`() {
        val body = """
        {"tag_name":"v0.4.0","name":"0.4.0","body":"notes here",
         "assets":[
           {"name":"sources.zip","browser_download_url":"https://x/src.zip","size":10},
           {"name":"zo-voice-v0.4.0-debug.apk","browser_download_url":"https://x/app.apk","size":16600000}
         ]}
        """.trimIndent()
        val rel = UpdateLogic.parseLatestRelease(body)!!
        assertEquals("v0.4.0", rel.tagName)
        assertEquals("https://x/app.apk", rel.apkUrl)
        assertEquals(16600000L, rel.apkSize)
        assertEquals("notes here", rel.notesHead)
    }

    @Test
    fun `parses release falling back to any apk`() {
        val body = """
        {"tag_name":"v1.2.3","name":"1.2.3","body":"",
         "assets":[{"name":"app-release.apk","browser_download_url":"https://x/r.apk","size":5}]}
        """.trimIndent()
        val rel = UpdateLogic.parseLatestRelease(body)!!
        assertEquals("https://x/r.apk", rel.apkUrl)
    }

    @Test
    fun `returns null without tag or apk`() {
        assertNull(UpdateLogic.parseLatestRelease("""{"tag_name":"v9"}"""))
        assertNull(UpdateLogic.parseLatestRelease("not json"))
        assertNull(
            UpdateLogic.parseLatestRelease(
                """{"tag_name":"v1","assets":[{"name":"a.zip","browser_download_url":"https://x"}]}""",
            ),
        )
    }
}
