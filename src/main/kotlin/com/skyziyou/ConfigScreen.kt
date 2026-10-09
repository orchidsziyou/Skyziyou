package com.skyziyou

import com.skyziyou.features.chat.ChatRulesScreen
import com.skyziyou.features.party.AutoInviteConfigScreen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Main config menu, SkyHanni-style: a category list where every feature area owns
 * its own sub-page. Opened via the /skyziyou and /sz client commands.
 *
 * Sub-pages extend [BaseConfigScreen], which centralises saving-on-close and the
 * navigation back to this menu.
 */
class ConfigScreen : Screen(Component.literal("Skyziyou Settings")) {

    private class Category(
        val label: String,
        val description: String,
        val factory: () -> Screen,
    )

    private val categories = listOf(
        Category(
            "Command Wheel",
            "Radial wheel slots, trigger keybind and wheel behaviour.",
        ) { WheelConfigScreen() },
        Category(
            "Custom Shortcuts",
            "Bind keys directly to commands or chat messages.",
        ) { ShortcutsScreen() },
        Category(
            "Custom Chat Rules",
            "Match chat text per channel and auto-send replies or commands.",
        ) { ChatRulesScreen() },
        Category(
            "Auto Party Invite",
            "Automatically invite players that PM you !invite.",
        ) { AutoInviteConfigScreen() },
    )

    override fun init() {
        val colW = 240
        val rowH = 34
        val startY = 70
        val x = width / 2 - colW / 2

        categories.forEachIndexed { index, category ->
            addRenderableWidget(
                Button.builder(Component.literal(category.label)) {
                    //~ if < 26.2 'minecraft.gui.setScreen' -> 'minecraft.setScreen'
                    minecraft.gui.setScreen(category.factory())
                }.bounds(x, startY + index * rowH, colW, 20).build(),
            )
        }

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

        val startY = 70
        val rowH = 34
        categories.forEachIndexed { index, category ->
            val desc = "§7${category.description}"
            graphics.text(font, desc, width / 2 - font.width(desc) / 2, startY + index * rowH + 23, -1, false)
        }
    }
}

/**
 * Shared base for every config sub-page: persists the config and returns to the
 * [ConfigScreen] category menu whenever the page closes (Back button or Esc).
 */
abstract class BaseConfigScreen(title: Component) : Screen(title) {

    override fun onClose() {
        SkyziyouConfig.save()
        //~ if < 26.2 'minecraft.gui.setScreen' -> 'minecraft.setScreen'
        minecraft.gui.setScreen(ConfigScreen())
    }

    /** Adds the standard bottom-centre Back button that closes into the category menu. */
    protected fun addBackButton() {
        addRenderableWidget(
            Button.builder(Component.literal("Back")) { onClose() }
                .bounds(width / 2 - 100, height - 30, 200, 20)
                .build(),
        )
    }

    /** Creates an ON/OFF button that flips its captured value and writes through [onChange]. */
    protected fun toggleButton(
        label: String,
        initial: Boolean,
        x: Int,
        y: Int,
        w: Int,
        onChange: (Boolean) -> Unit,
    ): Button {
        var value = initial
        fun text() = Component.literal("$label: ${if (value) "ON" else "OFF"}")
        return Button.builder(text()) { button ->
            value = !value
            onChange(value)
            button.message = text()
        }.bounds(x, y, w, 20).build()
    }
}
