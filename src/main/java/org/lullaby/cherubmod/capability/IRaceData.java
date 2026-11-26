package org.lullaby.cherubmod.capability;

import net.minecraft.item.ItemStack;

import java.util.List;

public interface IRaceData {
    Race getRace();
    void setRace(Race race);

    Archangel getArchangel();
    void setArchangel(Archangel archangel);

    boolean isPendingRaceChange();
    void setPendingRaceChange(boolean pendingRaceChange);

    boolean isJudgementTotemCharged();
    void setJudgementTotemCharged(boolean charged);

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
