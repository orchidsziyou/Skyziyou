package com.skyziyou.features.chat

import com.skyziyou.BaseConfigScreen
import com.skyziyou.ChatRuleEntry
import com.skyziyou.I18n
import com.skyziyou.SkyButton
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * "Custom Chat Rules" editor sub-page. Each row has three main boxes:
 *   match pattern | action (/command or chat text) | channel restriction,
 * plus an on/off switch, an ignore-case (Aa) toggle, a contains/regex mode
 * toggle and a delete button.
 * Action text supports [player] (and $1..$9 in regex mode).
 */
class ChatRulesScreen : BaseConfigScreen("cat.chatrules") {

    private var scroll = 0

    private val rowH = 25
    // toggles end at y=46, column headers occupy 48-57, first row starts at 58
    private val startY = 58

    override fun buildPage() {
        val left = centerX(348)
        val entries = SkyziyouConfig.chatRules
        scroll = scroll.coerceIn(0, maxScroll())

        // three master toggles above the list
        addRenderableWidget(
            toggleButton(I18n.t("common.enabled"), SkyziyouConfig.chatRulesEnabled, left, 26, 110) {
                SkyziyouConfig.chatRulesEnabled = it
            },
        )
        addRenderableWidget(
            toggleButton(I18n.t("common.log"), SkyziyouConfig.chatRulesLog, left + 114, 26, 110) {
                SkyziyouConfig.chatRulesLog = it
            },
        )
        addRenderableWidget(
            toggleButton(I18n.t("common.hypixel_only"), SkyziyouConfig.chatRulesHypixelOnly, left + 228, 26, 120) {
                SkyziyouConfig.chatRulesHypixelOnly = it
            },
        )

        // one row per rule: [on/off][match][action][channel][Aa][regex][x]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val rule = entries[index]

            addRenderableWidget(
                checkButton(left, y, rule.enabled) { rule.enabled = it },
            )

            val matchBox = EditBox(font, left + 26, y, 95, 20, Component.empty())
            matchBox.setMaxLength(128)
            matchBox.value = rule.pattern
            matchBox.setHint(Component.literal(I18n.t("chatrules.match_hint")))
            matchBox.setResponder { rule.pattern = it }
            addRenderableWidget(matchBox)

            val actionBox = EditBox(font, left + 125, y, 95, 20, Component.empty())
            actionBox.setMaxLength(128)
            actionBox.value = rule.action
            actionBox.setHint(Component.literal(I18n.t("chatrules.action_hint")))
            actionBox.setResponder { rule.action = it }
            addRenderableWidget(actionBox)

            addRenderableWidget(
                SkyButton(left + 224, y, 36, 20, Component.literal(ChatChannel.fromNameOrAny(rule.channel).label)) {
                    rule.channel = nextChannel(rule.channel).name
                    it.message = Component.literal(ChatChannel.fromNameOrAny(rule.channel).label)
                },
            )

            addRenderableWidget(
                SkyButton(left + 264, y, 24, 20, caseLabel(rule)) {
                    rule.ignoreCase = !rule.ignoreCase
                    it.message = caseLabel(rule)
                },
            )

            addRenderableWidget(
                SkyButton(left + 292, y, 30, 20, regexLabel(rule)) {
                    rule.regex = !rule.regex
                    it.message = regexLabel(rule)
                },
            )

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
            SkyButton(centerX(200), height - 30, 200, 20, Component.literal(I18n.t("chatrules.add"))) {
                entries.add(ChatRuleEntry())
                scroll = maxScroll()
                rebuildWidgets()
            },
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val left = centerX(348)
        graphics.text(font, I18n.t("chatrules.header_match"), left + 26, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("chatrules.header_action"), left + 125, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, "§7ch", left + 224, startY - 10, -1, false)
        graphics.text(font, "§7Aa", left + 266, startY - 10, -1, false)
        graphics.text(
            font,
            I18n.t("chatrules.legend"),
            left, height - 70, SkyTheme.TEXT_DIM, false,
        )
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

    private fun regexLabel(rule: ChatRuleEntry): Component =
        Component.literal(if (rule.regex) "§b.*" else "§7Ab")

    private fun caseLabel(rule: ChatRuleEntry): Component =
        Component.literal(if (rule.ignoreCase) "§aAa" else "§cAa")

    private fun nextChannel(currentName: String): ChatChannel {
        val channels = ChatChannel.selectable
        val current = ChatChannel.fromNameOrAny(currentName)
        val index = channels.indexOf(current)
        return channels[(index + 1) % channels.size]
    }
}
