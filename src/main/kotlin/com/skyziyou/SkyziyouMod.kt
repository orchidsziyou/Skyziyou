package com.skyziyou

import com.skyziyou.features.ActionQueue
import com.skyziyou.features.chat.ChatClassifier
import com.skyziyou.features.chat.ChatRulesFeature
import com.skyziyou.features.command.CommandAliases
import com.skyziyou.features.mineshaft.MineshaftCorpseReporter
import com.skyziyou.features.party.AutoInviteFeature
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object SkyziyouMod : ClientModInitializer {

    override fun onInitializeClient() {
        // config must be loaded before anything reads it
        SkyziyouConfig.load()
        // HUD element that draws the wheel while it is open
        WheelRenderer.register()

        // drive the wheel state machine and the custom shortcuts once per client tick
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            WheelState.onTick()
            Shortcuts.onTick()
        }

        // shared chat parsing + outgoing action throttle, then features
        ChatClassifier.register()
        ActionQueue.register()
        ChatRulesFeature.register()
        AutoInviteFeature.register()
        CommandAliases.register()
        MineshaftCorpseReporter.register()

        // /skyziyou and /sz both open the settings category screen
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("skyziyou").executes { ctx ->
                    openConfigScreen(ctx.source.client)
                    1
                },
            )
            dispatcher.register(
                ClientCommands.literal("sz").executes { ctx ->
                    openConfigScreen(ctx.source.client)
                    1
                },
            )
        }
    }

    private fun openConfigScreen(mc: Minecraft) {
        // command handlers may not run on render thread, so hop over via execute
        //~ if < 26.2 'mc.gui.setScreen' -> 'mc.setScreen'
        mc.execute { mc.gui.setScreen(ConfigScreen()) }
    }
}
