package com.skyziyou

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft

/**
 * Key codes and input polling shared by the wheel and the custom shortcuts.
 *
 * Minecraft 26.3 removed LWJGL from its compile classpath, so the numeric constants
 * (identical to the old GLFW values) live here instead of `org.lwjgl.glfw.GLFW`;
 * key codes already stored in the config keep working unchanged.
 */
object Keys {

    /** Stored value meaning "no key bound". */
    const val UNKNOWN = -1

    /** Keyboard key code used to cancel a key-binding capture. */
    const val ESCAPE = 256

    /** Action codes delivered by the input handler. */
    const val PRESS = 1
    const val RELEASE = 0

    /** Mouse buttons as stored in the config (0-7 are reserved for them). */
    const val MOUSE_LEFT = 0
    const val MOUSE_RIGHT = 1

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** True while [key] is held down; 0-7 are mouse buttons, anything else a key code. */
    fun isDown(key: Int): Boolean {
        if (key == UNKNOWN) return false
        if (key in 0..7) return WheelState.isMouseButtonDown(key)
        // 26.3 dropped the Window parameter from the polling helper
        //~ if >= 26.3 'InputConstants.isKeyDown(mc.window, key)' -> 'InputConstants.isKeyDown(key)'
        return InputConstants.isKeyDown(mc.window, key)
    }

    /** Human readable name of a stored key code, or null when it is unbound. */
    fun displayNameOrNull(key: Int): String? {
        if (key == UNKNOWN) return null
        //~ if >= 26.3 'InputConstants.Type.KEYSYM' -> 'InputConstants.Type.KEYBOARD'
        val type = if (key in 0..7) InputConstants.Type.MOUSE else InputConstants.Type.KEYSYM
        return type.getOrCreate(key).displayName.string
    }
}
