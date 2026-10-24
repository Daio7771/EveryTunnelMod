package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.screen.LoadingCover;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A world picked in the Singleplayer list: before vanilla shows "Reading world data", the
 * loading screen checks whether the world has levels ({@link LoadingCover#worldOpening}).
 */
@Mixin(WorldListWidget.WorldEntry.class)
public abstract class WorldListEntryMixin {

    @Shadow
    @Final
    private LevelSummary level;

    @Inject(method = "openReadingWorldScreen", at = @At("HEAD"))
    private void everyTunnel$worldOpening(CallbackInfo ci) {
        LoadingCover.worldOpening(this.level.getName());
    }
}
