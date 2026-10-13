package com.skyziyou

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.Util

/**
 * "General" sidebar tab: mod name, running version, short description, project
 * links and a MANUAL update check. The check only queries GitHub's latest-release
 * API and reports here; downloading/installing stays with the user via the
 * releases page opened in the system browser.
 */
class GeneralScreen : BaseConfigScreen("cat.general") {

    companion object {
        const val GITHUB_URL = "https://github.com/orchidsziyou/Skyziyou"
        const val RELEASES_URL = "$GITHUB_URL/releases"

        private val VERSION_REGEX = Regex(""""version"\s*:\s*"([^"]+)"""")

        private var cachedVersion: String? = null

        /** Installed mod version as declared in fabric.mod.json, or "?" if unavailable. */
        fun modVersion(): String = cachedVersion ?: readVersion().also { cachedVersion = it }

        private fun readVersion(): String {
            val json = GeneralScreen::class.java.getResourceAsStream("/fabric.mod.json")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return "?"
            return VERSION_REGEX.find(json)?.groupValues?.get(1) ?: "?"
        }
    }

    private val cardW = 300
    private val cardH = 200

    private val buttonW = 220

    private fun cardX() = centerX(cardW)
    private fun cardY() = 38

    override fun buildPage() {
        addRenderableWidget(
            SkyButton(
                centerX(buttonW),
                cardY() + 120,
                buttonW,
                20,
                Component.literal(I18n.t("general.check_updates")),
            ) {
                UpdateChecker.check()
            },
        )
        addRenderableWidget(
            SkyButton(
                centerX(buttonW),
                cardY() + 144,
                buttonW,
                20,
                Component.literal(I18n.t("general.open_releases")),
            ) {
                runCatching { Util.getPlatform().openUri(UpdateChecker.latestPageUrl ?: RELEASES_URL) }
            },
        )
        addRenderableWidget(
            SkyButton(
                centerX(buttonW),
                cardY() + cardH - 28,
                buttonW,
                20,
                Component.literal(I18n.t("general.open_github")),
            ) {
                // Desktop.browse is unsupported on a few locked-down JVMs; a failure must
                // never crash the config screen, so swallow it (the URL is shown as text).
                runCatching { Util.getPlatform().openUri(GITHUB_URL) }
            },
        )
    }

    override fun drawBehind(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val x = cardX()
        val y = cardY()
        graphics.fill(x, y, x + cardW, y + cardH, SkyTheme.ROW_BG)
        graphics.fill(x, y, x + cardW, y + 1, SkyTheme.TAB_HOVER)

        val cx = x + cardW / 2
        graphics.centeredText(font, Component.literal("§lSkyziyou"), cx, y + 13, SkyTheme.TEXT)
        graphics.centeredText(
            font,
            Component.literal(I18n.t("general.version", modVersion())),
            cx,
            y + 30,
            SkyTheme.TEXT,
        )
        graphics.centeredText(font, Component.literal(I18n.t("general.line1")), cx, y + 49, SkyTheme.TEXT_DIM)
        graphics.centeredText(font, Component.literal(I18n.t("general.line2")), cx, y + 62, SkyTheme.TEXT_DIM)
        graphics.centeredText(
            font,
            Component.literal("§7GitHub  §b$GITHUB_URL"),
            cx,
            y + 84,
            SkyTheme.TEXT,
        )

        // update status line (state arrives from the background HTTP thread)
        val (statusText, statusColor) = statusLine()
        graphics.centeredText(font, Component.literal(statusText), cx, y + 102, statusColor)
    }

    /** Returns the localised update-status text and its colour for [drawBehind]. */
    private fun statusLine(): Pair<String, Int> = when (UpdateChecker.status) {
        UpdateChecker.Status.IDLE ->
            I18n.t("general.update_idle") to SkyTheme.TEXT_DIM
        UpdateChecker.Status.CHECKING ->
            I18n.t("general.update_checking") to SkyTheme.TEXT_DIM
        UpdateChecker.Status.UP_TO_DATE ->
            I18n.t("general.update_latest", modVersion()) to SkyTheme.GREEN
        UpdateChecker.Status.AVAILABLE ->
            "§e§l" + I18n.t("general.update_available", UpdateChecker.latestVersion ?: "?") to -1
        UpdateChecker.Status.FAILED ->
            "§c" + I18n.t("general.update_failed", UpdateChecker.failureReason ?: "") to -1
    }
}
