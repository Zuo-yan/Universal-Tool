package org.gwfx.universaltool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.network.SelectActiveSkillPayload;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** A hold-to-open skill picker. Only input events change the highlighted skill. */
final class SkillWheelScreen extends Screen {
    private final InputConstants.Key openingInput;
    private String highlightedSkill;

    SkillWheelScreen(InputConstants.Key openingInput, String selectedSkill) {
        super(Component.translatable("screen.universal_tool.skills.wheel_title"));
        this.openingInput = openingInput;
        this.highlightedSkill = selectedSkill;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (loadout.isEmpty()) return;

        SkillWheelLayout layout = SkillWheelLayout.fit(width, height, loadout.size());
        SkillWheelRenderer.render(graphics, font, title, layout, data, loadout, highlightedSlot(data, loadout));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (loadout.isEmpty()) return;

        SkillWheelLayout layout = SkillWheelLayout.fit(width, height, loadout.size());
        int slot = layout.pointedSlot(mouseX, mouseY, highlightedSlot(data, loadout));
        highlightedSkill = loadout.get(slot);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            List<String> loadout = ClientSkillCache.data().activeLoadout();
            if (!loadout.isEmpty()) {
                int card = SkillWheelLayout.fit(width, height, loadout.size()).cardAt(mouseX, mouseY);
                if (card >= 0) highlightedSkill = loadout.get(card);
            }
            commitAndClose();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PlayerSkillData data = ClientSkillCache.data();
        List<String> loadout = data.activeLoadout();
        if (!loadout.isEmpty() && scrollY != 0) {
            int step = scrollY > 0 ? -1 : 1;
            int slot = Math.floorMod(highlightedSlot(data, loadout) + step, loadout.size());
            highlightedSkill = loadout.get(slot);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (openingInput.equals(InputConstants.getKey(keyCode, scanCode))) {
            commitAndClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (openingInput.equals(InputConstants.Type.MOUSE.getOrCreate(button))) {
            commitAndClose();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        if (!canSelect() || ClientSkillCache.data().activeLoadout().isEmpty()) {
            onClose();
        }
    }

    private int highlightedSlot(PlayerSkillData data, List<String> loadout) {
        int slot = loadout.indexOf(highlightedSkill);
        return slot >= 0 ? slot : Math.min(data.selectedActive(), loadout.size() - 1);
    }

    private boolean canSelect() {
        return minecraft != null && minecraft.screen == this && minecraft.isWindowActive()
                && minecraft.getOverlay() == null && minecraft.player != null && !minecraft.player.isDeadOrDying()
                && ClientSkillCache.isSystemEnabled();
    }

    private void commitAndClose() {
        if (canSelect()) {
            PlayerSkillData data = ClientSkillCache.data();
            int slot = data.activeLoadout().indexOf(highlightedSkill);
            if (slot >= 0 && slot != data.selectedActive() && data.setSelectedActive(slot)) {
                PacketDistributor.sendToServer(new SelectActiveSkillPayload(slot));
            }
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
