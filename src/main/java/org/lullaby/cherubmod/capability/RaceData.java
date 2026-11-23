package org.lullaby.cherubmod.capability;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RaceData implements IRaceData{
    private Race race = Race.NONE;
    private Archangel archangel = Archangel.NONE;
    private boolean pendingRaceChange = false;
    private ItemStack possession = ItemStack.EMPTY;
    private final List<ItemStack> hoveringItems = new ArrayList<>();

    @Override
    public Race getRace() {
        return race;
    }
    @Override
    public void setRace(Race race){
        if(race == null){
            this.race = Race.NONE;
        } else {
            this.race = race;
        }
    }

    @Override
    public Archangel getArchangel() {
        return archangel;
    }
    @Override
    public void setArchangel(Archangel archangel) {
        if(archangel == null){
            this.archangel = Archangel.NONE;
        }  else {
            this.archangel = archangel;
        }
    }

    @Override
    public boolean isPendingRaceChange() {
        return pendingRaceChange;
    }
    @Override
    public void setPendingRaceChange(boolean pendingRaceChange) {
        this.pendingRaceChange = pendingRaceChange;
    }

    @Override
    public void copyFrom(IRaceData raceData) {
        this.race = raceData.getRace();
        this.archangel = raceData.getArchangel();
        this.pendingRaceChange = raceData.isPendingRaceChange();
        this.possession = raceData.getPossession();
        this.setHoveringItems(raceData.getHoveringItems());
    }

    @Override
    public List<ItemStack> getHoveringItems() {
        return hoveringItems;
    }
    @Override
    public void setHoveringItems(List<ItemStack> hoveringItems) {
        this.hoveringItems.clear();
        if(hoveringItems != null){
            for(ItemStack hoveringItem : hoveringItems){
                if(hoveringItem != null && !hoveringItem.isEmpty()){
                    this.hoveringItems.add(hoveringItem);
                }
            }
        }
    }

    @Override
    public ItemStack getPossession() {
        return possession;
    }
    @Override
    public void setPossession(ItemStack possession) {
        this.possession = possession;
    }
}
