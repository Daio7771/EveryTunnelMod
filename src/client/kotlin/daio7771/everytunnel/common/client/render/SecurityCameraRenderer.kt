package daio7771.everytunnel.common.client.render

import daio7771.everytunnel.common.core.camera.CameraAim
import daio7771.everytunnel.common.platform.ModBlocks
import daio7771.everytunnel.common.platform.ModSounds
import daio7771.everytunnel.common.platform.SecurityCameraBlock
import daio7771.everytunnel.common.platform.SecurityCameraBlockEntity
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.entity.BlockEntityRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.sound.SoundCategory
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random
import net.minecraft.world.RaycastContext
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A [SecurityCameraBlockEntity]'s head and red light, turned round the mount's pivot ([PIVOT_Y]) by a [CameraAim]:
 * towards the player's eyes while it sees them (within [RANGE], nothing solid between), else back to resting the
 * way its block faces, a little down. It turns no faster than [SPEED]; while it turns, its servo whirrs
 * (`block.camera.servo`). Its light blinks ([BLINK_MILLIS]), drawn at full brightness. Where each camera points is
 * kept here by position, until the player leaves the world.
 *
 * It draws the cameras of [block] only. A [sweeping] one (a later chapter's decorative cameras, registered with their own block
 * entity type) never looks for the player: it sweeps [SWEEP_DEGREES] either side of the way its block faces, once every
 * [SWEEP_PERIOD_MILLIS], each at its own point of the sweep, and whirrs softly now and then if the player is near.
 */
class SecurityCameraRenderer(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererFactory.Context,
    private val block: Block = ModBlocks.SECURITY_CAMERA,
    private val sweeping: Boolean = false,
) : BlockEntityRenderer<SecurityCameraBlockEntity> {

    override fun render(
        entity: SecurityCameraBlockEntity, tickDelta: Float, matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider, light: Int, overlay: Int,
    ) {
        val state = entity.cachedState
        if (!state.isOf(block)) return
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val world = client.world ?: return
        val pos = entity.pos.toImmutable()
        val facing = state.get(SecurityCameraBlock.FACING)
        val restYaw = CameraAim.yawTo(facing.offsetX.toDouble(), facing.offsetZ.toDouble())
        val camera = cameras.getOrPut(pos) { Remembered(CameraAim(restYaw, REST_PITCH)) }
        val now = System.nanoTime()
        val seconds = ((now - camera.lastNanos) / 1e9).coerceIn(0.0, 0.25)
        camera.lastNanos = now

        val pivot = Vec3d(pos.x + 0.5, pos.y + PIVOT_Y, pos.z + 0.5)
        val eye = player.getCameraPosVec(tickDelta)
        val to = eye.subtract(pivot)
        val sees = !sweeping && to.length() < RANGE && world.raycast(
            RaycastContext(pivot, eye, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player),
        ).type == HitResult.Type.MISS
        val (yaw, pitch) = if (sweeping) {
            sweep(restYaw, pos)
        } else if (sees) {
            CameraAim.yawTo(to.x, to.z) to CameraAim.pitchTo(to.y, hypot(to.x, to.z)).coerceIn(LOWEST_PITCH, HIGHEST_PITCH)
        } else {
            restYaw to REST_PITCH
        }
        val turned = camera.aim.turnTowards(yaw, pitch, seconds, if (sweeping) INSTANT else SPEED)
        if (sweeping) {
            if (to.length() < SWEEP_HEARD && now - camera.lastServoNanos > SWEEP_SERVO_NANOS) {
                camera.lastServoNanos = now
                client.soundManager.play(
                    PositionedSoundInstance(ModSounds.CAMERA_SERVO, SoundCategory.BLOCKS, 0.3f, 0.9f + Random.create().nextFloat() * 0.1f,
                        Random.create(), pivot.x, pivot.y, pivot.z),
                )
            }
        } else if (seconds > 0 && turned / seconds > MOVING && now - camera.lastServoNanos > SERVO_NANOS) {
            camera.lastServoNanos = now
            client.soundManager.play(
                PositionedSoundInstance(ModSounds.CAMERA_SERVO, SoundCategory.BLOCKS, 0.5f, 0.95f + Random.create().nextFloat() * 0.1f,
                    Random.create(), pivot.x, pivot.y, pivot.z),
            )
        }

        val blocks = client.blockRenderManager
        val head = ModBlocks.SECURITY_CAMERA.defaultState.with(SecurityCameraBlock.FACING, Direction.NORTH)
        matrices.push()
        matrices.translate(0.5, PIVOT_Y, 0.5)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.aim.yaw.toFloat()))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.aim.pitch.toFloat()))
        matrices.translate(-0.5, -PIVOT_Y, -0.5)
        blocks.renderBlockAsEntity(head.with(SecurityCameraBlock.PART, SecurityCameraBlock.Part.HEAD), matrices, vertexConsumers, light, overlay)
        if (System.currentTimeMillis() % BLINK_MILLIS < BLINK_ON_MILLIS) {
            blocks.renderBlockAsEntity(head.with(SecurityCameraBlock.PART, SecurityCameraBlock.Part.LED), matrices, vertexConsumers,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay)
        }
        matrices.pop()
    }

    /** Where a sweeping camera at [pos] aims now: [restYaw] plus the swing, and the resting pitch. */
    private fun sweep(restYaw: Double, pos: BlockPos): Pair<Double, Double> {
        val phase = ((pos.x * 31 + pos.z * 17) and 0xFF) / 256.0
        val turns = System.currentTimeMillis() / SWEEP_PERIOD_MILLIS.toDouble() + phase
        return (restYaw + SWEEP_DEGREES * sin(2 * Math.PI * turns)) to REST_PITCH
    }

    private class Remembered(val aim: CameraAim, var lastNanos: Long = System.nanoTime(), var lastServoNanos: Long = 0L)

    companion object {
        /** The mount's pivot over the block's floor, blocks (the models' (8, 11, 8)). */
        private const val PIVOT_Y = 11.0 / 16.0
        private const val RANGE = 16.0
        /** Degrees a second its servo turns it. */
        private const val SPEED = 70.0
        /** Faster than this (degrees a second) it is heard turning. */
        private const val MOVING = 8.0
        /** One servo sound at a time: about as long as one. */
        private const val SERVO_NANOS = 400_000_000L
        private const val REST_PITCH = -25.0
        private const val LOWEST_PITCH = -80.0
        private const val HIGHEST_PITCH = 10.0
        /** A sweeping camera's swing either side of its rest, degrees, and how long one back and forth takes. */
        private const val SWEEP_DEGREES = 50.0
        private const val SWEEP_PERIOD_MILLIS = 10_000L

        /** Turning so fast that a camera is where it is told to be. */
        private const val INSTANT = 100_000.0

        /** A sweeping camera whirrs this often, to a player this near (blocks). */
        private const val SWEEP_SERVO_NANOS = 2_400_000_000L
        private const val SWEEP_HEARD = 14.0
        private const val BLINK_MILLIS = 1200L
        private const val BLINK_ON_MILLIS = 180L

        private val cameras = HashMap<BlockPos, Remembered>()

        fun register() {
            ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { cameras.clear() } }
        }
    }
}
