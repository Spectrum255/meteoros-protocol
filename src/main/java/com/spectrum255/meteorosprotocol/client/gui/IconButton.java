package com.spectrum255.meteorosprotocol.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Supplier;

/**
 * Square vanilla button that shows a 16 x 16 icon instead of a caption.
 *
 * <p>The vanilla background is drawn by {@link Button#renderWidget} (so hover,
 * pressed and disabled states stay pixel-perfect); the icon is then blitted on
 * top and tinted grey while the button is disabled. The screen owns tooltip
 * rendering and reads {@link #tooltipLines()} while the button is hovered, which
 * keeps dynamic text (phase names, on/off state) live.
 */
public class IconButton extends Button {
    private static final int ICON_SIZE = 16;
    private static final int DISABLED_TINT = 0x80808080;

    private final Supplier<ResourceLocation> iconSupplier;
    private final Supplier<List<Component>> tooltipSupplier;

    public IconButton(int x, int y, int width, int height, ResourceLocation icon,
                      Supplier<List<Component>> tooltipSupplier, OnPress onPress) {
        this(x, y, width, height, () -> icon, tooltipSupplier, onPress);
    }

    public IconButton(int x, int y, int width, int height, Supplier<ResourceLocation> iconSupplier,
                      Supplier<List<Component>> tooltipSupplier, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.iconSupplier = iconSupplier;
        this.tooltipSupplier = tooltipSupplier;
    }

    public List<Component> tooltipLines() {
        return this.tooltipSupplier.get();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);

        ResourceLocation icon = this.iconSupplier.get();
        if (icon == null) {
            return;
        }
        int iconX = getX() + (getWidth() - ICON_SIZE) / 2;
        int iconY = getY() + (getHeight() - ICON_SIZE) / 2;
        graphics.blit(icon, iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        if (!this.active) {
            graphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, DISABLED_TINT);
        }
    }
}
