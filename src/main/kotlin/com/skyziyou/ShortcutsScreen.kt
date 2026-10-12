package com.skyziyou

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * "Custom Shortcuts" editor sub-page, reached from the [BaseConfigScreen] sidebar.
 * Each row binds a key to a command or chat message; rows scroll when the list is
 * longer than the screen fits. Changes are applied instantly and saved on close.
 */
class ShortcutsScreen : BaseConfigScreen("cat.shortcuts") {

    private var scroll = 0

    /** While >= 0, the next key press or mouse click is captured as that row's keybind. */
    private var listeningIndex = -1

    private val rowH = 25
    private val startY = 35

    private fun visibleRows(): Int = ((height - startY - 60) / rowH).coerceAtLeast(1)

    private fun maxScroll(): Int = (SkyziyouConfig.shortcuts.size - visibleRows()).coerceAtLeast(0)

    override fun buildPage() {
        val left = centerX(344)
        val entries = SkyziyouConfig.shortcuts
        scroll = scroll.coerceIn(0, maxScroll())

        // one row per shortcut: [keybind button][check toggle][command input][delete]
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + row * rowH
            val entry = entries[index]

            addRenderableWidget(
                SkyButton(left, y, 90, 20, keybindLabel(index)) {
                    listeningIndex = index
                    it.message = Component.literal(I18n.t("shortcuts.listening"))
                },
            )

            addRenderableWidget(
                checkButton(left + 94, y, entry.enabled) { entry.enabled = it },
            )

            val box = EditBox(font, left + 120, y, 200, 20, Component.empty())
            box.setMaxLength(64)
            box.value = entry.command
            box.setHint(Component.literal(I18n.t("shortcuts.hint")))
            box.setResponder { value -> entry.command = value }
            addRenderableWidget(box)

            addRenderableWidget(
                SkyButton(
                    left + 324, y, 20, 20, Component.literal("X"),
                    style = SkyButton.Style.DANGER,
                ) {
                    // keep the listening index pointing at the same entry when rows shift
                    if (listeningIndex == index) listeningIndex = -1
                    else if (listeningIndex > index) listeningIndex--
                    entries.removeAt(index)
                    rebuildWidgets()
                },
            )
        }

        addRenderableWidget(
            SkyButton(centerX(200), height - 30, 200, 20, Component.literal(I18n.t("shortcuts.add"))) {
                entries.add(ShortcutEntry())
                scroll = maxScroll()
                rebuildWidgets()
            },
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        if (hasConflicts()) {
            val warn = I18n.t("shortcuts.conflict")
            graphics.text(font, warn, pageCX - font.width(warn) / 2, 25, SkyTheme.RED, false)
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
                SkyziyouConfig.shortcuts.getOrNull(listeningIndex)?.key = keyCode
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
            SkyziyouConfig.shortcuts.getOrNull(listeningIndex)?.key = button
            listeningIndex = -1
            rebuildWidgets()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    /** True when an enabled shortcut shares its key with another one or with the wheel trigger. */
    private fun hasConflicts(): Boolean {
        val keys = SkyziyouConfig.shortcuts.filter { it.enabled && it.key != GLFW.GLFW_KEY_UNKNOWN }.map { it.key }
        return keys.size != keys.distinct().size || keys.any { it == SkyziyouConfig.keybind }
    }

    private fun keybindLabel(index: Int): Component {
        val entry = SkyziyouConfig.shortcuts[index]
        val key = entry.key
        val name = if (key == GLFW.GLFW_KEY_UNKNOWN) {
            I18n.t("common.not_set")
        } else {
            val type = if (key in 0..7) InputConstants.Type.MOUSE else InputConstants.Type.KEYSYM
            type.getOrCreate(key).displayName.string
        }
        var label = I18n.t("shortcuts.key", name)
        // flag keys that will not trigger or that clash with another enabled shortcut
        if (key != GLFW.GLFW_KEY_UNKNOWN) {
            val duplicated = SkyziyouConfig.shortcuts.any { it !== entry && it.enabled && it.key == key }
            if (key == SkyziyouConfig.keybind || duplicated) label += " §c⚠"
        }
        return Component.literal(label)
    }
}
