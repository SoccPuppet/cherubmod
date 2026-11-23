package org.lullaby.cherubmod.capability;

public class RaceData implements IRaceData{
    private Race race = Race.NONE;
    private Archangel archangel = Archangel.NONE;
    private boolean pendingRaceChange = false;

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
    }
}
