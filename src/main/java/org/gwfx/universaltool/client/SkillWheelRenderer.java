package org.gwfx.universaltool.client;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.gwfx.universaltool.skill.ModSkills;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillConstellation;
import org.gwfx.universaltool.skill.SkillDefinition;

import java.util.List;

final class SkillWheelRenderer {
    private static final String TEXT_KEY = "screen.universal_tool.skills.";
    private static final int DETAILS_WIDTH = 118;
    private static final int DETAILS_HEIGHT = 78;

    static void render(GuiGraphics g, Font font, Component title, SkillWheelLayout layout, PlayerSkillData data,
                       List<String> loadout, int highlighted) {
        g.fillGradient(0, 0, g.guiWidth(), g.guiHeight(), 0x800A101E, 0xB004070F);
        g.pose().pushPose();
        g.pose().translate(layout.centerX(), layout.centerY(), 0);
        g.pose().scale(layout.scale(), layout.scale(), 1);

        String selectedSkill = loadout.get(highlighted);
        int accent = accent(selectedSkill);
        drawTracks(g, layout, highlighted, accent);

        String equipped = data.selectedSkill();
        for (int i = 0; i < loadout.size(); i++) {
            String id = loadout.get(i);
            drawCard(g, font, layout.slotX(i), layout.slotY(i), data, id, i == highlighted, id.equals(equipped));
        }
        drawDetails(g, font, data, selectedSkill, accent);
        drawControls(g, font, title);
        g.pose().popPose();
    }

    private static void drawTracks(GuiGraphics g, SkillWheelLayout layout, int highlighted, int accent) {
        SkillScreenStyle.drawAstrolabeRing(g, 0, 0, 58, 0xFF526281, 48, 0);
        SkillScreenStyle.drawAstrolabeRing(g, 0, 0, 66, 0xAA354461, 72, 0);
        float seconds = (Util.getMillis() % 20_000L) / 1000.0F;
        for (int i = 0; i < layout.count(); i++) {
            boolean selected = i == highlighted;
            SkillScreenStyle.drawStarTrackLine(g, 0, 0, layout.slotX(i), layout.slotY(i),
                    accent, accent, selected, selected, seconds);
        }
    }

    private static void drawCard(GuiGraphics g, Font font, int cx, int cy, PlayerSkillData data, String id,
                                 boolean highlighted, boolean equipped) {
        int w = SkillWheelLayout.CARD_WIDTH;
        int h = SkillWheelLayout.CARD_HEIGHT;
        int x = cx - w / 2;
        int y = cy - h / 2;
        int accent = accent(id);
        long remaining = data.cooldownRemainingMillis(id);

        g.fill(x - 1, y + 2, x + w + 1, y + h + 2, 0x60030710);
        g.fillGradient(x, y, x + w, y + h, highlighted ? 0xFF283450 : 0xF21A243A, 0xFA0C1220);
        SkillScreenStyle.outline(g, x, y, w, h, highlighted ? accent : 0xFF3A4967);
        g.fill(x, y + 5, x + 2, y + h - 5, highlighted ? accent : SkillScreenStyle.withAlpha(accent, 0.65F));
        if (highlighted) {
            g.fill(x + 3, y + 1, x + w - 3, y + 2, SkillScreenStyle.withAlpha(accent, 0.25F));
        }
        if (equipped) {
            g.fill(x + w - 5, y + 4, x + w - 3, y + 6, SkillScreenStyle.GOLD_BRIGHT);
        }

        SkillScreenStyle.skillIcon(g, id, x + 5, y + 4, 20, remaining > 0);
        String name = SkillScreenStyle.trim(font, skillName(id), w - 36);
        g.drawString(font, name, x + 29, y + 4, highlighted ? SkillScreenStyle.TEXT_TITLE : SkillScreenStyle.TEXT_WHITE);
        String status = remaining > 0
                ? Component.translatable(TEXT_KEY + "wheel_seconds", cooldownSeconds(remaining)).getString()
                : levelLabel(data, id);
        g.drawString(font, SkillScreenStyle.trim(font, status, w - 34), x + 29, y + 16,
                remaining > 0 ? SkillScreenStyle.TEXT_RED : SkillScreenStyle.TEXT_BODY);
    }

    private static void drawDetails(GuiGraphics g, Font font, PlayerSkillData data, String id, int accent) {
        int x = -DETAILS_WIDTH / 2;
        int y = -DETAILS_HEIGHT / 2;
        long remaining = data.cooldownRemainingMillis(id);
        g.fill(x - 2, y + 3, -x + 2, -y + 3, 0x70030710);
        g.fillGradient(x, y, -x, -y, 0xFA1C2640, 0xFD0A101D);
        SkillScreenStyle.outline(g, x, y, DETAILS_WIDTH, DETAILS_HEIGHT, 0xFF526281);
        SkillScreenStyle.outline(g, x + 2, y + 2, DETAILS_WIDTH - 4, DETAILS_HEIGHT - 4, 0xFF2A3551);
        g.fill(-24, y, 24, y + 1, accent);
        g.fill(-24, -y - 1, 24, -y, accent);
        SkillScreenStyle.drawStarGlyph(g, -24, -20, 2, accent);
        SkillScreenStyle.drawStarGlyph(g, 24, -20, 2, accent);
        SkillScreenStyle.skillIcon(g, id, -14, -34, 28, remaining > 0);

        drawCentered(g, font, skillName(id), 0, DETAILS_WIDTH - 12, SkillScreenStyle.TEXT_TITLE);
        drawCentered(g, font, levelLabel(data, id), 12, DETAILS_WIDTH - 12,
                data.isAwakened(id) ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_BODY);

        g.fill(x + 10, 23, -x - 10, 24, 0xFF35425D);
        Component status = remaining > 0
                ? Component.translatable(TEXT_KEY + "wheel_cooldown", cooldownSeconds(remaining))
                : Component.translatable(TEXT_KEY + "wheel_ready", SkillKeybinds.CAST.getTranslatedKeyMessage());
        drawCentered(g, font, status.getString(), 27, DETAILS_WIDTH - 12,
                remaining > 0 ? SkillScreenStyle.TEXT_RED : SkillScreenStyle.TEXT_GREEN);
    }

    private static void drawControls(GuiGraphics g, Font font, Component title) {
        int titleHalfWidth = font.width(title) / 2;
        g.fill(-titleHalfWidth - 25, -96, -titleHalfWidth - 7, -95, 0xFF827044);
        g.fill(titleHalfWidth + 7, -96, titleHalfWidth + 25, -95, 0xFF827044);
        g.drawCenteredString(font, title, 0, -100, SkillScreenStyle.TEXT_TITLE);

        String select = Component.translatable(TEXT_KEY + "wheel_select_hint").getString();
        String confirm = Component.translatable(TEXT_KEY + "wheel_confirm_hint",
                SkillKeybinds.SELECT.getTranslatedKeyMessage()).getString();
        int maxWidth = SkillWheelLayout.CONTENT_WIDTH - 22;
        int width = Math.min(maxWidth, Math.max(font.width(select), font.width(confirm)));
        g.fill(-width / 2 - 8, 88, (width + 1) / 2 + 8, 112, 0xEB0C1322);
        drawCentered(g, font, select, 91, maxWidth, SkillScreenStyle.TEXT_BODY);
        drawCentered(g, font, confirm, 102, maxWidth, SkillScreenStyle.TEXT_WHITE);
    }

    private static void drawCentered(GuiGraphics g, Font font, String text, int y, int maxWidth, int color) {
        g.drawCenteredString(font, SkillScreenStyle.trim(font, text, maxWidth), 0, y, color);
    }

    private static String skillName(String id) {
        return Component.translatable("skill.universal_tool." + id).getString();
    }

    private static String levelLabel(PlayerSkillData data, String id) {
        if (data.isAwakened(id)) return Component.translatable(TEXT_KEY + "wheel_awakened").getString();
        return Component.translatable(TEXT_KEY + "wheel_level", data.learnedLevel(id)).getString();
    }

    private static long cooldownSeconds(long millis) {
        return (millis + 999) / 1000;
    }

    private static int accent(String id) {
        return ModSkills.find(id).map(SkillDefinition::constellation).map(SkillConstellation::primaryColor)
                .orElse(SkillScreenStyle.GOLD);
    }

    private SkillWheelRenderer() {}
}
