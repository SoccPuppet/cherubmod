package org.lullaby.cherubmod.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import org.lullaby.cherubmod.light.CherubLightManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// UNCLE BEN WHAT HAPPENED
// :( embeddium
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(
            method = "getLightColor(Lnet/minecraft/world/IBlockDisplayReader;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void cherubmod_applyCherubVisualLight(
            IBlockDisplayReader world,
            BlockState state,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        int packed = cir.getReturnValueI();
        int vanillaBlock = LightTexture.block(packed);
        if (vanillaBlock >= 15) {
            return;
        }

        int cherubLight = CherubLightManager.getClientLightLevel(pos);
        if (cherubLight > vanillaBlock) {
            int sky = LightTexture.sky(packed);
            cir.setReturnValue(LightTexture.pack(cherubLight, sky));
        }
    }
}