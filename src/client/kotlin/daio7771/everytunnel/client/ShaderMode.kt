package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import java.lang.reflect.Method

/**
 * Whether the game is drawn with a shader pack (Iris installed and a pack in use) or not.
 *
 * The flashlight looks different in each: with shaders its block light is smoothed by the
 * pack; without, it is drawn as steps of light, so it is updated less often and helped by an
 * on-screen spot (`client.hud.FlashlightOverlay`). Asked live, so turning shaders on or off
 * while playing switches at once. Iris is optional: it is reached by reflection, through its
 * public API (`net.irisshaders.iris.api.v0.IrisApi`).
 */
object ShaderMode {

    private val iris: Pair<Any, Method>? by lazy {
        try {
            val api = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            val instance = api.getMethod("getInstance").invoke(null)
            instance to api.getMethod("isShaderPackInUse")
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            EveryTunnel.LOGGER.warn("Could not reach Iris's API: treating the game as without shaders", e)
            null
        }
    }

    /** True while a shader pack draws the game. */
    val shadersOn: Boolean
        get() {
            val (instance, inUse) = iris ?: return false
            return try {
                inUse.invoke(instance) as Boolean
            } catch (e: Exception) {
                false
            }
        }
}
