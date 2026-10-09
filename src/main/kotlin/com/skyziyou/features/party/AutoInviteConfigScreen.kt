package com.skyziyou.features.party

import com.skyziyou.BaseConfigScreen
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * Settings sub-page for [AutoInviteFeature]. Reached from the main ConfigScreen
 * category menu; saving and the Back navigation are handled by [BaseConfigScreen].
 */
class AutoInviteConfigScreen : BaseConfigScreen(Component.literal("Auto Party Invite")) {

    override fun init() {
        val colW = 240
        val x = width / 2 - colW / 2
        val rowH = 26
        val startY = 60

        addRenderableWidget(
            toggleButton(
                "Enabled",
                SkyziyouConfig.autoInviteEnabled,
                x, startY, colW,
            ) { SkyziyouConfig.autoInviteEnabled = it },
        )
        addRenderableWidget(
            toggleButton(
                "Invite Log",
                SkyziyouConfig.autoInviteLog,
                x, startY + rowH, colW,
            ) { SkyziyouConfig.autoInviteLog = it },
        )
        addRenderableWidget(
            toggleButton(
                "Hypixel Only",
                SkyziyouConfig.autoInviteHypixelOnly,
                x, startY + rowH * 2, colW,
            ) { SkyziyouConfig.autoInviteHypixelOnly = it },
        )

        addBackButton()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.text(font, title.string, width / 2 - font.width(title.string) / 2, 15, -1, false)

        val lines = listOf(
            "§7Invites a player when their private message is exactly §f!invite§7.",
            "§7Runs §f/p <player>§7; the message is matched case-insensitively.",
            "§7Same player cooldown: §f60s§7 · commands are spaced §f1s§7 apart (queued).",
        )
        var y = height - 70
        for (line in lines) {
            graphics.text(font, line, width / 2 - font.width(line) / 2, y, -1, false)
            y += 12
        }
    }
}
