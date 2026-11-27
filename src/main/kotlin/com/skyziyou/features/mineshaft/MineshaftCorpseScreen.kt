package com.skyziyou.features.mineshaft

import com.skyziyou.BaseConfigScreen
import com.skyziyou.I18n
import com.skyziyou.SkyButton
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * Settings page for [MineshaftCorpseReporter] and [CorpseHighlights]: the report
 * switches (master, Hypixel-only, format), the corpse highlight toggle with its
 * distance stepper, and the party message examples.
 */
class MineshaftCorpseScreen : BaseConfigScreen("corpses.title") {

    private val colW = 260
    private val rowH = 26
    private val startY = 50

    override fun buildPage() {
        val x = centerX(colW)

        addRenderableWidget(
            toggleButton(
                I18n.t("common.enabled"),
                SkyziyouConfig.corpseReportEnabled,
                x, startY, colW,
            ) { SkyziyouConfig.corpseReportEnabled = it },
        )
        addRenderableWidget(
            toggleButton(
                I18n.t("common.hypixel_only"),
                SkyziyouConfig.corpseReportHypixelOnly,
                x, startY + rowH, colW,
            ) { SkyziyouConfig.corpseReportHypixelOnly = it },
        )
        addRenderableWidget(
            toggleButton(
                I18n.t("corpses.detailed"),
                SkyziyouConfig.corpseReportDetailed,
                x, startY + rowH * 2, colW,
            ) { SkyziyouConfig.corpseReportDetailed = it },
        )

        // corpse highlight: master switch, then a distance stepper (- range +)
        addRenderableWidget(
            toggleButton(
                I18n.t("corpses.highlight"),
                SkyziyouConfig.corpseHighlightEnabled,
                x, startY + rowH * 3, colW,
            ) { SkyziyouConfig.corpseHighlightEnabled = it },
        )

        val rangeY = startY + rowH * 4
        addRenderableWidget(
            SkyButton(x, rangeY, 20, 20, Component.literal("-")) {
                adjustRange(-CorpseHighlights.RANGE_STEP)
            },
        ).also { it.active = SkyziyouConfig.corpseHighlightRange > CorpseHighlights.MIN_RANGE }
        addRenderableWidget(
            SkyButton(x + colW - 20, rangeY, 20, 20, Component.literal("+")) {
                adjustRange(CorpseHighlights.RANGE_STEP)
            },
        ).also { it.active = SkyziyouConfig.corpseHighlightRange < CorpseHighlights.MAX_RANGE }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        // distance readout between the - and + buttons, plus a one-line explanation
        val rangeLabel = I18n.t("corpses.highlight_range", SkyziyouConfig.corpseHighlightRange)
        val rangeY = startY + rowH * 4
        graphics.text(
            font, rangeLabel,
            pageCX - font.width(rangeLabel) / 2, rangeY + 6, SkyTheme.TEXT, false,
        )
        val hint = I18n.t("corpses.highlight_hint")
        graphics.text(
            font, hint,
            pageCX - font.width(hint) / 2, rangeY + 28, SkyTheme.TEXT_DIM, false,
        )

        // the actual /pc message examples stay English on purpose: that is exactly what gets sent
        val lines = listOf(
            I18n.t("corpses.line1"),
            I18n.t("corpses.line2"),
            "",
            I18n.t("corpses.line4"),
            I18n.t("corpses.line5"),
            I18n.t("corpses.line6"),
        )
        var y = height - 90
        for (line in lines) {
            graphics.text(font, line, pageCX - font.width(line) / 2, y, SkyTheme.TEXT_DIM, false)
            y += 12
        }
    }

    /** Steps the highlight distance and rebuilds so the button enable states refresh. */
    private fun adjustRange(delta: Int) {
        SkyziyouConfig.corpseHighlightRange = (SkyziyouConfig.corpseHighlightRange + delta)
            .coerceIn(CorpseHighlights.MIN_RANGE, CorpseHighlights.MAX_RANGE)
        rebuildWidgets()
    }
}
