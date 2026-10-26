package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.network.SkillActionPayload;
import org.gwfx.universaltool.skill.ModSkills;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillDefinition;
import org.gwfx.universaltool.skill.SkillStatFormatter;
import org.gwfx.universaltool.skill.SkillType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class SkillScreen extends Screen {
    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 20;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    private int leftW;
    private int centerW;
    private int rightW;
    private int contentY;
    private int contentH;

    private int tab = 0;
    private int selectedSkill = 0;
    private final int[] selectedByTab = {0, 0};
    private int browserScroll = 0;
    private boolean draggingBrowserScroll = false;
    private double dragStartMouseY = 0;
    private int dragStartScroll = 0;

    private int loadoutTab = 0;
    private int loadoutScroll = 0;
    private boolean draggingLoadoutScroll = false;
    private double dragStartLoadoutMouseY = 0;
    private int dragStartLoadoutScroll = 0;

    private boolean loadoutSelectionIsActive = true;
    private int loadoutSelectionIndex = 0;
    private boolean loadoutSelectionInitialized = false;
    private boolean showSkillDetails = false;

    public SkillScreen() {
        super(Component.translatable("screen.universal_tool.skills.title"));
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(width, Math.min(1080, Math.max(300, width - 10)));
        panelH = Math.min(height, Math.min(680, Math.max(220, height - 10)));
        left = Math.max(0, (width - panelW) / 2);
        top = Math.max(0, (height - panelH) / 2);

        int innerW = panelW - 16;
        int gap = 4;
        if (innerW <= 460) {
            leftW = Math.round(innerW * 0.285F);
            rightW = Math.round(innerW * 0.285F);
            centerW = innerW - leftW - rightW - gap * 2;
        } else {
            leftW = Math.min(230, Math.round(innerW * 0.27F));
            rightW = Math.min(230, Math.round(innerW * 0.27F));
            centerW = innerW - leftW - rightW - gap * 2;
        }

        contentY = top + HEADER_H + 6;
        contentH = panelH - HEADER_H - FOOTER_H - 12;

        if (!loadoutSelectionInitialized) {
            PlayerSkillData data = ClientSkillCache.data();
            if (!data.activeLoadout().isEmpty()) {
                loadoutSelectionIsActive = true;
                loadoutSelectionIndex = Math.min(data.selectedActive(), data.activeLoadout().size() - 1);
            } else if (!data.passiveLoadout().isEmpty()) {
                loadoutSelectionIsActive = false;
                loadoutSelectionIndex = 0;
            }
            loadoutSelectionInitialized = true;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        PlayerSkillData data = ClientSkillCache.data();
        clampSelection();

        SkillScreenStyle.frame(g, left, top, panelW, panelH);
        renderHeader(g, data, mouseX, mouseY);

        int gap = 4;
        int col1X = left + 8;
        int col2X = col1X + leftW + gap;
        int col3X = col2X + centerW + gap;

        int bgMouseX = showSkillDetails ? -1 : mouseX;
        int bgMouseY = showSkillDetails ? -1 : mouseY;

        renderBrowser(g, data, col1X, contentY, leftW, contentH, bgMouseX, bgMouseY);
        renderDetails(g, data, col2X, contentY, centerW, contentH, bgMouseX, bgMouseY);
        renderLoadouts(g, data, col3X, contentY, rightW, contentH, bgMouseX, bgMouseY);

        renderFooter(g, data, bgMouseX, bgMouseY);

        if (showSkillDetails) {
            g.flush();
            renderSkillDetailsOverlay(g, mouseX, mouseY);
            g.flush();
        }
    }

    private void renderHeader(GuiGraphics g, PlayerSkillData data, int mouseX, int mouseY) {
        int headerY = top + 5;
        int titleX = left + 12;
        SkillScreenStyle.drawDiamond(g, titleX, headerY + 6, SkillScreenStyle.BORDER_GOLD, true);
        g.drawString(font, title, titleX + 10, headerY + 4, SkillScreenStyle.TEXT_GOLD, false);

        int titleW = font.width(title);
        String levelStr = "Lv. " + data.level();
        int levelX = titleX + 10 + titleW + 8;
        g.drawString(font, levelStr, levelX, headerY + 4, SkillScreenStyle.BORDER_GOLD, false);
        int levelW = font.width(levelStr);

        int closeSize = 16;
        int closeX = left + panelW - closeSize - 8;
        int closeY = headerY + 2;
        boolean closeHover = inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        g.fill(closeX, closeY, closeX + closeSize, closeY + closeSize,
                closeHover ? 0xFF6A3828 : SkillScreenStyle.CARD_BORDER);
        SkillScreenStyle.outline(g, closeX, closeY, closeSize, closeSize,
                closeHover ? SkillScreenStyle.BORDER_GOLD : SkillScreenStyle.BORDER_BRONZE);
        g.drawCenteredString(font, "X", closeX + closeSize / 2, closeY + 4,
                closeHover ? SkillScreenStyle.TEXT_WHITE : SkillScreenStyle.TEXT_MUTED);

        String pointsStr = "◆ 技能点 " + data.skillPoints();
        int pointsW = font.width(pointsStr) + 12;
        int pointsX = closeX - pointsW - 6;
        int pointsY = headerY + 2;
        int pointsH = 16;
        g.fill(pointsX, pointsY, pointsX + pointsW, pointsY + pointsH, SkillScreenStyle.PANEL_BG);
        SkillScreenStyle.outline(g, pointsX, pointsY, pointsW, pointsH, SkillScreenStyle.BORDER_BRONZE);
        g.drawCenteredString(font, pointsStr, pointsX + pointsW / 2, pointsY + 4, SkillScreenStyle.TEXT_GOLD);

        int expX = levelX + levelW + 10;
        int expW = pointsX - expX - 10;
        if (expW > 40) {
            int expY = headerY + 6;
            int expH = 8;
            float ratio = data.experienceForNextLevel() == 0 ? 1.0F
                    : Math.min(1.0F, data.experience() / (float) data.experienceForNextLevel());
            String xpText = data.experience() + " / " + data.experienceForNextLevel();
            SkillScreenStyle.progressBar(g, expX, expY, expW, expH, ratio, xpText);
        }
    }

    private void renderBrowser(GuiGraphics g, PlayerSkillData data, int x, int y, int w, int h,
                               int mouseX, int mouseY) {
        SkillScreenStyle.card(g, x, y, w, h, true);

        int tabY = y + 4;
        int tabH = 16;
        int tabW = (w - 10) / 2;
        for (int i = 0; i < 2; i++) {
            int tx = x + 4 + i * (tabW + 2);
            boolean active = tab == i;
            boolean hover = inside(mouseX, mouseY, tx, tabY, tabW, tabH);
            g.fill(tx, tabY, tx + tabW, tabY + tabH,
                    active ? SkillScreenStyle.ROW_SELECTED : (hover ? SkillScreenStyle.ROW_HOVER : SkillScreenStyle.PANEL_BG));
            SkillScreenStyle.outline(g, tx, tabY, tabW, tabH,
                    active ? SkillScreenStyle.BORDER_GOLD : SkillScreenStyle.CARD_BORDER);
            String label = i == 0 ? "主动技能" : "被动技能";
            g.drawCenteredString(font, label, tx + tabW / 2, tabY + 4,
                    active ? SkillScreenStyle.TEXT_GOLD : (hover ? SkillScreenStyle.TEXT_WHITE : SkillScreenStyle.TEXT_MUTED));
        }

        List<SkillDefinition> skills = filteredSkills();
        int detailBtnH = 15;
        int detailBtnY = y + h - detailBtnH - 4;
        int detailBtnW = w - 8;
        int detailBtnX = x + 4;

        int listY = tabY + tabH + 4;
        int listH = detailBtnY - listY - 3;
        int rowH = 26;
        int rowGap = 2;
        int visible = Math.max(1, (listH + rowGap) / (rowH + rowGap));
        int maxScroll = Math.max(0, skills.size() - visible);
        browserScroll = Math.max(0, Math.min(browserScroll, maxScroll));

        boolean hasScroll = maxScroll > 0;
        int scrollBarW = 5;
        int cardW = hasScroll ? (w - 8 - scrollBarW - 2) : (w - 8);

        for (int i = 0; i < visible && i + browserScroll < skills.size(); i++) {
            int index = i + browserScroll;
            SkillDefinition skill = skills.get(index);
            int ry = listY + i * (rowH + rowGap);
            boolean selected = index == selectedSkill;
            boolean hover = inside(mouseX, mouseY, x + 4, ry, cardW, rowH);
            boolean learned = data.learned(skill.id());
            int curLevel = data.learnedLevel(skill.id());
            boolean unlocked = data.isUnlocked(skill.id());

            g.fill(x + 4, ry, x + 4 + cardW, ry + rowH,
                    selected ? SkillScreenStyle.ROW_SELECTED : (hover ? SkillScreenStyle.ROW_HOVER : SkillScreenStyle.ROW_BG));
            SkillScreenStyle.outline(g, x + 4, ry, cardW, rowH,
                    selected ? SkillScreenStyle.BORDER_GOLD : (hover ? SkillScreenStyle.BORDER_BRONZE : SkillScreenStyle.CARD_BORDER));

            if (selected) {
                g.fill(x + 4, ry, x + 6, ry + rowH, SkillScreenStyle.BORDER_GOLD);
            }

            SkillScreenStyle.skillIcon(g, skill.id(), x + 7, ry + 3, 20, !unlocked);

            String name = Component.translatable(skill.translationKey()).getString();
            int maxNameW = cardW - 55;
            g.drawString(font, SkillScreenStyle.trim(font, name, maxNameW),
                    x + 30, ry + 3, selected ? SkillScreenStyle.TEXT_GOLD : SkillScreenStyle.TEXT_WHITE, false);

            SkillScreenStyle.masteryDiamonds(g, x + 30, ry + 15, curLevel, skill.maxLevel());

            String lvlStr = curLevel + " / " + skill.maxLevel();
            int lvlW = font.width(lvlStr);
            g.drawString(font, lvlStr, x + 4 + cardW - lvlW - 4, ry + 9,
                    learned ? SkillScreenStyle.BORDER_GOLD : SkillScreenStyle.TEXT_DIM, false);
        }

        if (skills.isEmpty()) {
            g.drawCenteredString(font, "没有匹配的技能", x + w / 2, y + h / 2 - 4, SkillScreenStyle.TEXT_MUTED);
        }

        if (hasScroll) {
            int sbX = x + w - scrollBarW - 4;
            boolean sbHover = inside(mouseX, mouseY, sbX, listY, scrollBarW, listH);
            SkillScreenStyle.scrollbarVertical(g, sbX, listY, scrollBarW, listH,
                    browserScroll, maxScroll, visible, skills.size(), draggingBrowserScroll || sbHover);
        }

        boolean detailHover = inside(mouseX, mouseY, detailBtnX, detailBtnY, detailBtnW, detailBtnH);
        SkillScreenStyle.button(g, detailBtnX, detailBtnY, detailBtnW, detailBtnH, "查看详细信息", !skills.isEmpty(), detailHover);
    }

    private void renderDetails(GuiGraphics g, PlayerSkillData data, int x, int y, int w, int h,
                               int mouseX, int mouseY) {
        SkillScreenStyle.card(g, x, y, w, h, false);
        List<SkillDefinition> skills = filteredSkills();
        if (skills.isEmpty()) return;
        SkillDefinition skill = skills.get(selectedSkill);
        int learned = data.learnedLevel(skill.id());
        boolean unlocked = data.isUnlocked(skill.id());
        int pad = 8;

        int iconSize = 34;
        SkillScreenStyle.skillIcon(g, skill.id(), x + pad, y + pad, iconSize, !unlocked);

        int textLeft = x + pad + iconSize + 8;
        g.drawString(font, Component.translatable(skill.translationKey()),
                textLeft, y + pad, SkillScreenStyle.TEXT_GOLD, false);

        String masteryText = "熟练度 " + learned + " / " + skill.maxLevel();
        g.drawString(font, masteryText, textLeft, y + pad + 11, SkillScreenStyle.TEXT_MUTED, false);
        SkillScreenStyle.masteryDiamonds(g, textLeft + font.width(masteryText) + 6, y + pad + 13,
                learned, skill.maxLevel());

        int badgeY = y + pad + 23;
        String typeStr = skill.type() == SkillType.ACTIVE ? "主动技能" : "被动技能";
        int typeBg = skill.type() == SkillType.ACTIVE ? SkillScreenStyle.BADGE_ACTIVE_BG : SkillScreenStyle.BADGE_PASSIVE_BG;
        int typeBorder = skill.type() == SkillType.ACTIVE ? SkillScreenStyle.BADGE_ACTIVE_BORDER : SkillScreenStyle.BADGE_PASSIVE_BORDER;
        int typeText = skill.type() == SkillType.ACTIVE ? SkillScreenStyle.BADGE_ACTIVE_TEXT : SkillScreenStyle.BADGE_PASSIVE_TEXT;
        int typeW = font.width(typeStr) + 8;
        SkillScreenStyle.badge(g, textLeft, badgeY, typeW, 11, typeStr, typeBg, typeBorder, typeText);

        String roleStr = getSkillRole(skill.id());
        int roleW = font.width(roleStr) + 8;
        SkillScreenStyle.badge(g, textLeft + typeW + 4, badgeY, roleW, 11, roleStr,
                SkillScreenStyle.BADGE_ROLE_BG, SkillScreenStyle.BADGE_ROLE_BORDER, SkillScreenStyle.BADGE_ROLE_TEXT);

        int descY = y + pad + iconSize + 6;
        int descMaxW = w - pad * 2;
        List<net.minecraft.util.FormattedCharSequence> description =
                font.split(Component.translatable(skill.descriptionKey()), descMaxW);
        int maxDescLines = 3;
        for (int i = 0; i < Math.min(description.size(), maxDescLines); i++) {
            g.drawString(font, description.get(i), x + pad, descY, SkillScreenStyle.TEXT_BODY, false);
            descY += 10;
        }

        // Full details button moved to bottom of skill browser column

        int effectBoxY = y + pad + iconSize + 6 + maxDescLines * 10 + 4;
        int effectBoxH = 38;
        g.fill(x + pad, effectBoxY, x + w - pad, effectBoxY + effectBoxH, SkillScreenStyle.PANEL_INNER);
        SkillScreenStyle.outline(g, x + pad, effectBoxY, w - pad * 2, effectBoxH, SkillScreenStyle.CARD_BORDER);
        g.drawString(font, "等级效果", x + pad + 5, effectBoxY + 3, SkillScreenStyle.BORDER_GOLD, false);

        int maxStatW = w - pad * 2 - 10;
        if (learned <= 0) {
            String nextLine = "下一级: " + SkillStatFormatter.format(skill.id(), 1);
            g.drawString(font, SkillScreenStyle.trim(font, nextLine, maxStatW),
                    x + pad + 5, effectBoxY + 14, SkillScreenStyle.TEXT_WHITE, false);

            String costLine = "学习消耗 " + skill.costAt(1) + " 技能点";
            g.drawString(font, costLine, x + pad + 5, effectBoxY + 25, SkillScreenStyle.TEXT_MUTED, false);
        } else if (learned < skill.maxLevel()) {
            String curLine = "当前: " + SkillStatFormatter.format(skill.id(), learned);
            g.drawString(font, SkillScreenStyle.trim(font, curLine, maxStatW),
                    x + pad + 5, effectBoxY + 14, SkillScreenStyle.TEXT_WHITE, false);

            int nextCost = skill.costAt(learned + 1);
            String nextLine = "下级: " + SkillStatFormatter.format(skill.id(), learned + 1)
                    + " (消耗 " + nextCost + " 点)";
            g.drawString(font, SkillScreenStyle.trim(font, nextLine, maxStatW),
                    x + pad + 5, effectBoxY + 25, SkillScreenStyle.TEXT_GOLD, false);
        } else {
            String curLine = "当前: " + SkillStatFormatter.format(skill.id(), learned);
            g.drawString(font, SkillScreenStyle.trim(font, curLine, maxStatW),
                    x + pad + 5, effectBoxY + 14, SkillScreenStyle.TEXT_GOLD, false);
            g.drawString(font, "已升至最高等级", x + pad + 5, effectBoxY + 25, SkillScreenStyle.GREEN, false);
        }

        int unlockBoxY = effectBoxY + effectBoxH + 3;
        int unlockBoxH = 17;
        g.fill(x + pad, unlockBoxY, x + w - pad, unlockBoxY + unlockBoxH, SkillScreenStyle.PANEL_INNER);
        SkillScreenStyle.outline(g, x + pad, unlockBoxY, w - pad * 2, unlockBoxH, SkillScreenStyle.CARD_BORDER);

        String condStr = Component.translatable(skill.unlockKey()).getString();
        if (condStr.startsWith("解锁：") || condStr.startsWith("解锁: ")) {
            condStr = condStr.substring(3);
        }
        g.drawString(font, SkillScreenStyle.trim(font, "解锁: " + condStr, w - pad * 2 - 52),
                x + pad + 5, unlockBoxY + 5, unlocked ? SkillScreenStyle.TEXT_BODY : SkillScreenStyle.RED, false);

        String reqBadge = unlocked ? "✔ 已满足" : "✘ 未达成";
        int reqBadgeW = font.width(reqBadge);
        g.drawString(font, reqBadge, x + w - pad - reqBadgeW - 4, unlockBoxY + 5,
                unlocked ? SkillScreenStyle.GREEN : SkillScreenStyle.RED, false);

        int statBoxY = unlockBoxY + unlockBoxH + 3;
        int statBoxH = 16;
        g.fill(x + pad, statBoxY, x + w - pad, statBoxY + statBoxH, SkillScreenStyle.PANEL_INNER);
        SkillScreenStyle.outline(g, x + pad, statBoxY, w - pad * 2, statBoxH, SkillScreenStyle.CARD_BORDER);

        String cdText = skill.cooldownTicks() > 0 ? ("冷却: " + (skill.cooldownTicks() / 20.0F) + "s") : "被动常驻";
        String rangeText = "范围: " + getSkillRange(skill.id());
        String costText = learned >= skill.maxLevel() ? "已满级" : ("消耗: ◆" + skill.costAt(learned + 1));

        g.drawString(font, cdText, x + pad + 5, statBoxY + 4, SkillScreenStyle.TEXT_MUTED, false);
        g.drawString(font, rangeText, x + pad + (w - pad * 2) / 3, statBoxY + 4, SkillScreenStyle.TEXT_MUTED, false);
        g.drawString(font, costText, x + w - pad - font.width(costText) - 5, statBoxY + 4, SkillScreenStyle.TEXT_GOLD, false);

        int btnH = 20;
        int btnW = Math.min(180, w - pad * 2);
        int btnX = x + (w - btnW) / 2;
        int btnY = y + h - btnH - 6;

        boolean canLearn = canLearn(data);
        boolean hover = inside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        String btnText;
        if (learned >= skill.maxLevel()) {
            btnText = "已达最高等级";
        } else if (!unlocked) {
            btnText = "未满足解锁条件";
        } else if (data.skillPoints() < skill.costAt(learned + 1)) {
            btnText = "技能点不足 (需要 ◆ " + skill.costAt(learned + 1) + ")";
        } else {
            btnText = (learned == 0 ? "学习技能" : "升级技能") + "  ◆ " + skill.costAt(learned + 1);
        }

        SkillScreenStyle.button(g, btnX, btnY, btnW, btnH, btnText, canLearn, hover);
    }

    private void renderLoadouts(GuiGraphics g, PlayerSkillData data, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        SkillScreenStyle.card(g, x, y, w, h, true);

        int activeCount = data.activeLoadout().size();
        int activeMax = data.activeSlotCount();
        int passiveCount = data.passiveLoadout().size();
        int passiveMax = data.passiveSlotCount();

        int tabY = y + 4;
        int tabH = 16;
        int tabW = (w - 10) / 2;

        for (int i = 0; i < 2; i++) {
            int tx = x + 4 + i * (tabW + 2);
            boolean active = loadoutTab == i;
            boolean hover = inside(mouseX, mouseY, tx, tabY, tabW, tabH);
            g.fill(tx, tabY, tx + tabW, tabY + tabH,
                    active ? SkillScreenStyle.ROW_SELECTED : (hover ? SkillScreenStyle.ROW_HOVER : SkillScreenStyle.PANEL_BG));
            SkillScreenStyle.outline(g, tx, tabY, tabW, tabH,
                    active ? SkillScreenStyle.BORDER_GOLD : SkillScreenStyle.CARD_BORDER);

            String label = i == 0
                    ? ("主动 " + activeCount + "/" + activeMax)
                    : ("被动 " + passiveCount + "/" + passiveMax);
            g.drawCenteredString(font, label, tx + tabW / 2, tabY + 4,
                    active ? SkillScreenStyle.TEXT_GOLD : (hover ? SkillScreenStyle.TEXT_WHITE : SkillScreenStyle.TEXT_MUTED));
        }

        boolean showingActive = loadoutTab == 0;
        int maxSlots = showingActive ? activeMax : passiveMax;
        List<String> currentLoadout = showingActive ? data.activeLoadout() : data.passiveLoadout();

        int slotsTop = tabY + tabH + 4;
        int slotsH = h - (slotsTop - y) - 6;
        int slotH = showingActive
                ? Math.min(24, (slotsH - (maxSlots - 1) * 2) / Math.max(1, maxSlots))
                : Math.min(22, (slotsH - (maxSlots - 1) * 2) / Math.max(1, maxSlots));

        int visibleSlots = Math.max(1, (slotsH + 2) / (slotH + 2));
        int maxLoadoutScroll = Math.max(0, maxSlots - visibleSlots);
        loadoutScroll = Math.max(0, Math.min(loadoutScroll, maxLoadoutScroll));

        boolean hasScroll = maxLoadoutScroll > 0;
        int scrollBarW = 5;
        int slotW = hasScroll ? (w - 8 - scrollBarW - 2) : (w - 8);

        for (int i = 0; i < visibleSlots && i + loadoutScroll < maxSlots; i++) {
            int slotIndex = i + loadoutScroll;
            int sy = slotsTop + i * (slotH + 2);
            drawSlot(g, data, showingActive, slotIndex, x + 4, sy, slotW, slotH, mouseX, mouseY);
        }

        if (hasScroll) {
            int sbX = x + w - scrollBarW - 4;
            boolean sbHover = inside(mouseX, mouseY, sbX, slotsTop, scrollBarW, slotsH);
            SkillScreenStyle.scrollbarVertical(g, sbX, slotsTop, scrollBarW, slotsH,
                    loadoutScroll, maxLoadoutScroll, visibleSlots, maxSlots, draggingLoadoutScroll || sbHover);
        }
    }

    private void drawSlot(GuiGraphics g, PlayerSkillData data, boolean active, int index,
                          int x, int y, int w, int h, int mouseX, int mouseY) {
        List<String> loadout = active ? data.activeLoadout() : data.passiveLoadout();
        boolean filled = index < loadout.size();
        String id = filled ? loadout.get(index) : "";
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        boolean current = active && filled && index == data.selectedActive();
        boolean selectedInLoadout = filled && active == loadoutSelectionIsActive
                && index == loadoutSelectionIndex;

        g.fill(x, y, x + w, y + h,
                selectedInLoadout ? (current ? 0xFF2A3D35 : 0xFF3B2F25)
                        : (hover ? SkillScreenStyle.ROW_HOVER : SkillScreenStyle.ROW_BG));
        SkillScreenStyle.outline(g, x, y, w, h,
                selectedInLoadout ? (current ? SkillScreenStyle.TEAL : SkillScreenStyle.BORDER_GOLD)
                        : (hover ? SkillScreenStyle.BORDER_BRONZE : SkillScreenStyle.CARD_BORDER));

        int badgeSize = h - 4;
        int badgeX = x + 2;
        int badgeY = y + 2;
        g.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, SkillScreenStyle.PANEL_INNER);
        SkillScreenStyle.outline(g, badgeX, badgeY, badgeSize, badgeSize, SkillScreenStyle.CARD_BORDER);
        g.drawCenteredString(font, Integer.toString(index + 1), badgeX + badgeSize / 2, badgeY + (badgeSize - 8) / 2,
                SkillScreenStyle.TEXT_GOLD);

        if (!filled) {
            g.drawString(font, "空槽位", badgeX + badgeSize + 6, y + (h - 8) / 2, SkillScreenStyle.TEXT_DIM, false);
            return;
        }

        int iconSize = Math.max(12, Math.min(18, h - 4));
        SkillScreenStyle.skillIcon(g, id, badgeX + badgeSize + 3, y + (h - iconSize) / 2, iconSize, false);

        String name = Component.translatable("skill.universal_tool." + id).getString();
        int nameLeft = badgeX + badgeSize + iconSize + 6;
        int maxW = w - (nameLeft - x) - 18;
        g.drawString(font, SkillScreenStyle.trim(font, name, maxW), nameLeft, y + (h - 8) / 2, SkillScreenStyle.TEXT_WHITE, false);

        int btnSize = 12;
        int btnX = x + w - btnSize - 3;
        int btnY = y + (h - btnSize) / 2;
        boolean btnHover = inside(mouseX, mouseY, btnX, btnY, btnSize, btnSize);
        g.fill(btnX, btnY, btnX + btnSize, btnY + btnSize, btnHover ? 0xFF6A2E2E : SkillScreenStyle.PANEL_INNER);
        SkillScreenStyle.outline(g, btnX, btnY, btnSize, btnSize, btnHover ? SkillScreenStyle.RED : SkillScreenStyle.CARD_BORDER);
        g.drawCenteredString(font, "-", btnX + btnSize / 2, btnY + 2, btnHover ? SkillScreenStyle.RED : SkillScreenStyle.TEXT_MUTED);
    }

    private void renderFooter(GuiGraphics g, PlayerSkillData data, int mouseX, int mouseY) {
        int footerY = top + panelH - 22;
        g.fill(left + 6, footerY - 2, left + panelW - 6, footerY - 1, SkillScreenStyle.CARD_BORDER);

        String tip = "ℹ 使用鼠标选择技能，点击学习或装配到槽位";
        boolean combatLocked = data.inCombat(Minecraft.getInstance().level == null ? 0L
                : Minecraft.getInstance().level.getGameTime());
        if (combatLocked) {
            tip = "ℹ 战斗中不可修改技能，脱战10秒后可重置";
        }
        g.drawString(font, tip, left + 12, footerY + 5, SkillScreenStyle.TEXT_MUTED, false);

        List<SkillDefinition> skills = filteredSkills();
        boolean canReset = !skills.isEmpty() && !combatLocked
                && data.learnedLevel(skills.get(selectedSkill).id()) > 0;

        String resetText = "⟳ 重置技能点";
        int resetW = font.width(resetText) + 24;
        int resetH = 16;
        int resetX = left + panelW - resetW - 12;
        int resetY = footerY + 1;
        boolean hover = canReset && inside(mouseX, mouseY, resetX, resetY, resetW, resetH);
        SkillScreenStyle.button(g, resetX, resetY, resetW, resetH, resetText, canReset, hover);

        if (!skills.isEmpty()) {
            if (!canReset && inside(mouseX, mouseY, resetX, resetY, resetW, resetH)) {
                g.renderTooltip(font, Component.literal(combatLocked
                        ? "战斗中不可重置，脱战10秒后再试"
                        : "当前技能尚未投入技能点"), mouseX, mouseY);
            }
        }
    }

    private boolean canLearn(PlayerSkillData data) {
        List<SkillDefinition> skills = filteredSkills();
        if (skills.isEmpty()) return false;
        SkillDefinition skill = skills.get(selectedSkill);
        int learned = data.learnedLevel(skill.id());
        return learned < skill.maxLevel() && data.isUnlocked(skill.id())
                && data.skillPoints() >= skill.costAt(Math.min(skill.maxLevel(), learned + 1));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (showSkillDetails) {
            int mW = modalW();
            int mH = modalH();
            int mX = modalX();
            int mY = modalY();

            int modalCloseSize = 16;
            int modalCloseX = mX + mW - modalCloseSize - 10;
            int modalCloseY = mY + 8;
            if (inside(mx, my, modalCloseX, modalCloseY, modalCloseSize, modalCloseSize)) {
                showSkillDetails = false;
                return true;
            }

            int btnW = font.width("关闭") + 24;
            int btnH = 18;
            int btnX = mX + mW - btnW - 14;
            int btnY = mY + mH - btnH - 10;
            if (inside(mx, my, btnX, btnY, btnW, btnH)) {
                showSkillDetails = false;
                return true;
            }

            if (!inside(mx, my, mX, mY, mW, mH)) {
                showSkillDetails = false;
                return true;
            }

            return true;
        }

        int headerY = top + 5;
        int closeSize = 16;
        int closeX = left + panelW - closeSize - 8;
        int closeY = headerY + 2;
        if (inside(mx, my, closeX, closeY, closeSize, closeSize)) {
            onClose();
            return true;
        }

        int footerY = top + panelH - 22;
        String resetText = "⟳ 重置技能点";
        int resetW = font.width(resetText) + 24;
        int resetH = 16;
        int resetX = left + panelW - resetW - 12;
        int resetY = footerY + 1;
        if (inside(mx, my, resetX, resetY, resetW, resetH)) {
            if (!ClientSkillCache.data().inCombat(Minecraft.getInstance().level == null ? 0L
                    : Minecraft.getInstance().level.getGameTime())) {
                resetCurrentSkill();
            }
            return true;
        }

        int gap = 4;
        int col1X = left + 8;
        int col2X = col1X + leftW + gap;
        int col3X = col2X + centerW + gap;

        // Column 1: Tabs [ 主动技能 ] [ 被动技能 ]
        int tabY = contentY + 4;
        int tabH = 16;
        int tabW = (leftW - 10) / 2;
        for (int i = 0; i < 2; i++) {
            int tx = col1X + 4 + i * (tabW + 2);
            if (inside(mx, my, tx, tabY, tabW, tabH)) {
                if (tab != i) {
                    tab = i;
                    selectedSkill = selectedByTab[i];
                    browserScroll = 0;
                    loadoutTab = i;
                }
                return true;
            }
        }

        // Column 1: Detail button click
        int detailBtnH = 15;
        int detailBtnY = contentY + contentH - detailBtnH - 4;
        int detailBtnW = leftW - 8;
        int detailBtnX = col1X + 4;
        if (inside(mx, my, detailBtnX, detailBtnY, detailBtnW, detailBtnH)) {
            if (!filteredSkills().isEmpty()) {
                showSkillDetails = true;
            }
            return true;
        }

        // Column 1: Vertical scrollbar click
        List<SkillDefinition> skills = filteredSkills();
        int listY = tabY + tabH + 4;
        int listH = detailBtnY - listY - 3;
        int rowH = 26;
        int rowGap = 2;
        int visible = Math.max(1, (listH + rowGap) / (rowH + rowGap));
        int maxScroll = Math.max(0, skills.size() - visible);
        int scrollBarW = 5;

        if (maxScroll > 0) {
            int sbX = col1X + leftW - scrollBarW - 4;
            if (inside(mx, my, sbX - 2, listY, scrollBarW + 4, listH)) {
                draggingBrowserScroll = true;
                dragStartMouseY = mouseY;
                dragStartScroll = browserScroll;
                return true;
            }
        }

        // Column 1: Skill list item click
        int cardW = maxScroll > 0 ? (leftW - 8 - scrollBarW - 2) : (leftW - 8);
        for (int i = 0; i < visible && i + browserScroll < skills.size(); i++) {
            int ry = listY + i * (rowH + rowGap);
            if (inside(mx, my, col1X + 4, ry, cardW, rowH)) {
                selectedSkill = i + browserScroll;
                selectedByTab[tab] = selectedSkill;
                return true;
            }
        }

        // Column 2: Action Button [ 学习 / 升级 ]
        PlayerSkillData data = ClientSkillCache.data();
        int pad = 8;
        int btnH = 20;
        int btnW = Math.min(180, centerW - pad * 2);
        int btnX = col2X + (centerW - btnW) / 2;
        int btnY = contentY + contentH - btnH - 6;
        if (inside(mx, my, btnX, btnY, btnW, btnH)) {
            if (canLearn(data)) {
                learnSelected();
                return true;
            }
        }

        // Column 2: Details overlay button handled via Column 1 bottom button

        // Column 3: Loadout Tabs [ 主动装配 ] [ 被动装配 ]
        int loadoutTabY = contentY + 4;
        int loadoutTabH = 16;
        int loadoutTabW = (rightW - 10) / 2;
        for (int i = 0; i < 2; i++) {
            int tx = col3X + 4 + i * (loadoutTabW + 2);
            if (inside(mx, my, tx, loadoutTabY, loadoutTabW, loadoutTabH)) {
                if (loadoutTab != i) {
                    loadoutTab = i;
                    loadoutScroll = 0;
                }
                return true;
            }
        }

        // Column 3: Loadout slots
        boolean showingActive = loadoutTab == 0;
        int maxSlots = showingActive ? data.activeSlotCount() : data.passiveSlotCount();
        int slotsTop = loadoutTabY + loadoutTabH + 4;
        int slotsH = contentH - (slotsTop - contentY) - 6;
        int slotH = showingActive
                ? Math.min(24, (slotsH - (maxSlots - 1) * 2) / Math.max(1, maxSlots))
                : Math.min(22, (slotsH - (maxSlots - 1) * 2) / Math.max(1, maxSlots));

        int visibleSlots = Math.max(1, (slotsH + 2) / (slotH + 2));
        int maxLoadoutScroll = Math.max(0, maxSlots - visibleSlots);
        int loadoutScrollBarW = 5;
        int slotW = maxLoadoutScroll > 0 ? (rightW - 8 - loadoutScrollBarW - 2) : (rightW - 8);

        if (maxLoadoutScroll > 0) {
            int sbX = col3X + rightW - loadoutScrollBarW - 4;
            if (inside(mx, my, sbX - 2, slotsTop, loadoutScrollBarW + 4, slotsH)) {
                draggingLoadoutScroll = true;
                dragStartLoadoutMouseY = mouseY;
                dragStartLoadoutScroll = loadoutScroll;
                return true;
            }
        }

        List<String> curLoadout = showingActive ? data.activeLoadout() : data.passiveLoadout();
        for (int i = 0; i < visibleSlots && i + loadoutScroll < maxSlots; i++) {
            int slotIndex = i + loadoutScroll;
            int sy = slotsTop + i * (slotH + 2);
            if (inside(mx, my, col3X + 4, sy, slotW, slotH)) {
                int btnSize = 12;
                int bX = col3X + 4 + slotW - btnSize - 3;
                int bY = sy + (slotH - btnSize) / 2;
                if (inside(mx, my, bX, bY, btnSize, btnSize) && slotIndex < curLoadout.size()) {
                    unequipSlot(showingActive, slotIndex);
                    return true;
                }
                if (slotIndex < curLoadout.size()) {
                    loadoutSelectionIsActive = showingActive;
                    loadoutSelectionIndex = slotIndex;
                    loadoutSelectionInitialized = true;
                    selectSkillById(curLoadout.get(slotIndex));
                    if (showingActive) {
                        PacketDistributor.sendToServer(new SkillActionPayload(
                                SkillActionPayload.Action.SELECT_ACTIVE,
                                Integer.toString(slotIndex), List.of()));
                    }
                    return true;
                }
                if (!skills.isEmpty()) {
                    SkillDefinition cur = skills.get(selectedSkill);
                    boolean match = showingActive ? (cur.type() == SkillType.ACTIVE) : (cur.type() == SkillType.PASSIVE);
                    if (match && data.learned(cur.id()) && !curLoadout.contains(cur.id())) {
                        equipSkill(cur.id(), showingActive);
                        return true;
                    }
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectSkillById(String id) {
        SkillDefinition def = ModSkills.find(id).orElse(null);
        if (def == null) return;
        tab = def.type() == SkillType.ACTIVE ? 0 : 1;
        loadoutTab = tab;
        List<SkillDefinition> list = filteredSkills();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                selectedSkill = i;
                selectedByTab[tab] = i;
                break;
            }
        }
    }

    private int modalW() {
        return Math.min(520, Math.max(300, panelW - 30));
    }

    private int modalH() {
        return Math.min(340, Math.max(220, panelH - 30));
    }

    private int modalX() {
        return left + (panelW - modalW()) / 2;
    }

    private int modalY() {
        return top + (panelH - modalH()) / 2;
    }

    private void renderSkillDetailsOverlay(GuiGraphics g, int mouseX, int mouseY) {
        List<SkillDefinition> skills = filteredSkills();
        if (skills.isEmpty()) return;
        SkillDefinition skill = skills.get(selectedSkill);
        PlayerSkillData data = ClientSkillCache.data();

        // 1. Darken full screen behind the modal
        g.fill(0, 0, width, height, 0xCC000000);
        g.flush();

        // 2. Elevate modal to top layer
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);

        int mW = modalW();
        int mH = modalH();
        int mX = modalX();
        int mY = modalY();

        // Solid opaque modal card background
        g.fill(mX, mY, mX + mW, mY + mH, 0xFF140D1C);
        SkillScreenStyle.outline(g, mX, mY, mW, mH, SkillScreenStyle.BORDER_GOLD);
        SkillScreenStyle.outline(g, mX + 2, mY + 2, mW - 4, mH - 4, SkillScreenStyle.CARD_BORDER);
        g.fill(mX + 4, mY + 4, mX + mW - 4, mY + 5, SkillScreenStyle.BORDER_BRONZE_HI);

        // Corner diamond ornaments
        SkillScreenStyle.drawDiamond(g, mX + 3, mY + 3, SkillScreenStyle.BORDER_GOLD, true);
        SkillScreenStyle.drawDiamond(g, mX + mW - 8, mY + 3, SkillScreenStyle.BORDER_GOLD, true);
        SkillScreenStyle.drawDiamond(g, mX + 3, mY + mH - 8, SkillScreenStyle.BORDER_GOLD, true);
        SkillScreenStyle.drawDiamond(g, mX + mW - 8, mY + mH - 8, SkillScreenStyle.BORDER_GOLD, true);

        int pad = 12;
        int iconSize = 34;
        int iconX = mX + pad;
        int iconY = mY + pad;
        boolean unlocked = data.isUnlocked(skill.id());
        int learned = data.learnedLevel(skill.id());

        // Header: Skill icon
        SkillScreenStyle.skillIcon(g, skill.id(), iconX, iconY, iconSize, !unlocked);

        int textLeft = iconX + iconSize + 10;

        // Header: Skill name
        g.drawString(font, Component.translatable(skill.translationKey()),
                textLeft, iconY, SkillScreenStyle.TEXT_GOLD, false);

        // Header: Badges
        int badgeY = iconY + 12;
        String typeStr = skill.type() == SkillType.ACTIVE ? "主动技能" : "被动技能";
        int typeBg = skill.type() == SkillType.ACTIVE ? SkillScreenStyle.BADGE_ACTIVE_BG : SkillScreenStyle.BADGE_PASSIVE_BG;
        int typeBorder = skill.type() == SkillType.ACTIVE ? SkillScreenStyle.BADGE_ACTIVE_BORDER : SkillScreenStyle.BADGE_PASSIVE_BORDER;
        int typeText = skill.type() == SkillType.ACTIVE ? SkillScreenStyle.BADGE_ACTIVE_TEXT : SkillScreenStyle.BADGE_PASSIVE_TEXT;
        int typeW = font.width(typeStr) + 8;
        SkillScreenStyle.badge(g, textLeft, badgeY, typeW, 11, typeStr, typeBg, typeBorder, typeText);

        String roleStr = getSkillRole(skill.id());
        int roleW = font.width(roleStr) + 8;
        SkillScreenStyle.badge(g, textLeft + typeW + 4, badgeY, roleW, 11, roleStr,
                SkillScreenStyle.BADGE_ROLE_BG, SkillScreenStyle.BADGE_ROLE_BORDER, SkillScreenStyle.BADGE_ROLE_TEXT);

        // Header: Mastery diamonds
        String masteryText = "熟练度 " + learned + " / " + skill.maxLevel();
        g.drawString(font, masteryText, textLeft, badgeY + 13, SkillScreenStyle.TEXT_MUTED, false);
        SkillScreenStyle.masteryDiamonds(g, textLeft + font.width(masteryText) + 6, badgeY + 15,
                learned, skill.maxLevel());

        // Header: Top-right close button "X"
        int closeSize = 16;
        int closeX = mX + mW - closeSize - 10;
        int closeY = mY + 8;
        boolean closeHover = inside(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        g.fill(closeX, closeY, closeX + closeSize, closeY + closeSize,
                closeHover ? 0xFF6A3828 : SkillScreenStyle.CARD_BORDER);
        SkillScreenStyle.outline(g, closeX, closeY, closeSize, closeSize,
                closeHover ? SkillScreenStyle.BORDER_GOLD : SkillScreenStyle.BORDER_BRONZE);
        g.drawCenteredString(font, "X", closeX + closeSize / 2, closeY + 4,
                closeHover ? SkillScreenStyle.TEXT_WHITE : SkillScreenStyle.TEXT_MUTED);

        // Divider
        int divY = iconY + iconSize + 6;
        g.fill(mX + pad, divY, mX + mW - pad, divY + 1, SkillScreenStyle.CARD_BORDER);

        int sectionW = mW - pad * 2;

        // Section 1: Level Effects Box
        int effY = divY + 6;
        int effH = 46;
        g.fill(mX + pad, effY, mX + pad + sectionW, effY + effH, SkillScreenStyle.PANEL_INNER);
        SkillScreenStyle.outline(g, mX + pad, effY, sectionW, effH, SkillScreenStyle.CARD_BORDER);
        g.drawString(font, "◈ 等级效果", mX + pad + 6, effY + 4, SkillScreenStyle.BORDER_GOLD, false);

        int effTextY = effY + 16;
        if (learned <= 0) {
            String nextLine = "下一级: " + SkillStatFormatter.format(skill.id(), 1);
            g.drawString(font, SkillScreenStyle.trim(font, nextLine, sectionW - 12),
                    mX + pad + 6, effTextY, SkillScreenStyle.TEXT_WHITE, false);
            String costLine = "学习消耗: ◆ " + skill.costAt(1) + " 技能点";
            g.drawString(font, costLine, mX + pad + 6, effTextY + 12, SkillScreenStyle.TEXT_MUTED, false);
        } else if (learned < skill.maxLevel()) {
            String curLine = "当前 (Lv." + learned + "): " + SkillStatFormatter.format(skill.id(), learned);
            g.drawString(font, SkillScreenStyle.trim(font, curLine, sectionW - 12),
                    mX + pad + 6, effTextY, SkillScreenStyle.TEXT_WHITE, false);
            int nextCost = skill.costAt(learned + 1);
            String nextLine = "下一级 (Lv." + (learned + 1) + "): " + SkillStatFormatter.format(skill.id(), learned + 1)
                    + "  ·  消耗 ◆ " + nextCost + " 点";
            g.drawString(font, SkillScreenStyle.trim(font, nextLine, sectionW - 12),
                    mX + pad + 6, effTextY + 12, SkillScreenStyle.TEXT_GOLD, false);
        } else {
            String curLine = "当前 (Lv." + learned + " 满级): " + SkillStatFormatter.format(skill.id(), learned);
            g.drawString(font, SkillScreenStyle.trim(font, curLine, sectionW - 12),
                    mX + pad + 6, effTextY, SkillScreenStyle.TEXT_GOLD, false);
            g.drawString(font, "已升至最高等级", mX + pad + 6, effTextY + 12, SkillScreenStyle.GREEN, false);
        }

        // Section 2: Mechanics & Detailed Description
        int descBoxY = effY + effH + 5;
        int bottomReserved = 58;
        int descBoxH = Math.max(50, mH - (descBoxY - mY) - bottomReserved);
        g.fill(mX + pad, descBoxY, mX + pad + sectionW, descBoxY + descBoxH, SkillScreenStyle.PANEL_INNER);
        SkillScreenStyle.outline(g, mX + pad, descBoxY, sectionW, descBoxH, SkillScreenStyle.CARD_BORDER);
        g.drawString(font, "◈ 机制与技能说明", mX + pad + 6, descBoxY + 4, SkillScreenStyle.BORDER_GOLD, false);

        int descLineY = descBoxY + 16;
        List<net.minecraft.util.FormattedCharSequence> descLines =
                font.split(Component.translatable(skill.descriptionKey()), sectionW - 14);
        int maxLines = Math.max(1, (descBoxH - 20) / 11);
        for (int i = 0; i < Math.min(descLines.size(), maxLines); i++) {
            g.drawString(font, descLines.get(i), mX + pad + 6, descLineY, SkillScreenStyle.TEXT_BODY, false);
            descLineY += 11;
        }

        // Section 3: Unlock Requirements Box
        int unlY = descBoxY + descBoxH + 5;
        int unlH = 22;
        g.fill(mX + pad, unlY, mX + pad + sectionW, unlY + unlH, SkillScreenStyle.PANEL_INNER);
        SkillScreenStyle.outline(g, mX + pad, unlY, sectionW, unlH, SkillScreenStyle.CARD_BORDER);

        String condStr = Component.translatable(skill.unlockKey()).getString();
        if (condStr.startsWith("解锁：") || condStr.startsWith("解锁: ")) {
            condStr = condStr.substring(3);
        }
        g.drawString(font, SkillScreenStyle.trim(font, "◈ 解锁条件: " + condStr, sectionW - 70),
                mX + pad + 6, unlY + 7, unlocked ? SkillScreenStyle.TEXT_BODY : SkillScreenStyle.RED, false);

        String reqBadge = unlocked ? "✔ 已满足" : "✘ 未达成";
        int reqBadgeW = font.width(reqBadge);
        g.drawString(font, reqBadge, mX + pad + sectionW - reqBadgeW - 6, unlY + 7,
                unlocked ? SkillScreenStyle.GREEN : SkillScreenStyle.RED, false);

        // Section 4: Parameters & Bottom Footer Bar
        int footerBarY = unlY + unlH + 5;
        String cooldown = skill.cooldownTicks() > 0
                ? "冷却: " + (skill.cooldownTicks() / 20.0F) + "秒"
                : "被动常驻";
        String statsSummary = cooldown + "  ·  范围: " + getSkillRange(skill.id());
        g.drawString(font, statsSummary, mX + pad + 4, footerBarY + 5, SkillScreenStyle.TEXT_MUTED, false);

        // Close button at bottom-right
        int btnW = font.width("关闭") + 24;
        int btnH = 18;
        int btnX = mX + mW - btnW - pad;
        int btnY = mY + mH - btnH - 10;
        boolean btnHover = inside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        SkillScreenStyle.button(g, btnX, btnY, btnW, btnH, "关闭", true, btnHover);

        g.flush();
        g.pose().popPose();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (showSkillDetails) return true;
        if (button == 0) {
            if (draggingBrowserScroll) {
                List<SkillDefinition> skills = filteredSkills();
                int tabH = 16;
                int detailBtnH = 15;
                int detailBtnY = contentY + contentH - detailBtnH - 4;
                int listY = contentY + 4 + tabH + 4;
                int listH = detailBtnY - listY - 3;
                int rowH = 26;
                int rowGap = 2;
                int visible = Math.max(1, (listH + rowGap) / (rowH + rowGap));
                int maxScroll = Math.max(0, skills.size() - visible);

                if (maxScroll > 0) {
                    int thumbH = Math.max(16, Math.round(listH * ((float) visible / skills.size())));
                    int travel = listH - thumbH;
                    if (travel > 0) {
                        double deltaY = mouseY - dragStartMouseY;
                        int newScroll = dragStartScroll + Math.round((float) (deltaY / travel) * maxScroll);
                        browserScroll = Math.max(0, Math.min(maxScroll, newScroll));
                    }
                }
                return true;
            }

            if (draggingLoadoutScroll) {
                PlayerSkillData data = ClientSkillCache.data();
                boolean showingActive = loadoutTab == 0;
                int maxSlots = showingActive ? data.activeSlotCount() : data.passiveSlotCount();
                int tabH = 16;
                int slotsTop = contentY + 4 + tabH + 4;
                int slotsH = contentH - (slotsTop - contentY) - 6;
                int slotH = showingActive ? 24 : 22;
                int visibleSlots = Math.max(1, (slotsH + 2) / (slotH + 2));
                int maxLoadoutScroll = Math.max(0, maxSlots - visibleSlots);

                if (maxLoadoutScroll > 0) {
                    int thumbH = Math.max(16, Math.round(slotsH * ((float) visibleSlots / maxSlots)));
                    int travel = slotsH - thumbH;
                    if (travel > 0) {
                        double deltaY = mouseY - dragStartLoadoutMouseY;
                        int newScroll = dragStartLoadoutScroll + Math.round((float) (deltaY / travel) * maxLoadoutScroll);
                        loadoutScroll = Math.max(0, Math.min(maxLoadoutScroll, newScroll));
                    }
                }
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (showSkillDetails) return true;
        if (button == 0) {
            draggingBrowserScroll = false;
            draggingLoadoutScroll = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showSkillDetails) return true;
        int gap = 4;
        int col1X = left + 8;
        int col3X = col1X + leftW + gap + centerW + gap;

        if (inside((int) mouseX, (int) mouseY, col1X, contentY, leftW, contentH)) {
            List<SkillDefinition> skills = filteredSkills();
            int tabH = 16;
            int detailBtnH = 15;
            int detailBtnY = contentY + contentH - detailBtnH - 4;
            int listY = contentY + 4 + tabH + 4;
            int listH = detailBtnY - listY - 3;
            int rowH = 26;
            int rowGap = 2;
            int visible = Math.max(1, (listH + rowGap) / (rowH + rowGap));
            int maxScroll = Math.max(0, skills.size() - visible);
            browserScroll = Math.max(0, Math.min(maxScroll, browserScroll - (int) Math.signum(scrollY)));
            return true;
        }

        if (inside((int) mouseX, (int) mouseY, col3X, contentY, rightW, contentH)) {
            PlayerSkillData data = ClientSkillCache.data();
            boolean showingActive = loadoutTab == 0;
            int maxSlots = showingActive ? data.activeSlotCount() : data.passiveSlotCount();
            int tabH = 16;
            int slotsTop = contentY + 4 + tabH + 4;
            int slotsH = contentH - (slotsTop - contentY) - 6;
            int slotH = showingActive ? 24 : 22;
            int visibleSlots = Math.max(1, (slotsH + 2) / (slotH + 2));
            int maxLoadoutScroll = Math.max(0, maxSlots - visibleSlots);
            loadoutScroll = Math.max(0, Math.min(maxLoadoutScroll, loadoutScroll - (int) Math.signum(scrollY)));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private List<SkillDefinition> filteredSkills() {
        return skillsForTab(tab);
    }

    private static List<SkillDefinition> skillsForTab(int requestedTab) {
        return new ArrayList<>(requestedTab == 0 ? ModSkills.actives() : ModSkills.passives());
    }

    private void clampSelection() {
        for (int i = 0; i < selectedByTab.length; i++) {
            int size = skillsForTab(i).size();
            selectedByTab[i] = size == 0 ? 0 : Math.max(0, Math.min(selectedByTab[i], size - 1));
        }
        selectedSkill = selectedByTab[tab];
    }

    private void learnSelected() {
        List<SkillDefinition> skills = filteredSkills();
        if (skills.isEmpty()) return;
        PacketDistributor.sendToServer(new SkillActionPayload(SkillActionPayload.Action.LEARN,
                skills.get(selectedSkill).id(), List.of()));
    }

    private void equipSkill(String id, boolean active) {
        SkillActionPayload.Action action = active
                ? SkillActionPayload.Action.SET_ACTIVE : SkillActionPayload.Action.SET_PASSIVE;
        PacketDistributor.sendToServer(new SkillActionPayload(action, id, List.of()));
    }

    private void unequipSlot(boolean active, int slotIndex) {
        SkillActionPayload.Action action = active
                ? SkillActionPayload.Action.UNSET_ACTIVE : SkillActionPayload.Action.UNSET_PASSIVE;
        PacketDistributor.sendToServer(new SkillActionPayload(action,
                Integer.toString(slotIndex), List.of()));
    }

    private void resetCurrentSkill() {
        List<SkillDefinition> skills = filteredSkills();
        if (skills.isEmpty()) return;
        SkillDefinition selected = skills.get(selectedSkill);
        PlayerSkillData data = ClientSkillCache.data();
        if (data.learnedLevel(selected.id()) <= 0) return;
        PacketDistributor.sendToServer(new SkillActionPayload(
                SkillActionPayload.Action.RESET, selected.id(), List.of()));
    }

    private static String getSkillRole(String id) {
        return switch (id) {
            case ModSkills.COUNTER_GUARD -> "防守反击";
            case ModSkills.GRAVITY_WELL -> "群体牵引";
            case ModSkills.FROZEN_PULL -> "强力控制";
            case ModSkills.OPPRESSIVE_FIELD -> "群体削弱";
            case ModSkills.ORE_SIGHT -> "矿脉感知";
            case ModSkills.AIR_STEP -> "位移机动";
            case ModSkills.LIGHTNING_SPEAR -> "蓄力爆发";
            case ModSkills.BERSERK -> "战力强化";
            case ModSkills.BATTLE_MOMENTUM -> "连击增伤";
            case ModSkills.FOCUS_MINING -> "采掘加速";
            case ModSkills.LAST_STAND -> "濒死减伤";
            case ModSkills.SURVIVAL_FLOW -> "脱战自愈";
            case ModSkills.SWIFT_PACE -> "机动疾行";
            case ModSkills.LIGHT_FEATHER -> "轻盈免摔";
            case ModSkills.IRON_PHYSIQUE -> "体魄倍增";
            case ModSkills.ARMOR_PIERCING -> "破甲穿透";
            case ModSkills.BLOODTHIRST -> "近战吸血";
            case ModSkills.ITEM_MAGNET -> "引力拾取";
            case ModSkills.DIAMOND_BULWARK -> "护甲强化";
            case ModSkills.DAMAGE_CLAMP -> "单次限伤";
            default -> "特殊能力";
        };
    }

    private static String getSkillRange(String id) {
        return switch (id) {
            case ModSkills.COUNTER_GUARD -> "自身";
            case ModSkills.GRAVITY_WELL -> "28~36格";
            case ModSkills.FROZEN_PULL -> "20~28格";
            case ModSkills.OPPRESSIVE_FIELD -> "12~18格";
            case ModSkills.ORE_SIGHT -> "16~24格";
            case ModSkills.AIR_STEP -> "自身";
            case ModSkills.LIGHTNING_SPEAR -> "直线贯穿";
            case ModSkills.BERSERK -> "自身";
            default -> "常驻生效";
        };
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (showSkillDetails) {
                showSkillDetails = false;
                return true;
            }
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
