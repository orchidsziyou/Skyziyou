package com.skyziyou.features.sound

import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.chat.ChatChannel
import com.skyziyou.features.chat.ChatClassifier
import com.skyziyou.features.chat.ClassifiedChat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * "Chat Sounds" feature: like Custom Chat Rules but the action is playing a
 * built-in vanilla sound locally instead of sending anything. Every rule has a
 * match pattern (contains or regex, per-rule case toggle), a channel restriction
 * (ANY/PC/GC/AC/PM/SYS), a sound id from [SoundLibrary] and a repeat count
 * (1..5 quick plays). Nothing is ever sent to the server — pure client audio.
 *
 * Safety mirrors ChatRulesFeature: self-authored lines never fire, each rule has
 * a short cooldown (overlapping chat spam must not machine-gun sounds), and an
 * invalid regex is reported once and ignored rather than crashing the client.
 * Repeats are driven from the client tick (never a background thread, so all
 * [SoundLibrary.play] calls stay on the render thread).
 */
object ChatSounds {

    private const val RULE_COOLDOWN_MS = 2_000L
    private const val MAX_REGEX_INPUT = 256

    /** Ticks between the repeated plays of one rule (~0.25s). */
    private const val REPEAT_GAP_TICKS = 5
    private const val MAX_REPEAT = 5

    private val mePlaceholderRegex = Regex("""\[me]""", RegexOption.IGNORE_CASE)

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** Rule index -> last play time. */
    private val lastFiredAt = HashMap<Int, Long>()

    /** Regex cache: pattern text + case flag -> compiled regex, or null when invalid. */
    private val regexCache = HashMap<String, Regex?>()

    /** Invalid patterns already reported locally, so the warning shows only once. */
    private val warnedBadPatterns = HashSet<String>()

    /** Scheduled follow-up plays; first play of every burst is always immediate. */
    private data class PendingSound(val path: String, var remaining: Int, var delayTicks: Int)
    private val pending = ArrayList<PendingSound>()

    fun register() {
        ChatClassifier.handle(::onChat)
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
    }

    private fun onChat(chat: ClassifiedChat) {
        if (!SkyziyouConfig.chatSoundsEnabled) return

        val self = ChatClassifier.selfName()
        // never react to our own echoed party/guild/all lines or outgoing whispers
        if (chat.playerName?.equals(self, ignoreCase = true) == true) return

        SkyziyouConfig.chatSounds.forEachIndexed { index, rule ->
            if (!rule.enabled || rule.pattern.isBlank()) return@forEachIndexed

            val channel = ChatChannel.fromNameOrAny(rule.channel)
            if (channel != ChatChannel.ANY && channel != chat.channel) return@forEachIndexed

            val effectivePattern = if (rule.regex) {
                mePlaceholderRegex.replace(rule.pattern) { Regex.escape(self) }
            } else {
                rule.pattern.replace("[me]", self, ignoreCase = true)
            }

            val matched = if (rule.regex) {
                val regex = compile(effectivePattern, rule.ignoreCase) ?: return@forEachIndexed
                regex.containsMatchIn(chat.message.take(MAX_REGEX_INPUT))
            } else {
                chat.message.contains(effectivePattern, ignoreCase = rule.ignoreCase)
            }
            if (!matched) return@forEachIndexed

            val now = System.currentTimeMillis()
            if (now - (lastFiredAt[index] ?: 0L) < RULE_COOLDOWN_MS) return@forEachIndexed
            lastFiredAt[index] = now

            playRepeated(rule.sound, rule.repeat)

            if (SkyziyouConfig.chatSoundsLog) {
                val who = chat.playerName?.let { " §7from §e$it" } ?: ""
                mc.player?.sendSystemMessage(
                    Component.literal(
                        "§7[ChatSounds] §a${channel.label}§7/$index$who §7-> §f${SoundLibrary.label(rule.sound)} ×${rule.repeat.coerceIn(1, MAX_REPEAT)}",
                    ),
                )
            }
        }
    }

    /**
     * Plays [path] immediately and schedules [times] - 1 follow-up plays spaced
     * [REPEAT_GAP_TICKS] apart. Used both for real matches and the in-screen
     * preview, so the preview reproduces the full repeat pattern.
     */
    fun playRepeated(path: String, times: Int) {
        val count = times.coerceIn(1, MAX_REPEAT)
        SoundLibrary.play(path)
        if (count > 1) pending.add(PendingSound(path, count - 1, REPEAT_GAP_TICKS))
    }

    private fun onTick() {
        if (pending.isEmpty()) return
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.delayTicks > 0) {
                entry.delayTicks--
                continue
            }
            SoundLibrary.play(entry.path)
            entry.remaining--
            if (entry.remaining <= 0) iterator.remove() else entry.delayTicks = REPEAT_GAP_TICKS
        }
    }

    /** Compiles and caches a regex; reports an invalid pattern locally exactly once. */
    private fun compile(pattern: String, ignoreCase: Boolean): Regex? {
        val key = "$ignoreCase\n$pattern"
        if (regexCache.containsKey(key)) return regexCache[key]
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val regex = runCatching { Regex(pattern, options) }.getOrNull()
        regexCache[key] = regex
        if (regex == null && warnedBadPatterns.add(key)) {
            mc.player?.sendSystemMessage(
                Component.literal("§c[ChatSounds] Invalid regex, sound rule disabled: §7$pattern"),
            )
        }
        return regex
    }
}
