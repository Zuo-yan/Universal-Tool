package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * 「星辉秘仪 · 深空鎏金星盘」视觉风格原语与动态特效绘制库。
 */
final class SkillScreenStyle {
    // ===== 深空与鎏金核心色盘 =====
    static final int VOID_BG_TOP = 0xFF070A16;
    static final int VOID_BG_BOTTOM = 0xFF10172C;
    static final int PANEL_DARK = 0xEC0C1223;
    static final int PANEL_CARD = 0xE2121A32;
    static final int PANEL_INNER = 0xFF080C18;
    static final int SLOT_BG = 0xFF151E38;

    static final int GOLD_BRIGHT = 0xFFFFF1BA;
    static final int GOLD = 0xFFE8C26A;
    static final int GOLD_DIM = 0xFF8C6B2C;
    static final int GOLD_DARK = 0xFF453314;

    static final int BORDER_SUBTLE = 0xFF283558;
    static final int BORDER_ACTIVE = 0xFFE8C26A;

    static final int TEXT_TITLE = 0xFFFFF4D2;
    static final int TEXT_WHITE = 0xFFE6ECF5;
    static final int TEXT_BODY = 0xFFB8C4DA;
    static final int TEXT_MUTED = 0xFF7B8BA8;
    static final int TEXT_GOLD = 0xFFF5CF71;
    static final int TEXT_GREEN = 0xFF6CE89D;
    static final int TEXT_RED = 0xFFFF7A7A;
    static final int TEXT_CYAN = 0xFF6FE6F2;

    static final int BADGE_ACTIVE_BG = 0xFF421A24;
    static final int BADGE_ACTIVE_BORDER = 0xFFFF7366;
    static final int BADGE_PASSIVE_BG = 0xFF143530;
    static final int BADGE_PASSIVE_BORDER = 0xFF4BE0B8;

    static ResourceLocation icon(String skillId) {
        return ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "textures/gui/skills/" + skillId + ".png");
    }

    static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static float easeOutCubic(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** 深空鎏金外框与四角星仪角标。 */
    static void astrolabeWindowFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, VOID_BG_TOP, VOID_BG_BOTTOM);
        outline(g, x, y, w, h, GOLD_DARK);
        outline(g, x + 1, y + 1, w - 2, h - 2, GOLD);
        outline(g, x + 3, y + 3, w - 6, h - 6, BORDER_SUBTLE);

        int arm = 10;
        drawCornerBracket(g, x + 1, y + 1, 1, 1, arm);
        drawCornerBracket(g, x + w - 2, y + 1, -1, 1, arm);
        drawCornerBracket(g, x + 1, y + h - 2, 1, -1, arm);
        drawCornerBracket(g, x + w - 2, y + h - 2, -1, -1, arm);
    }

    private static void drawCornerBracket(GuiGraphics g, int cx, int cy, int sx, int sy, int arm) {
        for (int i = 0; i < arm; i++) {
            int col = i < 3 ? GOLD_BRIGHT : GOLD;
            g.fill(cx + sx * i, cy, cx + sx * i + 1, cy + 1, col);
            g.fill(cx, cy + sy * i, cx + 1, cy + sy * i + 1, col);
        }
        g.fill(cx + sx * 2, cy + sy * 2, cx + sx * 2 + 1, cy + sy * 2 + 1, GOLD_BRIGHT);
    }

    static void glassCard(GuiGraphics g, int x, int y, int w, int h, int borderColor) {
        g.fill(x, y, x + w, y + h, PANEL_CARD);
        outline(g, x, y, w, h, borderColor);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(GOLD_BRIGHT, 0.14F));
    }

    static void recessedWell(GuiGraphics g, int x, int y, int w, int h, int borderColor) {
        g.fill(x, y, x + w, y + h, PANEL_INNER);
        outline(g, x, y, w, h, borderColor);
    }

    /** 视差动态深空群星点阵。 */
    static void drawStarfieldBackground(GuiGraphics g, int x, int y, int w, int h, float timeSec, float camX, float camY) {
        g.fillGradient(x, y, x + w, y + h, VOID_BG_TOP, VOID_BG_BOTTOM);
        for (int i = 0; i < 90; i++) {
            int hash1 = Integer.hashCode(i * 7919 + 17);
            int hash2 = Integer.hashCode(i * 104729 + 31);
            float depth = (i % 3 == 0) ? 0.45F : (i % 3 == 1 ? 0.25F : 0.12F);
            int sx = x + Math.floorMod(Math.round((hash1 & 0xFFFF) - camX * depth), Math.max(1, w));
            int sy = y + Math.floorMod(Math.round((hash2 & 0xFFFF) - camY * depth), Math.max(1, h));
            float twinkle = 0.35F + 0.55F * (0.5F + 0.5F * (float) Math.sin(timeSec * (1.4F + (i % 5) * 0.4F) + i));
            int base = (i % 7 == 0) ? GOLD_BRIGHT : (i % 5 == 0 ? 0xFF9AC5FF : 0xFFD9E6FF);
            int col = withAlpha(base, twinkle);
            g.fill(sx, sy, sx + 1, sy + 1, col);
            if (i % 13 == 0 && sx + 1 < x + w && sy + 1 < y + h && twinkle > 0.72F) {
                g.fill(sx - 1, sy, sx + 2, sy + 1, withAlpha(base, twinkle * 0.4F));
                g.fill(sx, sy - 1, sx + 1, sy + 2, withAlpha(base, twinkle * 0.4F));
            }
        }
    }

    /** 绘制观星仪圆形刻度环。 */
    static void drawAstrolabeRing(GuiGraphics g, int cx, int cy, float radius, int color, int dots, float rotRad) {
        for (int i = 0; i < dots; i++) {
            double a = rotRad + Math.PI * 2.0 * i / dots;
            int px = cx + (int) Math.round(Math.cos(a) * radius);
            int py = cy + (int) Math.round(Math.sin(a) * radius);
            if (i % 6 == 0) {
                g.fill(px - 1, py - 1, px + 2, py + 2, color);
            } else {
                g.fill(px, py, px + 1, py + 1, withAlpha(color, 0.55F));
            }
        }
    }

    /** 绘制连接两颗星的发光星轨与能量流光脉冲。 */
    static void drawStarTrackLine(GuiGraphics g, int x1, int y1, int x2, int y2,
                                  int baseColor, int glowColor, boolean active, boolean highlighted, float timeSec) {
        int steps = Math.max(8, (int) Math.hypot(x2 - x1, y2 - y1) / 2);
        int lineColor = highlighted ? withAlpha(glowColor, 0.92F)
                : (active ? withAlpha(baseColor, 0.65F) : withAlpha(0xFF33415C, 0.55F));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(x1 + (x2 - x1) * t);
            int py = Math.round(y1 + (y2 - y1) * t);
            g.fill(px, py, px + 1, py + 1, lineColor);
            if (active || highlighted) {
                g.fill(px, py + 1, px + 1, py + 2, withAlpha(lineColor, 0.32F));
            }
        }
        if (active || highlighted) {
            float pulseT = (timeSec * 0.65F) % 1.0F;
            int px = Math.round(x1 + (x2 - x1) * pulseT);
            int py = Math.round(y1 + (y2 - y1) * pulseT);
            g.fill(px - 1, py - 1, px + 2, py + 2, withAlpha(GOLD_BRIGHT, 0.95F));
        }
    }

    static void drawStarGlyph(GuiGraphics g, int cx, int cy, int r, int color) {
        g.fill(cx, cy - r, cx + 1, cy + r + 1, color);
        g.fill(cx - r, cy, cx + r + 1, cy + 1, color);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, GOLD_BRIGHT);
    }

    static void skillIcon(GuiGraphics g, String id, int x, int y, int size, boolean dimmed) {
        recessedWell(g, x, y, size, size, GOLD_DIM);
        int inner = size - 2;
        if (inner > 0) {
            g.blit(icon(id), x + 1, y + 1, inner, inner, 0.0F, 0.0F, 32, 32, 32, 32);
        }
        if (dimmed) {
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x99060912);
        }
    }

    static void masteryDiamonds(GuiGraphics g, int x, int y, int learned, int max, int accentColor) {
        for (int i = 0; i < max; i++) {
            int px = x + i * 7;
            if (i < learned) {
                g.fill(px, y, px + 5, y + 5, GOLD_DARK);
                g.fill(px + 1, y + 1, px + 4, y + 4, learned >= max ? GOLD_BRIGHT : accentColor);
                g.fill(px + 2, y + 2, px + 3, y + 3, 0xFFFFFFFF);
            } else {
                g.fill(px, y, px + 5, y + 5, 0xFF1A2238);
                g.fill(px + 1, y + 1, px + 4, y + 4, 0xFF394666);
            }
        }
    }

    static void progressBar(GuiGraphics g, int x, int y, int w, int h, float ratio, int fillStart, int fillEnd, String label) {
        recessedWell(g, x, y, w, h, GOLD_DIM);
        int innerW = Math.max(0, w - 2);
        int filled = Math.max(0, Math.min(innerW, Math.round(innerW * Math.max(0.0F, Math.min(1.0F, ratio)))));
        if (filled > 0) {
            for (int px = 0; px < filled; px++) {
                float t = px / (float) Math.max(1, innerW);
                int col = lerpColor(fillStart, fillEnd, t);
                g.fill(x + 1 + px, y + 1, x + 2 + px, y + h - 1, col);
            }
            if (h >= 5) {
                g.fill(x + 1, y + 1, x + 1 + filled, y + 2, withAlpha(0xFFFFFFFF, 0.35F));
            }
            g.fill(x + filled, y + 1, x + filled + 1, y + h - 1, GOLD_BRIGHT);
        }
        if (label != null && !label.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int textX = x + w / 2 - font.width(label) / 2;
            int textY = y + (h - 8) / 2;
            // 在偏亮或高对比背景上增加 1px 深空暗影，确保 MAX 与百分比清晰锐利
            g.drawString(font, label, textX + 1, textY + 1, VOID_BG_TOP, false);
            g.drawString(font, label, textX, textY, TEXT_TITLE, false);
        }
    }

    static void button(GuiGraphics g, int x, int y, int w, int h, String text, boolean enabled, boolean hovered, int accentColor) {
        int border = !enabled ? BORDER_SUBTLE : (hovered ? GOLD_BRIGHT : accentColor);
        int bgTop = !enabled ? 0xFF141A2C : (hovered ? 0xFF2B3554 : 0xFF1B233D);
        int bgBot = !enabled ? 0xFF0E1322 : (hovered ? 0xFF1D2540 : 0xFF11172B);
        int textCol = !enabled ? TEXT_MUTED : (hovered ? GOLD_BRIGHT : TEXT_WHITE);

        g.fillGradient(x, y, x + w, y + h, bgTop, bgBot);
        outline(g, x, y, w, h, border);
        if (enabled && hovered) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(GOLD_BRIGHT, 0.45F));
        }

        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, trim(font, text, Math.max(8, w - 8)), x + w / 2, y + (h - 8) / 2, textCol);
    }

    static void closeButton(GuiGraphics g, int x, int y, int size, boolean hovered) {
        int bg = hovered ? 0xFF7B2330 : 0xFF232B45;
        int border = hovered ? 0xFFFF8C8C : GOLD;
        g.fill(x, y, x + size, y + size, bg);
        outline(g, x, y, size, size, border);
        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, "×", x + size / 2, y + (size - 8) / 2, hovered ? 0xFFFFF0F0 : GOLD_BRIGHT);
    }

    static void badge(GuiGraphics g, int x, int y, int w, int h, String text, int bg, int border, int textColor) {
        g.fill(x, y, x + w, y + h, bg);
        outline(g, x, y, w, h, border);
        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    /** 带顶部主色调光带的精致玻璃子卡容器。 */
    static void accentCard(GuiGraphics g, int x, int y, int w, int h, int accentColor) {
        g.fillGradient(x, y, x + w, y + h, 0xE611192E, 0xE60B1020);
        outline(g, x, y, w, h, BORDER_SUBTLE);
        g.fill(x + 1, y, x + w - 1, y + 1, withAlpha(accentColor, 0.75F));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, withAlpha(accentColor, 0.22F));
    }

    /** 精致胶囊筛选开关按钮（用于星图底部与图鉴主/被筛选）。 */
    static void filterPill(GuiGraphics g, int x, int y, int w, int h, String text,
                           boolean active, boolean hovered, int accentColor) {
        int bg = active ? withAlpha(accentColor, 0.34F) : (hovered ? 0xCC1E2845 : 0xCC0D1324);
        int border = active ? accentColor : (hovered ? GOLD_DIM : BORDER_SUBTLE);
        int textCol = active ? GOLD_BRIGHT : (hovered ? TEXT_WHITE : TEXT_BODY);
        g.fill(x, y, x + w, y + h, bg);
        outline(g, x, y, w, h, border);
        if (active) {
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, accentColor);
        }
        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, trim(font, text, Math.max(8, w - 4)), x + w / 2, y + (h - 8) / 2, textCol);
    }

    /** 星座共鸣 2/4/6 星三颗微型点亮星钻指示器。 */
    static void resonanceDiamonds(GuiGraphics g, int x, int y, int tier, int accentColor) {
        for (int i = 0; i < 3; i++) {
            int dx = x + i * 6;
            boolean lit = i < tier;
            int col = lit ? (i == 2 ? GOLD_BRIGHT : accentColor) : 0xFF2B3652;
            g.fill(dx + 1, y, dx + 3, y + 4, col);
            g.fill(dx, y + 1, dx + 4, y + 3, col);
            if (lit) {
                g.fill(dx + 1, y + 1, dx + 3, y + 3, 0xFFFFFFFF);
            }
        }
    }

    /** 扫光高光条：在激活共鸣卡或重要按钮上周期性柔光掠过。 */
    static void drawShimmerSweep(GuiGraphics g, int x, int y, int w, int h, int color, float phase01) {
        if (w <= 4 || h <= 2) return;
        float t = phase01 - (float) Math.floor(phase01);
        if (t > 0.35F) return; // 仅在周期前 35% 扫过，留出呼吸感
        float norm = t / 0.35F;
        int sweepW = Math.max(8, w / 4);
        int sx = x - sweepW + Math.round((w + sweepW * 2) * norm);
        int x0 = Math.max(x + 1, sx);
        int x1 = Math.min(x + w - 1, sx + sweepW);
        if (x1 > x0) {
            float alpha = 0.24F * (float) Math.sin(norm * Math.PI);
            g.fill(x0, y + 1, x1, y + h - 1, withAlpha(color, alpha));
        }
    }

    /** 带双脉冲流星与可调透明度的高级星轨连线。 */
    static void drawStarTrackLineAlpha(GuiGraphics g, int x1, int y1, int x2, int y2,
                                       int baseColor, int glowColor, boolean active,
                                       boolean highlighted, float alphaScale, float timeSec) {
        int steps = Math.max(8, (int) Math.hypot(x2 - x1, y2 - y1) / 2);
        float a = Math.max(0.12F, Math.min(1.0F, alphaScale));
        int lineColor = highlighted ? withAlpha(glowColor, 0.96F * a)
                : (active ? withAlpha(baseColor, 0.68F * a) : withAlpha(0xFF33415C, 0.50F * a));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(x1 + (x2 - x1) * t);
            int py = Math.round(y1 + (y2 - y1) * t);
            g.fill(px, py, px + 1, py + 1, lineColor);
            if (active || highlighted) {
                g.fill(px, py + 1, px + 1, py + 2, withAlpha(lineColor, 0.34F));
            }
        }
        if ((active || highlighted) && a > 0.45F) {
            int pulses = highlighted ? 2 : 1;
            for (int p = 0; p < pulses; p++) {
                float pulseT = (timeSec * (highlighted ? 0.95F : 0.62F) + p * 0.5F) % 1.0F;
                int px = Math.round(x1 + (x2 - x1) * pulseT);
                int py = Math.round(y1 + (y2 - y1) * pulseT);
                g.fill(px - 1, py - 1, px + 2, py + 2, withAlpha(GOLD_BRIGHT, 0.95F * a));
            }
        }
    }

    static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private SkillScreenStyle() {}
}
