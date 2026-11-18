package org.gwfx.universaltool.client;

import static org.gwfx.universaltool.client.KleinScreenStyle.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.klein.KleinAutoDepositPayload;
import org.gwfx.universaltool.klein.KleinDrawerPayload;
import org.gwfx.universaltool.klein.KleinEntryData;
import org.gwfx.universaltool.klein.KleinMenu;
import org.gwfx.universaltool.klein.KleinRenamePayload;
import org.lwjgl.glfw.GLFW;

public final class KleinScreen extends AbstractContainerScreen<KleinMenu> {
    private static final int MAIN_WIDTH = 190;
    private static final int HEIGHT = 237;
    private static final int COLUMNS = 9;
    private static final int CELL = 18;
    private static final int SCROLL_X = 175;
    private static final int GRID_HEIGHT = KleinMenu.VISIBLE_ROWS * CELL;
    private static final int DOCK = KleinMenu.DRAWER_X;
    private static final int DOCK_WIDTH = KleinMenu.DRAWER_WIDTH;
    private static final int SEARCH_X = 8, SEARCH_Y = 24, SEARCH_W = 104, SEARCH_H = 14;

    private enum Control {
        CLEAR(99, SEARCH_Y + 1, 12, 12),
        SORT(116, SEARCH_Y, 44, SEARCH_H),
        DIRECTION(164, SEARCH_Y, 18, SEARCH_H),
        CLOSE(DOCK + 96, 5, 14, 14),
        DEPOSIT(DOCK + 7, 24, 102, 17),
        AUTO(DOCK + 7, 45, 102, 17),
        CRAFT(DOCK + 7, 72, 32, 27),
        SMELT(DOCK + 42, 72, 32, 27),
        ANVIL(DOCK + 77, 72, 32, 27);

        final int x, y, w, h;

        Control(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private static final Control[] CONTROLS = Control.values();
    private record StockFlash(long started, int color) {}

    private final float[] hoveredControls = new float[CONTROLS.length];
    private Map<Long, BigInteger> visibleAmounts = new HashMap<>();
    private Map<Long, BigInteger> incomingAmounts = new HashMap<>();
    private final Map<Long, StockFlash> stockFlashes = new HashMap<>();
    private final ItemStack bottleIcon = ModItems.KLEIN_BOTTLE.get().getDefaultInstance();
    private final ItemStack craftIcon = new ItemStack(Items.CRAFTING_TABLE);
    private final ItemStack smeltIcon = new ItemStack(Items.FURNACE);
    private final ItemStack anvilIcon = new ItemStack(Items.ANVIL);
    private final long openedAt = Util.getMillis();
    private long now = openedAt;
    private long drawerChangedAt;
    private long viewChangedAt;
    private long depositPressedAt;
    private float searchFocus;
    private float autoSwitch;
    private float scrollThumb;
    private int drawerSlide;
    private int workspaceMode;
    private int previousScroll;
    private int visibleCount;
    private int capturedButton = -1;
    private boolean draggingScrollbar;
    private double grabOffset;
    private double wheelRemainder;
    private EditBox search;
    private EditBox anvilName;
    private ItemStack previousAnvilInput = ItemStack.EMPTY;

    public KleinScreen(KleinMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = DOCK + DOCK_WIDTH;
        imageHeight = HEIGHT;
        workspaceMode = menu.drawerMode();
        autoSwitch = menu.autoDeposit() ? 1 : 0;
    }

    @Override protected void init() {
        super.init();
        String priorQuery = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + 21, topPos + 27, 77, 10, label("search"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(INK);
        search.setHint(label("search").withStyle(style -> style.withColor(SECONDARY)));
        search.setValue(priorQuery);
        search.setResponder(value -> changeView(value, menu.sortMode(), menu.descending()));
        addRenderableWidget(search);

        String priorName = anvilName == null ? menu.anvilItemName() : anvilName.getValue();
        anvilName = new EditBox(font, leftPos + DOCK + 12, drawerY() + 28, 92, 10, label("anvil_name_hint"));
        anvilName.setBordered(false);
        anvilName.setMaxLength(50);
        anvilName.setTextColor(INK);
        anvilName.setHint(label("anvil_name_hint").withStyle(style -> style.withColor(SECONDARY)));
        anvilName.setValue(priorName);
        anvilName.setResponder(name -> {
            menu.setAnvilItemName(name);
            PacketDistributor.sendToServer(new KleinRenamePayload(name));
        });
        anvilName.setVisible(menu.drawerMode() == KleinMenu.DRAWER_ANVIL);
        addRenderableWidget(anvilName);
        scrollThumb = targetThumb();
    }

    @Override protected void containerTick() {
        if (menu.drawerMode() != KleinMenu.DRAWER_ANVIL) return;
        ItemStack input = menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).getItem();
        if (ItemStack.matches(previousAnvilInput, input)) return;
        previousAnvilInput = input.copy();
        if (!anvilName.isFocused()) anvilName.setValue(input.isEmpty() ? "" : input.getHoverName().getString());
    }

    private void changeView(String query, int sortMode, boolean descending) {
        menu.setView(query, sortMode, descending);
        viewChangedAt = Util.getMillis();
        visibleAmounts.clear();
        stockFlashes.clear();
    }

    private void selectDrawer(int mode) {
        int next = menu.drawerMode() == mode ? KleinMenu.DRAWER_NONE : mode;
        menu.setDrawerMode(next);
        PacketDistributor.sendToServer(new KleinDrawerPayload(next));
        updateDrawerLayout(next, Util.getMillis());
    }

    private void updateDrawerLayout(int mode, long time) {
        if (mode != workspaceMode) {
            workspaceMode = mode;
            drawerChangedAt = time;
            if (mode != KleinMenu.DRAWER_ANVIL && getFocused() == anvilName) setFocused(null);
            anvilName.setVisible(mode == KleinMenu.DRAWER_ANVIL);
        }
        int slideDistance = mode == KleinMenu.DRAWER_ANVIL ? 4 : 8;
        drawerSlide = Math.round(-slideDistance * (1f - easeOut(progress(time, drawerChangedAt, 260))));
        anvilName.setPosition(leftPos + DOCK + 12, drawerY() + 28 + drawerSlide);
    }

    private int drawerY() {
        return topPos + KleinMenu.DRAWER_Y;
    }

    private void animate(int mouseX, int mouseY) {
        long time = Util.getMillis();
        float step = Math.clamp((time - now) / 130f, 0f, 1f);
        now = time;
        Control hover = controlAt(mouseX, mouseY);
        for (Control control : CONTROLS) {
            int index = control.ordinal();
            hoveredControls[index] = move(hoveredControls[index], hover == control, step);
        }
        searchFocus = move(searchFocus, search.isFocused(), step);
        autoSwitch = move(autoSwitch, menu.autoDeposit(), step);
        float target = targetThumb();
        scrollThumb = draggingScrollbar ? target : scrollThumb + (target - scrollThumb) * Math.min(1f, step * 2);
        updateDrawerLayout(menu.drawerMode(), time);
    }

    private void observeStock() {
        List<KleinEntryData> entries = menu.filtered();
        int start = menu.scroll() * COLUMNS;
        int end = Math.min(entries.size(), start + KleinMenu.VISIBLE_ROWS * COLUMNS);
        if (previousScroll != menu.scroll()) {
            previousScroll = menu.scroll();
            visibleAmounts.clear();
        }
        if (visibleCount == 0 && end > start) {
            viewChangedAt = now;
        }
        visibleCount = end - start;
        stockFlashes.values().removeIf(flash -> progress(now, flash.started(), 420) >= 1);
        incomingAmounts.clear();
        for (int i = start; i < end; i++) {
            KleinEntryData entry = entries.get(i);
            BigInteger before = visibleAmounts.get(entry.id());
            if (before != null) {
                int change = entry.amount().compareTo(before);
                if (change != 0) stockFlashes.put(entry.id(), new StockFlash(now, change > 0 ? TEAL : AMBER));
            }
            incomingAmounts.put(entry.id(), entry.amount());
        }
        Map<Long, BigInteger> previousAmounts = visibleAmounts;
        visibleAmounts = incomingAmounts;
        incomingAmounts = previousAmounts;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        observeStock();
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        KleinEntryData entry = entryAt(mouseX, mouseY);
        if (entry != null && menu.getCarried().isEmpty()) {
            g.renderTooltip(font, List.of(entry.template().getHoverName(),
                    label("total", entry.amount().toString())), entry.template().getTooltipImage(),
                    entry.template(), mouseX, mouseY);
            return;
        }
        Control hover = controlAt(mouseX, mouseY);
        if (hover != null) g.renderTooltip(font, controlTip(hover), mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.drawManaged(() -> {
            card(g, leftPos, topPos, MAIN_WIDTH, HEIGHT, PAPER);
            card(g, leftPos + DOCK, topPos, DOCK_WIDTH, HEIGHT, 0xFFEAF6F2);
            drawHeader(g);
            drawWarehouse(g, mouseX, mouseY);
            drawInventory(g);
            drawTools(g);
        });
    }

    private void drawHeader(GuiGraphics g) {
        int x = leftPos, y = topPos;
        float arrival = easeOut(progress(now, openedAt, 480));
        g.fillGradient(x + 1, y + 3, x + MAIN_WIDTH - 1, y + 21, 0xFFBFECE1, 0xFFF3FBF6);
        g.fill(x + 2, y + 1, x + 2 + (int) ((MAIN_WIDTH - 4) * arrival), y + 3, TEAL);
        int bob = Math.round(2 * (1f - arrival));
        g.renderItem(bottleIcon, x + 7, y + 5 - bob);
        g.drawString(font, Component.translatable("item.universal_tool.klein_bottle"), x + 27, y + 9, INK, false);

        String count = menu.filtered().size() + " / \u221e";
        int countWidth = font.width(count);
        int countX = x + MAIN_WIDTH - 11 - countWidth;
        g.fill(countX - 4, y + 6, x + MAIN_WIDTH - 7, y + 19, MINT);
        g.drawString(font, count, countX, y + 9, TEAL, false);

        int sx = x + SEARCH_X, sy = y + SEARCH_Y;
        g.fill(sx, sy, sx + SEARCH_W, sy + SEARCH_H, WHITE);
        frame(g, sx, sy, SEARCH_W, SEARCH_H, BORDER);
        frame(g, sx + 4, sy + 3, 5, 5, TEAL);
        g.fill(sx + 8, sy + 8, sx + 10, sy + 10, TEAL);
        int underline = Math.round(smooth(searchFocus) * (SEARCH_W - 2));
        g.fill(sx + 1, sy + SEARCH_H - 1, sx + 1 + underline, sy + SEARCH_H, TEAL);
        if (!search.getValue().isEmpty()) {
            drawControl(g, Control.CLEAR, MINT, TEAL, false);
            cross(g, x + 103, y + 29, TEAL);
        }

        drawControl(g, Control.SORT, MINT, TEAL, false);
        drawCenter(g, sortLabel(), x + 138, y + 27, INK);
        drawControl(g, Control.DIRECTION, MINT, TEAL, false);
        arrow(g, x + 173, y + 30, menu.descending() ? 1 : -1, TEAL);
    }

    private void drawWarehouse(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + KleinMenu.GRID_X, y = topPos + KleinMenu.GRID_Y;
        g.drawString(font, label("warehouse"), x, y - 10, SECONDARY, false);
        g.fill(x - 2, y - 2, x + COLUMNS * CELL + 14, y + GRID_HEIGHT + 1, RECESS);
        int start = menu.scroll() * COLUMNS;
        int hoverCell = cellAt(mouseX, mouseY);
        List<KleinEntryData> entries = menu.filtered();
        for (int cell = 0; cell < KleinMenu.VISIBLE_ROWS * COLUMNS; cell++) {
            int sx = x + cell % COLUMNS * CELL, sy = y + cell / COLUMNS * CELL;
            socket(g, sx, sy, false);
            if (cell == hoverCell) {
                g.fill(sx + 1, sy + 1, sx + 16, sy + 16, MINT);
                frame(g, sx, sy, 17, 17, TEAL);
            }
            if (start + cell >= entries.size()) continue;
            KleinEntryData entry = entries.get(start + cell);
            StockFlash flash = stockFlashes.get(entry.id());
            if (flash != null) {
                float strength = 1 - smooth(progress(now, flash.started(), 420));
                g.fill(sx + 1, sy + 1, sx + 16, sy + 16, opacity(flash.color(), strength * 0.27f));
                frame(g, sx, sy, 17, 17, opacity(flash.color(), strength));
            }
            g.renderItem(entry.template(), sx + 1, sy - (cell == hoverCell ? 1 : 0));
            drawAmount(g, compact(entry.amount()), sx, sy);
        }

        if (entries.isEmpty()) {
            Component message = label(search.getValue().isBlank() ? "empty" : "no_results");
            int cx = x + COLUMNS * CELL / 2, py = y + GRID_HEIGHT / 2 - 8;
            int half = font.width(message) / 2 + 7;
            card(g, cx - half, py, half * 2, 16, PAPER);
            drawCenter(g, message, cx, py + 4, INK);
        }

        float scan = progress(now, depositPressedAt, 330);
        if (scan < 1) drawScan(g, x, y, scan, 0.5f);
        float view = progress(now, viewChangedAt, 210);
        if (view < 1 && !entries.isEmpty()) drawScan(g, x, y, view, 0.25f);

        int track = leftPos + SCROLL_X;
        g.fill(track + 2, y + 1, track + 5, y + GRID_HEIGHT - 1, BORDER);
        if (menu.maxScroll() > 0) {
            int ty = y + Math.round(scrollThumb);
            int h = thumbHeight();
            g.fill(track, ty, track + 7, ty + h, TEAL);
            g.fill(track + 2, ty + 2, track + 5, ty + h - 2, MINT);
        }
    }

    private static void drawScan(GuiGraphics g, int x, int y, float progress, float opacity) {
        int w = COLUMNS * CELL - 1;
        int py = y + (int) ((GRID_HEIGHT - 1) * easeOut(progress));
        g.fill(x, py, x + w, py + 1, opacity(TEAL, opacity * (1 - progress)));
    }

    private void drawAmount(GuiGraphics g, String amount, int x, int y) {
        int textWidth = font.width(amount);
        float scale = Math.min(0.72f, 14f / textWidth);
        int w = (int) Math.ceil(textWidth * scale);
        g.pose().pushPose();
        g.pose().translate(x + 16 - w, y + 10, 210);
        g.fill(-1, -1, w, 7, 0xF0102934);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, amount, 0, 0, WHITE, false);
        g.pose().popPose();
    }

    private void drawInventory(GuiGraphics g) {
        int x = leftPos + KleinMenu.PLAYER_X - 1;
        int y = topPos + KleinMenu.PLAYER_Y - 1;
        g.fill(x - 2, y - 3, x + COLUMNS * CELL + 1, y + 54, RECESS);
        g.fill(x - 2, topPos + KleinMenu.HOTBAR_Y - 3, x + COLUMNS * CELL + 1, topPos + HEIGHT - 3, RECESS);
        g.drawString(font, label("inventory"), x, y - 12, INK, false);
        for (Slot slot : menu.slots) {
            if (isDrawerSlot(slot)) break;
            socket(g, leftPos + slot.x - 1, topPos + slot.y - 1, menu.isStorageLocked(slot));
        }
    }

    private void drawTools(GuiGraphics g) {
        int x = leftPos + DOCK, y = topPos;
        g.drawString(font, label("tools"), x + 7, y + 8, INK, false);
        drawControl(g, Control.CLOSE, GOLD, AMBER, false);
        cross(g, x + 101, y + 10, INK);

        float deposit = 1 - progress(now, depositPressedAt, 330);
        drawControl(g, Control.DEPOSIT, MINT, TEAL, deposit > 0.6f);
        arrow(g, x + 16, y + 30, 1, TEAL);
        g.fill(x + 12, y + 36, x + 21, y + 37, TEAL);
        g.drawString(font, label("deposit_button"), x + 26, y + 28, INK, false);

        drawControl(g, Control.AUTO, MINT, TEAL, menu.autoDeposit());
        g.drawString(font, label("pickup_button"), x + 12, y + 49, INK, false);
        int switchX = x + 83;
        int track = FastColor.ARGB32.lerp(smooth(autoSwitch), 0xFF66858D, TEAL);
        g.fill(switchX, y + 49, switchX + 19, y + 58, track);
        int thumbX = switchX + 2 + Math.round(9 * smooth(autoSwitch));
        g.fill(thumbX, y + 50, thumbX + 6, y + 57, WHITE);

        drawToolTab(g, Control.CRAFT, craftIcon, "craft_short", KleinMenu.DRAWER_CRAFTING, TEAL, MINT);
        drawToolTab(g, Control.SMELT, smeltIcon, "smelt_short", KleinMenu.DRAWER_SMELTING, AMBER, GOLD);
        drawToolTab(g, Control.ANVIL, anvilIcon, "anvil_short", KleinMenu.DRAWER_ANVIL, VIOLET, LILAC);
        drawDrawer(g);
        Component hint = label("transfer_hint");
        drawCenter(g, hint, x + DOCK_WIDTH / 2, y + 221, SECONDARY);
    }

    private void drawToolTab(GuiGraphics g, Control control, ItemStack icon, String key, int mode, int ink, int tint) {
        boolean active = workspaceMode == mode;
        drawControl(g, control, tint, ink, active);
        int center = leftPos + control.x + control.w / 2;
        int y = topPos + control.y;
        g.pose().pushPose();
        g.pose().translate(center - 5, y + 2 - Math.round(hoveredControls[control.ordinal()]), 0);
        g.pose().scale(0.65f, 0.65f, 1);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
        drawCenter(g, label(key), center, y + 15, active ? ink : INK);
    }

    private void drawDrawer(GuiGraphics g) {
        int x = leftPos + DOCK, y = drawerY();
        int ink = workspaceMode == KleinMenu.DRAWER_SMELTING ? AMBER
                : workspaceMode == KleinMenu.DRAWER_ANVIL ? VIOLET : TEAL;
        int tint = workspaceMode == KleinMenu.DRAWER_SMELTING ? GOLD
                : workspaceMode == KleinMenu.DRAWER_ANVIL ? LILAC : MINT;
        card(g, x + 1, y, DOCK_WIDTH - 2, KleinMenu.DRAWER_HEIGHT, PAPER);
        g.fillGradient(x + 2, y + 2, x + DOCK_WIDTH - 2, y + 23, tint, PAPER);
        int ribbon = Math.round((DOCK_WIDTH - 12) * easeOut(progress(now, drawerChangedAt, 300)));
        g.fill(x + 6, y + 20, x + 6 + ribbon, y + 21, ink);

        if (workspaceMode == KleinMenu.DRAWER_NONE) {
            drawCenter(g, label("workspace"), x + DOCK_WIDTH / 2, y + 8, TEAL);
            g.renderItem(bottleIcon, x + DOCK_WIDTH / 2 - 8, y + 41);
            drawCenter(g, label("choose_tool"), x + DOCK_WIDTH / 2, y + 67, SECONDARY);
            return;
        }

        String title = switch (workspaceMode) {
            case KleinMenu.DRAWER_CRAFTING -> "tab_crafting";
            case KleinMenu.DRAWER_SMELTING -> "tab_smelting";
            default -> "tab_anvil";
        };
        drawCenter(g, label(title), x + DOCK_WIDTH / 2, y + 8, ink);
        y += drawerSlide;
        for (Slot slot : menu.slots) {
            if (isDrawerSlot(slot) && slot.isActive()) socket(g, leftPos + slot.x - 1, topPos + slot.y - 1 + drawerSlide, false);
        }

        int result = switch (workspaceMode) {
            case KleinMenu.DRAWER_CRAFTING -> KleinMenu.CRAFT_RESULT_SLOT;
            case KleinMenu.DRAWER_SMELTING -> KleinMenu.SMELT_OUTPUT_SLOT;
            default -> KleinMenu.ANVIL_RESULT_SLOT;
        };
        Slot output = menu.slots.get(result);
        boolean ready = output.hasItem();
        if (ready) {
            int pulse = (int) (2 + 2 * (1 + Math.sin(now / 160.0)));
            frame(g, leftPos + output.x - 2, topPos + output.y + drawerSlide - 2, 19, 19,
                    opacity(ink, 0.45f + pulse * 0.08f));
        }

        if (workspaceMode == KleinMenu.DRAWER_SMELTING) {
            int cx = x + KleinMenu.SMELT_X + 8;
            arrow(g, cx, y + 60, 1, ink);
            if (ready) {
                for (int i = 0; i < 3; i++) {
                    int rise = (int) ((now / 55 + i * 4) % 12);
                    int px = cx + (i - 1) * 5;
                    g.fill(px, y + 68 - rise, px + 2, y + 70 - rise, opacity(ink, 1 - rise / 14f));
                }
            }
        } else {
            int arrowX = workspaceMode == KleinMenu.DRAWER_CRAFTING ? 74 : 77;
            int travel = ready ? (int) ((now / 95) % 3) : 0;
            horizontalArrow(g, x + arrowX + travel, y + KleinMenu.DRAWER_RESULT_Y + 8, ink);
            if (workspaceMode == KleinMenu.DRAWER_ANVIL) {
                g.fill(x + 33, y + 56, x + 40, y + 57, ink);
                g.fill(x + 36, y + 53, x + 37, y + 60, ink);
                g.fill(x + 8, y + 25, x + 108, y + 40, WHITE);
                frame(g, x + 8, y + 25, 100, 15, anvilName.isFocused() ? VIOLET : BORDER);
                if (menu.anvilCost() > 0) {
                    boolean affordable = minecraft != null && minecraft.player != null
                            && (minecraft.player.getAbilities().instabuild || minecraft.player.experienceLevel >= menu.anvilCost());
                    Component cost = label("levels", menu.anvilCost());
                    g.fill(x + 8, y + 77, x + 108, y + 92, affordable ? MINT : GOLD);
                    drawCenter(g, cost, x + DOCK_WIDTH / 2, y + 81, affordable ? TEAL : AMBER);
                }
            }
        }
    }

    @Override protected void renderSlot(GuiGraphics g, Slot slot) {
        if (isDrawerSlot(slot)) {
            g.pose().pushPose();
            g.pose().translate(0, drawerSlide, 0);
            super.renderSlot(g, slot);
            g.pose().popPose();
        } else {
            super.renderSlot(g, slot);
        }
        if (menu.isStorageLocked(slot)) {
            g.pose().pushPose();
            g.pose().translate(slot.x + 8, slot.y + 8, 205);
            g.fill(0, 2, 8, 8, VIOLET);
            g.fill(2, 0, 6, 3, VIOLET);
            g.fill(3, 1, 5, 3, LILAC);
            g.fill(3, 4, 5, 6, WHITE);
            g.pose().popPose();
        }
    }

    @Override protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (!slot.isHighlightable()) return;
        int offset = isDrawerSlot(slot) ? drawerSlide : 0;
        frame(g, slot.x - 1, slot.y - 1 + offset, 17, 17, menu.isStorageLocked(slot) ? VIOLET : TEAL);
    }

    @Override protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        return super.isHovering(x, y + (x >= DOCK ? drawerSlide : 0), w, h, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

    private static boolean isDrawerSlot(Slot slot) {
        return slot.x >= DOCK;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) return true;
        Control control = controlAt(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && control != null) {
            handleControl(control);
            return capturePress(button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (anvilName.isVisible() && anvilName.isMouseOver(mouseX, mouseY)) {
                anvilName.setValue("");
                focus(anvilName);
                return capturePress(button);
            }
            if (insideSearch(mouseX, mouseY)) {
                search.setValue("");
                return capturePress(button);
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideSearch(mouseX, mouseY)) {
            search.mouseClicked(mouseX, mouseY, button);
            focus(search);
            return capturePress(button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && anvilName.isVisible() && anvilName.isMouseOver(mouseX, mouseY)) {
            anvilName.mouseClicked(mouseX, mouseY, button);
            focus(anvilName);
            return capturePress(button);
        }
        if (getFocused() == search || getFocused() == anvilName) setFocused(null);
        if (cellAt(mouseX, mouseY) >= 0) {
            if (button >= GLFW.GLFW_MOUSE_BUTTON_LEFT && button <= GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                menu.virtualClick(entryAt(mouseX, mouseY), button, hasShiftDown());
            }
            return capturePress(button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inScrollTrack(mouseX, mouseY) && menu.maxScroll() > 0) {
            draggingScrollbar = true;
            double relative = mouseY - topPos - KleinMenu.GRID_Y - scrollThumb;
            grabOffset = relative >= 0 && relative < thumbHeight() ? relative : thumbHeight() / 2.0;
            scrollTo(mouseY);
            return capturePress(button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleControl(Control control) {
        if (control != Control.CLEAR && (getFocused() == search || getFocused() == anvilName)) setFocused(null);
        switch (control) {
            case CLEAR -> {
                search.setValue("");
                focus(search);
            }
            case SORT -> changeView(search.getValue(), (menu.sortMode() + 1) % 3, menu.descending());
            case DIRECTION -> changeView(search.getValue(), menu.sortMode(), !menu.descending());
            case CLOSE -> onClose();
            case DEPOSIT -> {
                depositPressedAt = Util.getMillis();
                menu.sendDepositMatching();
            }
            case AUTO -> {
                boolean enabled = !menu.autoDeposit();
                menu.setAutoDeposit(enabled);
                PacketDistributor.sendToServer(new KleinAutoDepositPayload(enabled));
            }
            case CRAFT -> selectDrawer(KleinMenu.DRAWER_CRAFTING);
            case SMELT -> selectDrawer(KleinMenu.DRAWER_SMELTING);
            case ANVIL -> selectDrawer(KleinMenu.DRAWER_ANVIL);
        }
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                setFocused(null);
            } else {
                box.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void focus(EditBox box) {
        setFocused(box);
        box.setFocused(true);
    }

    private boolean capturePress(int button) {
        capturedButton = button;
        return true;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == capturedButton) {
            capturedButton = -1;
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollTo(mouseY);
            return true;
        }
        if (button == capturedButton) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (cellAt(mouseX, mouseY) < 0 && !inScrollTrack(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (scrollY == 0) return true;
        KleinEntryData entry = entryAt(mouseX, mouseY);
        if (hasShiftDown() && entry != null) {
            menu.virtualScroll(entry, scrollY > 0);
        } else {
            if (Math.signum(scrollY) != Math.signum(wheelRemainder)) wheelRemainder = 0;
            wheelRemainder += scrollY;
            int rows = (int) wheelRemainder;
            menu.scroll(menu.scroll() - rows);
            wheelRemainder -= rows;
        }
        return true;
    }

    private int thumbHeight() {
        int totalRows = Math.max(1, (menu.filtered().size() + COLUMNS - 1) / COLUMNS);
        return Math.clamp(Math.round(KleinMenu.VISIBLE_ROWS * (float) GRID_HEIGHT / totalRows), 14, GRID_HEIGHT);
    }

    private float targetThumb() {
        return menu.maxScroll() == 0 ? 0 : (GRID_HEIGHT - thumbHeight()) * (float) menu.scroll() / menu.maxScroll();
    }

    private void scrollTo(double screenY) {
        if (menu.maxScroll() == 0) return;
        double traveled = screenY - topPos - KleinMenu.GRID_Y - grabOffset;
        double ratio = Math.clamp(traveled / (GRID_HEIGHT - thumbHeight()), 0, 1);
        menu.scroll((int) Math.round(ratio * menu.maxScroll()));
        scrollThumb = targetThumb();
    }

    private int cellAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - KleinMenu.GRID_X;
        double y = mouseY - topPos - KleinMenu.GRID_Y;
        if (x < 0 || x >= COLUMNS * CELL || y < 0 || y >= GRID_HEIGHT) return -1;
        return (int) (y / CELL) * COLUMNS + (int) (x / CELL);
    }

    private KleinEntryData entryAt(double mouseX, double mouseY) {
        int cell = cellAt(mouseX, mouseY);
        int index = menu.scroll() * COLUMNS + cell;
        return cell < 0 || index >= menu.filtered().size() ? null : menu.filtered().get(index);
    }

    private boolean insideSearch(double mouseX, double mouseY) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        return x >= SEARCH_X && x < SEARCH_X + SEARCH_W && y >= SEARCH_Y && y < SEARCH_Y + SEARCH_H;
    }

    private boolean inScrollTrack(double mouseX, double mouseY) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        return x >= SCROLL_X - 1 && x < SCROLL_X + 8 && y >= KleinMenu.GRID_Y && y < KleinMenu.GRID_Y + GRID_HEIGHT;
    }

    private Control controlAt(double screenX, double screenY) {
        for (Control control : CONTROLS) {
            if (control == Control.CLEAR && (search == null || search.getValue().isEmpty())) continue;
            if (control.contains(screenX - leftPos, screenY - topPos)) return control;
        }
        return null;
    }

    private void drawControl(GuiGraphics g, Control control, int tint, int ink, boolean active) {
        button(g, leftPos + control.x, topPos + control.y, control.w, control.h,
                smooth(hoveredControls[control.ordinal()]), tint, ink, active);
    }

    private Component sortLabel() {
        return label(switch (menu.sortMode()) {
            case KleinMenu.SORT_NAME -> "name";
            case KleinMenu.SORT_MOD -> "sort_mod";
            default -> "quantity";
        });
    }

    private Component controlTip(Control control) {
        return switch (control) {
            case CLEAR -> label("clear_search");
            case SORT -> label("change_sort");
            case DIRECTION -> label(menu.descending() ? "sort_descending" : "sort_ascending");
            case CLOSE -> Component.translatable("gui.done");
            case DEPOSIT -> label("quick_deposit");
            case AUTO -> label(menu.autoDeposit() ? "auto_deposit.on" : "auto_deposit.off");
            case CRAFT -> label("tab_crafting");
            case SMELT -> label("tab_smelting");
            case ANVIL -> label("tab_anvil");
        };
    }

    private static MutableComponent label(String key, Object... args) {
        return Component.translatable("gui.universal_tool.klein." + key, args);
    }

    private void drawCenter(GuiGraphics g, Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private static void cross(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 4; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + i, y + 3 - i, x + i + 1, y + 4 - i, color);
        }
    }

    private static void arrow(GuiGraphics g, int x, int y, int direction, int color) {
        g.fill(x, y - 3, x + 1, y + 4, color);
        g.fill(x - 2, y + direction, x + 3, y + direction + 1, color);
        g.fill(x - 1, y + 2 * direction, x + 2, y + 2 * direction + 1, color);
    }

    private static void horizontalArrow(GuiGraphics g, int x, int y, int color) {
        g.fill(x - 5, y, x + 5, y + 1, color);
        g.fill(x + 2, y - 2, x + 3, y + 3, color);
        g.fill(x + 3, y - 1, x + 4, y + 2, color);
    }

    private static float move(float value, boolean towardOne, float step) {
        return Math.clamp(value + (towardOne ? step : -step), 0f, 1f);
    }

    private static float progress(long time, long start, int duration) {
        return start == 0 ? 1 : Math.clamp((time - start) / (float) duration, 0f, 1f);
    }

    private static String compact(BigInteger amount) {
        if (amount.compareTo(BigInteger.valueOf(10000)) < 0) return amount.toString();
        int exponent = amount.toString().length() - 1;
        BigDecimal value = new BigDecimal(amount).movePointLeft(exponent).setScale(1, RoundingMode.DOWN);
        String suffix = exponent < 6 ? "K" : exponent < 9 ? "M" : exponent < 12 ? "B" : "e" + exponent;
        return value.stripTrailingZeros().toPlainString() + suffix;
    }
}
