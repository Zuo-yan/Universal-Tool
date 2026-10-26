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
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.klein.KleinEntryData;
import org.gwfx.universaltool.klein.KleinMenu;

public final class KleinScreen extends AbstractContainerScreen<KleinMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/gui/container/klein_terminal.png");
    private static final int CYAN = 0xFF62D9D1, BRASS = 0xFFB29A61, TEXT = 0xFFE2D8B8;
    private EditBox search;
    private KleinEntryData hovered;
    private boolean isDraggingScrollbar = false;

    public KleinScreen(KleinMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 260;
        imageHeight = 264;
    }

    @Override protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 23, topPos + 26, 138, 14, Component.empty());
        search.setMaxLength(64);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("gui.universal_tool.klein.search"));
        search.setResponder(value -> menu.setView(value, menu.quantitySort(), menu.descending()));
        addRenderableWidget(search);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        // Render the full authentic GUI texture sheet:
        g.blit(GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 512, 512);

        // Dynamic Header Title & Badge
        g.drawString(font, Component.translatable("item.universal_tool.klein_bottle"), x + 28, y + 10, TEXT, false);
        g.drawString(font, Component.literal("| ").append(Component.translatable("gui.universal_tool.klein.private")), x + 76, y + 10, 0xFF7D9DA8, false);

        // Dynamic Section Headers
        g.drawString(font, Component.literal("✧ ").append(Component.translatable("gui.universal_tool.klein.infinite")).append(" ✧"), x + 12, y + 36, BRASS, false);
        g.drawString(font, Component.literal("✧ ").append(Component.translatable("gui.universal_tool.klein.inventory")).append(" ✧"), x + 12, y + 160, BRASS, false);

        // Dynamic Footer Hint
        String hint = Component.translatable("gui.universal_tool.klein.hint").getString();
        g.drawString(font, hint, x + imageWidth - font.width(hint) - 14, y + 252, 0xFF6A868E, false);

        // Dynamic close button hover:
        boolean closeHover = mouseX >= x + 241 && mouseX < x + 255 && mouseY >= y + 5 && mouseY < y + 19;
        if (closeHover) {
            g.fill(x + 242, y + 6, x + 254, y + 18, 0x60FF4040);
        }

        // Dynamic Subheader item types counter:
        String typesStr = Component.translatable("gui.universal_tool.klein.types", menu.filtered().size()).getString();
        g.drawString(font, typesStr, x + imageWidth - font.width(typesStr) - 18, y + 38, BRASS, false);

        // Dynamic Sort button labels & active highlights:
        boolean nameActive = !menu.quantitySort();
        boolean qtyActive = menu.quantitySort();
        int nameColor = nameActive ? CYAN : TEXT;
        int qtyColor = qtyActive ? CYAN : TEXT;
        g.drawCenteredString(font, Component.translatable("gui.universal_tool.klein.name"), x + 182, y + 28, nameColor);
        String qtyLabel = qtyActive ? (menu.descending() ? "数量▼" : "数量▲") : "数量";
        g.drawCenteredString(font, Component.literal(qtyLabel), x + 219, y + 28, qtyColor);
        g.drawCenteredString(font, Component.literal(menu.descending() ? "↓" : "↑"), x + 249, y + 28, CYAN);

        if (nameActive) {
            border(g, x + 168, y + 24, 28, 16, CYAN);
        } else if (qtyActive) {
            border(g, x + 200, y + 24, 38, 16, CYAN);
        }

        // Virtual Grid: 13 columns x 6 rows = 78 slots
        hovered = null;
        int start = menu.scroll() * 13;
        for (int cell = 0; cell < 78; cell++) {
            int col = cell % 13, row = cell / 13;
            int sx = x + KleinMenu.GRID_X + col * 18, sy = y + KleinMenu.GRID_Y + row * 18;
            boolean isHovered = mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
            int index = start + cell;
            if (index >= menu.filtered().size()) continue;
            KleinEntryData entry = menu.filtered().get(index);
            g.renderItem(entry.template(), sx + 1, sy + 1);
            String count = compact(entry.amount());
            int width = font.width(count);
            g.fill(sx + 18 - width - 1, sy + 9, sx + 18, sy + 18, 0xD00B151A);
            g.drawString(font, count, sx + 18 - width, sy + 9, 0xFFFFFFFF, false);
            if (isHovered) {
                hovered = entry;
                // Draw cyan glowing hover outline from texture sheet:
                g.blit(GUI_TEXTURE, sx, sy, 280, 24, 18, 18, 512, 512);
            }
        }

        // Dynamic Scrollbar Thumb
        int trackY = y + KleinMenu.GRID_Y;
        int totalRows = Math.max(1, (menu.filtered().size() + 12) / 13);
        int maxScroll = Math.max(1, totalRows - 6);
        int thumbHeight = Math.max(16, 108 * 6 / totalRows);
        int thumbY = trackY + (108 - thumbHeight) * menu.scroll() / maxScroll;
        g.blit(GUI_TEXTURE, x + 246, thumbY, 280, 48, 4, thumbHeight, 512, 512);

        // Locked bottle slot highlight & padlock badge
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            boolean isLocked = slot.container == menu.getSlot(0).container && slot.getContainerSlot() == menu.lockedInventorySlot();
            if (isLocked) {
                int sx = x + slot.x - 1, sy = y + slot.y - 1;
                g.blit(GUI_TEXTURE, sx, sy, 280, 0, 18, 18, 512, 512);
                g.blit(GUI_TEXTURE, sx + 9, sy + 9, 304, 0, 9, 9, 512, 512);
            }
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hovered != null) {
            g.renderTooltip(font, List.of(hovered.template().getHoverName(),
                    Component.translatable("gui.universal_tool.klein.total", hovered.amount().toString())), hovered.template().getTooltipImage(), mouseX, mouseY);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Close button
        if (button == 0 && mouseX >= leftPos + 241 && mouseX < leftPos + 255 && mouseY >= topPos + 5 && mouseY < topPos + 19) {
            onClose();
            return true;
        }

        // Grid slot click
        if (inGrid(mouseX, mouseY)) {
            int index = menu.scroll() * 13 + (int) ((mouseY - topPos - KleinMenu.GRID_Y) / 18) * 13
                    + (int) ((mouseX - leftPos - KleinMenu.GRID_X) / 18);
            menu.virtualClick(index < menu.filtered().size() ? menu.filtered().get(index) : null, button, hasShiftDown());
            return true;
        }

        // Scrollbar track click
        if (button == 0 && mouseX >= leftPos + 244 && mouseX < leftPos + 252
                && mouseY >= topPos + KleinMenu.GRID_Y && mouseY < topPos + KleinMenu.GRID_Y + 108) {
            isDraggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        // Sort buttons
        if (button == 0 && mouseY >= topPos + 24 && mouseY < topPos + 40) {
            if (mouseX >= leftPos + 168 && mouseX < leftPos + 196) menu.setView(search.getValue(), false, menu.descending());
            else if (mouseX >= leftPos + 200 && mouseX < leftPos + 238) menu.setView(search.getValue(), true, menu.descending());
            else if (mouseX >= leftPos + 242 && mouseX < leftPos + 256) menu.setView(search.getValue(), menu.quantitySort(), !menu.descending());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        double rel = Math.clamp((mouseY - trackY) / 108.0, 0.0, 1.0);
        int totalRows = Math.max(1, (menu.filtered().size() + 12) / 13);
        int maxScroll = Math.max(0, totalRows - 6);
        menu.scroll((int) Math.round(rel * maxScroll));
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inGrid(mouseX, mouseY)) {
            if (hasShiftDown() && hovered != null) {
                menu.virtualScroll(hovered, scrollY > 0);
            } else {
                menu.scroll(menu.scroll() - (int) Math.signum(scrollY));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean inGrid(double x, double y) {
        return x >= leftPos + KleinMenu.GRID_X && x < leftPos + KleinMenu.GRID_X + 13 * 18
                && y >= topPos + KleinMenu.GRID_Y && y < topPos + KleinMenu.GRID_Y + 6 * 18;
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
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
