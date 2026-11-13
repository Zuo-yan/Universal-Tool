package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.space.StarryVoidBlockEntity;
import org.joml.Matrix4f;

/**
 * Overlays a continuous, smoothly flowing starlight & nebula veil onto exposed faces of
 * {@link org.gwfx.universaltool.space.StarryVoidBlock} while preserving the underlying
 * baked cosmic-masonry grid, ambient occlusion, and 3D directional depth.
 */
public class StarryVoidBlockEntityRenderer implements BlockEntityRenderer<StarryVoidBlockEntity> {
    private static final ResourceLocation STARRY_FLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/block/starry_flow_overlay.png");
    private static final float OFFSET = 0.002F;
    private static final float TILE_SCALE = 0.25F; // Seamless 4x4 block tiling across walls/floor/ceiling
    private static final float MAX_RENDER_DIST_SQ = 42.0F * 42.0F;

    public StarryVoidBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(StarryVoidBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (entity.getLevel() == null) {
            return;
        }
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos pos = entity.getBlockPos();
        double dx = (pos.getX() + 0.5) - camera.getPosition().x;
        double dy = (pos.getY() + 0.5) - camera.getPosition().y;
        double dz = (pos.getZ() + 0.5) - camera.getPosition().z;
        if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQ) {
            return;
        }

        float time = (entity.getLevel().getGameTime() + partialTick) * 0.0045F;
        float scrollU = time % 1.0F;
        float scrollV = (time * 0.6F) % 1.0F;

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(STARRY_FLOW_TEXTURE));

        // Distinct per-face tint preserves clear 3D orientation (Floor bright aqua, Walls crisp sapphire, Ceiling violet)
        renderFace(entity, pos, pose, consumer, Direction.UP,
                0, 1, 1 + OFFSET, 1 + OFFSET, 1, 0,
                pos.getX(), pos.getZ(), scrollU, scrollV, 185, 240, 255, 115);
        renderFace(entity, pos, pose, consumer, Direction.DOWN,
                0, 1, -OFFSET, -OFFSET, 0, 1,
                pos.getX(), pos.getZ(), scrollU, -scrollV, 165, 155, 255, 130);
        renderFace(entity, pos, pose, consumer, Direction.SOUTH,
                0, 1, 0, 1, 1 + OFFSET, 1 + OFFSET,
                pos.getX(), pos.getY(), scrollU, scrollV, 160, 215, 255, 125);
        renderFace(entity, pos, pose, consumer, Direction.NORTH,
                0, 1, 1, 0, -OFFSET, -OFFSET,
                pos.getX(), pos.getY(), -scrollU, scrollV, 160, 215, 255, 125);
        renderFace(entity, pos, pose, consumer, Direction.EAST,
                1 + OFFSET, 1 + OFFSET, 1, 0, 0, 1,
                pos.getZ(), pos.getY(), scrollU, scrollV, 135, 190, 250, 105);
        renderFace(entity, pos, pose, consumer, Direction.WEST,
                -OFFSET, -OFFSET, 0, 1, 0, 1,
                pos.getZ(), pos.getY(), -scrollU, scrollV, 135, 190, 250, 105);
    }

    private static void renderFace(StarryVoidBlockEntity entity, BlockPos pos, Matrix4f pose, VertexConsumer consumer,
                                   Direction face, float x0, float x1, float y0, float y1, float z0, float z1,
                                   int coordU, int coordV, float su, float sv, int r, int g, int b, int a) {
        if (!entity.shouldRenderFace(face)) {
            return;
        }
        float u0 = coordU * TILE_SCALE + su;
        float u1 = u0 + TILE_SCALE;
        float v0 = coordV * TILE_SCALE + sv;
        float v1 = v0 + TILE_SCALE;
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        if (face == Direction.SOUTH) {
            emit(consumer, pose, x0, y0, z0, r, g, b, a, u0, v0, nx, ny, nz);
            emit(consumer, pose, x1, y0, z0, r, g, b, a, u1, v0, nx, ny, nz);
            emit(consumer, pose, x1, y1, z0, r, g, b, a, u1, v1, nx, ny, nz);
            emit(consumer, pose, x0, y1, z0, r, g, b, a, u0, v1, nx, ny, nz);
        } else if (face == Direction.NORTH) {
            emit(consumer, pose, x0, y0, z0, r, g, b, a, u0, v1, nx, ny, nz);
            emit(consumer, pose, x1, y0, z0, r, g, b, a, u1, v1, nx, ny, nz);
            emit(consumer, pose, x1, y1, z0, r, g, b, a, u1, v0, nx, ny, nz);
            emit(consumer, pose, x0, y1, z0, r, g, b, a, u0, v0, nx, ny, nz);
        } else if (face == Direction.EAST) {
            emit(consumer, pose, x0, y0, z0, r, g, b, a, u0, v1, nx, ny, nz);
            emit(consumer, pose, x0, y0, z1, r, g, b, a, u1, v1, nx, ny, nz);
            emit(consumer, pose, x0, y1, z1, r, g, b, a, u1, v0, nx, ny, nz);
            emit(consumer, pose, x0, y1, z0, r, g, b, a, u0, v0, nx, ny, nz);
        } else if (face == Direction.WEST) {
            emit(consumer, pose, x0, y0, z0, r, g, b, a, u0, v0, nx, ny, nz);
            emit(consumer, pose, x0, y0, z1, r, g, b, a, u1, v0, nx, ny, nz);
            emit(consumer, pose, x0, y1, z1, r, g, b, a, u1, v1, nx, ny, nz);
            emit(consumer, pose, x0, y1, z0, r, g, b, a, u0, v1, nx, ny, nz);
        } else if (face == Direction.DOWN) {
            emit(consumer, pose, x0, y0, z0, r, g, b, a, u0, v0, nx, ny, nz);
            emit(consumer, pose, x1, y0, z0, r, g, b, a, u1, v0, nx, ny, nz);
            emit(consumer, pose, x1, y0, z1, r, g, b, a, u1, v1, nx, ny, nz);
            emit(consumer, pose, x0, y0, z1, r, g, b, a, u0, v1, nx, ny, nz);
        } else { // UP
            emit(consumer, pose, x0, y0, z0, r, g, b, a, u0, v1, nx, ny, nz);
            emit(consumer, pose, x1, y0, z0, r, g, b, a, u1, v1, nx, ny, nz);
            emit(consumer, pose, x1, y0, z1, r, g, b, a, u1, v0, nx, ny, nz);
            emit(consumer, pose, x0, y0, z1, r, g, b, a, u0, v0, nx, ny, nz);
        }
    }

    private static void emit(VertexConsumer consumer, Matrix4f pose, float x, float y, float z,
                             int r, int g, int b, int a, float u, float v, float nx, float ny, float nz) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
