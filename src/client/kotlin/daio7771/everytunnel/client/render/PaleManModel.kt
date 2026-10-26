package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.PaleManEntity
import net.minecraft.util.Identifier
import software.bernie.geckolib.core.animatable.model.CoreGeoBone
import software.bernie.geckolib.core.animation.AnimationState
import software.bernie.geckolib.model.DefaultedEntityGeoModel

/**
 * The Pale man's GeckoLib model (`geo/entity/pale_man.geo.json` and its animations), plus
 * his stoop: under a ceiling lower than his upright 2.75 blocks, the bones are bent on top
 * of whatever animation plays, by [PaleManEntity.stoopAt] (0 = upright, 1 = fully).
 *
 * The pose is `STOOP_*` in `tools/entity/pale_man.py`, which draws it in its preview and
 * prints the stooped height (about 1.9 blocks); keep the numbers here the same. Angles are
 * Bedrock degrees (a positive x leans an upright part forwards); GeckoLib bones hold
 * radians with x negated.
 *
 * Setting a bone marks it as changed, and GeckoLib only clears those marks before this
 * hook, not after. A marked bone that the playing animation does not move is not reset
 * on the next frame, so the stoop would be added again and again (twisted limbs, a body
 * sinking through the floor). So every bent bone is unmarked here once it is set.
 */
class PaleManModel : DefaultedEntityGeoModel<PaleManEntity>(Identifier(EveryTunnel.MOD_ID, "pale_man")) {

    override fun setCustomAnimations(animatable: PaleManEntity, instanceId: Long, animationState: AnimationState<PaleManEntity>) {
        super.setCustomAnimations(animatable, instanceId, animationState)
        val amount = animatable.stoopAt(animationState.partialTick)
        if (amount <= 0f) return
        // The back leans to at least STOOP_TORSO (the run already leans far); the arms
        // turn back by as much, so they keep hanging.
        var extra = 0f
        bend("torso") { torso ->
            val lean = -Math.toDegrees(torso.rotX.toDouble()).toFloat()
            extra = (STOOP_TORSO - lean).coerceAtLeast(0f) * amount
            torso.rotX -= radians(extra)
        }
        for (arm in ARMS) bend(arm) { it.rotX += radians(extra) }
        bend("head") { it.rotX -= radians(STOOP_HEAD * amount) }
        for (thigh in THIGHS) bend(thigh) { it.rotX -= radians(STOOP_THIGH * amount) }
        for (shin in SHINS) bend(shin) { it.rotX -= radians(STOOP_SHIN * amount) }
        bend("root") { it.posY += STOOP_SINK * amount }
    }

    /** Applies [change] to the bone [name], then unmarks it, so the next frame starts it afresh. */
    private inline fun bend(name: String, change: (CoreGeoBone) -> Unit) {
        val bone = animationProcessor.getBone(name) ?: return
        change(bone)
        bone.resetStateChanges()
    }

    private fun radians(degrees: Float): Float = Math.toRadians(degrees.toDouble()).toFloat()

    companion object {
        /** `tools/entity/pale_man.py`: the least lean of the back, then added to the head, thighs and shins. */
        private const val STOOP_TORSO = 75f
        private const val STOOP_HEAD = -45f
        private const val STOOP_THIGH = -30f
        private const val STOOP_SHIN = 50f
        /** Model units (1/16 block) the whole body sinks, to keep the bent legs on the floor. */
        private const val STOOP_SINK = -1.0f

        private val ARMS = listOf("left_arm", "right_arm")
        private val THIGHS = listOf("left_thigh", "right_thigh")
        private val SHINS = listOf("left_shin", "right_shin")
    }
}
