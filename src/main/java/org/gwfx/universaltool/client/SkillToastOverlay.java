package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.SkillNotificationPayload;
import org.gwfx.universaltool.skill.ModSkills;
import org.gwfx.universaltool.skill.SkillDefinition;
import org.gwfx.universaltool.skill.StarRank;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 星辉仪式感通知横幅（Toast）与客户端视听反馈队列。
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class SkillToastOverlay {
    private static final Deque<ToastEntry> QUEUE = new ArrayDeque<>();
    private static ToastEntry active;
    private static long activeStartMillis;
    private static long lastXpPulseMillis;

    private record ToastEntry(
            SkillNotificationPayload.Kind kind,
            String title,
            String subtitle,
            int accentColor,
            String iconSkillId,
            long durationMillis
    ) {}

    private SkillToastOverlay() {}

    public static void clear() {
        QUEUE.clear();
        active = null;
        lastXpPulseMillis = 0;
    }

    public static float xpPulseAlpha() {
        long elapsed = System.currentTimeMillis() - lastXpPulseMillis;
        if (lastXpPulseMillis <= 0 || elapsed > 3200L) return 0.0F;
        if (elapsed < 200L) return elapsed / 200.0F;
        if (elapsed > 2400L) return Math.max(0.0F, 1.0F - (elapsed - 2400L) / 800.0F);
        return 1.0F;
    }

    public static void onNotification(SkillNotificationPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientSkillCache.isSystemEnabled()) return;

        switch (payload.kind()) {
            case XP_GAIN -> lastXpPulseMillis = System.currentTimeMillis();
            case LEVEL_UP -> {
                lastXpPulseMillis = System.currentTimeMillis();
                String keyName = SkillKeybinds.OPEN.isUnbound() ? "-" : SkillKeybinds.OPEN.getTranslatedKeyMessage().getString();
                QUEUE.addLast(new ToastEntry(
                        payload.kind(),
                        Component.translatable("toast.universal_tool.skill.level_up.title", payload.value()).getString(),
                        Component.translatable("toast.universal_tool.skill.level_up.sub", keyName).getString(),
                        0xFFF2C95A,
                        "",
                        3400L));
                playCelebration(mc, false);
            }
            case STAR_RANK_UP -> {
                lastXpPulseMillis = System.currentTimeMillis();
                StarRank rank = StarRank.forLevel(payload.value());
                String rankName = Component.translatable(rank.translationKey()).getString();
                QUEUE.addLast(new ToastEntry(
                        payload.kind(),
                        Component.translatable("toast.universal_tool.skill.star_rank.title", rankName).getString(),
                        Component.translatable("toast.universal_tool.skill.star_rank.sub",
                                rank.activeSlots(), rank.passiveSlots()).getString(),
                        rank.accentColor(),
                        "",
                        4500L));
                playCelebration(mc, true);
            }
            case SKILL_UNLOCKED -> {
                SkillDefinition def = ModSkills.find(payload.key()).orElse(null);
                if (def == null) return;
                String name = Component.translatable(def.translationKey()).getString();
                String keyName = SkillKeybinds.OPEN.isUnbound() ? "K" : SkillKeybinds.OPEN.getTranslatedKeyMessage().getString();
                QUEUE.addLast(new ToastEntry(
                        payload.kind(),
                        Component.translatable("toast.universal_tool.skill.unlocked.title", name).getString(),
                        Component.translatable("toast.universal_tool.skill.unlocked.sub", keyName).getString(),
                        def.constellation().primaryColor(),
                        def.id(),
                        3800L));
                mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.85F, 1.35F);
                spawnSpiralParticles(mc, 18);
            }
            case SKILL_AWAKENED -> {
                SkillDefinition def = ModSkills.find(payload.key()).orElse(null);
                if (def == null) return;
                String name = Component.translatable(def.translationKey()).getString();
                QUEUE.addLast(new ToastEntry(
                        payload.kind(),
                        Component.translatable("toast.universal_tool.skill.awakened.title", name).getString(),
                        Component.translatable(def.awakeningKey()).getString(),
                        0xFFFFE17D,
                        def.id(),
                        4400L));
                playCelebration(mc, true);
            }
        }
    }

    private static void playCelebration(Minecraft mc, boolean epic) {
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.PLAYER_LEVELUP, epic ? 0.85F : 0.65F, epic ? 1.15F : 1.0F);
        mc.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.75F, epic ? 1.5F : 1.25F);
        if (epic) {
            mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
        }
        spawnSpiralParticles(mc, epic ? 34 : 18);
    }

    private static void spawnSpiralParticles(Minecraft mc, int count) {
        if (mc.level == null || mc.player == null) return;
        double px = mc.player.getX();
        double py = mc.player.getY() + 0.3;
        double pz = mc.player.getZ();
        for (int i = 0; i < count; i++) {
            double angle = i * 0.55;
            double r = 0.6 + (i / (double) count) * 0.8;
            mc.level.addParticle(ParticleTypes.END_ROD,
                    px + Math.cos(angle) * r,
                    py + (i / (double) count) * 1.9,
                    pz + Math.sin(angle) * r,
                    0, 0.03, 0);
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientSkillCache.isSystemEnabled()) return;

        long now = System.currentTimeMillis();
        if (active != null && now - activeStartMillis >= active.durationMillis()) {
            active = null;
        }
        if (active == null && !QUEUE.isEmpty()) {
            active = QUEUE.pollFirst();
            activeStartMillis = now;
        }
        if (active == null) return;

        float elapsed = now - activeStartMillis;
        float dur = active.durationMillis();
        float enterT = Math.min(1.0F, elapsed / 260.0F);
        float exitT = elapsed > dur - 360.0F ? Math.max(0.0F, (dur - elapsed) / 360.0F) : 1.0F;
        float easeIn = 1.0F - (1.0F - enterT) * (1.0F - enterT) * (1.0F - enterT);
        float alpha = Math.min(easeIn, exitT);
        if (alpha <= 0.02F) return;

        GuiGraphics g = event.getGuiGraphics();
        int boxW = Math.min(260, Math.max(196, Math.max(mc.font.width(active.title()), mc.font.width(active.subtitle())) + 56));
        int boxH = 38;
        int x = (g.guiWidth() - boxW) / 2;
        int y = Math.round(-boxH + (boxH + 14) * easeIn);

        int bgAlpha = Math.round(0xD8 * alpha) << 24;
        int borderCol = (Math.round(0xFF * alpha) << 24) | (active.accentColor() & 0x00FFFFFF);

        // 两侧展开的星辉金线光带
        int wingW = Math.round(44 * easeIn);
        int wingCol = (Math.round(0x88 * alpha) << 24) | (active.accentColor() & 0x00FFFFFF);
        g.fill(x - wingW, y + boxH / 2, x, y + boxH / 2 + 1, wingCol);
        g.fill(x + boxW, y + boxH / 2, x + boxW + wingW, y + boxH / 2 + 1, wingCol);

        // 深空底板与鎏金双边框
        g.fill(x, y, x + boxW, y + boxH, bgAlpha | 0x0B1020);
        SkillScreenStyle.outline(g, x, y, boxW, boxH, borderCol);
        SkillScreenStyle.outline(g, x + 2, y + 2, boxW - 4, boxH - 4,
                (Math.round(0x70 * alpha) << 24) | 0xE8C26A);

        int textStartX = x + 12;
        if (!active.iconSkillId().isEmpty()) {
            ResourceLocation iconLoc = SkillScreenStyle.icon(active.iconSkillId());
            g.blit(iconLoc, x + 8, y + 7, 24, 24, 0.0F, 0.0F, 32, 32, 32, 32);
            textStartX = x + 38;
        } else {
            SkillScreenStyle.drawStarGlyph(g, x + 19, y + 19, 6, borderCol);
            textStartX = x + 32;
        }

        int titleCol = (Math.round(0xFF * alpha) << 24) | (active.accentColor() & 0x00FFFFFF);
        int subCol = (Math.round(0xEE * alpha) << 24) | 0xCBD5E1;
        g.drawString(mc.font, SkillScreenStyle.trim(mc.font, active.title(), boxW - (textStartX - x) - 8),
                textStartX, y + 8, titleCol, true);
        g.drawString(mc.font, SkillScreenStyle.trim(mc.font, active.subtitle(), boxW - (textStartX - x) - 8),
                textStartX, y + 21, subCol, false);
    }
}
