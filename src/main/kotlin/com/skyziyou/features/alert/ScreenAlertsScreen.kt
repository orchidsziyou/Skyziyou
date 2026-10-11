package com.skyziyou.features.alert

import com.skyziyou.AlertEntry
import com.skyziyou.BaseConfigScreen
import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.chat.ChatChannel
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * "Screen Alerts" editor sub-page. Each row has two boxes:
 *   trigger text watched for in chat | banner shown on screen when it appears,
 * plus an on/off switch, a channel restriction (ANY/PC/GC/AC/PM/SYS) and a
 * delete button. A - /+ stepper adjusts the banner font scale with a live
 * preview drawn at the actual in-game size. The banner is shown centred near
 * the top of the screen for 4 seconds; the display text supports § colour
 * codes and the [player] / [me] placeholders.
 */
class ScreenAlertsScreen : BaseConfigScreen(Component.literal("Screen Alerts")) {

    private var scroll = 0

    private val rowH = 25
    // toggles end at y=46, column headers occupy 48-57, first row starts at 58
    private val startY = 58
    // bottom band reserved for the live font-size preview, hint line and buttons
    private val bottomReserve = 150

    override fun init() {
        val left = width / 2 - 183
        val entries = SkyziyouConfig.screenAlerts
        scroll = scroll.coerceIn(0, maxScroll())

        addRenderableWidget(
            toggleButton("Enabled", SkyziyouConfig.screenAlertsEnabled, left, 26, 116) {
                SkyziyouConfig.screenAlertsEnabled = it
            },
        )

        // banner font size stepper: [ - ] Font size: 2.5x [ + ]
        addRenderableWidget(
            Button.builder(Component.literal("-")) {
                adjustScale(-ScreenAlerts.SCALE_STEP)
            }.bounds(left + 126, 26, 20, 20).build(),
        ).also { it.active = ScreenAlerts.fontScale() > ScreenAlerts.MIN_SCALE }
        addRenderableWidget(
            Button.builder(Component.literal("+")) {
                adjustScale(ScreenAlerts.SCALE_STEP)
            }.bounds(left + 226, 26, 20, 20).build(),
        ).also { it.active = ScreenAlerts.fontScale() < ScreenAlerts.MAX_SCALE }

        // one row per alert: [on/off][trigger][show text][channel][x]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val alert = entries[index]

            addRenderableWidget(
                Button.builder(onOffLabel(alert)) {
                    alert.enabled = !alert.enabled
                    it.message = onOffLabel(alert)
                }.bounds(left, y, 22, 20).build(),
            )

            val triggerBox = EditBox(font, left + 26, y, 124, 20, Component.empty())
            triggerBox.setMaxLength(128)
            triggerBox.value = alert.trigger
            triggerBox.setHint(Component.literal("chat text to watch"))
            triggerBox.setResponder { alert.trigger = it }
            addRenderableWidget(triggerBox)

            val textBox = EditBox(font, left + 154, y, 124, 20, Component.empty())
            textBox.setMaxLength(128)
            textBox.value = alert.text
            textBox.setHint(Component.literal("banner text ([player])"))
            textBox.setResponder { alert.text = it }
            addRenderableWidget(textBox)

            addRenderableWidget(
                Button.builder(Component.literal(ChatChannel.fromNameOrAny(alert.channel).label)) {
                    alert.channel = nextChannel(alert.channel).name
                    it.message = Component.literal(ChatChannel.fromNameOrAny(alert.channel).label)
                }.bounds(left + 282, y, 40, 20).build(),
            )

            addRenderableWidget(
                Button.builder(Component.literal("X")) {
                    entries.removeAt(index)
                    rebuildWidgets()
                }.bounds(left + 346, y, 20, 20).build(),
            )
        }

        addRenderableWidget(
            Button.builder(Component.literal("Add Alert")) {
                entries.add(AlertEntry())
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

        // font size readout between the - and + buttons
        val sizeLabel = "§7Font size: §f${ScreenAlerts.fontScale()}x"
        graphics.text(font, sizeLabel, left + 152, 32, -1, false)

        graphics.text(font, "§7trigger (contains)", left + 26, startY - 10, -1, false)
        graphics.text(font, "§7banner shown for 4s", left + 154, startY - 10, -1, false)
        graphics.text(font, "§7ch", left + 294, startY - 10, -1, false)

        // live banner preview drawn exactly as it appears in game at the chosen scale
        val caption = "§7Preview (actual in-game size)"
        graphics.text(font, caption, width / 2 - font.width(caption) / 2, height - 162, -1, false)
        ScreenAlerts.drawBanner(
            graphics, font, "Alert Preview",
            width / 2, height - 122, ScreenAlerts.fontScale(),
        )

        graphics.text(
            font,
            "§7Ch: ANY/PC/GC/AC/PM/SYS · case-insensitive · use [player]/[me] and § codes",
            left, height - 70, -1, false,
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

    private fun visibleRows(): Int = ((height - startY - bottomReserve) / rowH).coerceAtLeast(1)

    private fun adjustScale(delta: Float) {
        val target = ScreenAlerts.fontScale() + delta
        val stepped = (target / ScreenAlerts.SCALE_STEP).roundToInt() * ScreenAlerts.SCALE_STEP
        SkyziyouConfig.screenAlertFontScale =
            stepped.coerceIn(ScreenAlerts.MIN_SCALE, ScreenAlerts.MAX_SCALE)
        rebuildWidgets()
    }

    private fun maxScroll(): Int = (SkyziyouConfig.screenAlerts.size - visibleRows()).coerceAtLeast(0)

    private fun onOffLabel(alert: AlertEntry): Component =
        Component.literal(if (alert.enabled) "§aON" else "§cOFF")

    private fun nextChannel(currentName: String): ChatChannel {
        val channels = ChatChannel.selectable
        val current = ChatChannel.fromNameOrAny(currentName)
        val index = channels.indexOf(current)
        return channels[(index + 1) % channels.size]
    }
}
