package org.gwfx.universaltool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.november.mcphone.api.client.ui.IPhonePage;
import com.november.mcphone.api.client.ui.PhoneCanvas;
import com.november.mcphone.api.client.ui.PhoneStyle;
import com.november.mcphone.api.client.ui.PhoneViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.naiwa.DidiOrderPayload;
import org.gwfx.universaltool.naiwa.NaiwaEntity;

import java.util.List;

/**
 * In-phone UI page for "滴滴代打" (Didi Daida), displaying live nearby owned Naiwa status
 * and one-tap dispatch, follow/patrol mode, belly-laugh ultimate, and recall buttons.
 */
public final class DidiDaidaPage implements IPhonePage {

    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "textures/app/didi_daida.png");

    private static final int PAD = 5;
    private static final int TOAST_DURATION_TICKS = 50;
    private static final double QUERY_RADIUS = 96.0D;

    // Didi orange/gold accent palette layered smoothly over MCphone's PhoneStyle
    private static final int DIDI_ORANGE = 0xFFE8741E;
    private static final int DIDI_ORANGE_HOVER = 0xFFFF8C2B;
    private static final int DIDI_GOLD = 0xFFD99116;
    private static final int DIDI_GOLD_HOVER = 0xFFF2AA24;
    private static final int DIDI_ULT_BG = 0xFFB84308;
    private static final int DIDI_ULT_HOVER = 0xFFD6540F;
    private static final int DIDI_ULT_BORDER = 0xFFFFD84D;
    private static final int DIDI_DISMISS_BG = 0xFF283B35;
    private static final int DIDI_DISMISS_HOVER = 0xFF345248;
    private static final int DIDI_CARD_BG = 0x33000000;
    private static final int DIDI_TEXT_LIGHT = 0xFFFFFFFF;
    private static final int DIDI_TEXT_GOLD = 0xFFFFE27A;
    private static final int DIDI_CYAN = 0xFF55E6FF;

    private final ButtonRect[] buttons = new ButtonRect[6];

    private int layoutX;
    private int layoutY;
    private int layoutW;
    private int layoutH;

    private int nearbyCount;
    private int followCount;
    private int patrolCount;
    private int laughingCount;

    private Component toastMessage;
    private int toastTicks;

    public DidiDaidaPage() {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new ButtonRect();
        }
    }

    @Override
    public void onOpen() {
        refreshNearbyStatus();
    }

    @Override
    public void onResize(PhoneViewport viewport) {
        updateLayout(viewport.x(), viewport.y(), viewport.width(), viewport.height());
    }

    @Override
    public void tick() {
        refreshNearbyStatus();
        if (toastTicks > 0) {
            toastTicks--;
            if (toastTicks == 0) {
                toastMessage = null;
            }
        }
    }

    @Override
    public void render(PhoneCanvas canvas) {
        updateLayout(canvas.x(), canvas.y(), canvas.width(), canvas.height());
        PhoneStyle style = canvas.style();
        GuiGraphics g = canvas.graphics();
        Font font = canvas.font();

        // Fill phone screen background
        g.fill(canvas.x(), canvas.y(), canvas.x() + canvas.width(), canvas.y() + canvas.height(),
                style.screenBackground());

        canvas.clipped(canvas.x(), canvas.y(), canvas.width(), canvas.height(), () -> {
            int innerX = canvas.x() + PAD;
            int innerW = Math.max(20, canvas.width() - PAD * 2);
            int curY = canvas.y() + 3;

            // ---- Header Banner ----
            g.fill(innerX, curY, innerX + innerW, curY + 18, DIDI_ORANGE);
            g.fill(innerX, curY + 17, innerX + innerW, curY + 18, DIDI_ULT_BORDER);
            g.blit(ICON, innerX + 3, curY + 2, 0, 0, 14, 14, 14, 14);
            String title = Component.translatable("app.universal_tool.didi_daida").getString();
            drawScaledLeftText(g, font, title, innerX + 20, curY + 5, innerW - 23, DIDI_TEXT_LIGHT, true);
            curY += 21;

            // ---- Live Status Card ----
            g.fill(innerX, curY, innerX + innerW, curY + 22, DIDI_CARD_BG);
            g.fill(innerX, curY, innerX + 2, curY + 22, nearbyCount > 0 ? DIDI_CYAN : style.subtleColor());

            String countStr = Component.translatable("gui.universal_tool.didi.nearby_count", nearbyCount).getString();
            drawScaledLeftText(g, font, countStr, innerX + 5, curY + 2, innerW - 8,
                    nearbyCount > 0 ? style.titleColor() : style.subtleColor(), false);

            Component modeComp = resolveStatusComponent();
            int modeColor = laughingCount > 0 ? DIDI_TEXT_GOLD
                    : (nearbyCount > 0 ? style.accentColor() : style.subtleColor());
            drawScaledLeftText(g, font, modeComp.getString(), innerX + 5, curY + 12, innerW - 8, modeColor, false);

            // ---- Action Buttons ----
            renderButton(canvas, buttons[DidiOrderPayload.ACTION_SUMMON_ONE],
                    Component.translatable("gui.universal_tool.didi.btn.summon_one").getString(),
                    DIDI_ORANGE, DIDI_ORANGE_HOVER, DIDI_ULT_BORDER, DIDI_TEXT_LIGHT, true);

            renderButton(canvas, buttons[DidiOrderPayload.ACTION_SUMMON_THREE],
                    Component.translatable("gui.universal_tool.didi.btn.summon_three").getString(),
                    DIDI_GOLD, DIDI_GOLD_HOVER, DIDI_ULT_BORDER, DIDI_TEXT_LIGHT, true);

            boolean followActive = nearbyCount > 0 && followCount >= patrolCount;
            boolean patrolActive = nearbyCount > 0 && patrolCount > followCount;

            renderButton(canvas, buttons[DidiOrderPayload.ACTION_MODE_FOLLOW],
                    Component.translatable("gui.universal_tool.didi.btn.follow").getString(),
                    style.buttonColor(), style.buttonHoverColor(),
                    followActive ? DIDI_CYAN : 0x44FFFFFF,
                    followActive ? DIDI_CYAN : style.bodyColor(), false);

            renderButton(canvas, buttons[DidiOrderPayload.ACTION_MODE_PATROL],
                    Component.translatable("gui.universal_tool.didi.btn.patrol").getString(),
                    style.buttonColor(), style.buttonHoverColor(),
                    patrolActive ? DIDI_CYAN : 0x44FFFFFF,
                    patrolActive ? DIDI_CYAN : style.bodyColor(), false);

            renderButton(canvas, buttons[DidiOrderPayload.ACTION_ULTIMATE_LAUGH],
                    Component.translatable("gui.universal_tool.didi.btn.ultimate").getString(),
                    DIDI_ULT_BG, DIDI_ULT_HOVER, DIDI_ULT_BORDER, DIDI_TEXT_GOLD, true);

            renderButton(canvas, buttons[DidiOrderPayload.ACTION_DISMISS_ALL],
                    Component.translatable("gui.universal_tool.didi.btn.dismiss").getString(),
                    DIDI_DISMISS_BG, DIDI_DISMISS_HOVER, 0x5588CCAA, DIDI_TEXT_LIGHT, false);

            // ---- Confirmation Toast ----
            if (toastMessage != null && toastTicks > 0) {
                float fade = toastTicks < 10 ? toastTicks / 10.0F : 1.0F;
                int alphaBg = ((int) (215 * fade)) << 24;
                int alphaText = ((int) (255 * fade)) << 24;
                int toastH = 13;
                int toastY = canvas.y() + canvas.height() - toastH - 2;
                g.fill(innerX, toastY, innerX + innerW, toastY + toastH, alphaBg | 0x1E1A16);
                g.fill(innerX, toastY, innerX + 2, toastY + toastH, alphaText | (DIDI_ORANGE & 0x00FFFFFF));
                drawScaledCenteredText(g, font, toastMessage.getString(),
                        innerX + 3, toastY + 2, innerW - 5, alphaText | 0xFFE599, false);
            }
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        for (int action = 0; action < buttons.length; action++) {
            if (buttons[action].contains(mouseX, mouseY)) {
                dispatchOrder(action);
                return true;
            }
        }
        return false;
    }

    private void dispatchOrder(int action) {
        PacketDistributor.sendToServer(new DidiOrderPayload(action));
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        toastMessage = switch (action) {
            case DidiOrderPayload.ACTION_SUMMON_ONE ->
                    Component.translatable("gui.universal_tool.didi.toast.summon_one");
            case DidiOrderPayload.ACTION_SUMMON_THREE ->
                    Component.translatable("gui.universal_tool.didi.toast.summon_three");
            case DidiOrderPayload.ACTION_MODE_FOLLOW ->
                    Component.translatable("gui.universal_tool.didi.toast.follow");
            case DidiOrderPayload.ACTION_MODE_PATROL ->
                    Component.translatable("gui.universal_tool.didi.toast.patrol");
            case DidiOrderPayload.ACTION_ULTIMATE_LAUGH ->
                    Component.translatable("gui.universal_tool.didi.toast.ultimate");
            case DidiOrderPayload.ACTION_DISMISS_ALL ->
                    Component.translatable("gui.universal_tool.didi.toast.dismiss");
            default -> null;
        };
        toastTicks = TOAST_DURATION_TICKS;
    }

    private void refreshNearbyStatus() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            nearbyCount = 0;
            followCount = 0;
            patrolCount = 0;
            laughingCount = 0;
            return;
        }
        Player player = mc.player;
        List<NaiwaEntity> list = mc.level.getEntitiesOfClass(
                NaiwaEntity.class,
                player.getBoundingBox().inflate(QUERY_RADIUS),
                e -> e.isAlive() && player.getUUID().equals(e.getOwnerUuid()));

        int follow = 0;
        int patrol = 0;
        int laughing = 0;
        for (NaiwaEntity naiwa : list) {
            if (naiwa.getOrderMode() == NaiwaEntity.MODE_PATROL) {
                patrol++;
            } else {
                follow++;
            }
            if (naiwa.isLaughing()) {
                laughing++;
            }
        }
        nearbyCount = list.size();
        followCount = follow;
        patrolCount = patrol;
        laughingCount = laughing;
    }

    private Component resolveStatusComponent() {
        if (nearbyCount <= 0) {
            return Component.translatable("gui.universal_tool.didi.status_idle");
        }
        if (laughingCount > 0) {
            return Component.translatable("gui.universal_tool.didi.status_laughing");
        }
        if (followCount > 0 && patrolCount > 0) {
            return Component.translatable("gui.universal_tool.didi.status_mixed", followCount, patrolCount);
        }
        if (patrolCount > 0) {
            return Component.translatable("gui.universal_tool.didi.status_patrol");
        }
        return Component.translatable("gui.universal_tool.didi.status_follow");
    }

    private void updateLayout(int x, int y, int w, int h) {
        if (x == layoutX && y == layoutY && w == layoutW && h == layoutH) {
            return;
        }
        layoutX = x;
        layoutY = y;
        layoutW = w;
        layoutH = h;

        int innerX = x + PAD;
        int innerW = Math.max(20, w - PAD * 2);
        int topUsed = 3 + 18 + 3 + 22 + 4; // header + status card = 50px
        int remaining = Math.max(95, h - topUsed - 16); // reserve ~16px at bottom for toast

        // 5 button rows total; scale compactly if viewport height is tighter than standard 176px
        float scale = Mth.clamp(remaining / 108.0F, 0.72F, 1.0F);
        int gap = Math.max(2, Math.round(3 * scale));
        int hPrimary = Math.max(14, Math.round(19 * scale));
        int hRow3 = Math.max(14, Math.round(18 * scale));
        int hUlt = Math.max(15, Math.round(20 * scale));
        int hDismiss = Math.max(14, Math.round(18 * scale));

        int curY = y + topUsed;
        buttons[DidiOrderPayload.ACTION_SUMMON_ONE].set(innerX, curY, innerW, hPrimary);
        curY += hPrimary + gap;

        buttons[DidiOrderPayload.ACTION_SUMMON_THREE].set(innerX, curY, innerW, hPrimary);
        curY += hPrimary + gap;

        int halfGap = 3;
        int halfW = (innerW - halfGap) / 2;
        buttons[DidiOrderPayload.ACTION_MODE_FOLLOW].set(innerX, curY, halfW, hRow3);
        buttons[DidiOrderPayload.ACTION_MODE_PATROL].set(innerX + halfW + halfGap, curY, innerW - halfW - halfGap, hRow3);
        curY += hRow3 + gap;

        buttons[DidiOrderPayload.ACTION_ULTIMATE_LAUGH].set(innerX, curY, innerW, hUlt);
        curY += hUlt + gap;

        buttons[DidiOrderPayload.ACTION_DISMISS_ALL].set(innerX, curY, innerW, hDismiss);
    }

    private static void renderButton(PhoneCanvas canvas,
                                     ButtonRect rect,
                                     String label,
                                     int bgColor,
                                     int hoverColor,
                                     int borderColor,
                                     int textColor,
                                     boolean shadow) {
        GuiGraphics g = canvas.graphics();
        Font font = canvas.font();
        boolean hovered = canvas.hovered(rect.x, rect.y, rect.w, rect.h);
        int fill = hovered ? hoverColor : bgColor;

        g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, fill);
        if (hovered) {
            g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, canvas.style().pressedOverlay());
        }

        // Crisp 1px top/bottom highlight border
        g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + 1, borderColor);
        g.fill(rect.x, rect.y + rect.h - 1, rect.x + rect.w, rect.y + rect.h, borderColor);

        int textY = rect.y + (rect.h - font.lineHeight) / 2;
        drawScaledCenteredText(g, font, label, rect.x + 3, textY, rect.w - 6, textColor, shadow);
    }

    private static void drawScaledLeftText(GuiGraphics g,
                                           Font font,
                                           String text,
                                           int x,
                                           int y,
                                           int maxWidth,
                                           int color,
                                           boolean shadow) {
        int rawW = font.width(text);
        if (rawW <= maxWidth || rawW <= 0 || maxWidth <= 4) {
            g.drawString(font, text, x, y, color, shadow);
            return;
        }
        float scale = Math.max(0.55F, (float) maxWidth / (float) rawW);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (font.lineHeight * (1.0F - scale)) * 0.5F, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    private static void drawScaledCenteredText(GuiGraphics g,
                                               Font font,
                                               String text,
                                               int x,
                                               int y,
                                               int maxWidth,
                                               int color,
                                               boolean shadow) {
        int rawW = font.width(text);
        if (rawW <= maxWidth || rawW <= 0 || maxWidth <= 4) {
            int drawX = x + Math.max(0, (maxWidth - rawW) / 2);
            g.drawString(font, text, drawX, y, color, shadow);
            return;
        }
        float scale = Math.max(0.55F, (float) maxWidth / (float) rawW);
        float scaledW = rawW * scale;
        float drawX = x + Math.max(0.0F, (maxWidth - scaledW) * 0.5F);
        float drawY = y + (font.lineHeight * (1.0F - scale)) * 0.5F;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(drawX, drawY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    private static final class ButtonRect {
        int x;
        int y;
        int w;
        int h;

        void set(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
