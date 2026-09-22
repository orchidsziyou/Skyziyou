package dev.orchidsziyou.commandwheel

import com.google.gson.JsonParser
import org.lwjgl.glfw.GLFW
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Simple JSON-backed config, stored in `.minecraft/config/commandwheel.json`.
 * Entries starting with '/' are executed as commands, anything else is sent as a chat message.
 */
object WheelConfig {

    /** GLFW keycode of the wheel trigger; GLFW_KEY_UNKNOWN (-1) means unbound. Mouse buttons 0-7 are also allowed. */
    var keybind: Int = GLFW.GLFW_KEY_UNKNOWN
    var enabled: Boolean = true
    var executeOnRelease: Boolean = false
    var showLogMessage: Boolean = false
    var deadZone: Int = 40
    var sizePercent: Int = 100

    /** Font size in percent; 50 is half the vanilla size, 200 is double. */
    var fontScalePercent: Int = 100
    var timeoutSeconds: Int = 10

    /** Up, Up Right, Right, Down Right, Down, Down Left, Left, Up Left */
    val slots: MutableList<String> = mutableListOf(
        "/ah", "", "", "",
        "/bz", "", "", "",
    )

    private val file: Path
        get() = FabricLoader.getInstance().configDir.resolve("commandwheel.json")

    fun load() {
        runCatching {
            if (!Files.exists(file)) return
            val obj = JsonParser.parseString(Files.readString(file)).asJsonObject

            fun prim(name: String) = obj.get(name)?.takeIf { it.isJsonPrimitive }

            prim("keybind")?.let { keybind = it.asInt }
            prim("enabled")?.let { enabled = it.asBoolean }
            prim("executeOnRelease")?.let { executeOnRelease = it.asBoolean }
            prim("showLogMessage")?.let { showLogMessage = it.asBoolean }
            prim("deadZone")?.let { deadZone = it.asInt }
            prim("sizePercent")?.let { sizePercent = it.asInt }
            prim("fontScalePercent")?.let { fontScalePercent = it.asInt }
            prim("timeoutSeconds")?.let { timeoutSeconds = it.asInt }
            obj.getAsJsonArray("slots")?.let { array ->
                for (i in 0 until minOf(array.size(), slots.size)) {
                    slots[i] = array[i].asString
                }
            }
        }.onFailure { it.printStackTrace() }
    }

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

            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("commandwheel.json.tmp")
            Files.writeString(tmp, com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { it.printStackTrace() }
    }
}
