package dev.orchidsziyou.commandwheel

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * Custom key-to-command shortcuts. Each entry binds a key (GLFW keycode, 0-7 for
 * mouse buttons) to a command or chat message; pressing the key runs it immediately.
 * Polled once per client tick with press-edge detection, mirroring the wheel trigger.
 */
object Shortcuts {

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** pressed state of each bound key last tick, for edge detection */
    private val wasDown = HashMap<Int, Boolean>()

    /** Called once per client tick from the mod entrypoint. */
    fun onTick() {
        val entries = WheelConfig.shortcuts

        // update pressed states first so edges are not lost while a screen is open
        val down = HashMap<Int, Boolean>()
        for (entry in entries) {
            if (entry.enabled && entry.key != GLFW.GLFW_KEY_UNKNOWN) down[entry.key] = isDown(entry.key)
        }

        val active = WheelConfig.enabled &&
            !WheelState.isOpen &&
            mc.player != null &&
            mc.screen == null

        if (active) {
            for (entry in entries) {
                if (!entry.enabled) continue
                val pressed = down[entry.key] == true
                val justPressed = pressed && wasDown[entry.key] != true
                // skip the wheel trigger key so holding it cannot fire a shortcut too
                if (justPressed && entry.key != WheelConfig.keybind && entry.command.isNotBlank()) {
                    WheelState.executeEntry(entry.command)
                }
            }
        }
        wasDown.clear()
        wasDown.putAll(down)
    }

    private fun isDown(key: Int): Boolean =
        if (key in 0..7) WheelState.isMouseButtonDown(key)
        else InputConstants.isKeyDown(mc.window, key)
}
