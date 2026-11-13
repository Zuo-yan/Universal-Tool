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
