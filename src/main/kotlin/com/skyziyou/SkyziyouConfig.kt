package com.skyziyou

import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** One custom shortcut: a key (GLFW keycode, 0-7 = mouse button) bound to a command or chat message. */
class ShortcutEntry(
    var key: Int = Keys.UNKNOWN,
    var command: String = "",
    var enabled: Boolean = true,
)

/**
 * One custom chat rule: when [pattern] is found in a chat line of the restricted
 * [channel] (a [com.skyziyou.features.chat.ChatChannel] name), [action] is sent
 * (leading slash = command, otherwise chat). [regex] switches [pattern] from
 * contains matching to full regex matching. [ignoreCase] controls whether either
 * kind of match is case-insensitive (default true). [delaySeconds] postpones the
 * action by 0..10 seconds after a match (0 = immediate).
 */
class ChatRuleEntry(
    var enabled: Boolean = true,
    var pattern: String = "",
    var action: String = "",
    var channel: String = "ANY",
    var regex: Boolean = false,
    var ignoreCase: Boolean = true,
    /** Delay between match and sending the action, 0..10 seconds; 0 sends immediately. */
    var delaySeconds: Int = 0,
)

/**
 * One chat sound rule: when [pattern] is found in a received chat line of the
 * restricted [channel] (a [com.skyziyou.features.chat.ChatChannel] name), a
 * built-in vanilla sound event identified by [sound] is played locally (2D UI
 * sound). [sound] is the sound's identifier path under "minecraft:", e.g.
 * "entity.cat.ambient"; see com.skyziyou.features.sound.SoundLibrary for the
 * curated pick list. [regex] / [ignoreCase] work exactly like [ChatRuleEntry].
 */
class SoundRuleEntry(
    var enabled: Boolean = true,
    var pattern: String = "",
    var sound: String = "entity.experience_orb.pickup",
    var channel: String = "ANY",
    var regex: Boolean = false,
    var ignoreCase: Boolean = true,
    /** How many times the sound plays on a match, 1..5 (spaced ~0.25s apart). */
    var repeat: Int = 1,
)

/**
 * One user-imported custom sound. The audio itself lives at
 * `.minecraft/config/skyziyou/sounds/<id>.<format>` (see
 * com.skyziyou.features.sound.CustomSoundStore); a rule references it with the
 * pseudo-identifier `"custom:<id>"` in [SoundRuleEntry.sound]. [name] is the
 * user-chosen display label; [format] is "ogg" or "mp3".
 */
class CustomSoundEntry(
    var id: String = "",
    var name: String = "",
    var file: String = "",
    var format: String = "ogg",
)

/**
 * One screen alert: when [trigger] text is found in a received chat line of the
 * restricted [channel] (a [com.skyziyou.features.chat.ChatChannel] name; ANY =
 * all channels), [text] is shown as a large centred banner near the top of the
 * screen for a few seconds. Matching is case-insensitive contains; [text]
 * supports § codes and the `[player]` / `[me]` placeholders.
 */
class AlertEntry(
    var enabled: Boolean = true,
    var trigger: String = "",
    var text: String = "",
    var channel: String = "ANY",
)

/**
 * One entity highlight: every living entity whose display name contains [name]
 * (case-insensitive) gets the vanilla glowing outline in [color], a ChatFormatting
 * colour name such as "RED" (only the 16 team colours exist).
 */
class HighlightEntry(
    var enabled: Boolean = true,
    var name: String = "",
    var color: String = "RED",
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
 * One switchable profile. Only the wheel slot contents and the custom shortcuts
 * belong to a profile; every other setting in [SkyziyouConfig] is global.
 */
class ProfileData(
    /** Eight wheel slot texts: up, up-right, right, down-right, down, down-left, left, up-left. */
    val wheelSlots: MutableList<String> = MutableList(SkyziyouConfig.WHEEL_SLOTS) { "" },
    /** Custom shortcuts belonging to this profile. */
    val shortcuts: MutableList<ShortcutEntry> = mutableListOf(),
)

/**
 * Simple JSON-backed config, stored in `.minecraft/config/skyziyou.json`.
 * Entries starting with '/' are executed as commands, anything else is sent as a chat message.
 */
object SkyziyouConfig {

    /** Number of wheel slots (fixed); see [ProfileData.wheelSlots]. */
    const val WHEEL_SLOTS = 8

    /** Profile that always exists and is used for configs from before profiles were introduced. */
    const val DEFAULT_PROFILE = "Default"

    /** Longest accepted profile name, in characters. */
    const val MAX_PROFILE_NAME = 24

    /** Default Screen Alerts banner centre (percent of the GUI size), restored by the Reset button. */
    const val DEFAULT_ALERT_X = 50
    const val DEFAULT_ALERT_Y = 16

    /** Settings UI language code ("EN" / "ZH"); see [I18n]. */
    var language: String = "EN"

    /** GLFW keycode of the wheel trigger; GLFW_KEY_UNKNOWN (-1) means unbound. Mouse buttons 0-7 are also allowed. */
    var keybind: Int = Keys.UNKNOWN

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

    /** All profiles, insertion-ordered; [DEFAULT_PROFILE] always exists. */
    val profiles: LinkedHashMap<String, ProfileData> = linkedMapOf(
        DEFAULT_PROFILE to ProfileData(
            mutableListOf("/ah", "", "", "", "/bz", "", "", ""),
        ),
    )

    /** Name of the profile currently in use; see [switchProfile]. */
    var activeProfile: String = DEFAULT_PROFILE

    /** Wheel slot contents of the active profile (Up, Up Right, Right, Down Right, Down, Down Left, Left, Up Left). */
    val slots: MutableList<String>
        get() = currentProfile().wheelSlots

    /** Custom shortcuts of the active profile; pressing the bound key runs the entry immediately. */
    val shortcuts: MutableList<ShortcutEntry>
        get() = currentProfile().shortcuts

    /** Custom Chat Rules master switch. */
    var chatRulesEnabled: Boolean = true

    /** Print a local chat line whenever a chat rule fires. */
    var chatRulesLog: Boolean = true

    /** Only evaluate rules while connected to Hypixel (checked via server brand). */
    var chatRulesHypixelOnly: Boolean = true

    /** User-defined chat match -> action rules; see [ChatRuleEntry]. */
    val chatRules: MutableList<ChatRuleEntry> = mutableListOf()

    /** Chat Sounds master switch. */
    var chatSoundsEnabled: Boolean = true

    /** Print a local `[ChatSounds] ...` line whenever a sound rule fires. */
    var chatSoundsLog: Boolean = true

    /** User-defined chat match -> vanilla sound rules; see [SoundRuleEntry]. */
    val chatSounds: MutableList<SoundRuleEntry> = mutableListOf()

    /** Special fixed Chat Sounds rule: play [deathSound] when the vanilla death screen opens. */
    var deathSoundEnabled: Boolean = false

    /** Sound path (built-in id or custom: ref) played on death; a harsh default. */
    var deathSound: String = "block.anvil.land"

    /** How many times the death sound plays, 1..5. */
    var deathSoundRepeat: Int = 1

    /** User-imported custom sound library referenced by chatSounds rules; see [CustomSoundEntry]. */
    val customSounds: MutableList<CustomSoundEntry> = mutableListOf()

    /** Screen Alerts master switch. */
    var screenAlertsEnabled: Boolean = true

    /** User-defined chat text -> on-screen banner alerts; see [AlertEntry]. */
    val screenAlerts: MutableList<AlertEntry> = mutableListOf()

    /** Banner font scale for Screen Alerts (clamped to 1.0..5.0 at use time). */
    var screenAlertFontScale: Float = 2.5f

    /** Banner centre X position in percent of the GUI width (0-100, 50 = centred). */
    var screenAlertXPercent: Int = DEFAULT_ALERT_X

    /** Banner centre Y position in percent of the GUI height (0-100, 16 = near the top). */
    var screenAlertYPercent: Int = DEFAULT_ALERT_Y

    /** Banner text colour (ARGB); § codes inside the banner text still override it. */
    var screenAlertTextColor: Int = 0xFFFFB400.toInt()

    /** Entity Highlights master switch. */
    var entityHighlightsEnabled: Boolean = true

    /** User-defined entity name -> glow colour highlights; see [HighlightEntry]. */
    val entityHighlights: MutableList<HighlightEntry> = mutableListOf()

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

    /** Mineshaft corpse party-chat report master switch. */
    var corpseReportEnabled: Boolean = true

    /** Only send the corpse report while connected to Hypixel (checked via server brand). */
    var corpseReportHypixelOnly: Boolean = true

    /**
     * True  -> "Frozen Corpses (4): 2x Lapis, 1x Umber, 1x Vanguard"
     * False -> "Corpses (4): 2l 1u 1vg"
     */
    var corpseReportDetailed: Boolean = true

    /** Highlight mineshaft corpse armour stands; see com.skyziyou.features.mineshaft.CorpseHighlights. */
    var corpseHighlightEnabled: Boolean = true

    /** Highlight distance in blocks (8-48); outlines are visible through walls inside this range. */
    var corpseHighlightRange: Int = 24

    private val file: Path
        get() = FabricLoader.getInstance().configDir.resolve("skyziyou.json")

    /** Config left behind by the mod's former name; renamed into [file] on first launch. */
    private val legacyFile: Path
        get() = FabricLoader.getInstance().configDir.resolve("commandwheel.json")

    /** Active profile, creating an empty one only if the config was corrupted. */
    private fun currentProfile(): ProfileData {
        profiles[activeProfile]?.let { return it }
        val created = ProfileData()
        profiles[activeProfile] = created
        return created
    }

    /**
     * Switches the active profile (wheel slots + custom shortcuts) and persists it.
     * Returns false for unknown names. Runtime state tied to the old profile is
     * dropped: a wheel opened with the old slots must not execute, and held keys
     * must not fire a shortcut from the new profile on the same press.
     */
    fun switchProfile(name: String): Boolean {
        if (!profiles.containsKey(name)) return false
        if (name != activeProfile) {
            activeProfile = name
            WheelState.cancel()
            Shortcuts.resetState()
            save()
        }
        return true
    }

    /**
     * Creates a profile and makes it active. [copyFrom] copies that profile's wheel
     * slots and shortcuts; pass null for an empty 8-slot wheel. Returns the accepted
     * name, or null when the name is blank, too long after trimming, or already taken.
     */
    fun createProfile(name: String, copyFrom: String? = activeProfile): String? {
        val clean = name.trim().take(MAX_PROFILE_NAME)
        if (clean.isBlank() || profiles.containsKey(clean)) return null
        val source = copyFrom?.let { profiles[it] }
        profiles[clean] = ProfileData(
            wheelSlots = source?.wheelSlots?.toMutableList() ?: MutableList(WHEEL_SLOTS) { "" },
            shortcuts = source?.shortcuts
                ?.map { ShortcutEntry(it.key, it.command, it.enabled) }
                ?.toMutableList()
                ?: mutableListOf(),
        )
        switchProfile(clean)
        return clean
    }

    /** Renames a profile in place (order is kept); false when the new name is unusable. */
    fun renameProfile(old: String, new: String): Boolean {
        val clean = new.trim().take(MAX_PROFILE_NAME)
        if (clean.isBlank() || clean == old || profiles.containsKey(clean)) return false
        val data = profiles[old] ?: return false
        val snapshot = profiles.entries.map { if (it.key == old) clean to data else it.key to it.value }
        profiles.clear()
        snapshot.forEach { (name, value) -> profiles[name] = value }
        if (activeProfile == old) activeProfile = clean
        save()
        return true
    }

    /** Deletes a profile; the last remaining one cannot be removed. */
    fun deleteProfile(name: String): Boolean {
        if (profiles.size <= 1 || !profiles.containsKey(name)) return false
        profiles.remove(name)
        if (activeProfile == name) {
            activeProfile = profiles.keys.first()
            WheelState.cancel()
            Shortcuts.resetState()
        }
        save()
        return true
    }

    /** Parses one profile object; missing or malformed fields fall back to empty slots/entries. */
    private fun parseProfile(obj: com.google.gson.JsonObject): ProfileData {
        val data = ProfileData()
        obj.getAsJsonArray("wheelSlots")?.let { array ->
            for (i in 0 until minOf(array.size(), WHEEL_SLOTS)) {
                data.wheelSlots[i] = array[i].takeIf { it.isJsonPrimitive }?.asString ?: ""
            }
        }
        obj.getAsJsonArray("shortcuts")?.let { array ->
            for (element in array) {
                val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val key = entry.get("key")?.takeIf { it.isJsonPrimitive }?.asInt ?: Keys.UNKNOWN
                val command = entry.get("command")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                val enabled = entry.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
                data.shortcuts.add(ShortcutEntry(key, command, enabled))
            }
        }
        return data
    }

    /**
     * Reads the config file if it exists. Missing or malformed fields keep their
     * defaults; IO errors are swallowed so the game can always start.
     */
    fun load() {
        migrateLegacyConfig()
        runCatching {
            // brand-new install: seed starter rules/aliases, persist them, then skip parsing
            if (!Files.exists(file)) {
                seedFirstRunDefaults()
                save()
                I18n.language = language
                return
            }
            val obj = JsonParser.parseString(Files.readString(file)).asJsonObject

            // only accepts existing primitive values; a missing or mistyped field keeps the default
            fun prim(name: String) = obj.get(name)?.takeIf { it.isJsonPrimitive }

            prim("language")?.let { if (it.asString == I18n.EN || it.asString == I18n.ZH) language = it.asString }
            prim("keybind")?.let { keybind = it.asInt }
            prim("enabled")?.let { enabled = it.asBoolean }
            prim("executeOnRelease")?.let { executeOnRelease = it.asBoolean }
            prim("showLogMessage")?.let { showLogMessage = it.asBoolean }
            prim("deadZone")?.let { deadZone = it.asInt }
            prim("sizePercent")?.let { sizePercent = it.asInt }
            prim("fontScalePercent")?.let { fontScalePercent = it.asInt }
            prim("timeoutSeconds")?.let { timeoutSeconds = it.asInt }
            // profiles (wheel slots + shortcuts). New format stores them under "profiles";
            // configs written before profiles existed keep both groups at the top level
            // and are migrated into the "Default" profile here.
            val profilesObj = obj.get("profiles")?.takeIf { it.isJsonObject }?.asJsonObject
            if (profilesObj != null) {
                profiles.clear()
                for ((name, element) in profilesObj.entrySet()) {
                    val data = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val clean = name.trim().take(MAX_PROFILE_NAME)
                    if (clean.isBlank()) continue
                    profiles[clean] = parseProfile(data)
                }
                if (profiles.isEmpty()) profiles[DEFAULT_PROFILE] = ProfileData()
                prim("activeProfile")?.asString?.let { saved ->
                    if (profiles.containsKey(saved)) activeProfile = saved
                }
                // a stored active name that no longer exists must not leave a dangling profile
                if (!profiles.containsKey(activeProfile)) activeProfile = profiles.keys.first()
            } else {
                val migrated = ProfileData()
                // at most the known slot count, so an oversized array cannot break the list
                obj.getAsJsonArray("slots")?.let { array ->
                    for (i in 0 until minOf(array.size(), WHEEL_SLOTS)) {
                        migrated.wheelSlots[i] = array[i].asString
                    }
                }
                obj.getAsJsonArray("shortcuts")?.let { array ->
                    for (element in array) {
                        val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                        val key = entry.get("key")?.takeIf { it.isJsonPrimitive }?.asInt ?: Keys.UNKNOWN
                        val command = entry.get("command")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                        val enabled = entry.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
                        migrated.shortcuts.add(ShortcutEntry(key, command, enabled))
                    }
                }
                profiles[DEFAULT_PROFILE] = migrated
                activeProfile = DEFAULT_PROFILE
            }
            // auto party invite feature
            prim("autoInviteEnabled")?.let { autoInviteEnabled = it.asBoolean }
            prim("autoInviteLog")?.let { autoInviteLog = it.asBoolean }
            prim("autoInviteHypixelOnly")?.let { autoInviteHypixelOnly = it.asBoolean }
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
                        ignoreCase = bool("ignoreCase", true),
                        delaySeconds = entry.get("delaySeconds")?.takeIf { it.isJsonPrimitive }?.asInt?.coerceIn(0, 10) ?: 0,
                    )
                    chatRules.add(rule)
                }
            }
            // chat sounds; malformed entries are skipped entirely
            prim("chatSoundsEnabled")?.let { chatSoundsEnabled = it.asBoolean }
            prim("chatSoundsLog")?.let { chatSoundsLog = it.asBoolean }
            prim("deathSoundEnabled")?.let { deathSoundEnabled = it.asBoolean }
            prim("deathSound")?.asString?.takeIf { it.isNotBlank() }?.let { deathSound = it }
            prim("deathSoundRepeat")?.asInt?.coerceIn(1, 5)?.let { deathSoundRepeat = it }
            obj.getAsJsonArray("chatSounds")?.let { array ->
                chatSounds.clear()
                for (element in array) {
                    val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    fun str(name: String) = entry.get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                    chatSounds.add(
                        SoundRuleEntry(
                            enabled = entry.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
                            pattern = str("pattern"),
                            sound = str("sound").ifBlank { "entity.experience_orb.pickup" },
                            channel = str("channel").ifBlank { "ANY" },
                            regex = entry.get("regex")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                            ignoreCase = entry.get("ignoreCase")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
                            repeat = entry.get("repeat")?.takeIf { it.isJsonPrimitive }?.asInt?.coerceIn(1, 5) ?: 1,
                        ),
                    )
                }
            }
            // imported custom sound library (files themselves live in config/skyziyou/sounds/)
            obj.getAsJsonArray("customSounds")?.let { array ->
                customSounds.clear()
                for (element in array) {
                    val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    fun str(name: String) = entry.get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                    val id = str("id")
                    val file = str("file")
                    // a library entry without its backing file is useless, skip it
                    if (id.isBlank() || file.isBlank()) continue
                    customSounds.add(
                        CustomSoundEntry(
                            id = id,
                            name = str("name").ifBlank { "sound" },
                            file = file,
                            format = str("format").ifBlank { "ogg" }.take(4),
                        ),
                    )
                }
            }
            // screen alerts; malformed entries are skipped entirely
            prim("screenAlertsEnabled")?.let { screenAlertsEnabled = it.asBoolean }
            obj.getAsJsonArray("screenAlerts")?.let { array ->
                screenAlerts.clear()
                for (element in array) {
                    val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    fun str(name: String) = entry.get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                    screenAlerts.add(
                        AlertEntry(
                            enabled = entry.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
                            trigger = str("trigger"),
                            text = str("text"),
                            channel = str("channel").ifBlank { "ANY" },
                        ),
                    )
                }
            }
            prim("screenAlertFontScale")?.let { screenAlertFontScale = it.asFloat }
            prim("screenAlertXPercent")?.let { screenAlertXPercent = it.asInt.coerceIn(0, 100) }
            prim("screenAlertYPercent")?.let { screenAlertYPercent = it.asInt.coerceIn(0, 100) }
            prim("screenAlertTextColor")?.let { screenAlertTextColor = it.asInt }
            // entity highlights; malformed entries are skipped entirely
            prim("entityHighlightsEnabled")?.let { entityHighlightsEnabled = it.asBoolean }
            obj.getAsJsonArray("entityHighlights")?.let { array ->
                entityHighlights.clear()
                for (element in array) {
                    val entry = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    fun str(name: String) = entry.get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                    val colorName = str("color").ifBlank { "RED" }
                    entityHighlights.add(
                        HighlightEntry(
                            enabled = entry.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
                            name = str("name"),
                            // drop unknown legacy colours instead of crashing the ChatFormatting lookup
                            color = colorName.takeIf { runCatching { ChatFormatting.valueOf(it) }.isSuccess } ?: "RED",
                        ),
                    )
                }
            }
            // mineshaft corpse report
            prim("corpseReportEnabled")?.let { corpseReportEnabled = it.asBoolean }
            prim("corpseReportHypixelOnly")?.let { corpseReportHypixelOnly = it.asBoolean }
            prim("corpseReportDetailed")?.let { corpseReportDetailed = it.asBoolean }
            prim("corpseHighlightEnabled")?.let { corpseHighlightEnabled = it.asBoolean }
            prim("corpseHighlightRange")?.let { corpseHighlightRange = it.asInt.coerceIn(8, 48) }
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
        // apply (or keep the default) even when the file is missing or unreadable
        I18n.language = language
    }

    /**
     * Starter content for brand-new installs (no skyziyou.json and no legacy file):
     * two guild-channel auto-invite rules and a /p leave alias, all enabled.
     * Existing users never reach this path, and rules a user deletes stay deleted
     * because [load] only seeds while the config file is absent.
     */
    private fun seedFirstRunDefaults() {
        chatRules.add(
            ChatRuleEntry(pattern = "@[me] !i", action = "/p [player]", channel = "GUILD", regex = true),
        )
        chatRules.add(
            ChatRuleEntry(pattern = "@[me] boop", action = "/boop [player]", channel = "GUILD", regex = true),
        )
        aliases.add(CommandAliasEntry(alias = "/pl", target = "/p leave"))
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
            obj.addProperty("language", language)
            obj.addProperty("keybind", keybind)
            obj.addProperty("enabled", enabled)
            obj.addProperty("executeOnRelease", executeOnRelease)
            obj.addProperty("showLogMessage", showLogMessage)
            obj.addProperty("deadZone", deadZone)
            obj.addProperty("sizePercent", sizePercent)
            obj.addProperty("fontScalePercent", fontScalePercent)
            obj.addProperty("timeoutSeconds", timeoutSeconds)
            // profiles: only wheel slots and shortcuts are profile-scoped; everything else is global
            obj.addProperty("activeProfile", activeProfile)
            val profilesObj = com.google.gson.JsonObject()
            profiles.forEach { (name, data) ->
                val dataObj = com.google.gson.JsonObject()
                val slotArray = com.google.gson.JsonArray()
                data.wheelSlots.forEach { slotArray.add(it) }
                dataObj.add("wheelSlots", slotArray)
                val shortcutArray = com.google.gson.JsonArray()
                data.shortcuts.forEach { shortcut ->
                    val entryObj = com.google.gson.JsonObject()
                    entryObj.addProperty("key", shortcut.key)
                    entryObj.addProperty("command", shortcut.command)
                    entryObj.addProperty("enabled", shortcut.enabled)
                    shortcutArray.add(entryObj)
                }
                dataObj.add("shortcuts", shortcutArray)
                profilesObj.add(name, dataObj)
            }
            obj.add("profiles", profilesObj)
            obj.addProperty("autoInviteEnabled", autoInviteEnabled)
            obj.addProperty("autoInviteLog", autoInviteLog)
            obj.addProperty("autoInviteHypixelOnly", autoInviteHypixelOnly)
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
                entryObj.addProperty("ignoreCase", rule.ignoreCase)
                entryObj.addProperty("delaySeconds", rule.delaySeconds)
                chatRuleArray.add(entryObj)
            }
            obj.add("chatRules", chatRuleArray)
            obj.addProperty("chatSoundsEnabled", chatSoundsEnabled)
            obj.addProperty("chatSoundsLog", chatSoundsLog)
            obj.addProperty("deathSoundEnabled", deathSoundEnabled)
            obj.addProperty("deathSound", deathSound)
            obj.addProperty("deathSoundRepeat", deathSoundRepeat)
            val chatSoundArray = com.google.gson.JsonArray()
            chatSounds.forEach { soundRule ->
                val entryObj = com.google.gson.JsonObject()
                entryObj.addProperty("enabled", soundRule.enabled)
                entryObj.addProperty("pattern", soundRule.pattern)
                entryObj.addProperty("sound", soundRule.sound)
                entryObj.addProperty("channel", soundRule.channel)
                entryObj.addProperty("regex", soundRule.regex)
                entryObj.addProperty("ignoreCase", soundRule.ignoreCase)
                entryObj.addProperty("repeat", soundRule.repeat)
                chatSoundArray.add(entryObj)
            }
            obj.add("chatSounds", chatSoundArray)
            val customSoundArray = com.google.gson.JsonArray()
            customSounds.forEach { custom ->
                val entryObj = com.google.gson.JsonObject()
                entryObj.addProperty("id", custom.id)
                entryObj.addProperty("name", custom.name)
                entryObj.addProperty("file", custom.file)
                entryObj.addProperty("format", custom.format)
                customSoundArray.add(entryObj)
            }
            obj.add("customSounds", customSoundArray)
            obj.addProperty("screenAlertsEnabled", screenAlertsEnabled)
            val alertArray = com.google.gson.JsonArray()
            screenAlerts.forEach { alert ->
                val entryObj = com.google.gson.JsonObject()
                entryObj.addProperty("enabled", alert.enabled)
                entryObj.addProperty("trigger", alert.trigger)
                entryObj.addProperty("text", alert.text)
                entryObj.addProperty("channel", alert.channel)
                alertArray.add(entryObj)
            }
            obj.add("screenAlerts", alertArray)
            obj.addProperty("screenAlertFontScale", screenAlertFontScale)
            obj.addProperty("screenAlertXPercent", screenAlertXPercent)
            obj.addProperty("screenAlertYPercent", screenAlertYPercent)
            obj.addProperty("screenAlertTextColor", screenAlertTextColor)
            obj.addProperty("entityHighlightsEnabled", entityHighlightsEnabled)
            val highlightArray = com.google.gson.JsonArray()
            entityHighlights.forEach { highlight ->
                val entryObj = com.google.gson.JsonObject()
                entryObj.addProperty("enabled", highlight.enabled)
                entryObj.addProperty("name", highlight.name)
                entryObj.addProperty("color", highlight.color)
                highlightArray.add(entryObj)
            }
            obj.add("entityHighlights", highlightArray)
            obj.addProperty("corpseReportEnabled", corpseReportEnabled)
            obj.addProperty("corpseReportHypixelOnly", corpseReportHypixelOnly)
            obj.addProperty("corpseReportDetailed", corpseReportDetailed)
            obj.addProperty("corpseHighlightEnabled", corpseHighlightEnabled)
            obj.addProperty("corpseHighlightRange", corpseHighlightRange)
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
