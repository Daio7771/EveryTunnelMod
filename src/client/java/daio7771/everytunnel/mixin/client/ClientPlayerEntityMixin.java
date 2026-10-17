package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.ClientResources;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops sprinting while the player is exhausted (out of stamina).
 *
 * Sprinting is decided on the client, so the server cannot refuse it reliably.
 * {@code canSprint()} is checked both when starting a sprint and every tick while
 * sprinting (vanilla uses it for the "food level above 6" rule), so returning
 * false also ends a sprint in progress.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "canSprint", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$blockSprintWhenExhausted(CallbackInfoReturnable<Boolean> cir) {
        if (ClientResources.isSprintBlocked()) {
            cir.setReturnValue(false);
        }
    }
}
