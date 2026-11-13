package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.network.SelectActiveSkillPayload;
import org.gwfx.universaltool.skill.ModSkills;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillDefinition;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Interactive active-skill radial selection wheel screen.
 *
 * <p>Unlocks the mouse cursor and halts camera turning while open. Supports:
 * <ul>
 *   <li>Mouse direction / radial sector hovering</li>
 *   <li>Direct hover and click on skill cards</li>
 *   <li>Mouse scroll wheel cycling (up/down)</li>
 *   <li>Key release of {@link SkillKeybinds#SELECT} commits selection and returns to game</li>
 * </ul>
 */
public class SkillWheelScreen extends Screen {
    private static final int CARD_W = 62;
    private static final int CARD_H = 46;

    private int highlightedSlot;

    public SkillWheelScreen() {
        super(Component.translatable("screen.universal_tool.skills.wheel_title"));
    }

    @Override
    protected void init() {
        super.init();
        PlayerSkillData data = ClientSkillCache.data();
        highlightedSlot = Math.max(0, Math.min(data.activeLoadout().size() - 1, data.selectedActive()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x66120B06);

        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (loadout.isEmpty()) {
            onClose();
            return;
        }

        int count = loadout.size();
        int cx = width / 2;
        int cy = height / 2;

        int radius = count <= 3 ? 76 : 96;

        boolean directHover = false;
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * i / count;
            int x = cx + (int) (Math.cos(angle) * radius) - CARD_W / 2;
            int y = cy + (int) (Math.sin(angle) * radius) - CARD_H / 2;
            if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H) {
                highlightedSlot = i;
                directHover = true;
                break;
            }
        }

        if (!directHover) {
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            if (dx * dx + dy * dy > 16 * 16) {
                double angle = Math.atan2(dy, dx) + Math.PI / 2;
                if (angle < 0) angle += Math.PI * 2;
                int slot = (int) Math.floor((angle + Math.PI / count) / (Math.PI * 2) * count) % count;
                highlightedSlot = Math.max(0, Math.min(count - 1, slot));
            }
        }

        // Subtle astrolabe brass dial ring ticks connecting the radial medallions
        drawAstrolabeRing(g, cx, cy, radius, count);

        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * i / count;
            int x = cx + (int) (Math.cos(angle) * radius) - CARD_W / 2;
            int y = cy + (int) (Math.sin(angle) * radius) - CARD_H / 2;
            String id = loadout.get(i);
            boolean selected = i == highlightedSlot;
            boolean onCooldown = data.onCooldown(id);

            drawMedallionCard(g, x, y, CARD_W, CARD_H, selected, onCooldown);

            // 20x20 brass-recessed slot renders the 32x32 skill icon sharply
            int iconSize = 20;
            SkillScreenStyle.skillIcon(g, id, x + (CARD_W - iconSize) / 2, y + 6, iconSize, onCooldown);

            if (onCooldown) {
                long seconds = (data.cooldownRemainingMillis(id) + 999) / 1000;
                SkillScreenStyle.drawCenteredNoShadow(g, font, seconds + "s",
                        x + CARD_W / 2, y + 31, SkillScreenStyle.RED_DARK);
            } else {
                String name = Component.translatable("skill.universal_tool." + id).getString();
                SkillScreenStyle.drawCenteredNoShadow(g, font, SkillScreenStyle.trim(font, name, CARD_W - 10),
                        x + CARD_W / 2, y + 31, selected ? SkillScreenStyle.TEXT_DARK : SkillScreenStyle.TEXT_HEADER);
            }
        }

        if (highlightedSlot >= 0 && highlightedSlot < count) {
            String selectedId = loadout.get(highlightedSlot);
            drawAstrolabeCenterCrest(g, cx, cy);
            // 34x34 recessed slot renders the 32x32 icon cleanly at center
            SkillScreenStyle.skillIcon(g, selectedId, cx - 17, cy - 17, 34, data.onCooldown(selectedId));

            String selectedName = Component.translatable("skill.universal_tool." + selectedId).getString();
            g.drawCenteredString(font, selectedName, cx, cy - radius - 26, SkillScreenStyle.TEXT_GOLD);

            String status = data.onCooldown(selectedId)
                    ? "§c冷却中 (" + ((data.cooldownRemainingMillis(selectedId) + 999) / 1000) + "s)"
                    : "§a就绪 · 按 [" + SkillKeybinds.CAST.getTranslatedKeyMessage().getString() + "] 施放";
            g.drawCenteredString(font, status, cx, cy + radius + 22, SkillScreenStyle.TEXT_WHITE);
        }

        String hint = "移动鼠标或滑动滚轮选择 · 松开 [" + SkillKeybinds.SELECT.getTranslatedKeyMessage().getString() + "] 确认";
        g.drawCenteredString(font, hint, cx, height - 28, SkillScreenStyle.PANEL_BG_ALT);
    }

    private static void drawAstrolabeRing(GuiGraphics g, int cx, int cy, int radius, int count) {
        int ringRadius = Math.max(40, radius - 22);
        int dots = 48;
        for (int i = 0; i < dots; i++) {
            double a = Math.PI * 2 * i / dots;
            int px = cx + (int) Math.round(Math.cos(a) * ringRadius);
            int py = cy + (int) Math.round(Math.sin(a) * ringRadius);
            int col = (i % 4 == 0) ? 0xFFD6A84E : 0xAA8C6223;
            g.fill(px, py, px + 1, py + 1, col);
        }
        for (int i = 0; i < count; i++) {
            double a = -Math.PI / 2 + Math.PI * 2 * i / count;
            int px = cx + (int) Math.round(Math.cos(a) * (ringRadius + 5));
            int py = cy + (int) Math.round(Math.sin(a) * (ringRadius + 5));
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xFF9E7330);
            g.fill(px, py, px + 1, py + 1, 0xFFF2D27A);
        }
    }

    private static void drawAstrolabeCenterCrest(GuiGraphics g, int cx, int cy) {
        // Cardinal compass pointers extending beyond the octagonal center crest
        g.fill(cx - 1, cy - 28, cx + 1, cy + 28, 0xFF9E7330);
        g.fill(cx - 28, cy - 1, cx + 28, cy + 1, 0xFF9E7330);
        g.fill(cx, cy - 30, cx + 1, cy + 30, 0xFFF2D27A);
        g.fill(cx - 30, cy, cx + 30, cy + 1, 0xFFF2D27A);

        // Walnut & burnished-brass octagonal astrolabe frame around center crest
        int crest = 46;
        int x = cx - crest / 2;
        int y = cy - crest / 2;
        g.fill(x - 1, y + 2, x + crest + 1, y + crest - 2, SkillScreenStyle.OUTER_BORDER);
        g.fill(x + 2, y - 1, x + crest - 2, y + crest + 1, SkillScreenStyle.OUTER_BORDER);
        SkillScreenStyle.frame(g, x, y, crest, crest);
    }

    private static void drawMedallionCard(GuiGraphics g, int x, int y, int w, int h,
                                          boolean selected, boolean onCooldown) {
        int bg = selected ? SkillScreenStyle.FRAME_BG
                : (onCooldown ? 0xFFC8B89C : SkillScreenStyle.PANEL_BG);
        int outer = selected ? SkillScreenStyle.BORDER_GOLD_GLOW : SkillScreenStyle.OUTER_BORDER;
        int hi = selected ? 0xFFFFF2BC : SkillScreenStyle.BORDER_GOLD;
        int lo = selected ? SkillScreenStyle.BORDER_BRONZE : 0xFF593E18;

        // Subtle walnut backing + gilded parchment medallion face
        g.fill(x, y, x + w, y + h, outer);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SkillScreenStyle.WALNUT);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, bg);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);

        if (selected) {
            SkillScreenStyle.outline(g, x + 2, y + 2, w - 4, h - 4, SkillScreenStyle.BORDER_GOLD);
        } else {
            SkillScreenStyle.outline(g, x + 2, y + 2, w - 4, h - 4, SkillScreenStyle.BORDER_BRONZE);
        }

        // Gilded corner rivets
        int rivet = selected ? 0xFFFFF2BC : SkillScreenStyle.BORDER_GOLD;
        g.fill(x + 1, y + 1, x + 3, y + 3, rivet);
        g.fill(x + w - 3, y + 1, x + w - 1, y + 3, rivet);
        g.fill(x + 1, y + h - 3, x + 3, y + h - 1, rivet);
        g.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, rivet);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (!loadout.isEmpty()) {
            highlightedSlot = Math.floorMod(highlightedSlot - (int) Math.signum(scrollY), loadout.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            updateHighlight(mouseX, mouseY);
            commitAndClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateHighlight(double mouseX, double mouseY) {
        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (loadout.isEmpty()) return;
        int count = loadout.size();
        int cx = width / 2;
        int cy = height / 2;
        int radius = count <= 3 ? 76 : 96;

        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * i / count;
            int x = cx + (int) (Math.cos(angle) * radius) - CARD_W / 2;
            int y = cy + (int) (Math.sin(angle) * radius) - CARD_H / 2;
            if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H) {
                highlightedSlot = i;
                return;
            }
        }

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        if (dx * dx + dy * dy > 16 * 16) {
            double angle = Math.atan2(dy, dx) + Math.PI / 2;
            if (angle < 0) angle += Math.PI * 2;
            int slot = (int) Math.floor((angle + Math.PI / count) / (Math.PI * 2) * count) % count;
            highlightedSlot = Math.max(0, Math.min(count - 1, slot));
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (SkillKeybinds.SELECT.matches(keyCode, scanCode)) {
            commitAndClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitAndClose() {
        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (!loadout.isEmpty() && highlightedSlot >= 0 && highlightedSlot < loadout.size()) {
            PacketDistributor.sendToServer(new SelectActiveSkillPayload(highlightedSlot));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
