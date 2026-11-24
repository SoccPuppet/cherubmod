package org.lullaby.cherubmod.tarot;

import net.minecraft.util.ResourceLocation;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Archangel;

import javax.annotation.Nullable;

public enum TarotArcana {
    THE_FOOL("the_fool", "TheFool", "THE_FOOL"),
    THE_MAGICIAN("the_magician", "TheMagician", "THE_MAGICIAN"),
    THE_HIGH_PRIESTESS("the_high_priestess", "TheHighPriestess", "THE_HIGH_PRIESTESS"),
    THE_MATRIARCH("the_matriarch", "TheMatriarch", "THE_MATRIARCH"),
    THE_SOVEREIGN("the_sovereign", "TheSovereign", "THE_SOVEREIGN"),
    THE_HIEROPHANT("the_hierophant", "TheHierophant", "THE_HIEROPHANT"),
    THE_LOVERS("the_lovers", "TheLovers", "THE_LOVERS"),
    THE_CHARIOT("the_chariot", "TheChariot", "THE_CHARIOT"),
    STRENGTH("strength", "Strength", "STRENGTH"),
    THE_HERMIT("the_hermit", "TheHermit", "THE_HERMIT"),
    THE_WHEEL_OF_FORTUNE("the_wheel_of_fortune", "TheWheelOfFortune", "THE_WHEEL_OF_FORTUNE"),
    JUSTICE("justice", "Justice", "JUSTICE"),
    THE_HANGED_ONE("the_hanged_one", "TheHangedOne", "THE_HANGED_ONE"),
    DEATH("death", "Death", "DEATH"),
    TEMPERANCE("temperance", "Temperance", "TEMPERANCE"),
    // XV - The Devil omitted
    THE_TOWER("the_tower", "TheTower", "THE_TOWER"),
    THE_STAR("the_star", "TheStar", "THE_STAR"),
    THE_MOON("the_moon", "TheMoon", "THE_MOON"),
    THE_SUN("the_sun", "TheSun", "THE_SUN"),
    JUDGEMENT("judgement", "Judgement", "JUDGEMENT"),
    THE_WORLD("the_world", "TheWorld", "THE_WORLD");

    private final ResourceLocation texture;
    private final String locKey;
    private final String[] enumMatchNames;

    TarotArcana(String textureName, String locKey, String... enumMatchNames) {
        this.texture = new ResourceLocation(Cherubmod.MOD_ID, "textures/gui/tarot/" + textureName + ".png");
        this.locKey = locKey;
        this.enumMatchNames = enumMatchNames;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    /**
     * Localization key for the Archangel's display title, e.g. "archangel.cherubmod.Temperance.name".
     */
    public String getNameKey() {
        return "archangel." + Cherubmod.MOD_ID + "." + locKey + ".name";
    }

    /**
     * Localization key for a numbered power line, e.g. "archangel.cherubmod.Temperance.1".
     */
    public String getPowerKey(int lineIndex) {
        return "archangel." + Cherubmod.MOD_ID + "." + locKey + "." + lineIndex;
    }

    /**
     * Returns the matching capability.Archangel constant (excluding NONE), or null if unimplemented.
     */
    @Nullable
    public Archangel resolveArchangel() {
        for (Archangel entry : Archangel.values()) {
            if (entry == Archangel.NONE) {
                continue;
            }
            for (String match : enumMatchNames) {
                if (entry.name().equalsIgnoreCase(match)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public boolean isImplemented() {
        return resolveArchangel() != null;
    }

    @Nullable
    public static TarotArcana fromArchangel(@Nullable Archangel archangel) {
        if (archangel == null || archangel == Archangel.NONE) {
            return null;
        }
        for (TarotArcana arcana : values()) {
            if (arcana.resolveArchangel() == archangel) {
                return arcana;
            }
        }
        return null;
    }
}