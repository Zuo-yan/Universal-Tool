package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Visual styling and drawing primitives for the skill management and wheel screens.
 *
 * <p>Uses the "Create / Ars Nouveau" Warm Illuminated Parchment, Walnut Wood &amp; Burnished Brass
 * palette: rich walnut outer molding with brass corner brackets, cream-parchment cards with brass
 * hairline borders, dark-walnut recessed wells for high-contrast stats, and 32x32 skill icons.
 */
final class SkillScreenStyle {
    static final int BACKDROP = 0x88120B06;
    static final int FRAME_BG = 0xFFEFE5CF;
    static final int OUTER_BORDER = 0xFF1E140C;
    static final int WALNUT_DARK = 0xFF382415;
    static final int WALNUT = 0xFF5C4028;
    static final int WALNUT_HI = 0xFF7D593A;

    static final int BEVEL_HI = 0xFFF7EEDB;
    static final int BEVEL_LO = 0xFF9E7330;
    static final int SLOT_SHADOW = 0xFF4A331F;

    static final int BORDER_BRONZE = 0xFF9E7330;
    static final int BORDER_BRONZE_HI = 0xFFD6A84E;
    static final int BORDER_GOLD = 0xFFD6A84E;
    static final int BORDER_GOLD_GLOW = 0xFFF2D27A;

    static final int PANEL_BG = 0xFFE8DAC0;
    static final int PANEL_BG_ALT = 0xFFDFCEAE;
    static final int PANEL_INNER = 0xFF2B2017;
    static final int CARD_BORDER = 0xFF9E7330;

    static final int ROW_BG = 0xFF3A2A1E;
    static final int ROW_HOVER = 0xFF4D3929;
    static final int ROW_SELECTED = 0xFF2C4D3A;

    static final int TEXT_HEADER = 0xFF3A281A;
    static final int TEXT_GOLD = 0xFFF2D27A;
    static final int TEXT_WHITE = 0xFFF7ECDA;
    static final int TEXT_DARK = 0xFF2A1C12;
    static final int TEXT_BODY = 0xFF4F3B2A;
    static final int TEXT_BODY_LIGHT = 0xFFEADBC0;
    static final int TEXT_MUTED = 0xFF6E553D;
    static final int TEXT_MUTED_LIGHT = 0xFFC9B79C;
    static final int TEXT_DIM = 0xFF8C7458;

    static final int GREEN = 0xFF82E86B;
    static final int RED = 0xFFFF7A6E;
    static final int RED_DARK = 0xFFA63429;
    static final int TEAL = 0xFF6CE3D8;

    static final int BADGE_ACTIVE_BG = 0xFF8A2424;
    static final int BADGE_ACTIVE_BORDER = 0xFF3D0E0E;
    static final int BADGE_ACTIVE_TEXT = 0xFFFFEAE4;

    static final int BADGE_PASSIVE_BG = 0xFF2B613E;
    static final int BADGE_PASSIVE_BORDER = 0xFF143620;
    static final int BADGE_PASSIVE_TEXT = 0xFFDCFFE5;

    static final int BADGE_ROLE_BG = 0xFF5C4630;
    static final int BADGE_ROLE_BORDER = 0xFF2E2115;
    static final int BADGE_ROLE_TEXT = 0xFFF5EAD6;

    static ResourceLocation icon(String skillId) {
        return ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "textures/gui/skills/" + skillId + ".png");
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        // Warm Walnut + Burnished Brass + Illuminated Parchment outer window frame.
        g.fill(x, y, x + w, y + h, OUTER_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, WALNUT);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, WALNUT_HI);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, WALNUT_HI);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, WALNUT_DARK);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, WALNUT_DARK);

        outline(g, x + 3, y + 3, w - 6, h - 6, BORDER_BRONZE);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, FRAME_BG);

        // Burnished brass corner brackets
        int arm = Math.min(10, Math.min(w, h) / 6);
        drawCornerBracket(g, x + 1, y + 1, 1, 1, arm);
        drawCornerBracket(g, x + w - 2, y + 1, -1, 1, arm);
        drawCornerBracket(g, x + 1, y + h - 2, 1, -1, arm);
        drawCornerBracket(g, x + w - 2, y + h - 2, -1, -1, arm);
    }

    private static void drawCornerBracket(GuiGraphics g, int cx, int cy, int sx, int sy, int arm) {
        for (int i = 0; i < arm; i++) {
            int col = i == 0 ? BORDER_GOLD_GLOW : (i < arm - 2 ? BORDER_GOLD : BORDER_BRONZE);
            g.fill(cx + sx * i, cy, cx + sx * i + 1, cy + 1, col);
            g.fill(cx, cy + sy * i, cx + 1, cy + sy * i + 1, col);
            if (i < 4) {
                g.fill(cx + sx * i, cy + sy, cx + sx * i + 1, cy + sy + 1, BORDER_BRONZE);
                g.fill(cx + sx, cy + sy * i, cx + sx + 1, cy + sy * i + 1, BORDER_BRONZE);
            }
        }
        g.fill(cx + sx, cy + sy, cx + sx + 1, cy + sy + 1, BORDER_GOLD_GLOW);
    }

    static void card(GuiGraphics g, int x, int y, int w, int h, boolean accent) {
        int bg = accent ? PANEL_BG : PANEL_BG_ALT;
        g.fill(x, y, x + w, y + h, bg);
        outline(g, x, y, w, h, CARD_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, BEVEL_HI);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, BEVEL_HI);
    }

    static void recessed(GuiGraphics g, int x, int y, int w, int h, int bg) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x, y, x + w - 1, y + 1, OUTER_BORDER);
        g.fill(x, y, x + 1, y + h - 1, OUTER_BORDER);
        g.fill(x + 1, y + h - 1, x + w, y + h, BORDER_BRONZE);
        g.fill(x + w - 1, y + 1, x + w, y + h, BORDER_BRONZE);
        g.fill(x + w - 1, y, x + w, y + 1, bg);
        g.fill(x, y + h - 1, x + 1, y + h, bg);
    }

    static void raised(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, BORDER_GOLD);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, BORDER_GOLD);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF593E18);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF593E18);
    }

    static void tab(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean hovered) {
        Font font = Minecraft.getInstance().font;
        if (active) {
            g.fill(x, y, x + w, y + h, OUTER_BORDER);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME_BG);
            g.fill(x + 1, y + 1, x + w - 1, y + 3, BORDER_GOLD);
            g.fill(x + 1, y + 1, x + 2, y + h - 1, BORDER_GOLD_GLOW);
            g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, BORDER_BRONZE);
            drawCenteredNoShadow(g, font, label, x + w / 2, y + (h - 8) / 2, TEXT_HEADER);
        } else {
            int bg = hovered ? 0xFF735134 : WALNUT;
            g.fill(x, y, x + w, y + h, OUTER_BORDER);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, hovered ? BORDER_GOLD : WALNUT_HI);
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, WALNUT_DARK);
            g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hovered ? TEXT_GOLD : TEXT_WHITE);
        }
    }

    static void closeButton(GuiGraphics g, int x, int y, int size, boolean hovered) {
        int bg = hovered ? 0xFFA83030 : 0xFF6E4E26;
        g.fill(x, y, x + size, y + size, hovered ? BORDER_GOLD_GLOW : OUTER_BORDER);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, bg);
        g.fill(x + 1, y + 1, x + size - 2, y + 2, hovered ? 0xFFE88A7E : BORDER_GOLD);
        g.fill(x + 1, y + 1, x + 2, y + size - 2, hovered ? 0xFFE88A7E : BORDER_GOLD);
        g.fill(x + 2, y + size - 2, x + size - 1, y + size - 1, hovered ? 0xFF521616 : WALNUT_DARK);
        g.fill(x + size - 2, y + 2, x + size - 1, y + size - 1, hovered ? 0xFF521616 : WALNUT_DARK);
        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, "X", x + size / 2, y + (size - 8) / 2, hovered ? 0xFFFFF5E0 : TEXT_WHITE);
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    static void drawDiamond(GuiGraphics g, int x, int y, int color, boolean filled) {
        if (filled) {
            g.fill(x + 2, y, x + 3, y + 1, color);
            g.fill(x + 1, y + 1, x + 4, y + 2, color);
            g.fill(x, y + 2, x + 5, y + 3, color);
            g.fill(x + 1, y + 3, x + 4, y + 4, color);
            g.fill(x + 2, y + 4, x + 3, y + 5, color);
            g.fill(x + 2, y + 2, x + 3, y + 3, 0xFFFFF8DC);
        } else {
            g.fill(x + 2, y, x + 3, y + 1, color);
            g.fill(x + 1, y + 1, x + 2, y + 2, color);
            g.fill(x + 3, y + 1, x + 4, y + 2, color);
            g.fill(x, y + 2, x + 1, y + 3, color);
            g.fill(x + 4, y + 2, x + 5, y + 3, color);
            g.fill(x + 1, y + 3, x + 2, y + 4, color);
            g.fill(x + 3, y + 3, x + 4, y + 4, color);
            g.fill(x + 2, y + 4, x + 3, y + 5, color);
        }
    }

    static void masteryDiamonds(GuiGraphics g, int x, int y, int learned, int max) {
        for (int i = 0; i < max; i++) {
            boolean filled = i < learned;
            int px = x + i * 7;
            if (filled) {
                g.fill(px, y, px + 5, y + 5, 0xFF3A281A);
                g.fill(px + 1, y + 1, px + 4, y + 4, BORDER_GOLD);
                g.fill(px + 1, y + 1, px + 2, y + 2, 0xFFFFF6C4);
            } else {
                g.fill(px, y, px + 5, y + 5, SLOT_SHADOW);
                g.fill(px + 1, y + 1, px + 4, y + 4, 0xFF7D6954);
            }
        }
    }

    static void skillIcon(GuiGraphics g, String id, int x, int y, int size, boolean locked) {
        recessed(g, x, y, size, size, 0xFF241B14);
        int iconInner = size - 2;
        if (iconInner > 0) {
            // Skill PNGs are 32x32 full-canvas sprites; sample full [0..32) cleanly.
            g.blit(icon(id), x + 1, y + 1, iconInner, iconInner, 0.0F, 0.0F, 32, 32, 32, 32);
        }
        if (locked) {
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x991A110A);
        }
    }

    static void progressBar(GuiGraphics g, int x, int y, int w, int h, float ratio, String label) {
        // Emerald-gold XP bar inside dark-walnut & brass recessed frame.
        recessed(g, x, y, w, h, 0xFF241B14);
        int filled = Math.max(0, Math.min(w - 2, Math.round((w - 2) * ratio)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, 0xFF3E9C4B);
            if (h >= 5) {
                g.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0xFF7CE871);
                g.fill(x + 1, y + h - 2, x + 1 + filled, y + h - 1, 0xFF286932);
            }
            // Gilded tip cap at the progress frontier
            int capX = x + filled;
            g.fill(capX, y + 1, capX + 1, y + h - 1, BORDER_GOLD_GLOW);
        }
        if (label != null && !label.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, TEXT_WHITE);
        }
    }

    static void badge(GuiGraphics g, int x, int y, int w, int h, String text, int bg, int border, int textColor) {
        g.fill(x, y, x + w, y + h, bg);
        outline(g, x, y, w, h, border);
        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    static void button(GuiGraphics g, int x, int y, int w, int h, String text, boolean enabled, boolean hovered) {
        int border = !enabled ? 0xFF2B2017 : (hovered ? BORDER_GOLD_GLOW : OUTER_BORDER);
        int bg = !enabled ? 0xFF47382B : (hovered ? 0xFFA37028 : 0xFF7D541E);
        int hi = !enabled ? 0xFF635040 : (hovered ? 0xFFF7DC88 : BORDER_GOLD);
        int lo = !enabled ? 0xFF2E231A : (hovered ? 0xFF5C3D12 : 0xFF422B0C);
        int textColor = !enabled ? 0xFF9C8B7A : (hovered ? 0xFFFFF8D6 : TEXT_WHITE);

        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, hi);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, hi);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, lo);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, lo);

        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, trim(font, text, w - 8), x + w / 2, y + (h - 8) / 2, textColor);
    }

    static void scrollbarVertical(GuiGraphics g, int x, int y, int w, int h,
                                  int scroll, int maxScroll, int visible, int total, boolean active) {
        recessed(g, x, y, w, h, 0xFF261B13);
        if (maxScroll <= 0 || total <= 0) return;

        int thumbH = Math.max(16, Math.round(h * ((float) visible / total)));
        int travel = h - thumbH;
        int thumbY = y + Math.round(travel * ((float) scroll / maxScroll));

        int thumbBg = active ? BORDER_GOLD : 0xFFB5863B;
        g.fill(x, thumbY, x + w, thumbY + thumbH, OUTER_BORDER);
        g.fill(x + 1, thumbY + 1, x + w - 1, thumbY + thumbH - 1, thumbBg);
        g.fill(x + 1, thumbY + 1, x + w - 1, thumbY + 2, BORDER_GOLD_GLOW);
        g.fill(x + 1, thumbY + thumbH - 2, x + w - 1, thumbY + thumbH - 1, 0xFF593E18);
    }

    static void drawCenteredNoShadow(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private SkillScreenStyle() {
    }
}
