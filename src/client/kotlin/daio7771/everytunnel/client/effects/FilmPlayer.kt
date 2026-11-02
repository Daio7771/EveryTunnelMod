package daio7771.everytunnel.client.effects

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.film.FilmInfo
import daio7771.everytunnel.platform.LoungePuzzleHooks
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier

/**
 * The film of Level 4's Cinema, shown on its screen when the server says so
 * ([LoungePuzzleHooks.FILM_PACKET_ID]): frames from `assets/every_tunnel/film/` (`tools/film/film.py`),
 * one texture rewritten as they change, drawn full bright just in front of the screen. It looks at the
 * players from the north side of a screen facing north, so its left is at the larger x. The projector
 * sounds from its place in a loop (`block.projector.run`) until the film ends. It follows the world's
 * time, so pausing the game pauses it.
 */
object FilmPlayer {

    private val TEXTURE = Identifier(EveryTunnel.MOD_ID, "dynamic/film")
    private const val WIDTH = 288
    private const val HEIGHT = 128

    /** How far in front of the block's edge the picture lies: the screen's plate is a pixel thick. */
    private const val PLANE = 15f / 16f - 0.005f

    private class Showing(
        val info: FilmInfo, val startedAt: Long, val x0: Float, val x1: Float, val y0: Float, val y1: Float, val z: Float,
        val sound: SoundInstance, var frame: Int = 0,
    )

    private var showing: Showing? = null
    private var texture: NativeImageBackedTexture? = null

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(LoungePuzzleHooks.FILM_PACKET_ID) { client, _, buf, _ ->
            val x0 = buf.readDouble()
            val x1 = buf.readDouble()
            val y0 = buf.readDouble()
            val y1 = buf.readDouble()
            val z = buf.readDouble()
            val px = buf.readDouble()
            val py = buf.readDouble()
            val pz = buf.readDouble()
            client.execute { start(client, x0, x1, y0, y1, z, px, py, pz) }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { stop(client) } }
        WorldRenderEvents.AFTER_ENTITIES.register(::render)
    }

    private fun start(client: MinecraftClient, x0: Double, x1: Double, y0: Double, y1: Double, z: Double, px: Double, py: Double, pz: Double) {
        stop(client)
        val world = client.world ?: return
        val info = client.resourceManager.getResource(Identifier(EveryTunnel.MOD_ID, "film/film.json")).orElse(null)
            ?.inputStream?.use { FilmInfo.parse(it.reader().readText()) }
        if (info == null) {
            EveryTunnel.LOGGER.warn("The film cannot be read")
            return
        }
        if (texture == null) {
            texture = NativeImageBackedTexture(WIDTH, HEIGHT, false).also { client.textureManager.registerTexture(TEXTURE, it) }
        }
        val sound = PositionedSoundInstance(
            ModSounds.PROJECTOR_RUN.id, SoundCategory.BLOCKS, 1f, 1f, SoundInstance.createRandom(),
            true, 0, SoundInstance.AttenuationType.LINEAR, px, py + 0.5, pz, false,
        )
        client.soundManager.play(sound)
        showing = Showing(info, world.time, x0.toFloat(), x1.toFloat(), y0.toFloat(), y1.toFloat(), z.toFloat(), sound)
    }

    private fun stop(client: MinecraftClient) {
        showing?.let { client.soundManager.stop(it.sound) }
        showing = null
    }

    private fun render(context: WorldRenderContext) {
        val film = showing ?: return
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val frame = film.info.frameAt(world.time - film.startedAt + context.tickDelta())
        if (frame == null) {
            stop(client)
            return
        }
        if (frame != film.frame) {
            film.frame = frame
            load(client, frame)
        }
        val consumers = context.consumers() ?: return
        val matrices = context.matrixStack()
        val camera = context.camera().pos
        matrices.push()
        matrices.translate(-camera.x, -camera.y, -camera.z)
        val entry = matrices.peek()
        val matrix = entry.positionMatrix
        val normal = entry.normalMatrix
        val vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE))
        val z = film.z + PLANE
        fun corner(x: Float, y: Float, u: Float, v: Float) {
            vertices.vertex(matrix, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(normal, 0f, 0f, -1f).next()
        }
        // Seen from the north, the larger x is on the left.
        corner(film.x1, film.y1, 0f, 0f)
        corner(film.x1, film.y0, 0f, 1f)
        corner(film.x0, film.y0, 1f, 1f)
        corner(film.x0, film.y1, 1f, 0f)
        matrices.pop()
    }

    private fun load(client: MinecraftClient, frame: Int) {
        val id = Identifier(EveryTunnel.MOD_ID, "film/frame_%04d.png".format(frame))
        val resource = client.resourceManager.getResource(id).orElse(null) ?: return
        val image = resource.inputStream.use { NativeImage.read(NativeImage.Format.RGBA, it) }
        texture?.let {
            it.image = image
            it.upload()
        }
    }
}
