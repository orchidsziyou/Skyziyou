package com.skyziyou.features.party

import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.ActionQueue
import com.skyziyou.features.chat.ChatChannel
import com.skyziyou.features.chat.ChatClassifier
import com.skyziyou.features.chat.ClassifiedChat
import com.skyziyou.features.chat.PmDirection
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Auto Party Invite.
 *
 * When another player sends an incoming private message whose body is exactly
 * `!invite` (case-insensitive, surrounding whitespace ignored), the mod runs
 * `/p <player>` to invite them into the party.
 *
 * Chat lines arrive already classified from [ChatClassifier] (including the
 * "From stash:" exclusion and rank/name cleanup), so this feature only applies
 * its own policy: master switch, Hypixel-only switch, per-player cooldown and
 * logging. Sending goes through [ActionQueue] for rate limiting.
 */
object AutoInviteFeature {

    private const val TRIGGER_KEYWORD = "!invite"

    /** Per-player cooldown before the same player can trigger another invite. */
    private const val PLAYER_COOLDOWN_MS = 60_000L

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** Lower-cased player name -> last trigger time. */
    private val lastInviteAt = HashMap<String, Long>()

    /** Subscribes to classified chat. Call once during client init. */
    fun register() {
        ChatClassifier.handle(::onChat)
    }

    private fun onChat(chat: ClassifiedChat) {
        if (!SkyziyouConfig.autoInviteEnabled) return
        if (chat.channel != ChatChannel.PRIVATE || chat.pmDirection != PmDirection.INCOMING) return
        if (SkyziyouConfig.autoInviteHypixelOnly && !ChatClassifier.isOnHypixel()) return

        val name = chat.playerName ?: return
        if (name.equals(ChatClassifier.selfName(), ignoreCase = true)) return
        if (!chat.message.trim().equals(TRIGGER_KEYWORD, ignoreCase = true)) return

        // reserve the cooldown at trigger time so repeated PMs cannot queue duplicates
        val key = name.lowercase()
        val now = System.currentTimeMillis()
        if (now - (lastInviteAt[key] ?: 0L) < PLAYER_COOLDOWN_MS) return
        lastInviteAt[key] = now

        ActionQueue.enqueueCommand("p $name")

        if (SkyziyouConfig.autoInviteLog) {
            mc.player?.sendSystemMessage(
                Component.literal("§7[AutoInvite] §aInvited §e$name §7to your party §8(§7$TRIGGER_KEYWORD§8)"),
            )
        }
    }
}
