package com.skyziyou.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the protected shared-flag setter so client-side features can force the
 * glowing flag (bit 6). Entity#setGlowingTag is unusable on the physical client:
 * it recomputes the flag from isCurrentlyGlowing(), which on the client side reads
 * the flag itself — i.e. setGlowingTag(true) is a no-op while the flag is false.
 */
@Mixin(Entity.class)
public interface EntityAccessor {

    @Invoker("setSharedFlag")
    void skyziyou$setSharedFlag(int index, boolean value);
}
