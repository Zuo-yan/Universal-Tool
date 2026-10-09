package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.gwfx.universaltool.UniversalToolMod;

@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public class PhoenixCinematicOverlay {

    /**
     * 针对性屏蔽原版游戏玩法图层（快捷栏、准星、生命、饱食、护甲、经验条），保留电影沉浸感
     */
    @SubscribeEvent
    public static void onRenderLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!PhoenixCinematicController.isActive()) {
            return;
        }

        // 精确屏蔽原版玩法 HUD，绝不干扰顶层与通用图层
        var name = event.getName();
        if (name.equals(VanillaGuiLayers.HOTBAR)
                || name.equals(VanillaGuiLayers.CROSSHAIR)
                || name.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || name.equals(VanillaGuiLayers.FOOD_LEVEL)
                || name.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || name.equals(VanillaGuiLayers.AIR_LEVEL)
                || name.equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || name.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
                ) {
            event.setCanceled(true);
        }
    }

    /**
     * 在整个 GUI 渲染的最顶层（RenderGuiEvent.Post）统一绘制电影宽银幕黑边与科幻 HUD
     * 100% 保证不会被任何图层取消所影响！
     */
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!PhoenixCinematicController.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float p = PhoenixCinematicController.getProgressRatio(partialTick);
        int tick = PhoenixCinematicController.getCurrentTick();

        // 1. 宽银幕电影上下黑边 (Cinematic Letterbox, 高度各 12.5%)
        int barHeight = (int) (height * 0.125F);
        guiGraphics.fill(0, 0, width, barHeight, 0xFF000000);
        guiGraphics.fill(0, height - barHeight, width, height, 0xFF000000);

        // 2. 阶段性光效与生化眼睑视效
        if (tick >= 60 && tick <= 110) {
            // 阶段 2 量子超空间过载：高能青白泛光
            float sub = (tick - 60) / 50.0F;
            float alpha = Mth.sin(sub * (float) Math.PI) * 0.75F;
            int color = ((int) (alpha * 255) << 24) | 0x00E0FFFF;
            guiGraphics.fill(0, barHeight, width, height - barHeight, color);
        } else if (tick >= 155 && tick <= 185) {
            // 阶段 4 仓内生化睁眼模拟 (眼睑自中央逐渐向外分开)
            float openRatio = (tick - 155) / 30.0F;
            int eyelidH = (int) ((height / 2.0F - barHeight) * (1.0F - openRatio));
            if (eyelidH > 0) {
                guiGraphics.fill(0, barHeight, width, barHeight + eyelidH, 0xFA000000);
                guiGraphics.fill(0, height - barHeight - eyelidH, width, height - barHeight, 0xFA000000);
            }
        }

        // 3. 科幻 HUD 数据流与心电图波纹 (绝不会消失！)
        // 左上角主标题
        guiGraphics.drawString(mc.font, "§b[OPERATION PHOENIX // QUANTUM RE-ANCHORING]", 12, barHeight / 2 - 4, 0xFFFFFFFF, false);

        // 底部进度条与神经同步状态
        int syncPct = Math.min(100, (int) (p * 115.0F));
        String statusText;
        if (p < 0.25F) {
            statusText = String.format("§e[PHASE I: DEPARTURE] §3CONSCIOUSNESS DETACHED... §b%d%%", syncPct);
        } else if (p < 0.67F) {
            statusText = String.format("§b[PHASE II: HYPERJUMP] §3QUANTUM TRANSIT IN PROGRESS... §b%d%%", syncPct);
        } else if (p < 0.79F) {
            statusText = "§c[PHASE III: RESUSCITATION] §4HEARTBEAT RE-ESTABLISHED §7[▲▲▲▲]";
        } else if (p < 0.92F) {
            statusText = "§6[PHASE IV: DEPRESSURIZATION] §eHYDRAULIC DOORS OPENING...";
        } else {
            statusText = "§a[PHASE V: AWAKENED] §2PHOENIX REBIRTH PROTOCOL COMPLETE";
        }

        int textW = mc.font.width(statusText);
        guiGraphics.drawString(mc.font, statusText, (width - textW) / 2, height - barHeight / 2 - 4, 0xFFFFFFFF, false);
    }
}
