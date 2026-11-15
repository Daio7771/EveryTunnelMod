package daio7771.everytunnel.chapter2.level1.client

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.chapter2.level1.client.Ch2L1Client
import daio7771.everytunnel.client.cutscene.CutsceneView
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Guide
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.platform.FactoryNpcEntity
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1DataLoader
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.BufferRenderer
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.Tessellator
import net.minecraft.client.render.VertexFormat
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.math.RotationAxis
import org.joml.Matrix4f
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The guide arrow of Chapter 2's Level 1 ([Ch2L1Guide]): a filled V pointing down, blue with white edges, that turns slowly on itself
 * and bobs a little over the place of the player's current step (the nearest sample tube, the terminal, the analyzer, the broom, the
 * nearest patch of dirt; in Act 2 over the coworker's head, only when the player has lost him). It is drawn without a depth test, so benches and machines do not hide it, and it grows with distance so that
 * it can be seen across the LAB; it hides when the player is within `hide_within` of it, in a cinematic and with the HUD hidden (F1).
 * What it points at, how high and how near it hides are `tasks.json`'s `guide`.
 */
object TaskArrow {

    private const val SPIN_DEGREES_PER_TICK = 4.5f
    private const val BOB = 0.07
    private const val DEPTH = 0.07f

    /** The arrow is drawn at least this big, and bigger far away (up to [MAX_SCALE]) so that it stays readable. */
    private const val NEAR = 8.0
    private const val MAX_SCALE = 3.0

    private val BLUE = floatArrayOf(0.14f, 0.45f, 1.0f, 0.95f)
    private val BLUE_BACK = floatArrayOf(0.09f, 0.32f, 0.82f, 0.95f)
    private val WHITE = floatArrayOf(0.96f, 0.98f, 1.0f, 0.95f)

    /** One arm of the V, tip at the bottom (0, 0), as (x, y) corners; the other is its mirror image. */
    private val ARM = arrayOf(floatArrayOf(-0.35f, 0.6f), floatArrayOf(-0.13f, 0.6f), floatArrayOf(0f, 0.28f), floatArrayOf(0f, 0f))

    fun register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val world = client.world ?: return
        if (client.options.hudHidden || CutsceneView.active() || !Ch2L1Client.active()) return
        val tasks = Ch2L1DataLoader.tasks
        if (!tasks.guide.enabled) return
        val service = Ch2Progress
        val from = Position(player.x, player.eyeY, player.z)
        val state = Ch2L1Client.state()
        val target = if (state == Ch2L1State.ACT2_FOLLOW) {
            val coworker = world.entities.filterIsInstance<FactoryNpcEntity>().filter { it.skin == Ch2L1Skins.COWORKER }.minByOrNull { it.squaredDistanceTo(player) }
            Ch2L1Guide.follow(coworker?.let { Position(it.x, it.y, it.z) }, from, coworker != null && player.canSee(coworker), Ch2L1DataLoader.act2.follow) ?: return
        } else {
            val targets = Ch2L1Guide.targets(state, tasks, Ch2L1DataLoader.data.anchors) { service.ch2L1Counter(player, it) }
            val nearest = Ch2L1Guide.nearest(targets, from) ?: return
            if (Ch2L1Guide.hidden(nearest, from, tasks.guide)) return
            nearest
        }

        val matrices = context.matrixStack() ?: return
        val camera = context.camera().pos
        val time = world.time + context.tickDelta()
        val distance = sqrt((target.x - camera.x) * (target.x - camera.x) + (target.y - camera.y) * (target.y - camera.y) + (target.z - camera.z) * (target.z - camera.z))
        val scale = min(MAX_SCALE, max(1.0, distance / NEAR)).toFloat()

        matrices.push()
        matrices.translate(target.x - camera.x, target.y - camera.y + sin(time * 0.12) * BOB, target.z - camera.z)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * SPIN_DEGREES_PER_TICK))
        matrices.scale(scale, scale, scale)
        draw(matrices.peek().positionMatrix)
        matrices.pop()
    }

    private fun draw(matrix: Matrix4f) {
        val buffer = Tessellator.getInstance().buffer
        RenderSystem.setShader(GameRenderer::getPositionColorProgram)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()
        buffer.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR)
        for (side in floatArrayOf(1f, -1f)) {
            val arm = ARM.map { floatArrayOf(it[0] * side, it[1]) }
            face(buffer, matrix, arm, DEPTH, BLUE)
            face(buffer, matrix, arm, -DEPTH, BLUE_BACK)
            // The edges that show (the top, the inner side and the outer one) are white: the touch of white round the blue.
            for ((a, b) in listOf(0 to 1, 1 to 2, 3 to 0)) edge(buffer, matrix, arm[a], arm[b], WHITE)
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end())
        RenderSystem.enableCull()
        RenderSystem.enableDepthTest()
        RenderSystem.disableBlend()
    }

    /** The four-cornered [arm] at depth [z], as two triangles. */
    private fun face(buffer: BufferBuilder, matrix: Matrix4f, arm: List<FloatArray>, z: Float, colour: FloatArray) {
        for ((a, b, c) in listOf(Triple(0, 1, 2), Triple(0, 2, 3))) {
            for (i in listOf(a, b, c)) vertex(buffer, matrix, arm[i][0], arm[i][1], z, colour)
        }
    }

    /** A strip from [a] to [b] along the arm's thickness, front to back, as two triangles. */
    private fun edge(buffer: BufferBuilder, matrix: Matrix4f, a: FloatArray, b: FloatArray, colour: FloatArray) {
        val corners = listOf(
            Triple(a[0], a[1], -DEPTH), Triple(b[0], b[1], -DEPTH), Triple(b[0], b[1], DEPTH),
            Triple(a[0], a[1], -DEPTH), Triple(b[0], b[1], DEPTH), Triple(a[0], a[1], DEPTH),
        )
        for ((x, y, z) in corners) vertex(buffer, matrix, x, y, z, colour)
    }

    private fun vertex(buffer: BufferBuilder, matrix: Matrix4f, x: Float, y: Float, z: Float, c: FloatArray) {
        buffer.vertex(matrix, x, y, z).color(c[0], c[1], c[2], c[3]).next()
    }
}
