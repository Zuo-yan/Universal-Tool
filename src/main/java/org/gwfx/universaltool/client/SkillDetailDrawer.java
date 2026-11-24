package org.gwfx.universaltool.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.gwfx.universaltool.skill.ModSkills;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillConstellation;
import org.gwfx.universaltool.skill.SkillDefinition;
import org.gwfx.universaltool.skill.SkillStatFormatter;

import java.util.List;

/**
 * 右侧可滚动技能详情面板：完整展示星徽、试炼双轨进度、核心参数、技能简介、
 * Lv.1~Lv.3 全阶数值、★ 星辉觉醒词条与星座共鸣速览，不截断任何信息。
 */
final class SkillDetailDrawer {
    record ButtonBounds(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    ButtonBounds learnBtn;
    ButtonBounds equipBtn;
    ButtonBounds resetBtn;
    ButtonBounds prereqBtn;

    private String lastSkillId = "";
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int viewportX, viewportY, viewportW, viewportH;
    private ButtonBounds scrollbarTrack;
    private boolean draggingScroll = false;
    private double dragStartMouseY;
    private int dragStartOffset;

    void render(GuiGraphics g, Font font, PlayerSkillData data, SkillDefinition def,
                int x, int y, int w, int h, int mouseX, int mouseY) {
        learnBtn = null;
        equipBtn = null;
        resetBtn = null;
        prereqBtn = null;
        scrollbarTrack = null;

        if (!def.id().equals(lastSkillId)) {
            lastSkillId = def.id();
            scrollOffset = 0;
            draggingScroll = false;
        }

        SkillScreenStyle.glassCard(g, x, y, w, h, def.constellation().primaryColor());

        int pad = 8;
        int curY = y + pad;

        // 1. 固定头部（图标 + 名称 + 标签 + 星等）
        int iconSize = 32;
        int learnedLv = data.learnedLevel(def.id());
        boolean unlocked = data.isUnlocked(def.id());
        boolean preMet = data.isPrerequisiteMet(def);
        boolean canLearn = data.canLearnOrUpgrade(def.id());
        boolean awakened = learnedLv >= def.maxLevel();

        SkillScreenStyle.skillIcon(g, def.id(), x + pad, curY, iconSize, learnedLv == 0 && !canLearn);
        int textX = x + pad + iconSize + 7;
        int textW = w - pad * 2 - iconSize - 7;

        String name = Component.translatable(def.translationKey()).getString();
        g.drawString(font, SkillScreenStyle.trim(font, name, textW), textX, curY,
                awakened ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_TITLE, true);

        String cName = Component.translatable(def.constellation().translationKey()).getString();
        String typeStr = def.type().isActive() ? "主动" : "被动";
        String tierStr = Component.translatable(def.tier().translationKey()).getString();
        String metaBadge = cName + " · " + typeStr;
        int badgeW = Math.min(textW, font.width(metaBadge) + 8);
        SkillScreenStyle.badge(g, textX, curY + 11, badgeW, 11, metaBadge,
                def.type().isActive() ? SkillScreenStyle.BADGE_ACTIVE_BG : SkillScreenStyle.BADGE_PASSIVE_BG,
                def.constellation().primaryColor(), SkillScreenStyle.TEXT_WHITE);

        SkillScreenStyle.masteryDiamonds(g, textX, curY + 25, learnedLv, def.maxLevel(),
                def.constellation().primaryColor());
        String lvTag = awakened ? "★ 觉醒" : ("Lv." + learnedLv + "/" + def.maxLevel() + " · " + tierStr);
        g.drawString(font, SkillScreenStyle.trim(font, lvTag, Math.max(20, textW - 24)),
                textX + 23, curY + 24, awakened ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_GOLD, false);

        curY += iconSize + 6;
        g.fill(x + pad, curY, x + w - pad, curY + 1, SkillScreenStyle.withAlpha(def.constellation().primaryColor(), 0.45F));
        curY += 4;

        // 2. 固定底部操作按钮区
        int btnH = 18;
        int btnGap = 4;
        int bottomAreaH = btnH * 2 + btnGap + pad;
        int bodyBottom = y + h - bottomAreaH - 4;

        viewportX = x + pad;
        viewportY = curY;
        viewportW = w - pad * 2;
        viewportH = Math.max(24, bodyBottom - viewportY);

        // 先按需要滚动条的较保守宽度排版（若内容较长则右侧留 6px 给滚动条）
        int contentW = viewportW - 7;
        int drawY = viewportY - scrollOffset;

        g.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);

        // 2.1 前置星辰与试炼状态卡
        if (!unlocked || !preMet) {
            int cardH = 44;
            SkillScreenStyle.recessedWell(g, viewportX, drawY, contentW, cardH,
                    unlocked ? SkillScreenStyle.GOLD_DIM : SkillScreenStyle.BORDER_SUBTLE);
            int ty = drawY + 4;
            if (!preMet && def.hasPrerequisite()) {
                SkillDefinition pre = ModSkills.find(def.prerequisiteId()).orElse(null);
                String preName = pre != null ? Component.translatable(pre.translationKey()).getString() : def.prerequisiteId();
                String preText = "前置: " + preName + " (点击跳转)";
                ButtonBounds candidate = new ButtonBounds(viewportX + 4, ty, contentW - 8, 11);
                boolean preHover = candidate.contains(mouseX, mouseY)
                        && mouseY >= viewportY && mouseY < viewportY + viewportH;
                if (mouseY >= viewportY && mouseY < viewportY + viewportH) {
                    prereqBtn = candidate;
                }
                g.drawString(font, SkillScreenStyle.trim(font, preText, contentW - 8), viewportX + 4, ty,
                        preHover ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_RED, false);
            } else {
                String trialDesc = Component.translatable(def.unlockKey()).getString();
                g.drawString(font, SkillScreenStyle.trim(font, "试炼: " + trialDesc, contentW - 8),
                        viewportX + 4, ty, SkillScreenStyle.TEXT_GOLD, false);
            }
            PlayerSkillData.TrialProgress prog = data.trialProgress(def);
            String progStr = prog.current() + " / " + prog.target();
            SkillScreenStyle.progressBar(g, viewportX + 4, drawY + 20, contentW - 8, 12, prog.ratio(),
                    def.constellation().primaryColor(), SkillScreenStyle.GOLD_BRIGHT,
                    unlocked ? "试炼已达成 ✓" : progStr);
            drawY += cardH + 5;
            if (def.hasPrerequisite() && !preMet) {
                // 当未满足前置时，也把试炼条具体文字清晰写出以免被覆盖
                String trialFull = "试炼条件: " + Component.translatable(def.unlockKey()).getString();
                for (FormattedCharSequence tl : font.split(Component.literal(trialFull), contentW)) {
                    g.drawString(font, tl, viewportX, drawY, SkillScreenStyle.TEXT_MUTED, false);
                    drawY += 10;
                }
                drawY += 2;
            }
        } else {
            g.drawString(font, "✓ 前置与试炼均已达成", viewportX, drawY, SkillScreenStyle.TEXT_GREEN, false);
            drawY += 12;
        }

        // 2.2 核心参数与消耗行
        String costLine = "升级消耗: " + def.costAt(1) + " / " + def.costAt(2) + " / " + def.costAt(3) + " 点";
        if (def.type().isActive()) {
            int baseSec = def.cooldownTicks() / 20;
            String cdPart = data.resonanceTier(SkillConstellation.TIANSHU) >= 1
                    ? "冷却 " + Math.max(1, Math.round(baseSec * 0.88F)) + "s (原" + baseSec + "s)"
                    : "冷却 " + baseSec + "s";
            String mode = def.charged() ? "蓄力释放" : "瞬发";
            g.drawString(font, SkillScreenStyle.trim(font, cdPart + " · " + mode, contentW),
                    viewportX, drawY, SkillScreenStyle.TEXT_CYAN, false);
            drawY += 11;
        }
        g.drawString(font, costLine, viewportX, drawY, SkillScreenStyle.TEXT_MUTED, false);
        drawY += 12;

        // 2.3 完整技能描述
        g.drawString(font, "【技能说明】", viewportX, drawY, SkillScreenStyle.TEXT_GOLD, false);
        drawY += 11;
        List<FormattedCharSequence> descLines = font.split(Component.translatable(def.descriptionKey()), contentW);
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, viewportX, drawY, SkillScreenStyle.TEXT_WHITE, false);
            drawY += 10;
        }
        drawY += 6;

        // 2.4 Lv.1 / Lv.2 / Lv.3 完整三阶数值卡片
        g.drawString(font, "【等级数值阶梯】", viewportX, drawY, SkillScreenStyle.TEXT_GOLD, false);
        drawY += 11;
        for (int lv = 1; lv <= 3; lv++) {
            boolean activeLv = learnedLv >= lv;
            boolean nextLv = lv == learnedLv + 1;
            int rowCol = activeLv ? SkillScreenStyle.GOLD_BRIGHT
                    : (nextLv ? SkillScreenStyle.TEXT_WHITE : SkillScreenStyle.TEXT_BODY);
            String prefix = lv == 3 ? "★ Lv.3 " : ("Lv." + lv + " ");
            String stat = SkillStatFormatter.format(def.id(), lv);
            List<FormattedCharSequence> statLines = font.split(Component.literal(prefix + "· " + stat), contentW - 8);
            int boxH = statLines.size() * 10 + 6;
            int bg = lv == 3 ? 0x483B2A0C : (activeLv ? 0x401A3B31 : 0x2C0D1426);
            int boxBorder = lv == 3 ? SkillScreenStyle.GOLD
                    : (activeLv ? def.constellation().primaryColor() : (nextLv ? SkillScreenStyle.GOLD_DIM : SkillScreenStyle.BORDER_SUBTLE));
            g.fill(viewportX, drawY, viewportX + contentW, drawY + boxH, bg);
            SkillScreenStyle.outline(g, viewportX, drawY, contentW, boxH, boxBorder);
            int ly = drawY + 3;
            for (FormattedCharSequence sl : statLines) {
                g.drawString(font, sl, viewportX + 4, ly, rowCol, false);
                ly += 10;
            }
            drawY += boxH + 4;
        }

        // 2.5 星辉觉醒专属质变框
        String awakeBody = Component.translatable(def.awakeningKey()).getString();
        List<FormattedCharSequence> awakeLines = font.split(Component.literal("★ 星辉觉醒: " + awakeBody), contentW - 8);
        int awakeH = awakeLines.size() * 10 + 6;
        g.fill(viewportX, drawY, viewportX + contentW, drawY + awakeH, awakened ? 0x554D380E : 0x33241B0B);
        SkillScreenStyle.outline(g, viewportX, drawY, contentW, awakeH, SkillScreenStyle.GOLD_BRIGHT);
        int ay = drawY + 3;
        for (FormattedCharSequence al : awakeLines) {
            g.drawString(font, al, viewportX + 4, ay, SkillScreenStyle.GOLD_BRIGHT, false);
            ay += 10;
        }
        drawY += awakeH + 6;

        // 2.6 所属星座共鸣速览
        SkillConstellation c = def.constellation();
        int eqCount = data.constellationEquippedCount(c);
        int resTier = data.resonanceTier(c);
        g.drawString(font, cName + " 共鸣 (" + eqCount + " 星)", viewportX, drawY, c.primaryColor(), false);
        drawY += 10;
        for (FormattedCharSequence rl : font.split(Component.literal("2星: " + Component.translatable(c.resonance2Key()).getString()), contentW)) {
            g.drawString(font, rl, viewportX, drawY, resTier >= 1 ? SkillScreenStyle.TEXT_GREEN : SkillScreenStyle.TEXT_MUTED, false);
            drawY += 10;
        }
        for (FormattedCharSequence rl : font.split(Component.literal("4星: " + Component.translatable(c.resonance4Key()).getString()), contentW)) {
            g.drawString(font, rl, viewportX, drawY, resTier >= 2 ? SkillScreenStyle.TEXT_GOLD : SkillScreenStyle.TEXT_MUTED, false);
            drawY += 10;
        }
        drawY += 4;

        g.disableScissor();

        // 计算最大滚动量并钳制
        int totalContentH = (drawY + scrollOffset) - viewportY;
        maxScroll = Math.max(0, totalContentH - viewportH);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        // 绘制右侧精致滚动条
        if (maxScroll > 0) {
            int barX = viewportX + viewportW - 4;
            scrollbarTrack = new ButtonBounds(barX - 2, viewportY, 8, viewportH);
            g.fill(barX, viewportY, barX + 3, viewportY + viewportH, 0x66151E35);
            int thumbH = Math.max(18, Math.round(viewportH * (viewportH / (float) Math.max(1, totalContentH))));
            int travel = Math.max(1, viewportH - thumbH);
            int thumbY = viewportY + Math.round(travel * (scrollOffset / (float) maxScroll));
            int thumbCol = draggingScroll || scrollbarTrack.contains(mouseX, mouseY)
                    ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.GOLD;
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, thumbCol);
        }

        // 3. 固定底部按钮区
        int btnY1 = y + h - bottomAreaH + 2;
        int btnY2 = btnY1 + btnH + btnGap;
        int innerW = w - pad * 2;
        int nextCost = awakened ? 0 : def.costAt(learnedLv + 1);
        String learnLabel;
        if (awakened) {
            learnLabel = "★ 已至星辉觉醒";
        } else if (!preMet) {
            learnLabel = "需先点亮前置星辰";
        } else if (!unlocked) {
            learnLabel = "完成试炼以解锁";
        } else if (learnedLv == 0) {
            learnLabel = "点亮星辰 (消耗 " + nextCost + " 点)";
        } else {
            learnLabel = "精炼升级 Lv." + (learnedLv + 1) + " (消耗 " + nextCost + " 点)";
        }
        learnBtn = new ButtonBounds(x + pad, btnY1, innerW, btnH);
        SkillScreenStyle.button(g, learnBtn.x(), learnBtn.y(), learnBtn.w(), learnBtn.h(),
                learnLabel, canLearn, learnBtn.contains(mouseX, mouseY),
                def.constellation().primaryColor());

        boolean equipped = def.type().isActive()
                ? data.activeLoadout().contains(def.id())
                : data.passiveLoadout().contains(def.id());
        boolean canEquipOrUnset = learnedLv > 0 && (equipped || (def.type().isActive()
                ? data.activeLoadout().size() < data.activeSlotCount()
                : data.passiveLoadout().size() < data.passiveSlotCount()));

        int halfW = (innerW - 4) / 2;
        equipBtn = new ButtonBounds(x + pad, btnY2, halfW, btnH);
        String equipLabel = equipped ? "从星轨卸下" : (learnedLv > 0 ? "装配至星轨" : "未点亮");
        SkillScreenStyle.button(g, equipBtn.x(), equipBtn.y(), equipBtn.w(), equipBtn.h(),
                equipLabel, canEquipOrUnset, equipBtn.contains(mouseX, mouseY),
                SkillScreenStyle.GOLD);

        resetBtn = new ButtonBounds(x + pad + halfW + 4, btnY2, innerW - halfW - 4, btnH);
        SkillScreenStyle.button(g, resetBtn.x(), resetBtn.y(), resetBtn.w(), resetBtn.h(),
                "单星洗点", learnedLv > 0, resetBtn.contains(mouseX, mouseY),
                0xFFD46A6A);
    }

    boolean containsViewport(double mx, double my) {
        return mx >= viewportX && mx < viewportX + viewportW && my >= viewportY && my < viewportY + viewportH;
    }

    boolean handleScroll(double mx, double my, double scrollY) {
        if (!containsViewport(mx, my) || maxScroll <= 0) {
            return false;
        }
        int step = (int) Math.round(-scrollY * 20.0);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + step));
        return true;
    }

    boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && scrollbarTrack != null && scrollbarTrack.contains(mx, my) && maxScroll > 0) {
            draggingScroll = true;
            dragStartMouseY = my;
            dragStartOffset = scrollOffset;
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mx, double my, int button) {
        if (draggingScroll && button == 0 && maxScroll > 0 && viewportH > 18) {
            double dy = my - dragStartMouseY;
            int delta = (int) Math.round(dy * (maxScroll / (double) Math.max(1, viewportH - 24)));
            scrollOffset = Math.max(0, Math.min(maxScroll, dragStartOffset + delta));
            return true;
        }
        return false;
    }

    boolean mouseReleased(int button) {
        if (draggingScroll && button == 0) {
            draggingScroll = false;
            return true;
        }
        return false;
    }
}
