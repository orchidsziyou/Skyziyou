package dev.orchidsziyou.commandwheel

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the wheel as a HUD element. The disc is drawn as a scanline stack of
 * vanilla [GuiGraphicsExtractor.fill] quads, labels are drawn with the vanilla font.
 */
object WheelRenderer {

    private val id: Identifier = Identifier.fromNamespaceAndPath("commandwheel", "wheel")
    private val backgroundColor: Int = Color(0, 0, 0, 140).rgb

    fun register() {
        HudElementRegistry.addLast(id) { graphics, _ ->
            if (WheelState.isOpen) render(graphics)
        }
    }

    private fun render(g: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        val centerX = mc.window.guiScaledWidth / 2
        val centerY = mc.window.guiScaledHeight / 2
        val scale = WheelConfig.sizePercent / 100.0
        val radius = (WheelState.BASE_WHEEL_RADIUS * scale).toInt()
        val labelRadius = (WheelState.BASE_LABEL_RADIUS * scale).toInt()

        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(centerX.toFloat(), centerY.toFloat())
        try {
            // solid disc via horizontal scanlines
            for (dy in -radius until radius) {
                val half = sqrt((radius * radius - dy * dy).toDouble())
                g.fill(-half.toInt(), dy, half.toInt(), dy + 1, backgroundColor)
            }

            val font = mc.font
            val fontScale = WheelConfig.fontScalePercent / 100.0f

            // draw text centered at (x, y) with the configured font scale
            fun drawText(x: Float, y: Float, text: String) {
                pose.pushMatrix()
                pose.translate(x, y)
                pose.scale(fontScale, fontScale)
                g.text(font, text, -font.width(text) / 2, -4, -1, true)
                pose.popMatrix()
            }

            val commands = WheelState.slotCommands
            commands.forEachIndexed { index, command ->
                val angleRad = Math.toRadians(-90.0 + index * WheelState.SECTOR_ANGLE)
                val x = (labelRadius * cos(angleRad)).toInt()
                val y = (labelRadius * sin(angleRad)).toInt()
                val selected = index == WheelState.selectedSector
                val prefix = if (selected) "§e§l" else "§7"
                val text = command?.let { "$prefix$it" } ?: "$prefix§8${WheelState.SECTOR_NAMES[index]}"
                drawText(x.toFloat(), y.toFloat(), text)
            }

            val selectedCommand = WheelState.selectedSector?.let { commands.getOrNull(it) }
            val hint = if (WheelConfig.executeOnRelease) "§7Release key to run" else "§7Left click to run"
            val centerText = selectedCommand?.let { "§e§l$it" } ?: hint
            drawText(0f, 0f, centerText)
        } finally {
            pose.popMatrix()
        }
    }
}
