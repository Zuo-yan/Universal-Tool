package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.lottery.UniversalLotteryMachineBlockEntity;
import org.gwfx.universaltool.lottery.UniversalLotteryMachineMenu;
import org.joml.Matrix4f;

/**
 * 万能抽奖机三种玩法模式的子渲染器 (骰子 / 轮盘 / 老虎机)。
 */
final class LotterySubModeRenderers {

    static final ItemStack[] WHEEL_SECTOR_ITEMS = new ItemStack[]{
            new ItemStack(Items.GOLDEN_APPLE),
            new ItemStack(Items.NETHERITE_INGOT),
            new ItemStack(Items.DIAMOND),
            new ItemStack(Items.NETHER_STAR),
            new ItemStack(Items.ENDER_EYE),
            new ItemStack(Items.ENCHANTED_BOOK),
            new ItemStack(Items.IRON_INGOT),
            new ItemStack(Items.CHEST)
    };

    static final int[] WHEEL_SECTOR_COLORS = new int[]{
            0xFFD97706, // 0: Gold
            0xFF334155, // 1: Netherite Slate
            0xFF0284C7, // 2: Diamond Cyan
            0xFF7C3AED, // 3: Nether Star Mythic Purple
            0xFF059669, // 4: Eye Emerald
            0xFFDB2777, // 5: Book Magenta
            0xFF64748B, // 6: Iron Silver
            0xFFEA580C  // 7: Mystery Orange
    };

    static final ItemStack[] SLOT_SYMBOLS = new ItemStack[]{
            new ItemStack(Items.NETHER_STAR),
            new ItemStack(Items.NETHERITE_INGOT),
            new ItemStack(Items.GOLDEN_APPLE),
            new ItemStack(Items.DIAMOND),
            new ItemStack(Items.ENDER_EYE),
            new ItemStack(Items.CHORUS_FRUIT),
            new ItemStack(ModItems.VOID_SCRAP.get())
    };

    private LotterySubModeRenderers() {
    }

    static void renderDiceMode(
            GuiGraphics guiGraphics,
            Font font,
            UniversalLotteryMachineMenu menu,
            int relX,
            int relY,
            float partialTick,
            float diceRotX,
            float diceRotY,
            float diceRotZ,
            int odds
    ) {
        int chamberX = relX + 76;
        int chamberY = relY + 28;
        int chamberSize = 44;

        guiGraphics.fill(chamberX, chamberY, chamberX + chamberSize, chamberY + chamberSize, 0xFF0A0714);
        guiGraphics.renderOutline(chamberX, chamberY, chamberSize, chamberSize, 0xFF8A5CE6);
        guiGraphics.renderOutline(chamberX - 1, chamberY - 1, chamberSize + 2, chamberSize + 2, 0xFFD4AF37);

        renderCelestialDie(guiGraphics, menu.isRolling(), chamberX + chamberSize / 2, chamberY + chamberSize / 2, partialTick, diceRotX, diceRotY, diceRotZ);

        int meterX = relX + 70;
        int meterY = relY + 84;
        guiGraphics.fill(meterX, meterY, meterX + 56, meterY + 22, 0xFF141720);
        guiGraphics.renderOutline(meterX, meterY, 56, 22, 0xFF4A3B22);

        guiGraphics.fill(meterX + 4, meterY + 4, meterX + 28, meterY + 7, 0xFFDC2626);
        guiGraphics.fill(meterX + 28, meterY + 4, meterX + 52, meterY + 7, 0xFF2563EB);

        int needlePos = meterX + 4 + (int) Math.round(48.0 * (odds / 100.0));
        guiGraphics.fill(needlePos - 1, meterY + 2, needlePos + 1, meterY + 9, 0xFFFACC15);

        String oddsText = odds + "%";
        guiGraphics.drawString(font, oddsText, meterX + (56 - font.width(oddsText)) / 2, meterY + 11, 0xFFFDE047, false);
    }

    static void renderWheelMode(
            GuiGraphics guiGraphics,
            Font font,
            int relX,
            int relY,
            float wheelAngle,
            float needleJiggle,
            int odds
    ) {
        int centerX = relX + 98;
        int centerY = relY + 54;

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(wheelAngle));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        VertexConsumer buffer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 45.0F));
            drawWheelSector(pose.last().pose(), buffer, WHEEL_SECTOR_COLORS[i]);
            pose.popPose();
        }

        guiGraphics.flush();
        RenderSystem.enableCull();

        for (int i = 0; i < 8; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 45.0F));
            pose.translate(-6, -24, 0);
            pose.scale(0.75F, 0.75F, 1.0F);
            guiGraphics.renderItem(WHEEL_SECTOR_ITEMS[i], 0, 0);
            pose.popPose();
        }

        for (int i = 0; i < 8; i++) {
            float a = i * 45.0F * Mth.DEG_TO_RAD;
            int px = (int) (29.0F * Mth.sin(a));
            int py = (int) (-29.0F * Mth.cos(a));
            guiGraphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFDE047);
        }

        pose.popPose();

        guiGraphics.fill(centerX - 5, centerY - 5, centerX + 5, centerY + 5, 0xFFD4AF37);
        guiGraphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFFA855F7);
        guiGraphics.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, 0xFFFFFFFF);

        pose.pushPose();
        pose.translate(centerX, centerY - 29, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(needleJiggle));

        guiGraphics.fill(-3, -7, 4, -1, 0xFFD4AF37);
        guiGraphics.fill(-2, -1, 3, 1, 0xFFFACC15);
        guiGraphics.fill(-1, 1, 2, 3, 0xFFEF4444);
        guiGraphics.fill(0, 3, 1, 5, 0xFFEF4444);

        pose.popPose();

        int meterX = relX + 70;
        int meterY = relY + 86;
        guiGraphics.fill(meterX, meterY, meterX + 56, meterY + 16, 0xFF111827);
        guiGraphics.renderOutline(meterX, meterY, 56, 16, 0xFFD4AF37);

        int fillW = (int) (52.0F * (odds / 100.0F));
        guiGraphics.fill(meterX + 2, meterY + 12, meterX + 2 + fillW, meterY + 14, 0xFFA855F7);

        Component luckText = Component.translatable("gui.universal_tool.lottery.wheel.luck_bar", odds);
        guiGraphics.drawString(font, luckText, meterX + (56 - font.width(luckText)) / 2, meterY + 2, 0xFFFDE047, false);
    }

    private static void drawWheelSector(Matrix4f matrix, VertexConsumer buffer, int color) {
        float rIn = 6.0F;
        float rOut = 28.0F;
        int steps = 4;
        float startAngle = -22.5F * Mth.DEG_TO_RAD;
        float endAngle = 22.5F * Mth.DEG_TO_RAD;
        float step = (endAngle - startAngle) / steps;

        for (int i = 0; i < steps; i++) {
            float a0 = startAngle + i * step;
            float a1 = startAngle + (i + 1) * step;

            float x0In = rIn * Mth.sin(a0);
            float y0In = -rIn * Mth.cos(a0);
            float x0Out = rOut * Mth.sin(a0);
            float y0Out = -rOut * Mth.cos(a0);

            float x1In = rIn * Mth.sin(a1);
            float y1In = -rIn * Mth.cos(a1);
            float x1Out = rOut * Mth.sin(a1);
            float y1Out = -rOut * Mth.cos(a1);

            buffer.addVertex(matrix, x0In, y0In, 0.0F).setColor(color);
            buffer.addVertex(matrix, x1In, y1In, 0.0F).setColor(color);
            buffer.addVertex(matrix, x1Out, y1Out, 0.0F).setColor(color);
            buffer.addVertex(matrix, x0Out, y0Out, 0.0F).setColor(color);
        }
    }

    static void renderSlotMode(
            GuiGraphics guiGraphics,
            Font font,
            UniversalLotteryMachineMenu menu,
            int relX,
            int relY,
            float reel0Scroll,
            float reel1Scroll,
            float reel2Scroll,
            float leverPullTimer
    ) {
        int bannerY = relY + 23;

        boolean isRolling = menu.isRolling();
        int rollTicks = menu.getRollTicks();
        int totalTicks = menu.getTotalRollDurationTicks();
        boolean extended = (totalTicks == UniversalLotteryMachineBlockEntity.EXTENDED_ROLL_DURATION_TICKS);
        boolean isSuspense = isRolling && extended && rollTicks < 32;

        Component jpText;
        int textColor = 0xFFFDE047;

        if (isSuspense) {
            boolean flash = ((rollTicks / 3) % 2 == 0);
            textColor = flash ? 0xFFF43F5E : 0xFFFACC15;
            jpText = Component.translatable("gui.universal_tool.lottery.slot.banner.suspense");
        } else if (isRolling) {
            int total = menu.getMultiRollRequested();
            int remaining = menu.getRemainingMultiRolls();
            if (total > 1) {
                int current = Math.min(total, Math.max(1, total - remaining));
                jpText = Component.translatable("gui.universal_tool.lottery.slot.banner.multi_rolling", current, total);
            } else {
                jpText = Component.translatable("gui.universal_tool.lottery.slot.banner.jackpot_idle");
            }
        } else {
            int total = menu.getMultiRollRequested();
            int s1 = menu.getSlotReel1();
            int s2 = menu.getSlotReel2();
            int s3 = menu.getSlotReel3();
            if (s1 == 0 && s2 == 0 && s3 == 0) {
                jpText = Component.translatable(total > 1
                        ? "gui.universal_tool.lottery.slot.banner.grand_multi"
                        : "gui.universal_tool.lottery.slot.banner.grand_single");
                textColor = 0xFFFFD700;
            } else if (s1 == s2 && s2 == s3) {
                jpText = Component.translatable(total > 1
                        ? "gui.universal_tool.lottery.slot.banner.triple_multi"
                        : "gui.universal_tool.lottery.slot.banner.triple_single");
                textColor = 0xFFFDE047;
            } else if (s1 == s2 || s2 == s3 || s1 == s3) {
                jpText = Component.translatable(total > 1
                        ? "gui.universal_tool.lottery.slot.banner.pair_multi"
                        : "gui.universal_tool.lottery.slot.banner.pair_single");
                textColor = 0xFF93C5FD;
            } else if (total > 1) {
                jpText = Component.translatable("gui.universal_tool.lottery.slot.banner.multi_done", total);
                textColor = 0xFFFACC15;
            } else {
                jpText = Component.translatable("gui.universal_tool.lottery.slot.banner.consolation");
                textColor = 0xFF9CA3AF;
            }
        }

        int centerX = relX + 98;
        int textW = font.width(jpText);
        guiGraphics.drawString(font, jpText, centerX - textW / 2, bannerY, textColor, true);

        int cabinetX = relX + 54;
        int cabinetY = relY + 36;
        int cabinetW = 88;
        int cabinetH = 62;

        guiGraphics.fill(cabinetX, cabinetY, cabinetX + cabinetW, cabinetY + cabinetH, 0xFF0D0F18);
        int cabinetBorder = isSuspense ? 0xFFF43F5E : 0xFFD4AF37;
        guiGraphics.renderOutline(cabinetX, cabinetY, cabinetW, cabinetH, cabinetBorder);

        int[] reelX = new int[]{cabinetX + 4, cabinetX + 32, cabinetX + 60};
        float[] scrolls = new float[]{reel0Scroll, reel1Scroll, reel2Scroll};

        guiGraphics.enableScissor(cabinetX + 2, cabinetY + 2, cabinetX + cabinetW - 2, cabinetY + cabinetH - 2);

        int paylineY = cabinetY + 23;
        for (int r = 0; r < 3; r++) {
            int cx = reelX[r];
            guiGraphics.fill(cx, cabinetY + 2, cx + 24, cabinetY + cabinetH - 2, 0xFF1A1C28);
            guiGraphics.renderOutline(cx, cabinetY + 2, 24, cabinetH - 4, 0xFF374151);

            float scroll = scrolls[r];
            int totalSyms = SLOT_SYMBOLS.length;
            int baseIdx = ((int) Math.floor(scroll / 18.0F)) % totalSyms;
            float offsetFrac = scroll % 18.0F;

            for (int k = -2; k <= 2; k++) {
                int symIdx = (baseIdx + k + totalSyms * 100) % totalSyms;
                int drawY = paylineY + k * 18 - (int) offsetFrac;
                guiGraphics.renderItem(SLOT_SYMBOLS[symIdx], cx + 4, drawY);
            }
        }

        guiGraphics.disableScissor();

        guiGraphics.fill(cabinetX + 1, paylineY + 8, cabinetX + cabinetW - 1, paylineY + 9, 0xFFE879F9);
        guiGraphics.fill(cabinetX + 2, paylineY + 7, cabinetX + cabinetW - 2, paylineY + 8, 0x40E879F9);

        if (!menu.isRolling() && menu.getSlotReel1() == menu.getSlotReel2()
                && menu.getSlotReel2() == menu.getSlotReel3()) {
            for (int r = 0; r < 3; r++) {
                guiGraphics.renderOutline(reelX[r] + 2, paylineY - 2, 20, 20, 0xFFFACC15);
            }
        }

        renderLever(guiGraphics, relX + 185, relY + 44, leverPullTimer);
    }

    private static void renderLever(GuiGraphics guiGraphics, int pivotX, int pivotY, float leverPullTimer) {
        guiGraphics.fill(pivotX - 3, pivotY + 12, pivotX + 7, pivotY + 20, 0xFF475569);
        guiGraphics.renderOutline(pivotX - 3, pivotY + 12, 10, 8, 0xFFD4AF37);

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(pivotX + 2, pivotY + 14, 0);

        float pullAngle = -25.0F + (float) Math.sin(leverPullTimer * Math.PI) * 60.0F;
        pose.mulPose(Axis.ZP.rotationDegrees(pullAngle));

        guiGraphics.fill(-2, -18, 2, 0, 0xFFE2E8F0);
        guiGraphics.renderOutline(-2, -18, 4, 18, 0xFF94A3B8);

        guiGraphics.fill(-5, -24, 5, -16, 0xFFDC2626);
        guiGraphics.fill(-3, -22, 1, -19, 0xFFFCA5A5);

        pose.popPose();
    }

    static void drawRarityBadge(GuiGraphics guiGraphics, Font font, int x, int y, Component label, int color, boolean active) {
        int dotColor = active ? color : 0xFF3A3E4A;
        if (active) {
            guiGraphics.fill(x - 1, y + 1, x + 5, y + 7, color | 0x40000000);
            guiGraphics.fill(x, y + 2, x + 4, y + 6, dotColor);
            guiGraphics.drawString(font, label, x + 6, y, color, true);
        } else {
            guiGraphics.fill(x, y + 2, x + 4, y + 6, dotColor);
            guiGraphics.drawString(font, label, x + 6, y, 0xFF5B6270, false);
        }
    }

    private static void renderCelestialDie(
            GuiGraphics guiGraphics,
            boolean isRolling,
            int centerX,
            int centerY,
            float partialTick,
            float diceRotX,
            float diceRotY,
            float diceRotZ
    ) {
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 40.0F);

        float rx = diceRotX + (isRolling ? partialTick * 26.0F : partialTick * 0.8F);
        float ry = diceRotY + (isRolling ? partialTick * 34.0F : partialTick * 1.2F);
        float rz = diceRotZ + (isRolling ? partialTick * 18.0F : partialTick * 0.5F);

        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.flush();

        float s = 9.0F;

        pose.pushPose();
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 1);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 6);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 2);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 5);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 3);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.translate(0, 0, s);
        drawDiceFace(guiGraphics, s, 4);
        pose.popPose();

        guiGraphics.flush();
        RenderSystem.disableDepthTest();
        pose.popPose();
    }

    private static void drawDiceFace(GuiGraphics guiGraphics, float s, int pips) {
        int is = (int) s;
        guiGraphics.fill(-is, -is, is, is, 0xFF181028);
        guiGraphics.fill(-is + 1, -is + 1, is - 1, is - 1, 0xFF2B1C47);
        guiGraphics.renderOutline(-is, -is, is * 2, is * 2, 0xFFD4AF37);

        int pipColor = (pips == 1) ? 0xFFEF4444 : 0xFFFDE047;
        int d = 4;

        switch (pips) {
            case 1 -> guiGraphics.fill(-1, -1, 2, 2, pipColor);
            case 2 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
            case 3 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, 0, 0, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
            case 4 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, d, -d, pipColor);
                drawPip(guiGraphics, -d, d, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
            case 5 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, d, -d, pipColor);
                drawPip(guiGraphics, 0, 0, pipColor);
                drawPip(guiGraphics, -d, d, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
            case 6 -> {
                drawPip(guiGraphics, -d, -d, pipColor);
                drawPip(guiGraphics, -d, 0, pipColor);
                drawPip(guiGraphics, -d, d, pipColor);
                drawPip(guiGraphics, d, -d, pipColor);
                drawPip(guiGraphics, d, 0, pipColor);
                drawPip(guiGraphics, d, d, pipColor);
            }
        }
    }

    private static void drawPip(GuiGraphics guiGraphics, int px, int py, int color) {
        guiGraphics.fill(px - 1, py - 1, px + 1, py + 1, color);
    }
}
