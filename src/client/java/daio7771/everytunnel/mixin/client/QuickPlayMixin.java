package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.screen.LoadingCover;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.QuickPlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A world opened by quick play (the dev run tasks, or a launcher's "join world"): the same
 * check as {@link WorldListEntryMixin}.
 */
@Mixin(QuickPlay.class)
public abstract class QuickPlayMixin {

    @Inject(method = "startSingleplayer", at = @At("HEAD"))
    private static void everyTunnel$worldOpening(MinecraftClient client, String levelName, CallbackInfo ci) {
        LoadingCover.worldOpening(levelName);
    }
}
