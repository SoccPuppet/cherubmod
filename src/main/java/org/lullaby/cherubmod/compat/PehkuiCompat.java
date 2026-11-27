package org.lullaby.cherubmod.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.ModList;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

public final class PehkuiCompat {
    public static final String PEHKUI_MOD_ID = "pehkui";
    public static final float PLUSH_SCALE = 0.5F; // 1.8 * 0.5 = 0.9 blocks tall
    public static final float[] PLUSH_STACK_SCALES = new float[] {
            PLUSH_SCALE,   // 0 stacks: 0.9 blocks
            1.1F / 1.8F,   // 1 stack:  1.1 blocks
            1.5F / 1.8F,   // 2 stacks: 1.5 blocks
            1.0F           // 3 stacks: 1.8 blocks (normal player size)
    };
    private static final String TAG_PLUSH_SCALED = "cherubmod_plush_scaled";

    private PehkuiCompat() {}

    public static float getPlushScaleForStacks(int stuffedStacks) {
        int clamped = Math.max(0, Math.min(stuffedStacks, PLUSH_STACK_SCALES.length - 1));
        return PLUSH_STACK_SCALES[clamped];
    }

    public static void updatePlushScale(PlayerEntity player, boolean isPlush) {
        updatePlushScale(player, isPlush, 0);
    }

    public static void updatePlushScale(PlayerEntity player, boolean isPlush, int stuffedStacks) {
        if (!ModList.get().isLoaded(PEHKUI_MOD_ID)) {
            return;
        }

        CompoundNBT entityData = player.getPersistentData();
        CompoundNBT persisted = entityData.getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        if (!entityData.contains(PlayerEntity.PERSISTED_NBT_TAG)) {
            entityData.put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
        }

        if (isPlush) {
            Handler.applyScale(player, getPlushScaleForStacks(stuffedStacks));
            persisted.putBoolean(TAG_PLUSH_SCALED, true);
        } else if (persisted.getBoolean(TAG_PLUSH_SCALED)) {
            Handler.applyScale(player, 1.0F);
            persisted.putBoolean(TAG_PLUSH_SCALED, false);
        }
    }

    private static final class Handler {
        private static void applyScale(PlayerEntity player, float target) {
            setIfChanged(ScaleTypes.HEIGHT.getScaleData(player), target);
            setIfChanged(ScaleTypes.WIDTH.getScaleData(player), target);
        }

        private static void setIfChanged(ScaleData data, float target) {
            if (Math.abs(data.getTargetScale() - target) > 1.0E-4F) {
                data.setTargetScale(target);
            }
        }
    }
}