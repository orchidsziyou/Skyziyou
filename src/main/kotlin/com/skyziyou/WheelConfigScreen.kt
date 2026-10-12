package com.skyziyou

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * "Command Wheel" settings sub-page, reached from the [BaseConfigScreen] sidebar.
 * Changes are applied instantly and saved on close ([BaseConfigScreen]).
 */
class WheelConfigScreen : BaseConfigScreen("cat.wheel") {

    private val slotBoxes = mutableListOf<EditBox>()

    /** Rebuilt in [init]; kept here so the keybind listener can update its label. */
    private lateinit var keybindWidget: Button

    /** While true, the next key press or mouse click is captured as the new keybind. */
    private var listeningForKey = false

    override fun buildPage() {
        // compacted to fit the content area next to the sidebar: left controls column,
        // right side splits the remaining width between the two slot boxes per row
        val colW = 150
        val halfW = (pageW - colW - 20) / 2
        val left = pageX
        val right = left + colW + 10
        val rightMid = right + halfW + 10
        val rowH = 26
        val startY = 45

        // left column: keybind + toggles + sliders
        keybindWidget = SkyButton(left, startY, colW, 20, keybindLabel()) {
            listeningForKey = true
            it.message = Component.literal(I18n.t("wheel.keybind_listening"))
        }
        addRenderableWidget(keybindWidget)

        addRenderableWidget(
            toggleButton(I18n.t("common.enabled"), SkyziyouConfig.enabled, left, startY + rowH, colW) { SkyziyouConfig.enabled = it },
        )
        addRenderableWidget(
            toggleButton(
                I18n.t("wheel.execute_on_release"),
                SkyziyouConfig.executeOnRelease,
                left,
                startY + rowH * 2,
                colW,
            ) { SkyziyouConfig.executeOnRelease = it },
        )
        addRenderableWidget(
            toggleButton(
                I18n.t("wheel.execution_log"),
                SkyziyouConfig.showLogMessage,
                left,
                startY + rowH * 3,
                colW,
            ) { SkyziyouConfig.showLogMessage = it },
        )
        addRenderableWidget(
            slider(I18n.t("wheel.dead_zone"), SkyziyouConfig.deadZone, 10, 150, left, startY + rowH * 4, colW) {
                SkyziyouConfig.deadZone = it
            },
        )
        addRenderableWidget(
            slider(I18n.t("wheel.size_pct"), SkyziyouConfig.sizePercent, 50, 200, left, startY + rowH * 5, colW) {
                SkyziyouConfig.sizePercent = it
            },
        )
        addRenderableWidget(
            slider(I18n.t("wheel.font_pct"), SkyziyouConfig.fontScalePercent, 50, 200, left, startY + rowH * 6, colW) {
                SkyziyouConfig.fontScalePercent = it
            },
        )
        addRenderableWidget(
            slider(I18n.t("wheel.timeout_s"), SkyziyouConfig.timeoutSeconds, 3, 30, left, startY + rowH * 7, colW) {
                SkyziyouConfig.timeoutSeconds = it
            },
        )

        // right column: 8 direction slots, two per row
        slotBoxes.clear()
        for (i in 0 until WheelState.SECTOR_COUNT) {
            val row = i / 2
            val x = if (i % 2 == 0) right else rightMid
            val box = EditBox(font, x, startY + row * rowH, halfW, 20, Component.empty())
            box.setMaxLength(32)
            box.value = SkyziyouConfig.slots[i]
            box.setHint(Component.literal(WheelState.SECTOR_NAMES[i]))
            // live-apply the text so edits take effect without pressing Done
            box.setResponder { value -> SkyziyouConfig.slots[i] = value }
            slotBoxes.add(box)
            addRenderableWidget(box)
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode = event.key()
        if (listeningForKey) {
            // ESC cancels the binding instead of setting it; any other key is captured
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                SkyziyouConfig.keybind = keyCode
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
            SkyziyouConfig.keybind = button
            listeningForKey = false
            keybindWidget.message = keybindLabel()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    /** Button caption showing the current keybind, or the localised "not set" when unbound. */
    private fun keybindLabel(): Component {
        val key = SkyziyouConfig.keybind
        val name = if (key == GLFW.GLFW_KEY_UNKNOWN) {
            I18n.t("common.not_set")
        } else {
            val type = if (key in 0..7) InputConstants.Type.MOUSE else InputConstants.Type.KEYSYM
            type.getOrCreate(key).displayName.string
        }
        return Component.literal(I18n.t("wheel.keybind", name))
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

            // flat dark track + knob to match the SkyHanni-style theme; drag logic stays vanilla
            // AbstractSliderButton extends AbstractWidget directly, so the hook is named differently
            override fun extractWidgetRenderState(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                partialTick: Float,
            ) {
                graphics.fill(x, y, x + width, y + height, SkyTheme.BTN_BG)
                val knobCenter = x + 4 + (value * (width - 8)).toInt()
                graphics.fill(
                    knobCenter - 4, y + 1, knobCenter + 4, y + height - 1,
                    if (active && isHoveredOrFocused) SkyTheme.ROW_HOVER else SkyTheme.BTN_HOVER,
                )
                graphics.centeredText(
                    Minecraft.getInstance().font,
                    message,
                    x + width / 2,
                    y + (height - 8) / 2,
                    if (active) SkyTheme.TEXT else SkyTheme.TEXT_DIM,
                )
            }
        }
    }
}