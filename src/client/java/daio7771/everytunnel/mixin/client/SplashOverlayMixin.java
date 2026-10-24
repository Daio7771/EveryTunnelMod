package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.screen.LoadingCover;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opening a world reloads the resources for its resource pack ({@code resources.zip}),
 * under the Mojang splash. In a world with levels the Every Tunnel loading screen is drawn
 * over it ({@link LoadingCover#drawOverSplash}); the splash still runs, so the reload ends
 * as usual.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void everyTunnel$coverSplash(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        LoadingCover.drawOverSplash(context);
    }
}
