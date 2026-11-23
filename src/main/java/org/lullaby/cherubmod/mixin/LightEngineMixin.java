package org.lullaby.cherubmod.mixin;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkLightProvider;
import net.minecraft.world.lighting.LightEngine;
import org.lullaby.cherubmod.light.CherubLightManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class LightEngineMixin {

    @Shadow
    @Final
    protected IChunkLightProvider chunkSource;

    @Shadow
    @Final
    protected LightType layer;

    @Inject(method = "getLightValue", at = @At("RETURN"), cancellable = true)
    private void cherubmod_applyCherubBlockLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (this.layer != LightType.BLOCK) {
            return;
        }
        int vanillaLight = cir.getReturnValueI();
        if (vanillaLight >= 15) {
            return;
        }
        IBlockReader reader = this.chunkSource.getLevel();
        if (reader instanceof World) {
            int cherubLight = CherubLightManager.getLightLevel((World) reader, pos);
            if (cherubLight > vanillaLight) {
                cir.setReturnValue(cherubLight);
            }
        }
    }
}