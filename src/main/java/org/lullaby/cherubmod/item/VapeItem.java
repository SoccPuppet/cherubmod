package org.lullaby.cherubmod.item;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.network.CherubNetwork;
import org.lullaby.cherubmod.possession.PossessionHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class VapeItem extends Item {
    public static final int CHANNEL_TICKS = 40;

    public static final DamageSource VAPE_ASCENSION_DAMAGE = new DamageSource("cherub_vape") {
        {
            bypassArmor();
            bypassMagic();
            bypassInvul();
        }
    };

    public VapeItem(Properties properties) {
        super(properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable World world,
                                @Nonnull List<ITextComponent> tooltip, @Nonnull ITooltipFlag flag) {
        super.appendHoverText(stack, world, tooltip, flag);
        String translated = new TranslationTextComponent("tooltip.cherubmod.vape").getString();
        for (String line : translated.split("\\r?\\n")) {
            tooltip.add(new StringTextComponent(line));
        }
    }

    @Override
    public int getUseDuration(@Nonnull ItemStack stack) {
        return CHANNEL_TICKS;
    }

    @MethodsReturnNonnullByDefault
    @Override
    public UseAction getUseAnimation(@Nonnull ItemStack stack) {
        return UseAction.BOW;
    }

    @MethodsReturnNonnullByDefault
    @Override
    public ActionResult<ItemStack> use(@Nonnull World world, @Nonnull PlayerEntity player, @Nonnull Hand hand) {
        // Only usable from the offhand
        if (hand != Hand.OFF_HAND) {
            return ActionResult.pass(player.getItemInHand(hand));
        }
        player.startUsingItem(hand);
        return ActionResult.consume(player.getItemInHand(hand));
    }

    @MethodsReturnNonnullByDefault
    @Override
    public ItemStack finishUsingItem(@Nonnull ItemStack stack, World world, @Nonnull LivingEntity entity) {
        if (!world.isClientSide && entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;

            player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
                if (data.getRace() == Race.CHERUB) {
                    PossessionHelper.designateMainHand(player);
                } else {
                    if(PossessionHelper.designateMainHand(player)) {
                    data.setPendingRaceChange(true);
                    CherubNetwork.syncToTrackingAndSelf(player);
                    player.hurt(VAPE_ASCENSION_DAMAGE, Float.MAX_VALUE);}
                }
            });
        }
        // Return stack unchanged so the item is not consumed
        return stack;
    }
}