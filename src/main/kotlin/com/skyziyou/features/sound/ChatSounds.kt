package com.skyziyou.features.sound

import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.chat.ChatChannel
import com.skyziyou.features.chat.ChatClassifier
import com.skyziyou.features.chat.ClassifiedChat
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.DeathScreen
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

    /**
     * Window in which the death screen signal and the SkyBlock ☠ chat signal count
     * as one death, so a normal server that produces both only plays the sound once.
     */
    private const val DEATH_DEDUP_MS = 3_000L

    /**
     * SkyBlock death notices ("☠ You died to a Ghost.",
     * "☠ You were killed by …", dungeon ghost deaths included). Only matched on
     * SYSTEM lines: a player typing the same text in chat keeps their author name.
     */
    private val skyblockDeathRegex = Regex("""☠\s*You (?:died|were killed)\b""")

    /** Ticks between the repeated plays of one rule (~0.25s). */
    private const val REPEAT_GAP_TICKS = 5
    private const val MAX_REPEAT = 5

    private val mePlaceholderRegex = Regex("""\[me]""", RegexOption.IGNORE_CASE)

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** Rule index -> last play time. */
    private val lastFiredAt = HashMap<Int, Long>()

    /** Last time the death sound actually played; backs the two-signal dedup. */
    private var lastDeathFiredAt = 0L

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
        CustomSoundPlayer.warmUp() // decode imported sounds off-thread so first play never stalls
        // AFTER_INIT also re-fires when the screen is re-initialised on window
        // resize / F11, so gate on an edge and reset it when that exact screen
        // instance goes away (respawn / title).
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is DeathScreen && !deathScreenOpen) {
                deathScreenOpen = true
                ScreenEvents.remove(screen).register { deathScreenOpen = false }
                onDeath(null)
            }
        }
    }

    private var deathScreenOpen = false

    /** Fires the fixed death rule; shares the master switch and log toggle. */
    private fun onDeath(matchedText: String?) {
        if (!SkyziyouConfig.chatSoundsEnabled || !SkyziyouConfig.deathSoundEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastDeathFiredAt < DEATH_DEDUP_MS) return
        lastDeathFiredAt = now
        playRepeated(SkyziyouConfig.deathSound, SkyziyouConfig.deathSoundRepeat)
        if (SkyziyouConfig.chatSoundsLog) {
            // show exactly which line matched (clipped), so SkyBlock wording variants are easy to spot
            val clipped = matchedText?.trim()?.take(80)
            val source = if (clipped.isNullOrEmpty()) "§7vanilla death screen" else "§7chat §8→ §f$clipped"
            mc.player?.sendSystemMessage(
                Component.literal(
                    "§7[ChatSounds] §cdeath§7 ($source§7) §7-> §f${SoundLibrary.label(SkyziyouConfig.deathSound)} " +
                        "×${SkyziyouConfig.deathSoundRepeat.coerceIn(1, MAX_REPEAT)}",
                ),
            )
        }
    }

    private fun onChat(chat: ClassifiedChat) {
        if (!SkyziyouConfig.chatSoundsEnabled) return

        val self = ChatClassifier.selfName()
        // never react to our own echoed party/guild/all lines or outgoing whispers
        if (chat.playerName?.equals(self, ignoreCase = true) == true) return

        // SkyBlock never opens the vanilla death screen (the server handles death
        // before the client health hits zero; dungeon deaths become ghosts), so its
        // ☠ system notice is the only death signal there.
        if (chat.playerName == null && skyblockDeathRegex.containsMatchIn(chat.message)) {
            onDeath(chat.message)
            return
        }

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
        SoundLibrary.playAny(path)
        if (count > 1) pending.add(PendingSound(path, count - 1, REPEAT_GAP_TICKS))
    }

    private fun onTick() {
        // custom sounds play on their own Java Sound worker threads; no OpenAL reaping needed here
        if (pending.isEmpty()) return
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.delayTicks > 0) {
                entry.delayTicks--
                continue
            }
            SoundLibrary.playAny(entry.path)
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
