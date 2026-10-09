package com.skyziyou.features.chat

/**
 * A Hypixel chat line's channel.
 *
 * [ANY] and the [label]s only exist for rule configuration — a line is never
 * classified as ANY; it means "rule fires on every channel". [COOP] is a real
 * channel (SkyBlock co-op chat) but is not offered in the rule editor, so it only
 * ever matches ANY rules. [SYSTEM] covers every line that is no player chat at
 * all (server notices, loot messages, ...).
 */
enum class ChatChannel(val label: String) {
    ANY("ANY"),
    PARTY("PC"),
    GUILD("GC"),
    ALL("AC"),
    PRIVATE("PM"),
    SYSTEM("SYS"),
    COOP("CO-OP"),
    ;

    companion object {
        /** Channels offered by the cycle button in the rules editor, in button order. */
        val selectable = listOf(ANY, PARTY, GUILD, ALL, PRIVATE, SYSTEM)

        fun fromNameOrAny(name: String?): ChatChannel =
            entries.firstOrNull { it.name == name } ?: ANY
    }
}

/** Whether a private message line was received or sent by the local player. */
enum class PmDirection { INCOMING, OUTGOING }

/**
 * A parsed chat line.
 *
 * @param channel the channel this line belongs to
 * @param playerName clean 1-16 char username of the sender, or null for SYSTEM
 * @param authorRaw sender substring including rank/guild tags and colour codes
 * @param message plain message body with all formatting codes removed
 * @param styledLine the full raw line rebuilt with section codes (for debug/advanced matching)
 * @param pmDirection From/To for [ChatChannel.PRIVATE], null otherwise
 */
data class ClassifiedChat(
    val channel: ChatChannel,
    val playerName: String?,
    val authorRaw: String?,
    val message: String,
    val styledLine: String,
    val pmDirection: PmDirection? = null,
)
