package com.skyziyou.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * Process-wide throttle for outgoing chat/commands: Hypixel temporarily mutes or warns
 * when commands arrive too fast, so every feature enqueues here instead of sending
 * directly. One entry is flushed per tick, spaced at least [INTERVAL_MS] apart.
 *
 * Commands are enqueued without the leading slash; plain text is sent as chat.
 */
object ActionQueue {

    private const val INTERVAL_MS = 1_000L
    private const val MAX_QUEUE = 32

    private val mc: Minecraft get() = Minecraft.getInstance()

    private val queue = ArrayDeque<Entry>()
    private var lastSentAt = 0L

    private data class Entry(val command: String?, val chat: String?)

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { flush() }
    }

    /** Enqueues "/<command>" (do not include the slash). */
    fun enqueueCommand(command: String) {
        val normalized = command.removePrefix("/").takeIf { it.isNotBlank() } ?: return
        if (queue.size < MAX_QUEUE) queue.addLast(Entry(normalized, null))
    }

    /** Enqueues a plain chat message. */
    fun enqueueChat(message: String) {
        if (message.isNotBlank() && queue.size < MAX_QUEUE) queue.addLast(Entry(null, message))
    }

    private fun flush() {
        if (queue.isEmpty()) return
        val player = mc.player
        if (player == null) {
            // disconnected: nothing to send to; drop the backlog
            queue.clear()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSentAt < INTERVAL_MS) return

        val entry = queue.removeFirstOrNull() ?: return
        lastSentAt = now
        if (entry.command != null) {
            player.connection.sendCommand(entry.command)
        } else if (entry.chat != null) {
            player.connection.sendChat(entry.chat)
        }
    }
}
