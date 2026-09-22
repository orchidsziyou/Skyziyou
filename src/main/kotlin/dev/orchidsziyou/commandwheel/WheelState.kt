package dev.orchidsziyou.commandwheel

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Core state machine of the command wheel.
 *
 * Flow: hold the configured key to open the wheel, move the mouse into a direction to
 * select a sector, then either left click or release the key ("Execute On Release") to
 * run the entry. Right click closes without running anything. The camera is locked and
 * vanilla mouse clicks are swallowed while the wheel is open.
 */
object WheelState {

    /** 8 sectors of 45 degrees each; index 0 is Up, going clockwise. */
    const val SECTOR_COUNT = 8

    /** Wheel sizes in GUI pixels before the "Wheel Size %" scaling is applied. */
    const val BASE_WHEEL_RADIUS = 110
    const val BASE_LABEL_RADIUS = 76

    const val SECTOR_ANGLE = 360.0 / SECTOR_COUNT

    /** GLFW mouse button numbers for the click-to-run and cancel actions. */
    const val LEFT_MOUSE = 0
    const val RIGHT_MOUSE = 1

    val SECTOR_NAMES = listOf(
        "Up", "Up Right", "Right", "Down Right",
        "Down", "Down Left", "Left", "Up Left",
    )

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** The 8 slot entries, trimmed; empty slots become null so they cannot be executed. */
    val slotCommands: List<String?>
        get() = WheelConfig.slots.map { it.trim().takeIf { command -> command.isNotEmpty() } }

    /** Opening the wheel is pointless when it is disabled or every slot is empty. */
    private val canOpen: Boolean
        get() = WheelConfig.enabled && slotCommands.any { it != null }

    var isOpen = false
        private set

    var selectedSector: Int? = null
        private set

    private var openTime = 0L          // system time when the wheel was opened; used for the timeout
    private var movedX = 0.0           // accumulated mouse movement since the wheel was opened
    private var movedY = 0.0
    private var wasTriggerDown = false // trigger state last tick, for press edge detection
    private val mouseDown = HashMap<Int, Boolean>() // GLFW mouse button -> currently pressed

    /** While the wheel is open the camera must not move (see MixinMouseHandler#turnPlayer). */
    fun isCameraLocked(): Boolean = isOpen

    /**
     * Called every frame from MixinMouseHandler#handleAccumulatedMovement with the
     * current frame's mouse delta (vanilla resets its accumulator each frame, so
     * per-frame deltas must be accumulated here). Accumulating per frame instead of
     * per tick avoids both undercounting at high FPS and stale deltas when the
     * mouse stops moving.
     */
    fun onMouseMoved(dx: Double, dy: Double) {
        movedX += dx
        movedY += dy
    }

    /**
     * Called from MixinMouseHandler#onButton.
     * Returns true when the click belongs to the wheel and must be swallowed,
     * preventing the vanilla attack/use actions.
     */
    fun onMouseButton(button: Int, action: Int): Boolean {
        // always record the pressed state so isTriggerDown() can poll mouse-bound triggers
        mouseDown[button] = action == GLFW.GLFW_PRESS
        if (!isOpen) return false
        // releasing the trigger itself must reach vanilla, otherwise the key would stick
        if (button == boundTriggerMouseButton() && action == GLFW.GLFW_RELEASE) return false
        if (mc.screen != null) return false
        // swallow releases of buttons whose press was swallowed while the wheel was open
        if (action != GLFW.GLFW_PRESS) return true
        when (button) {
            LEFT_MOUSE -> if (selectedSector != null) close(execute = true) // run the selected entry
            RIGHT_MOUSE -> close(execute = false)                           // cancel
        }
        return true
    }

    /** Called once per client tick from the mod entrypoint. */
    fun onTick() {
        val down = isTriggerDown()
        if (isOpen) {
            // world left or another screen opened: always close without running anything
            if (mc.player == null || mc.screen != null) {
                close(execute = false)
                wasTriggerDown = down
                return
            }
            // trigger released: run the selection only in "Execute On Release" mode
            if (!down) {
                close(execute = WheelConfig.executeOnRelease && selectedSector != null)
                wasTriggerDown = false
                return
            }
            // held too long: cancel so a forgotten wheel can never misfire later
            if (System.currentTimeMillis() - openTime > WheelConfig.timeoutSeconds * 1000L) {
                close(execute = false)
                wasTriggerDown = down
                return
            }
            selectedSector = computeSelectedSector()
            return
        }
        // press edge detection: only the tick the trigger is first pressed opens the wheel
        if (down && !wasTriggerDown && canOpen && mc.screen == null && mc.player != null) {
            open()
        }
        wasTriggerDown = down
    }

    /** Opens the wheel and resets the selection and the accumulated mouse movement. */
    private fun open() {
        isOpen = true
        openTime = System.currentTimeMillis()
        movedX = 0.0
        movedY = 0.0
        selectedSector = null
    }

    /**
     * Closes the wheel and, when [execute] is set, runs the entry of the selected sector.
     * Does nothing if the selected slot is empty or the player is gone.
     */
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

    /** True while the trigger is held; mouse buttons come from [mouseDown], keyboard keys are polled directly. */
    private fun isTriggerDown(): Boolean {
        val key = WheelConfig.keybind
        if (key == GLFW.GLFW_KEY_UNKNOWN) return false  // unbound
        if (key in 0..7) return mouseDown[key] == true  // 0-7 is a mouse button
        return InputConstants.isKeyDown(mc.window, key) // otherwise a keyboard keycode
    }

    /** GLFW button number when the trigger key is bound to a mouse button, otherwise null. */
    private fun boundTriggerMouseButton(): Int? = WheelConfig.keybind.takeIf { it in 0..7 }

    /**
     * Maps the accumulated mouse movement to a sector index (0-7), or null while the
     * movement stays inside the dead zone.
     */
    private fun computeSelectedSector(): Int? {
        val distance = sqrt(movedX * movedX + movedY * movedY)
        if (distance < WheelConfig.deadZone) return null
        // Screen coordinates have y pointing down, so angle 0 is right and -90 is up.
        val angle = Math.toDegrees(atan2(movedY, movedX))
        val normalized = (angle + 90.0 + SECTOR_ANGLE / 2).mod(360.0)
        return (normalized / SECTOR_ANGLE).toInt().mod(SECTOR_COUNT)
    }
}