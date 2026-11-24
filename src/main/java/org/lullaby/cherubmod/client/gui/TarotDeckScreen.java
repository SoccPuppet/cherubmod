package org.lullaby.cherubmod.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lullaby.cherubmod.capability.IRaceData;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.network.CherubNetwork;
import org.lullaby.cherubmod.network.SelectArchangelPacket;
import org.lullaby.cherubmod.tarot.TarotArcana;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class TarotDeckScreen extends Screen {
    private static final int GRID_COLS = 5;
    private static final int GRID_ROWS = 5;
    private static final float CARD_ASPECT = 0.68F; // width / height portrait ratio
    private static final int SCROLLBAR_WIDTH = 5;
    private static final float TITLE_SCALE = 2F;

    private static final int COLOR_FRAME = 0xFFFFFFFF;
    private static final int COLOR_INNER_BG = 0xFFECECEC;
    private static final int COLOR_TEXTBOX_BG = 0xFF000000;
    private static final int COLOR_CARD_BORDER = 0xFF1A1A1A;
    private static final int COLOR_CARD_HOVER = 0xFFFFFFFF;
    private static final int COLOR_CARD_SELECTED = 0xFFFFD000;
    private static final int COLOR_TITLE_GOLD = 0xFFFFCC00;
    private static final int COLOR_BODY_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BODY_MUTED = 0xFF999999;

    // Semantic power-line colors
    private static final int COLOR_UPSIDE   = 0xFF6EEB83; // Soft green
    private static final int COLOR_DOWNSIDE = 0xFFFF6B6B; // Soft red
    private static final int COLOR_TWEAK    = 0xFFFFE066; // Soft amber/gold

    // Layout coordinates (recomputed on init / resize / GUI scale change)
    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, cardW, cardH, cardGap;
    private int textBoxX, textBoxY, textBoxW, textBoxH;
    private int bodyClipX, bodyClipY, bodyClipW, bodyClipH;

    private Button acceptButton;

    @Nullable
    private TarotArcana selectedArcana;
    private boolean initializedSelection = false;

    // Wrapped text caches
    private final List<IReorderingProcessor> wrappedTitle = new ArrayList<>();
    private final List<BodyLine> wrappedBody = new ArrayList<>();
    private int totalBodyHeight = 0;
    private double scrollOffset = 0.0D;
    private boolean draggingScrollbar = false;

    public TarotDeckScreen() {
        super(new TranslationTextComponent("gui.cherubmod.tarot.title"));
    }

    @Override
    protected void init() {
        super.init();

        // Pre-select the player's current Archangel on first open
        if (!initializedSelection) {
            initializedSelection = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Archangel current = mc.player.getCapability(RaceCapability.INSTANCE)
                        .map(IRaceData::getArchangel)
                        .orElse(Archangel.NONE);
                this.selectedArcana = TarotArcana.fromArchangel(current);
            }
        }

        computeLayout();

        // Place Cancel and Accept across columns 1..4 of row 4 (next to the 21st card)
        int btnAreaX = gridX + cardW + cardGap;
        int btnAreaW = (GRID_COLS - 1) * cardW + (GRID_COLS - 2) * cardGap;
        int btnGap = Math.max(4, Math.min(10, btnAreaW / 18));
        int btnW = Math.max(36, (btnAreaW - btnGap) / 2);
        int btnH = Math.min(20, Math.max(14, cardH - 2));
        int row4Y = gridY + (GRID_ROWS - 1) * (cardH + cardGap);
        int btnY = row4Y + (cardH - btnH) / 2;

        // The cancel button is added here
        this.addButton(new Button(
                btnAreaX,
                btnY,
                btnW,
                btnH,
                new TranslationTextComponent("gui.cherubmod.tarot.cancel"),
                btn -> this.onClose()
        ));

        this.acceptButton = this.addButton(new Button(
                btnAreaX + btnW + btnGap,
                btnY,
                btnAreaW - btnW - btnGap,
                btnH,
                new TranslationTextComponent("gui.cherubmod.tarot.accept"),
                btn -> onAcceptPressed()
        ));

        rebuildTextAndButtons();
    }

    private void computeLayout() {
        int marginX = Math.max(4, this.width / 24);
        int marginY = Math.max(4, this.height / 24);
        int availW = Math.max(1, this.width - marginX * 2);
        int availH = Math.max(1, this.height - marginY * 2);

        // Cap maximum size on very small GUI scales (e.g. Scale 1 on 1440p/4K),
        // while filling cleanly on large GUI scales (Scale 3/4).
        this.panelH = Math.min(availH, 420);
        int preferredW = Math.round(this.panelH * 1.65F);
        this.panelW = Math.min(availW, Math.max(Math.min(availW, 240), preferredW));

        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int pad = Math.max(4, Math.min(10, this.panelH / 34));
        int innerX = this.panelX + pad;
        int innerY = this.panelY + pad;
        int innerW = this.panelW - pad * 2;
        int innerH = this.panelH - pad * 2;

        this.cardGap = Math.max(2, Math.min(4, pad / 2));

        // Fit 5 rows of cards vertically first
        int maxCardH = (innerH - cardGap * (GRID_ROWS - 1)) / GRID_ROWS;
        int candidateW = Math.max(12, Math.round(maxCardH * CARD_ASPECT));
        int candidateGridW = GRID_COLS * candidateW + (GRID_COLS - 1) * cardGap;

        // Guarantee at least 42% of inner width remains for the description text box
        int maxLeftW = Math.round(innerW * 0.56F);
        if (candidateGridW > maxLeftW) {
            this.cardW = Math.max(12, (maxLeftW - (GRID_COLS - 1) * cardGap) / GRID_COLS);
            this.cardH = Math.min(maxCardH, Math.max(16, Math.round(this.cardW / CARD_ASPECT)));
        } else {
            this.cardW = candidateW;
            this.cardH = maxCardH;
        }

        int totalGridW = GRID_COLS * this.cardW + (GRID_COLS - 1) * this.cardGap;
        int totalGridH = GRID_ROWS * this.cardH + (GRID_ROWS - 1) * this.cardGap;

        this.gridX = innerX;
        this.gridY = innerY + (innerH - totalGridH) / 2;

        this.textBoxX = innerX + totalGridW + pad;
        this.textBoxY = this.gridY;
        this.textBoxW = Math.max(80, (innerX + innerW) - this.textBoxX);
        this.textBoxH = totalGridH;
    }

    private void selectArcana(TarotArcana arcana) {
        this.selectedArcana = arcana;
        this.scrollOffset = 0.0D;
        rebuildTextAndButtons();
    }

    private void rebuildTextAndButtons() {
        this.wrappedTitle.clear();
        this.wrappedBody.clear();

        int textPad = Math.max(5, Math.min(10, this.textBoxW / 24));
        int titleWrapW = Math.max(30, (int) Math.floor((this.textBoxW - textPad * 2) / TITLE_SCALE));
        // Reserve room on the right for the scrollbar
        int bodyWrapW = Math.max(40, this.textBoxW - textPad * 2 - SCROLLBAR_WIDTH - 4);

        if (this.selectedArcana == null) {
            this.acceptButton.active = false;
            ITextComponent prompt = new TranslationTextComponent("gui.cherubmod.tarot.select_prompt")
                    .withStyle(TextFormatting.BOLD);
            this.wrappedTitle.addAll(this.font.split(prompt, titleWrapW));
        } else {
            ITextComponent titleComp = new TranslationTextComponent(this.selectedArcana.getNameKey())
                    .withStyle(TextFormatting.BOLD);
            this.wrappedTitle.addAll(this.font.split(titleComp, titleWrapW));

            if (!this.selectedArcana.isImplemented()) {
                this.acceptButton.active = false;
                ITextComponent unimpl = new TranslationTextComponent("gui.cherubmod.tarot.unimplemented");
                for (IReorderingProcessor line : this.font.split(unimpl, bodyWrapW)) {
                    this.wrappedBody.add(new BodyLine(line, COLOR_BODY_MUTED, false));
                }
            } else {
                this.acceptButton.active = true;
                int powerIdx = 1;
                while (I18n.exists(this.selectedArcana.getPowerKey(powerIdx))) {
                    if (powerIdx > 1) {
                        this.wrappedBody.add(BodyLine.spacer());
                    }
                    appendPowerParagraph(I18n.get(this.selectedArcana.getPowerKey(powerIdx)), bodyWrapW);
                    powerIdx++;
                }

                // Fallback if an Archangel exists in capability.Archangel but has no .1 entry yet
                if (powerIdx == 1) {
                    ITextComponent noDesc = new TranslationTextComponent("gui.cherubmod.tarot.no_description");
                    for (IReorderingProcessor line : this.font.split(noDesc, bodyWrapW)) {
                        this.wrappedBody.add(new BodyLine(line, COLOR_BODY_MUTED, false));
                    }
                }
            }
        }

        int lineStep = this.font.lineHeight + 2;
        int titleLineStep = MathHelper.ceil((this.font.lineHeight + 2) * TITLE_SCALE);
        int titleBlockHeight = textPad + (this.wrappedTitle.size() * titleLineStep) + 6;

        this.bodyClipX = this.textBoxX + textPad;
        this.bodyClipY = this.textBoxY + titleBlockHeight;
        this.bodyClipW = Math.max(20, this.textBoxW - textPad * 2 - SCROLLBAR_WIDTH - 2);
        this.bodyClipH = Math.max(10, this.textBoxH - titleBlockHeight - textPad);

        int heightSum = 0;
        for (BodyLine line : this.wrappedBody) {
            heightSum += line.isSpacer ? (this.font.lineHeight / 2 + 2) : lineStep;
        }
        this.totalBodyHeight = heightSum;
        clampScroll();
    }

    private double getMaxScroll() {
        return Math.max(0.0D, this.totalBodyHeight - this.bodyClipH);
    }

    private void clampScroll() {
        this.scrollOffset = MathHelper.clamp(this.scrollOffset, 0.0D, getMaxScroll());
    }

    private void onAcceptPressed() {
        if (this.selectedArcana == null) {
            return;
        }
        Archangel chosen = this.selectedArcana.resolveArchangel();
        if (chosen == null || chosen == Archangel.NONE) {
            return;
        }
        CherubNetwork.CHANNEL.sendToServer(new SelectArchangelPacket(chosen));
        this.onClose();
    }

    private void appendPowerParagraph(String raw, int bodyWrapW) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        int color = COLOR_BODY_WHITE;
        String formatted;

        if (trimmed.startsWith("[+]")) {
            color = COLOR_UPSIDE;
            formatted = "+ " + trimmed.substring(3).trim();
        } else if (trimmed.startsWith("[-]")) {
            color = COLOR_DOWNSIDE;
            formatted = "- " + trimmed.substring(3).trim();
        } else if (trimmed.startsWith("[~]")) {
            color = COLOR_TWEAK;
            formatted = "~ " + trimmed.substring(3).trim();
        } else {
            formatted = trimmed.startsWith("* ") ? trimmed : "* " + trimmed;
        }

        for (IReorderingProcessor line : this.font.split(new StringTextComponent(formatted), bodyWrapW)) {
            this.wrappedBody.add(new BodyLine(line, color, false));
        }
    }

    @Override
    public void onClose(){
        this.draggingScrollbar = false;
        super.onClose();
    }

    @MethodsReturnNonnullByDefault
    @Override
    public void render(@Nonnull MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);

        // 1. Outer white frame & inner panel background
        fill(matrixStack, panelX, panelY, panelX + panelW, panelY + panelH, COLOR_FRAME);
        fill(matrixStack, panelX + 2, panelY + 2, panelX + panelW - 2, panelY + panelH - 2, COLOR_INNER_BG);

        // 2. Render the Major Arcana cards
        TarotArcana hoveredCard = renderCardGrid(matrixStack, mouseX, mouseY);

        // 3. Render right-side description box (title + scrollable wrapped body)
        renderDescriptionBox(matrixStack);

        // 4. Render Cancel and Accept buttons
        super.render(matrixStack, mouseX, mouseY, partialTicks);

        // 5. Tooltip on hovered card so cards remain readable at any UI scale
        if (hoveredCard != null) {
            this.renderTooltip(matrixStack, new TranslationTextComponent(hoveredCard.getNameKey()), mouseX, mouseY);
        }
    }

    private static void blitFullTexture(MatrixStack matrixStack, int x, int y, int width, int height) {
        Matrix4f matrix = matrixStack.last().pose();
        BufferBuilder buffer = Tessellator.getInstance().getBuilder();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR_TEX);
        buffer.vertex(matrix, x, y + height, 0.0F).color(255, 255, 255, 255).uv(0.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x + width, y + height, 0.0F).color(255, 255, 255, 255).uv(1.0F, 1.0F).endVertex();
        buffer.vertex(matrix, x + width, y, 0.0F).color(255, 255, 255, 255).uv(1.0F, 0.0F).endVertex();
        buffer.vertex(matrix, x, y, 0.0F).color(255, 255, 255, 255).uv(0.0F, 0.0F).endVertex();
        buffer.end();
        WorldVertexBufferUploader.end(buffer);
    }
    @Nullable
    private TarotArcana renderCardGrid(MatrixStack matrixStack, int mouseX, int mouseY) {
        TarotArcana[] cards = TarotArcana.values();
        TarotArcana hovered = null;

        for (int i = 0; i < cards.length; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int cx = gridX + col * (cardW + cardGap);
            int cy = gridY + row * (cardH + cardGap);

            boolean isHovered = mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + cardH;
            boolean isSelected = cards[i] == this.selectedArcana;
            if (isHovered) {
                hovered = cards[i];
            }

            // Card border / background
            int borderColor = isSelected ? COLOR_CARD_SELECTED : (isHovered ? COLOR_CARD_HOVER : COLOR_CARD_BORDER);
            fill(matrixStack, cx - 1, cy - 1, cx + cardW + 1, cy + cardH + 1, borderColor);
            fill(matrixStack, cx, cy, cx + cardW, cy + cardH, 0xFFBBBBBB);

            // Card art texture (maps entire PNG regardless of resolution)
            // If this assert fires I will be very upset
            assert this.minecraft != null;
            this.minecraft.getTextureManager().bind(cards[i].getTexture());
            RenderSystem.enableTexture();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            blitFullTexture(matrixStack, cx, cy, cardW, cardH);
            RenderSystem.disableBlend();

            // Extra inner highlight ring when selected
            if (isSelected) {
                drawHollowRect(matrixStack, cx, cy, cardW, cardH, COLOR_CARD_SELECTED);
            }
        }

        return hovered;
    }

    private void renderDescriptionBox(MatrixStack matrixStack) {
        fill(matrixStack, textBoxX, textBoxY, textBoxX + textBoxW, textBoxY + textBoxH, COLOR_TEXTBOX_BG);
        drawHollowRect(matrixStack, textBoxX, textBoxY, textBoxW, textBoxH, COLOR_CARD_BORDER);

        int textPad = Math.max(5, Math.min(10, this.textBoxW / 24));
        int lineStep = this.font.lineHeight + 2;

        // Centered gold title at the top of the box
        int titleY = this.textBoxY + textPad;
        int titleLineStep = MathHelper.ceil((this.font.lineHeight + 2) * TITLE_SCALE);
        for (IReorderingProcessor titleLine : this.wrappedTitle) {
            float scaledWidth = this.font.width(titleLine) * TITLE_SCALE;
            float tx = this.textBoxX + (this.textBoxW - scaledWidth) / 2.0F;

            matrixStack.pushPose();
            matrixStack.translate(tx, titleY, 0.0D);
            matrixStack.scale(TITLE_SCALE, TITLE_SCALE, 1.0F);
            this.font.drawShadow(matrixStack, titleLine, 0.0F, 0.0F, COLOR_TITLE_GOLD);
            matrixStack.popPose();

            titleY += titleLineStep;
        }

        // Scissor-clipped scrollable body area (scaled accurately to framebuffer pixels)
        // If this assert fires I will also be very upset
        assert this.minecraft != null;
        double guiScale = this.minecraft.getWindow().getGuiScale();
        int fbWidth = this.minecraft.getWindow().getWidth();
        int fbHeight = this.minecraft.getWindow().getHeight();

        int rawX = (int) Math.floor(this.bodyClipX * guiScale);
        int rawY = (int) Math.floor(fbHeight - (this.bodyClipY + this.bodyClipH) * guiScale);
        int rawRight = (int) Math.ceil((this.bodyClipX + this.bodyClipW) * guiScale);
        int rawTop = (int) Math.ceil(fbHeight - this.bodyClipY * guiScale);

        int scX = MathHelper.clamp(rawX, 0, fbWidth);
        int scY = MathHelper.clamp(rawY, 0, fbHeight);
        int scW = Math.max(0, MathHelper.clamp(rawRight, 0, fbWidth) - scX);
        int scH = Math.max(0, MathHelper.clamp(rawTop, 0, fbHeight) - scY);

        if (scW > 0 && scH > 0) {
            RenderSystem.enableScissor(scX, scY, scW, scH);
            try {
                int drawY = this.bodyClipY - (int) Math.round(this.scrollOffset);
                for (BodyLine line : this.wrappedBody) {
                    int step = line.isSpacer ? (this.font.lineHeight / 2 + 2) : lineStep;
                    if (!line.isSpacer && drawY + this.font.lineHeight >= this.bodyClipY && drawY <= this.bodyClipY + this.bodyClipH) {
                        this.font.draw(matrixStack, line.text, this.bodyClipX, drawY, line.color);
                    }
                    drawY += step;
                }
            } finally {
                RenderSystem.disableScissor();
            }
        }

        // Scrollbar (only shown when text exceeds visible area)
        double maxScroll = getMaxScroll();
        if (maxScroll > 0.0D) {
            int barX = getScrollbarX();
            int barTop = this.bodyClipY;
            int barH = this.bodyClipH;

            fill(matrixStack, barX, barTop, barX + SCROLLBAR_WIDTH, barTop + barH, 0xFF2B2B2B);

            int thumbH = Math.max(14, (int) Math.round((barH * (double) barH) / this.totalBodyHeight));
            int thumbY = barTop + (int) Math.round((this.scrollOffset / maxScroll) * (barH - thumbH));
            fill(matrixStack, barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    private int getScrollbarX() {
        return this.textBoxX + this.textBoxW - SCROLLBAR_WIDTH - 3;
    }

    private void drawHollowRect(MatrixStack matrixStack, int x, int y, int w, int h, int color) {
        fill(matrixStack, x, y, x + w, y + 1, color);
        fill(matrixStack, x, y + h - 1, x + w, y + h, color);
        fill(matrixStack, x, y, x + 1, y + h, color);
        fill(matrixStack, x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        // Check scrollbar click/drag
        if (getMaxScroll() > 0.0D) {
            int barX = getScrollbarX();
            if (mouseX >= barX - 2 && mouseX <= barX + SCROLLBAR_WIDTH + 2
                    && mouseY >= this.bodyClipY && mouseY <= this.bodyClipY + this.bodyClipH) {
                this.draggingScrollbar = true;
                updateScrollFromMouseY(mouseY);
                return true;
            }
        }

        // Check card clicks
        TarotArcana[] cards = TarotArcana.values();
        for (int i = 0; i < cards.length; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int cx = gridX + col * (cardW + cardGap);
            int cy = gridY + row * (cardH + cardGap);

            if (mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + cardH) {
                Minecraft.getInstance().getSoundManager().play(SimpleSound.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                selectArcana(cards[i]);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar && button == 0) {
            // I'm putting this everywhere so my IDE shuts up but goodness me
            assert this.minecraft != null;
            if (GLFW.glfwGetMouseButton(this.minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                this.draggingScrollbar = false;
                return false;
            }
            updateScrollFromMouseY(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= this.textBoxX && mouseX <= this.textBoxX + this.textBoxW
                && mouseY >= this.textBoxY && mouseY <= this.textBoxY + this.textBoxH) {
            this.scrollOffset -= delta * 14.0D;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void updateScrollFromMouseY(double mouseY) {
        double maxScroll = getMaxScroll();
        if (maxScroll <= 0.0D) {
            return;
        }
        int barH = this.bodyClipH;
        int thumbH = Math.max(14, (int) Math.round((barH * (double) barH) / this.totalBodyHeight));
        int trackRange = Math.max(1, barH - thumbH);
        double rel = (mouseY - this.bodyClipY - thumbH / 2.0D) / trackRange;
        this.scrollOffset = MathHelper.clamp(rel, 0.0D, 1.0D) * maxScroll;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class BodyLine {
        final IReorderingProcessor text;
        final int color;
        final boolean isSpacer;

        BodyLine(IReorderingProcessor text, int color, boolean isSpacer) {
            this.text = text;
            this.color = color;
            this.isSpacer = isSpacer;
        }

        static BodyLine spacer() {
            return new BodyLine(IReorderingProcessor.EMPTY, 0, true);
        }
    }
}