package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.gwfx.universaltool.skill.ModSkills;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillConstellation;
import org.gwfx.universaltool.skill.SkillDefinition;
import org.gwfx.universaltool.skill.SkillTier;
import org.gwfx.universaltool.skill.StarRank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 中央自由星图画布渲染器：处理视差星空、旋转观星仪核心、主被动动态筛选过渡、
 * 选中节点前置链路双流星星轨、弹性缩放插值与点亮爆发星环动画。
 */
final class SkillStarMapRenderer {
    enum MapFilter {
        ALL,
        ACTIVE,
        PASSIVE,
        EQUIPPED
    }

    private static final class BurstRing {
        final float mapX;
        final float mapY;
        final int color;
        final long startMillis;

        BurstRing(float mapX, float mapY, int color) {
            this.mapX = mapX;
            this.mapY = mapY;
            this.color = color;
            this.startMillis = System.currentTimeMillis();
        }
    }

    private final List<BurstRing> bursts = new ArrayList<>();
    private final Map<String, Float> nodeScales = new HashMap<>();
    private final Map<String, Float> nodeAlphas = new HashMap<>();

    void spawnBurst(float mapX, float mapY, int color) {
        bursts.add(new BurstRing(mapX, mapY, color));
    }

    static boolean matchesFilter(PlayerSkillData data, SkillDefinition def, MapFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case ACTIVE -> def.type().isActive();
            case PASSIVE -> !def.type().isActive();
            case EQUIPPED -> def.type().isActive()
                    ? data.activeLoadout().contains(def.id())
                    : data.passiveLoadout().contains(def.id());
        };
    }

    void render(GuiGraphics g, PlayerSkillData data, int x, int y, int w, int h,
                float camX, float camY, float zoom, float openProgress,
                String selectedId, String hoveredId, MapFilter filter, float timeSec) {
        g.enableScissor(x, y, x + w, y + h);
        SkillScreenStyle.drawStarfieldBackground(g, x, y, w, h, timeSec, camX, camY);

        float effectiveZoom = zoom * (0.76F + 0.24F * SkillScreenStyle.easeOutCubic(openProgress));
        int originX = x + w / 2 - Math.round(camX * effectiveZoom);
        int originY = y + h / 2 - Math.round(camY * effectiveZoom);

        // 计算与选中星辰关联的整条父系前置链与直接子星，用于动态双流星连线高亮
        Set<String> chainIds = buildChainSet(selectedId);

        // 1. 象限星座微光水印（完整展示 ★2 / ★4 / ★6 共鸣）
        Font font = Minecraft.getInstance().font;
        for (SkillConstellation c : SkillConstellation.values()) {
            int dx = (c == SkillConstellation.POJUN || c == SkillConstellation.XUANWU) ? 1 : -1;
            int dy = (c == SkillConstellation.XUANWU || c == SkillConstellation.QIAOJIANG) ? 1 : -1;
            int labelX = originX + Math.round(dx * 170 * effectiveZoom);
            int labelY = originY + Math.round(dy * 228 * effectiveZoom);
            String cname = Component.translatable(c.translationKey()).getString();
            int res = data.resonanceTier(c);
            String badge = res >= 3 ? cname + " ★6"
                    : (res == 2 ? cname + " ★4" : (res == 1 ? cname + " ★2" : cname));
            float wmAlpha = res > 0 ? (0.78F + 0.14F * (float) Math.sin(timeSec * 2.2F)) : 0.44F;
            g.drawCenteredString(font, badge, labelX, labelY,
                    SkillScreenStyle.withAlpha(res >= 3 ? SkillScreenStyle.GOLD_BRIGHT : c.primaryColor(), wmAlpha));
        }

        // 2. 旋转双层观星仪中心星环
        float rOuter = 54.0F * effectiveZoom;
        float rInner = 34.0F * effectiveZoom;
        SkillScreenStyle.drawAstrolabeRing(g, originX, originY, rOuter,
                SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD, 0.52F), 48, timeSec * 0.14F);
        SkillScreenStyle.drawAstrolabeRing(g, originX, originY, rInner,
                SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_BRIGHT, 0.72F), 24, -timeSec * 0.22F);

        // 3. 星轨连线（根据主被动筛选与选中前置链平滑过渡明暗与双流星）
        for (SkillDefinition def : ModSkills.all()) {
            int nx = originX + Math.round(def.mapX() * effectiveZoom);
            int ny = originY + Math.round(def.mapY() * effectiveZoom);
            boolean defMatch = matchesFilter(data, def, filter);
            boolean inChain = chainIds.contains(def.id());
            boolean hl = def.id().equals(selectedId) || def.id().equals(hoveredId) || inChain;

            if (!def.hasPrerequisite()) {
                boolean active = data.learned(def.id());
                float trackAlpha = (filter == MapFilter.ALL || defMatch || hl) ? 1.0F : 0.25F;
                SkillScreenStyle.drawStarTrackLineAlpha(g, originX, originY, nx, ny,
                        def.constellation().primaryColor(), def.constellation().glowColor(),
                        active, hl, trackAlpha, timeSec);
            } else {
                SkillDefinition pre = ModSkills.find(def.prerequisiteId()).orElse(null);
                if (pre != null) {
                    int px = originX + Math.round(pre.mapX() * effectiveZoom);
                    int py = originY + Math.round(pre.mapY() * effectiveZoom);
                    boolean active = data.learned(pre.id()) && data.learned(def.id());
                    boolean ready = data.learned(pre.id()) && data.isUnlocked(def.id());
                    boolean preMatch = matchesFilter(data, pre, filter);
                    boolean linkChain = chainIds.contains(def.id()) && chainIds.contains(pre.id());
                    float trackAlpha = (filter == MapFilter.ALL || defMatch || preMatch || linkChain) ? 1.0F : 0.22F;
                    SkillScreenStyle.drawStarTrackLineAlpha(g, px, py, nx, ny,
                            def.constellation().primaryColor(), def.constellation().glowColor(),
                            active || ready, hl || linkChain, trackAlpha, timeSec);
                }
            }
        }

        // 4. 中心星穹核心徽章
        StarRank rank = data.starRank();
        int coreSize = Math.max(18, Math.round(28 * effectiveZoom));
        SkillScreenStyle.recessedWell(g, originX - coreSize / 2, originY - coreSize / 2, coreSize, coreSize,
                rank.accentColor());
        SkillScreenStyle.drawStarGlyph(g, originX, originY, Math.max(4, coreSize / 4), rank.accentColor());
        int totalLearned = (int) ModSkills.all().stream().filter(d -> data.learned(d.id())).count();
        g.drawCenteredString(font, totalLearned + "/" + ModSkills.all().size(),
                originX, originY + coreSize / 2 + 4, SkillScreenStyle.TEXT_GOLD);

        // 5. 绘制全部 32 颗星辰节点（先画淡出/非命中节点，再画命中/高亮节点保证层级清晰）
        List<SkillDefinition> backLayer = new ArrayList<>();
        List<SkillDefinition> frontLayer = new ArrayList<>();
        for (SkillDefinition def : ModSkills.all()) {
            boolean focus = def.id().equals(selectedId) || def.id().equals(hoveredId)
                    || matchesFilter(data, def, filter);
            if (focus) {
                frontLayer.add(def);
            } else {
                backLayer.add(def);
            }
        }
        for (SkillDefinition def : backLayer) {
            drawNodeAt(g, font, data, def, originX, originY, effectiveZoom, selectedId, hoveredId, filter, timeSec);
        }
        for (SkillDefinition def : frontLayer) {
            drawNodeAt(g, font, data, def, originX, originY, effectiveZoom, selectedId, hoveredId, filter, timeSec);
        }

        // 6. 点亮/升级/装配爆发星环粒子
        long now = System.currentTimeMillis();
        Iterator<BurstRing> it = bursts.iterator();
        while (it.hasNext()) {
            BurstRing burst = it.next();
            float t = (now - burst.startMillis) / 680.0F;
            if (t >= 1.0F) {
                it.remove();
                continue;
            }
            int bx = originX + Math.round(burst.mapX * effectiveZoom);
            int by = originY + Math.round(burst.mapY * effectiveZoom);
            float ease = SkillScreenStyle.easeOutCubic(t);
            float alpha = 1.0F - t;
            float ringR = (11.0F + 34.0F * ease) * effectiveZoom;
            SkillScreenStyle.drawAstrolabeRing(g, bx, by, ringR,
                    SkillScreenStyle.withAlpha(burst.color, alpha), 18, t * 2.8F);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2.0 * k / 8.0 + t * 1.4F;
                int px = bx + Math.round((float) Math.cos(a) * ringR * 1.16F);
                int py = by + Math.round((float) Math.sin(a) * ringR * 1.16F);
                g.fill(px - 1, py - 1, px + 2, py + 2,
                        SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_BRIGHT, alpha));
            }
        }

        g.disableScissor();
        SkillScreenStyle.outline(g, x, y, w, h, SkillScreenStyle.GOLD_DIM);
    }

    private void drawNodeAt(GuiGraphics g, Font font, PlayerSkillData data, SkillDefinition def,
                            int originX, int originY, float effectiveZoom,
                            String selectedId, String hoveredId, MapFilter filter, float timeSec) {
        int nx = originX + Math.round(def.mapX() * effectiveZoom);
        int ny = originY + Math.round(def.mapY() * effectiveZoom);
        boolean selected = def.id().equals(selectedId);
        boolean hovered = def.id().equals(hoveredId);
        boolean matched = matchesFilter(data, def, filter);
        renderNode(g, font, data, def, nx, ny, effectiveZoom, selected, hovered, matched, filter != MapFilter.ALL, timeSec);
    }

    private void renderNode(GuiGraphics g, Font font, PlayerSkillData data, SkillDefinition def,
                            int cx, int cy, float zoom, boolean selected, boolean hovered,
                            boolean matched, boolean filteringActive, float timeSec) {
        int lv = data.learnedLevel(def.id());
        boolean awakened = lv >= def.maxLevel();
        boolean learned = lv > 0;
        boolean canLearn = data.canLearnOrUpgrade(def.id());
        boolean unlocked = data.isUnlocked(def.id());
        boolean preMet = data.isPrerequisiteMet(def);
        boolean equipped = def.type().isActive()
                ? data.activeLoadout().contains(def.id())
                : data.passiveLoadout().contains(def.id());

        // 平滑缩放与筛选透明度插值
        float targetScale = hovered ? 1.13F : (selected ? 1.07F : ((filteringActive && matched) ? 1.04F : 1.0F));
        float curScale = nodeScales.getOrDefault(def.id(), 1.0F);
        curScale += (targetScale - curScale) * 0.26F;
        nodeScales.put(def.id(), curScale);

        float targetAlpha = (!filteringActive || matched || selected || hovered) ? 1.0F : 0.24F;
        float curAlpha = nodeAlphas.getOrDefault(def.id(), 1.0F);
        curAlpha += (targetAlpha - curAlpha) * 0.24F;
        nodeAlphas.put(def.id(), curAlpha);

        int baseSize = def.tier() == SkillTier.ULTIMATE ? 28
                : (def.tier() == SkillTier.INITIATE ? 25 : 22);
        int size = Math.max(15, Math.round(baseSize * zoom * curScale));
        int x = cx - size / 2;
        int y = cy - size / 2;

        SkillConstellation c = def.constellation();
        boolean isActiveSkill = def.type().isActive();
        int border;
        if (selected) {
            border = SkillScreenStyle.GOLD_BRIGHT;
        } else if (awakened) {
            border = 0xFFFFE57F;
        } else if (equipped) {
            border = isActiveSkill ? 0xFFFF9668 : 0xFF56F2C2;
        } else if (learned) {
            border = c.primaryColor();
        } else if (canLearn) {
            float pulse = 0.55F + 0.45F * (float) Math.sin(timeSec * 4.2F);
            border = SkillScreenStyle.lerpColor(c.primaryColor(), SkillScreenStyle.GOLD_BRIGHT, pulse);
        } else if (unlocked && preMet) {
            border = SkillScreenStyle.GOLD_DIM;
        } else {
            border = 0xFF2C3654;
        }

        // 筛选高亮脉动环 / 满级觉醒环 / 已装配流动光环 / 可点亮脉动环
        if (curAlpha > 0.5F) {
            if (filteringActive && matched) {
                int filterCol = isActiveSkill ? 0xFFFF7A6B : 0xFF56F2C2;
                float pulseR = size * (0.68F + 0.09F * (float) Math.sin(timeSec * 4.8F));
                SkillScreenStyle.drawAstrolabeRing(g, cx, cy, pulseR,
                        SkillScreenStyle.withAlpha(filterCol, 0.78F), 12, timeSec * 1.4F);
            } else if (awakened) {
                SkillScreenStyle.drawAstrolabeRing(g, cx, cy, size * 0.72F,
                        SkillScreenStyle.withAlpha(0xFFFFE57F, 0.75F), 12, timeSec * 1.1F);
            } else if (equipped) {
                int eqRing = isActiveSkill ? 0xFFFFAE6B : 0xFF56F2C2;
                SkillScreenStyle.drawAstrolabeRing(g, cx, cy, size * 0.68F,
                        SkillScreenStyle.withAlpha(eqRing, 0.62F), 10, -timeSec * 0.9F);
            } else if (canLearn) {
                float pulseR = size * (0.64F + 0.08F * (float) Math.sin(timeSec * 4.2F));
                SkillScreenStyle.drawAstrolabeRing(g, cx, cy, pulseR,
                        SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_BRIGHT, 0.62F), 10, -timeSec * 0.8F);
            }
        }

        if (selected) {
            float selPulse = 0.72F + 0.28F * (float) Math.sin(timeSec * 5.0F);
            SkillScreenStyle.outline(g, x - 2, y - 2, size + 4, size + 4,
                    SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_BRIGHT, selPulse));
        }

        SkillScreenStyle.recessedWell(g, x, y, size, size, border);
        int inner = size - 4;
        if (inner > 2) {
            g.blit(SkillScreenStyle.icon(def.id()), x + 2, y + 2, inner, inner, 0.0F, 0.0F, 32, 32, 32, 32);
        }
        if (!learned && !canLearn) {
            g.fill(x + 2, y + 2, x + size - 2, y + size - 2, unlocked ? 0x55080B14 : 0xA805070E);
        }

        // 非当前筛选类型的柔和遮罩（平滑渐暗退居背景）
        if (curAlpha < 0.98F) {
            int dimA = Math.round((1.0F - curAlpha) * 205.0F);
            g.fill(x, y, x + size, y + size, (dimA << 24) | 0x050812);
        }

        // 右上角清晰直观的「主动(绯红) / 被动(翡翠)」色标角标
        int typeCol = isActiveSkill ? 0xFFFF6E6E : 0xFF4BE0B8;
        g.fill(x + size - 5, y + 1, x + size - 1, y + 5, 0xCC060912);
        g.fill(x + size - 4, y + 1, x + size - 1, y + 4, SkillScreenStyle.withAlpha(typeCol, curAlpha));

        // 左上角已装配亮星角标
        if (equipped) {
            int eqCol = isActiveSkill ? 0xFFFFD066 : 0xFF66FFE0;
            g.fill(x + 1, y + 1, x + 5, y + 5, 0xCC060912);
            g.fill(x + 1, y + 1, x + 4, y + 4, SkillScreenStyle.withAlpha(eqCol, curAlpha));
        }

        // 底部迷你星等钻石条
        if (learned && curAlpha > 0.45F) {
            int dotStart = cx - 9;
            SkillScreenStyle.masteryDiamonds(g, dotStart, y + size + 2, lv, def.maxLevel(), c.primaryColor());
        }

        if (hovered || selected) {
            String name = Component.translatable(def.translationKey()).getString();
            String typePrefix = isActiveSkill ? "◆ " : "● ";
            int prefixCol = isActiveSkill ? 0xFFFF8880 : 0xFF56E6B8;
            int nameY = learned ? (y + size + 9) : (y + size + 3);
            int totalW = font.width(typePrefix + name);
            int startX = cx - totalW / 2;
            g.fill(startX - 3, nameY - 1, startX + totalW + 3, nameY + 9, 0xDC070A16);
            SkillScreenStyle.outline(g, startX - 3, nameY - 1, totalW + 6, 10,
                    SkillScreenStyle.withAlpha(selected ? SkillScreenStyle.GOLD : c.primaryColor(), 0.72F));
            g.drawString(font, typePrefix, startX, nameY, prefixCol, false);
            g.drawString(font, name, startX + font.width(typePrefix), nameY,
                    awakened ? SkillScreenStyle.GOLD_BRIGHT : (learned ? c.glowColor() : SkillScreenStyle.TEXT_WHITE), false);
        }
    }

    private static Set<String> buildChainSet(String selectedId) {
        Set<String> set = new HashSet<>();
        if (selectedId == null || selectedId.isEmpty()) return set;
        String cur = selectedId;
        int guard = 0;
        while (!cur.isEmpty() && guard++ < 12) {
            set.add(cur);
            SkillDefinition def = ModSkills.find(cur).orElse(null);
            if (def == null || !def.hasPrerequisite()) break;
            cur = def.prerequisiteId();
        }
        for (SkillDefinition def : ModSkills.all()) {
            if (selectedId.equals(def.prerequisiteId())) {
                set.add(def.id());
            }
        }
        return set;
    }

    SkillDefinition hitTest(int canvasX, int canvasY, int canvasW, int canvasH,
                            float camX, float camY, float zoom, float openProgress,
                            double mouseX, double mouseY) {
        if (mouseX < canvasX || mouseX >= canvasX + canvasW || mouseY < canvasY || mouseY >= canvasY + canvasH) {
            return null;
        }
        float effectiveZoom = zoom * (0.76F + 0.24F * SkillScreenStyle.easeOutCubic(openProgress));
        int originX = canvasX + canvasW / 2 - Math.round(camX * effectiveZoom);
        int originY = canvasY + canvasH / 2 - Math.round(camY * effectiveZoom);

        SkillDefinition best = null;
        double bestDist = Double.MAX_VALUE;
        for (SkillDefinition def : ModSkills.all()) {
            int nx = originX + Math.round(def.mapX() * effectiveZoom);
            int ny = originY + Math.round(def.mapY() * effectiveZoom);
            int half = Math.max(10, Math.round(15 * effectiveZoom));
            if (Math.abs(mouseX - nx) <= half && Math.abs(mouseY - ny) <= half) {
                double d = Math.hypot(mouseX - nx, mouseY - ny);
                if (d < bestDist) {
                    bestDist = d;
                    best = def;
                }
            }
        }
        return best;
    }
}
