package daio7771.everytunnel.mixin.client;

import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No vanilla music anywhere: not in the menus, not in any world. Every Tunnel's music
 * (the menu's, the intro's, the Pale man's) plays as ordinary sounds, not through here.
 */
@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$noVanillaMusic(CallbackInfo ci) {
        ci.cancel();
    }
}
