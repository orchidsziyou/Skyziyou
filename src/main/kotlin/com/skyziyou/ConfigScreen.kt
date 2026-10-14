package com.skyziyou

import com.skyziyou.features.alert.ScreenAlertsScreen
import com.skyziyou.features.chat.ChatRulesScreen
import com.skyziyou.features.command.CommandAliasesScreen
import com.skyziyou.features.highlight.EntityHighlightsScreen
import com.skyziyou.features.mineshaft.MineshaftCorpseScreen
import com.skyziyou.features.party.AutoInviteConfigScreen
import com.skyziyou.features.sound.ChatSoundsScreen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Every page reachable from the config sidebar. [titleKey] matches the key a page
 * passes to [BaseConfigScreen] so the shell can highlight the active tab. The first
 * entry is what `/sz` opens.
 */
enum class ConfigCategory(
    val titleKey: String,
    val descKey: String,
    val factory: () -> BaseConfigScreen,
) {
    GENERAL("cat.general", "cat.general.desc", { GeneralScreen() }),
    WHEEL("cat.wheel", "cat.wheel.desc", { WheelConfigScreen() }),
    SHORTCUTS("cat.shortcuts", "cat.shortcuts.desc", { ShortcutsScreen() }),
    CHAT_RULES("cat.chatrules", "cat.chatrules.desc", { ChatRulesScreen() }),
    CHAT_SOUNDS("cat.sounds", "cat.sounds.desc", { ChatSoundsScreen() }),
    ALIASES("aliases.title", "cat.aliases.desc", { CommandAliasesScreen() }),
    ALERTS("cat.alerts", "cat.alerts.desc", { ScreenAlertsScreen() }),
    CORPSES("corpses.title", "cat.corpses.desc", { MineshaftCorpseScreen() }),
    INVITE("cat.invite", "cat.invite.desc", { AutoInviteConfigScreen() }),
    HIGHLIGHTS("cat.highlights", "cat.highlights.desc", { EntityHighlightsScreen() }),
}

/**
 * Shared shell for every config page, SkyHanni-style: a flat dark background with a
 * fixed left [SkyTheme.SIDEBAR_W]-wide category sidebar (wordmark, category tabs with
 * the active one marked by a green edge, language toggle and Done at the bottom) and
 * a content area on the right where subclasses lay out their widgets via [buildPage].
 *
 * Pages are still separate [Screen]s (switching a tab just opens another page), which
 * keeps every existing list editor (rules, alerts, highlights, ...) unchanged in
 * structure — only their anchor coordinates move from the screen centre to
 * [pageCX] / [centerX].
 */
abstract class BaseConfigScreen(private val titleKey: String) : Screen(Component.literal("")) {

    protected val pageX: Int get() = SkyTheme.SIDEBAR_W + 8
    protected val pageW: Int get() = width - SkyTheme.SIDEBAR_W - 16
    protected val pageCX: Int get() = pageX + pageW / 2

    /** Left X for a content widget of [w] pixels so it is centred in the content area. */
    protected fun centerX(w: Int): Int = pageCX - w / 2

    /** Localised page title drawn by the shell at the top of the content area. */
    protected fun titleText(): String = I18n.t(titleKey)

    /**
     * Subclasses populate their content widgets here. Called after the shell has put
     * the sidebar together in [init]; do not override init itself.
     */
    protected open fun buildPage() {}

    /**
     * Page-specific background drawing (cards, info text), rendered after the shell
     * background but BEFORE the widgets, so panels sit behind their buttons.
     */
    protected open fun drawBehind(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {}

    private val tabStartY = 28

    /** Vertical tab pitch, compressed on short windows so every tab + footer fits. */
    private fun tabPitch(): Int {
        val available = height - 48 - 6 - tabStartY
        return (available / ConfigCategory.entries.size).coerceIn(15, 24)
    }

    private fun tabHeight(): Int = (tabPitch() - 3).coerceAtLeast(13).coerceAtMost(20)

    private fun tabY(index: Int): Int = tabStartY + index * tabPitch()

    final override fun init() {
        val tabW = SkyTheme.SIDEBAR_W - 12
        ConfigCategory.entries.forEachIndexed { index, category ->
            addRenderableWidget(
                SkyButton(
                    6,
                    tabY(index),
                    tabW,
                    tabHeight(),
                    Component.literal(I18n.t(category.titleKey)),
                    style = SkyButton.Style.TAB,
                    accent = category.titleKey == titleKey,
                ) {
                    //~ if < 26.2 'minecraft.gui.setScreen' -> 'minecraft.setScreen'
                    minecraft.gui.setScreen(category.factory())
                },
            )
        }

        addRenderableWidget(
            SkyButton(
                6,
                height - 48,
                tabW,
                20,
                Component.literal(I18n.t("common.language_button")),
            ) {
                SkyziyouConfig.language = if (I18n.isZh()) I18n.EN else I18n.ZH
                I18n.language = SkyziyouConfig.language
                SkyziyouConfig.save()
                rebuildWidgets()
            },
        )
        addRenderableWidget(
            SkyButton(
                6,
                height - 26,
                tabW,
                20,
                Component.literal(I18n.t("common.done")),
            ) { onClose() },
        )

        buildPage()
    }

    override fun onClose() {
        SkyziyouConfig.save()
        // closing the config (Esc / Done) returns to the game; tabs navigate directly
        //~ if < 26.2 'minecraft.gui.setScreen' -> 'minecraft.setScreen'
        minecraft.gui.setScreen(null)
    }

    override fun extractRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        graphics.fill(0, 0, width, height, SkyTheme.BG)
        graphics.fill(0, 0, SkyTheme.SIDEBAR_W, height, SkyTheme.SIDEBAR_BG)
        graphics.fill(SkyTheme.SIDEBAR_W, 0, SkyTheme.SIDEBAR_W + 1, height, 0xFF000000.toInt())
        graphics.text(font, "§lSkyziyou", 10, 10, SkyTheme.TEXT, false)

        // page cards/info text behind the widgets
        drawBehind(graphics, mouseX, mouseY)

        // widgets (sidebar tabs + page content)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        // content title
        val title = titleText()
        graphics.text(font, title, pageCX - font.width(title) / 2, 10, SkyTheme.TEXT, false)

        renderSidebarTooltip(graphics, mouseX, mouseY)
    }

    /** Hovering a sidebar tab shows its localised description next to the cursor. */
    private fun renderSidebarTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (mouseX > SkyTheme.SIDEBAR_W) return
        val index = ConfigCategory.entries.indexOfFirst {
            val ty = tabY(it.ordinal)
            mouseX in 6..(6 + SkyTheme.SIDEBAR_W - 12) && mouseY in ty..(ty + tabHeight())
        }
        if (index < 0) return
        val desc = I18n.t(ConfigCategory.entries[index].descKey)
        val w = font.width(desc) + 8
        val x = SkyTheme.SIDEBAR_W + 4
        val y = (mouseY - 12).coerceIn(4, height - 16)
        graphics.fill(x, y, x + w, y + 14, 0xF0202028.toInt())
        graphics.text(font, desc, x + 4, y + 3, SkyTheme.TEXT, false)
    }

    /** SkyHanni-style ON/OFF toggle (label left, green check / red cross right). */
    protected fun toggleButton(
        label: String,
        initial: Boolean,
        x: Int,
        y: Int,
        w: Int,
        onChange: (Boolean) -> Unit,
    ): SkyToggle = SkyToggle(x, y, w, 20, label, initial, onChange)

    /** Compact 22px check/cross switch for the on/off column of editor list rows. */
    protected fun checkButton(x: Int, y: Int, initial: Boolean, onChange: (Boolean) -> Unit): SkyToggle =
        SkyToggle(x, y, 22, 20, "", initial, onChange)
}
