package daio7771.everytunnel.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Flowing water washes away every block without collision (lamps, props, switches) and drops it as an item; Fabric API has no event for
 * it in 1.20.1. The mod's own blocks are never filled by a fluid, so a flooded level keeps its furniture where it stands (the water
 * still flows around them). Vanilla blocks are untouched, and the mod's blocks only exist in worlds with levels.
 */
@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {

    @Inject(method = "canFill", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$keepOwnBlocks(BlockView world, BlockPos pos, BlockState state, Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
        if ("every_tunnel".equals(Registries.BLOCK.getId(state.getBlock()).getNamespace())) cir.setReturnValue(false);
    }
}
