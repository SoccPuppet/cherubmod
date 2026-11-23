package org.lullaby.cherubmod.config;

import net.minecraft.item.Item;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ITagCollection;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lullaby.cherubmod.Cherubmod;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CherubServerConfig {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_WORKSTATIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VALID_POSSESSIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> INVALID_POSSESSIONS;

    // Baked caches swapped atomically on load/reload to avoid parsing strings on hot paths
    private static volatile Set<ResourceLocation> allowedWorkstationIds = Collections.emptySet();
    private static volatile Set<ResourceLocation> validItemIds = Collections.emptySet();
    private static volatile Set<ResourceLocation> validTagIds = Collections.emptySet();
    private static volatile Set<ResourceLocation> invalidItemIds = Collections.emptySet();
    private static volatile Set<ResourceLocation> invalidTagIds = Collections.emptySet();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("possession");

        ALLOWED_WORKSTATIONS = builder
                .comment(
                        "Modded ContainerType registry IDs where a summoned possession may temporarily sit while the GUI is open.",
                        "Vanilla Anvil, Enchanting Table, Grindstone, and Smithing Table are handled separately."
                )
                .defineList(
                        "allowedWorkstations",
                        Arrays.asList(
                                "mekanism:modification_station",
                                "tetra:workbench"
                        ),
                        CherubServerConfig::isNonEmptyString
                );

        VALID_POSSESSIONS = builder
                .comment(
                        "Items or item tags explicitly allowed as a Cherub possession (unstackable items only).",
                        "Prefix tag entries with '#'."
                )
                .defineList(
                        "validPossessions",
                        Arrays.asList(
                                "#forge:wrenches",
                                "#forge:tools/wrench",
                                "#forge:tools",
                                "#forge:weapons",
                                "#forge:shears",
                                "#forge:fishing_rods",
                                "ars_nouveau:novice_spell_book",
                                "ars_nouveau:apprentice_spell_book",
                                "ars_nouveau:archmage_spell_book",
                                "ars_nouveau:wand",
                                "botania:twig_wand",
                                "mekanism:configurator",
                                "create:wrench"
                        ),
                        CherubServerConfig::isNonEmptyString
                );

        INVALID_POSSESSIONS = builder
                .comment(
                        "Items or item tags explicitly forbidden from being designated as a Cherub possession.",
                        "Prefix tag entries with '#'."
                )
                .defineList(
                        "invalidPossessions",
                        Arrays.asList(
                                "minecraft:totem_of_undying",
                                "minecraft:enchanted_book",
                                "minecraft:written_book",
                                "minecraft:writable_book"
                        ),
                        CherubServerConfig::isNonEmptyString
                );

        builder.pop();

        SPEC = builder.build();
    }

    private static boolean isNonEmptyString(Object obj) {
        return obj instanceof String && !((String) obj).trim().isEmpty();
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfig.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            bake();
        }
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfig.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            bake();
        }
    }

    private static void bake() {
        Set<ResourceLocation> workstations = new HashSet<>();
        for (String raw : ALLOWED_WORKSTATIONS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (id != null) {
                workstations.add(id);
            } else {
                LOGGER.warn("[{}] Ignoring invalid workstation ContainerType ID in config: '{}'", Cherubmod.MOD_ID, raw);
            }
        }
        allowedWorkstationIds = Collections.unmodifiableSet(workstations);

        Set<ResourceLocation> vItems = new HashSet<>();
        Set<ResourceLocation> vTags = new HashSet<>();
        parseItemsAndTags(VALID_POSSESSIONS.get(), vItems, vTags, "validPossessions");
        validItemIds = Collections.unmodifiableSet(vItems);
        validTagIds = Collections.unmodifiableSet(vTags);

        Set<ResourceLocation> iItems = new HashSet<>();
        Set<ResourceLocation> iTags = new HashSet<>();
        parseItemsAndTags(INVALID_POSSESSIONS.get(), iItems, iTags, "invalidPossessions");
        invalidItemIds = Collections.unmodifiableSet(iItems);
        invalidTagIds = Collections.unmodifiableSet(iTags);
    }

    private static void parseItemsAndTags(
            List<? extends String> entries,
            Set<ResourceLocation> itemOut,
            Set<ResourceLocation> tagOut,
            String listName
    ) {
        for (String raw : entries) {
            String trimmed = raw.trim();
            boolean isTag = trimmed.startsWith("#");
            String path = isTag ? trimmed.substring(1).trim() : trimmed;
            ResourceLocation id = ResourceLocation.tryParse(path);

            if (id == null) {
                LOGGER.warn("[{}] Ignoring malformed entry in {}: '{}'", Cherubmod.MOD_ID, listName, raw);
                continue;
            }
            if (isTag) {
                tagOut.add(id);
            } else {
                itemOut.add(id);
            }
        }
    }

    public static boolean isAllowedWorkstation(@Nullable ResourceLocation containerTypeId) {
        return containerTypeId != null && allowedWorkstationIds.contains(containerTypeId);
    }

    public static boolean isValidPossession(Item item) {
        return matchesConfigList(item, validItemIds, validTagIds);
    }

    public static boolean isInvalidPossession(Item item) {
        return matchesConfigList(item, invalidItemIds, invalidTagIds);
    }

    private static boolean matchesConfigList(
            Item item,
            Set<ResourceLocation> itemIds,
            Set<ResourceLocation> tagIds
    ) {
        ResourceLocation registryName = item.getRegistryName();
        if (registryName != null && itemIds.contains(registryName)) {
            return true;
        }
        if (tagIds.isEmpty()) {
            return false;
        }
        ITagCollection<Item> allTags = ItemTags.getAllTags();
        for (ResourceLocation tagId : tagIds) {
            ITag<Item> tag = allTags.getTag(tagId);
            if (tag != null && tag.contains(item)) {
                return true;
            }
        }
        return false;
    }
}