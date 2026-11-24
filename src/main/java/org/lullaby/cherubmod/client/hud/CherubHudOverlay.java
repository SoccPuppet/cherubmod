package org.lullaby.cherubmod.client.hud;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.AttackIndicatorStatus;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.HandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lullaby.cherubmod.Cherubmod;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;

@Mod.EventBusSubscriber(modid = Cherubmod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CherubHudOverlay {
    private static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("textures/gui/widgets.png");
    private static final int WIDGETS_TEX_SIZE = 256;

    // Vanilla offhand slot UVs in textures/gui/widgets.png
    private static final int SLOT_U_LEFT = 24;
    private static final int SLOT_U_RIGHT = 53;
    private static final int SLOT_V = 22;
    private static final int SLOT_TEX_W = 29;
    private static final int SLOT_TEX_H = 24;

    // Inner 16x16 slot text bounds
    private static final float MAX_TEXT_WIDTH = 14.5F;

    private CherubHudOverlay() {}

    @SubscribeEvent
    public static void onRenderHotbarPost(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HOTBAR) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        PlayerEntity player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) {
            return;
        }

        IRaceData data = player.getCapability(RaceCapability.INSTANCE).orElse(null);
        if (data.getRace() != Race.CHERUB) {
            return;
        }

        CherubHudState state = CherubHudRegistry.resolve(player, data);
        MatrixStack matrixStack = event.getMatrixStack();
        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;

        // Vanilla offhand sits on mainArm.getOpposite(), so the mirrored slot sits on mainArm
        HandSide slotSide = player.getMainArm();
        boolean hotbarIndicatorOffset = mc.options.attackIndicator == AttackIndicatorStatus.HOTBAR;

        int frameX;
        int frameY = screenHeight - 23;
        int texU;
        int innerX;
        int innerY = screenHeight - 19;

        if (slotSide == HandSide.RIGHT) {
            frameX = centerX + 91 + (hotbarIndicatorOffset ? 22 : 0);
            texU = SLOT_U_RIGHT;
            innerX = frameX + 10; // 7px gap + 3px border
        } else {
            frameX = centerX - 91 - SLOT_TEX_W - (hotbarIndicatorOffset ? 22 : 0);
            texU = SLOT_U_LEFT;
            innerX = frameX + 3;  // 3px border
        }

        // 1. Draw vanilla offhand slot frame (mirrored)
        mc.getTextureManager().bind(WIDGETS_LOCATION);
        RenderSystem.enableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blitRegion(matrixStack, frameX, frameY, texU, SLOT_V, SLOT_TEX_W, SLOT_TEX_H, WIDGETS_TEX_SIZE, WIDGETS_TEX_SIZE);

        // 2. Draw centered, auto-scaled Roman numeral
        renderSlotText(matrixStack, mc.font, state, innerX, innerY);

        // 3. Draw optional durability-style progress bar
        if (state.shouldShowBar()) {
            renderDurabilityBar(matrixStack, innerX, innerY, state.getBarProgress(), state.getBarColor());
        }

        // Restore default HUD texture/blend state for downstream overlays
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        mc.getTextureManager().bind(AbstractGui.GUI_ICONS_LOCATION);
    }

    private static void renderSlotText(
            MatrixStack matrixStack,
            FontRenderer font,
            CherubHudState state,
            int innerX,
            int innerY
    ) {
        ITextComponent text = state.getText();
        int actualWidth = font.width(text);
        if (actualWidth <= 0) {
            return;
        }

        // Measure as if both bold and italic are active so style changes never resize the text
        ITextComponent sizingText = new StringTextComponent(text.getString())
                .withStyle(Style.EMPTY
                        .withFont(text.getStyle().getFont())
                        .withBold(true)
                        .withItalic(true));
        int sizingWidth = Math.max(actualWidth, font.width(sizingText));

        float scale = sizingWidth > MAX_TEXT_WIDTH ? (MAX_TEXT_WIDTH / sizingWidth) : 1.0F;
        float scaledWidth = actualWidth * scale;
        float scaledHeight = (font.lineHeight - 1) * scale;

        // Shift text slightly upward when a durability bar occupies the bottom 3px of the 16x16 slot
        float availableHeight = state.shouldShowBar() ? 13.0F : 16.0F;
        float drawX = innerX + (16.0F - scaledWidth) * 0.5F;
        float drawY = innerY + (availableHeight - scaledHeight) * 0.5F;

        matrixStack.pushPose();
        matrixStack.translate(drawX, drawY, 0.0D);
        if (scale < 1.0F) {
            matrixStack.scale(scale, scale, 1.0F);
        }
        if (state.hasTextShadow()) {
            font.drawShadow(matrixStack, text, 0.0F, 0.0F, state.getDefaultTextColor());
        } else {
            font.draw(matrixStack, text, 0.0F, 0.0F, state.getDefaultTextColor());
        }
        matrixStack.popPose();
    }

    private static void renderDurabilityBar(
            MatrixStack matrixStack,
            int innerX,
            int innerY,
            float progress,
            int customRgb
    ) {
        float clamped = MathHelper.clamp(progress, 0.0F, 1.0F);
        int barX = innerX + 2;
        int barY = innerY + 13;
        int fillWidth = MathHelper.clamp(Math.round(clamped * 13.0F), 0, 13);

        int rgb = customRgb == CherubHudState.VANILLA_DURABILITY_COLOR
                ? MathHelper.hsvToRgb(clamped / 3.0F, 1.0F, 1.0F)
                : (customRgb & 0xFFFFFF);

        // Matches vanilla ItemRenderer durability bar geometry (13x2 black backing, 1px fill)
        AbstractGui.fill(matrixStack, barX, barY, barX + 13, barY + 2, 0xFF000000);
        if (fillWidth > 0) {
            AbstractGui.fill(matrixStack, barX, barY, barX + fillWidth, barY + 1, 0xFF000000 | rgb);
        }
    }

    private static void blitRegion(
            MatrixStack matrixStack,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height,
            int texWidth,
            int texHeight
    ) {
        float u0 = u / (float) texWidth;
        float u1 = (u + width) / (float) texWidth;
        float v0 = v / (float) texHeight;
        float v1 = (v + height) / (float) texHeight;

        Matrix4f matrix = matrixStack.last().pose();
        BufferBuilder buffer = Tessellator.getInstance().getBuilder();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR_TEX);
        buffer.vertex(matrix, x, y + height, 0.0F).color(255, 255, 255, 255).uv(u0, v1).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0F).color(255, 255, 255, 255).uv(u1, v1).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0F).color(255, 255, 255, 255).uv(u1, v0).endVertex();
        buffer.vertex(matrix, x, y, 0.0F).color(255, 255, 255, 255).uv(u0, v0).endVertex();
        buffer.end();
        WorldVertexBufferUploader.end(buffer);
    }
}