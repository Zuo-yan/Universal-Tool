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
        imageWidth = 194;
        imageHeight = 216;
    }

    @Override protected void init() {
        super.init();
        search = new EditBox(font, leftPos + 22, topPos + 20, 92, 12, Component.empty());
        search.setMaxLength(64);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("gui.universal_tool.klein.search"));
        search.setResponder(value -> menu.setView(value, menu.quantitySort(), menu.descending()));
        addRenderableWidget(search);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;

        // 1. Base GUI Texture (194x216)
        g.blit(GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 512, 512);

        // 2. Compact Header: Title & Count Badge
        g.drawString(font, Component.translatable("item.universal_tool.klein_bottle"), x + 22, y + 7, TEXT, false);
        String countBadge = menu.filtered().size() + " / ∞";
        g.drawString(font, countBadge, x + 172 - font.width(countBadge), y + 7, BRASS, false);

        // 3. Close button hover effect
        boolean closeHover = mouseX >= x + 178 && mouseX < x + 189 && mouseY >= y + 6 && mouseY < y + 17;
        if (closeHover) {
            g.fill(x + 178, y + 6, x + 188, y + 16, 0x60FF4040);
        }

        // 4. Sort buttons (Quantity/Name toggle at 120..158, Direction toggle at 162..184)
        String modeLabel = menu.quantitySort() ? "数量" : "名称";
        g.drawCenteredString(font, Component.literal(modeLabel), x + 139, y + 22, CYAN);
        border(g, x + 120, y + 19, 38, 14, CYAN);

        String dirLabel = menu.descending() ? "↓" : "↑";
        g.drawCenteredString(font, Component.literal(dirLabel), x + 173, y + 22, CYAN);
        border(g, x + 162, y + 19, 22, 14, menu.descending() ? CYAN : BRASS);

        // 5. Inventory Section Label
        g.drawString(font, Component.literal("✧ ").append(Component.translatable("gui.universal_tool.klein.inventory")).append(" ✧"), x + 8, y + 122, BRASS, false);

        // 6. Virtual Warehouse Grid: 9 columns x 4 rows = 36 slots at y=42..114
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
                // Cyan glowing hover outline from atlas (at 200, 24)
                g.blit(GUI_TEXTURE, sx, sy, 200, 24, 18, 18, 512, 512);
            }
        }

        // 7. Dynamic Scrollbar Thumb (track height=72)
        int trackY = y + KleinMenu.GRID_Y;
        int totalRows = Math.max(1, (menu.filtered().size() + 8) / 9);
        int maxScroll = Math.max(1, totalRows - 4);
        int thumbHeight = Math.max(14, 72 * 4 / totalRows);
        int thumbY = trackY + (72 - thumbHeight) * menu.scroll() / maxScroll;
        g.blit(GUI_TEXTURE, x + 176, thumbY, 200, 48, 6, thumbHeight, 512, 512);

        // 8. Locked bottle slot highlight & padlock badge from atlas
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            boolean isLocked = slot.container == menu.getSlot(0).container && slot.getContainerSlot() == menu.lockedInventorySlot();
            if (isLocked) {
                int sx = x + slot.x - 1, sy = y + slot.y - 1;
                g.blit(GUI_TEXTURE, sx, sy, 200, 0, 18, 18, 512, 512);
                g.blit(GUI_TEXTURE, sx + 9, sy + 9, 222, 0, 9, 10, 512, 512);
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
        if (button == 0 && mouseX >= leftPos + 178 && mouseX < leftPos + 189 && mouseY >= topPos + 6 && mouseY < topPos + 17) {
            onClose();
            return true;
        }

        // Grid slot click
        if (inGrid(mouseX, mouseY)) {
            int index = menu.scroll() * 9 + (int) ((mouseY - topPos - KleinMenu.GRID_Y) / 18) * 9
                    + (int) ((mouseX - leftPos - KleinMenu.GRID_X) / 18);
            menu.virtualClick(index < menu.filtered().size() ? menu.filtered().get(index) : null, button, hasShiftDown());
            return true;
        }

        // Scrollbar track click
        if (button == 0 && mouseX >= leftPos + 174 && mouseX < leftPos + 184
                && mouseY >= topPos + KleinMenu.GRID_Y && mouseY < topPos + KleinMenu.GRID_Y + 72) {
            isDraggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        // Sort buttons
        if (button == 0 && mouseY >= topPos + 19 && mouseY < topPos + 33) {
            if (mouseX >= leftPos + 120 && mouseX < leftPos + 158) {
                menu.setView(search.getValue(), !menu.quantitySort(), menu.descending());
                return true;
            }
            if (mouseX >= leftPos + 162 && mouseX < leftPos + 184) {
                menu.setView(search.getValue(), menu.quantitySort(), !menu.descending());
                return true;
            }
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
        double rel = Math.clamp((mouseY - trackY) / 72.0, 0.0, 1.0);
        int totalRows = Math.max(1, (menu.filtered().size() + 8) / 9);
        int maxScroll = Math.max(0, totalRows - 4);
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
