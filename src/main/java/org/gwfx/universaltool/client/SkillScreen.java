package org.gwfx.universaltool.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.client.SkillStarMapRenderer.MapFilter;
import org.gwfx.universaltool.network.SkillActionPayload;
import org.gwfx.universaltool.skill.ModSkills;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillConstellation;
import org.gwfx.universaltool.skill.SkillDefinition;
import org.gwfx.universaltool.skill.SkillPreset;
import org.gwfx.universaltool.skill.SkillStatFormatter;
import org.gwfx.universaltool.skill.StarRank;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 「星辉秘仪 · 深空鎏金星盘」主界面。
 *
 * <p>顶部为纤巧星阶、经验与工具栏；中央为纯净无遮挡的 32 星图画布（底部悬浮主/被/已配筛选胶囊）；
 * 右侧为高密度阻尼滚动技能详情抽屉；底部为四张紧包内容、零空洞的独立玻璃子卡：
 * 星阵预设（支持自定义改名）、主动星轨（2×3）、被动星轨（2×4）与星座共鸣（支持点击聚焦象限）。
 */
public class SkillScreen extends Screen {
    private static final int HEADER_H = 24;

    private final SkillStarMapRenderer starMap = new SkillStarMapRenderer();
    private final SkillDetailDrawer detailDrawer = new SkillDetailDrawer();
    private final SkillCodexModal codexModal = new SkillCodexModal();

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int footerH;

    private int mapX;
    private int mapY;
    private int mapW;
    private int mapH;
    private int detailX;
    private int detailY;
    private int detailW;
    private int detailH;
    private int footerY;

    private String selectedSkillId = ModSkills.BATTLE_MOMENTUM;
    private String hoveredSkillId = "";
    private MapFilter mapFilter = MapFilter.ALL;

    private float camX = 0.0F;
    private float camY = 0.0F;
    private float targetCamX = 0.0F;
    private float targetCamY = 0.0F;
    private float zoom = 0.88F;
    private float targetZoom = 0.88F;

    private boolean draggingMap = false;
    private boolean movedDuringDrag = false;
    private double dragStartX;
    private double dragStartY;
    private float dragCamStartX;
    private float dragCamStartY;

    private long openedAtMillis;
    private long slotFlashUntilMillis = 0L;
    private int slotFlashColor = SkillScreenStyle.GOLD_BRIGHT;

    // 预设自定义改名浮层状态
    private int renamingPresetIndex = -1;
    private EditBox renameInput;
    private Rect renameDialogRect = new Rect(0, 0, 0, 0);
    private Rect renameConfirmBtn = new Rect(0, 0, 0, 0);
    private Rect renameCancelBtn = new Rect(0, 0, 0, 0);

    private final List<NavBtn> headerButtons = new ArrayList<>();
    private final List<FilterPillHit> filterPillHits = new ArrayList<>();
    private final List<PresetRowHit> presetRowHits = new ArrayList<>();
    private final List<SlotHit> slotHits = new ArrayList<>();
    private final List<ResonanceHit> resonanceHits = new ArrayList<>();

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record NavBtn(int x, int y, int w, int h, String label, Runnable action, int accent) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record FilterPillHit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record PresetRowHit(int index, int x, int y, int w, int h, Rect editBtn) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record SlotHit(boolean active, int index, boolean unlocked, String skillId, int x, int y, int size) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + size && my >= y && my < y + size; }
    }

    private record ResonanceHit(SkillConstellation c, int tier, int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    public SkillScreen() {
        super(Component.translatable("screen.universal_tool.skills.title"));
    }

    @Override
    protected void init() {
        super.init();
        if (openedAtMillis == 0) {
            openedAtMillis = System.currentTimeMillis();
        }
        panelW = Math.min(width, Math.min(1140, Math.max(320, width - 6)));
        panelH = Math.min(height, Math.min(700, Math.max(230, height - 6)));
        left = Math.max(0, (width - panelW) / 2);
        top = Math.max(0, (height - panelH) / 2);

        footerH = panelW >= 680 ? 58 : (panelW >= 420 ? 54 : 48);

        int pad = 6;
        int gap = 5;
        int contentY = top + HEADER_H + 4;
        int contentH = Math.max(100, panelH - HEADER_H - footerH - 12);

        detailW = Math.max(164, Math.min(280, Math.round(panelW * 0.34F)));
        mapW = Math.max(120, panelW - pad * 2 - detailW - gap);
        mapX = left + pad;
        mapY = contentY;
        mapH = contentH;

        detailX = mapX + mapW + gap;
        detailY = contentY;
        detailH = contentH;

        footerY = top + panelH - footerH - 4;

        PlayerSkillData data = ClientSkillCache.data();
        if (ModSkills.find(selectedSkillId).isEmpty()) {
            selectedSkillId = !data.activeLoadout().isEmpty()
                    ? data.activeLoadout().get(0)
                    : ModSkills.BATTLE_MOMENTUM;
        }

        // 初始化隐藏的重命名输入框（保留中文 IME 与标准编辑能力）
        int dlgW = Math.min(220, Math.max(170, panelW - 40));
        int dlgX = left + (panelW - dlgW) / 2;
        int dlgY = top + (panelH - 74) / 2;
        String prevText = renameInput != null ? renameInput.getValue() : "";
        renameInput = new EditBox(font, dlgX + 10, dlgY + 26, dlgW - 20, 16,
                Component.literal("预设名称"));
        renameInput.setMaxLength(12);
        renameInput.setValue(prevText);
        renameInput.setVisible(renamingPresetIndex >= 0);
        addRenderableWidget(renameInput);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!ClientSkillCache.isSystemEnabled()) {
            onClose();
            return;
        }
        g.fill(0, 0, width, height, 0x9E03050B);

        long now = System.currentTimeMillis();
        float timeSec = (now % 3600000L) / 1000.0F;
        float openProgress = Math.min(1.0F, (now - openedAtMillis) / 320.0F);
        float easeOpen = SkillScreenStyle.easeOutCubic(openProgress);

        camX += (targetCamX - camX) * 0.20F;
        camY += (targetCamY - camY) * 0.20F;
        zoom += (targetZoom - zoom) * 0.20F;

        boolean modalBlocking = codexModal.isOpen() || renamingPresetIndex >= 0;
        int bgMouseX = modalBlocking ? -1 : mouseX;
        int bgMouseY = modalBlocking ? -1 : mouseY;

        PlayerSkillData data = ClientSkillCache.data();
        if (codexModal.isOpen()) {
            SkillScreenStyle.drawStarfieldBackground(g, 0, 0, width, height, timeSec, 0, 0);
            codexModal.render(g, font, data, width, height, mouseX, mouseY);
            return;
        }

        SkillDefinition hovered = starMap.hitTest(mapX, mapY, mapW, mapH, camX, camY, zoom, openProgress, bgMouseX, bgMouseY);
        hoveredSkillId = hovered != null ? hovered.id() : "";

        // 柔和错峰入场微位移
        int headerShift = Math.round((1.0F - easeOpen) * -4.0F);
        int detailShift = Math.round((1.0F - easeOpen) * 6.0F);
        int footerShift = Math.round((1.0F - easeOpen) * 6.0F);

        SkillScreenStyle.astrolabeWindowFrame(g, left, top, panelW, panelH);
        renderHeader(g, data, top + headerShift, bgMouseX, bgMouseY);

        starMap.render(g, data, mapX, mapY, mapW, mapH, camX, camY, zoom, openProgress,
                selectedSkillId, hoveredSkillId, mapFilter, timeSec);
        renderMapFilterBar(g, data, bgMouseX, bgMouseY);

        SkillDefinition selectedDef = ModSkills.find(selectedSkillId).orElse(ModSkills.all().iterator().next());
        detailDrawer.render(g, font, data, selectedDef, detailX + detailShift, detailY, detailW, detailH,
                bgMouseX, bgMouseY, timeSec);

        renderFooter(g, data, footerY + footerShift, bgMouseX, bgMouseY, timeSec, now);

        // 重命名浮层处于最顶层
        if (renamingPresetIndex >= 0) {
            renderRenameDialog(g, data, mouseX, mouseY, partialTick);
            return;
        }

        // 悬停共鸣卡、预设行、装配槽位或星图节点时渲染富文本 Tooltip
        boolean showedOverlayTooltip = false;
        for (ResonanceHit hit : resonanceHits) {
            if (hit.contains(mouseX, mouseY)) {
                renderResonanceTooltip(g, data, hit, mouseX, mouseY);
                showedOverlayTooltip = true;
                break;
            }
        }
        if (!showedOverlayTooltip) {
            for (PresetRowHit hit : presetRowHits) {
                if (hit.contains(mouseX, mouseY)) {
                    renderPresetTooltip(g, data, hit, mouseX, mouseY);
                    showedOverlayTooltip = true;
                    break;
                }
            }
        }
        if (!showedOverlayTooltip) {
            for (SlotHit hit : slotHits) {
                if (hit.contains(mouseX, mouseY)) {
                    renderSlotTooltip(g, data, hit, mouseX, mouseY);
                    showedOverlayTooltip = true;
                    break;
                }
            }
        }
        if (!showedOverlayTooltip && hovered != null && !draggingMap) {
            // 鼠标位于底部筛选栏时不弹节点提示，避免视觉打架
            boolean overFilter = filterPillHits.stream().anyMatch(fp -> fp.contains(mouseX, mouseY));
            if (!overFilter) {
                renderNodeTooltip(g, data, hovered, mouseX, mouseY);
            }
        }
    }

    private void renderHeader(GuiGraphics g, PlayerSkillData data, int barTop, int mouseX, int mouseY) {
        headerButtons.clear();
        int hy = barTop + 4;
        int btnH = 15;
        int curX = left + 8;

        StarRank rank = data.starRank();
        String rankName = Component.translatable(rank.translationKey()).getString();
        int rankW = font.width(rankName) + 10;
        SkillScreenStyle.badge(g, curX, hy + 1, rankW, 13, rankName,
                0xFF141D35, rank.accentColor(), rank.accentColor());
        curX += rankW + 5;

        String lvStr = "Lv." + data.level() + "/" + PlayerSkillData.MAX_LEVEL;
        g.drawString(font, lvStr, curX, hy + 4, SkillScreenStyle.TEXT_TITLE, true);
        curX += font.width(lvStr) + 6;

        int closeSize = 15;
        int closeX = left + panelW - closeSize - 6;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + closeSize && mouseY >= hy && mouseY < hy + closeSize;
        SkillScreenStyle.closeButton(g, closeX, hy, closeSize, closeHover);
        headerButtons.add(new NavBtn(closeX, hy, closeSize, closeSize, "", this::onClose, SkillScreenStyle.GOLD));

        int resetW = panelW > 560 ? 48 : 30;
        int resetX = closeX - resetW - 3;
        NavBtn resetBtn = new NavBtn(resetX, hy, resetW, btnH, panelW > 560 ? "重置星图" : "重置",
                () -> sendAction(SkillActionPayload.Action.RESET, "", List.of()), 0xFFD46A6A);
        SkillScreenStyle.button(g, resetBtn.x(), resetBtn.y(), resetBtn.w(), resetBtn.h(),
                resetBtn.label(), !data.levels().isEmpty(), resetBtn.contains(mouseX, mouseY), resetBtn.accent());
        headerButtons.add(resetBtn);

        int codexW = panelW > 560 ? 46 : 30;
        int codexX = resetX - codexW - 3;
        NavBtn codexBtn = new NavBtn(codexX, hy, codexW, btnH, panelW > 560 ? "★图鉴" : "图鉴",
                codexModal::open, SkillScreenStyle.GOLD_BRIGHT);
        SkillScreenStyle.button(g, codexBtn.x(), codexBtn.y(), codexBtn.w(), codexBtn.h(),
                codexBtn.label(), true, codexBtn.contains(mouseX, mouseY), codexBtn.accent());
        headerButtons.add(codexBtn);

        // 宽屏显示象限快捷聚焦按钮（窄屏可直接点底栏星座共鸣卡定位）
        int rightBound = codexX - 5;
        if (panelW >= 670) {
            String[] navLabels = {"全览", "破军", "玄武", "天枢", "巧匠"};
            int[] navColors = {
                    SkillScreenStyle.GOLD,
                    SkillConstellation.POJUN.primaryColor(),
                    SkillConstellation.XUANWU.primaryColor(),
                    SkillConstellation.TIANSHU.primaryColor(),
                    SkillConstellation.QIAOJIANG.primaryColor()
            };
            float[][] navPos = {{0, 0, 0.86F}, {140, -100, 1.12F}, {140, 100, 1.12F}, {-140, -100, 1.12F}, {-140, 100, 1.12F}};
            int navW = 28;
            int totalNavW = navLabels.length * (navW + 2);
            int navStartX = rightBound - totalNavW;
            for (int i = 0; i < navLabels.length; i++) {
                int bx = navStartX + i * (navW + 2);
                float tx = navPos[i][0], ty = navPos[i][1], tz = navPos[i][2];
                NavBtn nb = new NavBtn(bx, hy, navW, btnH, navLabels[i], () -> {
                    targetCamX = tx;
                    targetCamY = ty;
                    targetZoom = tz;
                }, navColors[i]);
                SkillScreenStyle.button(g, nb.x(), nb.y(), nb.w(), nb.h(),
                        nb.label(), true, nb.contains(mouseX, mouseY), nb.accent());
                headerButtons.add(nb);
            }
            rightBound = navStartX - 6;
        }

        // 技能点凹槽
        String ptsStr = "◆ " + data.skillPoints();
        int ptsW = font.width(ptsStr) + 10;
        int ptsX = rightBound - ptsW;
        SkillScreenStyle.recessedWell(g, ptsX, hy, ptsW, btnH, SkillScreenStyle.GOLD);
        g.drawCenteredString(font, ptsStr, ptsX + ptsW / 2, hy + 4, SkillScreenStyle.TEXT_GOLD);

        // 发光经验条
        int expW = ptsX - curX - 6;
        if (expW > 40) {
            int nextXp = data.experienceForNextLevel();
            float ratio = nextXp == 0 ? 1.0F : Math.min(1.0F, data.experience() / (float) nextXp);
            String xpStr = nextXp == 0 ? "MAX" : (data.experience() + "/" + nextXp);
            SkillScreenStyle.progressBar(g, curX, hy + 2, expW, 11, ratio,
                    0xFF1E4E8C, 0xFFC7932E, xpStr);
        }

        g.fill(left + 6, top + HEADER_H, left + panelW - 6, top + HEADER_H + 1, SkillScreenStyle.GOLD_DIM);
    }

    /** 星图底部正中悬浮的极简「全部 / 主动 / 被动 / 已配 + 复位视角」药丸条，不遮挡任何星辰节点。 */
    private void renderMapFilterBar(GuiGraphics g, PlayerSkillData data, int mouseX, int mouseY) {
        filterPillHits.clear();
        boolean compact = mapW < 260;
        int eqCount = data.activeLoadout().size() + data.passiveLoadout().size();

        String[] labels = compact
                ? new String[]{"全部", "◆主", "●被", "★配"}
                : new String[]{"全部", "◆主动", "●被动", "★已配(" + eqCount + ")"};
        MapFilter[] filters = {MapFilter.ALL, MapFilter.ACTIVE, MapFilter.PASSIVE, MapFilter.EQUIPPED};
        int[] accents = {SkillScreenStyle.GOLD, 0xFFFF756B, 0xFF4BE0B8, 0xFF8CC8FF};

        int pillH = 13;
        int pillGap = 2;
        int[] widths = new int[labels.length];
        int sumW = 0;
        for (int i = 0; i < labels.length; i++) {
            widths[i] = Math.max(compact ? 26 : 34, font.width(labels[i]) + (compact ? 8 : 10));
            sumW += widths[i];
        }
        int resetW = 15;
        int totalW = sumW + pillGap * labels.length + resetW + 6;
        int bx = mapX + (mapW - totalW) / 2;
        int by = mapY + mapH - pillH - 4;

        g.fill(bx, by - 2, bx + totalW, by + pillH + 2, 0xCC070B16);
        SkillScreenStyle.outline(g, bx, by - 2, totalW, pillH + 4, SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_DIM, 0.85F));

        int curX = bx + 3;
        for (int i = 0; i < labels.length; i++) {
            MapFilter f = filters[i];
            int w = widths[i];
            boolean hover = mouseX >= curX && mouseX < curX + w && mouseY >= by && mouseY < by + pillH;
            SkillScreenStyle.filterPill(g, curX, by, w, pillH, labels[i], mapFilter == f, hover, accents[i]);
            filterPillHits.add(new FilterPillHit(curX, by, w, pillH, () -> mapFilter = f));
            curX += w + pillGap;
        }

        boolean resetHover = mouseX >= curX && mouseX < curX + resetW && mouseY >= by && mouseY < by + pillH;
        SkillScreenStyle.filterPill(g, curX, by, resetW, pillH, "⌖", false, resetHover, SkillScreenStyle.GOLD_BRIGHT);
        filterPillHits.add(new FilterPillHit(curX, by, resetW, pillH, () -> {
            targetCamX = 0.0F;
            targetCamY = 0.0F;
            targetZoom = 0.88F;
        }));
    }

    /**
     * 底栏彻底重构为 4 张独立、紧贴内容、零多余空白的深空玻璃子卡：
     * [卡1: 星阵预设(支持自定义改名)] | [卡2: 主动星轨 2×3] | [卡3: 被动星轨 2×4] | [卡4: 星座共鸣(自适应 2×2 / 1×4)]
     */
    private void renderFooter(GuiGraphics g, PlayerSkillData data, int fy, int mouseX, int mouseY,
                              float timeSec, long nowMillis) {
        presetRowHits.clear();
        slotHits.clear();
        resonanceHits.clear();

        int x = left + 6;
        int totalW = panelW - 12;
        int cardGap = 4;

        int slotSize = panelW >= 680 ? 20 : (panelW >= 420 ? 18 : 15);
        int slotGap = panelW >= 680 ? 3 : 2;
        int cardPadX = 6;

        // 1. 主动星轨卡 (2×3 紧包 6 槽，零右侧空洞)
        int activeGridW = 3 * slotSize + 2 * slotGap;
        int activeCardW = activeGridW + cardPadX * 2;

        // 2. 被动星轨卡 (2×4 紧包 8 槽，零右侧空洞)
        int passiveGridW = 4 * slotSize + 3 * slotGap;
        int passiveCardW = passiveGridW + cardPadX * 2;

        // 3. 星阵预设卡（纵排 3 条，完整显示自定义名称）
        int presetCardW = panelW >= 680 ? 96 : (panelW >= 440 ? 68 : 48);

        // 4. 星座共鸣卡（自动无缝填满剩余全部宽度）
        int resCardW = totalW - presetCardW - activeCardW - passiveCardW - cardGap * 3;
        if (resCardW < 92) {
            int deficit = 92 - resCardW;
            int shrink = Math.min(deficit, Math.max(0, presetCardW - 38));
            presetCardW -= shrink;
            resCardW += shrink;
        }

        int curX = x;
        renderPresetCard(g, data, curX, fy, presetCardW, footerH, mouseX, mouseY, timeSec);
        curX += presetCardW + cardGap;

        float flashAlpha = slotFlashUntilMillis > nowMillis
                ? (slotFlashUntilMillis - nowMillis) / 420.0F : 0.0F;

        renderLoadoutCard(g, data, true, curX, fy, activeCardW, footerH, slotSize, slotGap,
                3, 6, mouseX, mouseY, flashAlpha);
        curX += activeCardW + cardGap;

        renderLoadoutCard(g, data, false, curX, fy, passiveCardW, footerH, slotSize, slotGap,
                4, 8, mouseX, mouseY, flashAlpha);
        curX += passiveCardW + cardGap;

        renderResonanceCard(g, data, curX, fy, Math.max(72, x + totalW - curX), footerH, mouseX, mouseY, timeSec);
    }

    private void renderPresetCard(GuiGraphics g, PlayerSkillData data, int cx, int cy, int cw, int ch,
                                  int mouseX, int mouseY, float timeSec) {
        SkillScreenStyle.accentCard(g, cx, cy, cw, ch, SkillScreenStyle.GOLD);

        int padX = 4;
        int padY = 4;
        int rowGap = 2;
        int rowH = Math.max(11, (ch - padY * 2 - rowGap * (PlayerSkillData.PRESET_COUNT - 1)) / PlayerSkillData.PRESET_COUNT);
        int rowW = cw - padX * 2;

        for (int i = 0; i < PlayerSkillData.PRESET_COUNT; i++) {
            SkillPreset preset = data.preset(i);
            int rx = cx + padX;
            int ry = cy + padY + i * (rowH + rowGap);
            boolean active = data.activePresetIndex() == i;
            boolean hover = mouseX >= rx && mouseX < rx + rowW && mouseY >= ry && mouseY < ry + rowH;

            int bg = active ? 0xFF222D4C : (hover ? 0xFF1A223B : 0xFF0E1426);
            int border = active ? SkillScreenStyle.GOLD_BRIGHT : (hover ? SkillScreenStyle.GOLD_DIM : SkillScreenStyle.BORDER_SUBTLE);
            g.fill(rx, ry, rx + rowW, ry + rowH, bg);
            SkillScreenStyle.outline(g, rx, ry, rowW, rowH, border);
            if (active) {
                g.fill(rx, ry + 1, rx + 2, ry + rowH - 1, SkillScreenStyle.GOLD_BRIGHT);
                SkillScreenStyle.drawShimmerSweep(g, rx, ry, rowW, rowH, SkillScreenStyle.GOLD_BRIGHT, timeSec * 0.22F);
            }

            // 悬停或激活时右侧显示小铅笔改名钮
            Rect editBtn = new Rect(rx + rowW - 12, ry + 1, 11, rowH - 2);
            boolean showEdit = (hover || active) && rowW >= 40;
            int textMaxW = rowW - (showEdit ? 15 : 6);
            String displayName = preset.name();
            if (cw < 54 && font.width(displayName) > textMaxW) {
                displayName = "阵" + (i + 1);
            }
            g.drawString(font, SkillScreenStyle.trim(font, displayName, Math.max(12, textMaxW)),
                    rx + 4, ry + (rowH - 8) / 2,
                    active ? SkillScreenStyle.GOLD_BRIGHT : (hover ? SkillScreenStyle.TEXT_WHITE : SkillScreenStyle.TEXT_BODY),
                    false);

            if (showEdit) {
                boolean editHover = editBtn.contains(mouseX, mouseY);
                if (editHover) {
                    g.fill(editBtn.x(), editBtn.y(), editBtn.x() + editBtn.w(), editBtn.y() + editBtn.h(), 0x55E8C26A);
                }
                g.drawCenteredString(font, "✎", editBtn.x() + editBtn.w() / 2, ry + (rowH - 8) / 2,
                        editHover ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_MUTED);
            }

            presetRowHits.add(new PresetRowHit(i, rx, ry, rowW, rowH, showEdit ? editBtn : new Rect(-10, -10, 0, 0)));
        }
    }

    private void renderLoadoutCard(GuiGraphics g, PlayerSkillData data, boolean isActive,
                                   int cx, int cy, int cw, int ch, int slotSize, int slotGap,
                                   int cols, int totalSlots, int mouseX, int mouseY, float flashAlpha) {
        int accent = isActive ? 0xFFFF7A6B : 0xFF4BE0B8;
        SkillScreenStyle.accentCard(g, cx, cy, cw, ch, accent);

        int count = isActive ? data.activeLoadout().size() : data.passiveLoadout().size();
        int max = isActive ? data.activeSlotCount() : data.passiveSlotCount();
        String title = (isActive ? "◆主动" : "●被动");
        String ratioStr = count + "/" + max;
        g.drawString(font, title, cx + 5, cy + 3, accent, false);
        int rw = font.width(ratioStr);
        g.drawString(font, ratioStr, cx + cw - rw - 5, cy + 3,
                count >= max ? SkillScreenStyle.TEXT_GOLD : SkillScreenStyle.TEXT_MUTED, false);

        int gridW = cols * slotSize + (cols - 1) * slotGap;
        int startX = cx + (cw - gridW) / 2;
        int row1Y = cy + 13;
        int row2Y = row1Y + slotSize + slotGap;

        List<String> loadout = isActive ? data.activeLoadout() : data.passiveLoadout();
        for (int i = 0; i < totalSlots; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = startX + col * (slotSize + slotGap);
            int sy = row == 0 ? row1Y : row2Y;
            boolean unlockedSlot = i < max;
            String sid = i < loadout.size() ? loadout.get(i) : "";
            boolean awakened = !sid.isEmpty() && data.isAwakened(sid);
            boolean selectedMain = isActive && i == data.selectedActive() && !sid.isEmpty();
            renderLoadoutSlot(g, sx, sy, slotSize, unlockedSlot, sid, isActive, selectedMain, awakened,
                    mouseX, mouseY, flashAlpha);
            slotHits.add(new SlotHit(isActive, i, unlockedSlot, sid, sx, sy, slotSize));
        }
    }

    private void renderResonanceCard(GuiGraphics g, PlayerSkillData data, int cx, int cy, int cw, int ch,
                                     int mouseX, int mouseY, float timeSec) {
        SkillScreenStyle.accentCard(g, cx, cy, cw, ch, SkillScreenStyle.GOLD);

        SkillConstellation[] allC = SkillConstellation.values();
        int activeResCount = 0;
        for (SkillConstellation c : allC) {
            if (data.resonanceTier(c) > 0) activeResCount++;
        }

        g.drawString(font, "✦ 星座共鸣", cx + 6, cy + 3, SkillScreenStyle.TEXT_GOLD, false);
        if (cw >= 145) {
            String hint = activeResCount > 0 ? ("已激活 " + activeResCount + " 项共鸣 · 点击聚焦") : "点击聚焦象限";
            int hw = font.width(hint);
            g.drawString(font, hint, cx + cw - hw - 6, cy + 3, SkillScreenStyle.TEXT_MUTED, false);
        }

        int padX = 5;
        int innerW = cw - padX * 2;
        int startY = cy + 13;
        int innerH = ch - 17;

        // 宽屏展开为 1×4 全景大共鸣卡，中窄屏自动采用精致 2×2 共鸣卡
        boolean singleRow = innerW >= 340;
        int gap = 3;
        int cols = singleRow ? 4 : 2;
        int rows = singleRow ? 1 : 2;
        int chipW = Math.max(30, (innerW - gap * (cols - 1)) / cols);
        int chipH = Math.max(14, (innerH - gap * (rows - 1)) / rows);

        for (int i = 0; i < allC.length; i++) {
            SkillConstellation c = allC[i];
            int tier = data.resonanceTier(c);
            int equipped = data.constellationEquippedCount(c);
            int col = i % cols;
            int row = i / cols;
            int rx = cx + padX + col * (chipW + gap);
            int ry = startY + row * (chipH + gap);
            boolean hover = mouseX >= rx && mouseX < rx + chipW && mouseY >= ry && mouseY < ry + chipH;

            int bg = tier > 0
                    ? SkillScreenStyle.withAlpha(c.primaryColor(), hover ? 0.38F : (tier >= 3 ? 0.30F : 0.22F))
                    : (hover ? 0xFF182038 : 0xFF0D1323);
            int border = hover ? SkillScreenStyle.GOLD_BRIGHT
                    : (tier >= 3 ? 0xFFFFE57F
                    : (tier == 2 ? SkillScreenStyle.GOLD
                    : (tier == 1 ? c.primaryColor() : SkillScreenStyle.BORDER_SUBTLE)));

            g.fill(rx, ry, rx + chipW, ry + chipH, bg);
            SkillScreenStyle.outline(g, rx, ry, chipW, chipH, border);
            // 左侧 2px 星座主题竖色带
            g.fill(rx, ry + 1, rx + 2, ry + chipH - 1, tier > 0 ? c.glowColor() : SkillScreenStyle.withAlpha(c.primaryColor(), 0.55F));

            // 激活态周期性柔光掠过动画
            if (tier > 0) {
                SkillScreenStyle.drawShimmerSweep(g, rx, ry, chipW, chipH,
                        tier >= 3 ? SkillScreenStyle.GOLD_BRIGHT : c.glowColor(),
                        timeSec * 0.28F + i * 0.23F);
            }

            // 底部 1px 能量充能进度线（0~6 星）
            int progW = Math.round((chipW - 4) * Math.min(1.0F, equipped / 6.0F));
            if (progW > 0) {
                g.fill(rx + 2, ry + chipH - 2, rx + 2 + progW, ry + chipH - 1,
                        tier >= 3 ? SkillScreenStyle.GOLD_BRIGHT : c.primaryColor());
            }

            String fullName = Component.translatable(c.translationKey()).getString();
            String shortName = fullName.length() >= 2 ? fullName.substring(0, 2) : fullName;
            String tierText = tier == 3 ? "★6" : (tier == 2 ? "★4" : (tier == 1 ? "★2" : equipped + "星"));
            int nameColor = tier >= 3 ? SkillScreenStyle.GOLD_BRIGHT
                    : (tier > 0 ? c.glowColor() : SkillScreenStyle.TEXT_BODY);

            if (singleRow && chipH >= 28) {
                g.drawString(font, SkillScreenStyle.trim(font, fullName, chipW - 8), rx + 5, ry + 4, nameColor, false);
                SkillScreenStyle.resonanceDiamonds(g, rx + 5, ry + 16, tier, c.primaryColor());
                int tw = font.width(tierText);
                g.drawString(font, tierText, rx + chipW - tw - 4, ry + 15,
                        tier > 0 ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_MUTED, false);
                if (chipH >= 34) {
                    String sub = tier == 3 ? "六星共鸣激活" : (tier == 2 ? "四星共鸣" : (tier == 1 ? "二星共鸣" : "已装备 " + equipped + "/6"));
                    g.drawString(font, sub, rx + 5, ry + chipH - 11, SkillScreenStyle.TEXT_MUTED, false);
                }
            } else {
                int ty = ry + (chipH - 8) / 2;
                String display = chipW >= 92 ? fullName : shortName;
                boolean showDiamonds = chipW >= 78;
                int rightReserve = font.width(tierText) + (showDiamonds ? 24 : 6);
                g.drawString(font, SkillScreenStyle.trim(font, display, Math.max(20, chipW - rightReserve - 5)),
                        rx + 5, ty, nameColor, false);
                if (showDiamonds) {
                    SkillScreenStyle.resonanceDiamonds(g, rx + chipW - font.width(tierText) - 22, ty + 2, tier, c.primaryColor());
                }
                int tw = font.width(tierText);
                g.drawString(font, tierText, rx + chipW - tw - 3, ty,
                        tier > 0 ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_MUTED, false);
            }

            resonanceHits.add(new ResonanceHit(c, tier, rx, ry, chipW, chipH));
        }
    }

    private void renderLoadoutSlot(GuiGraphics g, int x, int y, int size, boolean unlocked, String skillId,
                                   boolean isActiveSlot, boolean selectedActive, boolean awakened,
                                   int mouseX, int mouseY, float flashAlpha) {
        boolean hover = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        if (!unlocked) {
            SkillScreenStyle.recessedWell(g, x, y, size, size, 0xFF1C2338);
            g.drawCenteredString(font, "×", x + size / 2, y + (size - 8) / 2, 0xFF394566);
            return;
        }
        int border = selectedActive ? SkillScreenStyle.GOLD_BRIGHT
                : (hover ? SkillScreenStyle.GOLD
                : (!skillId.isEmpty() ? (isActiveSlot ? 0xFFE06D64 : 0xFF4BE0B8)
                : (isActiveSlot ? 0xFF482830 : 0xFF204038)));
        SkillScreenStyle.recessedWell(g, x, y, size, size, border);
        if (!skillId.isEmpty()) {
            int inner = Math.max(4, size - 4);
            g.blit(SkillScreenStyle.icon(skillId), x + 2, y + 2, inner, inner, 0.0F, 0.0F, 32, 32, 32, 32);
            if (awakened) {
                g.fill(x + size - 3, y + 1, x + size - 1, y + 3, SkillScreenStyle.GOLD_BRIGHT);
            }
            if (selectedActive) {
                g.fill(x + 1, y + 1, x + 4, y + 4, SkillScreenStyle.GOLD_BRIGHT);
            }
            if (hover) {
                g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x28FFF1BA);
                g.drawString(font, "-", x + size - 6, y, SkillScreenStyle.TEXT_RED, true);
            }
        }
        if (flashAlpha > 0.01F && !skillId.isEmpty()) {
            SkillScreenStyle.outline(g, x, y, size, size, SkillScreenStyle.withAlpha(slotFlashColor, flashAlpha));
        }
    }

    /** 预设自定义改名深空鎏金对话框。 */
    private void renderRenameDialog(GuiGraphics g, PlayerSkillData data, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xAA03050C);
        int dlgW = Math.min(220, Math.max(170, panelW - 40));
        int dlgH = 74;
        int dlgX = left + (panelW - dlgW) / 2;
        int dlgY = top + (panelH - dlgH) / 2;
        renameDialogRect = new Rect(dlgX, dlgY, dlgW, dlgH);

        SkillScreenStyle.astrolabeWindowFrame(g, dlgX, dlgY, dlgW, dlgH);
        g.drawCenteredString(font, "✦ 重命名星阵预设 #" + (renamingPresetIndex + 1),
                dlgX + dlgW / 2, dlgY + 10, SkillScreenStyle.GOLD_BRIGHT);

        if (renameInput != null) {
            renameInput.setX(dlgX + 10);
            renameInput.setY(dlgY + 26);
            renameInput.setWidth(dlgW - 20);
            renameInput.setVisible(true);
            renameInput.render(g, mouseX, mouseY, partialTick);
        }

        int btnW = (dlgW - 26) / 2;
        int btnY = dlgY + 49;
        renameConfirmBtn = new Rect(dlgX + 10, btnY, btnW, 16);
        renameCancelBtn = new Rect(dlgX + 16 + btnW, btnY, btnW, 16);

        SkillScreenStyle.button(g, renameConfirmBtn.x(), renameConfirmBtn.y(), renameConfirmBtn.w(), renameConfirmBtn.h(),
                "确认改名", true, renameConfirmBtn.contains(mouseX, mouseY), SkillScreenStyle.GOLD_BRIGHT);
        SkillScreenStyle.button(g, renameCancelBtn.x(), renameCancelBtn.y(), renameCancelBtn.w(), renameCancelBtn.h(),
                "取消", true, renameCancelBtn.contains(mouseX, mouseY), 0xFFD46A6A);
    }

    private void openRenameDialog(int presetIdx) {
        PlayerSkillData data = ClientSkillCache.data();
        if (presetIdx < 0 || presetIdx >= PlayerSkillData.PRESET_COUNT) return;
        renamingPresetIndex = presetIdx;
        if (renameInput != null) {
            renameInput.setValue(data.preset(presetIdx).name());
            renameInput.setVisible(true);
            renameInput.setFocused(true);
            setFocused(renameInput);
        }
    }

    private void closeRenameDialog(boolean commit) {
        if (commit && renamingPresetIndex >= 0 && renameInput != null) {
            String newName = renameInput.getValue().trim();
            if (!newName.isEmpty()) {
                sendAction(SkillActionPayload.Action.RENAME_PRESET,
                        String.valueOf(renamingPresetIndex), List.of(newName));
                triggerSlotFlash(SkillScreenStyle.GOLD_BRIGHT);
            }
        }
        renamingPresetIndex = -1;
        if (renameInput != null) {
            renameInput.setVisible(false);
            renameInput.setFocused(false);
        }
        setFocused(null);
    }

    private void triggerSlotFlash(int color) {
        slotFlashColor = color;
        slotFlashUntilMillis = System.currentTimeMillis() + 420L;
    }

    private void focusConstellation(SkillConstellation c) {
        switch (c) {
            case POJUN -> { targetCamX = 140; targetCamY = -100; }
            case XUANWU -> { targetCamX = 140; targetCamY = 100; }
            case TIANSHU -> { targetCamX = -140; targetCamY = -100; }
            case QIAOJIANG -> { targetCamX = -140; targetCamY = 100; }
        }
        targetZoom = 1.10F;
    }

    // ===== Tooltip 渲染 =====

    private void renderPresetTooltip(GuiGraphics g, PlayerSkillData data, PresetRowHit hit, int mouseX, int mouseY) {
        SkillPreset preset = data.preset(hit.index());
        List<Component> lines = List.of(
                Component.literal("§6" + preset.name()
                        + (data.activePresetIndex() == hit.index() ? " §a[当前启用]" : "")),
                Component.literal("§7已装配: §c主动 " + preset.activeLoadout().size()
                        + "/" + data.activeSlotCount()
                        + " §7· §a被动 " + preset.passiveLoadout().size()
                        + "/" + data.passiveSlotCount()),
                Component.literal("§8左键切换星阵 · 右键或点 ✎ 自定义改名")
        );
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderSlotTooltip(GuiGraphics g, PlayerSkillData data, SlotHit hit, int mouseX, int mouseY) {
        String typeLabel = hit.active() ? "主动星轨" : "被动星轨";
        if (!hit.unlocked()) {
            List<Component> lines = List.of(
                    Component.literal("§7未解锁" + typeLabel + "槽位 #" + (hit.index() + 1)),
                    Component.literal("§8提升技能等级并晋升星阶后自动解锁更多槽位")
            );
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        if (hit.skillId().isEmpty()) {
            List<Component> lines = List.of(
                    Component.literal((hit.active() ? "§c" : "§a") + "空" + typeLabel + "槽位 #" + (hit.index() + 1)),
                    Component.literal("§7在星图选中已学习的对应技能，点击右侧「装备技能」")
            );
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
            return;
        }
        SkillDefinition def = ModSkills.find(hit.skillId()).orElse(null);
        if (def == null) return;
        int lv = Math.max(1, data.learnedLevel(def.id()));
        String cName = Component.translatable(def.constellation().translationKey()).getString();
        String typeStr = def.type().isActive() ? "主动" : "被动";
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6" + Component.translatable(def.translationKey()).getString()
                + " §7[" + cName + " · " + typeStr + "]"));
        lines.add(Component.literal("§a当前等级: Lv." + lv + " / 3" + (lv >= 3 ? " §e★已觉醒" : "")));
        lines.add(Component.literal("§f效果: §b" + SkillStatFormatter.format(def.id(), lv)));
        lines.add(Component.literal("§7" + Component.translatable(def.descriptionKey()).getString()));
        if (hit.active()) {
            lines.add(Component.literal("§8左键查看 · 中键/Ctrl+左键设为主释 · 右键/Shift+左键卸下"));
        } else {
            lines.add(Component.literal("§8左键定位星图节点 · 右键 / Shift+左键卸下"));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderNodeTooltip(GuiGraphics g, PlayerSkillData data, SkillDefinition def, int mouseX, int mouseY) {
        int lv = data.learnedLevel(def.id());
        boolean unlocked = data.isUnlocked(def.id());
        String typeStr = def.type().isActive() ? "◆主动" : "●被动";
        String cName = Component.translatable(def.constellation().translationKey()).getString();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6" + Component.translatable(def.translationKey()).getString()
                + " §7[" + cName + " · " + typeStr + "]"));
        if (lv > 0) {
            boolean equipped = def.type().isActive()
                    ? data.activeLoadout().contains(def.id())
                    : data.passiveLoadout().contains(def.id());
            lines.add(Component.literal("§a当前等级: Lv." + lv + " / 3" + (lv >= 3 ? " §e★已觉醒" : "")
                    + (equipped ? " §b[★已装配]" : " §8[未装配]")));
            lines.add(Component.literal("§f效果: §b" + SkillStatFormatter.format(def.id(), lv)));
        } else {
            PlayerSkillData.TrialProgress prog = data.trialProgress(def);
            String lockInfo = unlocked ? "§a试炼已达成" : ("§e试炼 " + prog.current() + "/" + prog.target());
            lines.add(Component.literal("§7未点亮 · " + lockInfo));
            lines.add(Component.literal("§7Lv.1 效果: §f" + SkillStatFormatter.format(def.id(), 1)));
        }
        lines.add(Component.literal("§7" + Component.translatable(def.descriptionKey()).getString()));
        lines.add(Component.literal("§6★ 觉醒: §e" + Component.translatable(def.awakeningKey()).getString()));
        lines.add(Component.literal("§8点击选中星辰，右侧滚动查看全阶数值"));

        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private void renderResonanceTooltip(GuiGraphics g, PlayerSkillData data, ResonanceHit hit, int mouseX, int mouseY) {
        SkillConstellation c = hit.c();
        int count = data.constellationEquippedCount(c);
        List<Component> lines = List.of(
                Component.translatable(c.translationKey()).append(" · 已装配 " + count + " 星"),
                Component.literal((hit.tier() >= 1 ? "§a[已激活] " : "§7[2星解锁] "))
                        .append(Component.translatable(c.resonance2Key())),
                Component.literal((hit.tier() >= 2 ? "§6[已激活] " : "§7[4星解锁] "))
                        .append(Component.translatable(c.resonance4Key())),
                Component.literal((hit.tier() >= 3 ? "§d[已激活] " : "§7[6星解锁] "))
                        .append(Component.translatable(c.resonance6Key())),
                Component.literal("§8点击此徽章可将星图聚焦至该星座象限")
        );
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ===== 鼠标与键盘交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (renamingPresetIndex >= 0) {
            if (renameConfirmBtn.contains(mouseX, mouseY)) {
                closeRenameDialog(true);
                return true;
            }
            if (renameCancelBtn.contains(mouseX, mouseY) || !renameDialogRect.contains(mouseX, mouseY)) {
                closeRenameDialog(false);
                return true;
            }
            if (renameInput != null && renameInput.mouseClicked(mouseX, mouseY, button)) {
                setFocused(renameInput);
                return true;
            }
            return true;
        }

        if (codexModal.isOpen()) {
            return codexModal.mouseClicked(mouseX, mouseY, button, id -> {
                selectedSkillId = id;
                ModSkills.find(id).ifPresent(def -> {
                    targetCamX = def.mapX() * 0.55F;
                    targetCamY = def.mapY() * 0.55F;
                    targetZoom = Math.max(targetZoom, 1.05F);
                });
            });
        }

        if (detailDrawer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (NavBtn btn : headerButtons) {
            if (btn.contains(mouseX, mouseY)) {
                btn.action().run();
                return true;
            }
        }
        for (FilterPillHit fp : filterPillHits) {
            if (fp.contains(mouseX, mouseY)) {
                fp.action().run();
                return true;
            }
        }
        for (PresetRowHit pr : presetRowHits) {
            if (pr.contains(mouseX, mouseY)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || pr.editBtn().contains(mouseX, mouseY)) {
                    openRenameDialog(pr.index());
                } else {
                    sendAction(SkillActionPayload.Action.SWITCH_PRESET, String.valueOf(pr.index()), List.of());
                    triggerSlotFlash(SkillScreenStyle.GOLD_BRIGHT);
                }
                return true;
            }
        }
        for (ResonanceHit rh : resonanceHits) {
            if (rh.contains(mouseX, mouseY)) {
                focusConstellation(rh.c());
                return true;
            }
        }
        for (SlotHit hit : slotHits) {
            if (hit.contains(mouseX, mouseY)) {
                if (hit.unlocked() && !hit.skillId().isEmpty()) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || hasShiftDown()) {
                        sendAction(hit.active() ? SkillActionPayload.Action.UNSET_ACTIVE : SkillActionPayload.Action.UNSET_PASSIVE,
                                String.valueOf(hit.index()), List.of());
                        triggerSlotFlash(0xFFFF7A7A);
                    } else if (hit.active() && (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || hasControlDown())) {
                        sendAction(SkillActionPayload.Action.SELECT_ACTIVE, String.valueOf(hit.index()), List.of());
                        triggerSlotFlash(SkillScreenStyle.GOLD_BRIGHT);
                    } else {
                        selectedSkillId = hit.skillId();
                        ModSkills.find(selectedSkillId).ifPresent(def -> {
                            targetCamX = def.mapX() * 0.45F;
                            targetCamY = def.mapY() * 0.45F;
                        });
                    }
                }
                return true;
            }
        }

        PlayerSkillData data = ClientSkillCache.data();
        SkillDefinition selectedDef = ModSkills.find(selectedSkillId).orElse(null);
        if (selectedDef != null) {
            if (detailDrawer.prereqBtn != null && detailDrawer.prereqBtn.contains(mouseX, mouseY)
                    && selectedDef.hasPrerequisite()) {
                selectedSkillId = selectedDef.prerequisiteId();
                ModSkills.find(selectedSkillId).ifPresent(pre -> {
                    targetCamX = pre.mapX() * 0.45F;
                    targetCamY = pre.mapY() * 0.45F;
                });
                return true;
            }
            if (detailDrawer.learnBtn != null && detailDrawer.learnBtn.contains(mouseX, mouseY)) {
                if (data.canLearnOrUpgrade(selectedDef.id())) {
                    sendAction(SkillActionPayload.Action.LEARN, selectedDef.id(), List.of());
                    starMap.spawnBurst(selectedDef.mapX(), selectedDef.mapY(),
                            selectedDef.constellation().primaryColor());
                }
                return true;
            }
            if (detailDrawer.equipBtn != null && detailDrawer.equipBtn.contains(mouseX, mouseY)) {
                if (data.learnedLevel(selectedDef.id()) > 0) {
                    boolean isActive = selectedDef.type().isActive();
                    List<String> loadout = isActive ? data.activeLoadout() : data.passiveLoadout();
                    int idx = loadout.indexOf(selectedDef.id());
                    if (idx >= 0) {
                        sendAction(isActive ? SkillActionPayload.Action.UNSET_ACTIVE : SkillActionPayload.Action.UNSET_PASSIVE,
                                String.valueOf(idx), List.of());
                        triggerSlotFlash(0xFFFF7A7A);
                    } else {
                        sendAction(isActive ? SkillActionPayload.Action.SET_ACTIVE : SkillActionPayload.Action.SET_PASSIVE,
                                selectedDef.id(), List.of());
                        starMap.spawnBurst(selectedDef.mapX(), selectedDef.mapY(),
                                isActive ? 0xFFFF8866 : 0xFF4BE0B8);
                        triggerSlotFlash(isActive ? 0xFFFF8866 : 0xFF4BE0B8);
                    }
                }
                return true;
            }
            if (detailDrawer.resetBtn != null && detailDrawer.resetBtn.contains(mouseX, mouseY)) {
                if (data.learnedLevel(selectedDef.id()) > 0) {
                    sendAction(SkillActionPayload.Action.RESET, selectedDef.id(), List.of());
                    triggerSlotFlash(0xFFFF7A7A);
                }
                return true;
            }
        }

        if (mouseX >= mapX && mouseX < mapX + mapW && mouseY >= mapY && mouseY < mapY + mapH) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                draggingMap = true;
                movedDuringDrag = false;
                dragStartX = mouseX;
                dragStartY = mouseY;
                dragCamStartX = targetCamX;
                dragCamStartY = targetCamY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (renamingPresetIndex >= 0) {
            return renameInput != null && renameInput.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (detailDrawer.mouseDragged(mouseX, mouseY, button)) {
            return true;
        }
        if (draggingMap && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double dx = mouseX - dragStartX;
            double dy = mouseY - dragStartY;
            if (Math.hypot(dx, dy) > 4.0) {
                movedDuringDrag = true;
            }
            targetCamX = Math.max(-280, Math.min(280, dragCamStartX - (float) (dx / zoom)));
            targetCamY = Math.max(-220, Math.min(220, dragCamStartY - (float) (dy / zoom)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (renamingPresetIndex >= 0) {
            return true;
        }
        if (detailDrawer.mouseReleased(button)) {
            return true;
        }
        if (draggingMap && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingMap = false;
            if (!movedDuringDrag) {
                float openProgress = Math.min(1.0F, (System.currentTimeMillis() - openedAtMillis) / 320.0F);
                SkillDefinition hit = starMap.hitTest(mapX, mapY, mapW, mapH, camX, camY, zoom, openProgress, mouseX, mouseY);
                if (hit != null) {
                    selectedSkillId = hit.id();
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (renamingPresetIndex >= 0) {
            return true;
        }
        if (codexModal.isOpen()) {
            return codexModal.mouseScrolled(mouseX, mouseY, scrollY);
        }
        if (detailDrawer.handleScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (mouseX >= mapX && mouseX < mapX + mapW && mouseY >= mapY && mouseY < mapY + mapH) {
            float factor = scrollY > 0 ? 1.12F : 0.89F;
            targetZoom = Math.max(0.58F, Math.min(1.55F, targetZoom * factor));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renamingPresetIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                closeRenameDialog(true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeRenameDialog(false);
                return true;
            }
            if (renameInput != null && renameInput.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return true;
        }
        if (codexModal.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || SkillKeybinds.OPEN.matches(keyCode, scanCode)) {
                codexModal.close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || SkillKeybinds.OPEN.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renamingPresetIndex >= 0) {
            return renameInput != null && renameInput.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void sendAction(SkillActionPayload.Action action, String skillId, List<String> loadout) {
        PacketDistributor.sendToServer(new SkillActionPayload(action, skillId, loadout));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
