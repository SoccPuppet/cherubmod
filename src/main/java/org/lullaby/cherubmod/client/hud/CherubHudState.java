package org.lullaby.cherubmod.client.hud;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.Color;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

import java.util.function.UnaryOperator;

public final class CherubHudState {
    // Sentinel value telling the renderer to compute vanilla's HSV durability color.
    public static final int VANILLA_DURABILITY_COLOR = -1;

    private final ITextComponent text;
    private final int defaultTextColor;
    private final boolean textShadow;
    private final boolean showBar;
    private final float barProgress;
    private final int barColor;
    private final boolean showActiveKeybind;

    private CherubHudState(Builder builder) {
        this.text = builder.text;
        this.defaultTextColor = builder.defaultTextColor;
        this.textShadow = builder.textShadow;
        this.showBar = builder.showBar;
        this.barProgress = builder.barProgress;
        this.barColor = builder.barColor;
        this.showActiveKeybind = builder.showActiveKeybind;
    }

    public boolean shouldShowActiveKeybind() {
        return showActiveKeybind;
    }

    public ITextComponent getText() {
        return text;
    }

    public int getDefaultTextColor() {
        return defaultTextColor;
    }

    public boolean hasTextShadow() {
        return textShadow;
    }

    public boolean shouldShowBar() {
        return showBar;
    }

    // Clamped in [0.0, 1.0]
    public float getBarProgress() {
        return barProgress;
    }

    // RGB int (0xRRGGBB)
    public int getBarColor() {
        return barColor;
    }

    public static Builder builder(String rawText) {
        return new Builder(new StringTextComponent(rawText));
    }

    public static final class Builder {
        private IFormattableTextComponent text;
        private final int defaultTextColor = 0xFFFFFF;
        private boolean textShadow = true;
        private boolean showBar = false;
        private float barProgress = 0.0F;
        private int barColor = VANILLA_DURABILITY_COLOR;
        private boolean showActiveKeybind = false;

        public Builder showActiveKeybind() {
            return showActiveKeybind(true);
        }

        public Builder showActiveKeybind(boolean show) {
            this.showActiveKeybind = show;
            return this;
        }

        private Builder(IFormattableTextComponent initialText) {
            this.text = initialText;
        }

        /** Replace the displayed text completely (resets prior style unless applied after). */
        public Builder text(String rawText) {
            this.text = new StringTextComponent(rawText);
            return this;
        }

        /** Replace the displayed text with a pre-built component. */
        public Builder text(IFormattableTextComponent component) {
            this.text = component.copy();
            return this;
        }

        /** Apply one or more vanilla TextFormatting codes (e.g. GOLD, BOLD, ITALIC). */
        public Builder style(TextFormatting... formattings) {
            this.text.withStyle(formattings);
            return this;
        }

        /** Mutate the text's Style directly (useful for arbitrary RGB colors or custom flags). */
        public Builder style(UnaryOperator<Style> styleUpdater) {
            this.text.withStyle(styleUpdater);
            return this;
        }

        /** Convenience helper for arbitrary 24-bit RGB text colors (e.g. 0xFFE87A). */
        public Builder textColor(int rgb) {
            this.text.withStyle(s -> s.withColor(Color.fromRgb(rgb & 0xFFFFFF)));
            return this;
        }

        public Builder textShadow(boolean shadow) {
            this.textShadow = shadow;
            return this;
        }

        /** Show a durability bar using vanilla's red -> yellow -> green gradient. */
        public Builder bar(float progress) {
            return bar(progress, VANILLA_DURABILITY_COLOR);
        }

        /** Show a durability bar with a custom 24-bit RGB fill color (e.g. 0xFFAA00). */
        public Builder bar(float progress, int rgbColor) {
            this.showBar = true;
            this.barProgress = MathHelper.clamp(progress, 0.0F, 1.0F);
            this.barColor = rgbColor;
            return this;
        }

        /** Explicitly hide the bar (useful for modifiers overriding an earlier provider). */
        public Builder hideBar() {
            this.showBar = false;
            return this;
        }

        public CherubHudState build() {
            return new CherubHudState(this);
        }
    }
}