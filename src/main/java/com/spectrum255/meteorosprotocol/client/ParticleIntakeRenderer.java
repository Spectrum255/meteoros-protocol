package com.spectrum255.meteorosprotocol.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;

/**
 * Six-segment composition display for the particle intake tower's north face.
 *
 * <p>The v3.2 model reserves six cells on the north side (model pixel
 * coordinates are converted to block-local units here). Each cell shows the
 * fraction of that phase in the block entity's mixed buffer, not an absolute
 * mPU level: the bar height is {@code amount / getTotal()}. The texture is a
 * plain white 16x16 mask, so the per-phase colour is supplied by the vertex
 * colour.</p>
 */
public class ParticleIntakeRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    private static final ResourceLocation LED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MeteorosProtocol.MODID, "textures/block/particle_intake_led.png");

    /** North display plane, model pixels; the model's north face is -Z. */
    private static final float SCREEN_Z = 1.90F / 16.0F;
    /** Bar width in model pixels (1.0 model pixel = 1/16 block). */
    private static final float BAR_WIDTH = 1.0F / 16.0F;
    /** Smallest visible bar, also in model pixels. */
    private static final float MIN_BAR_HEIGHT = 0.08F / 16.0F;
    /** Inset from the cell's top and bottom, model pixels. */
    private static final float BAR_INSET = 0.12F / 16.0F;

    private record Cell(Phase phase, float x0, float x1, float y0, float y1, int rgb) {
    }

    // Top row: Aetheron, Thermion, Cryon. Bottom row: V+, V-, Aquantum.
    private static final List<Cell> CELLS = List.of(
            new Cell(Phase.AETHERON, 9.7F, 12.3F, 7.325F, 9.075F, 0xFFFFFF),
            new Cell(Phase.THERMION, 6.7F, 9.3F, 7.325F, 9.075F, 0xFF4A3F),
            new Cell(Phase.CRYON, 3.7F, 6.3F, 7.325F, 9.075F, 0x5FE8FF),
            new Cell(Phase.VOLTIX_POS, 9.7F, 12.3F, 5.125F, 6.875F, 0xFFE14A),
            new Cell(Phase.VOLTIX_NEG, 6.7F, 9.3F, 5.125F, 6.875F, 0x9A5CFF),
            new Cell(Phase.AQUANTUM, 3.7F, 6.3F, 5.125F, 6.875F, 0x3A54E0)
    );

    @Override
    public void render(MachineBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!blockEntity.getBlockState().is(MeteorosProtocol.PARTICLE_INTAKE.get())) {
            return;
        }
        long total = blockEntity.getTotal();
        if (total <= 0L) {
            return;
        }

        // The model's display is authored on the north (-Z) face. Rotate the
        // whole quad set around the block centre so it follows the blockstate
        // FACING property. The blockstate model is rotated by the same y angle.
        BlockState state = blockEntity.getBlockState();
        float facingAngle = 0.0F;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            facingAngle = switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
                case EAST -> 90.0F;
                case SOUTH -> 180.0F;
                case WEST -> 270.0F;
                default -> 0.0F;
            };
        }

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingAngle));
        poseStack.translate(-0.5F, 0.0F, -0.5F);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(LED_TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        for (Cell cell : CELLS) {
            long amount = blockEntity.getAmount(cell.phase());
            if (amount <= 0L) {
                continue;
            }
            float ratio = (float) amount / (float) total;
            float available = (cell.y1() - cell.y0()) / 16.0F - BAR_INSET * 2.0F;
            float height = Math.max(MIN_BAR_HEIGHT, available * ratio);

            float centerX = (cell.x0() + cell.x1()) * 0.5F / 16.0F;
            float x0 = centerX - BAR_WIDTH * 0.5F;
            float x1 = centerX + BAR_WIDTH * 0.5F;
            float y0 = cell.y0() / 16.0F + BAR_INSET;
            float y1 = y0 + height;

            int red = (cell.rgb() >> 16) & 0xFF;
            int green = (cell.rgb() >> 8) & 0xFF;
            int blue = cell.rgb() & 0xFF;
            addQuad(buffer, pose, x0, x1, y0, y1, red, green, blue);
        }
        poseStack.popPose();
    }

    private static void addQuad(VertexConsumer buffer, Matrix4f pose,
                                float x0, float x1, float y0, float y1,
                                int red, int green, int blue) {
        // The display looks north (-Z); entityCutoutNoCull means winding is not
        // used for culling, but the normal still drives lighting.
        buffer.addVertex(pose, x0, y0, SCREEN_Z)
                .setColor(red, green, blue, 255)
                .setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, -1.0F);
        buffer.addVertex(pose, x1, y0, SCREEN_Z)
                .setColor(red, green, blue, 255)
                .setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, -1.0F);
        buffer.addVertex(pose, x1, y1, SCREEN_Z)
                .setColor(red, green, blue, 255)
                .setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, -1.0F);
        buffer.addVertex(pose, x0, y1, SCREEN_Z)
                .setColor(red, green, blue, 255)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, -1.0F);
    }
}
