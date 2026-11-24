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
import java.util.Iterator;
import java.util.List;

/**
 * 中央自由星图画布渲染器：处理视差星空、旋转观星仪核心、星座连线流光、32 颗星辰节点与点亮爆发动画。
 */
final class SkillStarMapRenderer {
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

    void spawnBurst(float mapX, float mapY, int color) {
        bursts.add(new BurstRing(mapX, mapY, color));
    }

    void render(GuiGraphics g, PlayerSkillData data, int x, int y, int w, int h,
                float camX, float camY, float zoom, float openProgress,
                String selectedId, String hoveredId, float timeSec) {
        g.enableScissor(x, y, x + w, y + h);
        SkillScreenStyle.drawStarfieldBackground(g, x, y, w, h, timeSec, camX, camY);

        float effectiveZoom = zoom * (0.78F + 0.22F * SkillScreenStyle.easeOutCubic(openProgress));
        int originX = x + w / 2 - Math.round(camX * effectiveZoom);
        int originY = y + h / 2 - Math.round(camY * effectiveZoom);

        // 1. 象限星座微光底盘与文字水印
        Font font = Minecraft.getInstance().font;
        for (SkillConstellation c : SkillConstellation.values()) {
            int dx = (c == SkillConstellation.POJUN || c == SkillConstellation.XUANWU) ? 1 : -1;
            int dy = (c == SkillConstellation.XUANWU || c == SkillConstellation.QIAOJIANG) ? 1 : -1;
            int labelX = originX + Math.round(dx * 170 * effectiveZoom);
            int labelY = originY + Math.round(dy * 228 * effectiveZoom);
            String cname = Component.translatable(c.translationKey()).getString();
            int res = data.resonanceTier(c);
            String badge = res == 2 ? cname + " ★4" : (res == 1 ? cname + " ★2" : cname);
            g.drawCenteredString(font, badge, labelX, labelY,
                    SkillScreenStyle.withAlpha(c.primaryColor(), res > 0 ? 0.92F : 0.45F));
        }

        // 2. 旋转双层观星仪中心星环
        float rOuter = 54.0F * effectiveZoom;
        float rInner = 34.0F * effectiveZoom;
        SkillScreenStyle.drawAstrolabeRing(g, originX, originY, rOuter,
                SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD, 0.55F), 48, timeSec * 0.14F);
        SkillScreenStyle.drawAstrolabeRing(g, originX, originY, rInner,
                SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_BRIGHT, 0.72F), 24, -timeSec * 0.22F);

        // 四象限连接中心星盘的起始引导星轨
        for (SkillDefinition def : ModSkills.all()) {
            int nx = originX + Math.round(def.mapX() * effectiveZoom);
            int ny = originY + Math.round(def.mapY() * effectiveZoom);
            if (!def.hasPrerequisite()) {
                boolean active = data.learned(def.id());
                boolean hl = def.id().equals(selectedId) || def.id().equals(hoveredId);
                SkillScreenStyle.drawStarTrackLine(g, originX, originY, nx, ny,
                        def.constellation().primaryColor(), def.constellation().glowColor(),
                        active, hl, timeSec);
            } else {
                SkillDefinition pre = ModSkills.find(def.prerequisiteId()).orElse(null);
                if (pre != null) {
                    int px = originX + Math.round(pre.mapX() * effectiveZoom);
                    int py = originY + Math.round(pre.mapY() * effectiveZoom);
                    boolean active = data.learned(pre.id()) && data.learned(def.id());
                    boolean ready = data.learned(pre.id()) && data.isUnlocked(def.id());
                    boolean hl = def.id().equals(selectedId) || def.id().equals(hoveredId);
                    SkillScreenStyle.drawStarTrackLine(g, px, py, nx, ny,
                            def.constellation().primaryColor(), def.constellation().glowColor(),
                            active || ready, hl, timeSec);
                }
            }
        }

        // 3. 中心星穹核心徽章
        StarRank rank = data.starRank();
        int coreSize = Math.max(18, Math.round(28 * effectiveZoom));
        SkillScreenStyle.recessedWell(g, originX - coreSize / 2, originY - coreSize / 2, coreSize, coreSize,
                rank.accentColor());
        SkillScreenStyle.drawStarGlyph(g, originX, originY, Math.max(4, coreSize / 4), rank.accentColor());
        int totalLearned = (int) ModSkills.all().stream().filter(d -> data.learned(d.id())).count();
        g.drawCenteredString(font, totalLearned + "/" + ModSkills.all().size(),
                originX, originY + coreSize / 2 + 4, SkillScreenStyle.TEXT_GOLD);

        // 4. 绘制全部 32 颗星辰节点
        for (SkillDefinition def : ModSkills.all()) {
            int nx = originX + Math.round(def.mapX() * effectiveZoom);
            int ny = originY + Math.round(def.mapY() * effectiveZoom);
            renderNode(g, font, data, def, nx, ny, effectiveZoom,
                    def.id().equals(selectedId), def.id().equals(hoveredId), timeSec);
        }

        // 5. 绘制点亮/升级瞬间的爆发星环粒子
        long now = System.currentTimeMillis();
        Iterator<BurstRing> it = bursts.iterator();
        while (it.hasNext()) {
            BurstRing burst = it.next();
            float t = (now - burst.startMillis) / 650.0F;
            if (t >= 1.0F) {
                it.remove();
                continue;
            }
            int bx = originX + Math.round(burst.mapX * effectiveZoom);
            int by = originY + Math.round(burst.mapY * effectiveZoom);
            float ease = SkillScreenStyle.easeOutCubic(t);
            float alpha = 1.0F - t;
            float ringR = (12.0F + 32.0F * ease) * effectiveZoom;
            SkillScreenStyle.drawAstrolabeRing(g, bx, by, ringR,
                    SkillScreenStyle.withAlpha(burst.color, alpha), 16, t * 2.5F);
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2.0 * k / 8.0 + t * 1.2F;
                int px = bx + Math.round((float) Math.cos(a) * ringR * 1.15F);
                int py = by + Math.round((float) Math.sin(a) * ringR * 1.15F);
                g.fill(px - 1, py - 1, px + 2, py + 2, SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_BRIGHT, alpha));
            }
        }

        g.disableScissor();
        SkillScreenStyle.outline(g, x, y, w, h, SkillScreenStyle.GOLD_DIM);
    }

    private static void renderNode(GuiGraphics g, Font font, PlayerSkillData data, SkillDefinition def,
                                   int cx, int cy, float zoom, boolean selected, boolean hovered, float timeSec) {
        int lv = data.learnedLevel(def.id());
        boolean awakened = lv >= def.maxLevel();
        boolean learned = lv > 0;
        boolean canLearn = data.canLearnOrUpgrade(def.id());
        boolean unlocked = data.isUnlocked(def.id());
        boolean preMet = data.isPrerequisiteMet(def);

        int baseSize = def.tier() == SkillTier.ULTIMATE ? 28
                : (def.tier() == SkillTier.INITIATE ? 26 : 22);
        int size = Math.max(16, Math.round(baseSize * zoom * (hovered ? 1.09F : 1.0F)));
        int x = cx - size / 2;
        int y = cy - size / 2;

        SkillConstellation c = def.constellation();
        int border;
        if (selected) {
            border = SkillScreenStyle.GOLD_BRIGHT;
        } else if (awakened) {
            border = 0xFFFFE57F;
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

        // 满级星辉觉醒双环光晕 / 可点亮脉动环
        if (awakened) {
            SkillScreenStyle.drawAstrolabeRing(g, cx, cy, size * 0.72F,
                    SkillScreenStyle.withAlpha(0xFFFFE57F, 0.75F), 12, timeSec * 1.1F);
        } else if (canLearn) {
            float pulseR = size * (0.64F + 0.08F * (float) Math.sin(timeSec * 4.2F));
            SkillScreenStyle.drawAstrolabeRing(g, cx, cy, pulseR,
                    SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_BRIGHT, 0.6F), 10, -timeSec * 0.8F);
        }

        if (selected) {
            SkillScreenStyle.outline(g, x - 2, y - 2, size + 4, size + 4, SkillScreenStyle.GOLD_BRIGHT);
        }

        SkillScreenStyle.recessedWell(g, x, y, size, size, border);
        int inner = size - 4;
        if (inner > 2) {
            g.blit(SkillScreenStyle.icon(def.id()), x + 2, y + 2, inner, inner, 0.0F, 0.0F, 32, 32, 32, 32);
        }
        if (!learned && !canLearn) {
            g.fill(x + 2, y + 2, x + size - 2, y + size - 2, unlocked ? 0x55080B14 : 0xA805070E);
        }

        // 右上角主/被徽标点
        int tagCol = def.type().isActive() ? 0xFFFF6E6E : 0xFF56E6B8;
        g.fill(x + size - 4, y + 1, x + size - 1, y + 4, tagCol);

        // 底部迷你星等钻石条（仅当已学或缩放够大时）
        if (learned) {
            int dotStart = cx - 9;
            SkillScreenStyle.masteryDiamonds(g, dotStart, y + size + 2, lv, def.maxLevel(), c.primaryColor());
        }

        if (hovered || selected) {
            String name = Component.translatable(def.translationKey()).getString();
            int nameY = learned ? (y + size + 9) : (y + size + 3);
            int nw = font.width(name);
            g.fill(cx - nw / 2 - 3, nameY - 1, cx + nw / 2 + 3, nameY + 9, 0xCC070A16);
            g.drawCenteredString(font, name, cx, nameY,
                    awakened ? SkillScreenStyle.GOLD_BRIGHT : (learned ? c.glowColor() : SkillScreenStyle.TEXT_WHITE));
        }
    }

    SkillDefinition hitTest(int canvasX, int canvasY, int canvasW, int canvasH,
                            float camX, float camY, float zoom, float openProgress,
                            double mouseX, double mouseY) {
        if (mouseX < canvasX || mouseX >= canvasX + canvasW || mouseY < canvasY || mouseY >= canvasY + canvasH) {
            return null;
        }
        float effectiveZoom = zoom * (0.78F + 0.22F * SkillScreenStyle.easeOutCubic(openProgress));
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
