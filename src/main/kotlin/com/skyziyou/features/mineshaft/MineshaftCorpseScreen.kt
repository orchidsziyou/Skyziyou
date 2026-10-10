package com.skyziyou.features.mineshaft

import com.skyziyou.BaseConfigScreen
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * Settings sub-page for [MineshaftCorpseReporter]: a master switch and the
 * Hypixel-only restriction, plus a short explanation of the message format.
 */
class MineshaftCorpseScreen : BaseConfigScreen(Component.literal("Mineshaft Corpse Report")) {

    override fun init() {
        val colW = 240
        val x = width / 2 - colW / 2
        val rowH = 26
        val startY = 60

        addRenderableWidget(
            toggleButton(
                "Enabled",
                SkyziyouConfig.corpseReportEnabled,
                x, startY, colW,
            ) { SkyziyouConfig.corpseReportEnabled = it },
        )
        addRenderableWidget(
            toggleButton(
                "Hypixel Only",
                SkyziyouConfig.corpseReportHypixelOnly,
                x, startY + rowH, colW,
            ) { SkyziyouConfig.corpseReportHypixelOnly = it },
        )
        addRenderableWidget(
            toggleButton(
                "Detailed Format",
                SkyziyouConfig.corpseReportDetailed,
                x, startY + rowH * 2, colW,
            ) { SkyziyouConfig.corpseReportDetailed = it },
        )

        addBackButton()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.text(font, title.string, width / 2 - font.width(title.string) / 2, 15, -1, false)

        val lines = listOf(
            "§7Reads the §fFrozen Corpses §7tab widget right after entering a mineshaft",
            "§7and sends the corpse counts to party chat once via §f/pc§7.",
            "",
            "§7Detailed: §f/pc Frozen Corpses (4): 2x Lapis, 1x Umber, 1x Vanguard",
            "§7Brief:    §f/pc Corpses (4): 2l 1u 1vg",
            "§7Tokens: §9l §7=Lapis  §8t §7=Tungsten  §6u §7=Umber  §fvg §7=Vanguard",
        )
        var y = height - 90
        for (line in lines) {
            graphics.text(font, line, width / 2 - font.width(line) / 2, y, -1, false)
            y += 12
        }
    }
}
