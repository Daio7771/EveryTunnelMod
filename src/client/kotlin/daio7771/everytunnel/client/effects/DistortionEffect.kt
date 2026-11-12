package daio7771.everytunnel.client.effects

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ClientResources
import daio7771.everytunnel.client.ShaderMode
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.Gaze
import daio7771.everytunnel.platform.DistortionCloudEntity
import daio7771.everytunnel.platform.DistortionEntity
import daio7771.everytunnel.platform.DistortionJuniorEntity
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.BufferRenderer
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RotationAxis
import net.minecraft.world.RaycastContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * What the Distortions do to the player's senses (Level 5):
 *
 * - **Whispers:** each Distortion within [HEAR_RANGE] whispers, a loop that follows it
 *   (`entity.distortion_cloud.whisper`, `entity.distortion_junior.whisper`); a Cloud's grows louder as
 *   it is looked at, a Junior's eye hums higher and higher as it is stared at
 *   (`entity.distortion_junior.stare`).
 * - **The entry corridor's game** ([DistortionCloudEntity.inGame]): its Cloud's light (`LambDynamicLightsCompat`) is
 *   violet. While it is within [VIOLET_RANGE], the picture is multiplied by violet, eased in and out: what its light
 *   shows turns violet, and the dark stays dark. Each time it jumps ([DistortionCloudEntity.jumps]), violet flashes
 *   cover the screen with the turn round sign in the middle (`textures/gui/turn_icon.png`), for [CUE_TICKS]. While it
 *   is about, a static is heard (`ambient.cloud_game.static`), not from anywhere, its pitch rising as the player
 *   gets further along ([DistortionCloudEntity.progress]).
 * - **Looking at a Cloud:** the screen distorts as the look goes on, as far as the nearest Cloud's
 *   [DistortionEntity.stare] has got: dark purple closing in from the edges, black and purple bars torn
 *   across it, more and more until it kills.
 * - **A Junior close by** (within `junior.range` of its eye, nothing solid in between): the camera
 *   trembles ([applyTremor], from `GameRendererMixin`, only the picture) and drifts on its own, wandering
 *   and pushed away from its eye when the crosshair nears it, so staring it down takes some doing. The
 *   drift turns the player for real (yaw and pitch), so the server, which judges the stare, sees it too.
 *
 * Only while the survival resources apply (not in Creative or Spectator).
 */
object DistortionEffect {

    private val VIGNETTE = Identifier(EveryTunnel.MOD_ID, "textures/gui/vignette.png")
    private val TURN_ICON = Identifier(EveryTunnel.MOD_ID, "textures/gui/turn_icon.png")
    private const val HEAR_RANGE = 32.0
    private const val CLOUD_RANGE = 48.0

    private val loops = HashMap<Int, List<MovingSoundInstance>>()
    /** The nearest Cloud's stare, eased; and how troubled by a Junior the player is (0 to 1), eased. */
    private var cloudStare = 0f
    private var trouble = 0f
    /** How violet the picture is (0 to 1), eased. */
    private var violet = 0f
    /** How much of the entry corridor's dark lift is on (0 to 1), eased: 1 while the game's Cloud is about. */
    private var darkLift = 0f
    /** The game's Cloud's jumps as last seen, by entity id; ticks left of the cue of the last one. */
    private val jumpsSeen = HashMap<Int, Int>()
    private var cue = 0
    /** The game's static, and the Cloud it is for. */
    private var static: Follow? = null
    private var troublingEye: Position? = null
    /** The drift's speed, degrees per tick. */
    private var driftYaw = 0f
    private var driftPitch = 0f
    private val rng = Random(7)

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        HudRenderCallback.EVENT.register { context, _ -> renderOverlay(context) }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> stop() }
    }

    /**
     * How far the Brightness is lifted from Moody (0 to 1) for `LightmapTextureManagerMixin`: only without shaders and
     * while the entry corridor's game runs, `game_dark_lift` of the config. A shader pack gives the dark a minimum
     * that vanilla's lightmap does not, and the corridor's only light is the Cloud's.
     */
    fun brightnessLift(): Double =
        if (ShaderMode.shadersOn) 0.0 else darkLift * ConfigManager.gameplay.cloud.gameDarkLift

    private fun stop() {
        loops.clear()
        cloudStare = 0f
        trouble = 0f
        violet = 0f
        darkLift = 0f
        jumpsSeen.clear()
        cue = 0
        static = null
        troublingEye = null
        driftYaw = 0f
        driftPitch = 0f
    }

    private fun tick(client: MinecraftClient) {
        val player = client.player
        val world = client.world
        if (player == null || world == null) return stop()
        if (client.isPaused) return
        val active = ClientResources.current.active && !player.isSpectator && !player.isCreative

        var stare = 0f
        var lit = false
        var gameRunning = false
        var game: DistortionCloudEntity? = null
        var junior: DistortionJuniorEntity? = null
        var juniorDistance = Double.MAX_VALUE
        val range = ConfigManager.gameplay.junior.range
        loops.keys.retainAll { id -> world.getEntityById(id)?.isRemoved == false }
        jumpsSeen.keys.retainAll { id -> world.getEntityById(id)?.isRemoved == false }
        if (cue > 0) cue--
        for (entity in world.entities) {
            if (entity !is DistortionEntity || entity.isRemoved) continue
            val distance = sqrt(entity.squaredDistanceTo(player))
            if (distance <= HEAR_RANGE && entity.id !in loops) startLoops(client, entity)
            if (entity.ending || !active) continue
            if (entity is DistortionCloudEntity && distance <= CLOUD_RANGE) stare = maxOf(stare, entity.stare)
            if (entity is DistortionCloudEntity && entity.inGame) {
                gameRunning = true
                if (distance <= VIOLET_RANGE) lit = true
                if (distance <= HEAR_RANGE) game = entity
                val seen = jumpsSeen.put(entity.id, entity.jumps)
                if (seen != null && entity.jumps > seen) cue = CUE_TICKS
            }
            if (entity is DistortionJuniorEntity) {
                val eye = entity.eyeCentre()
                val d = player.eyePos.distanceTo(eye)
                if (d <= range && d < juniorDistance && world.raycast(
                        RaycastContext(player.eyePos, eye, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player),
                    ).type == HitResult.Type.MISS
                ) {
                    junior = entity
                    juniorDistance = d
                }
            }
        }
        cloudStare += (stare - cloudStare) * if (stare > cloudStare) 0.6f else 0.2f
        violet += ((if (lit) 1f else 0f) - violet) * VIOLET_EASING
        darkLift += ((if (gameRunning) 1f else 0f) - darkLift) * DARK_LIFT_EASING
        if (static?.isDone != false && game != null) {
            static = Follow(game, ModSounds.CLOUD_GAME_STATIC, { e -> if (e.ending) 0f else STATIC_VOLUME },
                { e -> STATIC_PITCH + STATIC_RISE * (e as DistortionCloudEntity).progress }, ambient = true).also(client.soundManager::play)
        }
        trouble += ((if (junior != null) 1f else 0f) - trouble) * 0.15f
        troublingEye = junior?.eyeCentre()?.let { Position(it.x, it.y, it.z) } ?: troublingEye
        if (trouble > 0.01f) drift(player)
    }

    /** The camera wanders on its own, and away from the Junior's eye when the crosshair nears it. */
    private fun drift(player: net.minecraft.client.network.ClientPlayerEntity) {
        val config = ConfigManager.gameplay.junior
        val top = (config.driftDegreesPerSecond / 20).toFloat()
        driftYaw = (driftYaw + (rng.nextFloat() - 0.5f) * top * 0.5f).coerceIn(-top, top)
        driftPitch = (driftPitch + (rng.nextFloat() - 0.5f) * top * 0.4f).coerceIn(-top * 0.6f, top * 0.6f)
        troublingEye?.let { eye ->
            val eyes = player.eyePos
            val yawTo = Gaze.yawTowards(Position(eyes.x, eyes.y, eyes.z), eye).toFloat()
            val horizontal = sqrt((eye.x - eyes.x) * (eye.x - eyes.x) + (eye.z - eyes.z) * (eye.z - eyes.z))
            val pitchTo = (-Math.toDegrees(atan2(eye.y - eyes.y, horizontal))).toFloat()
            val offYaw = MathHelper.wrapDegrees(player.yaw - yawTo)
            val offPitch = player.pitch - pitchTo
            if (kotlin.math.abs(offYaw) < PUSH_DEGREES && kotlin.math.abs(offPitch) < PUSH_DEGREES) {
                driftYaw += (if (offYaw == 0f) 1f else sign(offYaw)) * top * PUSH
                driftPitch += (if (offPitch == 0f) 1f else sign(offPitch)) * top * PUSH * 0.5f
            }
        }
        player.yaw += driftYaw * trouble
        player.pitch = (player.pitch + driftPitch * trouble).coerceIn(-90f, 90f)
    }

    /** The picture trembles while a Junior troubles the player (where they look does not change). */
    @JvmStatic
    fun applyTremor(matrices: MatrixStack, tickDelta: Float) {
        if (trouble <= 0.01f) return
        val world = MinecraftClient.getInstance().world ?: return
        val t = world.time + tickDelta
        val a = ConfigManager.gameplay.junior.tremorDegrees.toFloat() * trouble
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(a * (0.6f * sin(t * 2.7f) + 0.4f * sin(t * 6.1f + 1f))))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(a * (0.5f * sin(t * 3.3f + 2f) + 0.5f * sin(t * 7.3f))))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a * 0.5f * sin(t * 4.9f + 0.5f)))
    }

    private fun renderOverlay(context: DrawContext) {
        if (violet > 0.01f) tint(context)
        if (cue > 0) turnRound(context)
        val s = cloudStare
        if (s <= 0.01f) return
        val w = context.scaledWindowWidth
        val h = context.scaledWindowHeight
        RenderSystem.enableBlend()
        context.setShaderColor(0.22f, 0.02f, 0.3f, (s * 1.2f).coerceAtMost(1f))
        context.drawTexture(VIGNETTE, 0, 0, 0f, 0f, w, h, w, h)
        context.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
        // Bars torn across, new ones every tick.
        val world = MinecraftClient.getInstance().world ?: return
        val bars = Random(world.time)
        repeat((s * MAX_BARS).toInt()) {
            val y = bars.nextInt(h)
            val height = 1 + bars.nextInt(2 + (s * 6).toInt())
            val x = bars.nextInt(-w / 2, w / 2)
            val width = w / 3 + bars.nextInt(w)
            val alpha = ((0.25f + 0.5f * s) * 255).toInt().coerceIn(0, 255)
            val colour = if (bars.nextBoolean()) 0x000000 else if (bars.nextInt(3) == 0) 0x3A0A52 else 0x8A3BD8
            context.fill(x, y, x + width, y + height, (alpha shl 24) or colour)
        }
    }

    /** The whole picture multiplied by violet, as far as [violet] has got. */
    private fun tint(context: DrawContext) {
        context.draw()
        val w = context.scaledWindowWidth.toFloat()
        val h = context.scaledWindowHeight.toFloat()
        val r = 1f + (VIOLET_R - 1f) * violet
        val g = 1f + (VIOLET_G - 1f) * violet
        val b = 1f + (VIOLET_B - 1f) * violet
        val matrix = context.matrices.peek().positionMatrix
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SrcFactor.DST_COLOR, GlStateManager.DstFactor.ZERO)
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        val buffer = Tessellator.getInstance().buffer
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR)
        buffer.vertex(matrix, 0f, 0f, 0f).color(r, g, b, 1f).next()
        buffer.vertex(matrix, 0f, h, 0f).color(r, g, b, 1f).next()
        buffer.vertex(matrix, w, h, 0f).color(r, g, b, 1f).next()
        buffer.vertex(matrix, w, 0f, 0f).color(r, g, b, 1f).next()
        BufferRenderer.drawWithGlobalProgram(buffer.end())
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    /** The cue of a jump: violet flashing over the whole screen, three times, fading; the turn round sign over it. */
    private fun turnRound(context: DrawContext) {
        val t = (CUE_TICKS - cue + MinecraftClient.getInstance().tickDelta) / CUE_TICKS
        val fade = (1f - t).coerceIn(0f, 1f)
        val flash = (0.5f + 0.5f * cos(t * 3f * 2f * Math.PI.toFloat())) * fade
        val w = context.scaledWindowWidth
        val h = context.scaledWindowHeight
        context.fill(0, 0, w, h, ((flash * 0.7f * 255).toInt().coerceIn(0, 255) shl 24) or 0x7A2BD0)
        val size = minOf(w, h) / 4
        RenderSystem.enableBlend()
        context.setShaderColor(1f, 1f, 1f, sqrt(fade))
        context.drawTexture(TURN_ICON, (w - size) / 2, (h - size) / 2, 0f, 0f, size, size, size, size)
        context.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    private fun startLoops(client: MinecraftClient, entity: DistortionEntity) {
        val sounds = when (entity) {
            is DistortionCloudEntity -> listOf(
                Follow(entity, ModSounds.CLOUD_WHISPER, { e -> if (e.ending) 0f else 0.7f + 0.3f * e.stare }, { e -> 1f - 0.1f * e.stare }),
            )
            is DistortionJuniorEntity -> listOf(
                Follow(entity, ModSounds.JUNIOR_WHISPER, { e -> if (e.ending) 0f else 0.5f }, { 1f }),
                Follow(entity, ModSounds.JUNIOR_STARE, { e -> if (e.ending || e.stare <= 0f) 0f else 0.25f + 0.75f * e.stare }, { e -> 0.6f + 0.9f * e.stare }),
            )
            else -> return
        }
        loops[entity.id] = sounds
        for (sound in sounds) client.soundManager.play(sound)
    }

    /**
     * A loop that follows [entity], its volume and pitch following it too, until it is gone; [ambient]: heard the same
     * wherever it is.
     */
    private class Follow(
        private val entity: DistortionEntity,
        event: SoundEvent,
        private val volumeOf: (DistortionEntity) -> Float,
        private val pitchOf: (DistortionEntity) -> Float,
        private val ambient: Boolean = false,
    ) : MovingSoundInstance(event, SoundCategory.HOSTILE, SoundInstance.createRandom()) {

        init {
            repeat = true
            repeatDelay = 0
            if (ambient) {
                attenuationType = SoundInstance.AttenuationType.NONE
                relative = true
            }
            follow()
        }

        // Played even while silent, so the stare's hum is ready when the stare starts.
        override fun shouldAlwaysPlay(): Boolean = true

        override fun tick() {
            if (entity.isRemoved) {
                setDone()
                return
            }
            follow()
        }

        private fun follow() {
            if (!ambient) {
                x = entity.x
                y = entity.y + entity.height * 0.6
                z = entity.z
            }
            volume = volumeOf(entity).coerceIn(0f, 1f)
            pitch = pitchOf(entity).coerceIn(0.5f, 2f)
        }
    }

    /** Degrees round the Junior's eye within which the drift pushes the crosshair away, and how hard. */
    private const val PUSH_DEGREES = 12f
    private const val PUSH = 0.35f
    private const val MAX_BARS = 18
    /** Blocks within which a chasing Cloud turns the picture violet, and how fast it turns. */
    private const val VIOLET_RANGE = 20.0
    private const val VIOLET_EASING = 0.12f
    /** How fast the corridor's dark lift comes in and goes (about a second). */
    private const val DARK_LIFT_EASING = 0.08f
    /** The violet the picture is multiplied by. */
    private const val VIOLET_R = 0.72f
    private const val VIOLET_G = 0.42f
    private const val VIOLET_B = 1f
    /** Ticks a jump's cue lasts. */
    private const val CUE_TICKS = 16
    /** The game's static: its volume, its pitch at the start, and how much higher it is at the end. */
    private const val STATIC_VOLUME = 0.55f
    private const val STATIC_PITCH = 0.8f
    private const val STATIC_RISE = 0.6f
}
