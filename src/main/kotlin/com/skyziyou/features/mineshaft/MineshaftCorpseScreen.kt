package com.skyziyou.features.mineshaft

import com.skyziyou.BaseConfigScreen
import com.skyziyou.I18n
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Settings page for [MineshaftCorpseReporter]: a master switch, the Hypixel-only
 * restriction and the detailed/brief format toggle, plus the message examples.
 */
class MineshaftCorpseScreen : BaseConfigScreen("corpses.title") {

    override fun buildPage() {
        val colW = 260
        val x = centerX(colW)
        val rowH = 26
        val startY = 50

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
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

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
}
