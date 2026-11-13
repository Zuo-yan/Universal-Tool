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
 * 万能抽奖机三种玩法模式的子渲染器 (骰子 / 轮盘 / 老虎机) 与原版风格 UI 绘制辅助。
 */
final class LotterySubModeRenderers {

    static final int COLOR_PANEL = 0xFFEFE5CF;
    static final int COLOR_PARCHMENT_BANNER = 0xFFF4ECD9;
    static final int COLOR_PARCHMENT_SH = 0xFFE3D5B8;
    static final int COLOR_WALNUT = 0xFF5C4028;
    static final int COLOR_WALNUT_HI = 0xFF7A5638;
    static final int COLOR_WALNUT_SH = 0xFF3D2818;
    static final int COLOR_UMBER = 0xFF26180E;
    static final int COLOR_BRASS = 0xFFD6A84E;
    static final int COLOR_BRASS_HI = 0xFFF2D88A;
    static final int COLOR_BRASS_D = 0xFF9E7330;
    static final int COLOR_DARK = 0xFF735F46;
    static final int COLOR_SHADOW = 0xFF9E7330;
    static final int COLOR_SLOT = 0xFFC4B395;
    static final int COLOR_WHITE = 0xFFFAF2E2;
    static final int COLOR_LABEL = 0xFF3A281A;

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
            0xFF3F4756, // 1: Netherite Slate
            0xFF0284C7, // 2: Diamond Blue
            0xFF7C3AED, // 3: Nether Star Purple
            0xFF059669, // 4: Eye Emerald
            0xFFC026D3, // 5: Book Magenta
            0xFF64748B, // 6: Iron Silver
            0xFFEA580C  // 7: Copper Orange
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

    // -------------------------------------------------------------------------
    // Walnut Timber, Burnished Brass & Illuminated Parchment 绘图原语
    // -------------------------------------------------------------------------

    static void drawMcPanel(GuiGraphics g, int x, int y, int w, int h) {
        int x1 = x + w - 1;
        int y1 = y + h - 1;
        // 1. 主体羊皮纸填充 + 顶部羊皮纸横幅衬底
        g.fill(x + 2, y + 1, x1 - 1, y1, COLOR_PANEL);
        g.fill(x + 1, y + 2, x1, y1 - 1, COLOR_PANEL);
        g.fill(x + 5, y + 5, x1 - 4, y + 18, COLOR_PARCHMENT_BANNER);
        g.renderOutline(x + 5, y + 5, w - 10, h - 10, COLOR_PARCHMENT_SH);

        // 2. 内侧 1px 抛光黄铜嵌线
        g.fill(x + 4, y + 4, x1 - 3, y + 5, COLOR_BRASS);
        g.fill(x + 4, y1 - 4, x1 - 3, y1 - 3, COLOR_BRASS_D);
        g.fill(x + 4, y + 4, x + 5, y1 - 3, COLOR_BRASS);
        g.fill(x1 - 4, y + 4, x1 - 3, y1 - 3, COLOR_BRASS_D);

        // 3. 3px 胡桃木边框 (含高光与阴影倒角)
        g.fill(x + 2, y + 1, x1 - 1, y + 2, COLOR_WALNUT_HI);
        g.fill(x + 2, y + 2, x1 - 1, y + 4, COLOR_WALNUT);
        g.fill(x + 2, y1 - 3, x1 - 1, y1 - 1, COLOR_WALNUT);
        g.fill(x + 2, y1 - 1, x1 - 1, y1, COLOR_WALNUT_SH);

        g.fill(x + 1, y + 2, x + 2, y1 - 1, COLOR_WALNUT_HI);
        g.fill(x + 2, y + 2, x + 4, y1 - 1, COLOR_WALNUT);
        g.fill(x1 - 3, y + 2, x1 - 1, y1 - 1, COLOR_WALNUT);
        g.fill(x1 - 1, y + 2, x1, y1 - 1, COLOR_WALNUT_SH);

        // 4. 1px 深棕木外轮廓 (2px 圆角)
        g.fill(x + 2, y, x1 - 1, y + 1, COLOR_UMBER);
        g.fill(x + 2, y1, x1 - 1, y1 + 1, COLOR_UMBER);
        g.fill(x, y + 2, x + 1, y1 - 1, COLOR_UMBER);
        g.fill(x1, y + 2, x1 + 1, y1 - 1, COLOR_UMBER);
        g.fill(x + 1, y + 1, x + 2, y + 2, COLOR_UMBER);
        g.fill(x1 - 1, y + 1, x1, y + 2, COLOR_UMBER);
        g.fill(x + 1, y1 - 1, x + 2, y1, COLOR_UMBER);
        g.fill(x1 - 1, y1 - 1, x1, y1, COLOR_UMBER);

        // 5. 四角抛光黄铜护角与铆钉
        drawBrassCornerBrackets(g, x, y, w, h, 6);
    }

    private static void drawBrassCornerBrackets(GuiGraphics g, int x, int y, int w, int h, int len) {
        int x1 = x + w - 1;
        int y1 = y + h - 1;
        // Top-Left
        g.fill(x + 2, y + 1, x + 1 + len, y + 2, COLOR_BRASS_HI);
        g.fill(x + 2, y + 2, x + 1 + len, y + 3, COLOR_BRASS);
        g.fill(x + 2, y + 3, x + 1 + len, y + 4, COLOR_BRASS_D);
        g.fill(x + 1, y + 2, x + 2, y + 1 + len, COLOR_BRASS_HI);
        g.fill(x + 2, y + 2, x + 3, y + 1 + len, COLOR_BRASS);
        g.fill(x + 3, y + 2, x + 4, y + 1 + len, COLOR_BRASS_D);
        g.fill(x + 2, y + 2, x + 3, y + 3, COLOR_BRASS_HI);
        // Top-Right
        g.fill(x1 - len, y + 1, x1 - 1, y + 2, COLOR_BRASS_HI);
        g.fill(x1 - len, y + 2, x1 - 1, y + 3, COLOR_BRASS);
        g.fill(x1 - len, y + 3, x1 - 1, y + 4, COLOR_BRASS_D);
        g.fill(x1 - 3, y + 2, x1 - 2, y + 1 + len, COLOR_BRASS_HI);
        g.fill(x1 - 2, y + 2, x1 - 1, y + 1 + len, COLOR_BRASS);
        g.fill(x1 - 1, y + 2, x1, y + 1 + len, COLOR_BRASS_D);
        g.fill(x1 - 2, y + 2, x1 - 1, y + 3, COLOR_BRASS_HI);
        // Bottom-Left
        g.fill(x + 2, y1 - 3, x + 1 + len, y1 - 2, COLOR_BRASS_HI);
        g.fill(x + 2, y1 - 2, x + 1 + len, y1 - 1, COLOR_BRASS);
        g.fill(x + 2, y1 - 1, x + 1 + len, y1, COLOR_BRASS_D);
        g.fill(x + 1, y1 - len, x + 2, y1 - 1, COLOR_BRASS_HI);
        g.fill(x + 2, y1 - len, x + 3, y1 - 1, COLOR_BRASS);
        g.fill(x + 3, y1 - len, x + 4, y1 - 1, COLOR_BRASS_D);
        g.fill(x + 2, y1 - 2, x + 3, y1 - 1, COLOR_BRASS);
        // Bottom-Right
        g.fill(x1 - len, y1 - 3, x1 - 1, y1 - 2, COLOR_BRASS_HI);
        g.fill(x1 - len, y1 - 2, x1 - 1, y1 - 1, COLOR_BRASS);
        g.fill(x1 - len, y1 - 1, x1 - 1, y1, COLOR_BRASS_D);
        g.fill(x1 - 3, y1 - len, x1 - 2, y1 - 1, COLOR_BRASS_HI);
        g.fill(x1 - 2, y1 - len, x1 - 1, y1 - 1, COLOR_BRASS);
        g.fill(x1 - 1, y1 - len, x1, y1 - 1, COLOR_BRASS_D);
        g.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, COLOR_BRASS);
    }

    static void drawMcSlot(GuiGraphics g, int sx, int sy) {
        g.fill(sx + 1, sy + 1, sx + 17, sy + 17, COLOR_SLOT);
        g.fill(sx, sy, sx + 17, sy + 1, COLOR_DARK);
        g.fill(sx, sy + 1, sx + 1, sy + 17, COLOR_DARK);
        g.fill(sx + 1, sy + 17, sx + 18, sy + 18, COLOR_WHITE);
        g.fill(sx + 17, sy + 1, sx + 18, sy + 17, COLOR_WHITE);
        g.fill(sx + 17, sy, sx + 18, sy + 1, 0xFFB2A082);
        g.fill(sx, sy + 17, sx + 1, sy + 18, 0xFFB2A082);
    }

    static void drawGoldTrimSlot(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 2, sy - 2, sx + 20, sy + 20, COLOR_WALNUT);
        g.fill(sx - 1, sy - 1, sx + 19, sy + 19, COLOR_BRASS_D);
        g.fill(sx - 1, sy - 1, sx + 18, sy, COLOR_BRASS_HI);
        g.fill(sx - 1, sy - 1, sx, sy + 18, COLOR_BRASS_HI);
        drawMcSlot(g, sx, sy);
    }

    static void drawRecessedWell(GuiGraphics g, int x, int y, int w, int h, int interior) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, interior);
        g.fill(x, y, x + w - 1, y + 1, COLOR_DARK);
        g.fill(x, y + 1, x + 1, y + h - 1, COLOR_DARK);
        g.fill(x + 1, y + h - 1, x + w, y + h, COLOR_WHITE);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, COLOR_WHITE);
        g.fill(x + w - 1, y, x + w, y + 1, COLOR_PANEL);
        g.fill(x, y + h - 1, x + 1, y + h, COLOR_PANEL);
    }

    static void drawOakPlaque(GuiGraphics g, int x, int y, int w, int h) {
        drawRecessedWell(g, x, y, w, h, COLOR_WALNUT);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, COLOR_BRASS_D);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF241B14);
    }

    static void drawGroove(GuiGraphics g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, COLOR_BRASS_D);
        g.fill(x0, y + 1, x1, y + 2, COLOR_WHITE);
    }

    static void drawMcButton(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean enabled, boolean goldStyle) {
        int face, hi, sh;
        if (!enabled) {
            face = 0xFF8C7D6B;
            hi = 0xFFA89882;
            sh = 0xFF635648;
        } else if (goldStyle) {
            face = hovered ? 0xFFD9AE48 : 0xFFBC8E38;
            hi = hovered ? 0xFFFCE69C : 0xFFF2D88A;
            sh = hovered ? 0xFF855A18 : 0xFF704C1C;
        } else {
            face = hovered ? 0xFFA67846 : 0xFF845C34;
            hi = hovered ? 0xFFE6C273 : 0xFFD6A84E;
            sh = hovered ? 0xFF5C3E1C : 0xFF4C321A;
        }
        g.fill(x, y, x + w, y + h, COLOR_UMBER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, sh);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, sh);
    }

    // -------------------------------------------------------------------------
    // 模式 0: 3D 骰子模式
    // -------------------------------------------------------------------------

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

        // 胡桃木黄铜框 + 深靛蓝丝绒舞台视窗
        drawRecessedWell(guiGraphics, chamberX - 2, chamberY - 2, chamberSize + 4, chamberSize + 4, COLOR_WALNUT);
        guiGraphics.renderOutline(chamberX - 1, chamberY - 1, chamberSize + 2, chamberSize + 2, COLOR_BRASS);
        guiGraphics.fill(chamberX, chamberY, chamberX + chamberSize, chamberY + chamberSize, 0xFF171E30);
        guiGraphics.renderOutline(chamberX + 1, chamberY + 1, chamberSize - 2, chamberSize - 2, 0xFF232E4A);

        // 四角抛光黄铜铆钉
        guiGraphics.fill(chamberX + 1, chamberY + 1, chamberX + 3, chamberY + 3, COLOR_BRASS_HI);
        guiGraphics.fill(chamberX + chamberSize - 3, chamberY + 1, chamberX + chamberSize - 1, chamberY + 3, COLOR_BRASS_HI);
        guiGraphics.fill(chamberX + 1, chamberY + chamberSize - 3, chamberX + 3, chamberY + chamberSize - 1, COLOR_BRASS_HI);
        guiGraphics.fill(chamberX + chamberSize - 3, chamberY + chamberSize - 3, chamberX + chamberSize - 1, chamberY + chamberSize - 1, COLOR_BRASS_HI);

        renderCelestialDie(guiGraphics, menu.isRolling(), chamberX + chamberSize / 2, chamberY + chamberSize / 2, partialTick, diceRotX, diceRotY, diceRotZ);

        // 下方幸运仪表盘 (70, 84, 56x22)
        int meterX = relX + 70;
        int meterY = relY + 84;
        drawOakPlaque(guiGraphics, meterX, meterY, 56, 22);

        guiGraphics.fill(meterX + 4, meterY + 4, meterX + 28, meterY + 7, 0xFFB91C1C);
        guiGraphics.fill(meterX + 28, meterY + 4, meterX + 52, meterY + 7, 0xFF16A34A);

        int needlePos = meterX + 4 + (int) Math.round(48.0 * (odds / 100.0));
        guiGraphics.fill(needlePos - 1, meterY + 2, needlePos + 1, meterY + 9, 0xFFFACC15);

        String oddsText = odds + "%";
        guiGraphics.drawString(font, oddsText, meterX + (56 - font.width(oddsText)) / 2, meterY + 11, 0xFFFDE047, true);
    }

    // -------------------------------------------------------------------------
    // 模式 1: 8 扇区幸运轮盘模式
    // -------------------------------------------------------------------------

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

        // 胡桃木与抛光黄铜外环底座 + 深翡翠丝绒背景
        guiGraphics.fill(centerX - 31, centerY - 29, centerX + 31, centerY + 29, COLOR_WALNUT);
        guiGraphics.fill(centerX - 29, centerY - 31, centerX + 29, centerY + 31, COLOR_WALNUT);
        guiGraphics.fill(centerX - 29, centerY - 28, centerX + 29, centerY + 28, COLOR_BRASS_D);
        guiGraphics.fill(centerX - 28, centerY - 29, centerX + 28, centerY + 29, COLOR_BRASS_D);
        guiGraphics.fill(centerX - 27, centerY - 27, centerX + 27, centerY + 27, 0xFF14241D);

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

        // 外圈红石灯/金钉交替铆钉
        for (int i = 0; i < 8; i++) {
            float a = i * 45.0F * Mth.DEG_TO_RAD;
            int px = (int) (29.0F * Mth.sin(a));
            int py = (int) (-29.0F * Mth.cos(a));
            int pegColor = (i % 2 == 0) ? 0xFFFDE047 : 0xFFDC2626;
            guiGraphics.fill(px - 1, py - 1, px + 2, py + 2, pegColor);
        }

        pose.popPose();

        // 黄铜红石中心轮毂
        guiGraphics.fill(centerX - 5, centerY - 5, centerX + 5, centerY + 5, 0xFFD4AF37);
        guiGraphics.fill(centerX - 3, centerY - 3, centerX + 3, centerY + 3, 0xFFB91C1C);
        guiGraphics.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, 0xFFFDE047);

        // 12 点钟黄铜红石指针
        pose.pushPose();
        pose.translate(centerX, centerY - 29, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(needleJiggle));

        guiGraphics.fill(-3, -7, 4, -1, 0xFF8C6214);
        guiGraphics.fill(-2, -6, 3, -2, 0xFFD4AF37);
        guiGraphics.fill(-2, -1, 3, 1, 0xFFFACC15);
        guiGraphics.fill(-1, 1, 2, 3, 0xFFDC2626);
        guiGraphics.fill(0, 3, 1, 5, 0xFFDC2626);

        pose.popPose();

        // 幸运槽 (70, 86, 56x16)
        int meterX = relX + 70;
        int meterY = relY + 86;
        drawOakPlaque(guiGraphics, meterX, meterY, 56, 16);

        int fillW = (int) (50.0F * (odds / 100.0F));
        guiGraphics.fill(meterX + 3, meterY + 12, meterX + 3 + fillW, meterY + 13, 0xFFF59E0B);

        Component luckText = Component.translatable("gui.universal_tool.lottery.wheel.luck_bar", odds);
        guiGraphics.drawString(font, luckText, meterX + (56 - font.width(luckText)) / 2, meterY + 2, 0xFFFDE047, true);
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

    // -------------------------------------------------------------------------
    // 模式 2: 三轴老虎机模式
    // -------------------------------------------------------------------------

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
            textColor = flash ? 0xFFF87171 : 0xFFFACC15;
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
                textColor = 0xFFD1D5DB;
            }
        }

        // 橡木牌匾底条确保彩色提示在 #C6C6C6 面板上高对比可读
        int centerX = relX + 98;
        int textW = font.width(jpText);
        int plaqueW = Math.max(100, Math.min(136, textW + 10));
        drawOakPlaque(guiGraphics, centerX - plaqueW / 2, bannerY - 2, plaqueW, 13);
        guiGraphics.drawString(font, jpText, centerX - textW / 2, bannerY + 1, textColor, true);

        int cabinetX = relX + 54;
        int cabinetY = relY + 36;
        int cabinetW = 88;
        int cabinetH = 62;

        // 胡桃木黄铜框 + 深翡翠丝绒老虎机舞台视窗
        drawRecessedWell(guiGraphics, cabinetX - 2, cabinetY - 2, cabinetW + 4, cabinetH + 4, COLOR_WALNUT);
        int cabinetBorder = isSuspense ? 0xFFDC2626 : COLOR_BRASS;
        guiGraphics.renderOutline(cabinetX - 1, cabinetY - 1, cabinetW + 2, cabinetH + 2, cabinetBorder);
        guiGraphics.fill(cabinetX, cabinetY, cabinetX + cabinetW, cabinetY + cabinetH, 0xFF16261F);

        int[] reelX = new int[]{cabinetX + 4, cabinetX + 32, cabinetX + 60};
        float[] scrolls = new float[]{reel0Scroll, reel1Scroll, reel2Scroll};

        guiGraphics.enableScissor(cabinetX + 2, cabinetY + 2, cabinetX + cabinetW - 2, cabinetY + cabinetH - 2);

        int paylineY = cabinetY + 23;
        for (int r = 0; r < 3; r++) {
            int cx = reelX[r];
            drawRecessedWell(guiGraphics, cx, cabinetY + 2, 24, cabinetH - 4, 0xFF1E342A);
            guiGraphics.renderOutline(cx + 1, cabinetY + 3, 22, cabinetH - 6, COLOR_BRASS_D);

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

        // 红石灯/金线中奖基线
        guiGraphics.fill(cabinetX + 1, paylineY + 8, cabinetX + cabinetW - 1, paylineY + 9, 0xFFDC2626);
        guiGraphics.fill(cabinetX + 1, paylineY + 7, cabinetX + cabinetW - 1, paylineY + 8, 0xFFFACC15);

        if (!menu.isRolling() && menu.getSlotReel1() == menu.getSlotReel2()
                && menu.getSlotReel2() == menu.getSlotReel3()) {
            for (int r = 0; r < 3; r++) {
                guiGraphics.renderOutline(reelX[r] + 2, paylineY - 2, 20, 20, 0xFFFACC15);
            }
        }

        renderLever(guiGraphics, relX + 185, relY + 44, leverPullTimer);
    }

    private static void renderLever(GuiGraphics guiGraphics, int pivotX, int pivotY, float leverPullTimer) {
        // 胡桃木与抛光黄铜机械底座
        guiGraphics.fill(pivotX - 4, pivotY + 11, pivotX + 8, pivotY + 21, COLOR_UMBER);
        guiGraphics.fill(pivotX - 3, pivotY + 12, pivotX + 7, pivotY + 20, COLOR_WALNUT);
        guiGraphics.renderOutline(pivotX - 3, pivotY + 12, 10, 8, COLOR_BRASS);

        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(pivotX + 2, pivotY + 14, 0);

        float pullAngle = -25.0F + (float) Math.sin(leverPullTimer * Math.PI) * 60.0F;
        pose.mulPose(Axis.ZP.rotationDegrees(pullAngle));

        guiGraphics.fill(-2, -18, 2, 0, COLOR_BRASS_HI);
        guiGraphics.renderOutline(-2, -18, 4, 18, COLOR_BRASS_D);

        // 红宝石镶金机械拉杆球头
        guiGraphics.fill(-5, -24, 5, -16, 0xFFB91C1C);
        guiGraphics.renderOutline(-5, -24, 10, 8, COLOR_BRASS);
        guiGraphics.fill(-3, -22, 1, -19, 0xFFFCA5A5);

        pose.popPose();
    }

    static void drawRarityBadge(GuiGraphics guiGraphics, Font font, int x, int y, Component label, int color, boolean active) {
        if (active) {
            int labelW = font.width(label);
            guiGraphics.fill(x - 1, y - 1, x + 7 + labelW + 1, y + 9, 0xFF241B14);
            guiGraphics.renderOutline(x - 1, y - 1, 8 + labelW + 2, 10, COLOR_BRASS_D);
            guiGraphics.fill(x, y + 2, x + 4, y + 6, color);
            guiGraphics.drawString(font, label, x + 6, y, color, true);
        } else {
            guiGraphics.fill(x, y + 2, x + 4, y + 6, 0xFFA6967C);
            guiGraphics.drawString(font, label, x + 6, y, 0xFF735F46, false);
        }
    }

    // -------------------------------------------------------------------------
    // 3D 象牙骨质金边骰子渲染
    // -------------------------------------------------------------------------

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
        guiGraphics.fill(-is, -is, is, is, 0xFFD0C2A8);
        guiGraphics.fill(-is + 1, -is + 1, is - 1, is - 1, 0xFFEDE4D2);
        guiGraphics.renderOutline(-is, -is, is * 2, is * 2, 0xFF9C773B);

        int pipColor = (pips == 1) ? 0xFFB91C1C : 0xFF292524;
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
