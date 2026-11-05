package com.spectrum255.meteorosprotocol.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.spectrum255.meteorosprotocol.particle.FieldSample;
import com.spectrum255.meteorosprotocol.particle.SimpleBiomeField;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Client-only terrain survey map opened by the particle detector.
 *
 * <p>The map covers a 160 x 160 block area around the player, sampled as an
 * 80 x 80 grid of surface columns (one sample every two blocks) and rendered at
 * 2 x 2 pixels per sample. Terrain colours come from the top block's
 * {@link MapColor}, darkened or brightened by up to 12% based on the sample's
 * height. Sampling happens only when the screen opens, every 20 ticks, or
 * after the player moves more than 8 blocks; {@link #render} never samples the
 * world except for the single hovered column.</p>
 *
 * <p>The particle concentration overlay is opt-in (button or the O key) and
 * is drawn as a translucent layer on top of the terrain. With the overlay off
 * no colour scale is shown.</p>
 */
public class DetectorMapScreen extends Screen {
    private static final int MAP_SIZE = 160;
    private static final int SAMPLES = 80;
    /** Pixel size of one sampled block cell. */
    private static final int PIXELS_PER_BLOCK = 2;
    private static final int BLOCKS_PER_SAMPLE = 2;
    private static final int SAMPLE_INTERVAL = 20;
    private static final double MOVE_REBUILD_DISTANCE_SQ = 8.0 * 8.0;
    private static final int CHUNK_SIZE = 16;
    /** Coarse overlay grid: the map area is divided into 16 x 16 cells. */
    private static final int GRID = 16;
    private static final int CELL_BLOCKS = MAP_SIZE / GRID;
    private static final int OVERLAY_ALPHA = 0x60;
    private static final float MAX_LEGEND_DENSITY = 150.0F;
    private static final int UNLOADED_COLOR = 0xFF0B0B12;
    private static final int CHUNK_LINE_COLOR = 0x30FFFFFF;
    private static final int PLAYER_COLOR = 0xFFFFFFFF;

    private final int[] terrain = new int[SAMPLES * SAMPLES];
    private final int[] surfaceHeights = new int[SAMPLES * SAMPLES];
    private final float[] densities = new float[GRID * GRID];

    private BlockPos center;
    private boolean hasLevel;
    private boolean showField;
    private boolean fieldDirty = true;
    private int ticks;
    private int hoverWorldX = Integer.MIN_VALUE;
    private int hoverWorldZ = Integer.MIN_VALUE;
    private FieldSample hoverSample;
    private Button overlayButton;

    public DetectorMapScreen() {
        super(Component.translatable("gui.meteorosprotocol.detector_map.title"));
    }

    @Override
    protected void init() {
        this.overlayButton = this.addRenderableWidget(Button.builder(
                this.overlayLabel(),
                button -> this.toggleField()
        ).bounds(this.width / 2 - 90, this.height - 52, 180, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> this.onClose()
        ).bounds(this.width / 2 - 40, this.height - 28, 80, 20).build());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            this.hasLevel = false;
            return;
        }
        this.hasLevel = true;
        this.center = minecraft.player.blockPosition();
        this.rebuild(minecraft.level);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_O) {
            this.toggleField();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Component overlayLabel() {
        return Component.translatable(this.showField
                ? "gui.meteorosprotocol.detector_map.overlay_on"
                : "gui.meteorosprotocol.detector_map.overlay_off");
    }

    private void toggleField() {
        this.showField = !this.showField;
        if (this.showField && this.fieldDirty) {
            this.buildDensities(Minecraft.getInstance().level);
        }
        if (this.overlayButton != null) {
            this.overlayButton.setMessage(this.overlayLabel());
        }
    }

    private int originX() {
        return this.center.getX() - SAMPLES / 2 * BLOCKS_PER_SAMPLE;
    }

    private int originZ() {
        return this.center.getZ() - SAMPLES / 2 * BLOCKS_PER_SAMPLE;
    }

    /** Rebuilds terrain colours and surface heights for the whole map area. */
    private void rebuild(Level level) {
        if (this.center == null) {
            return;
        }
        int originX = this.originX();
        int originZ = this.originZ();
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;

        for (int sz = 0; sz < SAMPLES; sz++) {
            for (int sx = 0; sx < SAMPLES; sx++) {
                int index = sz * SAMPLES + sx;
                int worldX = originX + sx * BLOCKS_PER_SAMPLE;
                int worldZ = originZ + sz * BLOCKS_PER_SAMPLE;
                BlockPos column = new BlockPos(worldX, 0, worldZ);
                if (!level.hasChunkAt(column)) {
                    this.terrain[index] = UNLOADED_COLOR;
                    this.surfaceHeights[index] = Integer.MIN_VALUE;
                    continue;
                }
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
                int surfaceY = Mth.clamp(height - 1, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
                this.surfaceHeights[index] = surfaceY;
                minHeight = Math.min(minHeight, surfaceY);
                maxHeight = Math.max(maxHeight, surfaceY);
            }
        }

        for (int sz = 0; sz < SAMPLES; sz++) {
            for (int sx = 0; sx < SAMPLES; sx++) {
                int index = sz * SAMPLES + sx;
                int surfaceY = this.surfaceHeights[index];
                if (surfaceY == Integer.MIN_VALUE) {
                    continue;
                }
                BlockPos surface = new BlockPos(originX + sx * BLOCKS_PER_SAMPLE, surfaceY, originZ + sz * BLOCKS_PER_SAMPLE);
                BlockState state = level.getBlockState(surface);
                MapColor mapColor = state.getMapColor(level, surface);
                this.terrain[index] = shadeByHeight(mapColor.col, surfaceY, minHeight, maxHeight);
            }
        }

        this.fieldDirty = true;
        this.hoverSample = null;
        this.hoverWorldX = Integer.MIN_VALUE;
        this.hoverWorldZ = Integer.MIN_VALUE;
        if (this.showField) {
            this.buildDensities(level);
        }
    }

    /**
     * Returns the map colour scaled by a height factor in [0.88, 1.12], so
     * slopes and raised terrain read as lighter or darker than their
     * surroundings.
     */
    private static int shadeByHeight(int rgb, int height, int minHeight, int maxHeight) {
        float factor = 1.0F;
        if (maxHeight > minHeight) {
            float normalized = (height - minHeight) / (float) (maxHeight - minHeight);
            factor = 0.88F + 0.24F * normalized;
        }
        int r = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp(Math.round((rgb & 0xFF) * factor), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Samples total field density on the coarse 16 x 16 concentration grid. */
    private void buildDensities(Level level) {
        if (level == null || this.center == null) {
            return;
        }
        int originX = this.originX();
        int originZ = this.originZ();
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int index = gz * GRID + gx;
                int sampleX = Math.min(SAMPLES - 1, (gx * CELL_BLOCKS + CELL_BLOCKS / 2) / BLOCKS_PER_SAMPLE);
                int sampleZ = Math.min(SAMPLES - 1, (gz * CELL_BLOCKS + CELL_BLOCKS / 2) / BLOCKS_PER_SAMPLE);
                int surfaceY = this.surfaceHeights[sampleZ * SAMPLES + sampleX];
                if (surfaceY == Integer.MIN_VALUE) {
                    this.densities[index] = 0.0F;
                    continue;
                }
                BlockPos surface = new BlockPos(originX + gx * CELL_BLOCKS + CELL_BLOCKS / 2, surfaceY,
                        originZ + gz * CELL_BLOCKS + CELL_BLOCKS / 2);
                this.densities[index] = SimpleBiomeField.sampleAt(level, surface).totalDensity();
            }
        }
        this.fieldDirty = false;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        if (!this.hasLevel || this.center == null) {
            this.hasLevel = true;
            this.center = player.blockPosition();
            this.rebuild(level);
            return;
        }

        this.ticks++;
        if (this.ticks % SAMPLE_INTERVAL == 0
                || this.center.distSqr(player.blockPosition()) > MOVE_REBUILD_DISTANCE_SQ) {
            this.center = player.blockPosition();
            this.rebuild(level);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (this.width - MAP_SIZE) / 2;
        int top = Math.max(28, (this.height - MAP_SIZE) / 2);
        int textColor = 0xFFFFFFFF;

        if (!this.hasLevel || this.center == null) {
            graphics.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, top - 26, textColor, false);
            Component message = Component.translatable("gui.meteorosprotocol.detector_map.no_world");
            graphics.drawString(this.font, message, (this.width - this.font.width(message)) / 2, top + 74, 0xFFFF8080, false);
            return;
        }

        graphics.drawString(this.font, this.title, (this.width - this.font.width(this.title)) / 2, top - 26, textColor, false);
        graphics.drawString(this.font, Component.translatable("gui.meteorosprotocol.detector_map.scale"),
                left, top - 12, 0xFFB0B0B0, false);

        if (this.showField) {
            this.drawLegend(graphics, left, top);
        }

        graphics.fill(left - 2, top - 2, left + MAP_SIZE + 2, top + MAP_SIZE + 2, 0xFF101010);
        this.drawTerrain(graphics, left, top);
        this.drawChunkLines(graphics, left, top);
        if (this.showField) {
            this.drawFieldOverlay(graphics, left, top);
        }
        this.drawPlayerMarker(graphics, left, top);
        this.drawHoverInfo(graphics, left, top, mouseX, mouseY);
    }

    /** Draws terrain rows, merging runs of identical colours into one quad. */
    private void drawTerrain(GuiGraphics graphics, int left, int top) {
        for (int sz = 0; sz < SAMPLES; sz++) {
            int y = top + sz * PIXELS_PER_BLOCK;
            int runStart = 0;
            int runColor = this.terrain[sz * SAMPLES];
            for (int sx = 1; sx <= SAMPLES; sx++) {
                int color = sx < SAMPLES ? this.terrain[sz * SAMPLES + sx] : ~runColor;
                if (color != runColor) {
                    graphics.fill(left + runStart * PIXELS_PER_BLOCK, y,
                            left + sx * PIXELS_PER_BLOCK, y + PIXELS_PER_BLOCK, runColor);
                    runStart = sx;
                    runColor = color;
                }
            }
        }
    }

    /** Draws faint lines every 16 blocks so chunk boundaries stay readable. */
    private void drawChunkLines(GuiGraphics graphics, int left, int top) {
        int originX = this.originX();
        int originZ = this.originZ();
        for (int sx = 0; sx < SAMPLES; sx++) {
            if (Math.floorMod(originX + sx * BLOCKS_PER_SAMPLE, CHUNK_SIZE) == 0) {
                int x = left + sx * PIXELS_PER_BLOCK;
                graphics.fill(x, top, x + 1, top + MAP_SIZE, CHUNK_LINE_COLOR);
            }
        }
        for (int sz = 0; sz < SAMPLES; sz++) {
            if (Math.floorMod(originZ + sz * BLOCKS_PER_SAMPLE, CHUNK_SIZE) == 0) {
                int y = top + sz * PIXELS_PER_BLOCK;
                graphics.fill(left, y, left + MAP_SIZE, y + 1, CHUNK_LINE_COLOR);
            }
        }
    }

    /** Draws the translucent concentration layer over the terrain. */
    private void drawFieldOverlay(GuiGraphics graphics, int left, int top) {
        int cellPixels = MAP_SIZE / GRID;
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                float density = this.densities[gz * GRID + gx];
                if (density <= 0.0F) {
                    continue;
                }
                float t = Mth.clamp(density / MAX_LEGEND_DENSITY, 0.0F, 1.0F);
                int alpha = (int) (OVERLAY_ALPHA * t);
                if (alpha <= 0) {
                    continue;
                }
                int x = left + gx * cellPixels;
                int y = top + gz * cellPixels;
                graphics.fill(x, y, x + cellPixels, y + cellPixels,
                        (alpha << 24) | (colorFor(density) & 0xFFFFFF));
            }
        }
    }

    private void drawLegend(GuiGraphics graphics, int left, int top) {
        int legendX = left + MAP_SIZE - 60;
        int legendY = top - 10;
        for (int i = 0; i < 50; i++) {
            float value = MAX_LEGEND_DENSITY * i / 49.0F;
            graphics.fill(legendX + i, legendY, legendX + i + 1, legendY + 5, colorFor(value));
        }
        graphics.drawString(this.font, "0", legendX, legendY + 7, 0xFFB0B0B0, false);
        graphics.drawString(this.font, "150", legendX + 40, legendY + 7, 0xFFB0B0B0, false);
    }

    private void drawPlayerMarker(GuiGraphics graphics, int left, int top) {
        int centerX = left + MAP_SIZE / 2;
        int centerY = top + MAP_SIZE / 2;
        Direction facing = Minecraft.getInstance().player == null
                ? Direction.NORTH
                : Minecraft.getInstance().player.getDirection();
        for (int step = 0; step <= 10; step++) {
            int x = centerX + facing.getStepX() * step;
            int y = centerY + facing.getStepZ() * step;
            graphics.fill(x, y, x + 2, y + 2, PLAYER_COLOR);
        }
        graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFFFFFFFF);
        graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFF202020);
    }

    private void drawHoverInfo(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int sx = (mouseX - left) / PIXELS_PER_BLOCK;
        int sz = (mouseY - top) / PIXELS_PER_BLOCK;
        if (sx < 0 || sx >= SAMPLES || sz < 0 || sz >= SAMPLES) {
            graphics.drawString(this.font, Component.translatable("gui.meteorosprotocol.detector_map.hint"),
                    left, top + MAP_SIZE + 4, 0xFFB0B0B0, false);
            return;
        }

        int x = left + sx * PIXELS_PER_BLOCK;
        int y = top + sz * PIXELS_PER_BLOCK;
        graphics.fill(x, y, x + PIXELS_PER_BLOCK, y + 1, 0xFFFFFFFF);
        graphics.fill(x, y, x + 1, y + PIXELS_PER_BLOCK, 0xFFFFFFFF);
        graphics.fill(x + PIXELS_PER_BLOCK - 1, y, x + PIXELS_PER_BLOCK, y + PIXELS_PER_BLOCK, 0xFFFFFFFF);
        graphics.fill(x, y + PIXELS_PER_BLOCK - 1, x + PIXELS_PER_BLOCK, y + PIXELS_PER_BLOCK, 0xFFFFFFFF);

        int worldX = this.originX() + sx * BLOCKS_PER_SAMPLE;
        int worldZ = this.originZ() + sz * BLOCKS_PER_SAMPLE;
        int surfaceY = this.surfaceHeights[sz * SAMPLES + sx];
        Level level = Minecraft.getInstance().level;

        if (level != null && (worldX != this.hoverWorldX || worldZ != this.hoverWorldZ)) {
            if (surfaceY != Integer.MIN_VALUE) {
                this.hoverSample = SimpleBiomeField.sampleAt(level, new BlockPos(worldX, surfaceY, worldZ));
            } else {
                this.hoverSample = null;
            }
            this.hoverWorldX = worldX;
            this.hoverWorldZ = worldZ;
        }

        int textY = top + MAP_SIZE + 4;
        if (this.hoverSample != null) {
            graphics.drawString(this.font, String.format(Locale.ROOT,
                            "X=%d Z=%d Y=%d | total=%.1f",
                            worldX, worldZ, surfaceY, this.hoverSample.totalDensity()),
                    left, textY, 0xFFFFFFFF, false);
            graphics.drawString(this.font, String.format(Locale.ROOT,
                            "T=%.1f C=%.1f V+=%.1f V-=%.1f Aq=%.1f Ae=%.1f",
                            this.hoverSample.thermion(), this.hoverSample.cryon(),
                            this.hoverSample.voltixPos(), this.hoverSample.voltixNeg(),
                            this.hoverSample.aquantum(), this.hoverSample.aetheron()),
                    left, textY + 10, 0xFFD0D0D0, false);
        } else {
            graphics.drawString(this.font, String.format(Locale.ROOT, "X=%d Z=%d | unloaded", worldX, worldZ),
                    left, textY, 0xFFD0D0D0, false);
        }

        if (level != null) {
            String biome = level.getBiome(new BlockPos(worldX, Math.max(surfaceY, level.getMinBuildHeight()), worldZ))
                    .unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("unknown");
            graphics.drawString(this.font,
                    Component.translatable("gui.meteorosprotocol.detector_map.biome", biome),
                    left, textY + 20, 0xFFB0B0B0, false);
        }
    }

    private static int colorFor(float total) {
        float t = Mth.clamp(total / MAX_LEGEND_DENSITY, 0.0F, 1.0F);
        if (t < 0.34F) {
            return lerp(0xFF2855DC, 0xFF28C8DC, t / 0.34F);
        }
        if (t < 0.67F) {
            return lerp(0xFF28C8DC, 0xFFF0DC28, (t - 0.34F) / 0.33F);
        }
        return lerp(0xFFF0DC28, 0xFFE63428, (t - 0.67F) / 0.33F);
    }

    private static int lerp(int from, int to, float t) {
        int a = from >>> 24;
        int r = (from >> 16) & 0xFF;
        int g = (from >> 8) & 0xFF;
        int b = from & 0xFF;
        int ar = (to >> 16) & 0xFF;
        int ag = (to >> 8) & 0xFF;
        int ab = to & 0xFF;
        return (a << 24)
                | (Mth.clamp((int) (r + (ar - r) * t), 0, 255) << 16)
                | (Mth.clamp((int) (g + (ag - g) * t), 0, 255) << 8)
                | Mth.clamp((int) (b + (ab - b) * t), 0, 255);
    }
}
