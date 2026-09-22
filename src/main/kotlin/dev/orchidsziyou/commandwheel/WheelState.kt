package dev.orchidsziyou.commandwheel

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Core state machine of the command wheel (ported from the SkyHanni implementation).
 *
 * Flow: hold the configured key to open the wheel, move the mouse into a direction to
 * select a sector, then either left click or release the key ("Execute On Release") to
 * run the entry. Right click closes without running anything. The camera is locked and
 * vanilla mouse clicks are swallowed while the wheel is open.
 */
object WheelState {

    const val SECTOR_COUNT = 8
    const val BASE_WHEEL_RADIUS = 110
    const val BASE_LABEL_RADIUS = 76
    const val SECTOR_ANGLE = 360.0 / SECTOR_COUNT
    const val LEFT_MOUSE = 0
    const val RIGHT_MOUSE = 1

    val SECTOR_NAMES = listOf(
        "Up", "Up Right", "Right", "Down Right",
        "Down", "Down Left", "Left", "Up Left",
    )

    private val mc: Minecraft get() = Minecraft.getInstance()

    val slotCommands: List<String?>
        get() = WheelConfig.slots.map { it.trim().takeIf { command -> command.isNotEmpty() } }

    private val canOpen: Boolean
        get() = WheelConfig.enabled && slotCommands.any { it != null }

    var isOpen = false
        private set

    var selectedSector: Int? = null
        private set

    private var openTime = 0L
    private var movedX = 0.0
    private var movedY = 0.0
    private var deltaX = 0.0
    private var deltaY = 0.0
    private var wasTriggerDown = false
    private val mouseDown = HashMap<Int, Boolean>()

    /** While the wheel is open the camera must not move (see MixinMouseHandler#turnPlayer). */
    fun isCameraLocked(): Boolean = isOpen

    /** Called from MixinMouseHandler#onMove with the latest accumulated mouse delta. */
    fun onMouseMoved(dx: Double, dy: Double) {
        deltaX = dx
        deltaY = dy
    }

    /**
     * Called from MixinMouseHandler#onButton.
     * Returns true when the click belongs to the wheel and must be swallowed,
     * preventing the vanilla attack/use actions.
     */
    fun onMouseButton(button: Int, action: Int): Boolean {
        mouseDown[button] = action == GLFW.GLFW_PRESS
        if (!isOpen) return false
        if (button == boundTriggerMouseButton() && action == GLFW.GLFW_RELEASE) return false
        if (mc.screen != null) return false
        if (action != GLFW.GLFW_PRESS) return true
        when (button) {
            LEFT_MOUSE -> if (selectedSector != null) close(execute = true)
            RIGHT_MOUSE -> close(execute = false)
        }
        return true
    }

    /** Called once per client tick from the mod entrypoint. */
    fun onTick() {
        val down = isTriggerDown()
        if (isOpen) {
            if (mc.player == null || mc.screen != null) {
                close(execute = false)
                wasTriggerDown = down
                return
            }
            if (!down) {
                close(execute = WheelConfig.executeOnRelease && selectedSector != null)
                wasTriggerDown = false
                return
            }
            if (System.currentTimeMillis() - openTime > WheelConfig.timeoutSeconds * 1000L) {
                close(execute = false)
                wasTriggerDown = down
                return
            }
            // The mouse deltas are sampled once per tick: fast flicks are captured well,
            // very slow drags may need a lower dead zone to register.
            movedX += deltaX
            movedY += deltaY
            selectedSector = computeSelectedSector()
            return
        }
        if (down && !wasTriggerDown && canOpen && mc.screen == null && mc.player != null) {
            open()
        }
        wasTriggerDown = down
    }

    private fun open() {
        isOpen = true
        openTime = System.currentTimeMillis()
        movedX = 0.0
        movedY = 0.0
        selectedSector = null
    }

    private fun close(execute: Boolean) {
        isOpen = false
        val sector = selectedSector
        selectedSector = null
        if (!execute) return
        val command = sector?.let { slotCommands.getOrNull(it) } ?: return
        val player = mc.player ?: return
        // entries starting with '/' are executed as commands, anything else is sent as a chat message
        if (command.startsWith("/")) {
            player.connection.sendCommand(command.substring(1))
            if (WheelConfig.showLogMessage) {
                localChat("§7Command wheel ran: §e$command")
            }
        } else {
            player.connection.sendChat(command)
            if (WheelConfig.showLogMessage) {
                localChat("§7Command wheel sent: §e$command")
            }
        }
    }

    private fun localChat(message: String) {
        mc.player?.sendSystemMessage(Component.literal(message))
    }

    private fun isTriggerDown(): Boolean {
        val key = WheelConfig.keybind
        if (key == GLFW.GLFW_KEY_UNKNOWN) return false
        if (key in 0..7) return mouseDown[key] == true
        return InputConstants.isKeyDown(mc.window, key)
    }

    /** GLFW button number when the trigger key is bound to a mouse button, otherwise null. */
    private fun boundTriggerMouseButton(): Int? = WheelConfig.keybind.takeIf { it in 0..7 }

    private fun computeSelectedSector(): Int? {
        val distance = sqrt(movedX * movedX + movedY * movedY)
        if (distance < WheelConfig.deadZone) return null
        // Screen coordinates have y pointing down, so angle 0 is right and -90 is up.
        val angle = Math.toDegrees(atan2(movedY, movedX))
        val normalized = (angle + 90.0 + SECTOR_ANGLE / 2).mod(360.0)
        return (normalized / SECTOR_ANGLE).toInt().mod(SECTOR_COUNT)
    }
}