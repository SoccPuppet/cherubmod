package org.lullaby.cherubmod.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.item.SewnNeedleItem;
import org.lullaby.cherubmod.item.VapeItem;
import org.lullaby.cherubmod.network.CherubNetwork;
import org.lullaby.cherubmod.network.TogglePossessionPacket;
import org.lullaby.cherubmod.network.TriggerActiveAbilityPacket;
import org.lullaby.cherubmod.possession.PossessionHelper;

import java.util.List;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    private static void tickPlushStuffedTimer(Minecraft mc) {
        if (mc.isPaused()) {
            return;
        }
        PlayerEntity player = mc.player;
        if (player == null) {
            return;
        }

        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        if (data == null || data.getRace() != Race.PLUSH || data.getPlushStuffedStacks() <= 0) {
            return;
        }

        int ticks = data.getPlushStuffedTicks();
        if (ticks > 1) {
            data.setPlushStuffedTicks(ticks - 1);
        }
    }

    @SubscribeEvent
    public static void onFovUpdate(FOVUpdateEvent event) {
        PlayerEntity player = event.getEntity();
        Item item = player.getUseItem().getItem();
        if (player.isUsingItem() && (item instanceof VapeItem || item instanceof SewnNeedleItem)) {
            float progress = MathHelper.clamp(
                    (float) player.getTicksUsingItem() / (float) VapeItem.CHANNEL_TICKS,
                    0.0F,
                    1.0F
            );
            // Smoothly lowers FOV down to 65% of normal
            event.setNewfov(event.getFov() * (1.0F - 0.35F * progress));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.END){
            tickPlushStuffedTimer(Minecraft.getInstance());
        }
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
            return;
        }
        while (ClientSetup.SUMMON_POSSESSION.consumeClick()) {
            CherubNetwork.CHANNEL.sendToServer(new TogglePossessionPacket());
        }
        while (ClientSetup.ACTIVE_ABILITY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                CherubNetwork.CHANNEL.sendToServer(new TriggerActiveAbilityPacket());
            }
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!PossessionHelper.isPossessionStack(event.getItemStack())) {
            return;
        }

        List<ITextComponent> tooltip = event.getToolTip();
        ITextComponent line = new TranslationTextComponent("tooltip.cherubmod.possession")
                .withStyle(TextFormatting.GOLD, TextFormatting.BOLD);
        ITextComponent keyName = ClientSetup.SUMMON_POSSESSION.getTranslatedKeyMessage()
                .copy()
                .withStyle(TextFormatting.WHITE);
        ITextComponent hintLine = new TranslationTextComponent("tooltip.cherubmod.possession.hint", keyName)
                .withStyle(TextFormatting.GOLD);

        // Insert directly beneath the item's display name (index 0);
        int insertIndex = Math.min(1, tooltip.size());
        tooltip.add(insertIndex, line);
        tooltip.add(insertIndex + 1, hintLine);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        PlayerEntity player = event.getPlayer();
        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            List<ItemStack> hovering = data.getHoveringItems();
            if (hovering.isEmpty()) {
                return;
            }

            MatrixStack matrixStack = event.getMatrixStack();
            IRenderTypeBuffer buffer = event.getBuffers();
            int light = event.getLight();
            float time = player.tickCount + event.getPartialRenderTick();

            // Render the first (or stacked) hovering item(s) floating above the player's head
            for (int i = 0; i < hovering.size(); i++) {
                ItemStack stack = hovering.get(i);
                matrixStack.pushPose();
                float bob = MathHelper.sin((time + i * 10.0F) * 0.1F) * 0.08F;
                matrixStack.translate(0.0D, player.getBbHeight() + 0.55D + (i * 0.35D) + bob, 0.0D);
                matrixStack.mulPose(Vector3f.YP.rotationDegrees(time * 2.5F + i * 45.0F));
                matrixStack.scale(0.65F, 0.65F, 0.65F);

                Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack,
                        ItemCameraTransforms.TransformType.GROUND,
                        light,
                        OverlayTexture.NO_OVERLAY,
                        matrixStack,
                        buffer
                );
                matrixStack.popPose();
            }
        });
    }
}