package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.gwfx.universaltool.UniversalToolMod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Private, depth-independent ore markers. Server controls discoveries and expiry. */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class OreSightMarkers {
    private static long[] positions = new long[0];
    private static ClientLevel sourceLevel;
    private static long expiresAt;

    public static void update(CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().toString().equals(tag.getString("dimension"))) return;
        sourceLevel = mc.level;
        positions = tag.getLongArray("positions");
        expiresAt = mc.level.getGameTime() + Math.max(0, Math.min(200, tag.getInt("ticks")));
    }

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || sourceLevel != mc.level || mc.level.getGameTime() >= expiresAt) {
            positions = new long[0];
            sourceLevel = null;
            return;
        }
        if (mc.screen != null || mc.options.hideGui || mc.player == null) return;
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 origin = camera.getPosition();
        Quaternionf inverse = new Quaternionf(camera.rotation()).conjugate();
        var g = event.getGuiGraphics();
        double halfW = g.guiWidth() / 2.0;
        double halfH = g.guiHeight() / 2.0;
        double fov = mc.options.fov().get() * mc.player.getFieldOfViewModifier();
        double focal = halfH / Math.tan(Math.toRadians(fov) / 2.0);
        int count = 0;
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            // Hide mined blocks immediately, even before the next server refresh.
            if (mc.level.hasChunkAt(pos) && mc.level.getBlockState(pos).isAir()) continue;
            Vec3 delta = Vec3.atCenterOf(pos).subtract(origin);
            Vector3f view = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).rotate(inverse);
            if (view.z >= -0.1F) continue;
            int x = (int) Math.round(halfW + view.x * focal / -view.z);
            int y = (int) Math.round(halfH - view.y * focal / -view.z);
            if (x < 5 || y < 5 || x >= g.guiWidth() - 5 || y >= g.guiHeight() - 5) continue;
            int color = 0xFF62E9F2;
            // Small hollow square reads as a marker without obscuring the ore.
            g.fill(x - 3, y - 3, x + 4, y - 2, color);
            g.fill(x - 3, y + 3, x + 4, y + 4, color);
            g.fill(x - 3, y - 2, x - 2, y + 3, color);
            g.fill(x + 3, y - 2, x + 4, y + 3, color);
            if (Math.abs(x - halfW) < 18 && Math.abs(y - halfH) < 18) {
                g.drawString(mc.font, Math.round(delta.length()) + "m", x + 6, y - 3, color, true);
            }
            if (++count >= 128) break;
        }
    }

    private OreSightMarkers() {}
}
