package org.gwfx.universaltool.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
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
 * <p>顶部为星阶、图鉴入口与象限快捷定位栏，中央为可自由拖拽缩放的 32 星图画布，
 * 右侧为可滚动试炼与星辉觉醒详情栏，底部为 3 套星盘预设、主被动星轨槽位与星座共鸣总览。
 */
public class SkillScreen extends Screen {
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 50;

    private final SkillStarMapRenderer starMap = new SkillStarMapRenderer();
    private final SkillDetailDrawer detailDrawer = new SkillDetailDrawer();
    private final SkillCodexModal codexModal = new SkillCodexModal();

    private int left;
    private int top;
    private int panelW;
    private int panelH;

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

    private float camX = 0.0F;
    private float camY = 0.0F;
    private float targetCamX = 0.0F;
    private float targetCamY = 0.0F;
    private float zoom = 0.92F;
    private float targetZoom = 0.92F;

    private boolean draggingMap = false;
    private boolean movedDuringDrag = false;
    private double dragStartX;
    private double dragStartY;
    private float dragCamStartX;
    private float dragCamStartY;

    private long openedAtMillis;
    private final List<NavBtn> headerButtons = new ArrayList<>();
    private final List<PresetBtn> presetButtons = new ArrayList<>();
    private final List<SlotHit> slotHits = new ArrayList<>();
    private final List<ResonanceHit> resonanceHits = new ArrayList<>();

    private record NavBtn(int x, int y, int w, int h, String label, Runnable action, int accent) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record PresetBtn(int index, int x, int y, int w, int h, String label) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record SlotHit(boolean active, int index, String skillId, int x, int y, int size) {
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
        panelW = Math.min(width, Math.min(1120, Math.max(310, width - 8)));
        panelH = Math.min(height, Math.min(700, Math.max(230, height - 8)));
        left = Math.max(0, (width - panelW) / 2);
        top = Math.max(0, (height - panelH) / 2);

        int pad = 8;
        int gap = 6;
        int contentY = top + HEADER_H + 6;
        int contentH = Math.max(100, panelH - HEADER_H - FOOTER_H - 14);

        detailW = Math.max(162, Math.min(268, Math.round(panelW * 0.32F)));
        mapW = Math.max(120, panelW - pad * 2 - detailW - gap);
        mapX = left + pad;
        mapY = contentY;
        mapH = contentH;

        detailX = mapX + mapW + gap;
        detailY = contentY;
        detailH = contentH;

        footerY = top + panelH - FOOTER_H - 4;

        PlayerSkillData data = ClientSkillCache.data();
        if (ModSkills.find(selectedSkillId).isEmpty()) {
            selectedSkillId = !data.activeLoadout().isEmpty()
                    ? data.activeLoadout().get(0)
                    : ModSkills.BATTLE_MOMENTUM;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!ClientSkillCache.isSystemEnabled()) {
            onClose();
            return;
        }
        g.fill(0, 0, width, height, 0x9903050B);

        long now = System.currentTimeMillis();
        float timeSec = (now % 3600000L) / 1000.0F;
        float openProgress = Math.min(1.0F, (now - openedAtMillis) / 340.0F);

        camX += (targetCamX - camX) * 0.22F;
        camY += (targetCamY - camY) * 0.22F;
        zoom += (targetZoom - zoom) * 0.22F;

        int bgMouseX = codexModal.isOpen() ? -1 : mouseX;
        int bgMouseY = codexModal.isOpen() ? -1 : mouseY;

        PlayerSkillData data = ClientSkillCache.data();
        if (codexModal.isOpen()) {
            SkillScreenStyle.drawStarfieldBackground(g, 0, 0, width, height, timeSec, 0, 0);
            codexModal.render(g, font, data, width, height, mouseX, mouseY);
            return;
        }

        SkillDefinition hovered = starMap.hitTest(mapX, mapY, mapW, mapH, camX, camY, zoom, openProgress, bgMouseX, bgMouseY);
        hoveredSkillId = hovered != null ? hovered.id() : "";

        SkillScreenStyle.astrolabeWindowFrame(g, left, top, panelW, panelH);
        renderHeader(g, data, bgMouseX, bgMouseY);

        starMap.render(g, data, mapX, mapY, mapW, mapH, camX, camY, zoom, openProgress,
                selectedSkillId, hoveredSkillId, timeSec);

        SkillDefinition selectedDef = ModSkills.find(selectedSkillId).orElse(ModSkills.all().iterator().next());
        detailDrawer.render(g, font, data, selectedDef, detailX, detailY, detailW, detailH, bgMouseX, bgMouseY);

        renderFooter(g, data, bgMouseX, bgMouseY);

        // 悬停共鸣徽章或星图节点时渲染富文本 Tooltip
        boolean showedResonance = false;
        for (ResonanceHit hit : resonanceHits) {
            if (hit.contains(mouseX, mouseY)) {
                renderResonanceTooltip(g, data, hit, mouseX, mouseY);
                showedResonance = true;
                break;
            }
        }
        if (!showedResonance && hovered != null && !draggingMap) {
            renderNodeTooltip(g, data, hovered, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics g, PlayerSkillData data, int mouseX, int mouseY) {
        headerButtons.clear();
        int hy = top + 7;
        int curX = left + 10;

        StarRank rank = data.starRank();
        String rankName = Component.translatable(rank.translationKey()).getString();
        int rankW = font.width(rankName) + 12;
        SkillScreenStyle.badge(g, curX, hy + 1, rankW, 14, rankName,
                0xFF141D35, rank.accentColor(), rank.accentColor());
        curX += rankW + 6;

        String lvStr = "Lv." + data.level() + "/" + PlayerSkillData.MAX_LEVEL;
        g.drawString(font, lvStr, curX, hy + 4, SkillScreenStyle.TEXT_TITLE, true);
        curX += font.width(lvStr) + 8;

        int closeSize = 16;
        int closeX = left + panelW - closeSize - 8;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + closeSize && mouseY >= hy && mouseY < hy + closeSize;
        SkillScreenStyle.closeButton(g, closeX, hy, closeSize, closeHover);
        headerButtons.add(new NavBtn(closeX, hy, closeSize, closeSize, "", this::onClose, SkillScreenStyle.GOLD));

        // 全部洗点按钮
        int resetW = panelW > 560 ? 52 : 34;
        int resetX = closeX - resetW - 4;
        NavBtn resetBtn = new NavBtn(resetX, hy, resetW, 16, panelW > 560 ? "重置星图" : "重置",
                () -> sendAction(SkillActionPayload.Action.RESET, "", List.of()), 0xFFD46A6A);
        SkillScreenStyle.button(g, resetBtn.x(), resetBtn.y(), resetBtn.w(), resetBtn.h(),
                resetBtn.label(), !data.levels().isEmpty(), resetBtn.contains(mouseX, mouseY), resetBtn.accent());
        headerButtons.add(resetBtn);

        // 星典图鉴全览按钮（宽窄屏皆可见）
        int codexW = panelW > 560 ? 48 : 34;
        int codexX = resetX - codexW - 4;
        NavBtn codexBtn = new NavBtn(codexX, hy, codexW, 16, panelW > 560 ? "★图鉴" : "图鉴",
                codexModal::open, SkillScreenStyle.GOLD_BRIGHT);
        SkillScreenStyle.button(g, codexBtn.x(), codexBtn.y(), codexBtn.w(), codexBtn.h(),
                codexBtn.label(), true, codexBtn.contains(mouseX, mouseY), codexBtn.accent());
        headerButtons.add(codexBtn);

        // 象限快捷聚焦按钮（宽屏显示）
        int rightBound = codexX - 6;
        if (panelW >= 670) {
            String[] navLabels = {"全览", "破军", "玄武", "天枢", "巧匠"};
            int[] navColors = {
                    SkillScreenStyle.GOLD,
                    SkillConstellation.POJUN.primaryColor(),
                    SkillConstellation.XUANWU.primaryColor(),
                    SkillConstellation.TIANSHU.primaryColor(),
                    SkillConstellation.QIAOJIANG.primaryColor()
            };
            float[][] navPos = {{0, 0, 0.85F}, {140, -100, 1.15F}, {140, 100, 1.15F}, {-140, -100, 1.15F}, {-140, 100, 1.15F}};
            int navW = 30;
            int totalNavW = navLabels.length * (navW + 2);
            int navStartX = rightBound - totalNavW;
            for (int i = 0; i < navLabels.length; i++) {
                int bx = navStartX + i * (navW + 2);
                float tx = navPos[i][0], ty = navPos[i][1], tz = navPos[i][2];
                NavBtn nb = new NavBtn(bx, hy, navW, 16, navLabels[i], () -> {
                    targetCamX = tx;
                    targetCamY = ty;
                    targetZoom = tz;
                }, navColors[i]);
                SkillScreenStyle.button(g, nb.x(), nb.y(), nb.w(), nb.h(),
                        nb.label(), true, nb.contains(mouseX, mouseY), nb.accent());
                headerButtons.add(nb);
            }
            rightBound = navStartX - 8;
        }

        // 技能点标签
        String ptsStr = "◆ " + data.skillPoints();
        int ptsW = font.width(ptsStr) + 12;
        int ptsX = rightBound - ptsW;
        SkillScreenStyle.recessedWell(g, ptsX, hy, ptsW, 16, SkillScreenStyle.GOLD);
        g.drawCenteredString(font, ptsStr, ptsX + ptsW / 2, hy + 4, SkillScreenStyle.TEXT_GOLD);

        // 发光经验条
        int expW = ptsX - curX - 8;
        if (expW > 46) {
            int nextXp = data.experienceForNextLevel();
            float ratio = nextXp == 0 ? 1.0F : Math.min(1.0F, data.experience() / (float) nextXp);
            String xpStr = nextXp == 0 ? "MAX" : (data.experience() + "/" + nextXp);
            SkillScreenStyle.progressBar(g, curX, hy + 2, expW, 12, ratio,
                    0xFF2869A8, SkillScreenStyle.GOLD_BRIGHT, xpStr);
        }

        g.fill(left + 6, top + HEADER_H + 1, left + panelW - 6, top + HEADER_H + 2, SkillScreenStyle.GOLD_DIM);
    }

    private void renderFooter(GuiGraphics g, PlayerSkillData data, int mouseX, int mouseY) {
        presetButtons.clear();
        slotHits.clear();
        resonanceHits.clear();

        int x = left + 8;
        int w = panelW - 16;
        SkillScreenStyle.glassCard(g, x, footerY, w, FOOTER_H, SkillScreenStyle.BORDER_SUBTLE);

        int presetW = panelW >= 620 ? 58 : 34;
        int curX = x + 6;
        g.drawString(font, "星阵预设", curX, footerY + 5, SkillScreenStyle.TEXT_MUTED, false);
        for (int i = 0; i < PlayerSkillData.PRESET_COUNT; i++) {
            SkillPreset preset = data.preset(i);
            String label = panelW >= 620 ? preset.name() : String.valueOf(i + 1);
            int bx = curX + i * (presetW + 3);
            int by = footerY + 17;
            boolean active = data.activePresetIndex() == i;
            PresetBtn pb = new PresetBtn(i, bx, by, presetW, 24, label);
            presetButtons.add(pb);
            SkillScreenStyle.button(g, pb.x(), pb.y(), pb.w(), pb.h(), label, true,
                    pb.contains(mouseX, mouseY) || active,
                    active ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.BORDER_SUBTLE);
        }
        curX += PlayerSkillData.PRESET_COUNT * (presetW + 3) + 10;

        int slotSize = 20;
        int slotGap = 3;
        int resAreaW = panelW >= 660 ? 150 : 72;
        int slotsAvailW = x + w - resAreaW - curX - 8;

        if (slotsAvailW > 120) {
            g.drawString(font, "主动星轨 (" + data.activeLoadout().size() + "/" + data.activeSlotCount() + ")",
                    curX, footerY + 5, 0xFFFF8F8F, false);
            for (int i = 0; i < 6; i++) {
                int sx = curX + i * (slotSize + slotGap);
                int sy = footerY + 17;
                boolean unlockedSlot = i < data.activeSlotCount();
                String sid = i < data.activeLoadout().size() ? data.activeLoadout().get(i) : "";
                renderLoadoutSlot(g, sx, sy, slotSize, unlockedSlot, sid, true,
                        i == data.selectedActive() && !sid.isEmpty(), mouseX, mouseY);
                if (unlockedSlot && !sid.isEmpty()) {
                    slotHits.add(new SlotHit(true, i, sid, sx, sy, slotSize));
                }
            }

            int passiveStartX = curX + 6 * (slotSize + slotGap) + 12;
            if (passiveStartX + 8 * (slotSize + slotGap) <= x + w - resAreaW) {
                g.drawString(font, "被动星轨 (" + data.passiveLoadout().size() + "/" + data.passiveSlotCount() + ")",
                        passiveStartX, footerY + 5, 0xFF7CE8C0, false);
                for (int i = 0; i < 8; i++) {
                    int sx = passiveStartX + i * (slotSize + slotGap);
                    int sy = footerY + 17;
                    boolean unlockedSlot = i < data.passiveSlotCount();
                    String sid = i < data.passiveLoadout().size() ? data.passiveLoadout().get(i) : "";
                    renderLoadoutSlot(g, sx, sy, slotSize, unlockedSlot, sid, false, false, mouseX, mouseY);
                    if (unlockedSlot && !sid.isEmpty()) {
                        slotHits.add(new SlotHit(false, i, sid, sx, sy, slotSize));
                    }
                }
            }
        }

        int resX = x + w - resAreaW - 4;
        g.drawString(font, "星座共鸣", resX, footerY + 5, SkillScreenStyle.TEXT_GOLD, false);
        int chipW = (resAreaW - 6) / 2;
        int chipH = 13;
        SkillConstellation[] allC = SkillConstellation.values();
        for (int i = 0; i < allC.length; i++) {
            SkillConstellation c = allC[i];
            int tier = data.resonanceTier(c);
            int equipped = data.constellationEquippedCount(c);
            int cx = resX + (i % 2) * (chipW + 3);
            int cy = footerY + 16 + (i / 2) * (chipH + 3);
            String cname = Component.translatable(c.translationKey()).getString();
            String shortName = cname.length() >= 2 ? cname.substring(0, 2) : cname;
            String text = tier == 2 ? shortName + "★4" : (tier == 1 ? shortName + "★2" : shortName + " " + equipped);
            int bg = tier > 0 ? SkillScreenStyle.withAlpha(c.primaryColor(), 0.28F) : 0xFF0E1322;
            int border = tier == 2 ? SkillScreenStyle.GOLD_BRIGHT : (tier == 1 ? c.primaryColor() : SkillScreenStyle.BORDER_SUBTLE);
            SkillScreenStyle.badge(g, cx, cy, chipW, chipH, text, bg, border,
                    tier > 0 ? c.glowColor() : SkillScreenStyle.TEXT_MUTED);
            resonanceHits.add(new ResonanceHit(c, tier, cx, cy, chipW, chipH));
        }
    }

    private void renderLoadoutSlot(GuiGraphics g, int x, int y, int size, boolean unlocked, String skillId,
                                   boolean isActiveSlot, boolean selectedActive, int mouseX, int mouseY) {
        boolean hover = unlocked && mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        if (!unlocked) {
            SkillScreenStyle.recessedWell(g, x, y, size, size, 0xFF1C2338);
            g.drawCenteredString(font, "×", x + size / 2, y + 6, 0xFF394566);
            return;
        }
        int border = selectedActive ? SkillScreenStyle.GOLD_BRIGHT
                : (hover ? SkillScreenStyle.GOLD : (isActiveSlot ? 0xFF6E3B44 : 0xFF285C4E));
        SkillScreenStyle.recessedWell(g, x, y, size, size, border);
        if (!skillId.isEmpty()) {
            g.blit(SkillScreenStyle.icon(skillId), x + 2, y + 2, size - 4, size - 4, 0.0F, 0.0F, 32, 32, 32, 32);
            if (hover) {
                g.drawString(font, "-", x + size - 7, y + 1, SkillScreenStyle.TEXT_RED, true);
            }
        }
    }

    private void renderNodeTooltip(GuiGraphics g, PlayerSkillData data, SkillDefinition def, int mouseX, int mouseY) {
        int lv = data.learnedLevel(def.id());
        boolean unlocked = data.isUnlocked(def.id());
        String typeStr = def.type().isActive() ? "主动" : "被动";
        String cName = Component.translatable(def.constellation().translationKey()).getString();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6" + Component.translatable(def.translationKey()).getString()
                + " §7[" + cName + " · " + typeStr + "]"));
        if (lv > 0) {
            lines.add(Component.literal("§a当前等级: Lv." + lv + " / 3" + (lv >= 3 ? " §e★已觉醒" : "")));
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
                        .append(Component.translatable(c.resonance4Key()))
        );
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ===== 鼠标与键盘交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        for (PresetBtn pb : presetButtons) {
            if (pb.contains(mouseX, mouseY)) {
                sendAction(SkillActionPayload.Action.SWITCH_PRESET, String.valueOf(pb.index()), List.of());
                return true;
            }
        }
        for (SlotHit hit : slotHits) {
            if (hit.contains(mouseX, mouseY)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || hasShiftDown()) {
                    sendAction(hit.active() ? SkillActionPayload.Action.UNSET_ACTIVE : SkillActionPayload.Action.UNSET_PASSIVE,
                            String.valueOf(hit.index()), List.of());
                } else {
                    selectedSkillId = hit.skillId();
                    ModSkills.find(selectedSkillId).ifPresent(def -> {
                        targetCamX = def.mapX() * 0.45F;
                        targetCamY = def.mapY() * 0.45F;
                    });
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
                    } else {
                        sendAction(isActive ? SkillActionPayload.Action.SET_ACTIVE : SkillActionPayload.Action.SET_PASSIVE,
                                selectedDef.id(), List.of());
                    }
                }
                return true;
            }
            if (detailDrawer.resetBtn != null && detailDrawer.resetBtn.contains(mouseX, mouseY)) {
                if (data.learnedLevel(selectedDef.id()) > 0) {
                    sendAction(SkillActionPayload.Action.RESET, selectedDef.id(), List.of());
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
        if (detailDrawer.mouseReleased(button)) {
            return true;
        }
        if (draggingMap && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingMap = false;
            if (!movedDuringDrag) {
                float openProgress = Math.min(1.0F, (System.currentTimeMillis() - openedAtMillis) / 340.0F);
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
        if (codexModal.isOpen()) {
            return codexModal.mouseScrolled(mouseX, mouseY, scrollY);
        }
        if (detailDrawer.handleScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (mouseX >= mapX && mouseX < mapX + mapW && mouseY >= mapY && mouseY < mapY + mapH) {
            float factor = scrollY > 0 ? 1.12F : 0.89F;
            targetZoom = Math.max(0.60F, Math.min(1.55F, targetZoom * factor));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (codexModal.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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

    private void sendAction(SkillActionPayload.Action action, String skillId, List<String> loadout) {
        PacketDistributor.sendToServer(new SkillActionPayload(action, skillId, loadout));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
