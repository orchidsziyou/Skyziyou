package dev.orchidsziyou.commandwheel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.orchidsziyou.commandwheel.WheelState;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bridges vanilla mouse handling to [WheelState]:
 * - feeds per-frame mouse deltas for the sector selection
 * - swallows clicks while the wheel is open
 * - locks the camera while the wheel is open
 */
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void commandwheel$onFrame(CallbackInfo ci) {
        // handleAccumulatedMovement runs once per frame and resets accumulatedDX/DY
        // at its end, so sampling here yields exactly this frame's mouse delta.
        WheelState.INSTANCE.onMouseMoved(this.accumulatedDX, this.accumulatedDY);
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void commandwheel$onButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (WheelState.INSTANCE.onMouseButton(input.button(), action)) {
            ci.cancel();
        }
    }

    @WrapOperation(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void commandwheel$turn(LocalPlayer player, double x, double y, Operation<Void> original) {
        // pass a zero delta instead of cancelling so vanilla keeps its own reset logic intact
        if (WheelState.INSTANCE.isCameraLocked()) {
            original.call(player, 0.0D, 0.0D);
        } else {
            original.call(player, x, y);
        }
    }
}
