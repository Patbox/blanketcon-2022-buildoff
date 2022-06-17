package eu.pb4.buildoff.mixin;

import eu.pb4.buildoff.BuildOffSetup;
import net.minecraft.block.PistonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBlock.class)
public class PistonBlockMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void move(World world, BlockPos pos, Direction dir, boolean retract, CallbackInfoReturnable<Boolean> cir) {
        if (world == BuildOffSetup.world.asWorld()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

}
