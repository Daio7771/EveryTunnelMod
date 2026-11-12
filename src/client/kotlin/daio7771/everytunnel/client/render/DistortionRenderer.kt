package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.DistortionEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import software.bernie.geckolib.core.animation.AnimationState
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer
import kotlin.random.Random

/**
 * Draws a Distortion (the Cloud, the Junior) with GeckoLib from `geo/entity/<name>.geo.json`, its
 * animations and its texture (all made by `tools/entity/distortion.py`), its eye and brightest glitches
 * glowing in the dark (the `_glowmask` textures) ([EyesGlowLayer]). They are not living things, so GeckoLib would leave
 * them facing north: they are turned to the entity's eased yaw ([DistortionEntity.renderYaw]).
 */
class DistortionRenderer<T : DistortionEntity>(context: EntityRendererFactory.Context, name: String, stages: Int) :
    GeoEntityRenderer<T>(context, DistortionModel(name, stages)) {

    init {
        shadowRadius = 0f
        addRenderLayer(EyesGlowLayer(this))
    }

    override fun applyRotations(animatable: T, poseStack: MatrixStack, ageInTicks: Float, rotationYaw: Float, partialTick: Float) {
        super.applyRotations(animatable, poseStack, ageInTicks, animatable.renderYaw(partialTick), partialTick)
    }
}

/**
 * A Distortion's model, plus its glitch: every [GLITCH_TICKS] some of its puffs jump sideways or vanish
 * for a moment, and now and then its eye jerks; more as it is stared at ([DistortionEntity.stare]), and
 * in its first moments. With [stages] textures (the Junior's `<name>_<n>.png`), the one shown is the
 * stage its stare has reached: its eye cracks; with none, it has one, `<name>.png`.
 *
 * Bones moved here are unmarked once set, as in [PaleManModel], so the next frame starts afresh.
 */
class DistortionModel<T : DistortionEntity>(name: String, stages: Int) : GeoModel<T>() {

    private val model = Identifier(EveryTunnel.MOD_ID, "geo/entity/$name.geo.json")
    private val animation = Identifier(EveryTunnel.MOD_ID, "animations/entity/$name.animation.json")
    private val textures =
        if (stages <= 0) listOf(Identifier(EveryTunnel.MOD_ID, "textures/entity/$name.png"))
        else (0 until stages).map { Identifier(EveryTunnel.MOD_ID, "textures/entity/${name}_$it.png") }

    override fun getModelResource(animatable: T): Identifier = model

    override fun getAnimationResource(animatable: T): Identifier = animation

    override fun getTextureResource(animatable: T): Identifier =
        textures[(animatable.stare * textures.size).toInt().coerceIn(0, textures.size - 1)]

    override fun setCustomAnimations(animatable: T, instanceId: Long, animationState: AnimationState<T>) {
        super.setCustomAnimations(animatable, instanceId, animationState)
        if (animatable.ending) return
        val step = ((animatable.age + animationState.partialTick) / GLITCH_TICKS).toInt()
        val rng = Random(animatable.id * 7919L + step)
        val intensity = 1f + animatable.stare * STARE_GLITCH + if (animatable.age < NEW_TICKS) NEW_GLITCH else 0f
        for (bone in animationProcessor.registeredBones) {
            when (bone.name) {
                "root" -> continue
                "eye" -> if (rng.nextFloat() < EYE_JERK * intensity) bone.posX += (rng.nextFloat() * 2 - 1) * 1.5f
                else -> {
                    bone.isHidden = rng.nextFloat() < VANISH * intensity
                    if (rng.nextFloat() < JUMP * intensity) bone.posX += (rng.nextFloat() * 2 - 1) * JUMP_UNITS
                }
            }
            bone.resetStateChanges()
        }
    }

    private companion object {
        /** A new glitch every this many ticks. */
        const val GLITCH_TICKS = 2f
        /** Chances, per glitch, for each puff to jump sideways (up to [JUMP_UNITS] model units) or vanish, and for the eye to jerk. */
        const val JUMP = 0.1f
        const val JUMP_UNITS = 3f
        const val VANISH = 0.03f
        const val EYE_JERK = 0.03f
        /** Stared at all the way, it glitches this many times more; newly come, this many. */
        const val STARE_GLITCH = 3f
        const val NEW_TICKS = 10
        const val NEW_GLITCH = 4f
    }
}
