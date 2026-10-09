package com.skyziyou.features.chat

import com.skyziyou.BaseConfigScreen
import com.skyziyou.ChatRuleEntry
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * "Custom Chat Rules" editor sub-page. Each row has three main boxes:
 *   match pattern | action (/command or chat text) | channel restriction,
 * plus an on/off switch, a contains/regex mode toggle and a delete button.
 * Action text supports [player] (and $1..$9 in regex mode).
 */
class ChatRulesScreen : BaseConfigScreen(Component.literal("Custom Chat Rules")) {

    private var scroll = 0

    private val rowH = 25
    // toggles end at y=46, column headers occupy 48-57, first row starts at 58
    private val startY = 58

    override fun init() {
        val left = width / 2 - 183
        val entries = SkyziyouConfig.chatRules
        scroll = scroll.coerceIn(0, maxScroll())

        // three master toggles above the list
        addRenderableWidget(
            toggleButton("Enabled", SkyziyouConfig.chatRulesEnabled, left, 26, 116) {
                SkyziyouConfig.chatRulesEnabled = it
            },
        )
        addRenderableWidget(
            toggleButton("Log", SkyziyouConfig.chatRulesLog, left + 120, 26, 116) {
                SkyziyouConfig.chatRulesLog = it
            },
        )
        addRenderableWidget(
            toggleButton("Hypixel Only", SkyziyouConfig.chatRulesHypixelOnly, left + 240, 26, 126) {
                SkyziyouConfig.chatRulesHypixelOnly = it
            },
        )

        // one row per rule: [on/off][match][action][channel][regex][x]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val rule = entries[index]

            addRenderableWidget(
                Button.builder(onOffLabel(rule)) {
                    rule.enabled = !rule.enabled
                    it.message = onOffLabel(rule)
                }.bounds(left, y, 22, 20).build(),
            )

            val matchBox = EditBox(font, left + 26, y, 118, 20, Component.empty())
            matchBox.setMaxLength(128)
            matchBox.value = rule.pattern
            matchBox.setHint(Component.literal("match text"))
            matchBox.setResponder { rule.pattern = it }
            addRenderableWidget(matchBox)

            val actionBox = EditBox(font, left + 148, y, 118, 20, Component.empty())
            actionBox.setMaxLength(128)
            actionBox.value = rule.action
            actionBox.setHint(Component.literal("/command or reply"))
            actionBox.setResponder { rule.action = it }
            addRenderableWidget(actionBox)

            addRenderableWidget(
                Button.builder(Component.literal(ChatChannel.fromNameOrAny(rule.channel).label)) {
                    rule.channel = nextChannel(rule.channel).name
                    it.message = Component.literal(ChatChannel.fromNameOrAny(rule.channel).label)
                }.bounds(left + 270, y, 40, 20).build(),
            )

            addRenderableWidget(
                Button.builder(regexLabel(rule)) {
                    rule.regex = !rule.regex
                    it.message = regexLabel(rule)
                }.bounds(left + 314, y, 28, 20).build(),
            )

            addRenderableWidget(
                Button.builder(Component.literal("X")) {
                    entries.removeAt(index)
                    rebuildWidgets()
                }.bounds(left + 346, y, 20, 20).build(),
            )
        }

        addRenderableWidget(
            Button.builder(Component.literal("Add Rule")) {
                entries.add(ChatRuleEntry())
                scroll = maxScroll()
                rebuildWidgets()
            }.bounds(width / 2 - 150, height - 55, 145, 20).build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal("Back")) { onClose() }
                .bounds(width / 2 + 5, height - 55, 145, 20)
                .build(),
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val left = width / 2 - 183
        graphics.text(font, title.string, width / 2 - font.width(title.string) / 2, 8, -1, false)
        graphics.text(font, "§7match ([me] = you)", left + 26, startY - 10, -1, false)
        graphics.text(font, "§7action  (use [player], \$1..\$9)", left + 148, startY - 10, -1, false)
        graphics.text(font, "§7ch", left + 270, startY - 10, -1, false)
        graphics.text(font, "§7Ch: ANY/PC/GC/AC/PM/SYS · mode button: Ab=contains  .*=regex", left, height - 70, -1, false)
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

    private fun maxScroll(): Int = (SkyziyouConfig.chatRules.size - visibleRows()).coerceAtLeast(0)

    private fun onOffLabel(rule: ChatRuleEntry): Component =
        Component.literal(if (rule.enabled) "§aON" else "§cOFF")

    private fun regexLabel(rule: ChatRuleEntry): Component =
        Component.literal(if (rule.regex) "§b.*" else "§7Ab")

    private fun nextChannel(currentName: String): ChatChannel {
        val channels = ChatChannel.selectable
        val current = ChatChannel.fromNameOrAny(currentName)
        val index = channels.indexOf(current)
        return channels[(index + 1) % channels.size]
    }
}
