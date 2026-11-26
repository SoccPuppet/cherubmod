package org.lullaby.cherubmod.capability;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.event.JudgementAbilityEvents;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class JudgementMilkCapability {

    public static final ResourceLocation ID = new ResourceLocation(Cherubmod.MOD_ID, "judgement_milk");

    private static final Tags.IOptionalNamedTag<Fluid> MILK_FLUID_TAG =
            FluidTags.createOptional(new ResourceLocation("forge", "milk"));

    private JudgementMilkCapability() {}

    public static Fluid resolveMilkFluid() {
        if (ForgeMod.MILK.isPresent()) {
            return ForgeMod.MILK.get();
        }
        for (Fluid fluid : MILK_FLUID_TAG.getValues()) {
            if (fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState())) {
                return fluid;
            }
        }
        return Fluids.EMPTY;
    }

    public static final class Provider implements ICapabilityProvider {
        private final JudgementMilkFluidHandler handler;
        private final LazyOptional<IFluidHandler> optional;

        public Provider(PlayerEntity player) {
            this.handler = new JudgementMilkFluidHandler(player);
            this.optional = LazyOptional.of(() -> this.handler);
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && handler.isAvailable()) {
                return optional.cast();
            }
            return LazyOptional.empty();
        }

        public void invalidate() {
            optional.invalidate();
        }
    }

    private static final class JudgementMilkFluidHandler implements IFluidHandler {
        private final PlayerEntity player;

        private JudgementMilkFluidHandler(PlayerEntity player) {
            this.player = player;
        }

        private boolean isAvailable() {
            return player.isAlive()
                    && !player.isSpectator()
                    && !JudgementAbilityEvents.isNotJudgementCherub(player)
                    && resolveMilkFluid() != Fluids.EMPTY;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank != 0 || !isAvailable()) {
                return FluidStack.EMPTY;
            }
            return new FluidStack(resolveMilkFluid(), FluidAttributes.BUCKET_VOLUME);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? FluidAttributes.BUCKET_VOLUME : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return false; // Drain-only
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0; // Drain-only
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (!isAvailable() || resource.isEmpty() || resource.getAmount() <= 0) {
                return FluidStack.EMPTY;
            }
            Fluid milk = resolveMilkFluid();
            if (!resource.getFluid().isSame(milk)) {
                return FluidStack.EMPTY;
            }
            int drained = Math.min(resource.getAmount(), FluidAttributes.BUCKET_VOLUME);
            return new FluidStack(milk, drained);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (!isAvailable() || maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            Fluid milk = resolveMilkFluid();
            int drained = Math.min(maxDrain, FluidAttributes.BUCKET_VOLUME);
            return new FluidStack(milk, drained);
        }
    }
}