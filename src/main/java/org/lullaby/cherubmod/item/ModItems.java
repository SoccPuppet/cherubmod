package org.lullaby.cherubmod.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.lullaby.cherubmod.Cherubmod;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Cherubmod.MOD_ID);

    public static final RegistryObject<Item> VAPE = ITEMS.register("vape",
            () -> new VapeItem(new Item.Properties().tab(ItemGroup.TAB_MISC).stacksTo(1)));
}