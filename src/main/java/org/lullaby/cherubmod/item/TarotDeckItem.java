package org.lullaby.cherubmod.item;

import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.client.ClientScreenOpener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class TarotDeckItem extends Item {

    public TarotDeckItem(Properties properties) {
        super(properties);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable World world,
                                @Nonnull List<ITextComponent> tooltip, @Nonnull ITooltipFlag flag) {
        super.appendHoverText(stack, world, tooltip, flag);
        String translated = new TranslationTextComponent("tooltip.cherubmod.tarot_deck").getString();
        for (String line : translated.split("\\r?\\n")) {
            tooltip.add(new StringTextComponent(line));
        }
    }

    @MethodsReturnNonnullByDefault
    @Override
    public ActionResult<ItemStack> use(World world, PlayerEntity player, @Nonnull Hand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (world.isClientSide) {
            boolean isCherub = player.getCapability(RaceCapability.INSTANCE)
                    .map(data -> data.getRace() == Race.CHERUB)
                    .orElse(false);

            if (isCherub) {
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientScreenOpener::openTarotDeckScreen);
            } else {
                // Client-only chat message (actionBar = false prints to chat)
                player.displayClientMessage(
                        new TranslationTextComponent("message.cherubmod.tarot.not_cherub")
                                .withStyle(TextFormatting.RED),
                        false
                );
            }
        }

        return ActionResult.sidedSuccess(stack, world.isClientSide);
    }
}