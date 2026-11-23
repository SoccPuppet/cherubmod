package org.lullaby.cherubmod.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CherubDamageEvents {

    // Exact msgId matches (lowercased)
    private static final Set<String> ELECTRIC_MSG_IDS = new HashSet<>(Arrays.asList(
            "lightningbolt",
            "lightning_bolt",
            "createaddition.tesla_coil",
            "tesla_coil",
            "ars_elemental.spark",
            "ars_elemental.shock"
    ));

    // Keyword matches for DamageSource msgIds and direct entity registry paths
    private static final String[] ELECTRIC_KEYWORDS = {
            "lightning",
            "electric",
            "electro",
            "shock",
            "tesla",
            "spark",
            "volt",
            "thunder",
            "discharge"
    };

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntityLiving() instanceof PlayerEntity)) {
            return;
        }
        PlayerEntity player = (PlayerEntity) event.getEntityLiving();
        if (player.level.isClientSide) {
            return;
        }

        if (!isElectricDamage(event.getSource())) {
            return;
        }

        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            if (data.getRace() == Race.CHERUB) {
                event.setAmount(event.getAmount() * 2.0F);
            }
        });
    }

    public static boolean isElectricDamage(DamageSource source) {
        if (source == null) {
            return false;
        }

        // 1. Vanilla LightningBolt reference (msgId is "lightningBolt" in 1.16.5)
        if (source == DamageSource.LIGHTNING_BOLT) {
            return true;
        }

        // 2. Check DamageSource msgId (covers lightning_bolt, Create Crafts & Additions, Ars Elemental, etc.)
        String msgId = source.getMsgId();
        if (!msgId.isEmpty()) {
            String lower = msgId.toLowerCase(Locale.ROOT);
            if (ELECTRIC_MSG_IDS.contains(lower) || containsElectricKeyword(lower)) {
                return true;
            }
        }

        // 3. Check direct entity (catches custom lightning bolts or electric projectiles using generic "magic"/"mob" damage)
        Entity direct = source.getDirectEntity();
        if (direct instanceof LightningBoltEntity) {
            return true;
        }
        if (direct != null) {
            ResourceLocation entityId = direct.getType().getRegistryName();
            return entityId != null && containsElectricKeyword(entityId.getPath().toLowerCase(Locale.ROOT));
        }

        return false;
    }

    private static boolean containsElectricKeyword(String value) {
        for (String keyword : ELECTRIC_KEYWORDS) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}