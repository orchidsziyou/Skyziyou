package com.skyziyou

import com.google.gson.JsonParser
import org.lwjgl.glfw.GLFW
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** One custom shortcut: a key (GLFW keycode, 0-7 = mouse button) bound to a command or chat message. */
class ShortcutEntry(
    var key: Int = GLFW.GLFW_KEY_UNKNOWN,
    var command: String = "",
    var enabled: Boolean = true,
)

/**
 * One custom chat rule: when [pattern] is found in a chat line of the restricted
 * [channel] (a [com.skyziyou.features.chat.ChatChannel] name), [action] is sent
 * (leading slash = command, otherwise chat). [regex] switches [pattern] from
 * case-insensitive contains matching to full regex matching.
 */
class ChatRuleEntry(
    var enabled: Boolean = true,
    var pattern: String = "",
    var action: String = "",
    var channel: String = "ANY",
    var regex: Boolean = false,
)

/**
 * One command alias: typing exactly "/<[alias]>" (the stored value may include the
 * leading slash) runs [target] instead — a command when it starts with '/',
 * otherwise a chat message. Matching is exact and case-insensitive; extra arguments
 * are not forwarded.
 */
class CommandAliasEntry(
    var enabled: Boolean = true,
    var alias: String = "",
    var target: String = "",
)

/**
 * Simple JSON-backed config, stored in `.minecraft/config/skyziyou.json`.
 * Entries starting with '/' are executed as commands, anything else is sent as a chat message.
 */
object SkyziyouConfig {

    /** GLFW keycode of the wheel trigger; GLFW_KEY_UNKNOWN (-1) means unbound. Mouse buttons 0-7 are also allowed. */
    var keybind: Int = GLFW.GLFW_KEY_UNKNOWN

    /** Master switch; when off the wheel cannot be opened. */
    var enabled: Boolean = true

    /** Run the selected entry when the trigger is released instead of on left click. */
    var executeOnRelease: Boolean = false

    /** Print a local chat line whenever the wheel runs an entry. */
    var showLogMessage: Boolean = false

    /** Minimum mouse travel (GUI pixels) before a direction counts as selected. */
    var deadZone: Int = 40

    /** Wheel disc size in percent (50-200). */
    var sizePercent: Int = 100

    /** Font size in percent; 50 is half the vanilla size, 200 is double. */
    var fontScalePercent: Int = 100

    /** Seconds the wheel may stay open before it auto-closes without executing. */
    var timeoutSeconds: Int = 10

    /** Up, Up Right, Right, Down Right, Down, Down Left, Left, Up Left */
    val slots: MutableList<String> = mutableListOf(
        "/ah", "", "", "",
        "/bz", "", "", "",
    )

    /** Custom key shortcuts; pressing the bound key runs the entry immediately. */
    val shortcuts: MutableList<ShortcutEntry> = mutableListOf()

    /** Custom Chat Rules master switch. */
    var chatRulesEnabled: Boolean = true

    /** Print a local chat line whenever a chat rule fires. */
    var chatRulesLog: Boolean = true

    /** Only evaluate rules while connected to Hypixel (checked via server brand). */
    var chatRulesHypixelOnly: Boolean = true

    /** User-defined chat match -> action rules; see [ChatRuleEntry]. */
    val chatRules: MutableList<ChatRuleEntry> = mutableListOf()

    /** Auto Party Invite master switch: a PM containing only "!invite" runs "/p <sender>". */
    var autoInviteEnabled: Boolean = true

    /** Print a local chat line whenever Auto Party Invite sends an invite. */
    var autoInviteLog: Boolean = true

    /** Only react to private messages while connected to Hypixel (checked via server brand). */
    var autoInviteHypixelOnly: Boolean = true

    /** Custom Command Aliases master switch. */
    var aliasesEnabled: Boolean = true

    /** Print a local chat line whenever a command alias rewrites a typed command. */
    var aliasesLog: Boolean = true

    /** User-defined typed-command -> real command/chat aliases; see [CommandAliasEntry]. */
    val aliases: MutableList<CommandAliasEntry> = mutableListOf()

    private val file: Path
        get() = FabricLoader.getInstance().configDir.resolve("skyziyou.json")

    /** Config left behind by the mod's former name; renamed into [file] on first launch. */
    private val legacyFile: Path
        get() = FabricLoader.getInstance().configDir.resolve("commandwheel.json")

    /**
     * Reads the config file if it exists. Missing or malformed fields keep their
     * defaults; IO errors are swallowed so the game can always start.
     */
    fun load() {
        migrateLegacyConfig()
        runCatching {
            if (!Files.exists(file)) return
            val obj = JsonParser.parseString(Files.readString(file)).asJsonObject

            // only accepts existing primitive values; a missing or mistyped field keeps the default
            fun prim(name: String) = obj.get(name)?.takeIf { it.isJsonPrimitive }

            prim("keybind")?.let { keybind = it.asInt }
            prim("enabled")?.let { enabled = it.asBoolean }
            prim("executeOnRelease")?.let { executeOnRelease = it.asBoolean }
            prim("showLogMessage")?.let { showLogMessage = it.asBoolean }
            prim("deadZone")?.let { deadZone = it.asInt }
            prim("sizePercent")?.let { sizePercent = it.asInt }
            prim("fontScalePercent")?.let { fontScalePercent = it.asInt }
            prim("timeoutSeconds")?.let { timeoutSeconds = it.asInt }
            // copy at most the known slot count so an oversized array cannot break the list
            obj.getAsJsonArray("slots")?.let { array ->
                for (i in 0 until minOf(array.size(), slots.size)) {
                    slots[i] = array[i].asString
                }
            }
            // auto party invite feature
            prim("autoInviteEnabled")?.let { autoInviteEnabled = it.asBoolean }
            prim("autoInviteLog")?.let { autoInviteLog = it.asBoolean }
            prim("autoInviteHypixelOnly")?.let { autoInviteHypixelOnly = it.asBoolean }
            // custom shortcuts; malformed entries are skipped entirely
            obj.getAsJsonArray("shortcuts")?.let { array ->
                shortcuts.clear()
                for (element in array) {
                    val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val key = entry.get("key")?.takeIf { it.isJsonPrimitive }?.asInt ?: GLFW.GLFW_KEY_UNKNOWN
                    val command = entry.get("command")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                    val enabled = entry.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
                    shortcuts.add(ShortcutEntry(key, command, enabled))
                }
            }
            // custom chat rules; malformed entries are skipped entirely
            prim("chatRulesEnabled")?.let { chatRulesEnabled = it.asBoolean }
            prim("chatRulesLog")?.let { chatRulesLog = it.asBoolean }
            prim("chatRulesHypixelOnly")?.let { chatRulesHypixelOnly = it.asBoolean }
            obj.getAsJsonArray("chatRules")?.let { array ->
                chatRules.clear()
                for (element in array) {
                    val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    fun str(name: String) = entry.get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                    fun bool(name: String, default: Boolean) =
                        entry.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default
                    val rule = ChatRuleEntry(
                        enabled = bool("enabled", true),
                        pattern = str("pattern"),
                        action = str("action"),
                        channel = str("channel").ifBlank { "ANY" },
                        regex = bool("regex", false),
                    )
                    chatRules.add(rule)
                }
            }
            // custom command aliases; malformed entries are skipped entirely
            prim("aliasesEnabled")?.let { aliasesEnabled = it.asBoolean }
            prim("aliasesLog")?.let { aliasesLog = it.asBoolean }
            obj.getAsJsonArray("aliases")?.let { array ->
                aliases.clear()
                for (element in array) {
                    val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    fun str(name: String) = entry.get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                    val alias = CommandAliasEntry(
                        enabled = entry.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
                        alias = str("alias"),
                        target = str("target"),
                    )
                    aliases.add(alias)
                }
            }
        }.onFailure { it.printStackTrace() }
    }

    /**
     * One-time migration from the mod's former name: when no skyziyou.json exists yet
     * but a commandwheel.json does, rename it so existing wheel slots and shortcuts
     * carry over. Failures are ignored; [load] then simply falls back to defaults.
     */
    private fun migrateLegacyConfig() {
        runCatching {
            if (!Files.exists(file) && Files.exists(legacyFile)) {
                Files.move(legacyFile, file)
            }
        }
    }

    /**
     * Writes the config to disk. The JSON is first written to a temporary file and
     * then atomically moved over the real one, so a crash mid-write cannot leave a
     * truncated config behind.
     */
    fun save() {
        runCatching {
            val obj = com.google.gson.JsonObject()
            obj.addProperty("keybind", keybind)
            obj.addProperty("enabled", enabled)
            obj.addProperty("executeOnRelease", executeOnRelease)
            obj.addProperty("showLogMessage", showLogMessage)
            obj.addProperty("deadZone", deadZone)
            obj.addProperty("sizePercent", sizePercent)
            obj.addProperty("fontScalePercent", fontScalePercent)
            obj.addProperty("timeoutSeconds", timeoutSeconds)
            val array = com.google.gson.JsonArray()
            slots.forEach { array.add(it) }
            obj.add("slots", array)
            obj.addProperty("autoInviteEnabled", autoInviteEnabled)
            obj.addProperty("autoInviteLog", autoInviteLog)
            obj.addProperty("autoInviteHypixelOnly", autoInviteHypixelOnly)
            val shortcutArray = com.google.gson.JsonArray()
            shortcuts.forEach { shortcut ->
                val entryObj = com.google.gson.JsonObject()
                entryObj.addProperty("key", shortcut.key)
                entryObj.addProperty("command", shortcut.command)
                entryObj.addProperty("enabled", shortcut.enabled)
                shortcutArray.add(entryObj)
            }
            obj.add("shortcuts", shortcutArray)
            obj.addProperty("chatRulesEnabled", chatRulesEnabled)
            obj.addProperty("chatRulesLog", chatRulesLog)
            obj.addProperty("chatRulesHypixelOnly", chatRulesHypixelOnly)
            val chatRuleArray = com.google.gson.JsonArray()
            chatRules.forEach { rule ->
                val entryObj = com.google.gson.JsonObject()
                entryObj.addProperty("enabled", rule.enabled)
                entryObj.addProperty("pattern", rule.pattern)
                entryObj.addProperty("action", rule.action)
                entryObj.addProperty("channel", rule.channel)
                entryObj.addProperty("regex", rule.regex)
                chatRuleArray.add(entryObj)
            }
            obj.add("chatRules", chatRuleArray)
            obj.addProperty("aliasesEnabled", aliasesEnabled)
            obj.addProperty("aliasesLog", aliasesLog)
            val aliasArray = com.google.gson.JsonArray()
            aliases.forEach { alias ->
                val entryObj = com.google.gson.JsonObject()
                entryObj.addProperty("enabled", alias.enabled)
                entryObj.addProperty("alias", alias.alias)
                entryObj.addProperty("target", alias.target)
                aliasArray.add(entryObj)
            }
            obj.add("aliases", aliasArray)

            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("skyziyou.json.tmp")
            Files.writeString(tmp, com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { it.printStackTrace() }
    }
}
