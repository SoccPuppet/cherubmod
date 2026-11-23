package org.lullaby.cherubmod.possession;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.EnchantmentContainer;
import net.minecraft.inventory.container.GrindstoneContainer;
import net.minecraft.inventory.container.RepairContainer;
import net.minecraft.inventory.container.SmithingTableContainer;
import net.minecraft.util.ResourceLocation;
import org.lullaby.cherubmod.config.CherubServerConfig;

public class PossessionContainerRules {
    /**
     * Returns true if a valid possession is allowed to temporarily occupy this non-player slot
     * while the player keeps the GUI open.
     */
    public static boolean isAllowedWorkstationSlot(ServerPlayerEntity player, Container container, int slotIndex) {
        if (container == player.inventoryMenu) {
            return false; // Blocks 2x2 player crafting grid
        }

        // 1. Vanilla Anvil & Smithing Table: allow slot 0 (base item) and slot 2 (upgraded output),
        //    reject slot 1 (right-hand sacrifice/material slot) to prevent enchantment-transfer dupes.
        if (container instanceof RepairContainer || container instanceof SmithingTableContainer) {
            return slotIndex == 0 || slotIndex == 2;
        }

        // 2. Vanilla Enchanting Table: allow slot 0 (item to enchant), reject slot 1 (lapis).
        if (container instanceof EnchantmentContainer) {
            return slotIndex == 0;
        }

        // 3. Vanilla Grindstone: allow disenchanting alone, reject combining with another tool.
        if (container instanceof GrindstoneContainer) {
            if (slotIndex == 2) {
                return true;
            }
            if (slotIndex == 0 || slotIndex == 1) {
                int otherSlot = slotIndex == 0 ? 1 : 0;
                return container.getSlot(otherSlot).getItem().isEmpty();
            }
            return false;
        }

        // 4. Modded upgrade/modification stations configured in cherubmod-server.toml
        ResourceLocation typeId = getContainerTypeId(container);
        return CherubServerConfig.isAllowedWorkstation(typeId);
    }

    public static boolean isPreviewOutputSlot(Container container, int slotIndex) {
        return slotIndex == 2 && (container instanceof RepairContainer
                || container instanceof SmithingTableContainer
                || container instanceof GrindstoneContainer);
    }

    private static ResourceLocation getContainerTypeId(Container container) {
        try {
            ContainerType<?> type = container.getType();
            return type.getRegistryName();
        } catch (UnsupportedOperationException e) {
            // Vanilla/modded menus built with null ContainerType throw here
            return null;
        }
    }
}