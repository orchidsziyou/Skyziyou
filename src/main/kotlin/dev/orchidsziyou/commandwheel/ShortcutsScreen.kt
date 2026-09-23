package dev.orchidsziyou.commandwheel

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Editor for the custom key shortcuts. Each row binds a key to a command or chat
 * message; rows scroll when the list is longer than the screen fits.
 * Changes are applied instantly and saved when the screen is closed.
 */
class ShortcutsScreen : Screen(Component.literal("Command Wheel Shortcuts")) {

    private var scroll = 0

    /** While >= 0, the next key press or mouse click is captured as that row's keybind. */
    private var listeningIndex = -1

    private val rowH = 25
    private val startY = 35

    private fun visibleRows(): Int = ((height - startY - 60) / rowH).coerceAtLeast(1)

    private fun maxScroll(): Int = (WheelConfig.shortcuts.size - visibleRows()).coerceAtLeast(0)

    override fun init() {
        val left = width / 2 - 190
        val entries = WheelConfig.shortcuts
        scroll = scroll.coerceIn(0, maxScroll())

        // one row per shortcut: [keybind button][on/off toggle][command input][delete]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val entry = entries[index]

            addRenderableWidget(
                Button.builder(keybindLabel(index)) {
                    listeningIndex = index
                    it.message = Component.literal("Key: press any key...")
                }.bounds(left, y, 105, 20).build(),
            )

            addRenderableWidget(
                Button.builder(toggleLabel(entry)) {
                    entry.enabled = !entry.enabled
                    it.message = toggleLabel(entry)
                }.bounds(left + 110, y, 45, 20).build(),
            )

            val box = EditBox(font, left + 160, y, 195, 20, Component.empty())
            box.setMaxLength(64)
            box.value = entry.command
            box.setHint(Component.literal("/command or chat message"))
            box.setResponder { value -> entry.command = value }
            addRenderableWidget(box)

            addRenderableWidget(
                Button.builder(Component.literal("X")) {
                    // keep the listening index pointing at the same entry when rows shift
                    if (listeningIndex == index) listeningIndex = -1
                    else if (listeningIndex > index) listeningIndex--
                    entries.removeAt(index)
                    rebuildWidgets()
                }.bounds(left + 360, y, 20, 20).build(),
            )
        }

        addRenderableWidget(
            Button.builder(Component.literal("Add Shortcut")) {
                entries.add(ShortcutEntry())
                scroll = maxScroll()
                rebuildWidgets()
            }.bounds(width / 2 - 100, height - 55, 200, 20).build(),
        )
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds(width / 2 - 100, height - 30, 200, 20)
                .build(),
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val text = title.string
        graphics.text(font, text, width / 2 - font.width(text) / 2, 15, -1, false)
        if (hasConflicts()) {
            val warn = "§c⚠ duplicate keys will all trigger"
            graphics.text(font, warn, width / 2 - font.width(warn) / 2, 26, -1, false)
        }
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

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode = event.key()
        if (listeningIndex >= 0) {
            // ESC cancels the binding instead of setting it; any other key is captured
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                WheelConfig.shortcuts.getOrNull(listeningIndex)?.key = keyCode
            }
            listeningIndex = -1
            rebuildWidgets()
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val button = event.button()
        if (listeningIndex >= 0) {
            // mouse buttons 0-7 are valid keybinds too, so clicks are captured as well
            WheelConfig.shortcuts.getOrNull(listeningIndex)?.key = button
            listeningIndex = -1
            rebuildWidgets()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun onClose() {
        WheelConfig.save()
        // go back to the main settings screen instead of leaving the game menu
        minecraft?.setScreen(WheelConfigScreen())
    }

    /** True when an enabled shortcut shares its key with another one or with the wheel trigger. */
    private fun hasConflicts(): Boolean {
        val keys = WheelConfig.shortcuts.filter { it.enabled && it.key != GLFW.GLFW_KEY_UNKNOWN }.map { it.key }
        return keys.size != keys.distinct().size || keys.any { it == WheelConfig.keybind }
    }

    private fun toggleLabel(entry: ShortcutEntry): Component =
        Component.literal(if (entry.enabled) "§aON" else "§cOFF")

    private fun keybindLabel(index: Int): Component {
        val entry = WheelConfig.shortcuts[index]
        val key = entry.key
        val name = if (key == GLFW.GLFW_KEY_UNKNOWN) {
            "not set"
        } else {
            val type = if (key in 0..7) InputConstants.Type.MOUSE else InputConstants.Type.KEYSYM
            type.getOrCreate(key).displayName.string
        }
        var label = "Key: $name"
        // flag keys that will not trigger or that clash with another enabled shortcut
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            val duplicated = WheelConfig.shortcuts.any { it !== entry && it.enabled && it.key == key }
            if (key == WheelConfig.keybind || duplicated) label += " §c⚠"
        }
        return Component.literal(label)
    }
}
