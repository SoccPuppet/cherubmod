package org.lullaby.cherubmod.damage;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nullable;

public class StrengthPunchDamageSource extends EntityDamageSource {
    private final float critMultiplier;
    private final float crouchMultiplier;
    private final boolean crouchSlam;
    private final boolean shockwaveSecondary;
    @Nullable
    private final Vector3d shockwaveOrigin;

    private float rawPunch;
    private float fullRawPunch;
    private float targetArmor;
    private float spallDamage;
    private float preHitAbsorption;
    private boolean impactRecorded;
    private boolean spallDominant;

    private StrengthPunchDamageSource(
            PlayerEntity attacker,
            float critMultiplier,
            float crouchMultiplier,
            boolean crouchSlam,
            boolean shockwaveSecondary,
            @Nullable Vector3d shockwaveOrigin
    ) {
        super("cherub_strength_punch", attacker);
        this.critMultiplier = critMultiplier;
        this.crouchMultiplier = crouchMultiplier;
        this.crouchSlam = crouchSlam;
        this.shockwaveSecondary = shockwaveSecondary;
        this.shockwaveOrigin = shockwaveOrigin;
    }

    public static StrengthPunchDamageSource createPrimary(
            PlayerEntity attacker,
            float critMultiplier,
            boolean crouchSlam
    ) {
        return new StrengthPunchDamageSource(
                attacker,
                critMultiplier,
                crouchSlam ? 0.8F : 1.0F,
                crouchSlam,
                false,
                null
        );
    }

    public static StrengthPunchDamageSource createShockwave(
            PlayerEntity attacker,
            float critMultiplier,
            float crouchMultiplier,
            Vector3d shockwaveOrigin
    ) {
        return new StrengthPunchDamageSource(
                attacker,
                critMultiplier,
                crouchMultiplier,
                true,
                true,
                shockwaveOrigin
        );
    }

    public float getCrouchMultiplier() {
        return crouchMultiplier;
    }

    public float getDamageScale() {
        return critMultiplier * crouchMultiplier;
    }

    public boolean isCrouchSlam() {
        return crouchSlam;
    }

    public boolean isShockwaveSecondary() {
        return shockwaveSecondary;
    }

    @Nullable
    public Vector3d getShockwaveOrigin() {
        return shockwaveOrigin;
    }

    public void recordImpact(float rawPunch, float targetArmor, float spallDamage, float preHitAbsorption) {
        this.rawPunch = rawPunch;
        this.targetArmor = targetArmor;
        this.spallDamage = spallDamage;
        this.preHitAbsorption = preHitAbsorption;
        this.impactRecorded = true;
    }

    public void setFullRawPunch(float fullRawPunch) {
        this.fullRawPunch = fullRawPunch;
    }

    public float getFullRawPunch() {
        return fullRawPunch;
    }

    public boolean isImpactRecorded() {
        return impactRecorded;
    }

    public float getRawPunch() {
        return rawPunch;
    }

    public float getTargetArmor() {
        return targetArmor;
    }

    public float getSpallDamage() {
        return spallDamage;
    }

    public float getPreHitAbsorption() {
        return preHitAbsorption;
    }

    public void setSpallDominant(boolean spallDominant) {
        this.spallDominant = spallDominant;
    }

    @MethodsReturnNonnullByDefault
    @Override
    public ITextComponent getLocalizedDeathMessage(LivingEntity victim) {
        String key = spallDominant
                ? "death.attack.cherub_strength_spall"
                : "death.attack.cherub_strength_punch";
        assert this.entity != null;
        return new TranslationTextComponent(key, victim.getDisplayName(), this.entity.getDisplayName());
    }
}