package org.lullaby.cherubmod.capability;

import net.minecraft.item.ItemStack;

import java.util.List;

public interface IRaceData {
    Race getRace();
    void setRace(Race race);

    Archangel getArchangel();
    void setArchangel(Archangel archangel);

    boolean isJudgementTotemCharged();
    void setJudgementTotemCharged(boolean charged);

    Race getPendingRace();
    void setPendingRace(Race pendingRace);

    int getWorldStasisTicks();
    void setWorldStasisTicks(int ticks);

    int getWorldStasisCooldownTicks();
    void setWorldStasisCooldownTicks(int ticks);

    default boolean isWorldStasisActive() {
        return getWorldStasisTicks() > 0;
    }

    default boolean isPendingRaceChange() {
        return getPendingRace() != Race.NONE;
    }

    int getHangedOneActiveTicks();
    void setHangedOneActiveTicks(int ticks);

    default boolean isHangedOneActive() {
        return getHangedOneActiveTicks() > 0;
    }

    ItemStack getPossession();
    void setPossession(ItemStack stack);

    List<ItemStack> getHoveringItems();
    void setHoveringItems(List<ItemStack> hoveringItems);

    long getPossessionGeneration();
    void setPossessionGeneration(long generation);
    long incrementPossessionGeneration();

    void copyFrom(IRaceData raceData);
}
