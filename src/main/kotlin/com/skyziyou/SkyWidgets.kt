package com.skyziyou

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component

/**
 * Palette and custom widgets for the SkyHanni-style config UI: a flat dark theme,
 * green check / red cross toggles and left category sidebar. Everything is drawn
 * with [GuiGraphicsExtractor] primitives, so the look is identical on 26.1/26.2.
 */
object SkyTheme {
    const val SIDEBAR_W = 120

    /** Full-screen dim behind the menu (the world stays faintly visible, like SkyHanni). */
    const val BG = 0xC0101014.toInt()

    /** Slightly lighter panel for the left category sidebar. */
    const val SIDEBAR_BG = 0xF21B1B21.toInt()

    const val ROW_BG = 0xFF2A2A33.toInt()
    const val ROW_HOVER = 0xFF383845.toInt()
    const val BTN_BG = 0xFF34343F.toInt()
    const val BTN_HOVER = 0xFF474757.toInt()
    const val DANGER_BG = 0xFF5A2828.toInt()
    const val DANGER_HOVER = 0xFF743434.toInt()
    const val TAB_HOVER = 0xFF2E2E38.toInt()
    const val TEXT = 0xFFE8E8EE.toInt()
    const val TEXT_DIM = 0xFF9A9AA8.toInt()
    const val GREEN = 0xFF4CD964.toInt()
    const val RED = 0xFFFF5A52.toInt()
}

/**
 * Flat dark button replacing the vanilla grey [Button]. [style] selects the colour
 * scheme (normal / red delete / sidebar tab); [accent] draws the green edge of the
 * currently active sidebar tab. Labels may contain § colour codes.
 */
open class SkyButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    private val style: Style = Style.NORMAL,
    private val accent: Boolean = false,
    onPress: Button.OnPress,
) : Button(x, y, width, height, message, onPress, DEFAULT_NARRATION) {

    enum class Style { NORMAL, DANGER, TAB }

    override fun extractContents(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (!visible) return
        val hover = active && isHoveredOrFocused
        val bg = when {
            !active -> SkyTheme.ROW_BG
            style == Style.DANGER -> if (hover) SkyTheme.DANGER_HOVER else SkyTheme.DANGER_BG
            style == Style.TAB -> if (hover) SkyTheme.TAB_HOVER else 0
            else -> if (hover) SkyTheme.BTN_HOVER else SkyTheme.BTN_BG
        }
        if (bg != 0) graphics.fill(x, y, x + width, y + height, bg)
        if (accent) graphics.fill(x, y, x + 2, y + height, SkyTheme.GREEN)
        val textColor = if (active) SkyTheme.TEXT else SkyTheme.TEXT_DIM
        graphics.centeredText(
            Minecraft.getInstance().font,
            message,
            x + width / 2,
            y + (height - 8) / 2,
            textColor,
        )
    }
}

/**
 * SkyHanni-style toggle: label on the left and a green ✓ / red ✕ on the right.
 * With an empty label it collapses to a compact check/cross box for list rows.
 */
class SkyToggle(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val label: String,
    initial: Boolean,
    private val onChange: (Boolean) -> Unit,
) : Button(
    x,
    y,
    width,
    height,
    Component.empty(),
    Button.OnPress { btn -> (btn as SkyToggle).onClick() },
    DEFAULT_NARRATION,
) {
    var value: Boolean = initial
        private set

    private fun onClick() {
        value = !value
        onChange(value)
    }

    override fun extractContents(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        if (!visible) return
        val hover = active && isHoveredOrFocused
        graphics.fill(x, y, x + width, y + height, if (hover) SkyTheme.ROW_HOVER else SkyTheme.ROW_BG)
        val font = Minecraft.getInstance().font
        if (label.isNotEmpty()) {
            graphics.text(font, Component.literal(label), x + 6, y + (height - 8) / 2, SkyTheme.TEXT, false)
        }
        val glyph = if (value) "§a✓" else "§c✕"
        val glyphX = if (label.isEmpty()) x + width / 2 else x + width - 12
        graphics.centeredText(font, Component.literal(glyph), glyphX, y + (height - 8) / 2, -1)
    }
}
