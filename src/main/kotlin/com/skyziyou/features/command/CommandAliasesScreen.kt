package com.skyziyou.features.command

import com.skyziyou.BaseConfigScreen
import com.skyziyou.CommandAliasEntry
import com.skyziyou.I18n
import com.skyziyou.SkyButton
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * "Command Aliases" editor sub-page. Each row has two boxes:
 *   typed command (with or without the leading '/') | replacement that actually runs,
 * plus an on/off switch and a delete button. A target starting with '/' runs as a
 * command; anything else is sent as a chat message.
 */
class CommandAliasesScreen : BaseConfigScreen("aliases.title") {

    private var scroll = 0

    private val rowH = 25
    // toggles end at y=46, column headers occupy 48-57, first row starts at 58
    private val startY = 58

    override fun buildPage() {
        val left = centerX(344)
        val entries = SkyziyouConfig.aliases
        scroll = scroll.coerceIn(0, maxScroll())

        // two master toggles above the list
        addRenderableWidget(
            toggleButton(I18n.t("common.enabled"), SkyziyouConfig.aliasesEnabled, left, 26, 116) {
                SkyziyouConfig.aliasesEnabled = it
            },
        )
        addRenderableWidget(
            toggleButton(I18n.t("common.log"), SkyziyouConfig.aliasesLog, left + 120, 26, 116) {
                SkyziyouConfig.aliasesLog = it
            },
        )

        // one row per alias: [on/off][typed][-> runs][x]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val alias = entries[index]

            addRenderableWidget(
                checkButton(left, y, alias.enabled) { alias.enabled = it },
            )

            val typedBox = EditBox(font, left + 26, y, 150, 20, Component.empty())
            typedBox.setMaxLength(128)
            typedBox.value = alias.alias
            typedBox.setHint(Component.literal(I18n.t("aliases.typed_hint")))
            typedBox.setResponder { alias.alias = it }
            addRenderableWidget(typedBox)

            val targetBox = EditBox(font, left + 180, y, 140, 20, Component.empty())
            targetBox.setMaxLength(256)
            targetBox.value = alias.target
            targetBox.setHint(Component.literal(I18n.t("aliases.target_hint")))
            targetBox.setResponder { alias.target = it }
            addRenderableWidget(targetBox)

            addRenderableWidget(
                SkyButton(
                    left + 324, y, 20, 20, Component.literal("X"),
                    style = SkyButton.Style.DANGER,
                ) {
                    entries.removeAt(index)
                    rebuildWidgets()
                },
            )
        }

        addRenderableWidget(
            SkyButton(centerX(200), height - 30, 200, 20, Component.literal(I18n.t("aliases.add"))) {
                entries.add(CommandAliasEntry())
                scroll = maxScroll()
                rebuildWidgets()
            },
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val left = centerX(344)
        graphics.text(font, I18n.t("aliases.header_typed"), left + 26, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("aliases.header_target"), left + 180, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("aliases.footer"), left, height - 70, SkyTheme.TEXT_DIM, false)
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

    private fun visibleRows(): Int = ((height - startY - 80) / rowH).coerceAtLeast(1)

    private fun maxScroll(): Int = (SkyziyouConfig.aliases.size - visibleRows()).coerceAtLeast(0)
}
