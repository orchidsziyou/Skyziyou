package com.skyziyou

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * "Profiles" sidebar tab: switch between wheel/shortcut profiles, create copies,
 * rename and delete. Only the wheel slots and the custom shortcuts are
 * profile-scoped; every other setting in the mod is global.
 */
class ProfilesScreen : BaseConfigScreen("cat.profiles") {

    private var scroll = 0

    private val rowH = 25
    private val rowW = 348
    private val startY = 58

    override fun buildPage() {
        val left = centerX(rowW)
        scroll = scroll.coerceIn(0, maxScroll())

        // one row per profile: name (click = use) | copy | delete
        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= SkyziyouConfig.profiles.size) break
            val name = SkyziyouConfig.profiles.keys.elementAt(index)
            val current = name == SkyziyouConfig.activeProfile
            val y = startY + row * rowH

            addRenderableWidget(
                SkyButton(left, y, 180, 20, Component.literal((if (current) "§a● §f" else "§7   ") + name)) {
                    if (SkyziyouConfig.switchProfile(name)) rebuildWidgets()
                },
            )
            addRenderableWidget(
                SkyButton(left + 184, y, 52, 20, Component.literal(I18n.t("profiles.copy"))) {
                    if (SkyziyouConfig.createProfile(uniqueName("$name copy")) != null) rebuildWidgets()
                },
            )
            addRenderableWidget(
                SkyButton(
                    left + 240, y, 24, 20, Component.literal("X"),
                    style = SkyButton.Style.DANGER,
                ) {
                    if (!SkyziyouConfig.deleteProfile(name)) {
                        minecraft.player?.sendSystemMessage(
                            Component.literal("§c[Profiles] " + I18n.t("profiles.last_one")),
                        )
                    }
                    rebuildWidgets()
                },
            )
        }

        // rename the active profile: the box always starts with its current name
        val nameBox = EditBox(font, left, height - 96, 200, 20, Component.empty())
        nameBox.setMaxLength(SkyziyouConfig.MAX_PROFILE_NAME)
        nameBox.value = SkyziyouConfig.activeProfile
        nameBox.setHint(Component.literal(I18n.t("profiles.name_hint")))
        addRenderableWidget(nameBox)

        addRenderableWidget(
            SkyButton(left + 206, height - 96, 90, 20, Component.literal(I18n.t("profiles.rename"))) {
                if (SkyziyouConfig.renameProfile(SkyziyouConfig.activeProfile, nameBox.value)) {
                    rebuildWidgets()
                } else {
                    minecraft.player?.sendSystemMessage(
                        Component.literal("§c[Profiles] " + I18n.t("profiles.invalid_name")),
                    )
                }
            },
        )
        addRenderableWidget(
            SkyButton(left, height - 68, 165, 20, Component.literal(I18n.t("profiles.new_copy"))) {
                if (SkyziyouConfig.createProfile(uniqueName("Profile")) != null) rebuildWidgets()
            },
        )
        addRenderableWidget(
            SkyButton(left + 171, height - 68, 165, 20, Component.literal(I18n.t("profiles.new_blank"))) {
                if (SkyziyouConfig.createProfile(uniqueName("Profile"), copyFrom = null) != null) {
                    rebuildWidgets()
                }
            },
        )
    }

    override fun drawBehind(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val left = centerX(rowW)
        graphics.text(font, I18n.t("profiles.hint1"), left, 28, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("profiles.hint2"), left, 40, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("profiles.hint_current"), left, startY - 11, SkyTheme.TEXT_DIM, false)
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

    private fun visibleRows(): Int = ((height - startY - 116) / rowH).coerceAtLeast(1)

    private fun maxScroll(): Int = (SkyziyouConfig.profiles.size - visibleRows()).coerceAtLeast(0)

    /** Generates an unused profile name, keeping the result inside the name length limit. */
    private fun uniqueName(base: String): String {
        val root = base.trim().take(SkyziyouConfig.MAX_PROFILE_NAME)
        if (!SkyziyouConfig.profiles.containsKey(root)) return root
        var index = 2
        while (index < 1000) {
            val suffix = " $index"
            val candidate = root.take(SkyziyouConfig.MAX_PROFILE_NAME - suffix.length) + suffix
            if (!SkyziyouConfig.profiles.containsKey(candidate)) return candidate
            index++
        }
        return root
    }
}
