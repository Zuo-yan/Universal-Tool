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
        g.fill(0, 0, width, height, 0x6608050C);

        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (loadout.isEmpty()) {
            onClose();
            return;
        }

        int count = loadout.size();
        int cx = width / 2;
        int cy = height / 2;

        int radius = count <= 3 ? 72 : 92;
        int cardW = 54;
        int cardH = 46;

        boolean directHover = false;
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * i / count;
            int x = cx + (int) (Math.cos(angle) * radius) - cardW / 2;
            int y = cy + (int) (Math.sin(angle) * radius) - cardH / 2;
            if (mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH) {
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
            int x = cx + (int) (Math.cos(angle) * radius) - cardW / 2;
            int y = cy + (int) (Math.sin(angle) * radius) - cardH / 2;
            String id = loadout.get(i);
            boolean selected = i == highlightedSlot;
            boolean onCooldown = data.onCooldown(id);

            int bg = selected ? 0xEE4D3622 : 0xDD181220;
            int border = selected ? SkillScreenStyle.BORDER_GOLD_GLOW : (onCooldown ? SkillScreenStyle.CARD_BORDER : SkillScreenStyle.BORDER_BRONZE);

            g.fill(x, y, x + cardW, y + cardH, bg);
            SkillScreenStyle.outline(g, x, y, cardW, cardH, border);
            if (selected) {
                g.fill(x + 1, y + 1, x + cardW - 1, y + 2, SkillScreenStyle.BORDER_GOLD);
            }

            int iconSize = 22;
            SkillScreenStyle.skillIcon(g, id, x + (cardW - iconSize) / 2, y + 4, iconSize, onCooldown);

            if (onCooldown) {
                long seconds = (data.cooldownRemainingMillis(id) + 999) / 1000;
                g.drawCenteredString(font, "§c" + seconds + "s", x + cardW / 2, y + 30, 0xFFFF5555);
            } else {
                String name = Component.translatable("skill.universal_tool." + id).getString();
                g.drawCenteredString(font, SkillScreenStyle.trim(font, name, cardW - 6), x + cardW / 2, y + 31,
                        selected ? SkillScreenStyle.TEXT_GOLD : SkillScreenStyle.TEXT_WHITE);
            }
        }

        if (highlightedSlot >= 0 && highlightedSlot < count) {
            String selectedId = loadout.get(highlightedSlot);
            int centerSize = 36;
            g.fill(cx - centerSize / 2, cy - centerSize / 2, cx + centerSize / 2, cy + centerSize / 2, 0xEE140F1B);
            SkillScreenStyle.outline(g, cx - centerSize / 2, cy - centerSize / 2, centerSize, centerSize, SkillScreenStyle.BORDER_GOLD);
            SkillScreenStyle.skillIcon(g, selectedId, cx - 11, cy - 11, 22, data.onCooldown(selectedId));

            String selectedName = Component.translatable("skill.universal_tool." + selectedId).getString();
            g.drawCenteredString(font, selectedName, cx, cy - radius - 20, SkillScreenStyle.TEXT_GOLD);

            String status = data.onCooldown(selectedId)
                    ? "§c冷却中 (" + ((data.cooldownRemainingMillis(selectedId) + 999) / 1000) + "s)"
                    : "§a就绪 · 按 [" + SkillKeybinds.CAST.getTranslatedKeyMessage().getString() + "] 施放";
            g.drawCenteredString(font, status, cx, cy + radius + 18, 0xFFFFFFFF);
        }

        String hint = "移动鼠标或滑动滚轮选择 · 松开 [" + SkillKeybinds.SELECT.getTranslatedKeyMessage().getString() + "] 确认";
        g.drawCenteredString(font, hint, cx, height - 28, SkillScreenStyle.TEXT_MUTED);
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
        int radius = count <= 3 ? 72 : 92;
        int cardW = 54;
        int cardH = 46;

        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * i / count;
            int x = cx + (int) (Math.cos(angle) * radius) - cardW / 2;
            int y = cy + (int) (Math.sin(angle) * radius) - cardH / 2;
            if (mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH) {
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
