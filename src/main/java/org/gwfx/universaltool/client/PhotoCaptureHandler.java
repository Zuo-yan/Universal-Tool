package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.gwfx.universaltool.UniversalToolMod;

@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public class PhotoCaptureHandler {

    private static boolean pendingCapture = false;

    public static void requestCapture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            // 1. 关闭当前手记界面，让下 1 帧渲染纯净游戏世界
            mc.setScreen(null);
        }
        pendingCapture = true;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 在 3D 世界渲染完成的瞬间抓取纯净画面
        if (pendingCapture && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            pendingCapture = false;

            // 2. 抓取主帧缓冲纯净世界实景
            String photoId = PhotoManager.captureCurrentView();

            // 3. 抓取完毕，重新打开手记界面并进入命名输入模式
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> {
                CodexScreen screen = new CodexScreen();
                mc.setScreen(screen);
                screen.enterNamingMode(photoId);

                // 播放快门音效
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.8F);
                }
            });
        }
    }
}
