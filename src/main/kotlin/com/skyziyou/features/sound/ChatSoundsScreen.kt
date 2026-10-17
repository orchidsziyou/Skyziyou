package com.skyziyou.features.sound

import com.skyziyou.BaseConfigScreen
import com.skyziyou.I18n
import com.skyziyou.SkyButton
import com.skyziyou.SkyTheme
import com.skyziyou.SkyziyouConfig
import com.skyziyou.SoundRuleEntry
import com.skyziyou.features.chat.ChatChannel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs

/**
 * "Chat Sounds" editor. Row layout:
 *   [on/off][match text][sound picker][▶][×n][channel][Aa][regex][x]
 *
 * The sound column opens [Pick] — a scrollable card listing every available
 * sound (the 23 built-ins plus all user imports) with per-entry ▶ preview,
 * instead of the old click-to-cycle button. The card also hosts the
 * "add custom sound" flow: native file dialog (.ogg/.mp3) -> in-game naming
 * card -> background copy+decode -> instantly selectable. Imported files land
 * in config/skyziyou/sounds and play through [CustomSoundPlayer] (Java Sound).
 */
class ChatSoundsScreen : BaseConfigScreen("cat.sounds") {

    private var scroll = 0

    // rule row being assigned a sound; -1 = picker closed, DEATH_ROW = fixed death row
    private var pickerRule = -1
    private var pickerScroll = 0

    // naming/import card state
    private enum class Dialog { NONE, IMPORT, RENAME }
    private var dialog = Dialog.NONE
    private var importSrc: String? = null
    private var renameId: String? = null
    private var draftName = ""
    private var errorKey: String? = null
    private var importing = false
    private var picking = false

    private val rowH = 25
    private val startY = 58

    /** Pseudo row index for the fixed "play on death" row (it never scrolls). */
    private val deathRowPickerId = -2

    // rule row column geometry (total 348)
    private val colW = 348
    private val matchX = 26
    private val matchW = 88
    private val pickX = 118
    private val pickW = 82
    private val playX = 204
    private val playW = 14
    private val repeatX = 222
    private val repeatW = 24
    private val channelX = 248
    private val channelW = 30
    private val caseX = 280
    private val caseW = 20
    private val regexX = 302
    private val regexW = 24
    private val deleteX = 328

    // picker card geometry
    private val cardW = 300
    private val cardTop = 34
    private val cardItemH = 20
    private val listPadTop = 28
    private val listPadBottom = 44

    // import/rename dialog geometry
    private val dlgW = 280
    private val dlgH = 122

    /** Header/hint rows in the currently shown picker list, remembered for drawing. */
    private val headerDraws = ArrayList<Triple<Int, Int, String>>()

    private sealed interface PickItem {
        val key: String
        data class Header(override val key: String) : PickItem
        data class Hint(override val key: String) : PickItem
        data class Sound(val path: String, val label: String, val customId: String?) : PickItem {
            override val key get() = path
        }
    }

    override fun buildPage() {
        when {
            dialog != Dialog.NONE -> buildDialog()
            pickerRule != -1 -> buildPicker()
            else -> buildRules()
        }
    }

    // ---------------------------------------------------------------- rules list

    private fun buildRules() {
        val left = centerX(colW)
        val entries = SkyziyouConfig.chatSounds
        scroll = scroll.coerceIn(0, maxScroll())

        addRenderableWidget(
            toggleButton(I18n.t("common.enabled"), SkyziyouConfig.chatSoundsEnabled, left, 26, 110) {
                SkyziyouConfig.chatSoundsEnabled = it
            },
        )
        addRenderableWidget(
            toggleButton(I18n.t("common.log"), SkyziyouConfig.chatSoundsLog, left + 114, 26, 110) {
                SkyziyouConfig.chatSoundsLog = it
            },
        )

        // fixed special row (first list row): sound played when the death screen opens
        addRenderableWidget(
            checkButton(left, startY, SkyziyouConfig.deathSoundEnabled) {
                SkyziyouConfig.deathSoundEnabled = it
            },
        )
        addRenderableWidget(
            SkyButton(left + pickX, startY, pickW, 20, soundPathLabel(SkyziyouConfig.deathSound)) {
                openPicker(deathRowPickerId)
            },
        )
        addRenderableWidget(
            SkyButton(left + playX, startY, playW, 20, Component.literal("§a▶")) {
                ChatSounds.playRepeated(SkyziyouConfig.deathSound, SkyziyouConfig.deathSoundRepeat)
            },
        )
        addRenderableWidget(
            SkyButton(left + repeatX, startY, repeatW, 20, Component.literal("§e×${SkyziyouConfig.deathSoundRepeat}")) {
                SkyziyouConfig.deathSoundRepeat =
                    if (SkyziyouConfig.deathSoundRepeat >= 5) 1 else SkyziyouConfig.deathSoundRepeat + 1
                it.message = Component.literal("§e×${SkyziyouConfig.deathSoundRepeat}")
            },
        )

        for (row in 0 until visibleRows()) {
            val index = scroll + row
            if (index >= entries.size) break
            val y = startY + rowH + row * rowH
            val rule = entries[index]

            addRenderableWidget(checkButton(left, y, rule.enabled) { rule.enabled = it })

            val matchBox = EditBox(font, left + matchX, y, matchW, 20, Component.empty())
            matchBox.setMaxLength(128)
            matchBox.value = rule.pattern
            matchBox.setHint(Component.literal(I18n.t("sounds.match_hint")))
            matchBox.setResponder { rule.pattern = it }
            addRenderableWidget(matchBox)

            addRenderableWidget(
                SkyButton(left + pickX, y, pickW, 20, soundButtonLabel(rule)) {
                    openPicker(index)
                },
            )
            addRenderableWidget(
                SkyButton(left + playX, y, playW, 20, Component.literal("§a▶")) {
                    ChatSounds.playRepeated(rule.sound, rule.repeat)
                },
            )
            addRenderableWidget(
                SkyButton(left + repeatX, y, repeatW, 20, repeatLabel(rule)) {
                    rule.repeat = if (rule.repeat >= 5) 1 else rule.repeat + 1
                    it.message = repeatLabel(rule)
                },
            )
            addRenderableWidget(
                SkyButton(left + channelX, y, channelW, 20, Component.literal(ChatChannel.fromNameOrAny(rule.channel).label)) {
                    rule.channel = nextChannel(rule.channel).name
                    it.message = Component.literal(ChatChannel.fromNameOrAny(rule.channel).label)
                },
            )
            addRenderableWidget(
                SkyButton(left + caseX, y, caseW, 20, caseLabel(rule)) {
                    rule.ignoreCase = !rule.ignoreCase
                    it.message = caseLabel(rule)
                },
            )
            addRenderableWidget(
                SkyButton(left + regexX, y, regexW, 20, regexLabel(rule)) {
                    rule.regex = !rule.regex
                    it.message = regexLabel(rule)
                },
            )
            addRenderableWidget(
                SkyButton(
                    left + deleteX, y, 20, 20, Component.literal("X"),
                    style = SkyButton.Style.DANGER,
                ) {
                    entries.removeAt(index)
                    rebuildWidgets()
                },
            )
        }

        addRenderableWidget(
            SkyButton(centerX(200), height - 30, 200, 20, Component.literal(I18n.t("sounds.add"))) {
                entries.add(SoundRuleEntry())
                scroll = maxScroll()
                rebuildWidgets()
            },
        )
    }

    // ---------------------------------------------------------------- sound picker

    private fun pickerItems(): List<PickItem> {
        val items = ArrayList<PickItem>(SoundLibrary.CHOICES.size + SkyziyouConfig.customSounds.size + 4)
        items.add(PickItem.Header("sounds.picker_builtin"))
        SoundLibrary.CHOICES.forEach { items.add(PickItem.Sound(it.path, I18n.t(it.labelKey), null)) }
        items.add(PickItem.Header("sounds.picker_custom"))
        if (SkyziyouConfig.customSounds.isEmpty()) {
            items.add(PickItem.Hint("sounds.picker_empty"))
        } else {
            SkyziyouConfig.customSounds.forEach {
                items.add(PickItem.Sound(SoundLibrary.customRef(it.id), it.name, it.id))
            }
        }
        return items
    }

    private fun visibleSlots(): Int = ((height - cardTop - 26 - listPadTop - listPadBottom) / cardItemH).coerceAtLeast(1)

    private fun buildPicker() {
        val left = centerX(cardW)
        val items = pickerItems()
        pickerScroll = pickerScroll.coerceIn(0, (items.size - visibleSlots()).coerceAtLeast(0))
        headerDraws.clear()

        val listY = cardTop + listPadTop
        var slot = 0
        for (i in pickerScroll until items.size) {
            if (slot >= visibleSlots()) break
            val y = listY + slot * cardItemH
            when (val item = items[i]) {
                is PickItem.Header -> headerDraws.add(Triple(left + 10, y + 6, "§7§l" + I18n.t(item.key)))
                is PickItem.Hint -> headerDraws.add(Triple(left + 10, y + 6, "§8" + I18n.t(item.key)))
                is PickItem.Sound -> {
                    // right-aligned cluster inside [left+8, edge]; play w24 / delete w22 / rename w18
                    val edge = left + cardW - 8
                    val playEdgeX = edge - 24
                    val labelEnd = if (item.customId != null) edge - 72 else playEdgeX - 4

                    addRenderableWidget(
                        SkyButton(left + 8, y, labelEnd - (left + 8), 20, Component.literal(pickerLabel(item))) {
                            if (pickerRule == deathRowPickerId) {
                                SkyziyouConfig.deathSound = item.path
                            } else {
                                SkyziyouConfig.chatSounds.getOrNull(pickerRule)?.sound = item.path
                            }
                            SkyziyouConfig.save()
                            closePicker()
                        },
                    )
                    if (item.customId != null) {
                        val id = item.customId
                        addRenderableWidget(
                            SkyButton(edge - 68, y, 18, 20, Component.literal("§e✎")) {
                                openRename(id)
                            },
                        )
                        addRenderableWidget(
                            SkyButton(edge - 46, y, 22, 20, Component.literal("§cX"), style = SkyButton.Style.DANGER) {
                                deleteCustom(id)
                            },
                        )
                    }
                    addRenderableWidget(
                        SkyButton(playEdgeX, y, 24, 20, Component.literal("§a▶")) {
                            SoundLibrary.playAny(item.path)
                        },
                    )
                }
            }
            slot++
        }

        addRenderableWidget(
            SkyButton(left + 8, height - 50, 138, 20, Component.literal(I18n.t("sounds.add_custom"))) {
                pickFile()
            },
        )
        addRenderableWidget(
            SkyButton(left + 154, height - 50, 138, 20, Component.literal(I18n.t("common.done"))) {
                closePicker()
            },
        )
    }

    // ---------------------------------------------------------------- name dialog

    private fun buildDialog() {
        val dlgX = centerX(dlgW)
        val dlgY = height / 2 - dlgH / 2 - 10

        val box = EditBox(font, dlgX + 10, dlgY + 34, dlgW - 20, 20, Component.empty())
        box.setMaxLength(32)
        box.value = draftName
        box.setHint(Component.literal(I18n.t("sounds.name_hint")))
        box.setResponder { draftName = it }
        box.setFocused(true)
        addRenderableWidget(box)

        val confirmLabel = when {
            importing -> I18n.t("sounds.importing")
            dialog == Dialog.RENAME -> I18n.t("sounds.rename_confirm")
            else -> I18n.t("sounds.import_confirm")
        }
        addRenderableWidget(
            SkyButton(dlgX + 10, dlgY + dlgH - 30, 124, 20, Component.literal(confirmLabel)) {
                if (!importing) confirmDialog()
            },
        )
        addRenderableWidget(
            SkyButton(dlgX + 146, dlgY + dlgH - 30, 124, 20, Component.literal(I18n.t("common.back"))) {
                if (!importing) cancelDialog()
            },
        )
    }

    // ---------------------------------------------------------------- flow

    private fun openPicker(ruleIndex: Int) {
        pickerRule = ruleIndex
        pickerScroll = 0
        rebuildWidgets()
    }

    private fun closePicker() {
        pickerRule = -1
        rebuildWidgets()
    }

    /** Native file chooser on a daemon thread; the game keeps rendering behind it. */
    private fun pickFile() {
        if (picking) return // tinyfd keeps global dialog state: never open two at once
        picking = true
        // snapshot localised strings on the client thread before leaving it
        val title = I18n.t("sounds.dialog_title")
        val filter = I18n.t("sounds.dialog_filter")
        Thread({
            val picked = runCatching {
                MemoryStack.stackPush().use { stack ->
                    val patterns: PointerBuffer = stack.mallocPointer(2)
                    patterns.put(stack.UTF8("*.ogg"))
                    patterns.put(stack.UTF8("*.mp3"))
                    patterns.flip()
                    TinyFileDialogs.tinyfd_openFileDialog(title, null, patterns, filter, false)
                }
            }.getOrNull()
            // always release the guard on the client thread, even if the native call failed
            Minecraft.getInstance().execute {
                picking = false
                if (picked != null) openImport(picked)
            }
        }, "skyziyou-sound-pick").apply { isDaemon = true }.start()
    }

    private fun openImport(path: String) {
        dialog = Dialog.IMPORT
        importSrc = path
        renameId = null
        errorKey = null
        importing = false
        draftName = CustomSoundStore.displayNameOf(path)
        rebuildWidgets()
    }

    private fun openRename(id: String) {
        val entry = SkyziyouConfig.customSounds.firstOrNull { it.id == id } ?: return
        dialog = Dialog.RENAME
        renameId = id
        importSrc = null
        errorKey = null
        importing = false
        draftName = entry.name
        rebuildWidgets()
    }

    private fun cancelDialog() {
        dialog = Dialog.NONE
        importSrc = null
        renameId = null
        errorKey = null
        importing = false
        rebuildWidgets()
    }

    private fun confirmDialog() {
        if (importing) return
        val name = draftName.trim()
        if (name.isBlank()) {
            errorKey = "sounds.err_name"
            rebuildWidgets()
            return
        }
        if (dialog == Dialog.RENAME) {
            SkyziyouConfig.customSounds.firstOrNull { it.id == renameId }?.name = name
            SkyziyouConfig.save()
            cancelDialog()
            return
        }

        // import: copy + decode off-thread so a large file never freezes the game
        val source = importSrc ?: return
        importing = true
        errorKey = null
        rebuildWidgets()
        Thread({
            val outcome = runCatching { CustomSoundStore.importFile(source, name) }
            Minecraft.getInstance().execute {
                outcome
                    .onSuccess { (entry, pcm) ->
                        SkyziyouConfig.customSounds.add(entry)
                        SkyziyouConfig.save()
                        CustomSoundPlayer.preload(entry.id, pcm)
                        dialog = Dialog.NONE
                        importing = false
                        importSrc = null
                        renameId = null
                        errorKey = null
                        // reveal the new entry at the bottom of the custom section
                        pickerScroll = (pickerItems().size - visibleSlots()).coerceAtLeast(0)
                        rebuildWidgets()
                    }
                    .onFailure { throwable ->
                        importing = false
                        errorKey = (throwable as? SoundImportException)?.reasonKey ?: "sounds.err_read"
                        rebuildWidgets()
                    }
            }
        }, "skyziyou-sound-import").apply { isDaemon = true }.start()
    }

    private fun deleteCustom(id: String) {
        val entry = SkyziyouConfig.customSounds.firstOrNull { it.id == id } ?: return
        CustomSoundStore.deleteFile(entry)
        CustomSoundPlayer.evict(id)
        SkyziyouConfig.customSounds.remove(entry)
        SkyziyouConfig.save()
        rebuildWidgets()
    }

    // ---------------------------------------------------------------- input

    override fun keyPressed(event: KeyEvent): Boolean {
        if (dialog != Dialog.NONE) {
            when (event.key()) {
                257, 335 -> { // Enter / numpad Enter -> confirm
                    if (!importing) confirmDialog()
                    return true
                }
                256 -> { // Esc -> cancel
                    if (!importing) cancelDialog()
                    return true
                }
                else -> return super.keyPressed(event) // let the name box handle backspace/arrows/etc.
            }
        }
        if (pickerRule != -1 && event.key() == 256) {
            closePicker()
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        // modal dialog: swallow clicks outside the card so the sidebar/page behind can't be touched
        if (dialog != Dialog.NONE) {
            val mx = event.x().toInt()
            val my = event.y().toInt()
            val dx = centerX(dlgW)
            val dy = height / 2 - dlgH / 2 - 10
            val inside = mx in dx..(dx + dlgW) && my in dy..(dy + dlgH)
            if (!inside) return true
        }
        // click outside the open card closes the picker (but never the whole screen)
        if (pickerRule != -1 && dialog == Dialog.NONE) {
            val mx = event.x().toInt()
            val my = event.y().toInt()
            val left = centerX(cardW)
            val inside = mx in left..(left + cardW) && my in cardTop..(height - 26)
            if (!inside) {
                closePicker()
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (pickerRule != -1 && dialog == Dialog.NONE) {
            val max = (pickerItems().size - visibleSlots()).coerceAtLeast(0)
            pickerScroll = (pickerScroll - verticalAmount.toInt()).coerceIn(0, max)
            rebuildWidgets()
            return true
        }
        val newScroll = (scroll - verticalAmount.toInt()).coerceIn(0, maxScroll())
        if (newScroll != scroll) {
            scroll = newScroll
            rebuildWidgets()
        }
        return true
    }

    override fun drawBehind(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (dialog != Dialog.NONE) {
            drawCard(graphics, centerX(dlgW), height / 2 - dlgH / 2 - 10, dlgW, dlgH)
        } else if (pickerRule != -1) {
            drawCard(graphics, centerX(cardW), cardTop, cardW, height - 26 - cardTop)
        }
    }

    private fun drawCard(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(0, 0, width, height, 0xD0101014.toInt())
        graphics.fill(x, y, x + w, y + h, SkyTheme.ROW_BG)
        val border = 0xFF4A4A58.toInt()
        graphics.fill(x, y, x + w, y + 1, border)
        graphics.fill(x, y + h - 1, x + w, y + h, border)
        graphics.fill(x, y, x + 1, y + h, border)
        graphics.fill(x + w - 1, y, x + w, y + h, border)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        when {
            dialog != Dialog.NONE -> drawDialogText(graphics)
            pickerRule != -1 -> drawPickerText(graphics)
            else -> drawRulesText(graphics)
        }
    }

    private fun drawRulesText(graphics: GuiGraphicsExtractor) {
        val left = centerX(colW)
        graphics.text(font, I18n.t("sounds.header_match"), left + matchX, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("sounds.header_sound"), left + pickX, startY - 10, SkyTheme.TEXT_DIM, false)
        graphics.text(font, I18n.t("sounds.header_repeat"), left + repeatX, startY - 10, -1, false)
        graphics.text(font, "§7ch", left + channelX, startY - 10, -1, false)
        // label for the fixed death row (the row itself has no match text box)
        graphics.text(
            font,
            "§c☠ §f" + clipText(I18n.t("sounds.death_row"), matchW - 12),
            left + matchX,
            startY + 6,
            SkyTheme.TEXT,
            false,
        )
        graphics.text(font, I18n.t("sounds.legend"), left, height - 70, SkyTheme.TEXT_DIM, false)
    }

    private fun drawPickerText(graphics: GuiGraphicsExtractor) {
        val left = centerX(cardW)
        graphics.text(font, "§l" + I18n.t("sounds.picker_title"), left + 10, cardTop + 8, SkyTheme.TEXT, false)
        headerDraws.forEach { (x, y, text) ->
            graphics.text(font, text, x, y, SkyTheme.TEXT_DIM, false)
        }
        // format hint directly above the "add custom sound" button
        graphics.text(
            font,
            "§8" + clipText(I18n.t("sounds.picker_format"), cardW - 20),
            left + 10,
            height - 64,
            SkyTheme.TEXT_DIM,
            false,
        )
    }

    private fun drawDialogText(graphics: GuiGraphicsExtractor) {
        val dlgX = centerX(dlgW)
        val dlgY = height / 2 - dlgH / 2 - 10
        val title = if (dialog == Dialog.RENAME) I18n.t("sounds.rename_title") else I18n.t("sounds.import_title")
        graphics.text(font, "§l$title", dlgX + 10, dlgY + 12, SkyTheme.TEXT, false)
        val hint = I18n.t("sounds.format_hint")
        graphics.text(font, "§8$hint", dlgX + 10, dlgY + 58, SkyTheme.TEXT_DIM, false)
        val err = errorKey?.let { I18n.t(it) }
        if (err != null) {
            graphics.text(font, "§c" + clipText(err, dlgW - 20), dlgX + 10, dlgY + 72, SkyTheme.RED, false)
        }
    }

    // ---------------------------------------------------------------- helpers

    // one list slot is permanently occupied by the fixed death row
    private fun visibleRows(): Int = ((height - startY - 78) / rowH - 1).coerceAtLeast(0)

    private fun maxScroll(): Int = (SkyziyouConfig.chatSounds.size - visibleRows()).coerceAtLeast(0)

    private fun soundButtonLabel(rule: SoundRuleEntry): Component = soundPathLabel(rule.sound)

    private fun soundPathLabel(path: String): Component {
        val clipped = clipText(SoundLibrary.label(path), pickW - 8)
        val colour = if (SoundLibrary.isCustom(path)) "§d♪ " else "§b"
        return Component.literal(colour + clipped)
    }

    private fun pickerLabel(item: PickItem.Sound): String {
        val prefix = if (item.customId != null) "§d♪ " else "§f"
        return prefix + clipText(item.label, (if (item.customId != null) 212 else 256))
    }

    /** Truncates with an ellipsis if [text] is wider than [maxWidth] pixels. */
    private fun clipText(text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var clipped = text
        while (clipped.isNotEmpty() && font.width(clipped + "…") > maxWidth) clipped = clipped.dropLast(1)
        return clipped + "…"
    }

    private fun repeatLabel(rule: SoundRuleEntry): Component = Component.literal("§e×${rule.repeat}")

    private fun regexLabel(rule: SoundRuleEntry): Component =
        Component.literal(if (rule.regex) "§b.*" else "§7Ab")

    private fun caseLabel(rule: SoundRuleEntry): Component =
        Component.literal(if (rule.ignoreCase) "§aAa" else "§cAa")

    private fun nextChannel(currentName: String): ChatChannel {
        val channels = ChatChannel.selectable
        val current = ChatChannel.fromNameOrAny(currentName)
        val index = channels.indexOf(current)
        return channels[(index + 1) % channels.size]
    }
}
