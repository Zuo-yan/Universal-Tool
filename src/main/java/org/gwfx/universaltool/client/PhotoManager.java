package org.gwfx.universaltool.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class PhotoManager {

    private static final Pattern SAFE_PHOTO_ID = Pattern.compile("^[a-z0-9_.-]+$");
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();

    public static Path getPhotosDir() {
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("universal_tool_photos").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {}
        return dir;
    }

    public static String sanitizePhotoId(String photoId) {
        if (photoId == null || photoId.isEmpty()) {
            return "";
        }
        if (photoId.contains("..") || photoId.contains("/") || photoId.contains("\\")) {
            return "";
        }
        return SAFE_PHOTO_ID.matcher(photoId).matches() ? photoId : "";
    }

    private static Path resolveSafePhotoPath(String photoId) {
        String safeId = sanitizePhotoId(photoId);
        if (safeId.isEmpty()) {
            return null;
        }
        Path dir = getPhotosDir();
        Path resolved = dir.resolve(safeId).normalize();
        if (!resolved.startsWith(dir)) {
            return null;
        }
        return resolved;
    }

    /**
     * 抓取当前主帧缓冲画面，高质量等比压缩为 160x90 缩略图并保存
     * @return 生成的照片 ID (photoId)
     */
    public static String captureCurrentView() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.getMainRenderTarget();
        if (target == null) return "";

        String photoId = UUID.randomUUID().toString() + ".png";
        Path savePath = resolveSafePhotoPath(photoId);
        if (savePath == null) return "";

        try (NativeImage full = new NativeImage(target.width, target.height, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            full.downloadTexture(0, false);
            full.flipY();

            // 下采样缩放到 160x90
            try (NativeImage thumb = new NativeImage(160, 90, false)) {
                for (int ty = 0; ty < 90; ty++) {
                    int sy = (int) ((ty / 90.0) * target.height);
                    for (int tx = 0; tx < 160; tx++) {
                        int sx = (int) ((tx / 160.0) * target.width);
                        thumb.setPixelRGBA(tx, ty, full.getPixelRGBA(sx, sy));
                    }
                }
                thumb.writeToFile(savePath);
            }
            return photoId;
        } catch (Exception e) {
            UniversalToolMod.LOGGER.warn("Failed to capture waypoint photo {}", photoId, e);
            return "";
        }
    }

    public static String spacePhotoId(String dimensionPath) {
        if (dimensionPath == null || dimensionPath.isEmpty()) {
            return "";
        }
        String sanitized = dimensionPath.toLowerCase(java.util.Locale.ROOT)
                .replace('/', '_')
                .replaceAll("[^a-z0-9_.-]", "_");
        return sanitizePhotoId("space_" + sanitized + ".png");
    }

    public static ResourceLocation getOrLoadSpaceSnapshot(String dimensionPath) {
        String photoId = spacePhotoId(dimensionPath);
        return photoId.isEmpty() ? null : getOrLoadTexture(photoId);
    }

    public static boolean captureSpaceSnapshot(String dimensionPath, int outWidth, int outHeight) {
        String photoId = spacePhotoId(dimensionPath);
        if (photoId.isEmpty()) return false;
        Path savePath = resolveSafePhotoPath(photoId);
        if (savePath == null) return false;
        if (!capturePanoramicToFile(savePath, outWidth, outHeight)) return false;
        ResourceLocation old = TEXTURE_CACHE.remove(photoId);
        if (old != null) {
            Minecraft.getInstance().getTextureManager().release(old);
        }
        return true;
    }

    /**
     * Captures the center panoramic slice of the main framebuffer matching {@code outWidth:outHeight}
     * and box-downsamples it directly to {@code outputPath}.
     */
    public static boolean capturePanoramicToFile(Path outputPath, int outWidth, int outHeight) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.getMainRenderTarget();
        if (target == null || target.width <= 0 || target.height <= 0 || outWidth <= 0 || outHeight <= 0) {
            return false;
        }
        try (NativeImage full = new NativeImage(target.width, target.height, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            full.downloadTexture(0, false);
            full.flipY();

            int srcWidth = full.getWidth();
            int srcHeight = full.getHeight();
            int cropW = srcWidth;
            int cropH = Math.round(cropW * (float) outHeight / outWidth);
            if (cropH > srcHeight) {
                cropH = srcHeight;
                cropW = Math.round(cropH * (float) outWidth / outHeight);
            }
            int cropX = Math.max(0, (srcWidth - cropW) / 2);
            int cropY = Math.max(0, (srcHeight - cropH) / 2);

            Files.createDirectories(outputPath.toAbsolutePath().normalize().getParent());
            try (NativeImage out = new NativeImage(outWidth, outHeight, false)) {
                for (int ty = 0; ty < outHeight; ty++) {
                    int sy0 = cropY + (ty * cropH) / outHeight;
                    int sy1 = Math.max(sy0 + 1, cropY + ((ty + 1) * cropH) / outHeight);
                    sy1 = Math.min(srcHeight, sy1);
                    for (int tx = 0; tx < outWidth; tx++) {
                        int sx0 = cropX + (tx * cropW) / outWidth;
                        int sx1 = Math.max(sx0 + 1, cropX + ((tx + 1) * cropW) / outWidth);
                        sx1 = Math.min(srcWidth, sx1);
                        long sumC0 = 0, sumC1 = 0, sumC2 = 0;
                        int count = 0;
                        for (int sy = sy0; sy < sy1; sy++) {
                            for (int sx = sx0; sx < sx1; sx++) {
                                int rgba = full.getPixelRGBA(sx, sy);
                                sumC0 += rgba & 0xFF;
                                sumC1 += (rgba >>> 8) & 0xFF;
                                sumC2 += (rgba >>> 16) & 0xFF;
                                count++;
                            }
                        }
                        int c0 = (int) (sumC0 / count);
                        int c1 = (int) (sumC1 / count);
                        int c2 = (int) (sumC2 / count);
                        out.setPixelRGBA(tx, ty, (0xFF << 24) | (c2 << 16) | (c1 << 8) | c0);
                    }
                }
                out.writeToFile(outputPath);
            }
            return true;
        } catch (Exception e) {
            UniversalToolMod.LOGGER.warn("Failed to capture panoramic screenshot {}", outputPath, e);
            return false;
        }
    }

    /**
     * Decompresses a server-sampled RGB24 live destination vista and registers it as a cached DynamicTexture.
     */
    public static void registerLiveVista(String vistaId, int width, int height, byte[] compressedRgb) {
        if (vistaId == null || vistaId.isEmpty() || width <= 0 || height <= 0 || compressedRgb == null) {
            return;
        }
        String safeId = sanitizePhotoId(vistaId);
        if (safeId.isEmpty()) {
            return;
        }
        int expected = width * height * 3;
        byte[] raw = new byte[expected];
        try {
            java.util.zip.Inflater inflater = new java.util.zip.Inflater();
            inflater.setInput(compressedRgb);
            int decoded = inflater.inflate(raw);
            inflater.end();
            if (decoded < expected) {
                return;
            }
            NativeImage image = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = (y * width + x) * 3;
                    int r = raw[idx] & 0xFF;
                    int g = raw[idx + 1] & 0xFF;
                    int b = raw[idx + 2] & 0xFF;
                    int abgr = (0xFF << 24) | (b << 16) | (g << 8) | r;
                    image.setPixelRGBA(x, y, abgr);
                }
            }
            ResourceLocation old = TEXTURE_CACHE.remove(safeId);
            if (old != null) {
                Minecraft.getInstance().getTextureManager().release(old);
            }
            String rlPath = "vista_" + safeId.replace('.', '_').toLowerCase(java.util.Locale.ROOT);
            ResourceLocation loc = ResourceLocation.tryBuild(UniversalToolMod.MODID, rlPath);
            if (loc == null) {
                image.close();
                return;
            }
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            TEXTURE_CACHE.put(safeId, loc);
        } catch (Exception e) {
            UniversalToolMod.LOGGER.warn("Failed to decode live wormhole vista {}", vistaId, e);
        }
    }

    public static ResourceLocation getOrLoadTexture(String photoId) {
        String safeId = sanitizePhotoId(photoId);
        if (safeId.isEmpty()) return null;
        if (TEXTURE_CACHE.containsKey(safeId)) {
            return TEXTURE_CACHE.get(safeId);
        }

        Path path = resolveSafePhotoPath(safeId);
        if (path == null || !Files.exists(path)) return null;

        try (InputStream is = new FileInputStream(path.toFile())) {
            NativeImage image = NativeImage.read(is);
            DynamicTexture texture = new DynamicTexture(image);
            String rlPath = "photo_" + safeId.replace(".png", "").replace('.', '_');
            ResourceLocation loc = ResourceLocation.tryBuild(UniversalToolMod.MODID, rlPath);
            if (loc == null) {
                texture.close();
                return null;
            }
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            TEXTURE_CACHE.put(safeId, loc);
            return loc;
        } catch (Exception e) {
            UniversalToolMod.LOGGER.warn("Failed to load waypoint photo texture {}", safeId, e);
            return null;
        }
    }

    public static void releasePhoto(String photoId) {
        String safeId = sanitizePhotoId(photoId);
        if (safeId.isEmpty()) {
            return;
        }
        ResourceLocation loc = TEXTURE_CACHE.remove(safeId);
        if (loc != null) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        Path path = resolveSafePhotoPath(safeId);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Releases all cached GPU textures on world/server disconnect without deleting saved PNG files.
     */
    public static void clearTextureCache() {
        if (TEXTURE_CACHE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : TEXTURE_CACHE.values()) {
            mc.getTextureManager().release(loc);
        }
        TEXTURE_CACHE.clear();
    }
}
