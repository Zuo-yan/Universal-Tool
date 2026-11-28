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
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.klein.KleinAutoDepositPayload;
import org.gwfx.universaltool.klein.KleinDrawerPayload;
import org.gwfx.universaltool.klein.KleinSortPayload;
import org.gwfx.universaltool.klein.KleinEntryData;
import org.gwfx.universaltool.klein.KleinMenu;
import org.gwfx.universaltool.klein.KleinRenamePayload;
import org.lwjgl.glfw.GLFW;

/**
 * 「克莱因超维星舱」人因工程单核主仓 + 按需滑出式全息工作翼界面。
 *
 * <p>严格遵循单一职责与零重复原则：
 * <ul>
 *   <li>平时为居中紧凑单核终端，视线 100% 聚焦仓储与随身背包，右侧通透无任何冗余占位；</li>
 *   <li>顶栏右侧提供全屏唯一的「合成 / 烧炼 / 铁砧」图标切换钮，激活时右侧丝滑滑出全息工作翼（JEI 自动避让），主仓全程纹丝不动；</li>
 *   <li>仓库与背包之间的「传输交互过渡带」提供全屏唯一的「一键入库」与物理拨片式「自动入库」开关，操作动线最短。</li>
 * </ul>
 */
public final class KleinScreen extends AbstractContainerScreen<KleinMenu> {
    private static final int MAIN_W = KleinMenu.MAIN_WIDTH;
    private static final int HEIGHT = KleinMenu.HEIGHT;
    private static final int WING_X = KleinMenu.DRAWER_X;
    private static final int WING_Y = KleinMenu.DRAWER_Y;
    private static final int WING_W = KleinMenu.DRAWER_WIDTH;
    private static final int WING_H = KleinMenu.DRAWER_HEIGHT;

    private static final int COLUMNS = 9;
    private static final int COLUMN_STEP = KleinMenu.SLOT_COLUMN_STEP;
    private static final int ROW_STEP = KleinMenu.SLOT_ROW_STEP;
    private static final int GRID_WIDTH = COLUMNS * COLUMN_STEP;
    private static final int GRID_HEIGHT = KleinMenu.VISIBLE_ROWS * ROW_STEP;
    private static final int SCROLL_X = KleinMenu.GRID_X + GRID_WIDTH + 2;
    // Keep the main panel above the screen's bottom controls at large GUI scales.
    private static final int FOOTER_CLEARANCE = 24;

    private static final int SEARCH_X = 8;
    private static final int SEARCH_Y = 19;
    private static final int SORT_X = MAIN_W - 79;
    private static final int SEARCH_W = SORT_X - SEARCH_X - 3;
    private static final int SEARCH_H = 13;
    private static final int CATEGORY_Y = 33;
    private static final int TRANSFER_Y = 132;
    private static final int AUTO_W = 68;
    private static final int AUTO_X = MAIN_W - 8 - AUTO_W;

    private enum Control {
        // 顶栏（右侧唯一工作翼切换组 + 关闭）
        STAT_BADGE(MAIN_W - 112, 5, 26, 13),
        TOOL_CRAFT(MAIN_W - 82, 3, 18, 15),
        TOOL_SMELT(MAIN_W - 62, 3, 18, 15),
        TOOL_ANVIL(MAIN_W - 42, 3, 18, 15),
        CLOSE(MAIN_W - 20, 4, 13, 13),

        // 搜索与排序行
        CLEAR(SEARCH_X + SEARCH_W - 12, SEARCH_Y + 1, 11, 11),
        SORT(SORT_X, SEARCH_Y, 48, SEARCH_H),
        DIRECTION(MAIN_W - 28, SEARCH_Y, 20, SEARCH_H),

        // 4 维智能分类药丸
        CAT_ALL(8, CATEGORY_Y, 41, 11),
        CAT_BLOCKS(52, CATEGORY_Y, 41, 11),
        CAT_GEAR(96, CATEGORY_Y, 41, 11),
        CAT_OTHER(140, CATEGORY_Y, 42, 11),

        // 仓库与背包交界传输带（全屏唯一入库 & 自动开关）
        DEPOSIT(AUTO_X - 57, TRANSFER_Y, 53, 13),
        AUTO(AUTO_X, TRANSFER_Y, AUTO_W, 13),

        // 折叠工作翼右上角收起键
        WING_COLLAPSE(WING_X + WING_W - 17, WING_Y + 4, 12, 12);

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
    private long autoPressedAt;

    private float searchFocus;
    private float autoSwitch;
    private float scrollThumb;
    private float wingOpen; // 0 = 收起，1 = 展开
    private int wingShiftX;
    private int workspaceMode;
    private int lastVisibleWingMode = KleinMenu.DRAWER_CRAFTING;
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
        // imageWidth 仅锚定主舱，确保主舱永远居中且开关工作翼时画面零跳动
        imageWidth = MAIN_W;
        imageHeight = HEIGHT;
        workspaceMode = menu.drawerMode();
        if (workspaceMode != KleinMenu.DRAWER_NONE) {
            lastVisibleWingMode = workspaceMode;
            wingOpen = 1f;
        }
        autoSwitch = menu.autoDeposit() ? 1f : 0f;
    }

    /** 供 JEI IGuiContainerHandler 动态读取展开中的全息侧翼区域，实现智能避让。 */
    public List<Rect2i> getJeiExtraAreas() {
        if (wingOpen <= 0.05f && menu.drawerMode() == KleinMenu.DRAWER_NONE) {
            return List.of();
        }
        int visibleW = Math.max(1, Math.round(WING_W * easeOut(wingOpen)));
        return List.of(new Rect2i(leftPos + MAIN_W, topPos + WING_Y, visibleW + 6, WING_H));
    }

    @Override protected void init() {
        super.init();
        topPos = Math.max(0, Math.min(topPos, height - imageHeight - FOOTER_CLEARANCE));
        String priorQuery = search == null ? "" : search.getValue();
        search = new EditBox(font, leftPos + SEARCH_X + 13, topPos + SEARCH_Y + 3, SEARCH_W - 25, 9, label("search"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(TEXT_BRIGHT);
        search.setHint(label("search").withStyle(s -> s.withColor(TEXT_MUTED)));
        search.setValue(priorQuery);
        search.setResponder(value -> changeView(value, menu.sortMode(), menu.descending()));
        addRenderableWidget(search);

        String priorName = anvilName == null ? menu.anvilItemName() : anvilName.getValue();
        anvilName = new EditBox(font, leftPos + WING_X + 11, topPos + WING_Y + 25, WING_W - 22, 10, label("anvil_name_hint"));
        anvilName.setBordered(false);
        anvilName.setMaxLength(50);
        anvilName.setTextColor(TEXT_BRIGHT);
        anvilName.setHint(label("anvil_name_hint").withStyle(s -> s.withColor(TEXT_MUTED)));
        anvilName.setValue(priorName);
        anvilName.setResponder(this::renameAnvilItem);
        anvilName.setVisible(menu.drawerMode() == KleinMenu.DRAWER_ANVIL);
        addRenderableWidget(anvilName);
        scrollThumb = targetThumb();
    }

    private void renameAnvilItem(String name) {
        menu.setAnvilItemName(name);
        PacketDistributor.sendToServer(new KleinRenamePayload(name));
    }

    @Override protected void containerTick() {
        if (menu.drawerMode() != KleinMenu.DRAWER_ANVIL) return;
        ItemStack input = menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).getItem();
        if (ItemStack.matches(previousAnvilInput, input)) return;
        previousAnvilInput = input.copy();
        if (!anvilName.isFocused()) anvilName.setValue(input.isEmpty() ? "" : input.getHoverName().getString());
    }

    private void changeView(String query, int sortMode, boolean descending) {
        if (sortMode != menu.sortMode() || descending != menu.descending()) {
            PacketDistributor.sendToServer(new KleinSortPayload(menu.containerId, sortMode, descending));
        }
        menu.setView(query, sortMode, descending);
        viewChangedAt = Util.getMillis();
        visibleAmounts.clear();
        stockFlashes.clear();
    }

    private void selectCategory(int category) {
        if (menu.category() == category) return;
        menu.setCategory(category);
        viewChangedAt = Util.getMillis();
        visibleAmounts.clear();
        stockFlashes.clear();
    }

    private void toggleWing(int mode) {
        int next = menu.drawerMode() == mode ? KleinMenu.DRAWER_NONE : mode;
        menu.setDrawerMode(next);
        PacketDistributor.sendToServer(new KleinDrawerPayload(menu.containerId, next));
        updateWingState(next, Util.getMillis());
    }

    private void updateWingState(int mode, long time) {
        if (mode != workspaceMode) {
            workspaceMode = mode;
            if (mode != KleinMenu.DRAWER_NONE) {
                lastVisibleWingMode = mode;
            }
            drawerChangedAt = time;
            if (mode != KleinMenu.DRAWER_ANVIL && getFocused() == anvilName) setFocused(null);
            if (mode == KleinMenu.DRAWER_NONE && lastVisibleWingMode == KleinMenu.DRAWER_ANVIL) {
                previousAnvilInput = ItemStack.EMPTY;
                anvilName.setResponder(null);
                anvilName.setValue("");
                anvilName.setResponder(this::renameAnvilItem);
            }
            anvilName.setVisible(mode == KleinMenu.DRAWER_ANVIL && wingOpen > 0.7f);
        }
        wingShiftX = Math.round(-(1f - easeOut(wingOpen)) * 18f);
        boolean showAnvilBox = mode == KleinMenu.DRAWER_ANVIL && wingOpen > 0.7f;
        anvilName.setVisible(showAnvilBox);
        anvilName.setPosition(leftPos + WING_X + 11 + wingShiftX, topPos + WING_Y + 25);
    }

    private int wingAccent(int mode) {
        return switch (mode) {
            case KleinMenu.DRAWER_SMELTING -> AMBER;
            case KleinMenu.DRAWER_ANVIL -> VIOLET;
            default -> CYAN;
        };
    }

    private int wingGlow(int mode) {
        return switch (mode) {
            case KleinMenu.DRAWER_SMELTING -> AMBER_GLOW;
            case KleinMenu.DRAWER_ANVIL -> VIOLET_GLOW;
            default -> CYAN_GLOW;
        };
    }

    private void animate(int mouseX, int mouseY) {
        long time = Util.getMillis();
        float step = Math.clamp((time - now) / 110f, 0f, 1f);
        now = time;
        Control hover = controlAt(mouseX, mouseY);
        for (Control control : CONTROLS) {
            int idx = control.ordinal();
            hoveredControls[idx] = move(hoveredControls[idx], hover == control, step);
        }
        searchFocus = move(searchFocus, search.isFocused(), step);
        autoSwitch = move(autoSwitch, menu.autoDeposit(), step);
        wingOpen = move(wingOpen, menu.drawerMode() != KleinMenu.DRAWER_NONE, Math.clamp(step * 1.15f, 0f, 1f));
        float target = targetThumb();
        scrollThumb = draggingScrollbar ? target : scrollThumb + (target - scrollThumb) * Math.min(1f, step * 2.2f);
        updateWingState(menu.drawerMode(), time);
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
                if (change != 0) stockFlashes.put(entry.id(), new StockFlash(now, change > 0 ? EMERALD : AMBER));
            }
            incomingAmounts.put(entry.id(), entry.amount());
        }
        Map<Long, BigInteger> prev = visibleAmounts;
        visibleAmounts = incomingAmounts;
        incomingAmounts = prev;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        animate(mouseX, mouseY);
        observeStock();
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        KleinEntryData entry = entryAt(mouseX, mouseY);
        if (entry != null && menu.getCarried().isEmpty()) {
            g.renderTooltip(font, List.of(
                    entry.template().getHoverName(),
                    label("total", entry.amount().toString()).withStyle(s -> s.withColor(CYAN_GLOW)),
                    label("op_clicks").withStyle(s -> s.withColor(TEXT_MUTED)),
                    label("transfer_hint").withStyle(s -> s.withColor(TEXT_MUTED))),
                    entry.template().getTooltipImage(), entry.template(), mouseX, mouseY);
            return;
        }
        Control hover = controlAt(mouseX, mouseY);
        if (hover != null) {
            if (hover == Control.STAT_BADGE) {
                g.renderTooltip(font, List.of(
                        label("types", menu.totalStoredTypes()).withStyle(s -> s.withColor(CYAN_GLOW)),
                        Component.literal("§7" + label("stat_items").getString() + "：§f" + menu.totalStoredItems()),
                        label("op_clicks").withStyle(s -> s.withColor(TEXT_MUTED)),
                        label("transfer_hint").withStyle(s -> s.withColor(TEXT_MUTED))),
                        java.util.Optional.empty(), mouseX, mouseY);
            } else {
                g.renderTooltip(font, controlTip(hover), mouseX, mouseY);
            }
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float timeSec = (now % 3600000L) / 1000.0f;
        float arrival = progress(now, openedAt, 340);
        int mainAccent = workspaceMode == KleinMenu.DRAWER_NONE ? CYAN : wingAccent(workspaceMode);

        g.drawManaged(() -> {
            // 1. 若全息工作翼处于展开或过渡动画中，先在主舱右侧按裁切宽度丝滑展开
            if (wingOpen > 0.01f) {
                drawCollapsibleWing(g, timeSec);
            }

            // 2. 居中单核主舱（稳如磐石，绝不随工作翼开关跳动）
            obsidianPanel(g, leftPos, topPos, MAIN_W, HEIGHT, mainAccent, arrival);
            drawAmbientStars(g, leftPos + 2, topPos + 2, MAIN_W - 4, HEIGHT - 4, mainAccent, timeSec);
            drawHeader(g, timeSec, arrival);
            drawSearchAndSort(g);
            drawCategoryBar(g, mouseX, mouseY);
            drawWarehouse(g, mouseX, mouseY, timeSec);
            drawTransferBridge(g);
            drawInventory(g);
        });
    }

    private void drawHeader(GuiGraphics g, float timeSec, float arrival) {
        int x = leftPos, y = topPos;
        g.fillGradient(x + 2, y + 2, x + MAIN_W - 2, y + 21, 0xA80F2438, 0x00070C17);

        // 克莱因瓶核心微悬浮图标 + 标题
        int bob = Math.round((float) Math.sin(timeSec * 2.2f) * 1.0f + 2f * (1f - easeOut(arrival)));
        g.pose().pushPose();
        g.pose().translate(x + 6, y + 4 - bob, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.renderItem(bottleIcon, 0, 0);
        g.pose().popPose();

        drawFittedLabel(g, Component.translatable("item.universal_tool.klein_bottle").getString(),
                x + 22, y + 7, Control.STAT_BADGE.x - 25, TEXT_BRIGHT);

        // 种类微标（悬停查看完整藏品总数与操作速查，零占用空间）
        String countStr = menu.filtered().size() + "/\u221e";
        int bx = leftPos + Control.STAT_BADGE.x;
        int by = topPos + Control.STAT_BADGE.y;
        boolean statHover = hoveredControls[Control.STAT_BADGE.ordinal()] > 0.2f;
        well(g, bx, by, Control.STAT_BADGE.w, Control.STAT_BADGE.h, statHover ? CYAN : BORDER_SUBTLE);
        int countWidth = Math.min(font.width(countStr), Control.STAT_BADGE.w - 4);
        drawFittedLabel(g, countStr, bx + (Control.STAT_BADGE.w - countWidth) / 2,
                by + 3, countWidth, CYAN_GLOW);

        // 全屏唯一的 3 枚工作翼快捷图标按钮（点开即右侧滑出，再点收回）
        drawToolIconBtn(g, Control.TOOL_CRAFT, craftIcon, workspaceMode == KleinMenu.DRAWER_CRAFTING, CYAN, timeSec);
        drawToolIconBtn(g, Control.TOOL_SMELT, smeltIcon, workspaceMode == KleinMenu.DRAWER_SMELTING, AMBER, timeSec);
        drawToolIconBtn(g, Control.TOOL_ANVIL, anvilIcon, workspaceMode == KleinMenu.DRAWER_ANVIL, VIOLET, timeSec);

        // 唯一关闭按钮
        drawControl(g, Control.CLOSE, CORAL, false);
        cross(g, leftPos + Control.CLOSE.x + 4, topPos + Control.CLOSE.y + 4,
                hoveredControls[Control.CLOSE.ordinal()] > 0.25f ? TEXT_BRIGHT : TEXT_MUTED);
    }

    private void drawToolIconBtn(GuiGraphics g, Control control, ItemStack icon, boolean active, int accent, float timeSec) {
        drawControl(g, control, accent, active);
        int x = leftPos + control.x;
        int y = topPos + control.y;
        if (active) {
            drawShimmer(g, x, y, control.w, control.h, accent, timeSec * 0.36f);
        }
        g.pose().pushPose();
        g.pose().translate(x + 3.5f, y + 2.0f - hoveredControls[control.ordinal()] * 0.8f, 0);
        g.pose().scale(0.68f, 0.68f, 1f);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
    }

    private void drawSearchAndSort(GuiGraphics g) {
        int sx = leftPos + SEARCH_X, sy = topPos + SEARCH_Y;
        well(g, sx, sy, SEARCH_W, SEARCH_H, search.isFocused() ? CYAN : BORDER_SUBTLE);
        frame(g, sx + 4, sy + 3, 5, 5, CYAN);
        g.fill(sx + 8, sy + 8, sx + 10, sy + 10, CYAN);
        int underline = Math.round(smooth(searchFocus) * (SEARCH_W - 2));
        if (underline > 0) {
            g.fill(sx + 1, sy + SEARCH_H - 1, sx + 1 + underline, sy + SEARCH_H, CYAN_GLOW);
        }
        if (!search.getValue().isEmpty()) {
            drawControl(g, Control.CLEAR, CORAL, false);
            cross(g, leftPos + Control.CLEAR.x + 3, topPos + Control.CLEAR.y + 3, TEXT_BRIGHT);
        }

        drawControl(g, Control.SORT, CYAN, false);
        drawCenter(g, sortLabel(), leftPos + Control.SORT.x + Control.SORT.w / 2, sy + 3, TEXT_MAIN);
        drawControl(g, Control.DIRECTION, CYAN, false);
        arrow(g, leftPos + Control.DIRECTION.x + 10, sy + 6, menu.descending() ? 1 : -1, CYAN_GLOW);
    }

    private void drawCategoryBar(GuiGraphics g, int mouseX, int mouseY) {
        Control[] cats = {Control.CAT_ALL, Control.CAT_BLOCKS, Control.CAT_GEAR, Control.CAT_OTHER};
        String[] keys = {"cat_all", "cat_blocks", "cat_gear", "cat_other"};
        int[] accents = {CYAN, 0xFF66D8FF, AMBER, VIOLET};
        for (int i = 0; i < cats.length; i++) {
            Control c = cats[i];
            int cx = leftPos + c.x;
            int cy = topPos + c.y;
            boolean active = menu.category() == i;
            boolean hover = mouseX >= cx && mouseX < cx + c.w && mouseY >= cy && mouseY < cy + c.h;
            pill(g, font, cx, cy, c.w, c.h, label(keys[i]).getString(), active, hover, accents[i]);
        }
    }

    private void drawWarehouse(GuiGraphics g, int mouseX, int mouseY, float timeSec) {
        int gx = leftPos + KleinMenu.GRID_X;
        int gy = topPos + KleinMenu.GRID_Y;
        well(g, leftPos + 8, gy - 1, MAIN_W - 16, GRID_HEIGHT + 2, BORDER_SUBTLE);

        int start = menu.scroll() * COLUMNS;
        int hoverCell = cellAt(mouseX, mouseY);
        List<KleinEntryData> entries = menu.filtered();
        float pulse = 0.5f + 0.5f * (float) Math.sin(timeSec * 5.0f);

        for (int cell = 0; cell < KleinMenu.VISIBLE_ROWS * COLUMNS; cell++) {
            int sx = gx + cell % COLUMNS * COLUMN_STEP;
            int sy = gy + cell / COLUMNS * ROW_STEP;
            socket(g, sx, sy, false);
            if (cell == hoverCell) {
                g.fill(sx + 1, sy + 1, sx + 16, sy + 16, SLOT_HOVER_BG);
                focusReticle(g, sx, sy, 17, CYAN_GLOW, pulse);
            }
            if (start + cell >= entries.size()) continue;
            KleinEntryData entry = entries.get(start + cell);
            StockFlash flash = stockFlashes.get(entry.id());
            if (flash != null) {
                float strength = 1f - smooth(progress(now, flash.started(), 420));
                g.fill(sx + 1, sy + 1, sx + 16, sy + 16, opacity(flash.color(), strength * 0.34f));
                frame(g, sx, sy, 17, 17, opacity(flash.color(), strength));
            }
            g.renderItem(entry.template(), sx + 1, sy + 1 - (cell == hoverCell ? 1 : 0));
            drawAmount(g, compact(entry.amount()), sx, sy);
        }

        if (entries.isEmpty()) {
            Component msg = label(search.getValue().isBlank() && menu.category() == KleinMenu.CATEGORY_ALL ? "empty" : "no_results");
            int cx = gx + GRID_WIDTH / 2;
            int py = gy + GRID_HEIGHT / 2 - 9;
            int half = font.width(msg) / 2 + 10;
            well(g, cx - half, py, half * 2, 18, opacity(CYAN, 0.5f));
            drawCenter(g, msg, cx, py + 5, TEXT_MAIN);
        }

        float scan = progress(now, depositPressedAt, 380);
        if (scan < 1f) drawScan(g, gx, gy, scan, 0.72f, EMERALD);
        float view = progress(now, viewChangedAt, 220);
        if (view < 1f && !entries.isEmpty()) drawScan(g, gx, gy, view, 0.34f, CYAN);

        // 纤巧霓虹滚动条
        if (menu.maxScroll() > 0) {
            int trackX = leftPos + SCROLL_X;
            g.fill(trackX + 2, gy + 1, trackX + 4, gy + GRID_HEIGHT - 1, BORDER_SUBTLE);
            int ty = gy + Math.round(scrollThumb);
            int h = thumbHeight();
            g.fill(trackX + 1, ty, trackX + 5, ty + h, CYAN);
            g.fill(trackX + 2, ty + 1, trackX + 4, ty + h - 1, CYAN_GLOW);
        }
    }

    /** 仓库与背包交界处的「传输交互过渡带」：全屏唯一的「一键入库」与「自动入库」物理开关。 */
    private void drawTransferBridge(GuiGraphics g) {
        int y = topPos + TRANSFER_Y;
        drawFittedLabel(g, label("inventory").getString(), leftPos + 10, y + 3, Control.DEPOSIT.x - 13, TEXT_MAIN);

        // 一键匹配入库按钮
        boolean depositPulse = progress(now, depositPressedAt, 340) < 0.7f;
        drawControl(g, Control.DEPOSIT, depositPulse ? EMERALD : CYAN, depositPulse);
        int dx = leftPos + Control.DEPOSIT.x;
        int dy = topPos + Control.DEPOSIT.y;
        arrow(g, dx + 7, dy + 6, -1, depositPulse ? TEXT_BRIGHT : CYAN_GLOW);
        drawFittedLabel(g, label("deposit_button").getString(), dx + 13, dy + 3,
                Control.DEPOSIT.w - 16, depositPulse ? TEXT_BRIGHT : TEXT_MAIN);

        drawControl(g, Control.AUTO, CYAN, false);
        int ax = leftPos + Control.AUTO.x;
        int ay = topPos + Control.AUTO.y;
        float flash = 1f - smooth(progress(now, autoPressedAt, 250));
        if (flash > 0f) {
            g.fill(ax + 1, ay + 1, ax + Control.AUTO.w - 1, ay + Control.AUTO.h - 1,
                    opacity(EMERALD, flash * 0.26f));
            frame(g, ax, ay, Control.AUTO.w, Control.AUTO.h, opacity(EMERALD, flash * 0.72f));
        }
        drawFittedLabel(g, label("pickup_button").getString(), ax + 5, ay + 3, Control.AUTO.w - 25, TEXT_MAIN);
        toggleSwitch(g, ax + Control.AUTO.w - 17, ay + 3, autoSwitch);
    }

    private void drawInventory(GuiGraphics g) {
        int wellX = leftPos + KleinMenu.PLAYER_X - 5;
        int wellWidth = GRID_WIDTH + 7;
        well(g, wellX, topPos + KleinMenu.PLAYER_Y - 2, wellWidth, 3 * ROW_STEP + 2, BORDER_SUBTLE);
        well(g, wellX, topPos + KleinMenu.HOTBAR_Y - 2, wellWidth, ROW_STEP + 2, opacity(CYAN, 0.30f));

        for (Slot slot : menu.slots) {
            if (isDrawerSlot(slot)) break;
            socket(g, leftPos + slot.x - 1, topPos + slot.y - 1, menu.isStorageLocked(slot));
        }
    }

    /** 右侧按需滑出的悬浮全息工作翼（关闭时完全消失，不留任何空盒）。 */
    private void drawCollapsibleWing(GuiGraphics g, float timeSec) {
        int mode = workspaceMode != KleinMenu.DRAWER_NONE ? workspaceMode : lastVisibleWingMode;
        int accent = wingAccent(mode);
        int glow = wingGlow(mode);
        float ease = easeOut(wingOpen);
        int clipW = Math.round((WING_W + 6) * ease);
        if (clipW <= 1) return;

        int clipX = leftPos + MAIN_W;
        int wy = topPos + WING_Y;

        // 磁吸能量锁扣（连接主舱右缘与工作翼）
        if (ease > 0.35f) {
            drawConduit(g, leftPos + MAIN_W, leftPos + WING_X + wingShiftX, wy + 28, accent, timeSec);
            drawConduit(g, leftPos + MAIN_W, leftPos + WING_X + wingShiftX, wy + WING_H - 28, accent, timeSec + 0.5f);
        }

        g.enableScissor(clipX, wy - 2, clipX + clipW, wy + WING_H + 4);
        int wx = leftPos + WING_X + wingShiftX;

        obsidianPanel(g, wx, wy, WING_W, WING_H, accent, ease);
        drawAmbientStars(g, wx + 2, wy + 2, WING_W - 4, WING_H - 4, accent, timeSec);
        g.fillGradient(wx + 2, wy + 2, wx + WING_W - 2, wy + 20, opacity(accent, 0.24f), 0x00070C17);

        String titleKey = switch (mode) {
            case KleinMenu.DRAWER_SMELTING -> "tab_smelting";
            case KleinMenu.DRAWER_ANVIL -> "tab_anvil";
            default -> "tab_crafting";
        };
        g.drawString(font, label(titleKey), wx + 8, wy + 6, glow, true);

        // 右上角折叠收回钮 [‹]
        int colX = wx + WING_W - 17;
        int colY = wy + 4;
        boolean colHover = hoveredControls[Control.WING_COLLAPSE.ordinal()] > 0.25f;
        button(g, colX, colY, 12, 12, hoveredControls[Control.WING_COLLAPSE.ordinal()], accent, false);
        g.drawCenteredString(font, "‹", colX + 6, colY + 2, colHover ? TEXT_BRIGHT : TEXT_MAIN);

        g.fill(wx + 7, wy + 19, wx + WING_W - 7, wy + 20, opacity(accent, 0.55f));

        // 绘制当前工作模式对应的真实槽位背景与动画
        if (workspaceMode == mode) {
            for (Slot slot : menu.slots) {
                if (isDrawerSlot(slot) && slot.isActive()) {
                    socket(g, leftPos + slot.x - 1 + wingShiftX, topPos + slot.y - 1, false);
                }
            }
            int resultIdx = switch (mode) {
                case KleinMenu.DRAWER_CRAFTING -> KleinMenu.CRAFT_RESULT_SLOT;
                case KleinMenu.DRAWER_SMELTING -> KleinMenu.SMELT_OUTPUT_SLOT;
                default -> KleinMenu.ANVIL_RESULT_SLOT;
            };
            Slot output = menu.slots.get(resultIdx);
            boolean ready = output.hasItem();
            if (ready) {
                int ox = leftPos + output.x - 2 + wingShiftX;
                int oy = topPos + output.y - 2;
                float p = 0.5f + 0.5f * (float) Math.sin(timeSec * 6.0f);
                frame(g, ox, oy, 19, 19, opacity(glow, 0.55f + 0.45f * p));
                focusReticle(g, ox - 1, oy - 1, 21, accent, p);
            }

            if (mode == KleinMenu.DRAWER_CRAFTING) {
                int travel = ready ? (int) ((now / 90) % 3) : 0;
                horizontalArrow(g, wx + 73 + travel, wy + KleinMenu.DRAWER_RESULT_Y + 8, ready ? glow : accent);
                drawCenter(g, label("jei_ready"), wx + WING_W / 2, wy + WING_H - 16, opacity(CYAN, 0.85f));
            } else if (mode == KleinMenu.DRAWER_SMELTING) {
                int cx = wx + KleinMenu.SMELT_X + 8;
                int ry = wy + 59;
                drawReactorRing(g, cx, ry, 14f, AMBER, AMBER_GLOW, timeSec * (ready ? 2.1f : 0.9f));
                arrow(g, cx, ry, 1, ready ? AMBER_GLOW : AMBER);
                for (int i = 0; i < 4; i++) {
                    int rise = (int) ((now / (ready ? 45 : 95) + i * 5) % 16);
                    int px = cx + (i - 1) * 4 - 2;
                    g.fill(px, wy + 71 - rise, px + 2, wy + 73 - rise,
                            opacity(ready ? AMBER_GLOW : AMBER, (1f - rise / 17f) * (ready ? 0.9f : 0.45f)));
                }
                drawCenter(g, label("smelt_instant"), wx + WING_W / 2, wy + WING_H - 16, AMBER_GLOW);
            } else if (mode == KleinMenu.DRAWER_ANVIL) {
                well(g, wx + 8, wy + 22, WING_W - 16, 14, anvilName.isFocused() ? VIOLET_GLOW : VIOLET);
                g.fill(wx + 31, wy + 56, wx + 39, wy + 57, VIOLET_GLOW);
                g.fill(wx + 34, wy + 53, wx + 35, wy + 60, VIOLET_GLOW);
                int travel = ready ? (int) ((now / 90) % 3) : 0;
                horizontalArrow(g, wx + 73 + travel, wy + KleinMenu.DRAWER_RESULT_Y + 8, ready ? VIOLET_GLOW : VIOLET);
                if (menu.anvilCost() > 0) {
                    boolean affordable = minecraft != null && minecraft.player != null
                            && (minecraft.player.getAbilities().instabuild || minecraft.player.experienceLevel >= menu.anvilCost());
                    int badgeCol = affordable ? EMERALD : CORAL;
                    well(g, wx + 10, wy + 78, WING_W - 20, 15, badgeCol);
                    drawCenter(g, label("levels", menu.anvilCost()), wx + WING_W / 2, wy + 82, badgeCol);
                } else {
                    drawCenter(g, label("anvil_ready"), wx + WING_W / 2, wy + 82, TEXT_MUTED);
                }
            }
        }

        g.disableScissor();
    }

    private static void drawScan(GuiGraphics g, int x, int y, float progress, float alpha, int color) {
        int w = GRID_WIDTH - 1;
        int py = y + (int) ((GRID_HEIGHT - 1) * easeOut(progress));
        g.fill(x, Math.max(y, py - 2), x + w, py + 1, opacity(color, alpha * 0.25f * (1f - progress)));
        g.fill(x, py, x + w, py + 1, opacity(color, alpha * (1f - progress)));
    }

    private void drawFittedLabel(GuiGraphics g, String text, int x, int y, int width, int color) {
        float scale = Math.min(1f, width / (float) Math.max(1, font.width(text)));
        if (scale == 1f) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y + 4 * (1f - scale), 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void drawAmount(GuiGraphics g, String amount, int x, int y) {
        int textWidth = font.width(amount);
        float scale = Math.min(0.70f, 14f / Math.max(1, textWidth));
        int w = (int) Math.ceil(textWidth * scale);
        g.pose().pushPose();
        g.pose().translate(x + 16 - w, y + 10, 210);
        g.fill(-1, -1, w + 1, 7, 0xE6050A14);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, amount, 0, 0, TEXT_BRIGHT, false);
        g.pose().popPose();
    }

    @Override protected void renderSlot(GuiGraphics g, Slot slot) {
        if (isDrawerSlot(slot)) {
            if (wingOpen <= 0.45f) return;
            g.pose().pushPose();
            g.pose().translate(wingShiftX, 0, 0);
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
            g.fill(3, 1, 5, 3, VIOLET_DARK);
            g.fill(3, 4, 5, 6, TEXT_BRIGHT);
            g.pose().popPose();
        }
    }

    @Override protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (!slot.isHighlightable()) return;
        int offX = isDrawerSlot(slot) ? wingShiftX : 0;
        float pulse = 0.5f + 0.5f * (float) Math.sin((now % 3600000L) / 180.0);
        int col = menu.isStorageLocked(slot) ? VIOLET_GLOW : (isDrawerSlot(slot) ? wingGlow(workspaceMode) : CYAN_GLOW);
        g.fill(slot.x + offX, slot.y, slot.x + 16 + offX, slot.y + 16, opacity(col, 0.16f));
        focusReticle(g, slot.x - 1 + offX, slot.y - 1, 17, col, pulse);
    }

    @Override protected boolean isHovering(int x, int y, int w, int h, double mouseX, double mouseY) {
        return super.isHovering(x + (x >= WING_X ? wingShiftX : 0), y, w, h, mouseX, mouseY);
    }

    /** 展开工作翼时将侧翼区域纳入容器有效范围，防止点击侧翼槽位触发丢弃手执物品。 */
    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        if (menu.drawerMode() != KleinMenu.DRAWER_NONE && wingOpen > 0.3f) {
            double rx = mouseX - guiLeft;
            double ry = mouseY - guiTop;
            if (rx >= WING_X - 4 && rx < WING_X + WING_W && ry >= WING_Y && ry < WING_Y + WING_H) {
                return false;
            }
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}

    private static boolean isDrawerSlot(Slot slot) {
        return slot.x >= WING_X;
    }

    // ===== 输入与交互 =====

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) return true;
        Control control = controlAt(mouseX, mouseY);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && control != null && control != Control.STAT_BADGE) {
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
            case TOOL_CRAFT -> toggleWing(KleinMenu.DRAWER_CRAFTING);
            case TOOL_SMELT -> toggleWing(KleinMenu.DRAWER_SMELTING);
            case TOOL_ANVIL -> toggleWing(KleinMenu.DRAWER_ANVIL);
            case WING_COLLAPSE -> {
                menu.setDrawerMode(KleinMenu.DRAWER_NONE);
                PacketDistributor.sendToServer(new KleinDrawerPayload(menu.containerId, KleinMenu.DRAWER_NONE));
                updateWingState(KleinMenu.DRAWER_NONE, Util.getMillis());
            }
            case DEPOSIT -> {
                depositPressedAt = Util.getMillis();
                menu.sendDepositMatching();
            }
            case AUTO -> {
                autoPressedAt = Util.getMillis();
                boolean enabled = !menu.autoDeposit();
                menu.setAutoDeposit(enabled);
                PacketDistributor.sendToServer(new KleinAutoDepositPayload(enabled));
            }
            case CLEAR -> {
                search.setValue("");
                focus(search);
            }
            case SORT -> changeView(search.getValue(), (menu.sortMode() + 1) % 3, menu.descending());
            case DIRECTION -> changeView(search.getValue(), menu.sortMode(), !menu.descending());
            case CAT_ALL -> selectCategory(KleinMenu.CATEGORY_ALL);
            case CAT_BLOCKS -> selectCategory(KleinMenu.CATEGORY_BLOCKS);
            case CAT_GEAR -> selectCategory(KleinMenu.CATEGORY_GEAR);
            case CAT_OTHER -> selectCategory(KleinMenu.CATEGORY_OTHER);
            case CLOSE -> onClose();
            case STAT_BADGE -> {}
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
        if (x < 0 || x >= GRID_WIDTH || y < 0 || y >= GRID_HEIGHT) return -1;
        return (int) (y / ROW_STEP) * COLUMNS + (int) (x / COLUMN_STEP);
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
        return x >= SCROLL_X - 1 && x < SCROLL_X + 7 && y >= KleinMenu.GRID_Y && y < KleinMenu.GRID_Y + GRID_HEIGHT;
    }

    private Control controlAt(double screenX, double screenY) {
        double px = screenX - leftPos, py = screenY - topPos;
        for (Control control : CONTROLS) {
            if (control == Control.CLEAR && (search == null || search.getValue().isEmpty())) continue;
            if (control == Control.WING_COLLAPSE) {
                if (menu.drawerMode() == KleinMenu.DRAWER_NONE || wingOpen < 0.7f) continue;
                if (control.contains(px - wingShiftX, py)) return control;
                continue;
            }
            if (control.contains(px, py)) return control;
        }
        return null;
    }

    private void drawControl(GuiGraphics g, Control control, int accent, boolean active) {
        button(g, leftPos + control.x, topPos + control.y, control.w, control.h,
                smooth(hoveredControls[control.ordinal()]), accent, active);
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
            case TOOL_CRAFT -> label("tab_crafting");
            case TOOL_SMELT -> label("tab_smelting");
            case TOOL_ANVIL -> label("tab_anvil");
            case WING_COLLAPSE -> Component.translatable("gui.back");
            case DEPOSIT -> label("quick_deposit");
            case AUTO -> label(menu.autoDeposit() ? "auto_deposit.on" : "auto_deposit.off");
            case CLEAR -> label("clear_search");
            case SORT -> label("change_sort");
            case DIRECTION -> label(menu.descending() ? "sort_descending" : "sort_ascending");
            case CAT_ALL -> label("cat_all_tip");
            case CAT_BLOCKS -> label("cat_blocks_tip");
            case CAT_GEAR -> label("cat_gear_tip");
            case CAT_OTHER -> label("cat_other_tip");
            case CLOSE -> Component.translatable("gui.done");
            case STAT_BADGE -> label("types", menu.totalStoredTypes());
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
