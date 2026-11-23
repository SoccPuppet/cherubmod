package org.lullaby.cherubmod.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SSetSlotPacket;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.possession.PossessionContainerRules;
import org.lullaby.cherubmod.possession.PossessionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(
            method = "slotChanged(Lnet/minecraft/inventory/container/Container;ILnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void cherubmod_onSlotChanged(Container container, int slotIndex, ItemStack stack, CallbackInfo ci) {
        if (!PossessionHelper.isPossessionStack(stack)) {
            return;
        }
        if (slotIndex < 0 || slotIndex >= container.slots.size()) {
            return;
        }

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        if (data == null) {
            return;
        }

        Slot slot = container.getSlot(slotIndex);
        boolean isCurrent = PossessionHelper.isCurrentValidCopy(stack, player, data);
        boolean shouldDelete = false;

        if (slot.container != player.inventory) {
            if (!isCurrent) {
                // Stale or foreign copy in any external slot: always delete
                shouldDelete = true;
            } else if (PossessionContainerRules.isAllowedWorkstationSlot(player, container, slotIndex)) {
                // Valid copy in an allowed upgrade workstation: keep it in the slot,
                data.setPossession(PossessionHelper.stripPossessionTag(stack));
            } else {
                // Chest, hopper, furnace, discharge station, crafting table, or sacrifice slot: sync & dismiss
                data.setPossession(PossessionHelper.stripPossessionTag(stack));
                shouldDelete = true;
            }
        } else if (!isCurrent) {
            // Stale or foreign copy inside player inventory: delete immediately
            shouldDelete = true;
        }

        if (shouldDelete) {
            slot.set(ItemStack.EMPTY);
            if (!player.ignoreSlotUpdateHack) {
                player.connection.send(new SSetSlotPacket(container.containerId, slotIndex, ItemStack.EMPTY));
            }
            ci.cancel();
        }
    }
}