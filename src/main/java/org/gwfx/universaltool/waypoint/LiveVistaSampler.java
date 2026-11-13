package org.gwfx.universaltool.waypoint;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Fast server-side 3D DDA voxel raycaster that captures a live 16:9 snapshot
 * of a destination {@link Waypoint} directly from its current {@link ServerLevel} state
 * (reflecting live blocks, lighting, water, and day/night sky across dimensions).
 */
public final class LiveVistaSampler {
    public static final int WIDTH = 112;
    public static final int HEIGHT = 63;
    private static final double MAX_DIST = 44.0D;

    private LiveVistaSampler() {}

    @Nullable
    public static byte[] captureCompressed(MinecraftServer server, Waypoint target) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, target.dimension());
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) {
            return null;
        }

        try {
            // Ensure immediate surrounding chunks are loaded for accurate raycasting
            int cx = Mth.floor(target.x()) >> 4;
            int cz = Mth.floor(target.z()) >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.getChunk(cx + dx, cz + dz);
                }
            }

            byte[] rgb = new byte[WIDTH * HEIGHT * 3];
            renderFrame(level, target, rgb);
            return compress(rgb);
        } catch (Exception e) {
            UniversalToolMod.LOGGER.warn("Failed to sample live vista for waypoint {}", target.name(), e);
            return null;
        }
    }

    private static void renderFrame(ServerLevel level, Waypoint target, byte[] rgb) {
        double eyeX = target.x();
        double eyeY = target.y() + 1.62D;
        double eyeZ = target.z();

        double yawRad = Math.toRadians(target.yaw());
        double pitchRad = Math.toRadians(Mth.clamp(target.pitch(), -75.0F, 75.0F));

        double cosPitch = Math.cos(pitchRad);
        double fwdX = -Math.sin(yawRad) * cosPitch;
        double fwdY = -Math.sin(pitchRad);
        double fwdZ = Math.cos(yawRad) * cosPitch;

        // Right vector = normalize(fwd x (0, 1, 0))
        double rightX = -Math.cos(yawRad);
        double rightY = 0.0D;
        double rightZ = -Math.sin(yawRad);

        // Up vector = right x fwd
        double upX = rightY * fwdZ - rightZ * fwdY;
        double upY = rightZ * fwdX - rightX * fwdZ;
        double upZ = rightX * fwdY - rightY * fwdX;

        double tanHalfFovY = Math.tan(Math.toRadians(34.0D));
        double aspect = (double) WIDTH / (double) HEIGHT;

        String dimStr = level.dimension().location().toString();
        boolean isNether = dimStr.contains("nether");
        boolean isEnd = dimStr.contains("end");
        float daySkyFactor = computeDaySkyFactor(level, isNether, isEnd);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos prevPos = new BlockPos.MutableBlockPos();

        for (int py = 0; py < HEIGHT; py++) {
            double ny = (1.0D - 2.0D * ((py + 0.5D) / HEIGHT)) * tanHalfFovY;
            for (int px = 0; px < WIDTH; px++) {
                double nx = (2.0D * ((px + 0.5D) / WIDTH) - 1.0D) * tanHalfFovY * aspect;

                double dx = fwdX + nx * rightX + ny * upX;
                double dy = fwdY + nx * rightY + ny * upY;
                double dz = fwdZ + nx * rightZ + ny * upZ;
                double invLen = 1.0D / Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx *= invLen;
                dy *= invLen;
                dz *= invLen;

                int color = traceRay(level, eyeX, eyeY, eyeZ, dx, dy, dz,
                        daySkyFactor, isNether, isEnd, pos, prevPos);

                int idx = (py * WIDTH + px) * 3;
                rgb[idx] = (byte) ((color >> 16) & 0xFF);
                rgb[idx + 1] = (byte) ((color >> 8) & 0xFF);
                rgb[idx + 2] = (byte) (color & 0xFF);
            }
        }
    }

    private static int traceRay(ServerLevel level, double ox, double oy, double oz,
                                double dx, double dy, double dz,
                                float daySkyFactor, boolean isNether, boolean isEnd,
                                BlockPos.MutableBlockPos pos, BlockPos.MutableBlockPos prevPos) {
        int bx = Mth.floor(ox);
        int by = Mth.floor(oy);
        int bz = Mth.floor(oz);

        int stepX = dx >= 0.0D ? 1 : -1;
        int stepY = dy >= 0.0D ? 1 : -1;
        int stepZ = dz >= 0.0D ? 1 : -1;

        double tDeltaX = Math.abs(dx) < 1e-6 ? 1e6 : Math.abs(1.0D / dx);
        double tDeltaY = Math.abs(dy) < 1e-6 ? 1e6 : Math.abs(1.0D / dy);
        double tDeltaZ = Math.abs(dz) < 1e-6 ? 1e6 : Math.abs(1.0D / dz);

        double tMaxX = Math.abs(dx) < 1e-6 ? 1e6 : ((dx > 0 ? (bx + 1.0D - ox) : (ox - bx)) * tDeltaX);
        double tMaxY = Math.abs(dy) < 1e-6 ? 1e6 : ((dy > 0 ? (by + 1.0D - oy) : (oy - by)) * tDeltaY);
        double tMaxZ = Math.abs(dz) < 1e-6 ? 1e6 : ((dz > 0 ? (bz + 1.0D - oz) : (oz - bz)) * tDeltaZ);

        double dist = 0.0D;
        int hitFaceAxis = 1; // 0=X, 1=Y, 2=Z
        float waterTint = 0.0F;
        prevPos.set(bx, by, bz);

        for (int iter = 0; iter < 96 && dist < MAX_DIST; iter++) {
            pos.set(bx, by, bz);
            if (iter > 0 && by >= level.getMinBuildHeight() && by < level.getMaxBuildHeight()) {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !state.is(Blocks.BARRIER)) {
                    if (state.getFluidState().is(FluidTags.WATER)) {
                        waterTint = Math.min(0.65F, waterTint + 0.16F);
                    } else if (!state.is(Blocks.GLASS) && !state.is(Blocks.TINTED_GLASS)) {
                        int baseCol = resolveBlockColor(level, pos, state);
                        float faceShade = switch (hitFaceAxis) {
                            case 1 -> (dy < 0.0D ? 1.0F : 0.62F);
                            case 0 -> 0.84F;
                            default -> 0.74F;
                        };
                        int skyLight = level.getBrightness(LightLayer.SKY, prevPos);
                        int blockLight = level.getBrightness(LightLayer.BLOCK, prevPos);
                        float light = Math.max(0.16F, Math.max((skyLight / 15.0F) * daySkyFactor, blockLight / 15.0F));
                        int jitter = ((bx * 73 + by * 37 + bz * 19) & 7) - 3;

                        int r = Mth.clamp((int) (((baseCol >> 16) & 0xFF) * faceShade * light) + jitter, 0, 255);
                        int g = Mth.clamp((int) (((baseCol >> 8) & 0xFF) * faceShade * light) + jitter, 0, 255);
                        int b = Mth.clamp((int) ((baseCol & 0xFF) * faceShade * light) + jitter, 0, 255);

                        if (waterTint > 0.0F) {
                            r = (int) Mth.lerp(waterTint, r, 32);
                            g = (int) Mth.lerp(waterTint, g, 96);
                            b = (int) Mth.lerp(waterTint, b, 198);
                        }

                        float fog = Mth.clamp((float) ((dist - MAX_DIST * 0.52D) / (MAX_DIST * 0.48D)), 0.0F, 0.72F);
                        if (fog > 0.0F) {
                            int sky = sampleSkyColor(dy, daySkyFactor, isNether, isEnd);
                            r = (int) Mth.lerp(fog, r, (sky >> 16) & 0xFF);
                            g = (int) Mth.lerp(fog, g, (sky >> 8) & 0xFF);
                            b = (int) Mth.lerp(fog, b, sky & 0xFF);
                        }
                        return (r << 16) | (g << 8) | b;
                    }
                }
            }

            prevPos.set(bx, by, bz);
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                dist = tMaxX;
                tMaxX += tDeltaX;
                bx += stepX;
                hitFaceAxis = 0;
            } else if (tMaxY < tMaxZ) {
                dist = tMaxY;
                tMaxY += tDeltaY;
                by += stepY;
                hitFaceAxis = 1;
            } else {
                dist = tMaxZ;
                tMaxZ += tDeltaZ;
                bz += stepZ;
                hitFaceAxis = 2;
            }
        }

        int sky = sampleSkyColor(dy, daySkyFactor, isNether, isEnd);
        if (waterTint > 0.0F) {
            int r = (int) Mth.lerp(waterTint, (sky >> 16) & 0xFF, 28);
            int g = (int) Mth.lerp(waterTint, (sky >> 8) & 0xFF, 84);
            int b = (int) Mth.lerp(waterTint, sky & 0xFF, 178);
            return (r << 16) | (g << 8) | b;
        }
        return sky;
    }

    private static int resolveBlockColor(ServerLevel level, BlockPos pos, BlockState state) {
        MapColor mapColor = state.getMapColor(level, pos);
        int col = mapColor.col;
        if (col == 0) {
            return 0x606870;
        }
        return col;
    }

    private static float computeDaySkyFactor(ServerLevel level, boolean isNether, boolean isEnd) {
        if (isNether) return 0.62F;
        if (isEnd) return 0.52F;
        long time = level.getDayTime() % 24000L;
        if (time < 0) time += 24000L;
        float angle = (float) ((time / 24000.0D) * Math.PI * 2.0D);
        float sun = Mth.sin(angle);
        return Mth.clamp(0.24F + Math.max(-0.2F, sun) * 0.78F, 0.22F, 1.0F);
    }

    private static int sampleSkyColor(double dy, float daySkyFactor, boolean isNether, boolean isEnd) {
        float t = Mth.clamp((float) (dy * 1.4D + 0.2D), 0.0F, 1.0F);
        if (isNether) {
            return lerpRgb(0x6C1E14, 0x3A0B08, t);
        }
        if (isEnd) {
            return lerpRgb(0x28143E, 0x0C0616, t);
        }
        int horizon = lerpRgb(0x161E34, 0xA8D4F8, daySkyFactor);
        int zenith = lerpRgb(0x090D1C, 0x4E8EF0, daySkyFactor);
        return lerpRgb(horizon, zenith, t);
    }

    private static int lerpRgb(int c0, int c1, float t) {
        int r = (int) Mth.lerp(t, (c0 >> 16) & 0xFF, (c1 >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (c0 >> 8) & 0xFF, (c1 >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, c0 & 0xFF, c1 & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    private static byte[] compress(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length / 3);
        byte[] buf = new byte[2048];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            baos.write(buf, 0, n);
        }
        deflater.end();
        return baos.toByteArray();
    }
}
