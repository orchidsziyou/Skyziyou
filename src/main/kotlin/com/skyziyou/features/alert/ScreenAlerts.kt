package com.skyziyou.features.alert

import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.chat.ChatChannel
import com.skyziyou.features.chat.ChatClassifier
import com.skyziyou.features.chat.ClassifiedChat
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.awt.Color

/**
 * User-defined "chat text -> big on-screen banner" reminders.
 *
 * Every configured [com.skyziyou.AlertEntry] watches received chat lines of its
 * restricted channel (ANY by default; case-insensitive contains, also matching
 * system notices when ANY/SYSTEM is selected). On a match,
 * its display text is rendered as a centred 2x banner near the top of the screen
 * for [DURATION_MS]. The same banner re-firing just refreshes its timer instead of
 * stacking duplicates; distinct banners stack vertically (newest added last,
 * [MAX_ACTIVE] visible at most). Self-authored chat echoes never trigger alerts.
 *
 * Rendering is a plain HUD draw (same mechanism as the wheel): no packets, no
 * Mixin, text supports § colour codes and the `[player]` / `[me]` placeholders.
 */
object ScreenAlerts {

    private const val DURATION_MS = 4_000L
    private const val MAX_ACTIVE = 5

    /** Adjustable banner font scale bounds/step, shared with the settings screen. */
    const val MIN_SCALE = 1.0f
    const val MAX_SCALE = 5.0f
    const val SCALE_STEP = 0.25f
    const val DEFAULT_SCALE = 2.5f

    private val id: Identifier = Identifier.fromNamespaceAndPath("skyziyou", "screen_alerts")
    private val backgroundColor: Int = Color(0, 0, 0, 140).rgb
    // reddish gold/amber (红黄色); § codes inside a banner can still override this
    const val TEXT_COLOR = 0xFFFFB400.toInt()

    /** Effective, clamped banner scale from config. */
    fun fontScale(): Float = SkyziyouConfig.screenAlertFontScale.coerceIn(MIN_SCALE, MAX_SCALE)

    private val mc: Minecraft get() = Minecraft.getInstance()

    private class Active(val text: String, var expireAt: Long)

    private val active = ArrayDeque<Active>()

    fun register() {
        ChatClassifier.handle(::onChat)
        HudElementRegistry.addLast(id) { graphics, _ -> render(graphics) }
    }

    private fun onChat(chat: ClassifiedChat) {
        if (!SkyziyouConfig.screenAlertsEnabled) return

        val self = ChatClassifier.selfName()
        // never pop alerts for our own party/guild/all echoes
        if (chat.playerName?.equals(self, ignoreCase = true) == true) return

        for (rule in SkyziyouConfig.screenAlerts) {
            if (!rule.enabled || rule.trigger.isBlank()) continue
            val channel = ChatChannel.fromNameOrAny(rule.channel)
            if (channel != ChatChannel.ANY && channel != chat.channel) continue
            if (!chat.message.contains(rule.trigger, ignoreCase = true)) continue

            val text = rule.text
                .replace("[player]", chat.playerName ?: "", ignoreCase = true)
                .replace("[me]", self, ignoreCase = true)
                .trim()
                .ifBlank { rule.trigger.trim() }
            show(text)
        }
    }

    private fun show(text: String) {
        val now = System.currentTimeMillis()
        active.firstOrNull { it.text == text }?.let {
            it.expireAt = now + DURATION_MS
            return
        }
        active.addLast(Active(text, now + DURATION_MS))
        while (active.size > MAX_ACTIVE) active.removeFirst()
    }

    private fun render(g: GuiGraphicsExtractor) {
        if (active.isEmpty()) return
        val now = System.currentTimeMillis()
        active.removeAll { now > it.expireAt }
        if (active.isEmpty()) return

        val font = mc.font
        val centerX = mc.window.guiScaledWidth / 2
        val baseY = (mc.window.guiScaledHeight * 0.16).toInt()
        val scale = fontScale()
        // gap grows with the scale so stacked banners never overlap
        val rowGap = (12f * scale).toInt()

        active.forEachIndexed { index, banner ->
            drawBanner(g, font, banner.text, centerX, baseY + index * rowGap, scale)
        }
    }

    /**
     * Draw one centred banner: translucent dark bar + shadowed scaled text.
     * Shared by the live HUD render and the size preview on the settings screen.
     */
    fun drawBanner(g: GuiGraphicsExtractor, font: Font, text: String, centerX: Int, y: Int, scale: Float) {
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(centerX.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        try {
            val width = font.width(text)
            g.fill(-width / 2 - 3, -5, width / 2 + 3, 6, backgroundColor)
            g.text(font, text, -width / 2, -4, TEXT_COLOR, true)
        } finally {
            pose.popMatrix()
        }
    }
}
