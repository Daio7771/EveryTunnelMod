package daio7771.everytunnel.client.render

import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.core.animatable.GeoAnimatable
import software.bernie.geckolib.renderer.GeoRenderer
import software.bernie.geckolib.renderer.layer.GeoRenderLayer

/**
 * The glow of an entity's eyes (and brightest parts), from the `_glowmask` texture beside its own: the model drawn
 * again with vanilla's `eyes` layer (what spiders and Endermen glow with), at full brightness, adding light to what is
 * there.
 *
 * It replaces GeckoLib's `AutoGlowingGeoLayer`, which cuts those pixels out of the base texture (alpha 0) and paints
 * them only through its own emissive layer: where that layer was not drawn (seen without a shader pack) the Hunters'
 * eyes and the Clouds' violet eye were simply missing. Here the base texture keeps them, so they show even then, and
 * the glow is added on top.
 */
class EyesGlowLayer<T : GeoAnimatable>(renderer: GeoRenderer<T>) : GeoRenderLayer<T>(renderer) {

    override fun render(
        poseStack: MatrixStack, animatable: T, bakedModel: BakedGeoModel, renderType: RenderLayer?,
        bufferSource: VertexConsumerProvider, buffer: VertexConsumer?, partialTick: Float, packedLight: Int, packedOverlay: Int,
    ) {
        val base = getTextureResource(animatable)
        val glow = Identifier(base.namespace, base.path.removeSuffix(".png") + "_glowmask.png")
        val type = RenderLayer.getEyes(glow)
        renderer.reRender(
            bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
            LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f,
        )
    }
}
