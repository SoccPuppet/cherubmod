package org.lullaby.cherubmod.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.event.WorldAbilityEvents;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WorldClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientWorld world = mc.level;
        if (world == null || mc.isPaused()) {
            return;
        }

        for (PlayerEntity player : world.players()) {
            IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
            if (data == null
                    || data.getRace() != Race.CHERUB
                    || data.getArchangel() != Archangel.THE_WORLD) {
                continue;
            }

            int stasis = data.getWorldStasisTicks();
            if (stasis > 0) {
                if (player == mc.player) {
                    player.setDeltaMovement(Vector3d.ZERO);
                }
                if ((player.tickCount & 1) == 0) {
                    spawnStasisRingParticles(world, player);
                }
                if (stasis > 1) {
                    data.setWorldStasisTicks(stasis - 1);
                }
            } else {
                int cooldown = data.getWorldStasisCooldownTicks();
                if (cooldown > 1) {
                    data.setWorldStasisCooldownTicks(cooldown - 1);
                }
            }
        }
    }

    private static void spawnStasisRingParticles(ClientWorld world, PlayerEntity player) {
        double angle = (player.tickCount * 0.35D) % (Math.PI * 2.0D);
        double radius = 0.75D;
        double y = player.getY() + player.getBbHeight() * 0.55D;

        for (int i = 0; i < 2; i++) {
            double theta = angle + i * Math.PI;
            double px = player.getX() + Math.cos(theta) * radius;
            double pz = player.getZ() + Math.sin(theta) * radius;
            world.addParticle(ParticleTypes.END_ROD, px, y, pz, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Zero local movement input during stasis so the player can look around without rubberbanding. */
    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        if (!WorldAbilityEvents.isInWorldStasis(event.getPlayer())) {
            return;
        }
        MovementInput input = event.getMovementInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** Prevent arm-swinging, left-clicking, and right-clicking while frozen in stasis. */
    @SubscribeEvent
    public static void onClickInput(InputEvent.ClickInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && WorldAbilityEvents.isInWorldStasis(mc.player)) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Subtle FOV focus while stepped out of time. */
    @SubscribeEvent
    public static void onFOVUpdate(FOVUpdateEvent event) {
        if (WorldAbilityEvents.isInWorldStasis(event.getEntity())) {
            event.setNewfov(event.getFov() * 0.92F);
        }
    }

    /** Subtle translucent tint in first-person while stepped out of time. */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !WorldAbilityEvents.isInWorldStasis(mc.player)) {
            return;
        }
        MatrixStack matrixStack = event.getMatrixStack();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        AbstractGui.fill(matrixStack, 0, 0, width, height, 0x2088C8FF);
    }
}