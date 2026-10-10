package com.skyziyou.features.command

import com.skyziyou.SkyziyouConfig
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * User-defined command aliases ("Command Aliases" settings sub-page).
 *
 * Every enabled entry maps a command the player types (without the slash) to a
 * replacement that is executed instead: a command when the target starts with '/',
 * otherwise a chat message. Matching is exact (after trimming, case-insensitive) —
 * typing the alias with extra arguments does not match and the line is sent to the
 * server unchanged.
 *
 * Fabric fires [ClientSendMessageEvents.ALLOW_COMMAND] from inside the network
 * handler's send methods, including the one we use to forward the replacement, so
 * a re-entrancy guard prevents the forwarded command from being rewritten again.
 */
object CommandAliases {

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** True while the replacement of a matched alias is being sent. */
    private var forwarding = false

    fun register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register { typed ->
            if (forwarding || !SkyziyouConfig.aliasesEnabled) return@register true
            val player = mc.player ?: return@register true

            val typedCommand = typed.trim()
            val match = SkyziyouConfig.aliases.firstOrNull { entry ->
                entry.enabled &&
                    entry.alias.isNotBlank() &&
                    entry.target.isNotBlank() &&
                    entry.alias.trim().removePrefix("/")
                        .equals(typedCommand, ignoreCase = true)
            } ?: return@register true

            val target = match.target.trim()
            // re-entrant event: the guard makes the nested ALLOW_COMMAND pass through
            forwarding = true
            try {
                if (target.startsWith("/")) {
                    player.connection.sendCommand(target.removePrefix("/").trim())
                } else {
                    player.connection.sendChat(target)
                }
            } finally {
                forwarding = false
            }

            if (SkyziyouConfig.aliasesLog) {
                player.sendSystemMessage(
                    Component.literal("§7[Aliases] §f/$typedCommand §7-> §f$target"),
                )
            }
            // cancel the originally typed command: the replacement already went out
            false
        }
    }
}
