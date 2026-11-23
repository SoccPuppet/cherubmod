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

    ItemStack getPossession();
    void setPossession(ItemStack stack);

    List<ItemStack> getHoveringItems();
    void setHoveringItems(List<ItemStack> hoveringItems);

    void copyFrom(IRaceData raceData);
}
