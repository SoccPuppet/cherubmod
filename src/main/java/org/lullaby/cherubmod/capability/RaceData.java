package org.lullaby.cherubmod.capability;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RaceData implements IRaceData{
    private Race race = Race.NONE;
    private Archangel archangel = Archangel.NONE;
    private Race pendingRace = Race.NONE;
    private ItemStack possession = ItemStack.EMPTY;
    private final List<ItemStack> hoveringItems = new ArrayList<>();
    private long possessionGeneration = 0L;
    private int worldStasisTicks = 0;
    private int worldStasisCooldownTicks = 0;
    private int plushStuffedStacks = 0;
    private int plushStuffedTicks = 0;

    @Override
    public int getPlushStuffedStacks() {
        return plushStuffedStacks;
    }

    @Override
    public void setPlushStuffedStacks(int stacks) {
        this.plushStuffedStacks = Math.max(0, Math.min(stacks, 3));
    }

    @Override
    public int getPlushStuffedTicks() {
        return plushStuffedTicks;
    }

    @Override
    public void setPlushStuffedTicks(int ticks) {
        this.plushStuffedTicks = Math.max(0, ticks);
    }

    @Override
    public int getWorldStasisTicks() {
        return worldStasisTicks;
    }

    @Override
    public void setWorldStasisTicks(int ticks) {
        this.worldStasisTicks = Math.max(0, ticks);
    }

    @Override
    public int getWorldStasisCooldownTicks() {
        return worldStasisCooldownTicks;
    }

    @Override
    public void setWorldStasisCooldownTicks(int ticks) {
        this.worldStasisCooldownTicks = Math.max(0, ticks);
    }

    @Override
    public Race getPendingRace() {
        return pendingRace;
    }

    @Override
    public void setPendingRace(Race pendingRace) {
        this.pendingRace = pendingRace != null ? pendingRace : Race.NONE;
    }

    @Override
    public long getPossessionGeneration() {
        return possessionGeneration;
    }

    @Override
    public void setPossessionGeneration(long generation) {
        this.possessionGeneration = generation;
    }

    @Override
    public long incrementPossessionGeneration() {
        return ++this.possessionGeneration;
    }

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

    private boolean judgementTotemCharged = true;

    @Override
    public boolean isJudgementTotemCharged() {
        return judgementTotemCharged;
    }

    @Override
    public void setJudgementTotemCharged(boolean charged) {
        this.judgementTotemCharged = charged;
    }

    private int hangedOneActiveTicks = 0;

    @Override
    public int getHangedOneActiveTicks() {
        return hangedOneActiveTicks;
    }

    @Override
    public void setHangedOneActiveTicks(int ticks) {
        this.hangedOneActiveTicks = Math.max(0, ticks);
    }

    @Override
    public void copyFrom(IRaceData raceData) {
        this.race = raceData.getRace();
        this.pendingRace = raceData.getPendingRace();
        this.archangel = raceData.getArchangel();
        this.possession = raceData.getPossession();
        this.setHoveringItems(raceData.getHoveringItems());
        this.possessionGeneration = raceData.getPossessionGeneration();
        this.judgementTotemCharged = raceData.isJudgementTotemCharged();
        this.hangedOneActiveTicks = raceData.getHangedOneActiveTicks();
        this.worldStasisTicks = raceData.getWorldStasisTicks();
        this.worldStasisCooldownTicks = raceData.getWorldStasisCooldownTicks();
        this.plushStuffedStacks = raceData.getPlushStuffedStacks();
        this.plushStuffedTicks = raceData.getPlushStuffedTicks();
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
