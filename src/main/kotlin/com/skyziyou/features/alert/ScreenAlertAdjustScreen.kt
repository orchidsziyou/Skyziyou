package com.skyziyou.features.alert

import com.skyziyou.BaseConfigScreen
import com.skyziyou.I18n
import com.skyziyou.SkyButton
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * Detailed "Screen Alerts" appearance editor, opened from the Adjust button of the
 * alerts page. Four sliders tweak the banner without leaving the screen:
 * X position, Y position (percent of the GUI size), font size and text colour.
 *
 * Every slider writes straight into [SkyziyouConfig] while it is dragged (the
 * vanilla slider calls applyValue on each value change), so the preview below —
 * a schematic of the whole screen, scaled proportionally — updates live for all
 * four settings at once.
 */
class ScreenAlertAdjustScreen : BaseConfigScreen("cat.alerts") {

    private val sliderW = 240
    private val firstY = 30
    private val rowGap = 24
    private val previewTop = 140

    override fun buildPage() {
        val x = centerX(sliderW)

        // X position, percent of the GUI width
        addRenderableWidget(
            slider(x, firstY, { I18n.t("alerts.adjust_x", SkyziyouConfig.screenAlertXPercent) },
                SkyziyouConfig.screenAlertXPercent / 100f,
            ) { SkyziyouConfig.screenAlertXPercent = (it * 100).roundToInt() },
        )

        // Y position, percent of the GUI height
        addRenderableWidget(
            slider(x, firstY + rowGap, { I18n.t("alerts.adjust_y", SkyziyouConfig.screenAlertYPercent) },
                SkyziyouConfig.screenAlertYPercent / 100f,
            ) { SkyziyouConfig.screenAlertYPercent = (it * 100).roundToInt() },
        )

        // font size, snapped to the same 0.25 steps as the -/+ stepper on the alerts page
        addRenderableWidget(
            slider(x, firstY + rowGap * 2, { I18n.t("alerts.adjust_size", scaleText()) },
                ((ScreenAlerts.fontScale() - ScreenAlerts.MIN_SCALE) / ScreenAlerts.SCALE_STEP) /
                    ScreenAlerts.SCALE_STEPS,
            ) {
                val step = (it * ScreenAlerts.SCALE_STEPS).roundToInt()
                SkyziyouConfig.screenAlertFontScale =
                    ScreenAlerts.MIN_SCALE + step * ScreenAlerts.SCALE_STEP
            },
        )

        // text colour, picked from the shared palette; the label shows the hex value
        addRenderableWidget(
            slider(
                x, firstY + rowGap * 3,
                { I18n.t("alerts.adjust_color", hexColor(ScreenAlerts.textColor())) },
                ScreenAlerts.colorIndex(ScreenAlerts.textColor())
                    .toFloat() / (ScreenAlerts.COLOR_CHOICES.size - 1),
                swatch = { ScreenAlerts.textColor() },
            ) {
                val index = (it * (ScreenAlerts.COLOR_CHOICES.size - 1)).roundToInt()
                SkyziyouConfig.screenAlertTextColor = ScreenAlerts.COLOR_CHOICES[index]
            },
        )

        // bottom row: restore the factory defaults | go back to the alert list
        addRenderableWidget(
            SkyButton(pageCX - 150, height - 30, 120, 20, Component.literal(I18n.t("alerts.adjust_reset"))) {
                SkyziyouConfig.screenAlertXPercent = SkyziyouConfig.DEFAULT_ALERT_X
                SkyziyouConfig.screenAlertYPercent = SkyziyouConfig.DEFAULT_ALERT_Y
                SkyziyouConfig.screenAlertFontScale = ScreenAlerts.DEFAULT_SCALE
                SkyziyouConfig.screenAlertTextColor = ScreenAlerts.DEFAULT_TEXT_COLOR
                // rebuild so every slider handle and label pick up the restored values
                rebuildWidgets()
            },
        )
        addRenderableWidget(
            SkyButton(pageCX - 20, height - 30, 170, 20, Component.literal(I18n.t("alerts.adjust_back"))) {
                //~ if < 26.2 'minecraft.gui.setScreen' -> 'minecraft.setScreen'
                minecraft.gui.setScreen(ScreenAlertsScreen())
            },
        )
    }

    override fun drawBehind(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val hint = I18n.t("alerts.adjust_hint")
        graphics.text(font, hint, pageCX - font.width(hint) / 2, height - 50, SkyTheme.TEXT_DIM, false)

        val box = previewBox() ?: return
        val caption = I18n.t("alerts.adjust_preview")
        graphics.text(font, caption, pageCX - font.width(caption) / 2, box.y - 12, SkyTheme.TEXT, false)

        // schematic of the whole screen: translucent fill plus a thin border
        graphics.fill(box.x, box.y, box.x + box.w, box.y + box.h, 0x50000000.toInt())
        val border = SkyTheme.BTN_HOVER
        graphics.fill(box.x, box.y, box.x + box.w, box.y + 1, border)
        graphics.fill(box.x, box.y + box.h - 1, box.x + box.w, box.y + box.h, border)
        graphics.fill(box.x, box.y, box.x + 1, box.y + box.h, border)
        graphics.fill(box.x + box.w - 1, box.y, box.x + box.w, box.y + box.h, border)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val box = previewBox() ?: return

        // the banner is drawn inside the schematic at the configured percentage, with
        // the font scale shrunk by the same factor so proportions match the real HUD
        val bannerX = box.x + (box.w * SkyziyouConfig.screenAlertXPercent / 100f).toInt()
        val bannerY = box.y + (box.h * SkyziyouConfig.screenAlertYPercent / 100f).toInt()
        val previewScale = ScreenAlerts.fontScale() * (box.h.toFloat() / height)

        // crosshair so an empty/blank banner can still be placed precisely
        val arm = 4
        val mark = SkyTheme.TEXT_DIM
        graphics.fill(bannerX - arm, bannerY, bannerX + arm + 1, bannerY + 1, mark)
        graphics.fill(bannerX, bannerY - arm, bannerX + 1, bannerY + arm + 1, mark)

        ScreenAlerts.drawBanner(
            graphics, font, I18n.t("alerts.preview"), bannerX, bannerY, previewScale, ScreenAlerts.textColor(),
        )
    }

    /** Screen-shaped preview area between the sliders and the footer, or null when it does not fit. */
    private fun previewBox(): Box? {
        val maxW = pageW - 40
        val maxH = (height - 56) - previewTop
        if (maxW <= 0 || maxH <= 0) return null
        val ratio = width.toFloat() / height
        val h = minOf(maxH, (maxW / ratio).toInt())
        val w = (h * ratio).toInt()
        if (w <= 0 || h <= 0) return null
        return Box(pageCX - w / 2, previewTop, w, h)
    }

    private data class Box(val x: Int, val y: Int, val w: Int, val h: Int)

    /**
     * Dark-themed slider: the vanilla drag/click logic is kept, only the track, knob
     * and label are drawn by hand. [label] and [onChange] read/write the live config,
     * so a drag repaints the preview every frame. [swatch] paints a colour chip.
     */
    private fun slider(
        x: Int,
        y: Int,
        label: () -> String,
        initial: Float,
        swatch: (() -> Int)? = null,
        onChange: (Float) -> Unit,
    ): AbstractSliderButton = object : AbstractSliderButton(
        x, y, sliderW, 20, Component.empty(), initial.coerceIn(0f, 1f).toDouble(),
    ) {
        init {
            updateMessage()
        }

        override fun updateMessage() {
            setMessage(Component.literal(label()))
        }

        override fun applyValue() {
            onChange(value.toFloat())
        }

        override fun extractWidgetRenderState(
            graphics: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            partialTick: Float,
        ) {
            graphics.fill(x, y, x + width, y + height, SkyTheme.BTN_BG)
            swatch?.let { graphics.fill(x + 5, y + 5, x + 13, y + height - 5, it()) }
            val knobCenter = x + 4 + (value * (width - 8)).toInt()
            graphics.fill(
                knobCenter - 4, y + 1, knobCenter + 4, y + height - 1,
                if (active && isHoveredOrFocused) SkyTheme.ROW_HOVER else SkyTheme.BTN_HOVER,
            )
            graphics.centeredText(
                Minecraft.getInstance().font,
                message,
                x + width / 2,
                y + (height - 8) / 2,
                if (active) SkyTheme.TEXT else SkyTheme.TEXT_DIM,
            )
        }
    }

    /** Scale shown without a trailing ".0" (e.g. 2.5x stays 2.5, 3.0 shows as 3). */
    private fun scaleText(): String {
        val scale = ScreenAlerts.fontScale()
        return if (scale % 1f == 0f) scale.toInt().toString() else scale.toString()
    }

    private fun hexColor(color: Int): String = "#%06X".format(color and 0xFFFFFF)
}
