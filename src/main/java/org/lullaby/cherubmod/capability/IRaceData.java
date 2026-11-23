package org.lullaby.cherubmod.capability;

public interface IRaceData {
    Race getRace();
    void setRace(Race race);

    Archangel getArchangel();
    void setArchangel(Archangel archangel);

    boolean isPendingRaceChange();
    void setPendingRaceChange(boolean pendingRaceChange);

    void copyFrom(IRaceData raceData);
}
