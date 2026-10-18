package daio7771.everytunnel.mixin;

import daio7771.everytunnel.platform.NoiseHooks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the noise system hear vanilla game events (the "vibrations" sculk sensors and
 * the Warden react to), e.g. a player opening a door or pulling a lever. Fabric API
 * has no event for these in 1.20.1. Nothing is changed or cancelled.
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    @Inject(method = "emitGameEvent", at = @At("HEAD"))
    private void everyTunnel$hearGameEvent(GameEvent event, Vec3d emitterPos, GameEvent.Emitter emitter, CallbackInfo ci) {
        NoiseHooks.onGameEvent((ServerWorld) (Object) this, event, emitterPos, emitter);
    }
}
