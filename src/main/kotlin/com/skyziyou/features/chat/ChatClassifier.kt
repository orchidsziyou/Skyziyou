package com.skyziyou.features.chat

import com.skyziyou.mixin.ClientCommonPacketListenerAccessor
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

/**
 * Single entry point for player chat parsing. Every incoming game chat line is
 * classified into a [ClassifiedChat] (channel + clean sender + clean body) and
 * pushed to all registered handlers, so each feature does not need its own regex set.
 *
 * Channel prefixes are matched against the line rebuilt WITH section colour codes
 * (e.g. the literal `§9Party §8>`), so a player typing "Party > foo: bar" into all
 * chat cannot spoof a party-chat line. Only the body players actually type is read
 * as plain text.
 *
 * Patterns are adapted from SkyHanni's PlayerChatManager (battle-tested on Hypixel).
 */
object ChatClassifier {

    private val mc: Minecraft get() = Minecraft.getInstance()

    private val handlers = mutableListOf<(ClassifiedChat) -> Unit>()

    /** Subscribes to every classified chat line. Call once during client init. */
    fun handle(handler: (ClassifiedChat) -> Unit) {
        handlers.add(handler)
    }

    fun register() {
        ClientReceiveMessageEvents.GAME.register { component, overlay ->
            if (!overlay) classify(component)
        }
    }

    // ---------------- patterns (matched on the colour-coded line, §r removed) ----------------
    //
    // Two-stage parsing: a strict, server-coloured PREFIX decides the channel (players
    // cannot type § codes, so the prefix cannot be spoofed); the remainder is then split
    // loosely, so rank/guild tags of any content or colour (e.g. Chinese guild tags like
    // [原神玩家]) do not break sender extraction.

    private data class Prefix(val channel: ChatChannel, val regex: Regex)

    private val channelPrefixes = listOf(
        Prefix(ChatChannel.COOP, Regex("""^§bCo-op > (?<rest>.*)$""")),
        Prefix(ChatChannel.PARTY, Regex("""^§9Party §8> (?<rest>.*)$""")),
        // officer chat shares guild's shape with an aqua prefix; treated as the GUILD channel
        Prefix(ChatChannel.GUILD, Regex("""^§3Officer(?: §8)?> (?<rest>.*)$""")),
        Prefix(ChatChannel.GUILD, Regex("""^§2Guild > (?<rest>.*)$""")),
    )

    // author runs lazily up to the coloured message colon; tags/colours inside are irrelevant
    private val authorMessageSplit =
        Regex("""^(?<author>.*?)(?:§f|§7): (?<message>.*)$""")

    // lobby / SkyBlock all chat: optional [level], optional prefix icon, rank, name, §f/§7 colon
    private val globalPattern =
        Regex("""^(?:\[\d+] )?(?<author>(?:(?:§.)*[^ ] )?(?:(?:§.)*\[[^\]]+\] )?[^ ]+?)(?:§f|§7): (?<message>.*)""")

    // whispers carry no colour codes in the span; matched on the plain line
    private val incomingPmPattern = Regex("""^From (?:\[[^\]]*\]\s+)?(\S[^:]*?):\s*(.*)$""")
    private val outgoingPmPattern = Regex("""^To (?:\[[^\]]*\]\s+)?(\S[^:]*?):\s*(.*)$""")

    private val codePattern = Regex("§.")
    private val bracketTagPattern = Regex("""\[[^\]]*]""")
    private val usernameRunPattern = Regex("""[A-Za-z0-9_]{1,16}""")

    /**
     * Pulls the clean Minecraft username out of an author substring: drops every
     * bracketed tag (network rank, guild rank/tag in any language) and takes the last
     * username-shaped token. Usernames never appear inside brackets on Hypixel.
     */
    private fun extractUsername(authorRaw: String): String? {
        val clean = authorRaw.replace(codePattern, "").replace(bracketTagPattern, " ")
        return usernameRunPattern.findAll(clean).lastOrNull()?.value
    }

    private fun classify(component: Component) {
        val plain = component.string
        val styled = component.toLegacySectionString()

        fun playerChat(
            channel: ChatChannel,
            authorRaw: String?,
            messageStyled: String?,
            direction: PmDirection? = null,
        ): ClassifiedChat? {
            if (authorRaw == null) return null
            val name = extractUsername(authorRaw) ?: return null
            val body = messageStyled?.replace(codePattern, "") ?: ""
            return ClassifiedChat(channel, name, authorRaw, body, styled, direction)
        }

        val prefixHit = channelPrefixes.firstNotNullOfOrNull { prefix ->
            prefix.regex.find(styled)?.let { prefix.channel to it.groups["rest"]!!.value }
        }

        val result = if (prefixHit != null) {
            val (channel, rest) = prefixHit
            authorMessageSplit.find(rest)?.let {
                playerChat(channel, it.groups["author"]?.value, it.groups["message"]?.value)
            }
        } else if (!plain.startsWith("From stash:", ignoreCase = true)) {
            // "From stash: ..." (sack withdraw notice) shares the whisper shape and must stay SYSTEM
            incomingPmPattern.find(plain)?.let {
                playerChat(ChatChannel.PRIVATE, it.groupValues[1], it.groupValues[2], PmDirection.INCOMING)
            } ?: outgoingPmPattern.find(plain)?.let {
                playerChat(ChatChannel.PRIVATE, it.groupValues[1], it.groupValues[2], PmDirection.OUTGOING)
            } ?: globalPattern.find(styled)?.let {
                playerChat(ChatChannel.ALL, it.groups["author"]?.value, it.groups["message"]?.value)
            }
        } else null

        val classified = result ?: ClassifiedChat(ChatChannel.SYSTEM, null, null, plain, styled)
        handlers.forEach { it(classified) }
    }

    /**
     * Hypixel brands the connection as "Hypixel BungeeCord". The backing brand field
     * is protected and exposed via [ClientCommonPacketListenerAccessor]; the brand
     * packet has always arrived before any chat line is shown.
     */
    fun isOnHypixel(): Boolean {
        val listener = mc.player?.connection as? ClientCommonPacketListenerAccessor ?: return false
        return listener.serverBrand?.contains("hypixel", ignoreCase = true) == true
    }

    /** The local player's own username, used to ignore self-echoed party/guild lines. */
    fun selfName(): String = mc.user.name

    /**
     * Walks the component tree and rebuilds a section-code string (`§9Party ...`).
     * Legacy colours/formatters are emitted; arbitrary RGB colours are skipped (channel
     * prefixes only ever use legacy codes). `§r` resets are dropped afterwards, matching
     * the shape SkyHanni's patterns expect.
     */
    private fun Component.toLegacySectionString(): String {
        val out = StringBuilder()
        var previous = Style.EMPTY

        visit(object : FormattedText.StyledContentConsumer<Unit> {
            override fun accept(style: Style, text: String): Optional<Unit> {
                if (style != previous) {
                    if (style.isEmpty) {
                        out.append("§r")
                    } else {
                        style.color?.value?.let { rgb -> legacyByRgb[rgb]?.let { out.append('§').append(it) } }
                        if (style.isObfuscated) out.append("§k")
                        if (style.isBold) out.append("§l")
                        if (style.isStrikethrough) out.append("§m")
                        if (style.isUnderlined) out.append("§n")
                        if (style.isItalic) out.append("§o")
                    }
                    previous = style
                }
                out.append(text)
                return Optional.empty()
            }
        }, Style.EMPTY)

        return out.toString().replace("§r", "")
    }

    /** ChatFormatting declaration order maps directly to the legacy code chars. */
    private val legacyByRgb: Map<Int, Char> = buildMap {
        val chars = "0123456789abcdef"
        ChatFormatting.values().take(16).forEachIndexed { index, formatting ->
            formatting.color?.let { put(it, chars[index]) }
        }
    }
}
