package org.gwfx.universaltool.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;

/** Shared rendering for the terminal and its workstations. Colors are opaque unless used as an overlay. */
final class KleinScreenStyle {
    static final int PAPER = 0xFFFFFDF6;
    static final int WHITE = 0xFFFFFFFF;
    static final int INK = 0xFF102E3A;
    static final int SECONDARY = 0xFF38545C;
    static final int BORDER = 0xFF9EB8BC;
    static final int RECESS = 0xFFDCE9E8;
    static final int TEAL = 0xFF086E67;
    static final int MINT = 0xFFCEF1E2;
    static final int AMBER = 0xFF91491A;
    static final int GOLD = 0xFFFFE6B2;
    static final int VIOLET = 0xFF593B8D;
    static final int LILAC = 0xFFEDE3FF;

    static void card(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x + 1, y + 2, x + w + 1, y + h + 1, 0x4D10212A);
        g.fill(x + 2, y, x + w - 2, y + h, BORDER);
        g.fill(x, y + 2, x + w, y + h - 2, BORDER);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 2, fill);
        g.fill(x + 2, y + 1, x + w - 2, y + h - 1, fill);
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    static void socket(GuiGraphics g, int x, int y, boolean locked) {
        g.fill(x, y, x + 17, y + 17, locked ? LILAC : WHITE);
        frame(g, x, y, 17, 17, locked ? VIOLET : BORDER);
        g.fill(x + 1, y + 15, x + 16, y + 16, locked ? 0xFFD5C6F5 : 0xFFEEF4F3);
    }

    static void button(GuiGraphics g, int x, int y, int w, int h, float hover, int tint, int ink, boolean active) {
        float amount = active ? 1 : 0.85f * hover;
        g.fill(x, y, x + w, y + h, FastColor.ARGB32.lerp(amount, WHITE, tint));
        frame(g, x, y, w, h, FastColor.ARGB32.lerp(amount, BORDER, ink));
        if (active) g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, ink);
    }

    static int opacity(int color, float opacity) {
        return (Math.round(255f * opacity) << 24) | (color & 0x00FFFFFF);
    }

    static float easeOut(float t) {
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }

    static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private KleinScreenStyle() {}
}
