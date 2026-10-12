package com.skyziyou.features.highlight

import com.skyziyou.BaseConfigScreen
import com.skyziyou.HighlightEntry
import com.skyziyou.I18n
import com.skyziyou.SkyButton
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * "Entity Highlights" editor sub-page. Each row is [on/off][entity name][colour][x]:
 * every living entity whose display name contains the entered text (case-insensitive)
 * gets the vanilla glowing outline in the chosen team colour. Colours cycle through
 * the 15 usable [ChatFormatting] values (black is skipped).
 */
class EntityHighlightsScreen : BaseConfigScreen("cat.highlights") {

    private var scroll = 0

    /** Temporary diagnostic overlay switch (not persisted): lists entities within 6 blocks. */
    private var debug = false

    private val rowH = 25
    private val startY = 58
    private val bottomReserve = 80

    override fun buildPage() {
        val left = centerX(344)
        val entries = SkyziyouConfig.entityHighlights
        scroll = scroll.coerceIn(0, maxScroll())

        addRenderableWidget(
            toggleButton(I18n.t("common.enabled"), SkyziyouConfig.entityHighlightsEnabled, left, 26, 116) {
                SkyziyouConfig.entityHighlightsEnabled = it
            },
        )

        addRenderableWidget(
            SkyButton(left + 228, 26, 116, 20, Component.literal("Debug: ${if (debug) "ON" else "OFF"}")) {
                debug = !debug
                it.message = Component.literal("Debug: ${if (debug) "ON" else "OFF"}")
            },
        )

        // one row per highlight: [on/off][name][colour][x]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val highlight = entries[index]

            addRenderableWidget(
                checkButton(left, y, highlight.enabled) { highlight.enabled = it },
            )

            val nameBox = EditBox(font, left + 26, y, 204, 20, Component.empty())
            nameBox.setMaxLength(64)
            nameBox.value = highlight.name
            nameBox.setHint(Component.literal(I18n.t("highlights.name_hint")))
            nameBox.setResponder { highlight.name = it }
            addRenderableWidget(nameBox)

            addRenderableWidget(
                SkyButton(left + 234, y, 76, 20, colorLabel(highlight.color)) {
                    highlight.color = nextColorName(highlight.color)
                    it.message = colorLabel(highlight.color)
                },
            )

            addRenderableWidget(
                SkyButton(
                    left + 314, y, 30, 20, Component.literal("X"),
                    style = SkyButton.Style.DANGER,
                ) {
                    entries.removeAt(index)
                    rebuildWidgets()
                },
            )
        }

        addRenderableWidget(
            SkyButton(centerX(200), height - 30, 200, 20, Component.literal(I18n.t("highlights.add"))) {
                entries.add(HighlightEntry())
                scroll = maxScroll()
                rebuildWidgets()
            },
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val left = centerX(344)

        graphics.text(font, I18n.t("highlights.header_name"), left + 26, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("highlights.header_color"), left + 234, startY - 10, SkyTheme.TEXT_DIM, false)

        graphics.text(font, I18n.t("highlights.legend"), left, height - 70, SkyTheme.TEXT_DIM, false)

        if (debug) renderDebugOverlay(graphics)
    }

    /**
     * Live dump of entities near the player: friendly type, own name, hologram text,
     * relative position and match state (GLOW = applied, WAIT = in the retry queue).
     * Temporary diagnostic for "why does X not glow" reports.
     */
    private fun renderDebugOverlay(graphics: GuiGraphicsExtractor) {
        val nearby = EntityHighlights.debugNearby()
        val boxW = 470
        val lineH = 10
        val extraLines = nearby.count { it.near.isNotBlank() }
        val boxH = 14 + (nearby.size + 1 + extraLines) * lineH
        // keep the box inside the content area, never covering the sidebar
        val x1 = maxOf(SkyTheme.SIDEBAR_W + 4, width - boxW - 4)
        val y1 = 4
        graphics.fill(x1, y1, x1 + boxW, y1 + boxH, 0xB0000000.toInt())
        graphics.text(
            font,
            "§eentities within 6 blocks (GLOW=highlighted, WAIT=retry queue)",
            x1 + 4,
            y1 + 3,
            -1,
            false,
        )
        var y = y1 + 3 + lineH
        for (e in nearby) {
            val stateColor = when (e.state) {
                "GLOW" -> "§a"
                "WAIT" -> "§e"
                else -> "§7"
            }
            val holo = if (e.hologramText.isNotBlank()) " tag:'${e.hologramText}'" else ""
            val line = "%s[%s] %s '%s'%s d(%.1f,%.1f,%.1f) %.1fm".format(
                stateColor,
                e.state,
                e.type,
                e.ownName,
                holo,
                e.dx, e.dy, e.dz,
                e.distance,
            )
            graphics.text(font, line, x1 + 4, y, -1, false)
            y += lineH
            if (e.near.isNotBlank()) {
                graphics.text(font, "      ${e.near}", x1 + 4, y, -1, false)
                y += lineH
            }
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        val newScroll = (scroll - verticalAmount.toInt()).coerceIn(0, maxScroll())
        if (newScroll != scroll) {
            scroll = newScroll
            rebuildWidgets()
        }
        return true
    }

    private fun visibleRows(): Int = ((height - startY - bottomReserve) / rowH).coerceAtLeast(1)

    private fun maxScroll(): Int = (SkyziyouConfig.entityHighlights.size - visibleRows()).coerceAtLeast(0)

    /** Colour name rendered in its own colour so the button doubles as a swatch. */
    private fun colorLabel(colorName: String): Component {
        val color = runCatching { ChatFormatting.valueOf(colorName) }.getOrDefault(ChatFormatting.RED)
        return Component.literal(color.name).withStyle(color)
    }

    private fun nextColorName(currentName: String): String {
        val current = runCatching { ChatFormatting.valueOf(currentName) }.getOrDefault(ChatFormatting.RED)
        val colors = EntityHighlights.COLORS
        val index = colors.indexOf(current)
        return colors[(index + 1).coerceAtLeast(0) % colors.size].name
    }
}
