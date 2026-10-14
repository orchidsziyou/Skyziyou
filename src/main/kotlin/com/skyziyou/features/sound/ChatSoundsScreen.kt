package com.skyziyou.features.sound

import com.skyziyou.BaseConfigScreen
import com.skyziyou.I18n
import com.skyziyou.SkyButton
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import com.skyziyou.SoundRuleEntry
import com.skyziyou.features.chat.ChatChannel
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * "Chat Sounds" editor sub-page. Row layout mirrors Custom Chat Rules but the
 * second column is a sound picker instead of an action box:
 *   [on/off][match text][vanilla sound][×n][channel][Aa][regex][x]
 * Left-clicking the sound button cycles the curated [SoundLibrary] list;
 * right-clicking it previews the sound immediately (including its ×n repeat).
 * The ×n button cycles how many times the sound plays on a match (1..5).
 */
class ChatSoundsScreen : BaseConfigScreen("cat.sounds") {

    private var scroll = 0

    private val rowH = 25
    // toggles end at y=46, column headers occupy 48-57, first row starts at 58
    private val startY = 58

    // row column geometry (total 348, matching the chat-rules page)
    private val colW = 348
    private val matchX = 26
    private val matchW = 86
    private val soundX = 116
    private val soundW = 96
    private val repeatX = 216
    private val repeatW = 24
    private val channelX = 244
    private val channelW = 30
    private val caseX = 278
    private val caseW = 20
    private val regexX = 302
    private val regexW = 22
    private val deleteX = 328

    override fun buildPage() {
        val left = centerX(colW)
        val entries = SkyziyouConfig.chatSounds
        scroll = scroll.coerceIn(0, maxScroll())

        addRenderableWidget(
            toggleButton(I18n.t("common.enabled"), SkyziyouConfig.chatSoundsEnabled, left, 26, 110) {
                SkyziyouConfig.chatSoundsEnabled = it
            },
        )
        addRenderableWidget(
            toggleButton(I18n.t("common.log"), SkyziyouConfig.chatSoundsLog, left + 114, 26, 110) {
                SkyziyouConfig.chatSoundsLog = it
            },
        )

        // one row per sound rule: [on/off][match][sound][×n][channel][Aa][regex][x]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val rule = entries[index]

            addRenderableWidget(
                checkButton(left, y, rule.enabled) { rule.enabled = it },
            )

            val matchBox = EditBox(font, left + matchX, y, matchW, 20, Component.empty())
            matchBox.setMaxLength(128)
            matchBox.value = rule.pattern
            matchBox.setHint(Component.literal(I18n.t("sounds.match_hint")))
            matchBox.setResponder { rule.pattern = it }
            addRenderableWidget(matchBox)

            addRenderableWidget(
                SkyButton(left + soundX, y, soundW, 20, soundLabel(rule)) {
                    rule.sound = SoundLibrary.next(rule.sound).path
                    it.message = soundLabel(rule)
                },
            )

            addRenderableWidget(
                SkyButton(left + repeatX, y, repeatW, 20, repeatLabel(rule)) {
                    rule.repeat = if (rule.repeat >= 5) 1 else rule.repeat + 1
                    it.message = repeatLabel(rule)
                },
            )

            addRenderableWidget(
                SkyButton(left + channelX, y, channelW, 20, Component.literal(ChatChannel.fromNameOrAny(rule.channel).label)) {
                    rule.channel = nextChannel(rule.channel).name
                    it.message = Component.literal(ChatChannel.fromNameOrAny(rule.channel).label)
                },
            )

            addRenderableWidget(
                SkyButton(left + caseX, y, caseW, 20, caseLabel(rule)) {
                    rule.ignoreCase = !rule.ignoreCase
                    it.message = caseLabel(rule)
                },
            )

            addRenderableWidget(
                SkyButton(left + regexX, y, regexW, 20, regexLabel(rule)) {
                    rule.regex = !rule.regex
                    it.message = regexLabel(rule)
                },
            )

            addRenderableWidget(
                SkyButton(
                    left + deleteX, y, 20, 20, Component.literal("X"),
                    style = SkyButton.Style.DANGER,
                ) {
                    entries.removeAt(index)
                    rebuildWidgets()
                },
            )
        }

        addRenderableWidget(
            SkyButton(centerX(200), height - 30, 200, 20, Component.literal(I18n.t("sounds.add"))) {
                entries.add(SoundRuleEntry())
                scroll = maxScroll()
                rebuildWidgets()
            },
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val left = centerX(colW)
        graphics.text(font, I18n.t("sounds.header_match"), left + matchX, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("sounds.header_sound"), left + soundX, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("sounds.header_repeat"), left + repeatX, startY - 10, -1, false)
        graphics.text(font, "§7ch", left + channelX, startY - 10, -1, false)
        graphics.text(font, I18n.t("sounds.legend"), left, height - 70, SkyTheme.TEXT_DIM, false)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        // right-click a sound button to preview it (with its repeat count)
        if (event.button() == 1) {
            val left = centerX(colW)
            val mx = event.x().toInt()
            val my = event.y().toInt()
            for (row in 0 until visibleRows()) {
                val index = scroll + row
                val rule = SkyziyouConfig.chatSounds.getOrNull(index) ?: break
                val y = startY + row * rowH
                if (mx in (left + soundX)..(left + soundX + soundW) && my in y..(y + 20)) {
                    ChatSounds.playRepeated(rule.sound, rule.repeat)
                    return true
                }
            }
        }
        return super.mouseClicked(event, doubleClick)
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

    private fun maxScroll(): Int = (SkyziyouConfig.chatSounds.size - visibleRows()).coerceAtLeast(0)

    private fun soundLabel(rule: SoundRuleEntry): Component =
        Component.literal("§b" + SoundLibrary.label(rule.sound))

    private fun repeatLabel(rule: SoundRuleEntry): Component =
        Component.literal("§e×${rule.repeat}")

    private fun regexLabel(rule: SoundRuleEntry): Component =
        Component.literal(if (rule.regex) "§b.*" else "§7Ab")

    private fun caseLabel(rule: SoundRuleEntry): Component =
        Component.literal(if (rule.ignoreCase) "§aAa" else "§cAa")

    private fun nextChannel(currentName: String): ChatChannel {
        val channels = ChatChannel.selectable
        val current = ChatChannel.fromNameOrAny(currentName)
        val index = channels.indexOf(current)
        return channels[(index + 1) % channels.size]
    }
}
