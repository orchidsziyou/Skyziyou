package com.skyziyou.features.chat

import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.ActionQueue
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * User-defined chat rules ("Custom Chat Rules" settings sub-page).
 *
 * Each rule has a match pattern (case-insensitive contains, or a regex), an action
 * (leading slash = command, otherwise chat text) and a channel restriction
 * (ANY / PARTY / GUILD / ALL / PRIVATE / SYSTEM). Actions support the `[player]`
 * placeholder (replaced with the sender's clean username) and, in regex mode,
 * `$1`..`$9` capture-group substitutions.
 *
 * Safety: self-authored/echoed lines never trigger rules; every rule has a
 * cooldown; invalid regexes are reported once and disabled; all sends go through
 * the rate-limited [ActionQueue].
 */
object ChatRulesFeature {

    private const val RULE_COOLDOWN_MS = 3_000L

    /** `[me]` token inside a regex pattern, replaced with the escaped local player name. */
    private val mePlaceholderRegex = Regex("""\[me]""", RegexOption.IGNORE_CASE)

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** Rule index -> last fire time. */
    private val lastFiredAt = HashMap<Int, Long>()

    /** Regex cache: pattern text -> compiled regex, or null when the pattern is invalid. */
    private val regexCache = HashMap<String, Regex?>()

    /** Invalid patterns already reported locally, so the warning shows only once. */
    private val warnedBadPatterns = HashSet<String>()

    fun register() {
        ChatClassifier.handle(::onChat)
    }

    private fun onChat(chat: ClassifiedChat) {
        if (!SkyziyouConfig.chatRulesEnabled) return
        if (SkyziyouConfig.chatRulesHypixelOnly && !ChatClassifier.isOnHypixel()) return

        val self = ChatClassifier.selfName()
        // never react to our own party/guild/all echoes or to outgoing whispers
        if (chat.playerName?.equals(self, ignoreCase = true) == true) return

        val rules = SkyziyouConfig.chatRules
        rules.forEachIndexed { index, rule ->
            if (!rule.enabled || rule.pattern.isBlank() || rule.action.isBlank()) return@forEachIndexed

            val channel = ChatChannel.fromNameOrAny(rule.channel)
            if (channel != ChatChannel.ANY && channel != chat.channel) return@forEachIndexed

            // expand [me] to the local player's name, so rules can react to being mentioned,
            // e.g. pattern "@[me] !i" matches "@MyName !i"
            val effectivePattern = if (rule.regex) {
                mePlaceholderRegex.replace(rule.pattern) { Regex.escape(self) }
            } else {
                rule.pattern.replace("[me]", self, ignoreCase = true)
            }

            // regex groups captured for $1..$9 substitution; null in plain contains mode
            val match: MatchResult? = if (rule.regex) {
                val regex = compile(effectivePattern) ?: return@forEachIndexed
                regex.find(chat.message) ?: return@forEachIndexed
            } else {
                if (!chat.message.contains(effectivePattern, ignoreCase = true)) return@forEachIndexed
                null
            }

            val now = System.currentTimeMillis()
            if (now - (lastFiredAt[index] ?: 0L) < RULE_COOLDOWN_MS) return@forEachIndexed
            lastFiredAt[index] = now

            var action = rule.action.trim().replace("[player]", chat.playerName ?: "")
            if (match != null) {
                for (i in 1..minOf(9, match.groups.size - 1)) {
                    action = action.replace("$" + i, match.groupValues[i])
                }
            }

            if (action.startsWith("/")) {
                ActionQueue.enqueueCommand(action.substring(1))
            } else {
                ActionQueue.enqueueChat(action)
            }

            if (SkyziyouConfig.chatRulesLog) {
                val who = chat.playerName?.let { " §7from §e$it" } ?: ""
                mc.player?.sendSystemMessage(
                    Component.literal("§7[ChatRules] §a${channel.label}§7/$index$who §7-> §f$action"),
                )
            }
        }
    }

    /** Compiles and caches a regex; reports an invalid pattern locally exactly once. */
    private fun compile(pattern: String): Regex? {
        if (regexCache.containsKey(pattern)) return regexCache[pattern]
        val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
        regexCache[pattern] = regex
        if (regex == null && warnedBadPatterns.add(pattern)) {
            mc.player?.sendSystemMessage(
                Component.literal("§c[ChatRules] Invalid regex, rule disabled: §7$pattern"),
            )
        }
        return regex
    }
}
