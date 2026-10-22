package com.skyziyou.features.chat

import com.skyziyou.ChatRuleEntry
import com.skyziyou.I18n
import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.ActionQueue
import com.skyziyou.features.alert.ScreenAlerts
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * User-defined chat rules ("Custom Chat Rules" settings sub-page).
 *
 * Each rule has a match pattern (contains or regex, each optionally case-sensitive),
 * an action (leading slash = command, otherwise chat text) and a channel restriction
 * (ANY / PARTY / GUILD / ALL / PRIVATE / SYSTEM). Each rule may delay its action by
 * 0-10 seconds; delayed actions are cancelled when the rule is deleted/turned off,
 * the master switch is toggled off, the player disconnects, or (with the Hypixel-only
 * restriction) leaves Hypixel. Actions support the `[player]` placeholder (replaced
 * with the sender's clean username) and, in regex mode, `$1`..`$9` capture-group
 * substitutions.
 *
 * Safety: self-authored/echoed lines never trigger rules; every rule has a
 * cooldown; invalid regexes are reported once and disabled; all sends go through
 * the rate-limited [ActionQueue].
 */
object ChatRulesFeature {

    private const val RULE_COOLDOWN_MS = 3_000L

    /** Upper bound on delayed actions waiting to fire (never approached under the 3 s rule cooldown). */
    private const val MAX_PENDING = 32

    /** Per-rule configurable delay ceiling, in seconds. */
    const val MAX_DELAY_SECONDS = 10

    /**
     * Max chat-line length fed to a USER-supplied regex. A pathological pattern
     * (catastrophic backtracking such as "(a+)+") is exponential even on short
     * inputs, so this is only a secondary bound — [RegexSafety] rejects such
     * patterns before they are ever used. Hypixel chat lines are never longer
     * than this anyway, so matching is unaffected.
     */
    private const val MAX_REGEX_INPUT = 256

    /** `[me]` token inside a regex pattern, replaced with the escaped local player name. */
    private val mePlaceholderRegex = Regex("""\[me]""", RegexOption.IGNORE_CASE)

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** Rule index -> last fire time. */
    private val lastFiredAt = HashMap<Int, Long>()

    /** Regex cache: pattern text + case flag -> compiled regex, or null when invalid. */
    private val regexCache = HashMap<String, Regex?>()

    /** Invalid patterns already reported locally, so the warning shows only once. */
    private val warnedBadPatterns = HashSet<String>()

    /**
     * Actions waiting out their per-rule delay. Holds the final, placeholder-expanded
     * text and the originating [ChatRuleEntry] object (identity, not index), so a
     * rule deleted or disabled while pending is detected directly.
     */
    private data class PendingAction(val rule: ChatRuleEntry, val command: String?, val chat: String?, val dueAt: Long)
    private val pending = ArrayList<PendingAction>()

    fun register() {
        ChatClassifier.handle(::onChat)
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
    }

    /**
     * Flushes due delayed actions. The whole backlog is dropped on disconnect,
     * while the master switch is off, or when no longer on Hypixel (with the
     * Hypixel-only restriction) — a delayed command must never fire after the
     * player has left the context it matched in. Individual entries are also
     * cancelled when their rule is deleted or switched off.
     */
    private fun onTick() {
        if (pending.isEmpty()) return
        if (mc.player == null ||
            !SkyziyouConfig.chatRulesEnabled ||
            (SkyziyouConfig.chatRulesHypixelOnly && !ChatClassifier.isOnHypixel())
        ) {
            pending.clear()
            return
        }
        val rules = SkyziyouConfig.chatRules
        val now = System.currentTimeMillis()
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.rule !in rules || !entry.rule.enabled) {
                iterator.remove()
                continue
            }
            if (now >= entry.dueAt) {
                if (entry.command != null) ActionQueue.enqueueCommand(entry.command)
                else if (entry.chat != null) ActionQueue.enqueueChat(entry.chat)
                iterator.remove()
            }
        }
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
                val regex = compile(effectivePattern, rule.ignoreCase) ?: return@forEachIndexed
                regex.find(chat.message.take(MAX_REGEX_INPUT)) ?: return@forEachIndexed
            } else {
                if (!chat.message.contains(effectivePattern, ignoreCase = rule.ignoreCase)) return@forEachIndexed
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

            val accepted = if (action.startsWith("/")) {
                schedule(rule, action.substring(1), null)
            } else {
                schedule(rule, null, action)
            }

            if (SkyziyouConfig.chatRulesLog) {
                val who = chat.playerName?.let { " §7from §e$it" } ?: ""
                val delay = rule.delaySeconds.coerceIn(0, MAX_DELAY_SECONDS)
                val whenSent = if (delay > 0) " §7in §e${delay}s" else ""
                val note = if (accepted) "" else " §c(pending queue full, skipped)"
                mc.player?.sendSystemMessage(
                    Component.literal("§7[ChatRules] §a${channel.label}§7/$index$who$whenSent §7-> §f$action$note"),
                )
            }
        }
    }

    /** Sends now for a zero delay, otherwise parks the final action until its due tick. */
    private fun schedule(rule: ChatRuleEntry, command: String?, chat: String?): Boolean {
        val delaySeconds = rule.delaySeconds.coerceIn(0, MAX_DELAY_SECONDS)
        if (delaySeconds <= 0) {
            if (command != null) ActionQueue.enqueueCommand(command) else ActionQueue.enqueueChat(chat ?: "")
            return true
        }
        if (pending.size >= MAX_PENDING) return false
        pending.add(PendingAction(rule, command, chat, System.currentTimeMillis() + delaySeconds * 1000L))
        // one-shot on-screen notice through the existing banner HUD; the banner
        // itself expires on its own, nothing is queued for the execution moment
        ScreenAlerts.notifyExternal(delayNotice(delaySeconds, if (command != null) "/$command" else chat.orEmpty()))
        return true
    }

    /**
     * Builds the banner text for a delayed action. Text is drawn at the user's
     * Screen Alerts scale, so a long command is trimmed to the on-screen width
     * (the action is echoed back, never sent anywhere).
     */
    private fun delayNotice(seconds: Int, action: String): String {
        val scale = ScreenAlerts.fontScale()
        val maxWidth = (mc.window.guiScaledWidth * 0.9f / scale).toInt()
        fun build(body: String) = I18n.t("chatrules.delay_notice", seconds, body)
        var body = action
        if (mc.font.width(build(body)) > maxWidth) {
            var end = body.length
            while (end > 1 && mc.font.width(build(body.take(end) + "…")) > maxWidth) end--
            body = body.take(end) + "…"
        }
        return build(body)
    }

    /** Compiles, ReDoS-validates and caches a regex; reports a bad pattern locally exactly once. */
    private fun compile(pattern: String, ignoreCase: Boolean): Regex? {
        val key = "$ignoreCase\n$pattern"
        if (regexCache.containsKey(key)) return regexCache[key]
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val compiled = runCatching { Regex(pattern, options) }.getOrNull()
        val regex = compiled?.takeIf { runCatching { RegexSafety.isSafe(it) }.getOrDefault(false) }
        regexCache[key] = regex
        if (regex == null && warnedBadPatterns.add(key)) {
            val reason = if (compiled != null) "Slow/unsafe regex (catastrophic backtracking)" else "Invalid regex"
            mc.player?.sendSystemMessage(
                Component.literal("§c[ChatRules] $reason, rule disabled: §7$pattern"),
            )
        }
        return regex
    }
}
