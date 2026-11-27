package org.gwfx.universaltool.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;

/**
 * 「克莱因超维星舱」克制深海玻璃视觉原语库。
 * 强调低噪点层级、柔和微凹槽位与精准高亮反馈，让物品图标成为绝对视觉主角。
 */
final class KleinScreenStyle {
    // ===== 深海午夜基底 =====
    static final int VOID_TOP = 0xF4070C17;
    static final int VOID_BOT = 0xF40B1424;
    static final int WELL_BG = 0xEB050A13;
    static final int SLOT_BG = 0xFF0A1220;
    static final int SLOT_BORDER = 0xFF15253B;
    static final int SLOT_HOVER_BG = 0xFF13293D;

    static final int BORDER_SUBTLE = 0xFF1A2F47;
    static final int BORDER_MID = 0xFF284B6B;

    // ===== 文字色阶 =====
    static final int TEXT_BRIGHT = 0xFFF2FBFF;
    static final int TEXT_MAIN = 0xFFC8E0EE;
    static final int TEXT_MUTED = 0xFF69879E;

    // ===== 三大工作能量主题 =====
    static final int CYAN = 0xFF39E5CE;
    static final int CYAN_GLOW = 0xFF9EFFF1;

    static final int AMBER = 0xFFFFA53B;
    static final int AMBER_GLOW = 0xFFFFE599;

    static final int VIOLET = 0xFFB880FF;
    static final int VIOLET_GLOW = 0xFFE6CEFF;
    static final int VIOLET_DARK = 0xFF281642;

    static final int EMERALD = 0xFF4BE896;
    static final int CORAL = 0xFFFF6D78;

    static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** 深海磨砂玻璃主舱外框 + 极简高光顶缘 + 精致四角星标锚点。 */
    static void obsidianPanel(GuiGraphics g, int x, int y, int w, int h, int accent, float arrival) {
        g.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x66010308);
        g.fillGradient(x, y, x + w, y + h, VOID_TOP, VOID_BOT);
        frame(g, x, y, w, h, BORDER_SUBTLE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, opacity(accent, 0.16f));

        // 顶部丝滑展开的细量子光条
        int barW = Math.round((w - 6) * easeOut(arrival));
        if (barW > 0) {
            g.fill(x + 3, y, x + 3 + barW, y + 1, accent);
        }

        // 纤巧四角锚点（克制不喧宾夺主）
        int arm = 5;
        drawCorner(g, x, y, 1, 1, arm, accent);
        drawCorner(g, x + w - 1, y, -1, 1, arm, accent);
        drawCorner(g, x, y + h - 1, 1, -1, arm, opacity(accent, 0.55f));
        drawCorner(g, x + w - 1, y + h - 1, -1, -1, arm, opacity(accent, 0.55f));
    }

    private static void drawCorner(GuiGraphics g, int cx, int cy, int sx, int sy, int arm, int color) {
        for (int i = 0; i < arm; i++) {
            int col = i == 0 ? TEXT_BRIGHT : color;
            g.fill(cx + sx * i, cy, cx + sx * i + 1, cy + 1, col);
            g.fill(cx, cy + sy * i, cx + 1, cy + sy * i + 1, col);
        }
    }

    /** 深邃内嵌视窗卡（用于仓库区、背包区与输入框）。 */
    static void well(GuiGraphics g, int x, int y, int w, int h, int borderColor) {
        g.fill(x, y, x + w, y + h, WELL_BG);
        frame(g, x, y, w, h, borderColor);
    }

    /** 柔和深空星尘微光背景（低干扰、高质感）。 */
    static void drawAmbientStars(GuiGraphics g, int x, int y, int w, int h, int accent, float timeSec) {
        for (int i = 0; i < 14; i++) {
            int hx = Math.floorMod(i * 521 + 29, Math.max(1, w - 8));
            int hy = Math.floorMod(Math.round(i * 193 - timeSec * (3.2f + (i % 3) * 1.8f)), Math.max(1, h - 8));
            float tw = 0.14f + 0.28f * (0.5f + 0.5f * (float) Math.sin(timeSec * 1.8f + i * 1.5f));
            int col = (i % 3 == 0) ? TEXT_BRIGHT : accent;
            g.fill(x + 4 + hx, y + 4 + hy, x + 5 + hx, y + 5 + hy, opacity(col, tw));
        }
    }

    /** 低噪点柔和微凹槽位（去除刺眼网格感，突出彩色物品本身）。 */
    static void socket(GuiGraphics g, int x, int y, boolean locked) {
        g.fill(x, y, x + 17, y + 17, locked ? 0xFF1A112E : SLOT_BG);
        frame(g, x, y, 17, 17, locked ? VIOLET : SLOT_BORDER);
        if (locked) {
            g.fill(x + 1, y + 1, x + 16, y + 2, opacity(VIOLET_GLOW, 0.28f));
        }
    }

    /** 悬停格子四角动态准星。 */
    static void focusReticle(GuiGraphics g, int x, int y, int size, int accent, float pulse) {
        int col = opacity(accent, 0.70f + 0.30f * pulse);
        int arm = 4;
        g.fill(x, y, x + arm, y + 1, col);
        g.fill(x, y, x + 1, y + arm, col);
        g.fill(x + size - arm, y, x + size, y + 1, col);
        g.fill(x + size - 1, y, x + size, y + arm, col);
        g.fill(x, y + size - 1, x + arm, y + size, col);
        g.fill(x, y + size - arm, x + 1, y + size, col);
        g.fill(x + size - arm, y + size - 1, x + size, y + size, col);
        g.fill(x + size - 1, y + size - arm, x + size, y + size, col);
    }

    /** 主舱与展开工作翼之间的磁吸能量锁扣导管。 */
    static void drawConduit(GuiGraphics g, int x1, int x2, int y, int accent, float timeSec) {
        int len = Math.max(1, x2 - x1);
        g.fill(x1, y, x2, y + 1, opacity(accent, 0.65f));
        g.fill(x1, y + 2, x2, y + 3, opacity(accent, 0.35f));
        float t = (timeSec * 1.9f) % 1.0f;
        int px = x1 + Math.round((len - 1) * t);
        g.fill(px, y, Math.min(x2, px + 2), y + 3, TEXT_BRIGHT);
    }

    /** 超维熔炉旋转反应星环。 */
    static void drawReactorRing(GuiGraphics g, int cx, int cy, float radius, int accent, int glow, float timeSec) {
        int dots = 24;
        for (int i = 0; i < dots; i++) {
            double a = (Math.PI * 2.0 * i / dots) + timeSec * 1.6;
            int px = cx + (int) Math.round(Math.cos(a) * radius);
            int py = cy + (int) Math.round(Math.sin(a) * radius * 0.55);
            g.fill(px, py, px + 1, py + 1, opacity(i % 6 == 0 ? glow : accent, 0.72f));
        }
    }

    /** 克制发光按钮。 */
    static void button(GuiGraphics g, int x, int y, int w, int h, float hover, int accent, boolean active) {
        float amount = active ? 1.0f : 0.72f * hover;
        int topBg = FastColor.ARGB32.lerp(amount, 0xFF0E182A, opacity(accent, 0.34f) | 0xFF0E1C30);
        int botBg = FastColor.ARGB32.lerp(amount, 0xFF09101E, 0xFF0C1E30);
        g.fillGradient(x, y, x + w, y + h, topBg, botBg);
        int border = active ? accent : FastColor.ARGB32.lerp(hover, BORDER_SUBTLE, accent);
        frame(g, x, y, w, h, border);
        if (active) {
            g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, accent);
        }
    }

    /** 紧凑全息分类药丸。 */
    static void pill(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
                     boolean active, boolean hovered, int accent) {
        int bg = active ? opacity(accent, 0.26f) : (hovered ? 0xCC14243B : 0xCC09111F);
        int border = active ? accent : (hovered ? BORDER_MID : BORDER_SUBTLE);
        int textCol = active ? TEXT_BRIGHT : (hovered ? TEXT_MAIN : TEXT_MUTED);
        g.fill(x, y, x + w, y + h, bg);
        frame(g, x, y, w, h, border);
        if (active) {
            g.fill(x + 2, y + h - 1, x + w - 2, y + h, TEXT_BRIGHT);
        }
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, textCol);
    }

    /** 物理拨片式微开关（用于传输过渡带上的「自动入库」唯一开关）。 */
    static void toggleSwitch(GuiGraphics g, int x, int y, float anim01) {
        float s = smooth(anim01);
        int track = FastColor.ARGB32.lerp(s, 0xFF1F3347, EMERALD);
        g.fill(x, y, x + 14, y + 7, track);
        frame(g, x, y, 14, 7, FastColor.ARGB32.lerp(s, BORDER_MID, EMERALD));
        int knobX = x + 1 + Math.round(6 * s);
        g.fill(knobX, y + 1, knobX + 6, y + 6, TEXT_BRIGHT);
    }

    /** 周期性扫光光泽。 */
    static void drawShimmer(GuiGraphics g, int x, int y, int w, int h, int color, float phase) {
        float t = phase - (float) Math.floor(phase);
        if (t > 0.30f || w <= 4) return;
        float n = t / 0.30f;
        int sw = Math.max(8, w / 3);
        int sx = x - sw + Math.round((w + sw * 2) * n);
        int x0 = Math.max(x + 1, sx);
        int x1 = Math.min(x + w - 1, sx + sw);
        if (x1 > x0) {
            float a = 0.20f * (float) Math.sin(n * Math.PI);
            g.fill(x0, y + 1, x1, y + h - 1, opacity(color, a));
        }
    }

    static int opacity(int color, float opacity) {
        int a = Math.max(0, Math.min(255, Math.round(255f * opacity)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private KleinScreenStyle() {}
}
