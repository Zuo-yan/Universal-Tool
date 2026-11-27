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

/**
 * 矿脉感知客户端透视标记（按稀有度分色高亮矿石；Lv.3 觉醒额外以琥珀紫光高亮宝箱与刷怪笼）。
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class OreSightMarkers {
    private record Markers(ClientLevel level, long expiresAt, long[] positions, int[] colors, long[] specials) {
        boolean isCurrent(ClientLevel currentLevel) {
            return currentLevel != null && currentLevel == level && currentLevel.getGameTime() < expiresAt;
        }
    }

    private static Markers oreSight;
    private static Markers transmuted;

    public static void update(CompoundTag tag) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().location().toString().equals(tag.getString("dimension"))) return;
        int ticks = Math.max(0, Math.min(320, tag.getInt("ticks")));
        Markers markers = ticks == 0 ? null : new Markers(level, level.getGameTime() + ticks,
                tag.getLongArray("positions"), tag.getIntArray("colors"), tag.getLongArray("specialPositions"));
        if (tag.getBoolean("transmuted")) transmuted = markers;
        else oreSight = markers;
    }

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!ClientSkillCache.isSystemEnabled()) {
            oreSight = null;
            transmuted = null;
            return;
        }
        if (oreSight != null && !oreSight.isCurrent(mc.level)) oreSight = null;
        if (transmuted != null && !transmuted.isCurrent(mc.level)) transmuted = null;
        if (oreSight == null && transmuted == null) return;
        if (mc.screen != null || mc.options.hideGui || mc.player == null) return;
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 origin = camera.getPosition();
        Quaternionf inverse = new Quaternionf(camera.rotation()).conjugate();
        var g = event.getGuiGraphics();
        double halfW = g.guiWidth() / 2.0;
        double halfH = g.guiHeight() / 2.0;
        double fov = mc.options.fov().get() * mc.player.getFieldOfViewModifier();
        double focal = halfH / Math.tan(Math.toRadians(fov) / 2.0);

        if (oreSight != null) {
            drawList(mc, g, oreSight.positions, oreSight.colors, 0xFF62E9F2, 160, origin, inverse, halfW, halfH, focal);
            drawList(mc, g, oreSight.specials, null, 0xFFF2B649, 64, origin, inverse, halfW, halfH, focal);
        }
        if (transmuted != null) {
            drawList(mc, g, transmuted.positions, transmuted.colors, 0xFF62E9F2, 20, origin, inverse, halfW, halfH, focal);
        }
    }

    private static void drawList(Minecraft mc, net.minecraft.client.gui.GuiGraphics g, long[] list, int[] colors,
                                 int defaultColor, int cap, Vec3 origin, Quaternionf inverse,
                                 double halfW, double halfH, double focal) {
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Vector3f view = new Vector3f();
        for (int i = 0; i < list.length; i++) {
            long packed = list[i];
            int color = (colors != null && i < colors.length && colors[i] != 0) ? colors[i] : defaultColor;
            pos.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
            if (mc.level.hasChunkAt(pos) && mc.level.getBlockState(pos).isAir()) continue;
            double xOffset = pos.getX() + 0.5D - origin.x;
            double yOffset = pos.getY() + 0.5D - origin.y;
            double zOffset = pos.getZ() + 0.5D - origin.z;
            view.set((float) xOffset, (float) yOffset, (float) zOffset).rotate(inverse);
            if (view.z >= -0.1F) continue;
            int x = (int) Math.round(halfW + view.x * focal / -view.z);
            int y = (int) Math.round(halfH - view.y * focal / -view.z);
            if (x < 5 || y < 5 || x >= g.guiWidth() - 5 || y >= g.guiHeight() - 5) continue;
            g.fill(x - 3, y - 3, x + 4, y - 2, color);
            g.fill(x - 3, y + 3, x + 4, y + 4, color);
            g.fill(x - 3, y - 2, x - 2, y + 3, color);
            g.fill(x + 3, y - 2, x + 4, y + 3, color);
            if (Math.abs(x - halfW) < 18 && Math.abs(y - halfH) < 18) {
                g.drawString(mc.font, Math.round(Math.sqrt(xOffset * xOffset + yOffset * yOffset + zOffset * zOffset)) + "m", x + 6, y - 3, color, true);
            }
            if (++count >= cap) break;
        }
    }

    private OreSightMarkers() {}
}
