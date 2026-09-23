package dev.orchidsziyou.commandwheel

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object CommandWheelMod : ClientModInitializer {

    override fun onInitializeClient() {
        // config must be loaded before anything reads it
        WheelConfig.load()
        // HUD element that draws the wheel while it is open
        WheelRenderer.register()

        // drive the wheel state machine and the custom shortcuts once per client tick
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            WheelState.onTick()
            Shortcuts.onTick()
        }

        // /commandwheel and /cwh both open the settings screen
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("commandwheel").executes { ctx ->
                    openConfigScreen(ctx.source.client)
                    1
                },
            )
            dispatcher.register(
                ClientCommands.literal("cwh").executes { ctx ->
                    openConfigScreen(ctx.source.client)
                    1
                },
            )
        }
    }

    private fun openConfigScreen(mc: Minecraft) {
        // command handlers may not run on the render thread, so hop over via execute
        mc.execute { mc.setScreen(WheelConfigScreen()) }
    }
}