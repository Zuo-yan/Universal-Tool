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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 「星典图鉴」全览浮层：按星座筛选浏览全部 32 个技能的完整说明、Lv.1~Lv.3 数值、试炼条件与觉醒质变。
 */
final class SkillCodexModal {
    private boolean open = false;
    private int filterTab = 0; // 0=全部, 1..4=四大星座
    private int scrollOffset = 0;
    private int maxScroll = 0;

    private int modalX, modalY, modalW, modalH;
    private int listX, listY, listW, listH;

    private final List<TabHit> tabHits = new ArrayList<>();
    private final List<CardHit> cardHits = new ArrayList<>();
    private int closeX, closeY, closeSize = 15;

    private record TabHit(int index, int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private record CardHit(String skillId, int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    boolean isOpen() { return open; }

    void open() {
        open = true;
        scrollOffset = 0;
    }

    void close() {
        open = false;
    }

    void render(GuiGraphics g, Font font, PlayerSkillData data, int screenW, int screenH, int mouseX, int mouseY) {
        if (!open) return;
        g.fill(0, 0, screenW, screenH, 0xCC03050C);

        modalW = Math.min(760, Math.max(296, screenW - 24));
        modalH = Math.min(480, Math.max(200, screenH - 24));
        modalX = (screenW - modalW) / 2;
        modalY = (screenH - modalH) / 2;

        SkillScreenStyle.astrolabeWindowFrame(g, modalX, modalY, modalW, modalH);

        // 标题与内缩关闭按钮
        int pad = 10;
        g.drawString(font, "✦ 星典图鉴 · 技能全数值一览", modalX + pad, modalY + 9, SkillScreenStyle.GOLD_BRIGHT, true);
        closeX = modalX + modalW - closeSize - 10;
        closeY = modalY + 7;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + closeSize && mouseY >= closeY && mouseY < closeY + closeSize;
        SkillScreenStyle.closeButton(g, closeX, closeY, closeSize, closeHover);

        // 筛选标签栏
        tabHits.clear();
        String[] labels = {"全部 (32)", "破军·战魂", "玄武·壁垒", "天枢·星移", "巧匠·秘工"};
        int[] accents = {
                SkillScreenStyle.GOLD,
                SkillConstellation.POJUN.primaryColor(),
                SkillConstellation.XUANWU.primaryColor(),
                SkillConstellation.TIANSHU.primaryColor(),
                SkillConstellation.QIAOJIANG.primaryColor()
        };
        int tabY = modalY + 26;
        int tabH = 16;
        int tabGap = 4;
        int tabW = Math.max(44, (modalW - pad * 2 - tabGap * 4) / 5);
        for (int i = 0; i < labels.length; i++) {
            int tx = modalX + pad + i * (tabW + tabGap);
            TabHit th = new TabHit(i, tx, tabY, tabW, tabH);
            tabHits.add(th);
            boolean active = filterTab == i;
            SkillScreenStyle.button(g, tx, tabY, tabW, tabH, labels[i], true,
                    active || th.contains(mouseX, mouseY), active ? SkillScreenStyle.GOLD_BRIGHT : accents[i]);
        }

        // 列表裁切区
        listX = modalX + pad;
        listY = tabY + tabH + 6;
        listW = modalW - pad * 2;
        listH = modalY + modalH - listY - pad;
        int cardW = listW - 8;

        List<SkillDefinition> items = filteredSkills();
        cardHits.clear();

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int curY = listY - scrollOffset;
        int totalH = 0;

        for (SkillDefinition def : items) {
            int learned = data.learnedLevel(def.id());
            boolean unlocked = data.isUnlocked(def.id());
            int accent = def.constellation().primaryColor();

            String name = Component.translatable(def.translationKey()).getString();
            String typeTag = def.type().isActive()
                    ? ("主动 · 冷却 " + (def.cooldownTicks() / 20) + "s" + (def.charged() ? " · 蓄力" : ""))
                    : "被动";
            String headerTitle = name + "  [" + typeTag + "]";

            String statusBadge = learned > 0 ? ("Lv." + learned + "/3")
                    : (unlocked ? "已解锁·未点亮" : "未解锁");
            int statusCol = learned > 0 ? SkillScreenStyle.TEXT_GREEN
                    : (unlocked ? SkillScreenStyle.TEXT_GOLD : SkillScreenStyle.TEXT_MUTED);

            int textW = cardW - 52;
            String trialLine = (!unlocked && learned == 0)
                    ? ("试炼条件: " + Component.translatable(def.unlockKey()).getString())
                    : "";
            List<FormattedCharSequence> trialLines = trialLine.isEmpty() ? List.of()
                    : font.split(Component.literal(trialLine), textW);
            List<FormattedCharSequence> descLines = font.split(Component.translatable(def.descriptionKey()), textW);
            List<FormattedCharSequence> lvLines = new ArrayList<>();
            for (int lv = 1; lv <= 3; lv++) {
                String line = (lv == 3 ? "★ Lv.3: " : "Lv." + lv + ": ") + SkillStatFormatter.format(def.id(), lv);
                lvLines.addAll(font.split(Component.literal(line), textW));
            }
            List<FormattedCharSequence> awakeLines = font.split(
                    Component.literal("★ 星辉觉醒: " + Component.translatable(def.awakeningKey()).getString()), textW);

            int contentLines = trialLines.size() + descLines.size() + lvLines.size() + awakeLines.size();
            int cardH = Math.max(48, 22 + contentLines * 10 + 10);
            boolean visible = curY + cardH >= listY && curY <= listY + listH;
            if (visible) {
                boolean hover = mouseX >= listX && mouseX < listX + cardW
                        && mouseY >= Math.max(listY, curY) && mouseY < Math.min(listY + listH, curY + cardH);
                g.fill(listX, curY, listX + cardW, curY + cardH, hover ? 0xE618223E : 0xD80F162A);
                SkillScreenStyle.outline(g, listX, curY, cardW, cardH,
                        hover ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.withAlpha(accent, 0.72F));

                SkillScreenStyle.skillIcon(g, def.id(), listX + 6, curY + 6, 32, learned == 0 && !unlocked);

                int tx = listX + 44;
                int ty = curY + 6;
                int statusW = font.width(statusBadge);
                int maxTitleW = Math.max(60, cardW - 44 - statusW - 14);
                g.drawString(font, SkillScreenStyle.trim(font, headerTitle, maxTitleW), tx, ty,
                        learned >= 3 ? SkillScreenStyle.GOLD_BRIGHT : accent, true);
                g.drawString(font, statusBadge, listX + cardW - statusW - 6, ty, statusCol, false);
                ty += 12;

                for (FormattedCharSequence trl : trialLines) {
                    g.drawString(font, trl, tx, ty, SkillScreenStyle.TEXT_GOLD, false);
                    ty += 10;
                }
                for (FormattedCharSequence dl : descLines) {
                    g.drawString(font, dl, tx, ty, SkillScreenStyle.TEXT_WHITE, false);
                    ty += 10;
                }
                ty += 2;
                for (FormattedCharSequence ll : lvLines) {
                    g.drawString(font, ll, tx, ty, SkillScreenStyle.TEXT_BODY, false);
                    ty += 10;
                }
                for (FormattedCharSequence al : awakeLines) {
                    g.drawString(font, al, tx, ty, SkillScreenStyle.TEXT_GOLD, false);
                    ty += 10;
                }
                cardHits.add(new CardHit(def.id(), listX, Math.max(listY, curY), cardW,
                        Math.min(listY + listH, curY + cardH) - Math.max(listY, curY)));
            }

            curY += cardH + 6;
            totalH += cardH + 6;
        }
        g.disableScissor();

        maxScroll = Math.max(0, totalH - listH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        if (maxScroll > 0) {
            int barX = listX + listW - 4;
            g.fill(barX, listY, barX + 3, listY + listH, 0x66151E35);
            int thumbH = Math.max(20, Math.round(listH * (listH / (float) Math.max(1, totalH))));
            int travel = Math.max(1, listH - thumbH);
            int thumbY = listY + Math.round(travel * (scrollOffset / (float) maxScroll));
            g.fill(barX, thumbY, barX + 3, thumbY + thumbH, SkillScreenStyle.GOLD);
        }
    }

    private List<SkillDefinition> filteredSkills() {
        if (filterTab == 0) {
            return List.copyOf(ModSkills.all());
        }
        SkillConstellation c = SkillConstellation.values()[filterTab - 1];
        return ModSkills.byConstellation(c);
    }

    boolean mouseClicked(double mx, double my, int button, Consumer<String> onSelectSkill) {
        if (!open) return false;
        if (mx < modalX || mx >= modalX + modalW || my < modalY || my >= modalY + modalH) {
            close();
            return true;
        }
        if (mx >= closeX && mx < closeX + closeSize && my >= closeY && my < closeY + closeSize) {
            close();
            return true;
        }
        for (TabHit th : tabHits) {
            if (th.contains(mx, my)) {
                filterTab = th.index();
                scrollOffset = 0;
                return true;
            }
        }
        for (CardHit ch : cardHits) {
            if (ch.contains(mx, my)) {
                onSelectSkill.accept(ch.skillId());
                close();
                return true;
            }
        }
        return true;
    }

    boolean mouseScrolled(double mx, double my, double scrollY) {
        if (!open) return false;
        if (maxScroll > 0) {
            int step = (int) Math.round(-scrollY * 26.0);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + step));
        }
        return true;
    }
}
