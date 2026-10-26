package org.gwfx.universaltool.client;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.klein.KleinEntryData;
import org.gwfx.universaltool.klein.KleinMenu;

public final class KleinScreen extends AbstractContainerScreen<KleinMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/gui/container/klein_terminal.png");
    private static final int CYAN = 0xFF62D9D1, BRASS = 0xFFB29A61, TEXT = 0xFFE2D8B8;
    private static final int BUTTON_X = 172, BUTTON_Y = 132, BUTTON_STEP = 15, BUTTON_SIZE = 14;
    private EditBox search;
    private KleinEntryData hovered;
    private boolean isDraggingScrollbar = false;
    private int drawerX, drawerY;

    public KleinScreen(KleinMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 194;
        imageHeight = 216;
    }

    @Override protected void init() {
        super.init();
        // Keep the default window centred; renderDrawer flips to the right if the left is clipped.
        leftPos = Math.max(leftPos, 2);
        search = new EditBox(font, leftPos + 22, topPos + 20, 96, 12, Component.empty());
        search.setMaxLength(64);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("gui.universal_tool.klein.search"));
        search.setResponder(value -> menu.setView(value, menu.sortMode(), menu.descending()));
        addRenderableWidget(search);

    }

    private void selectDrawer(int mode) {
        int next = menu.drawerMode() == mode ? KleinMenu.DRAWER_NONE : mode;
        menu.setDrawerMode(next);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new org.gwfx.universaltool.klein.KleinDrawerPayload(next));
    }

    private int buttonY(int index) { return topPos + BUTTON_Y + index * BUTTON_STEP; }

    private boolean hoveringButton(double mouseX, double mouseY, int index) {
        int by = buttonY(index);
        return mouseX >= leftPos + BUTTON_X && mouseX < leftPos + BUTTON_X + BUTTON_SIZE
                && mouseY >= by && mouseY < by + BUTTON_SIZE;
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 512, 512);

        g.drawString(font, Component.translatable("item.universal_tool.klein_bottle"), x + 22, y + 7, TEXT, false);
        String countBadge = menu.filtered().size() + " / \u221e";
        g.drawString(font, countBadge, x + 172 - font.width(countBadge), y + 7, BRASS, false);

        if (mouseX >= x + 178 && mouseX < x + 189 && mouseY >= y + 6 && mouseY < y + 17) {
            g.fill(x + 178, y + 6, x + 188, y + 16, 0x60FF4040);
        }

        String modeLabel = switch (menu.sortMode()) {
            case KleinMenu.SORT_NAME -> Component.translatable("gui.universal_tool.klein.name").getString();
            case KleinMenu.SORT_MOD -> Component.translatable("gui.universal_tool.klein.sort_mod").getString();
            default -> Component.translatable("gui.universal_tool.klein.quantity").getString();
        };
        g.drawCenteredString(font, Component.literal(modeLabel), x + 144, y + 22, CYAN);
        border(g, x + 126, y + 19, 37, 14, CYAN);

        String dirLabel = menu.descending() ? "\u2193" : "\u2191";
        g.drawCenteredString(font, Component.literal(dirLabel), x + 177, y + 22, CYAN);
        border(g, x + 166, y + 19, 22, 14, menu.descending() ? CYAN : BRASS);

        g.drawString(font, Component.literal("\u2727 ").append(Component.translatable("gui.universal_tool.klein.inventory")).append(" \u2727"), x + 8, y + 122, BRASS, false);

        for (int i = 0; i < 5; i++) {
            int by = buttonY(i);
            drawButtonGlyph(g, x + BUTTON_X, by, i, hoveringButton(mouseX, mouseY, i));
        }

        hovered = null;
        int start = menu.scroll() * 9;
        for (int cell = 0; cell < 36; cell++) {
            int col = cell % 9, row = cell / 9;
            int sx = x + KleinMenu.GRID_X + col * 18, sy = y + KleinMenu.GRID_Y + row * 18;
            boolean isHovered = mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
            int index = start + cell;
            if (index >= menu.filtered().size()) continue;
            KleinEntryData entry = menu.filtered().get(index);
            g.renderItem(entry.template(), sx + 1, sy + 1);
            g.renderItemDecorations(font, entry.template(), sx + 1, sy + 1, compact(entry.amount()));
            if (isHovered) {
                hovered = entry;
                g.blit(GUI_TEXTURE, sx, sy, 200, 24, 18, 18, 512, 512);
            }
        }

        int trackY = y + KleinMenu.GRID_Y;
        int totalRows = Math.max(1, (menu.filtered().size() + 8) / 9);
        int maxScroll = Math.max(0, totalRows - 4);
        int thumbHeight = maxScroll == 0 ? 72 : Math.max(14, Math.min(72, (int) Math.round((4.0 / totalRows) * 72)));
        int thumbY = maxScroll == 0 ? trackY : trackY + (72 - thumbHeight) * menu.scroll() / maxScroll;
        g.blit(GUI_TEXTURE, x + 176, thumbY, 200, 48, 6, thumbHeight, 512, 512);

        for (Slot slot : menu.slots) {
            if (slot.container == menu.getSlot(0).container && slot.getContainerSlot() == menu.lockedInventorySlot()) {
                int sx = x + slot.x - 1, sy = y + slot.y - 1;
                g.blit(GUI_TEXTURE, sx, sy, 200, 0, 18, 18, 512, 512);
                g.blit(GUI_TEXTURE, sx + 9, sy + 9, 222, 0, 9, 10, 512, 512);
            }
        }

        renderDrawer(g, mouseX, mouseY);
    }

    private void renderDrawer(GuiGraphics g, int mouseX, int mouseY) {
        int mode = menu.drawerMode();
        if (mode == KleinMenu.DRAWER_NONE) return;
        drawerX = leftPos - 96;
        if (drawerX < 2) drawerX = leftPos + imageWidth + 4;
        drawerY = topPos + 42;
        g.blit(GUI_TEXTURE, drawerX, drawerY, 200, 80, 92, 108, 512, 512);

        Component title = switch (mode) {
            case KleinMenu.DRAWER_CRAFTING -> Component.translatable("gui.universal_tool.klein.tab_crafting");
            case KleinMenu.DRAWER_SMELTING -> Component.translatable("gui.universal_tool.klein.tab_smelting");
            default -> Component.translatable("gui.universal_tool.klein.tab_anvil");
        };
        g.drawCenteredString(font, title, drawerX + 46, drawerY + 6, BRASS);

        if (mode == KleinMenu.DRAWER_CRAFTING) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int sx = drawerX + 8 + col * 18, sy = drawerY + 20 + row * 18;
                    drawSocket(g, sx, sy);
                }
            }
            drawSocket(g, drawerX + 70, drawerY + 38);
            drawArrow(g, drawerX + 63, drawerY + 45);
        } else if (mode == KleinMenu.DRAWER_SMELTING) {
            drawSocket(g, drawerX + 38, drawerY + 20);
            drawFlame(g, drawerX + 43, drawerY + 44);
            drawSocket(g, drawerX + 38, drawerY + 70);
        } else {
            drawSocket(g, drawerX + 10, drawerY + 36);
            drawSocket(g, drawerX + 34, drawerY + 36);
            drawArrow(g, drawerX + 52, drawerY + 44);
            drawSocket(g, drawerX + 66, drawerY + 36);
        }
    }

    private void drawSocket(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF04090C);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF081318);
        g.fill(x + 1, y + 1, x + 17, y + 2, 0xFF122A31);
        g.fill(x + 1, y + 1, x + 2, y + 17, 0xFF122A31);
        g.fill(x + 17, y + 1, x + 18, y + 17, 0xFF1B3B47);
        g.fill(x + 1, y + 17, x + 17, y + 18, 0xFF1B3B47);
    }

    private void drawArrow(GuiGraphics g, int x, int y) {
        g.fill(x, y + 3, x + 8, y + 5, 0xFF62D9D1);
        g.fill(x + 5, y, x + 8, y + 8, 0xFF62D9D1);
    }

    private void drawFlame(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y, x + 6, y + 2, 0xFFF0A050);
        g.fill(x + 1, y + 2, x + 7, y + 5, 0xFFF0A050);
        g.fill(x, y + 5, x + 8, y + 9, 0xFFE06030);
        g.fill(x + 3, y + 3, x + 5, y + 6, 0xFFFFE096);
    }

    private void drawButtonGlyph(GuiGraphics g, int x, int y, int index, boolean hovered) {
        boolean active = (index == 1 && menu.drawerMode() == KleinMenu.DRAWER_CRAFTING)
                || (index == 2 && menu.drawerMode() == KleinMenu.DRAWER_SMELTING)
                || (index == 3 && menu.drawerMode() == KleinMenu.DRAWER_ANVIL);
        int color = hovered || active ? 0xFFFFFFFF : 0xFF62D9D1;
        if (hovered) g.fill(x, y, x + 14, y + 14, 0x302F8F8C);
        if (active) border(g, x, y, 14, 14, 0xFFB29A61);
        switch (index) {
            case 0 -> {
                g.fill(x + 4, y + 2, x + 10, y + 3, color);
                g.fill(x + 6, y + 3, x + 8, y + 9, color);
                g.fill(x + 4, y + 7, x + 10, y + 8, color);
                g.fill(x + 2, y + 10, x + 12, y + 11, color);
            }
            case 1 -> {
                for (int i = 0; i < 4; i++) {
                    g.fill(x + 2 + i * 3, y + 2, x + 3 + i * 3, y + 11, color);
                    g.fill(x + 2, y + 2 + i * 3, x + 11, y + 3 + i * 3, color);
                }
            }
            case 2 -> {
                g.fill(x + 6, y + 1, x + 8, y + 4, 0xFFF0A050);
                g.fill(x + 4, y + 3, x + 10, y + 8, 0xFFF0A050);
                g.fill(x + 3, y + 8, x + 11, y + 12, 0xFFE06030);
                g.fill(x + 6, y + 5, x + 8, y + 9, 0xFFFFE096);
            }
            case 3 -> {
                g.fill(x + 1, y + 3, x + 12, y + 5, 0xFFBEC4CD);
                g.fill(x + 4, y + 5, x + 9, y + 8, 0xFF969EAA);
                g.fill(x + 2, y + 8, x + 11, y + 11, 0xFFBEC4CD);
            }
            default -> {
                for (int i = 0; i < 7; i++) {
                    g.fill(x + 3 + i, y + 3 + i, x + 4 + i, y + 4 + i, 0xFFD25F57);
                    g.fill(x + 3 + i, y + 9 - i, x + 4 + i, y + 10 - i, 0xFFD25F57);
                }
            }
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hovered != null) {
            g.renderTooltip(font, List.of(hovered.template().getHoverName(),
                    Component.translatable("gui.universal_tool.klein.total", hovered.amount().toString())),
                    hovered.template().getTooltipImage(), mouseX, mouseY);
            return;
        }
        if (hoveredSlot != null && hoveredSlot.isActive()) return;
        String[] keys = {"gui.universal_tool.klein.quick_deposit", "gui.universal_tool.klein.tab_crafting",
                "gui.universal_tool.klein.tab_smelting", "gui.universal_tool.klein.tab_anvil",
                "gui.universal_tool.klein.clear_search"};
        for (int i = 0; i < keys.length; i++) {
            if (hoveringButton(mouseX, mouseY, i)) {
                g.renderTooltip(font, Component.translatable(keys[i]), mouseX, mouseY);
                return;
            }
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 178 && mouseX < leftPos + 189 && mouseY >= topPos + 6 && mouseY < topPos + 17) {
            onClose();
            return true;
        }
        if (button == 1 && mouseX >= leftPos + 8 && mouseX < leftPos + 122 && mouseY >= topPos + 19 && mouseY < topPos + 33) {
            search.setValue("");
            return true;
        }
        if (button == 0 && hoveringButton(mouseX, mouseY, 0)) {
            menu.sendDepositMatching();
            return true;
        }
        if (button == 0 && hoveringButton(mouseX, mouseY, 1)) {
            selectDrawer(KleinMenu.DRAWER_CRAFTING);
            return true;
        }
        if (button == 0 && hoveringButton(mouseX, mouseY, 2)) {
            selectDrawer(KleinMenu.DRAWER_SMELTING);
            return true;
        }
        if (button == 0 && hoveringButton(mouseX, mouseY, 3)) {
            selectDrawer(KleinMenu.DRAWER_ANVIL);
            return true;
        }
        if (button == 0 && hoveringButton(mouseX, mouseY, 4)) {
            search.setValue("");
            return true;
        }
        if (mouseX >= drawerX && mouseX < drawerX + 92 && mouseY >= drawerY && mouseY < drawerY + 108
                && hoveredSlot == null) {
            return true;
        }
        if (inGrid(mouseX, mouseY)) {
            int index = menu.scroll() * 9 + (int) ((mouseY - topPos - KleinMenu.GRID_Y) / 18) * 9
                    + (int) ((mouseX - leftPos - KleinMenu.GRID_X) / 18);
            menu.virtualClick(index < menu.filtered().size() ? menu.filtered().get(index) : null, button, hasShiftDown());
            return true;
        }
        if (button == 0 && mouseX >= leftPos + 174 && mouseX < leftPos + 184
                && mouseY >= topPos + KleinMenu.GRID_Y && mouseY < topPos + KleinMenu.GRID_Y + 72) {
            isDraggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (button == 0 && mouseY >= topPos + 19 && mouseY < topPos + 33) {
            if (mouseX >= leftPos + 126 && mouseX < leftPos + 163) {
                menu.setView(search.getValue(), (menu.sortMode() + 1) % 3, menu.descending());
                return true;
            }
            if (mouseX >= leftPos + 166 && mouseX < leftPos + 188) {
                menu.setView(search.getValue(), menu.sortMode(), !menu.descending());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (mouseX >= drawerX && mouseX < drawerX + 92 && mouseY >= drawerY && mouseY < drawerY + 108) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackY = topPos + KleinMenu.GRID_Y;
        double rel = Math.clamp((mouseY - trackY) / 72.0, 0.0, 1.0);
        int totalRows = Math.max(1, (menu.filtered().size() + 8) / 9);
        int maxScroll = Math.max(0, totalRows - 4);
        menu.scroll((int) Math.round(rel * maxScroll));
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inGrid(mouseX, mouseY)) {
            if (hasShiftDown() && hovered != null) menu.virtualScroll(hovered, scrollY > 0);
            else menu.scroll(menu.scroll() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean inGrid(double x, double y) {
        return x >= leftPos + KleinMenu.GRID_X && x < leftPos + KleinMenu.GRID_X + 9 * 18
                && y >= topPos + KleinMenu.GRID_Y && y < topPos + KleinMenu.GRID_Y + 4 * 18;
    }

    private static String compact(BigInteger amount) {
        if (amount.compareTo(BigInteger.valueOf(10000)) < 0) return amount.toString();
        String digits = amount.toString();
        int exponent = digits.length() - 1;
        BigDecimal value = new BigDecimal(amount).movePointLeft(exponent).setScale(1, RoundingMode.DOWN);
        String suffix = exponent < 6 ? "K" : exponent < 9 ? "M" : exponent < 12 ? "B" : "e" + exponent;
        return value.stripTrailingZeros().toPlainString() + suffix;
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
