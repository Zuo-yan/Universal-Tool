package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Visual styling and drawing primitives for the skill management screen.
 *
 * <p>Recreates the dark-plum, slate, and brass RPG aesthetic defined in the concept art
 * ({@code art/previews/skill_ui_concept.png}) with pixel-perfect responsive primitives.
 */
final class SkillScreenStyle {
    static final int BACKDROP = 0xC008050C;
    static final int FRAME_BG = 0xF6130D19;
    static final int OUTER_BORDER = 0xFF2D1F16;
    static final int BORDER_BRONZE = 0xFF8A6538;
    static final int BORDER_BRONZE_HI = 0xFFD6A44D;
    static final int BORDER_GOLD = 0xFFFFD15C;
    static final int BORDER_GOLD_GLOW = 0xFFFFE89E;

    static final int PANEL_BG = 0xFF191222;
    static final int PANEL_INNER = 0xFF130E1A;
    static final int CARD_BORDER = 0xFF3D2C42;

    static final int ROW_BG = 0xFF1C1526;
    static final int ROW_HOVER = 0xFF2E2138;
    static final int ROW_SELECTED = 0xFF4A3420;

    static final int TEXT_GOLD = 0xFFFFE59E;
    static final int TEXT_WHITE = 0xFFF7ECDA;
    static final int TEXT_BODY = 0xFFD8CCDC;
    static final int TEXT_MUTED = 0xFF9E90A3;
    static final int TEXT_DIM = 0xFF65586B;

    static final int GREEN = 0xFF7CD97C;
    static final int RED = 0xFFE7807C;
    static final int TEAL = 0xFF77D4DE;

    static final int BADGE_ACTIVE_BG = 0xFF5C232B;
    static final int BADGE_ACTIVE_BORDER = 0xFFA64650;
    static final int BADGE_ACTIVE_TEXT = 0xFFFFB2B8;

    static final int BADGE_PASSIVE_BG = 0xFF1C3A32;
    static final int BADGE_PASSIVE_BORDER = 0xFF357565;
    static final int BADGE_PASSIVE_TEXT = 0xFF98E8D5;

    static final int BADGE_ROLE_BG = 0xFF232A36;
    static final int BADGE_ROLE_BORDER = 0xFF45546C;
    static final int BADGE_ROLE_TEXT = 0xFFB4C6DC;

    static ResourceLocation icon(String skillId) {
        return ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "textures/gui/skills/" + skillId + ".png");
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        // Base dark obsidian-plum fill:
        g.fill(x + 2, y, x + w - 2, y + h, FRAME_BG);
        g.fill(x, y + 2, x + w, y + h - 2, FRAME_BG);

        // Double-line bronze border:
        outline(g, x + 1, y + 1, w - 2, h - 2, OUTER_BORDER);
        outline(g, x + 3, y + 3, w - 6, h - 6, BORDER_BRONZE);

        // Top and bottom 1px brass highlights:
        g.fill(x + 5, y + 5, x + w - 5, y + 6, BORDER_BRONZE_HI);
        g.fill(x + 5, y + h - 6, x + w - 5, y + h - 5, OUTER_BORDER);

        // Corner diamond florets:
        drawDiamond(g, x + 2, y + 2, BORDER_GOLD, true);
        drawDiamond(g, x + w - 7, y + 2, BORDER_GOLD, true);
        drawDiamond(g, x + 2, y + h - 7, BORDER_GOLD, true);
        drawDiamond(g, x + w - 7, y + h - 7, BORDER_GOLD, true);

        // Top center crest ornament:
        int midX = x + w / 2;
        g.fill(midX - 14, y + 1, midX + 14, y + 3, BORDER_BRONZE);
        drawDiamond(g, midX - 2, y, BORDER_GOLD, true);

        // Bottom center crest:
        g.fill(midX - 10, y + h - 3, midX + 10, y + h - 1, BORDER_BRONZE);
        drawDiamond(g, midX - 2, y + h - 6, BORDER_GOLD, true);
    }

    static void card(GuiGraphics g, int x, int y, int w, int h, boolean accent) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        outline(g, x, y, w, h, accent ? BORDER_BRONZE : CARD_BORDER);
        if (accent) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, BORDER_BRONZE_HI);
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
            g.fill(x + 2, y + 2, x + 3, y + 3, 0xFFFFFAEB);
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
            drawDiamond(g, x + i * 7, y, filled ? BORDER_GOLD : TEXT_DIM, filled);
        }
    }

    static void skillIcon(GuiGraphics g, String id, int x, int y, int size, boolean locked) {
        int border = locked ? CARD_BORDER : BORDER_BRONZE;
        g.fill(x, y, x + size, y + size, PANEL_INNER);
        outline(g, x, y, size, size, border);
        int iconInner = size - 2;
        if (iconInner > 0) {
            g.blit(icon(id), x + 1, y + 1, iconInner, iconInner, 0, 0, 32, 32, 32, 32);
        }
        if (locked) {
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xAA120D16);
        }
    }

    static void progressBar(GuiGraphics g, int x, int y, int w, int h, float ratio, String label) {
        g.fill(x, y, x + w, y + h, 0xFF0E0A14);
        outline(g, x, y, w, h, BORDER_BRONZE);
        int filled = Math.max(0, Math.min(w - 2, Math.round((w - 2) * ratio)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, 0xFFD69A30);
            if (h >= 5) {
                g.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0xFFFFEFA6);
            }
        }
        if (label != null && !label.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, 0xFFE0D5C0);
        }
    }

    static void badge(GuiGraphics g, int x, int y, int w, int h, String text, int bg, int border, int textColor) {
        g.fill(x, y, x + w, y + h, bg);
        outline(g, x, y, w, h, border);
        Font font = Minecraft.getInstance().font;
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    static void button(GuiGraphics g, int x, int y, int w, int h, String text, boolean enabled, boolean hovered) {
        int bg = enabled ? (hovered ? 0xFF6E4A20 : 0xFF4A3216) : 0xFF19131F;
        int border = enabled ? (hovered ? BORDER_GOLD_GLOW : BORDER_GOLD) : CARD_BORDER;
        int textColor = enabled ? (hovered ? 0xFFFFFFFF : TEXT_GOLD) : TEXT_DIM;

        g.fill(x, y, x + w, y + h, bg);
        outline(g, x, y, w, h, border);
        Font font = Minecraft.getInstance().font;
        int textW = font.width(text);
        if (enabled) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, hovered ? 0xFFFFFAEB : BORDER_GOLD);
            if (w >= textW + 24) {
                drawDiamond(g, x + 4, y + (h - 5) / 2, BORDER_GOLD, true);
                drawDiamond(g, x + w - 9, y + (h - 5) / 2, BORDER_GOLD, true);
            }
        }

        g.drawCenteredString(font, trim(font, text, w - 8), x + w / 2, y + (h - 8) / 2, textColor);
    }

    static void scrollbarVertical(GuiGraphics g, int x, int y, int w, int h,
                                  int scroll, int maxScroll, int visible, int total, boolean active) {
        g.fill(x, y, x + w, y + h, 0xFF120E17);
        outline(g, x, y, w, h, CARD_BORDER);
        if (maxScroll <= 0 || total <= 0) return;

        int thumbH = Math.max(16, Math.round(h * ((float) visible / total)));
        int travel = h - thumbH;
        int thumbY = y + Math.round(travel * ((float) scroll / maxScroll));

        int thumbBg = active ? 0xFF8A5A35 : 0xFF5D382B;
        int thumbBorder = active ? BORDER_GOLD : BORDER_BRONZE;

        g.fill(x + 1, thumbY, x + w - 1, thumbY + thumbH, thumbBg);
        outline(g, x, thumbY, w, thumbH, thumbBorder);

        int midX = x + w / 2;
        int midY = thumbY + thumbH / 2;
        g.fill(midX - 1, midY - 3, midX + 1, midY - 2, active ? TEXT_GOLD : BORDER_GOLD);
        g.fill(midX - 1, midY, midX + 1, midY + 1, active ? TEXT_GOLD : BORDER_GOLD);
        g.fill(midX - 1, midY + 3, midX + 1, midY + 4, active ? TEXT_GOLD : BORDER_GOLD);
    }

    static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private SkillScreenStyle() {
    }
}