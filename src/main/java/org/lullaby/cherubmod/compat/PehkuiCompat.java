package org.lullaby.cherubmod.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.ModList;
import virtuoel.pehkui.api.ScaleData;
import virtuoel.pehkui.api.ScaleTypes;

public final class PehkuiCompat {
    public static final String PEHKUI_MOD_ID = "pehkui";
    public static final float PLUSH_SCALE = 0.5F;                 // 1.8 * 0.5 = 0.9 blocks tall
    public static final float PLUSH_STUFFED_SCALE = 1.1F / 1.8F;  // 1.1 blocks tall (use 1.1F for 1.1x vanilla height)
    private static final String TAG_PLUSH_SCALED = "cherubmod_plush_scaled";

    private PehkuiCompat() {}

    public static void updatePlushScale(PlayerEntity player, boolean isPlush) {
        updatePlushScale(player, isPlush, false);
    }

    public static void updatePlushScale(PlayerEntity player, boolean isPlush, boolean isStuffed) {
        if (!ModList.get().isLoaded(PEHKUI_MOD_ID)) {
            return;
        }

        CompoundNBT entityData = player.getPersistentData();
        CompoundNBT persisted = entityData.getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        if (!entityData.contains(PlayerEntity.PERSISTED_NBT_TAG)) {
            entityData.put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
        }

        if (isPlush) {
            Handler.applyScale(player, isStuffed ? PLUSH_STUFFED_SCALE : PLUSH_SCALE);
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