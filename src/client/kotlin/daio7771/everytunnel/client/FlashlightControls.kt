package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.FlashlightHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.lwjgl.glfw.GLFW

/**
 * The Flashlight key (default F, changeable in Options -> Controls -> Every Tunnel).
 *
 * Pressing it asks the server to switch the flashlight on or off
 * ([FlashlightHooks.TOGGLE_PACKET_ID]); the server checks the inventory and battery
 * and syncs the result back through [ClientResources].
 */
object FlashlightControls {

    /** Mod id of LambDynamicLights, which draws the flashlight's light. */
    const val DYNAMIC_LIGHTS_MOD_ID: String = "lambdynlights"

    private lateinit var toggleKey: KeyBinding
    private var wasOn = false
    private var toldAboutDynamicLights = false

    fun register() {
        toggleKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.every_tunnel.flashlight", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.every_tunnel"),
        )
        ClientLifecycleEvents.CLIENT_STARTED.register(::resolveSwapHandsConflict)
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> wasOn = false }
    }

    /** The key the flashlight is bound to, as the game names it (e.g. "F"). */
    fun keyName(): Text = toggleKey.boundKeyLocalizedText

    private fun tick(client: MinecraftClient) {
        while (toggleKey.wasPressed()) {
            if (client.player != null && ClientPlayNetworking.canSend(FlashlightHooks.TOGGLE_PACKET_ID)) {
                ClientPlayNetworking.send(FlashlightHooks.TOGGLE_PACKET_ID, PacketByteBufs.empty())
            }
        }

        // Without LambDynamicLights the flashlight switches on but gives no light: say so once per game.
        val on = ClientResources.current.flashlightOn
        if (on && !wasOn && !toldAboutDynamicLights && !FabricLoader.getInstance().isModLoaded(DYNAMIC_LIGHTS_MOD_ID)) {
            client.player?.sendMessage(
                Text.translatable("message.every_tunnel.dynamic_lights_missing").formatted(Formatting.YELLOW),
                false,
            )
            EveryTunnel.LOGGER.warn("LambDynamicLights is not installed: the flashlight gives no light")
            toldAboutDynamicLights = true
        }
        wasOn = on
    }

    /**
     * F is also vanilla's "Swap Item With Offhand" key, and Minecraft only fires one
     * binding per key. While both are on the same key, the vanilla one is unbound (and
     * the options file saved) so the flashlight key always works. Rebinding either key
     * in Controls ends this; it only happens again if both end up on one key.
     */
    private fun resolveSwapHandsConflict(client: MinecraftClient) {
        val swapHands = client.options.swapHandsKey
        val flashlightKey = KeyBindingHelper.getBoundKeyOf(toggleKey)
        if (flashlightKey == InputUtil.UNKNOWN_KEY || KeyBindingHelper.getBoundKeyOf(swapHands) != flashlightKey) return
        swapHands.setBoundKey(InputUtil.UNKNOWN_KEY)
        KeyBinding.updateKeysByCode()
        client.options.write()
        EveryTunnel.LOGGER.info(
            "\"Swap Item With Offhand\" was on the same key as the flashlight ({}) and was unbound; rebind it in Controls if needed",
            flashlightKey.translationKey,
        )
    }
}
