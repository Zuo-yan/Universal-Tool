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
 * 「星典图鉴」全览浮层：支持按「星座 / 已装配」与「全部 / 仅主动 / 仅被动」双维度组合筛选，
 * 平滑阻尼滚动展示全部 32 技能的完整数值、试炼条件与觉醒质变。
 */
final class SkillCodexModal {
    private boolean open = false;
    private int filterTab = 0; // 0=全部, 1=已装配, 2..5=四大星座
    private int typeFilter = 0; // 0=全部类型, 1=仅主动, 2=仅被动
    private float scrollOffset = 0.0F;
    private float targetScroll = 0.0F;
    private int maxScroll = 0;
    private long openedAtMillis = 0L;

    private int modalX, modalY, modalW, modalH;
    private int listX, listY, listW, listH;

    private final List<TabHit> tabHits = new ArrayList<>();
    private final List<TabHit> typeHits = new ArrayList<>();
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
        scrollOffset = 0.0F;
        targetScroll = 0.0F;
        openedAtMillis = System.currentTimeMillis();
    }

    void close() {
        open = false;
    }

    void render(GuiGraphics g, Font font, PlayerSkillData data, int screenW, int screenH, int mouseX, int mouseY) {
        if (!open) return;
        float openT = SkillScreenStyle.easeOutCubic(Math.min(1.0F, (System.currentTimeMillis() - openedAtMillis) / 220.0F));
        scrollOffset += (targetScroll - scrollOffset) * 0.28F;
        if (Math.abs(targetScroll - scrollOffset) < 0.25F) scrollOffset = targetScroll;

        g.fill(0, 0, screenW, screenH, SkillScreenStyle.withAlpha(0x03050C, 0.80F * openT));

        modalW = Math.min(780, Math.max(300, screenW - 20));
        modalH = Math.min(500, Math.max(204, screenH - 20));
        modalX = (screenW - modalW) / 2;
        modalY = (screenH - modalH) / 2 + Math.round((1.0F - openT) * 14.0F);

        SkillScreenStyle.astrolabeWindowFrame(g, modalX, modalY, modalW, modalH);

        // 标题行 + 右侧类型快速筛选胶囊（全部 / 主动 / 被动）+ 关闭按钮
        int pad = 10;
        g.drawString(font, "✦ 星典图鉴", modalX + pad, modalY + 9, SkillScreenStyle.GOLD_BRIGHT, true);
        closeX = modalX + modalW - closeSize - 8;
        closeY = modalY + 6;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + closeSize && mouseY >= closeY && mouseY < closeY + closeSize;
        SkillScreenStyle.closeButton(g, closeX, closeY, closeSize, closeHover);

        // 类型筛选胶囊（放在标题行右侧，零额外高度占用！）
        typeHits.clear();
        String[] typeLabels = {"全部", "◆主动", "●被动"};
        int[] typeAccents = {SkillScreenStyle.GOLD, 0xFFFF756B, 0xFF4BE0B8};
        int pillW = modalW >= 460 ? 40 : 32;
        int pillGap = 3;
        int pillsTotalW = typeLabels.length * pillW + (typeLabels.length - 1) * pillGap;
        int pillStartX = closeX - pillsTotalW - 8;
        for (int i = 0; i < typeLabels.length; i++) {
            int px = pillStartX + i * (pillW + pillGap);
            TabHit th = new TabHit(i, px, modalY + 6, pillW, 14);
            typeHits.add(th);
            SkillScreenStyle.filterPill(g, px, modalY + 6, pillW, 14, typeLabels[i],
                    typeFilter == i, th.contains(mouseX, mouseY), typeAccents[i]);
        }

        // 星座筛选标签栏
        tabHits.clear();
        int eqTotal = data.activeLoadout().size() + data.passiveLoadout().size();
        boolean wideTab = modalW >= 540;
        String[] labels = wideTab
                ? new String[]{"全部 (32)", "已装配 (" + eqTotal + ")", "破军·战魂", "玄武·壁垒", "天枢·星移", "巧匠·秘工"}
                : new String[]{"全部", "已配(" + eqTotal + ")", "破军", "玄武", "天枢", "巧匠"};
        int[] accents = {
                SkillScreenStyle.GOLD,
                0xFF56E6B8,
                SkillConstellation.POJUN.primaryColor(),
                SkillConstellation.XUANWU.primaryColor(),
                SkillConstellation.TIANSHU.primaryColor(),
                SkillConstellation.QIAOJIANG.primaryColor()
        };
        int tabY = modalY + 24;
        int tabH = 15;
        int tabGap = 3;
        int tabW = Math.max(38, (modalW - pad * 2 - tabGap * 5) / 6);
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
        listY = tabY + tabH + 5;
        listW = modalW - pad * 2;
        listH = modalY + modalH - listY - pad;
        int cardW = listW - 7;

        List<SkillDefinition> items = filteredSkills(data);
        cardHits.clear();

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int curY = listY - Math.round(scrollOffset);
        int totalH = 0;

        if (items.isEmpty()) {
            g.drawCenteredString(font, "当前筛选条件下暂无技能", listX + listW / 2, listY + 24, SkillScreenStyle.TEXT_MUTED);
        }

        for (SkillDefinition def : items) {
            int learned = data.learnedLevel(def.id());
            boolean unlocked = data.isUnlocked(def.id());
            boolean equipped = def.type().isActive()
                    ? data.activeLoadout().contains(def.id())
                    : data.passiveLoadout().contains(def.id());
            int accent = def.constellation().primaryColor();

            String name = Component.translatable(def.translationKey()).getString();
            String typeTag = def.type().isActive()
                    ? ("◆主动 · 冷却 " + (def.cooldownTicks() / 20) + "s"
                    + (ModSkills.AIR_STEP.equals(def.id()) ? " · 按住推进" : (def.charged() ? " · 蓄力" : "")))
                    : "●被动";
            String headerTitle = name + "  [" + typeTag + "]";

            String statusBadge = learned > 0
                    ? ("Lv." + learned + "/3" + (equipped ? " · 已装配" : ""))
                    : (unlocked ? "已解锁·未点亮" : "未解锁");
            int statusCol = equipped ? (def.type().isActive() ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.TEXT_CYAN)
                    : (learned > 0 ? SkillScreenStyle.TEXT_GREEN
                    : (unlocked ? SkillScreenStyle.TEXT_GOLD : SkillScreenStyle.TEXT_MUTED));

            int textW = cardW - 50;
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
            int cardH = Math.max(46, 20 + contentLines * 10 + 8);
            boolean visible = curY + cardH >= listY && curY <= listY + listH;
            if (visible) {
                boolean hover = mouseX >= listX && mouseX < listX + cardW
                        && mouseY >= Math.max(listY, curY) && mouseY < Math.min(listY + listH, curY + cardH);
                g.fill(listX, curY, listX + cardW, curY + cardH, hover ? 0xE618223E : 0xD80F162A);
                SkillScreenStyle.outline(g, listX, curY, cardW, cardH,
                        hover ? SkillScreenStyle.GOLD_BRIGHT : SkillScreenStyle.withAlpha(accent, 0.72F));
                // 左侧 2px 主被动色彩识别条
                g.fill(listX, curY + 1, listX + 2, curY + cardH - 1,
                        def.type().isActive() ? 0xFFFF756B : 0xFF4BE0B8);

                SkillScreenStyle.skillIcon(g, def.id(), listX + 6, curY + 6, 30, learned == 0 && !unlocked);

                int tx = listX + 42;
                int ty = curY + 6;
                int statusW = font.width(statusBadge);
                int maxTitleW = Math.max(60, cardW - 42 - statusW - 14);
                g.drawString(font, SkillScreenStyle.trim(font, headerTitle, maxTitleW), tx, ty,
                        learned >= 3 ? SkillScreenStyle.GOLD_BRIGHT : accent, true);
                g.drawString(font, statusBadge, listX + cardW - statusW - 6, ty, statusCol, false);
                ty += 11;

                for (FormattedCharSequence trl : trialLines) {
                    g.drawString(font, trl, tx, ty, SkillScreenStyle.TEXT_GOLD, false);
                    ty += 10;
                }
                for (FormattedCharSequence dl : descLines) {
                    g.drawString(font, dl, tx, ty, SkillScreenStyle.TEXT_WHITE, false);
                    ty += 10;
                }
                ty += 1;
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

            curY += cardH + 5;
            totalH += cardH + 5;
        }
        g.disableScissor();

        maxScroll = Math.max(0, totalH - listH);
        if (targetScroll > maxScroll) targetScroll = maxScroll;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        if (maxScroll > 0) {
            int barX = listX + listW - 3;
            g.fill(barX, listY, barX + 2, listY + listH, 0x66151E35);
            int thumbH = Math.max(18, Math.round(listH * (listH / (float) Math.max(1, totalH))));
            int travel = Math.max(1, listH - thumbH);
            int thumbY = listY + Math.round(travel * (scrollOffset / Math.max(1.0F, maxScroll)));
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, SkillScreenStyle.GOLD);
        }
    }

    private List<SkillDefinition> filteredSkills(PlayerSkillData data) {
        List<SkillDefinition> base;
        if (filterTab == 0) {
            base = List.copyOf(ModSkills.all());
        } else if (filterTab == 1) {
            base = new ArrayList<>();
            for (String id : data.activeLoadout()) {
                ModSkills.find(id).ifPresent(base::add);
            }
            for (String id : data.passiveLoadout()) {
                ModSkills.find(id).ifPresent(base::add);
            }
        } else {
            SkillConstellation c = SkillConstellation.values()[Math.min(SkillConstellation.values().length - 1, filterTab - 2)];
            base = ModSkills.byConstellation(c);
        }
        if (typeFilter == 0) {
            return base;
        }
        boolean wantActive = typeFilter == 1;
        List<SkillDefinition> result = new ArrayList<>();
        for (SkillDefinition def : base) {
            if (def.type().isActive() == wantActive) {
                result.add(def);
            }
        }
        return result;
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
        for (TabHit th : typeHits) {
            if (th.contains(mx, my)) {
                typeFilter = th.index();
                scrollOffset = 0.0F;
                targetScroll = 0.0F;
                return true;
            }
        }
        for (TabHit th : tabHits) {
            if (th.contains(mx, my)) {
                filterTab = th.index();
                scrollOffset = 0.0F;
                targetScroll = 0.0F;
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
            targetScroll = Math.max(0.0F, Math.min(maxScroll, targetScroll + (float) (-scrollY * 26.0)));
        }
        return true;
    }
}
