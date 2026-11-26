package org.lullaby.cherubmod.capability;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

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
        private static final String KEY_PENDING_RACE = "PendingRace";
        private static final String KEY_LEGACY_PENDING = "PendingRaceChange";
        private static final String KEY_POSSESSION = "Possession";
        private static final String KEY_HOVERINGITEMS = "HoveringItems";
        private static final String KEY_POSSESSION_GEN = "PossessionGen";
        private static final String KEY_JUDGEMENT_TOTEM_CHARGED = "JudgementTotemCharged";
        private static final String KEY_HANGED_ONE_ACTIVE_TICKS = "HangedOneActiveTicks";

        @Nullable
        @Override
        public INBT writeNBT(Capability<IRaceData> capability, IRaceData instance, Direction _unused){
            CompoundNBT tag = new CompoundNBT();
            tag.putString(KEY_RACE, instance.getRace().toString());
            tag.putString(KEY_ARCHANGEL, instance.getArchangel().toString());
            tag.putBoolean(KEY_LEGACY_PENDING, instance.isPendingRaceChange());
            tag.putString(KEY_PENDING_RACE, instance.getPendingRace().name());
            tag.putLong(KEY_POSSESSION_GEN, instance.getPossessionGeneration());
            tag.putBoolean(KEY_JUDGEMENT_TOTEM_CHARGED, instance.isJudgementTotemCharged());
            tag.putInt(KEY_HANGED_ONE_ACTIVE_TICKS, instance.getHangedOneActiveTicks());
            if(!instance.getPossession().isEmpty()){
                tag.put(KEY_POSSESSION, instance.getPossession().save(new CompoundNBT()));
            }
            ListNBT hoverList = new ListNBT();
            for (ItemStack stack : instance.getHoveringItems()) {
                if (!stack.isEmpty()) {
                    hoverList.add(stack.save(new CompoundNBT()));
                }
            }
            tag.put(KEY_HOVERINGITEMS, hoverList);
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
            if (tag.contains(KEY_PENDING_RACE, Constants.NBT.TAG_STRING)) {
                try {
                    instance.setPendingRace(Race.valueOf(tag.getString(KEY_PENDING_RACE)));
                } catch (IllegalArgumentException e) {
                    instance.setPendingRace(Race.NONE);
                }
            } else if (tag.getBoolean(KEY_LEGACY_PENDING)) {
                instance.setPendingRace(Race.CHERUB);
            } else {
                instance.setPendingRace(Race.NONE);
            }
            instance.setPossessionGeneration(tag.getLong(KEY_POSSESSION_GEN));
            instance.setHangedOneActiveTicks(tag.getInt(KEY_HANGED_ONE_ACTIVE_TICKS));
            if (tag.contains(KEY_JUDGEMENT_TOTEM_CHARGED)) {
                instance.setJudgementTotemCharged(tag.getBoolean(KEY_JUDGEMENT_TOTEM_CHARGED));
            } else {
                instance.setJudgementTotemCharged(true);
            }

            if(tag.contains(KEY_POSSESSION, Constants.NBT.TAG_COMPOUND)){
                instance.setPossession(ItemStack.of(tag.getCompound(KEY_POSSESSION)));
            }

            List<ItemStack> loadHoveringItems = new ArrayList<>();
            if(tag.contains(KEY_HOVERINGITEMS, Constants.NBT.TAG_LIST)){
                ListNBT list =  tag.getList(KEY_HOVERINGITEMS, Constants.NBT.TAG_COMPOUND);
                for(int i = 0; i < list.size(); i++){
                    ItemStack stack = ItemStack.of(list.getCompound(i));
                    if(!stack.isEmpty()){
                        loadHoveringItems.add(stack);
                    }
                }
            }
            instance.setHoveringItems(loadHoveringItems);
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
