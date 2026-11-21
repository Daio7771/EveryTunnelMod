package daio7771.everytunnel.chapter2.prologue.client

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.core.cutscene.Cutscene
import daio7771.everytunnel.chapter2.prologue.platform.ScientistEntity
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer
import software.bernie.geckolib.renderer.GeoRenderer
import software.bernie.geckolib.renderer.layer.GeoRenderLayer
import daio7771.everytunnel.common.client.render.EyesGlowLayer
/**
 * Draws the prologue's scientist with GeckoLib through [ScientistModel]: as the man (`geo/entity/scientist.geo.json`,
 * `textures/entity/scientist.png`, `animations/entity/scientist.animation.json`; from `tools/entity/chapter2/scientist.py`),
 * and, once he has changed ([ScientistEntity.PALE], and in the factory's chamber [ScientistEntity.CONTAINED]), as the Level 2 Pale man, eyes glowing. Not a living thing, so it
 * is turned to its yaw here.
 */
class ScientistRenderer(context: EntityRendererFactory.Context) : GeoEntityRenderer<ScientistEntity>(context, ScientistModel()) {

    init {
        shadowRadius = 0.4f
        addRenderLayer(PaleEyes(this))
    }

    override fun applyRotations(animatable: ScientistEntity, poseStack: MatrixStack, ageInTicks: Float, rotationYaw: Float, partialTick: Float) {
        super.applyRotations(animatable, poseStack, ageInTicks, animatable.yaw, partialTick)
        if (animatable.stage != ScientistEntity.PALE) return
        // The Pale man does not appear standing: he comes up curled over and smaller, and unfolds (slowly at first, then
        // quickly, then easing into his height), so the change reads as a body rising and not as a model swapped.
        val folded = 1f - Cutscene.ease(animatable.unfolded(partialTick).toDouble()).toFloat()
        poseStack.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(UNFOLD_LEAN * folded))
        val size = 1f - UNFOLD_SHRINK * folded
        poseStack.scale(size, size, size)
    }

    companion object {
        /** Curled over by this many degrees and this much smaller when he appears. */
        private const val UNFOLD_LEAN = 42f
        private const val UNFOLD_SHRINK = 0.22f
    }

    /** The Pale man's eyes ([EyesGlowLayer] with his `_glowmask`), only once the scientist has become him. */
    private class PaleEyes(renderer: GeoRenderer<ScientistEntity>) : GeoRenderLayer<ScientistEntity>(renderer) {

        private val eyes = EyesGlowLayer(renderer)

        override fun render(
            poseStack: MatrixStack, animatable: ScientistEntity, bakedModel: BakedGeoModel, renderType: RenderLayer?,
            bufferSource: VertexConsumerProvider, buffer: VertexConsumer?, partialTick: Float, packedLight: Int, packedOverlay: Int,
        ) {
            if (animatable.stage == ScientistEntity.PALE || animatable.stage == ScientistEntity.CONTAINED) {
                eyes.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay)
            }
        }
    }
}

/**
 * The scientist's model, texture and animations; his sick skin (`scientist_pale.png`) once he is [ScientistEntity.SICK]
 * and through [ScientistEntity.STRETCHED]; and the Pale man's own, all three, from the moment he becomes one.
 */
class ScientistModel : GeoModel<ScientistEntity>() {

    private fun name(animatable: ScientistEntity) =
        if (animatable.stage == ScientistEntity.PALE || animatable.stage == ScientistEntity.CONTAINED) "pale_man" else "scientist"

    override fun getModelResource(animatable: ScientistEntity): Identifier =
        Identifier(EveryTunnel.MOD_ID, "geo/entity/${name(animatable)}.geo.json")

    override fun getTextureResource(animatable: ScientistEntity): Identifier {
        val skin = if (animatable.stage == ScientistEntity.SICK || animatable.stage == ScientistEntity.STRETCHED) "scientist_pale" else name(animatable)
        return Identifier(EveryTunnel.MOD_ID, "textures/entity/$skin.png")
    }

    override fun getAnimationResource(animatable: ScientistEntity): Identifier =
        Identifier(EveryTunnel.MOD_ID, "animations/entity/${name(animatable)}.animation.json")
}
