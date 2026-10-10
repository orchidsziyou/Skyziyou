package com.skyziyou.features.command

import com.skyziyou.BaseConfigScreen
import com.skyziyou.CommandAliasEntry
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * "Command Aliases" editor sub-page. Each row has two boxes:
 *   typed command (with or without the leading '/') | replacement that actually runs,
 * plus an on/off switch and a delete button. A target starting with '/' runs as a
 * command; anything else is sent as a chat message.
 */
class CommandAliasesScreen : BaseConfigScreen(Component.literal("Custom Command Aliases")) {

    private var scroll = 0

    private val rowH = 25
    // toggles end at y=46, column headers occupy 48-57, first row starts at 58
    private val startY = 58

    override fun init() {
        val left = width / 2 - 183
        val entries = SkyziyouConfig.aliases
        scroll = scroll.coerceIn(0, maxScroll())

        // two master toggles above the list
        addRenderableWidget(
            toggleButton("Enabled", SkyziyouConfig.aliasesEnabled, left, 26, 116) {
                SkyziyouConfig.aliasesEnabled = it
            },
        )
        addRenderableWidget(
            toggleButton("Log", SkyziyouConfig.aliasesLog, left + 120, 26, 116) {
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
                Button.builder(onOffLabel(alias)) {
                    alias.enabled = !alias.enabled
                    it.message = onOffLabel(alias)
                }.bounds(left, y, 22, 20).build(),
            )

            val typedBox = EditBox(font, left + 26, y, 164, 20, Component.empty())
            typedBox.setMaxLength(128)
            typedBox.value = alias.alias
            typedBox.setHint(Component.literal("type this, e.g. /home"))
            typedBox.setResponder { alias.alias = it }
            addRenderableWidget(typedBox)

            val targetBox = EditBox(font, left + 194, y, 152, 20, Component.empty())
            targetBox.setMaxLength(256)
            targetBox.value = alias.target
            targetBox.setHint(Component.literal("runs this: /cmd or chat"))
            targetBox.setResponder { alias.target = it }
            addRenderableWidget(targetBox)

            addRenderableWidget(
                Button.builder(Component.literal("X")) {
                    entries.removeAt(index)
                    rebuildWidgets()
                }.bounds(left + 346, y, 20, 20).build(),
            )
        }

        addRenderableWidget(
            Button.builder(Component.literal("Add Alias")) {
                entries.add(CommandAliasEntry())
                scroll = maxScroll()
                rebuildWidgets()
            }.bounds(width / 2 - 150, height - 55, 145, 20).build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal("Back")) { onClose() }
                .bounds(width / 2 + 5, height - 55, 145, 20).build(),
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val left = width / 2 - 183
        graphics.text(font, title.string, width / 2 - font.width(title.string) / 2, 8, -1, false)
        graphics.text(font, "§7typed command (exact match)", left + 26, startY - 10, -1, false)
        graphics.text(font, "§7runs instead", left + 194, startY - 10, -1, false)
        graphics.text(font, "§7Exact match only: extra arguments are not forwarded · target: '/' = command, otherwise chat", left, height - 70, -1, false)
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

    private fun onOffLabel(alias: CommandAliasEntry): Component =
        Component.literal(if (alias.enabled) "§aON" else "§cOFF")
}
