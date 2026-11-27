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
 * 右侧高密度可滚动技能详情面板：紧凑头部与操作区大幅提升首屏信息可视面积，
 * 支持丝滑阻尼滚轮滚动、完整 Lv.1~Lv.3 阶梯卡、星辉觉醒词条与星座 2/4/6 星共鸣速览。
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
    private float scrollOffset = 0.0F;
    private float targetScroll = 0.0F;
    private int maxScroll = 0;
    private int viewportX, viewportY, viewportW, viewportH;
    private ButtonBounds scrollbarTrack;
    private boolean draggingScroll = false;
    private double dragStartMouseY;
    private float dragStartOffset;

    void render(GuiGraphics g, Font font, PlayerSkillData data, SkillDefinition def,
                int x, int y, int w, int h, int mouseX, int mouseY, float timeSec) {
        learnBtn = null;
        equipBtn = null;
        resetBtn = null;
        prereqBtn = null;
        scrollbarTrack = null;

        if (!def.id().equals(lastSkillId)) {
            lastSkillId = def.id();
            scrollOffset = 0.0F;
            targetScroll = 0.0F;
            draggingScroll = false;
        }

        // 平滑阻尼滚动插值
        if (draggingScroll) {
            scrollOffset = targetScroll;
        } else {
            scrollOffset += (targetScroll - scrollOffset) * 0.28F;
            if (Math.abs(targetScroll - scrollOffset) < 0.25F) {
                scrollOffset = targetScroll;
            }
        }

        SkillScreenStyle.accentCard(g, x, y, w, h, def.constellation().primaryColor());

        int pad = 7;
        int curY = y + 6;

        // 1. 紧凑精致头部（26px 图标 + 名称/等级同行 + 星座类型胶囊与星钻同行）
        int iconSize = 26;
        int learnedLv = data.learnedLevel(def.id());
        boolean unlocked = data.isUnlocked(def.id());
        boolean preMet = data.isPrerequisiteMet(def);
        boolean canLearn = data.canLearnOrUpgrade(def.id());
        boolean awakened = learnedLv >= def.maxLevel();
        boolean isActive = def.type().isActive();

        SkillScreenStyle.skillIcon(g, def.id(), x + pad, curY, iconSize, learnedLv == 0 && !canLearn);
        int textX = x + pad + iconSize + 6;
        int textW = Math.max(40, w - pad * 2 - iconSize - 6);

        String name = Component.translatable(def.translationKey()).getString();
        String lvBadge = awakened ? "★觉醒" : ("Lv." + learnedLv + "/" + def.maxLevel());
        int lvW = font.width(lvBadge);
        g.drawString(font, SkillScreenStyle.trim(font, name, Math.max(24, textW - lvW - 4)), textX, curY + 1,
                awakened ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_TITLE, true);
        g.drawString(font, lvBadge, textX + textW - lvW, curY + 1,
                awakened ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_GOLD, false);

        String cName = Component.translatable(def.constellation().translationKey()).getString();
        String typeStr = isActive ? "主动" : "被动";
        String metaBadge = cName + " · " + typeStr;
        int badgeW = Math.min(Math.max(40, textW - 24), font.width(metaBadge) + 8);
        SkillScreenStyle.badge(g, textX, curY + 13, badgeW, 11, metaBadge,
                isActive ? SkillScreenStyle.BADGE_ACTIVE_BG : SkillScreenStyle.BADGE_PASSIVE_BG,
                isActive ? SkillScreenStyle.BADGE_ACTIVE_BORDER : SkillScreenStyle.BADGE_PASSIVE_BORDER,
                SkillScreenStyle.TEXT_WHITE);
        SkillScreenStyle.masteryDiamonds(g, textX + textW - 19, curY + 16, learnedLv, def.maxLevel(),
                def.constellation().primaryColor());

        curY += iconSize + 5;
        g.fill(x + pad, curY, x + w - pad, curY + 1,
                SkillScreenStyle.withAlpha(def.constellation().primaryColor(), 0.45F));
        curY += 4;

        // 2. 紧凑底部双排操作按钮区（每排 15px，释放更多内容视口高度）
        int btnH = 15;
        int btnGap = 3;
        int bottomAreaH = btnH * 2 + btnGap + 6;
        int bodyBottom = y + h - bottomAreaH - 3;

        viewportX = x + pad;
        viewportY = curY;
        viewportW = w - pad * 2;
        viewportH = Math.max(28, bodyBottom - viewportY);

        int contentW = viewportW - 6;
        int drawY = viewportY - Math.round(scrollOffset);

        g.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);

        // 2.1 状态、消耗与冷却紧凑元信息区
        if (!unlocked || !preMet) {
            int cardH = 38;
            SkillScreenStyle.recessedWell(g, viewportX, drawY, contentW, cardH,
                    unlocked ? SkillScreenStyle.GOLD_DIM : SkillScreenStyle.BORDER_SUBTLE);
            int ty = drawY + 3;
            if (!preMet && def.hasPrerequisite()) {
                SkillDefinition pre = ModSkills.find(def.prerequisiteId()).orElse(null);
                String preName = pre != null ? Component.translatable(pre.translationKey()).getString() : def.prerequisiteId();
                String preText = "前置: " + preName + " (点击跳转)";
                int clipTop = Math.max(viewportY, ty);
                int clipBottom = Math.min(viewportY + viewportH, ty + 11);
                ButtonBounds candidate = new ButtonBounds(viewportX + 4, clipTop, contentW - 8, clipBottom - clipTop);
                boolean preHover = clipBottom > clipTop && candidate.contains(mouseX, mouseY);
                if (clipBottom > clipTop) {
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
            SkillScreenStyle.progressBar(g, viewportX + 4, drawY + 17, contentW - 8, 11, prog.ratio(),
                    def.constellation().primaryColor(), SkillScreenStyle.GOLD_BRIGHT,
                    unlocked ? "试炼已达成 ✓" : progStr);
            drawY += cardH + 4;
            if (def.hasPrerequisite() && !preMet) {
                String trialFull = "试炼: " + Component.translatable(def.unlockKey()).getString();
                for (FormattedCharSequence tl : font.split(Component.literal(trialFull), contentW)) {
                    g.drawString(font, tl, viewportX, drawY, SkillScreenStyle.TEXT_MUTED, false);
                    drawY += 9;
                }
                drawY += 2;
            }
        } else {
            String costShort = "消耗 " + def.costAt(1) + "/" + def.costAt(2) + "/" + def.costAt(3) + " 点";
            g.drawString(font, "✓ 已达成", viewportX, drawY, SkillScreenStyle.TEXT_GREEN, false);
            int cw = font.width(costShort);
            g.drawString(font, costShort, viewportX + contentW - cw, drawY, SkillScreenStyle.TEXT_MUTED, false);
            drawY += 10;
        }

        if (isActive) {
            int baseSec = def.cooldownTicks() / 20;
            int tTier = data.resonanceTier(SkillConstellation.TIANSHU);
            int spatialLv = data.passiveLoadout().contains(ModSkills.SPATIAL_ATTUNEMENT)
                    ? data.learnedLevel(ModSkills.SPATIAL_ATTUNEMENT) : 0;
            float mult = 1.0F;
            if (tTier >= 1) mult -= 0.18F;
            if (tTier >= 3) mult -= 0.15F;
            if (spatialLv > 0) mult -= 0.05F * spatialLv;
            mult = Math.max(0.35F, mult);
            String cdPart = mult < 0.999F
                    ? "冷却 " + Math.max(1, Math.round(baseSec * mult)) + "s (原" + baseSec + "s)"
                    : "冷却 " + baseSec + "s";
            String mode = ModSkills.AIR_STEP.equals(def.id()) ? "按住推进" : (def.charged() ? "蓄力释放" : "瞬发主动");
            g.drawString(font, SkillScreenStyle.trim(font, "◆ " + cdPart + " · " + mode, contentW),
                    viewportX, drawY, SkillScreenStyle.TEXT_CYAN, false);
            drawY += 10;
        } else {
            String tierStr = Component.translatable(def.tier().translationKey()).getString();
            g.drawString(font, SkillScreenStyle.trim(font, "● 常驻被动 · " + tierStr, contentW),
                    viewportX, drawY, 0xFF66E8C2, false);
            drawY += 10;
        }

        // 2.2 完整技能简介（直出，无冗余标题行）
        List<FormattedCharSequence> descLines = font.split(Component.translatable(def.descriptionKey()), contentW);
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, viewportX, drawY, SkillScreenStyle.TEXT_WHITE, false);
            drawY += 9;
        }
        drawY += 5;

        // 2.3 Lv.1 / Lv.2 / Lv.3 紧凑阶梯数值卡
        for (int lv = 1; lv <= 3; lv++) {
            boolean activeLv = learnedLv >= lv;
            boolean nextLv = lv == learnedLv + 1;
            int rowCol = activeLv ? SkillScreenStyle.GOLD_BRIGHT
                    : (nextLv ? SkillScreenStyle.TEXT_WHITE : SkillScreenStyle.TEXT_BODY);
            String prefix = lv == 3 ? "★Lv.3 " : ("Lv." + lv + " ");
            String stat = SkillStatFormatter.format(def.id(), lv);
            List<FormattedCharSequence> statLines = font.split(Component.literal(prefix + stat), contentW - 8);
            int boxH = statLines.size() * 9 + 4;
            int bg = lv == 3 ? 0x483B2A0C : (activeLv ? 0x3D1A3B31 : 0x2A0D1426);
            int boxBorder = lv == 3 ? SkillScreenStyle.GOLD
                    : (activeLv ? def.constellation().primaryColor() : (nextLv ? SkillScreenStyle.GOLD_DIM : SkillScreenStyle.BORDER_SUBTLE));
            g.fill(viewportX, drawY, viewportX + contentW, drawY + boxH, bg);
            SkillScreenStyle.outline(g, viewportX, drawY, contentW, boxH, boxBorder);
            if (activeLv) {
                g.fill(viewportX, drawY + 1, viewportX + 2, drawY + boxH - 1,
                        lv == 3 ? SkillScreenStyle.GOLD_BRIGHT : def.constellation().primaryColor());
            }
            int ly = drawY + 2;
            for (FormattedCharSequence sl : statLines) {
                g.drawString(font, sl, viewportX + 4, ly, rowCol, false);
                ly += 9;
            }
            drawY += boxH + 3;
        }

        // 2.4 星辉觉醒专属质变框
        String awakeBody = Component.translatable(def.awakeningKey()).getString();
        List<FormattedCharSequence> awakeLines = font.split(Component.literal("★ 星辉觉醒: " + awakeBody), contentW - 8);
        int awakeH = awakeLines.size() * 9 + 5;
        g.fill(viewportX, drawY, viewportX + contentW, drawY + awakeH, awakened ? 0x554D380E : 0x33241B0B);
        SkillScreenStyle.outline(g, viewportX, drawY, contentW, awakeH, SkillScreenStyle.GOLD_BRIGHT);
        if (awakened) {
            SkillScreenStyle.drawShimmerSweep(g, viewportX, drawY, contentW, awakeH,
                    SkillScreenStyle.GOLD_BRIGHT, timeSec * 0.25F);
        }
        int ay = drawY + 3;
        for (FormattedCharSequence al : awakeLines) {
            g.drawString(font, al, viewportX + 4, ay, SkillScreenStyle.GOLD_BRIGHT, false);
            ay += 9;
        }
        drawY += awakeH + 5;

        // 2.5 所属星座 2 / 4 / 6 星共鸣速览
        SkillConstellation c = def.constellation();
        int eqCount = data.constellationEquippedCount(c);
        int resTier = data.resonanceTier(c);
        g.drawString(font, cName + " 共鸣 (" + eqCount + "星)", viewportX, drawY, c.primaryColor(), false);
        SkillScreenStyle.resonanceDiamonds(g, viewportX + contentW - 18, drawY + 2, resTier, c.primaryColor());
        drawY += 10;
        for (FormattedCharSequence rl : font.split(Component.literal("2星: " + Component.translatable(c.resonance2Key()).getString()), contentW)) {
            g.drawString(font, rl, viewportX, drawY, resTier >= 1 ? SkillScreenStyle.TEXT_GREEN : SkillScreenStyle.TEXT_MUTED, false);
            drawY += 9;
        }
        for (FormattedCharSequence rl : font.split(Component.literal("4星: " + Component.translatable(c.resonance4Key()).getString()), contentW)) {
            g.drawString(font, rl, viewportX, drawY, resTier >= 2 ? SkillScreenStyle.TEXT_GOLD : SkillScreenStyle.TEXT_MUTED, false);
            drawY += 9;
        }
        for (FormattedCharSequence rl : font.split(Component.literal("★6星: " + Component.translatable(c.resonance6Key()).getString()), contentW)) {
            g.drawString(font, rl, viewportX, drawY, resTier >= 3 ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_MUTED, false);
            drawY += 9;
        }
        drawY += 3;

        g.disableScissor();

        // 计算最大滚动量并钳制
        int totalContentH = (drawY + Math.round(scrollOffset)) - viewportY;
        maxScroll = Math.max(0, totalContentH - viewportH);
        if (targetScroll > maxScroll) targetScroll = maxScroll;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // 精致右侧滚动条
        if (maxScroll > 0) {
            int barX = viewportX + viewportW - 3;
            scrollbarTrack = new ButtonBounds(barX - 3, viewportY, 8, viewportH);
            g.fill(barX, viewportY, barX + 2, viewportY + viewportH, 0x66151E35);
            int thumbH = Math.max(16, Math.round(viewportH * (viewportH / (float) Math.max(1, totalContentH))));
            int travel = Math.max(1, viewportH - thumbH);
            int thumbY = viewportY + Math.round(travel * (scrollOffset / Math.max(1.0F, maxScroll)));
            int thumbCol = draggingScroll || scrollbarTrack.contains(mouseX, mouseY)
                    ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.GOLD;
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, thumbCol);
        }

        // 3. 固定底部按钮区
        int btnY1 = y + h - bottomAreaH + 1;
        int btnY2 = btnY1 + btnH + btnGap;
        int innerW = w - pad * 2;
        int nextCost = awakened ? 0 : def.costAt(learnedLv + 1);
        String learnLabel;
        if (awakened) {
            learnLabel = "★ 已满级觉醒";
        } else if (!preMet) {
            learnLabel = "需先学习前置技能";
        } else if (!unlocked) {
            learnLabel = "完成试炼以解锁";
        } else if (learnedLv == 0) {
            learnLabel = "学习技能 (消耗 " + nextCost + " 点)";
        } else {
            learnLabel = "升级 Lv." + (learnedLv + 1) + " (消耗 " + nextCost + " 点)";
        }
        learnBtn = new ButtonBounds(x + pad, btnY1, innerW, btnH);
        SkillScreenStyle.button(g, learnBtn.x(), learnBtn.y(), learnBtn.w(), learnBtn.h(),
                learnLabel, canLearn, learnBtn.contains(mouseX, mouseY),
                def.constellation().primaryColor());
        if (canLearn) {
            SkillScreenStyle.drawShimmerSweep(g, learnBtn.x(), learnBtn.y(), learnBtn.w(), learnBtn.h(),
                    SkillScreenStyle.GOLD_BRIGHT, timeSec * 0.38F);
        }

        boolean equipped = isActive
                ? data.activeLoadout().contains(def.id())
                : data.passiveLoadout().contains(def.id());
        boolean canEquipOrUnset = learnedLv > 0 && (equipped || (isActive
                ? data.activeLoadout().size() < data.activeSlotCount()
                : data.passiveLoadout().size() < data.passiveSlotCount()));

        int halfW = (innerW - 4) / 2;
        equipBtn = new ButtonBounds(x + pad, btnY2, halfW, btnH);
        String equipLabel = equipped ? "卸下技能" : (learnedLv > 0 ? "装备技能" : "未学习");
        SkillScreenStyle.button(g, equipBtn.x(), equipBtn.y(), equipBtn.w(), equipBtn.h(),
                equipLabel, canEquipOrUnset, equipBtn.contains(mouseX, mouseY),
                isActive ? 0xFFFF8866 : 0xFF4BE0B8);

        resetBtn = new ButtonBounds(x + pad + halfW + 4, btnY2, innerW - halfW - 4, btnH);
        SkillScreenStyle.button(g, resetBtn.x(), resetBtn.y(), resetBtn.w(), resetBtn.h(),
                "重置此技能", learnedLv > 0, resetBtn.contains(mouseX, mouseY),
                0xFFD46A6A);
    }

    boolean containsViewport(double mx, double my) {
        return mx >= viewportX && mx < viewportX + viewportW && my >= viewportY && my < viewportY + viewportH;
    }

    boolean handleScroll(double mx, double my, double scrollY) {
        if (!containsViewport(mx, my) || maxScroll <= 0) {
            return false;
        }
        targetScroll = Math.max(0.0F, Math.min(maxScroll, targetScroll + (float) (-scrollY * 22.0)));
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
        if (draggingScroll && button == 0 && maxScroll > 0 && viewportH > 0) {
            double delta = my - dragStartMouseY;
            float ratio = (float) delta / Math.max(1.0F, viewportH - 18.0F);
            targetScroll = Math.max(0.0F, Math.min(maxScroll, dragStartOffset + ratio * maxScroll));
            scrollOffset = targetScroll;
            return true;
        }
        return false;
    }

    boolean mouseReleased(int button) {
        if (button == 0 && draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return false;
    }
}
