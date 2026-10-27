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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.klein.KleinDrawerPayload;
import org.gwfx.universaltool.klein.KleinEntryData;
import org.gwfx.universaltool.klein.KleinMenu;
import org.gwfx.universaltool.klein.KleinRenamePayload;

public final class KleinScreen extends AbstractContainerScreen<KleinMenu> {
    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/gui/container/klein_terminal.png");
    private static final int CYAN = 0xFF62D9D1, BRASS = 0xFFB29A61, TEXT = 0xFFE2D8B8;
    private static final int BUTTON_X = 172, BUTTON_Y = 153, BUTTON_STEP = 15, BUTTON_SIZE = 14;
    private static final int SCROLL_TRACK_HEIGHT = KleinMenu.VISIBLE_ROWS * 18;
    private EditBox search;
    private EditBox anvilName;
    private KleinEntryData hovered;
    private boolean isDraggingScrollbar = false;
    private double scrollbarGrabOffset;
    private double wheelRemainder;
    private int drawerX, drawerY;
    private ItemStack lastAnvilLeft = ItemStack.EMPTY;

    public KleinScreen(KleinMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 194;
        imageHeight = 236;
    }

    @Override protected void init() {
        super.init();
        leftPos = Math.max(2, (width - imageWidth) / 2);
        topPos = Math.max(2, (height - imageHeight) / 2);
        // Spacious search bar (y=24..38)
        search = new EditBox(font, leftPos + 22, topPos + 26, 96, 11, Component.empty());
        search.setMaxLength(64);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("gui.universal_tool.klein.search"));
        search.setResponder(value -> menu.setView(value, menu.sortMode(), menu.descending()));
        addRenderableWidget(search);

        // Anvil rename box inside the drawer (y=23, height=10, vertically centered)
        updateDrawerPos();
        anvilName = new EditBox(font, drawerX + 18, drawerY + 23, 82, 10, Component.empty());
        anvilName.setMaxLength(50);
        anvilName.setBordered(false);
        anvilName.setTextColor(TEXT);
        anvilName.setHint(Component.translatable("gui.universal_tool.klein.anvil_name_hint"));
        anvilName.setResponder(this::onAnvilNameChanged);
        anvilName.setVisible(menu.drawerMode() == KleinMenu.DRAWER_ANVIL);
        addRenderableWidget(anvilName);
    }

    private void updateDrawerPos() {
        int centered = Math.max(2, (width - imageWidth) / 2);
        leftPos = menu.drawerMode() == KleinMenu.DRAWER_NONE
                ? centered : Math.max(centered, -KleinMenu.DRAWER_X);
        drawerX = leftPos + KleinMenu.DRAWER_X;
        drawerY = topPos + KleinMenu.DRAWER_Y;
        if (search != null) search.setPosition(leftPos + 22, topPos + 26);
    }

    public List<net.minecraft.client.renderer.Rect2i> jeiExtraAreas() {
        updateDrawerPos();
        return menu.drawerMode() == KleinMenu.DRAWER_NONE ? List.of()
                : List.of(new net.minecraft.client.renderer.Rect2i(drawerX, drawerY,
                        KleinMenu.DRAWER_WIDTH, KleinMenu.DRAWER_HEIGHT));
    }

    private void onAnvilNameChanged(String newName) {
        PacketDistributor.sendToServer(new KleinRenamePayload(newName));
        menu.setAnvilItemName(newName);
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (menu.drawerMode() == KleinMenu.DRAWER_ANVIL) {
            ItemStack currentLeft = menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).getItem();
            if (!ItemStack.matches(currentLeft, lastAnvilLeft)) {
                lastAnvilLeft = currentLeft.copy();
                if (!anvilName.isFocused()) {
                    String defaultName = currentLeft.isEmpty() ? "" : currentLeft.getHoverName().getString();
                    anvilName.setValue(defaultName);
                    PacketDistributor.sendToServer(new KleinRenamePayload(defaultName));
                }
            }
        }
    }

    private void selectDrawer(int mode) {
        int next = menu.drawerMode() == mode ? KleinMenu.DRAWER_NONE : mode;
        menu.setDrawerMode(next);
        PacketDistributor.sendToServer(new KleinDrawerPayload(next));
        if (anvilName != null) {
            updateDrawerPos();
            anvilName.setPosition(drawerX + 18, drawerY + 23);
            anvilName.setVisible(next == KleinMenu.DRAWER_ANVIL);
        }
    }

    private int buttonY(int index) { return topPos + BUTTON_Y + index * BUTTON_STEP; }

    private boolean hoveringButton(double mouseX, double mouseY, int index) {
        int by = buttonY(index);
        return mouseX >= leftPos + BUTTON_X && mouseX < leftPos + BUTTON_X + BUTTON_SIZE
                && mouseY >= by && mouseY < by + BUTTON_SIZE;
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        updateDrawerPos();
        if (anvilName != null) {
            anvilName.setPosition(drawerX + 18, drawerY + 23);
            anvilName.setVisible(menu.drawerMode() == KleinMenu.DRAWER_ANVIL);
        }

        g.blit(GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 512, 512);

        // Header bar: Title & Count Badge (y=9, perfectly vertically centered)
        g.drawString(font, Component.translatable("item.universal_tool.klein_bottle"), x + 26, y + 9, TEXT, false);
        String countBadge = menu.filtered().size() + " / \u221e";
        g.drawString(font, countBadge, x + 172 - font.width(countBadge), y + 9, BRASS, false);

        // Close button hover highlight (y=7..18)
        if (mouseX >= x + 178 && mouseX < x + 189 && mouseY >= y + 7 && mouseY < y + 18) {
            g.fill(x + 178, y + 7, x + 188, y + 17, 0x60FF4040);
        }

        // Sort Button: (126, 24, w=37, h=15)
        String modeLabel = switch (menu.sortMode()) {
            case KleinMenu.SORT_NAME -> Component.translatable("gui.universal_tool.klein.name").getString();
            case KleinMenu.SORT_MOD -> Component.translatable("gui.universal_tool.klein.sort_mod").getString();
            default -> Component.translatable("gui.universal_tool.klein.quantity").getString();
        };
        g.drawCenteredString(font, Component.literal(modeLabel), x + 144, y + 28, CYAN);
        border(g, x + 126, y + 24, 37, 15, CYAN);

        // Direction Button: (166, 24, w=21, h=15)
        String dirLabel = menu.descending() ? "\u2193" : "\u2191";
        g.drawCenteredString(font, Component.literal(dirLabel), x + 176, y + 28, CYAN);
        border(g, x + 166, y + 24, 21, 15, menu.descending() ? CYAN : BRASS);

        // Inventory section divider label
        g.drawString(font, Component.literal("\u2727 ").append(Component.translatable("gui.universal_tool.klein.inventory")).append(" \u2727"), x + 8, y + 142, BRASS, false);

        // Right action buttons (5 buttons)
        for (int i = 0; i < 5; i++) {
            int by = buttonY(i);
            drawButtonGlyph(g, x + BUTTON_X, by, i, hoveringButton(mouseX, mouseY, i));
        }

        // 5 FULL ROWS WAREHOUSE STORAGE (45 cells, y=46..135)
        hovered = null;
        int start = menu.scroll() * 9;
        for (int cell = 0; cell < KleinMenu.VISIBLE_ROWS * 9; cell++) {
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

        // Scrollbar track (height=90px, y=46..135)
        int thumbHeight = scrollbarThumbHeight();
        int thumbY = scrollbarThumbY();
        // 3-slice smooth rendering: top cap (4px), middle (height-8), bottom cap (4px at y=136 in atlas)
        g.blit(GUI_TEXTURE, x + 175, thumbY, 200, 48, 8, 4, 512, 512);
        if (thumbHeight > 8) {
            g.blit(GUI_TEXTURE, x + 175, thumbY + 4, 200, 52, 8, thumbHeight - 8, 512, 512);
        }
        g.blit(GUI_TEXTURE, x + 175, thumbY + thumbHeight - 4, 200, 136, 8, 4, 512, 512);

        // Use the same locked-slot frame for every item forbidden from storage.
        for (Slot slot : menu.slots) {
            if (slot.isActive() && menu.isStorageLocked(slot)) {
                int sx = x + slot.x - 1, sy = y + slot.y - 1;
                g.blit(GUI_TEXTURE, sx, sy, 200, 0, 18, 18, 512, 512);
            }
        }

        renderDrawer(g, mouseX, mouseY);
    }

    private void renderDrawer(GuiGraphics g, int mouseX, int mouseY) {
        int mode = menu.drawerMode();
        if (mode == KleinMenu.DRAWER_NONE) return;
        g.blit(GUI_TEXTURE, drawerX, drawerY, 300, 0, KleinMenu.DRAWER_WIDTH, KleinMenu.DRAWER_HEIGHT, 512, 512);

        Component title = switch (mode) {
            case KleinMenu.DRAWER_CRAFTING -> Component.translatable("gui.universal_tool.klein.tab_crafting");
            case KleinMenu.DRAWER_SMELTING -> Component.translatable("gui.universal_tool.klein.tab_smelting");
            default -> Component.translatable("gui.universal_tool.klein.tab_anvil");
        };
        g.drawCenteredString(font, title, drawerX + KleinMenu.DRAWER_WIDTH / 2, drawerY + 6, BRASS);

        if (mode == KleinMenu.DRAWER_CRAFTING) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int sx = drawerX + KleinMenu.DRAWER_GRID_X + col * KleinMenu.DRAWER_SLOT_STEP;
                    int sy = drawerY + KleinMenu.DRAWER_GRID_Y + row * KleinMenu.DRAWER_SLOT_STEP;
                    drawSocket(g, sx, sy);
                }
            }
            g.blit(GUI_TEXTURE, drawerX + 69, drawerY + 48, 216, 48, 15, 11, 512, 512);
            drawSocket(g, drawerX + KleinMenu.DRAWER_RESULT_X, drawerY + KleinMenu.DRAWER_RESULT_Y);
        } else if (mode == KleinMenu.DRAWER_SMELTING) {
            drawSocket(g, drawerX + 50, drawerY + 24);
            g.blit(GUI_TEXTURE, drawerX + 53, drawerY + 45, 248, 48, 12, 14, 512, 512);
            drawSocket(g, drawerX + 50, drawerY + 61);
        } else {
            // Anvil Mode: Rename box frame at (14, 20, w=90, h=16)
            g.fill(drawerX + 14, drawerY + 20, drawerX + 104, drawerY + 36, 0xFF060E12);
            border(g, drawerX + 14, drawerY + 20, 90, 16,
                    anvilName.isFocused() ? CYAN : 0xFF183E46);

            drawSocket(g, drawerX + KleinMenu.ANVIL_LEFT_X, drawerY + KleinMenu.DRAWER_RESULT_Y);
            g.blit(GUI_TEXTURE, drawerX + 36, drawerY + 49, 236, 48, 7, 7, 512, 512);
            drawSocket(g, drawerX + KleinMenu.ANVIL_RIGHT_X, drawerY + KleinMenu.DRAWER_RESULT_Y);
            g.blit(GUI_TEXTURE, drawerX + 71, drawerY + 48, 216, 48, 15, 11, 512, 512);
            drawSocket(g, drawerX + KleinMenu.DRAWER_RESULT_X, drawerY + KleinMenu.DRAWER_RESULT_Y);
        }
    }

    private void drawSocket(GuiGraphics g, int x, int y) {
        g.blit(GUI_TEXTURE, x, y, 268, 24, 18, 18, 512, 512);
    }

    private void drawButtonGlyph(GuiGraphics g, int x, int y, int index, boolean hovered) {
        boolean active = (index == 1 && menu.drawerMode() == KleinMenu.DRAWER_CRAFTING)
                || (index == 2 && menu.drawerMode() == KleinMenu.DRAWER_SMELTING)
                || (index == 3 && menu.drawerMode() == KleinMenu.DRAWER_ANVIL);
        int frameU = active ? 232 : (hovered ? 216 : 200);
        g.blit(GUI_TEXTURE, x, y, frameU, 150, 14, 14, 512, 512);
        int glyphU = 200 + index * 16;
        int offset = active ? 1 : 0;
        g.blit(GUI_TEXTURE, x + 1 + offset, y + 1 + offset, glyphU, 170, 12, 12, 512, 512);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateDrawerPos();
        super.render(g, mouseX, mouseY, partialTick);
        // Draw above the real inventory items so their icons cannot hide the badge.
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (Slot slot : menu.slots) {
            if (slot.isActive() && menu.isStorageLocked(slot)) {
                g.blit(GUI_TEXTURE, leftPos + slot.x + 8, topPos + slot.y + 8,
                        222, 0, 9, 10, 512, 512);
            }
        }
        g.pose().popPose();
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
        // Widgets must receive focus before the custom hit areas, but container
        // slots must not consume clicks intended for our own controls.
        for (var child : children()) {
            if (!child.isMouseOver(mouseX, mouseY) || !child.mouseClicked(mouseX, mouseY, button)) continue;
            if (child == anvilName && anvilName.isVisible()) {
                setFocused(anvilName);
                anvilName.setFocused(true);
            } else {
                setFocused(child);
            }
            return true;
        }
        if (button == 0 && mouseX >= leftPos + 178 && mouseX < leftPos + 189 && mouseY >= topPos + 7 && mouseY < topPos + 18) {
            onClose();
            return true;
        }
        if (button == 1 && anvilName.isVisible()
                && mouseX >= anvilName.getX() && mouseX < anvilName.getX() + anvilName.getWidth()
                && mouseY >= anvilName.getY() && mouseY < anvilName.getY() + anvilName.getHeight()) {
            anvilName.setValue("");
            setFocused(anvilName);
            anvilName.setFocused(true);
            return true;
        }
        if (button == 1 && mouseX >= leftPos + 8 && mouseX < leftPos + 122 && mouseY >= topPos + 24 && mouseY < topPos + 38) {
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
        if (inGrid(mouseX, mouseY)) {
            int index = menu.scroll() * 9 + (int) ((mouseY - topPos - KleinMenu.GRID_Y) / 18) * 9
                    + (int) ((mouseX - leftPos - KleinMenu.GRID_X) / 18);
            menu.virtualClick(index < menu.filtered().size() ? menu.filtered().get(index) : null, button, hasShiftDown());
            return true;
        }
        if (button == 0 && mouseX >= leftPos + 174 && mouseX < leftPos + 184
                && mouseY >= topPos + KleinMenu.GRID_Y
                && mouseY < topPos + KleinMenu.GRID_Y + SCROLL_TRACK_HEIGHT) {
            isDraggingScrollbar = true;
            int thumbY = scrollbarThumbY();
            int thumbHeight = scrollbarThumbHeight();
            boolean onThumb = mouseY >= thumbY && mouseY < thumbY + thumbHeight;
            double exactThumbY = topPos + KleinMenu.GRID_Y + (menu.maxScroll() == 0 ? 0
                    : (SCROLL_TRACK_HEIGHT - thumbHeight) * (double) menu.scroll() / menu.maxScroll());
            scrollbarGrabOffset = onThumb ? mouseY - exactThumbY : thumbHeight / 2.0;
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (button == 0 && mouseY >= topPos + 24 && mouseY < topPos + 38) {
            if (mouseX >= leftPos + 126 && mouseX < leftPos + 163) {
                menu.setView(search.getValue(), (menu.sortMode() + 1) % 3, menu.descending());
                return true;
            }
            if (mouseX >= leftPos + 166 && mouseX < leftPos + 187) {
                menu.setView(search.getValue(), menu.sortMode(), !menu.descending());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (anvilName != null && anvilName.isVisible() && anvilName.isFocused()) {
            if (keyCode == 256) {
                anvilName.setFocused(false);
                return true;
            }
            anvilName.keyPressed(keyCode, scanCode, modifiers);
            // Printable keys are delivered separately through charTyped. Do not
            // let the inventory shortcut close the screen while typing a name.
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char codePoint, int modifiers) {
        if (anvilName != null && anvilName.isVisible() && anvilName.isFocused()) {
            if (anvilName.charTyped(codePoint, modifiers)) return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (mouseX >= drawerX && mouseX < drawerX + KleinMenu.DRAWER_WIDTH && mouseY >= drawerY && mouseY < drawerY + KleinMenu.DRAWER_HEIGHT) {
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

    private int scrollbarThumbHeight() {
        int totalRows = Math.max(1, (menu.filtered().size() + 8) / 9);
        return menu.maxScroll() == 0 ? SCROLL_TRACK_HEIGHT
                : Math.max(14, Math.min(SCROLL_TRACK_HEIGHT,
                (int) Math.round((KleinMenu.VISIBLE_ROWS / (double) totalRows) * SCROLL_TRACK_HEIGHT)));
    }

    private int scrollbarThumbY() {
        int trackY = topPos + KleinMenu.GRID_Y;
        int maxScroll = menu.maxScroll();
        return maxScroll == 0 ? trackY : trackY + (int) Math.round(
                (SCROLL_TRACK_HEIGHT - scrollbarThumbHeight()) * (double) menu.scroll() / maxScroll);
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackY = topPos + KleinMenu.GRID_Y;
        int maxScroll = menu.maxScroll();
        if (maxScroll == 0) {
            menu.scroll(0);
            return;
        }
        double scrollableDistance = SCROLL_TRACK_HEIGHT - scrollbarThumbHeight();
        double clickOffset = (mouseY - trackY) - scrollbarGrabOffset;
        double rel = Math.clamp(clickOffset / scrollableDistance, 0.0, 1.0);
        menu.scroll((int) Math.round(rel * maxScroll));
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inGrid(mouseX, mouseY)) {
            if (hasShiftDown() && hovered != null) {
                menu.virtualScroll(hovered, scrollY > 0);
            } else {
                scrollRows(scrollY);
            }
            return true;
        }
        if (inScrollbarTrack(mouseX, mouseY)) {
            scrollRows(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void scrollRows(double delta) {
        if (delta == 0) return;
        if (Math.signum(delta) != Math.signum(wheelRemainder)) wheelRemainder = 0;
        wheelRemainder += delta;
        int rows = (int) wheelRemainder;
        if (rows != 0) {
            menu.scroll(menu.scroll() - rows);
            wheelRemainder -= rows;
        }
    }

    private boolean inGrid(double x, double y) {
        return x >= leftPos + KleinMenu.GRID_X && x < leftPos + KleinMenu.GRID_X + 9 * 18
                && y >= topPos + KleinMenu.GRID_Y
                && y < topPos + KleinMenu.GRID_Y + KleinMenu.VISIBLE_ROWS * 18;
    }

    private boolean inScrollbarTrack(double x, double y) {
        return x >= leftPos + 174 && x < leftPos + 184
                && y >= topPos + KleinMenu.GRID_Y
                && y < topPos + KleinMenu.GRID_Y + SCROLL_TRACK_HEIGHT;
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
