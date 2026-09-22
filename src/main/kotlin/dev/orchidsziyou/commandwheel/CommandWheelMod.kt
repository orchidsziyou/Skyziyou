package dev.orchidsziyou.commandwheel

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object CommandWheelMod : ClientModInitializer {

    override fun onInitializeClient() {
        WheelConfig.load()
        WheelRenderer.register()

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            WheelState.onTick()
        }

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
        mc.execute { mc.setScreen(WheelConfigScreen()) }
    }
}