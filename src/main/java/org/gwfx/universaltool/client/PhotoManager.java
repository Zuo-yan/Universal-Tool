package org.gwfx.universaltool.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PhotoManager {

    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();

    public static Path getPhotosDir() {
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("universal_tool_photos");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {}
        return dir;
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
        Path savePath = getPhotosDir().resolve(photoId);

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
            e.printStackTrace();
            return "";
        }
    }

    public static ResourceLocation getOrLoadTexture(String photoId) {
        if (photoId == null || photoId.isEmpty()) return null;
        if (TEXTURE_CACHE.containsKey(photoId)) {
            return TEXTURE_CACHE.get(photoId);
        }

        Path path = getPhotosDir().resolve(photoId);
        if (!Files.exists(path)) return null;

        try (InputStream is = new FileInputStream(path.toFile())) {
            NativeImage image = NativeImage.read(is);
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "photo_" + photoId.replace(".png", ""));
            Minecraft.getInstance().getTextureManager().register(loc, texture);
            TEXTURE_CACHE.put(photoId, loc);
            return loc;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
