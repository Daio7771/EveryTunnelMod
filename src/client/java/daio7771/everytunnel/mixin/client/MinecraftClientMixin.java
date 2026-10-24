package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.screen.MainMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wherever the game opens vanilla's title screen (start-up, leaving a world, the end of the
 * demo), Every Tunnel's main menu opens instead ({@link MainMenuScreen#replace}).
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen everyTunnel$mainMenu(Screen screen) {
        return MainMenuScreen.replace(screen);
    }
}
