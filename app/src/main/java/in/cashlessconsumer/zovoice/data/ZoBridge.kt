// Bridges the app's local transcript type and the SDK's history type.
// zo-voice owns ChatMessage (transcript persistence format); the SDK owns HistoryMessage.
package `in`.cashlessconsumer.zovoice.data

import dev.zocomputer.ask.HistoryMessage

fun ChatMessage.toHistory() = HistoryMessage(role = role, text = text, ts = ts)

fun HistoryMessage.toChat() = ChatMessage(role = role, text = text, ts = ts)
