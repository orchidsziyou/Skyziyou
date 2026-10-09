package com.skyziyou.features.party

import com.skyziyou.SkyziyouConfig
import com.skyziyou.mixin.ClientCommonPacketListenerAccessor
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Auto Party Invite.
 *
 * When another player sends a private message whose body is exactly `!invite`
 * (case-insensitive, surrounding whitespace ignored), the mod runs `/p <player>`
 * to invite them into the party.
 *
 * Incoming Hypixel PMs look like `From [MVP+] Player: message` (the rank prefix is
 * optional), while outgoing ones start with `To`, so only `From` lines are matched.
 * `From stash: ...` (Hypixel sack/stash withdraw notices) shares the same shape and
 * is explicitly rejected.
 *
 * Anti-spam:
 *  - the same player can only trigger once per [PLAYER_COOLDOWN_MS]
 *  - outgoing commands are spaced [GLOBAL_COMMAND_INTERVAL_MS] apart; triggers that
 *    arrive faster are queued (FIFO, distinct players, capped at [MAX_QUEUE]) instead
 *    of dropped, so a burst of different invites is never lost to rate limiting
 */
object AutoInviteFeature {

    /** Keyword (lower-cased compare) that triggers an invite. */
    private const val TRIGGER_KEYWORD = "!invite"

    /** Per-player cooldown before the same player can trigger another invite. */
    private const val PLAYER_COOLDOWN_MS = 60_000L

    /** Minimum gap between any two outgoing invite commands. */
    private const val GLOBAL_COMMAND_INTERVAL_MS = 1_000L

    /** Maximum number of invites held in the outgoing queue. */
    private const val MAX_QUEUE = 10

    private val mc: Minecraft get() = Minecraft.getInstance()

    // `From <optional [rank]> author: message`; rank text may itself contain spaces/brackets
    private val privateMessagePattern = Regex("""^From (?:\[[^\]]*]\s+)?(\S[^:]*?):\s*(.*)$""")

    /** Minecraft usernames are 1-16 chars from this set; taken as the author suffix. */
    private val nameSuffixPattern = Regex("""[A-Za-z0-9_]{1,16}$""")

    /** FIFO of display-case player names waiting for an invite command. */
    private val queue = ArrayDeque<String>()

    /** Lower-cased names already queued, so one player cannot fill the queue. */
    private val queued = HashSet<String>()

    /** Lower-cased player name -> last trigger time (cooldown). */
    private val lastInviteAt = HashMap<String, Long>()

    private var lastCommandAt = 0L

    /** Wires the feature into Fabric's chat and tick events. Call once during client init. */
    fun register() {
        // GAME fires for every displayed game chat line (after allow/modify); read-only here
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) handleChatLine(message.string)
        }
        ClientTickEvents.END_CLIENT_TICK.register { processQueue() }
    }

    private fun handleChatLine(line: String) {
        if (!SkyziyouConfig.autoInviteEnabled) return
        if (mc.player == null) return
        if (SkyziyouConfig.autoInviteHypixelOnly && !isOnHypixel()) return

        val text = line.trimStart()
        // same shape as a PM ("From stash: Wheat") but is a sack withdraw notice
        if (text.startsWith("From stash:", ignoreCase = true)) return

        val match = privateMessagePattern.matchEntire(text) ?: return
        val author = match.groupValues[1].trim()
        val body = match.groupValues[2].trim()
        if (!body.equals(TRIGGER_KEYWORD, ignoreCase = true)) return

        // pull the real username out of any trailing decoration (icons etc.)
        val name = nameSuffixPattern.find(author)?.value ?: return
        if (name.equals(mc.user.name, ignoreCase = true)) return

        val key = name.lowercase()
        val now = System.currentTimeMillis()
        // reserve the cooldown at trigger time so repeated PMs cannot queue duplicates
        if (now - (lastInviteAt[key] ?: 0L) < PLAYER_COOLDOWN_MS) return
        lastInviteAt[key] = now

        // only reserve the dedupe slot when the invite actually enters the queue,
        // otherwise a rejected trigger would block this player forever
        if (queue.size < MAX_QUEUE && queued.add(key)) {
            queue.addLast(name)
        }
    }

    /** Sends one queued invite per tick when the global interval allows. */
    private fun processQueue() {
        if (queue.isEmpty()) return
        val player = mc.player
        if (player == null) {
            // left the world: drop pending invites and let cooldowns simply expire
            queue.clear()
            queued.clear()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastCommandAt < GLOBAL_COMMAND_INTERVAL_MS) return

        val name = queue.removeFirstOrNull() ?: return
        queued.remove(name.lowercase())
        lastCommandAt = now
        player.connection.sendCommand("p $name")

        if (SkyziyouConfig.autoInviteLog) {
            player.sendSystemMessage(
                Component.literal("§7[AutoInvite] §aInvited §e$name §7to your party §8(§7$TRIGGER_KEYWORD§8)"),
            )
        }
    }

    /**
     * Hypixel brands the connection as "Hypixel BungeeCord". The brand packet has
     * always arrived long before any chat line is shown, so reading it on demand is safe.
     * The backing field is protected; [com.skyziyou.mixin.ClientCommonPacketListenerAccessor]
     * exposes it.
     */
    private fun isOnHypixel(): Boolean {
        val listener = mc.player?.connection as? ClientCommonPacketListenerAccessor ?: return false
        return listener.serverBrand?.contains("hypixel", ignoreCase = true) == true
    }
}
