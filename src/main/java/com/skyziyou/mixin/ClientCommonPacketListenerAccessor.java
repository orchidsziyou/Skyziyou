package com.skyziyou.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected server brand string (set from the vanilla brand packet,
 * e.g. "Hypixel BungeeCord") so features can tell which server network they are on.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerAccessor {

    @Accessor("serverBrand")
    String getServerBrand();
}
