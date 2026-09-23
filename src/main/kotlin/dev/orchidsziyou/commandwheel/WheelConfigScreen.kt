package dev.orchidsziyou.commandwheel

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Minimal in-game settings screen, opened via the /commandwheel or /cwh client command.
 * Changes are applied instantly and saved on close.
 */
class WheelConfigScreen : Screen(Component.literal("Command Wheel Settings")) {

    private val slotBoxes = mutableListOf<EditBox>()

    /** Rebuilt in [init]; kept here so the keybind listener can update its label. */
    private lateinit var keybindWidget: Button

    /** While true, the next key press or mouse click is captured as the new keybind. */
    private var listeningForKey = false

    override fun init() {
        val colW = 200
        val left = width / 2 - colW - 10
        val right = width / 2 + 10
        val halfW = 95
        val rightMid = width / 2 + 115
        val rowH = 26
        val startY = 45

        // left column: keybind + toggles + sliders
        keybindWidget = Button.builder(keybindLabel()) {
            listeningForKey = true
            it.message = Component.literal("Wheel Keybind: press any key...")
        }.bounds(left, startY, colW, 20).build()
        addRenderableWidget(keybindWidget)

        addRenderableWidget(
            toggleButton("Enabled", WheelConfig.enabled, left, startY + rowH, colW) { WheelConfig.enabled = it },
        )
        addRenderableWidget(
            toggleButton(
                "Execute On Release",
                WheelConfig.executeOnRelease,
                left,
                startY + rowH * 2,
                colW,
            ) { WheelConfig.executeOnRelease = it },
        )
        addRenderableWidget(
            toggleButton(
                "Execution Log",
                WheelConfig.showLogMessage,
                left,
                startY + rowH * 3,
                colW,
            ) { WheelConfig.showLogMessage = it },
        )
        addRenderableWidget(
            slider("Dead Zone", WheelConfig.deadZone, 10, 150, left, startY + rowH * 4, colW) {
                WheelConfig.deadZone = it
            },
        )
        addRenderableWidget(
            slider("Wheel Size %", WheelConfig.sizePercent, 50, 200, left, startY + rowH * 5, colW) {
                WheelConfig.sizePercent = it
            },
        )
        addRenderableWidget(
            slider("Font Size %", WheelConfig.fontScalePercent, 50, 200, left, startY + rowH * 6, colW) {
                WheelConfig.fontScalePercent = it
            },
        )
        addRenderableWidget(
            slider("Timeout s", WheelConfig.timeoutSeconds, 3, 30, left, startY + rowH * 7, colW) {
                WheelConfig.timeoutSeconds = it
            },
        )

        // right column: 8 direction slots, two per row
        slotBoxes.clear()
        for (i in 0 until WheelState.SECTOR_COUNT) {
            val row = i / 2
            val x = if (i % 2 == 0) right else rightMid
            val box = EditBox(font, x, startY + row * rowH, halfW, 20, Component.empty())
            box.setMaxLength(32)
            box.value = WheelConfig.slots[i]
            box.setHint(Component.literal(WheelState.SECTOR_NAMES[i]))
            // live-apply the text so edits take effect without pressing Done
            box.setResponder { value -> WheelConfig.slots[i] = value }
            slotBoxes.add(box)
            addRenderableWidget(box)
        }

        // opens the custom key shortcut editor (the right column is free below the slot boxes)
        addRenderableWidget(
            Button.builder(Component.literal("Custom Shortcuts...")) {
                minecraft?.setScreen(ShortcutsScreen())
            }.bounds(width / 2 + 10, startY + rowH * 4, 200, 20).build(),
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
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode = event.key()
        if (listeningForKey) {
            // ESC cancels the binding instead of setting it; any other key is captured
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                WheelConfig.keybind = keyCode
            }
            listeningForKey = false
            keybindWidget.message = keybindLabel()
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val button = event.button()
        if (listeningForKey) {
            // mouse buttons 0-7 are valid keybinds too, so clicks are captured as well
            WheelConfig.keybind = button
            listeningForKey = false
            keybindWidget.message = keybindLabel()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    /** Widgets write straight into [WheelConfig]; persist everything when leaving the screen. */
    override fun onClose() {
        WheelConfig.save()
        super.onClose()
    }

    /** Button caption showing the current keybind, or "not set" when unbound. */
    private fun keybindLabel(): Component {
        val key = WheelConfig.keybind
        val name = if (key == GLFW.GLFW_KEY_UNKNOWN) {
            "not set"
        } else {
            val type = if (key in 0..7) InputConstants.Type.MOUSE else InputConstants.Type.KEYSYM
            type.getOrCreate(key).displayName.string
        }
        return Component.literal("Wheel Keybind: $name")
    }

    /** Creates an ON/OFF button that flips its captured value and writes through [onChange]. */
    private fun toggleButton(label: String, initial: Boolean, x: Int, y: Int, w: Int, onChange: (Boolean) -> Unit): Button {
        var value = initial
        fun text() = Component.literal("$label: ${if (value) "ON" else "OFF"}")
        return Button.builder(text()) { button ->
            value = !value
            onChange(value)
            button.message = text()
        }.bounds(x, y, w, 20).build()
    }

    /** Creates a slider mapping the 0-1 handle position onto [min, max], writing the rounded value through [onChange]. */
    private fun slider(label: String, current: Int, min: Int, max: Int, x: Int, y: Int, w: Int, onChange: (Int) -> Unit): AbstractSliderButton {
        val initial = (current.coerceIn(min, max) - min).toDouble() / (max - min)
        return object : AbstractSliderButton(x, y, w, 20, Component.empty(), initial) {
            init {
                updateMessage()
            }

            override fun updateMessage() {
                setMessage(Component.literal("$label: ${(min + (value * (max - min))).toInt()}"))
            }

            override fun applyValue() {
                onChange((min + (value * (max - min))).toInt())
            }
        }
    }
}