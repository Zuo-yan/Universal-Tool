package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Visual styling and drawing primitives for the skill management and wheel screens.
 *
 * <p>Uses classic Minecraft {@code #C6C6C6} beveled stone-gray panels, {@code #8B8B8B} recessed
 * slots, {@code #404040} labels, and Advancement/Recipe-Book style tabs and buttons.
 */
final class SkillScreenStyle {
    static final int BACKDROP = 0x88000000;
    static final int FRAME_BG = 0xFFC6C6C6;
    static final int OUTER_BORDER = 0xFF000000;
    static final int BEVEL_HI = 0xFFFFFFFF;
    static final int BEVEL_LO = 0xFF555555;
    static final int SLOT_SHADOW = 0xFF373737;

    static final int BORDER_BRONZE = 0xFF555555;
    static final int BORDER_BRONZE_HI = 0xFFFFFFFF;
    static final int BORDER_GOLD = 0xFFFFD54F;
    static final int BORDER_GOLD_GLOW = 0xFFFFEA8A;

    static final int PANEL_BG = 0xFFC6C6C6;
    static final int PANEL_INNER = 0xFF8B8B8B;
    static final int CARD_BORDER = 0xFF555555;

    static final int ROW_BG = 0xFF8B8B8B;
    static final int ROW_HOVER = 0xFF9C9C9C;
    static final int ROW_SELECTED = 0xFF5D6E59;

    static final int TEXT_HEADER = 0xFF404040;
    static final int TEXT_GOLD = 0xFFFFFF55;
    static final int TEXT_WHITE = 0xFFFFFFFF;
    static final int TEXT_DARK = 0xFF2B2B2B;
    static final int TEXT_BODY = 0xFF373737;
    static final int TEXT_BODY_LIGHT = 0xFFF2F2F2;
    static final int TEXT_MUTED = 0xFF555555;
    static final int TEXT_MUTED_LIGHT = 0xFFE0E0E0;
    static final int TEXT_DIM = 0xFF5A5A5A;

    static final int GREEN = 0xFF80FF20;
    static final int RED = 0xFFFF5555;
    static final int RED_DARK = 0xFFAA0000;
    static final int TEAL = 0xFF55FFFF;

    static final int BADGE_ACTIVE_BG = 0xFF8F2D2D;
    static final int BADGE_ACTIVE_BORDER = 0xFF4A1414;
    static final int BADGE_ACTIVE_TEXT = 0xFFFFE5E5;

    static final int BADGE_PASSIVE_BG = 0xFF2E6B40;
    static final int BADGE_PASSIVE_BORDER = 0xFF173D22;
    static final int BADGE_PASSIVE_TEXT = 0xFFDCFFE5;

    static final int BADGE_ROLE_BG = 0xFF556070;
    static final int BADGE_ROLE_BORDER = 0xFF323842;
    static final int BADGE_ROLE_TEXT = 0xFFEBF2FA;

    static ResourceLocation icon(String skillId) {
        return ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "textures/gui/skills/" + skillId + ".png");
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        // Classic Minecraft #C6C6C6 container window with 2px bevel and crisp #000000 border.
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FRAME_BG);
        outline(g, x, y, w, h, OUTER_BORDER);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, BEVEL_HI);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, BEVEL_HI);
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, BEVEL_LO);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, BEVEL_LO);
        g.fill(x + w - 3, y + 1, x + w - 2, y + 2, FRAME_BG);
        g.fill(x + 1, y + h - 3, x + 2, y + h - 2, FRAME_BG);
    }

    static void card(GuiGraphics g, int x, int y, int w, int h, boolean accent) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        // Subtle etched MC group border.
        g.fill(x, y, x + w - 1, y + 1, BEVEL_LO);
        g.fill(x, y, x + 1, y + h - 1, BEVEL_LO);
        g.fill(x + 1, y + h - 1, x + w, y + h, BEVEL_HI);
        g.fill(x + w - 1, y + 1, x + w, y + h, BEVEL_HI);
        if (accent) {
            g.fill(x + 1, y + 1, x + w - 2, y + 2, 0xFFD8D8D8);
        }
    }

    static void recessed(GuiGraphics g, int x, int y, int w, int h, int bg) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x, y, x + w - 1, y + 1, SLOT_SHADOW);
        g.fill(x, y, x + 1, y + h - 1, SLOT_SHADOW);
        g.fill(x + 1, y + h - 1, x + w, y + h, BEVEL_HI);
        g.fill(x + w - 1, y + 1, x + w, y + h, BEVEL_HI);
        g.fill(x + w - 1, y, x + w, y + 1, bg);
        g.fill(x, y + h - 1, x + 1, y + h, bg);
    }

    static void raised(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, BEVEL_HI);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, BEVEL_HI);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, BEVEL_LO);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, BEVEL_LO);
    }

    static void tab(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean hovered) {
        Font font = Minecraft.getInstance().font;
        if (active) {
            raised(g, x, y, w, h, 0xFFD8D8D8, OUTER_BORDER);
            drawCenteredNoShadow(g, font, label, x + w / 2, y + (h - 8) / 2, TEXT_HEADER);
        } else {
            recessed(g, x, y, w, h, hovered ? ROW_HOVER : PANEL_INNER);
            g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hovered ? TEXT_WHITE : 0xFFE6E6E6);
        }
    }

    static void closeButton(GuiGraphics g, int x, int y, int size, boolean hovered) {
        int bg = hovered ? 0xFFB85A5A : PANEL_BG;
        raised(g, x, y, size, size, bg, hovered ? OUTER_BORDER : BEVEL_LO);
        Font font = Minecraft.getInstance().font;
        if (hovered) {
            g.drawCenteredString(font, "X", x + size / 2, y + (size - 8) / 2, TEXT_WHITE);
        } else {
            drawCenteredNoShadow(g, font, "X", x + size / 2, y + (size - 8) / 2, TEXT_HEADER);
        }
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
            g.fill(x + 2, y + 2, x + 3, y + 3, 0xFFFFFFFF);
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
                g.fill(px, y, px + 5, y + 5, 0xFF3A2B00);
                g.fill(px + 1, y + 1, px + 4, y + 4, 0xFFFFD54F);
                g.fill(px + 1, y + 1, px + 2, y + 2, 0xFFFFFFCC);
            } else {
                g.fill(px, y, px + 5, y + 5, SLOT_SHADOW);
                g.fill(px + 1, y + 1, px + 4, y + 4, 0xFF636363);
            }
        }
    }

    static void skillIcon(GuiGraphics g, String id, int x, int y, int size, boolean locked) {
        recessed(g, x, y, size, size, PANEL_INNER);
        int iconInner = size - 2;
        if (iconInner > 0) {
            // Skill PNGs are 16x16 full-canvas sprites; sample full [0..16) cleanly.
            g.blit(icon(id), x + 1, y + 1, iconInner, iconInner, 0.0F, 0.0F, 16, 16, 16, 16);
        }
        if (locked) {
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x99202020);
        }
    }

    static void progressBar(GuiGraphics g, int x, int y, int w, int h, float ratio, String label) {
        // Classic Minecraft green XP bar inside recessed frame.
        recessed(g, x, y, w, h, 0xFF1F1F1F);
        int filled = Math.max(0, Math.min(w - 2, Math.round((w - 2) * ratio)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, 0xFF56C21B);
            if (h >= 5) {
                g.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0xFFA2FF5A);
            }
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
        int border = !enabled ? SLOT_SHADOW : (hovered ? BEVEL_HI : OUTER_BORDER);
        int bg = !enabled ? 0xFF5A5A5A : (hovered ? 0xFF7B88B8 : PANEL_INNER);
        int hi = !enabled ? 0xFF6B6B6B : (hovered ? 0xFFAEB8E0 : FRAME_BG);
        int lo = !enabled ? 0xFF424242 : (hovered ? 0xFF4F5980 : BEVEL_LO);
        int textColor = !enabled ? 0xFFA0A0A0 : (hovered ? 0xFFFFFFA0 : TEXT_WHITE);

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
        recessed(g, x, y, w, h, 0xFF707070);
        if (maxScroll <= 0 || total <= 0) return;

        int thumbH = Math.max(16, Math.round(h * ((float) visible / total)));
        int travel = h - thumbH;
        int thumbY = y + Math.round(travel * ((float) scroll / maxScroll));

        int thumbBg = active ? 0xFFDADADA : FRAME_BG;
        raised(g, x, thumbY, w, thumbH, thumbBg, OUTER_BORDER);
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
