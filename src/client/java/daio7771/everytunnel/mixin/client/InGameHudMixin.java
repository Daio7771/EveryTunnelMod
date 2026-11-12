package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.cutscene.CutsceneView;
import daio7771.everytunnel.client.hud.StatusHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla status bars while Every Tunnel's own bars ({@link StatusHud})
 * replace them.
 *
 * {@code renderStatusBars} draws armor, health, hunger and air; {@code renderExperienceBar}
 * draws the experience bar and level. The hotbar, crosshair and everything else stay.
 *
 * While a cinematic plays ({@link CutsceneView}) nothing of the HUD is drawn, the mod's own parts neither.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$hideInCutscene(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (CutsceneView.active()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$hideStatusBars(DrawContext context, CallbackInfo ci) {
        if (StatusHud.replacesVanillaBars()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$hideExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        if (StatusHud.replacesVanillaBars()) {
            ci.cancel();
        }
    }
}
