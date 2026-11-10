package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.SubjectEntity
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import software.bernie.geckolib.model.GeoModel
import software.bernie.geckolib.renderer.GeoEntityRenderer

/**
 * Draws a test subject with GeckoLib, its stage's body and skin strapped to its stretcher
 * (`geo/entity/subject_<stage>.geo.json`, `textures/entity/subject_<stage>.png`, from `tools/entity/subject.py`).
 * Nothing of it glows: its eyes are shut. Not a living thing, so it is turned to its yaw here.
 */
class SubjectRenderer(context: EntityRendererFactory.Context) : GeoEntityRenderer<SubjectEntity>(context, SubjectModel()) {

    init {
        shadowRadius = 0.5f
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
