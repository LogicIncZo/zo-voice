package `in`.cashlessconsumer.zovoice.data

import org.junit.Assert.assertEquals
import dev.zocomputer.ask.HistoryMessage
import org.junit.Test

class ZoBridgeTest {

    @Test
    fun `chat message round-trips through history`() {
        val original = ChatMessage(role = "assistant", text = "UPI MDR is zero.", ts = 1789894800000L)
        val back = original.toHistory().toChat()
        assertEquals(original, back)
    }

    @Test
    fun `history message maps to chat`() {
        val h = HistoryMessage(role = "user", text = "catch me up", ts = 42L)
        val c = h.toChat()
        assertEquals("user", c.role)
        assertEquals("catch me up", c.text)
        assertEquals(42L, c.ts)
    }
}
