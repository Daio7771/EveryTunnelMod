package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.SubjectEntity
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer

/**
 * Draws a test subject with GeckoLib, its stage's body and skin (`geo/entity/subject_<stage>.geo.json`,
 * `textures/entity/subject_<stage>.png`, from `tools/entity/subject.py`); S-4's clouded eyes glow faintly (the
 * others have nothing that glows, so no glow mask, which GeckoLib would refuse empty).
 * Not a living thing, so it is turned to its yaw here.
 */
class SubjectRenderer(context: EntityRendererFactory.Context) : GeoEntityRenderer<SubjectEntity>(context, SubjectModel()) {

    init {
        shadowRadius = 0.4f
        addRenderLayer(GlowOfTheLast(this))
    }

    override fun applyRotations(animatable: SubjectEntity, poseStack: MatrixStack, ageInTicks: Float, rotationYaw: Float, partialTick: Float) {
        super.applyRotations(animatable, poseStack, ageInTicks, animatable.yaw, partialTick)
    }
}

private class SubjectModel : GeoModel<SubjectEntity>() {

    override fun getModelResource(animatable: SubjectEntity): Identifier = Identifier(EveryTunnel.MOD_ID, "geo/entity/subject_${animatable.stage}.geo.json")

    override fun getTextureResource(animatable: SubjectEntity): Identifier = Identifier(EveryTunnel.MOD_ID, "textures/entity/subject_${animatable.stage}.png")

    override fun getAnimationResource(animatable: SubjectEntity): Identifier = ANIMATION

    private companion object {
        val ANIMATION = Identifier(EveryTunnel.MOD_ID, "animations/entity/subject.animation.json")
    }
}

/** The glow layer, for the last stage only: the only one with a glow mask. */
private class GlowOfTheLast(renderer: SubjectRenderer) : AutoGlowingGeoLayer<SubjectEntity>(renderer) {

    override fun render(
        poseStack: MatrixStack, animatable: SubjectEntity, bakedModel: BakedGeoModel, renderType: RenderLayer?,
        bufferSource: VertexConsumerProvider, buffer: VertexConsumer?, partialTick: Float, packedLight: Int, packedOverlay: Int,
    ) {
        if (animatable.stage != SubjectEntity.STAGES) return
        super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay)
    }
}
