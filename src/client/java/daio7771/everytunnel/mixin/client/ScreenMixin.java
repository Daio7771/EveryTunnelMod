package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.screen.LoadingCover;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a world with levels opens, vanilla's loading screens are drawn over by the
 * Every Tunnel loading screen ({@link LoadingCover#drawOver}).
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void everyTunnel$coverLoading(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        LoadingCover.drawOver((Screen) (Object) this, context);
    }
}
