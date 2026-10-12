package com.skyziyou.features.alert

import com.skyziyou.AlertEntry
import com.skyziyou.BaseConfigScreen
import com.skyziyou.I18n
import com.skyziyou.SkyButton
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.chat.ChatChannel
import net.minecraft.client.gui.GuiGraphicsExtractor
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
class ScreenAlertsScreen : BaseConfigScreen("cat.alerts") {

    private var scroll = 0

    private val rowH = 25
    // toggles end at y=46, column headers occupy 48-57, first row starts at 58
    private val startY = 58
    // bottom band reserved for the live font-size preview, hint line and buttons
    private val bottomReserve = 150

    override fun buildPage() {
        val left = centerX(344)
        val entries = SkyziyouConfig.screenAlerts
        scroll = scroll.coerceIn(0, maxScroll())

        addRenderableWidget(
            toggleButton(I18n.t("common.enabled"), SkyziyouConfig.screenAlertsEnabled, left, 26, 116) {
                SkyziyouConfig.screenAlertsEnabled = it
            },
        )

        // banner font size stepper: [ - ] Font size: 2.5x [ + ]
        addRenderableWidget(
            SkyButton(left + 126, 26, 20, 20, Component.literal("-")) {
                adjustScale(-ScreenAlerts.SCALE_STEP)
            },
        ).also { it.active = ScreenAlerts.fontScale() > ScreenAlerts.MIN_SCALE }
        addRenderableWidget(
            SkyButton(left + 226, 26, 20, 20, Component.literal("+")) {
                adjustScale(ScreenAlerts.SCALE_STEP)
            },
        ).also { it.active = ScreenAlerts.fontScale() < ScreenAlerts.MAX_SCALE }

        // one row per alert: [on/off][trigger][show text][channel][x]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val alert = entries[index]

            addRenderableWidget(
                checkButton(left, y, alert.enabled) { alert.enabled = it },
            )

            val triggerBox = EditBox(font, left + 26, y, 116, 20, Component.empty())
            triggerBox.setMaxLength(128)
            triggerBox.value = alert.trigger
            triggerBox.setHint(Component.literal(I18n.t("alerts.trigger_hint")))
            triggerBox.setResponder { alert.trigger = it }
            addRenderableWidget(triggerBox)

            val textBox = EditBox(font, left + 146, y, 116, 20, Component.empty())
            textBox.setMaxLength(128)
            textBox.value = alert.text
            textBox.setHint(Component.literal(I18n.t("alerts.text_hint")))
            textBox.setResponder { alert.text = it }
            addRenderableWidget(textBox)

            addRenderableWidget(
                SkyButton(left + 266, y, 40, 20, Component.literal(ChatChannel.fromNameOrAny(alert.channel).label)) {
                    alert.channel = nextChannel(alert.channel).name
                    it.message = Component.literal(ChatChannel.fromNameOrAny(alert.channel).label)
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
            SkyButton(centerX(200), height - 30, 200, 20, Component.literal(I18n.t("alerts.add"))) {
                entries.add(AlertEntry())
                scroll = maxScroll()
                rebuildWidgets()
            },
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val left = centerX(344)

        // font size readout between the - and + buttons
        val sizeLabel = I18n.t("alerts.font_size", fontScaleText())
        graphics.text(font, sizeLabel, left + 152, 32, SkyTheme.TEXT, false)

        graphics.text(font, I18n.t("alerts.header_trigger"), left + 26, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("alerts.header_banner"), left + 146, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("alerts.header_ch"), left + 266, startY - 10, SkyTheme.TEXT_DIM, false)

        // live banner preview drawn exactly as it appears in game at the chosen scale
        val caption = I18n.t("alerts.preview_caption")
        graphics.text(font, caption, pageCX - font.width(caption) / 2, height - 162, SkyTheme.TEXT, false)
        ScreenAlerts.drawBanner(
            graphics, font, I18n.t("alerts.preview"),
            pageCX, height - 122, ScreenAlerts.fontScale(),
        )

        graphics.text(
            font,
            I18n.t("alerts.legend"),
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

    private fun visibleRows(): Int = ((height - startY - bottomReserve) / rowH).coerceAtLeast(1)

    private fun adjustScale(delta: Float) {
        val target = ScreenAlerts.fontScale() + delta
        val stepped = (target / ScreenAlerts.SCALE_STEP).roundToInt() * ScreenAlerts.SCALE_STEP
        SkyziyouConfig.screenAlertFontScale =
            stepped.coerceIn(ScreenAlerts.MIN_SCALE, ScreenAlerts.MAX_SCALE)
        rebuildWidgets()
    }

    private fun maxScroll(): Int = (SkyziyouConfig.screenAlerts.size - visibleRows()).coerceAtLeast(0)

    /** Scale shown without a trailing ".0" (e.g. 2.5x stays 2.5, 3.0 shows as 3). */
    private fun fontScaleText(): String {
        val scale = ScreenAlerts.fontScale()
        return if (scale % 1f == 0f) scale.toInt().toString() else scale.toString()
    }

    private fun nextChannel(currentName: String): ChatChannel {
        val channels = ChatChannel.selectable
        val current = ChatChannel.fromNameOrAny(currentName)
        val index = channels.indexOf(current)
        return channels[(index + 1) % channels.size]
    }
}
