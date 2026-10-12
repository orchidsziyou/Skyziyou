package com.skyziyou.features.party

import com.skyziyou.BaseConfigScreen
import com.skyziyou.I18n
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Settings page for [AutoInviteFeature]. Saving and sidebar navigation are handled
 * by [BaseConfigScreen].
 */
class AutoInviteConfigScreen : BaseConfigScreen("cat.invite") {

    override fun buildPage() {
        val colW = 260
        val x = centerX(colW)
        val rowH = 26
        val startY = 50

        addRenderableWidget(
            toggleButton(
                I18n.t("common.enabled"),
                SkyziyouConfig.autoInviteEnabled,
                x, startY, colW,
            ) { SkyziyouConfig.autoInviteEnabled = it },
        )
        addRenderableWidget(
            toggleButton(
                I18n.t("invite.invite_log"),
                SkyziyouConfig.autoInviteLog,
                x, startY + rowH, colW,
            ) { SkyziyouConfig.autoInviteLog = it },
        )
        addRenderableWidget(
            toggleButton(
                I18n.t("common.hypixel_only"),
                SkyziyouConfig.autoInviteHypixelOnly,
                x, startY + rowH * 2, colW,
            ) { SkyziyouConfig.autoInviteHypixelOnly = it },
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        val lines = listOf(
            I18n.t("invite.line1"),
            I18n.t("invite.line2"),
            I18n.t("invite.line3"),
        )
        var y = height - 70
        for (line in lines) {
            graphics.text(font, line, pageCX - font.width(line) / 2, y, SkyTheme.TEXT_DIM, false)
            y += 12
        }
    }
}
