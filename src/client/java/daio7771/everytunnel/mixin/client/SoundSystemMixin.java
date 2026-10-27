package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.effects.MenuMusic;
import daio7771.everytunnel.client.screen.LoadingCover;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game is silent behind the loading screen and fades in with the picture
 * ({@link LoadingCover#worldVolume}). Every sound's volume goes through here when it starts,
 * and every tick for looping sounds, so the level's loops keep running, inaudible, and come
 * back on their own. Never quite zero: the game would skip a sound that starts at zero.
 *
 * A resource reload (leaving a world drops its resource pack) restarts the sound engine and
 * cuts every sound; the menu's music is told ({@link MenuMusic#soundsReloaded}).
 */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    @Inject(method = "getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F", at = @At("RETURN"), cancellable = true)
    private void everyTunnel$quietWhileLoading(float volume, SoundCategory category, CallbackInfoReturnable<Float> cir) {
        float scale = LoadingCover.worldVolume();
        if (scale < 1f) {
            cir.setReturnValue(cir.getReturnValueF() * scale);
        }
    }

    @Inject(method = "reloadSounds", at = @At("TAIL"))
    private void everyTunnel$soundsReloaded(CallbackInfo ci) {
        MenuMusic.soundsReloaded();
    }
}
