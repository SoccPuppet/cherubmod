package org.lullaby.cherubmod.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RaceCapability {
    @CapabilityInject(IRaceData.class)
    public static Capability<IRaceData> INSTANCE = null;

    public static final ResourceLocation ID = new ResourceLocation("cherubmod:race");

    public static void register(){
        CapabilityManager.INSTANCE.register(IRaceData.class, new Storage(), RaceData::new);
    }


    public static class Storage implements Capability.IStorage<IRaceData>{
        private static final String KEY_RACE = "Race";
        private static final String KEY_ARCHANGEL = "Archangel";
        private static final String KEY_PENDING_RACECHANGE = "PendingRaceChange";

        @Nullable
        @Override
        public INBT writeNBT(Capability<IRaceData> capability, IRaceData instance, Direction _unused){
            CompoundNBT tag = new CompoundNBT();
            tag.putString(KEY_RACE, instance.getRace().toString());
            tag.putString(KEY_ARCHANGEL, instance.getArchangel().toString());
            tag.putBoolean(KEY_PENDING_RACECHANGE, instance.isPendingRaceChange());
            return tag;
        }

        @Override
        public void readNBT(Capability<IRaceData> capability, IRaceData instance, Direction _unused, INBT nbt){
            if(!(nbt instanceof CompoundNBT)){
                return;
            }
            CompoundNBT tag = (CompoundNBT)nbt;

            try{
                instance.setRace(Race.valueOf(tag.getString(KEY_RACE)));
            } catch(IllegalArgumentException e){
                instance.setRace(Race.NONE);
            }
            try{
                instance.setArchangel(Archangel.valueOf(tag.getString(KEY_ARCHANGEL)));
            } catch(IllegalArgumentException e){
                instance.setArchangel(Archangel.NONE);
            }
            instance.setPendingRaceChange(tag.getBoolean(KEY_PENDING_RACECHANGE));
        }
    }

    public static class Provider implements ICapabilitySerializable<CompoundNBT>{
        private final IRaceData raceData = new RaceData();
        private final LazyOptional<IRaceData> optional = LazyOptional.of(() -> raceData);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction _unused){
            return cap == INSTANCE ? optional.cast() : LazyOptional.empty();
        }
        @Override
        public CompoundNBT serializeNBT(){
            return (CompoundNBT) INSTANCE.getStorage().writeNBT(INSTANCE, raceData, null);
        }
        @Override
        public void deserializeNBT(CompoundNBT nbt){
            INSTANCE.getStorage().readNBT(INSTANCE, raceData, null, nbt);
        }

        public void invalidate(){
            optional.invalidate();
        }
    }
}
