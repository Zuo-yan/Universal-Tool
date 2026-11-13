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
    private static final int CARD_W = 58;
    private static final int CARD_H = 44;

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
        g.fill(0, 0, width, height, 0x66000000);

        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (loadout.isEmpty()) {
            onClose();
            return;
        }

        int count = loadout.size();
        int cx = width / 2;
        int cy = height / 2;

        int radius = count <= 3 ? 74 : 94;

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

        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * i / count;
            int x = cx + (int) (Math.cos(angle) * radius) - CARD_W / 2;
            int y = cy + (int) (Math.sin(angle) * radius) - CARD_H / 2;
            String id = loadout.get(i);
            boolean selected = i == highlightedSlot;
            boolean onCooldown = data.onCooldown(id);

            int bg = selected ? 0xFFE2DFCE : (onCooldown ? 0xFFA6A6A6 : SkillScreenStyle.FRAME_BG);
            int border = selected ? 0xFF3A2B00 : SkillScreenStyle.OUTER_BORDER;

            // Classic MC #C6C6C6 / Advancement-frame beveled card
            SkillScreenStyle.raised(g, x, y, CARD_W, CARD_H, bg, border);
            if (selected) {
                SkillScreenStyle.outline(g, x + 1, y + 1, CARD_W - 2, CARD_H - 2, SkillScreenStyle.BORDER_GOLD);
            }

            // 18x18 recessed MC slot renders the 16x16 skill icon at exact 1:1 resolution
            int iconSize = 18;
            SkillScreenStyle.skillIcon(g, id, x + (CARD_W - iconSize) / 2, y + 6, iconSize, onCooldown);

            if (onCooldown) {
                long seconds = (data.cooldownRemainingMillis(id) + 999) / 1000;
                SkillScreenStyle.drawCenteredNoShadow(g, font, seconds + "s",
                        x + CARD_W / 2, y + 29, SkillScreenStyle.RED_DARK);
            } else {
                String name = Component.translatable("skill.universal_tool." + id).getString();
                SkillScreenStyle.drawCenteredNoShadow(g, font, SkillScreenStyle.trim(font, name, CARD_W - 8),
                        x + CARD_W / 2, y + 29, selected ? 0xFF1E1E1E : SkillScreenStyle.TEXT_HEADER);
            }
        }

        if (highlightedSlot >= 0 && highlightedSlot < count) {
            String selectedId = loadout.get(highlightedSlot);
            int centerSize = 42;
            SkillScreenStyle.frame(g, cx - centerSize / 2, cy - centerSize / 2, centerSize, centerSize);
            // 34x34 recessed slot renders the 16x16 icon at crisp 2x (32x32)
            SkillScreenStyle.skillIcon(g, selectedId, cx - 17, cy - 17, 34, data.onCooldown(selectedId));

            String selectedName = Component.translatable("skill.universal_tool." + selectedId).getString();
            g.drawCenteredString(font, selectedName, cx, cy - radius - 22, SkillScreenStyle.TEXT_GOLD);

            String status = data.onCooldown(selectedId)
                    ? "§c冷却中 (" + ((data.cooldownRemainingMillis(selectedId) + 999) / 1000) + "s)"
                    : "§a就绪 · 按 [" + SkillKeybinds.CAST.getTranslatedKeyMessage().getString() + "] 施放";
            g.drawCenteredString(font, status, cx, cy + radius + 18, SkillScreenStyle.TEXT_WHITE);
        }

        String hint = "移动鼠标或滑动滚轮选择 · 松开 [" + SkillKeybinds.SELECT.getTranslatedKeyMessage().getString() + "] 确认";
        g.drawCenteredString(font, hint, cx, height - 28, SkillScreenStyle.TEXT_MUTED_LIGHT);
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
        int radius = count <= 3 ? 74 : 94;

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
