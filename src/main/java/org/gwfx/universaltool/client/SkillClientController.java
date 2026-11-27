package org.gwfx.universaltool.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.CancelChargePayload;
import org.gwfx.universaltool.network.CastActiveSkillPayload;
import org.gwfx.universaltool.network.RequestSkillScreenPayload;
import org.gwfx.universaltool.skill.ModSkills;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillConstellation;
import org.gwfx.universaltool.skill.SkillDefinition;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 客户端技能输入控制与局内「动态星徽 HUD」。
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class SkillClientController {
    private static boolean wasCasting;
    private static boolean castInterrupted;
    private static long chargeStartedAt;
    private static long castBounceAt;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SkillKeybinds.SELECT);
        event.register(SkillKeybinds.CAST);
        event.register(SkillKeybinds.OPEN);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            ClientSkillCache.clear();
            wasCasting = false;
            castInterrupted = false;
            return;
        }

        if (minecraft.screen != null || minecraft.getOverlay() != null || !minecraft.isWindowActive()) {
            if (wasCasting) cancelCharge();
            return;
        }

        while (SkillKeybinds.OPEN.consumeClick()) {
            if (wasCasting) cancelCharge();
            if (!ClientSkillCache.isSystemEnabled()) {
                minecraft.player.displayClientMessage(Component.translatable("message.universal_tool.skills.disabled"), true);
                continue;
            }
            PacketDistributor.sendToServer(RequestSkillScreenPayload.INSTANCE);
        }

        if (!ClientSkillCache.isSystemEnabled()) {
            if (wasCasting) cancelCharge();
            return;
        }

        handleCastKey();
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            onInputPressed(InputConstants.getKey(event.getKey(), event.getScanCode()));
        }
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == GLFW.GLFW_PRESS
                && onInputPressed(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            event.setCanceled(true);
        }
    }

    private static boolean onInputPressed(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isDeadOrDying() || minecraft.screen != null
                || minecraft.getOverlay() != null || !minecraft.isWindowActive()) {
            return false;
        }
        if (SkillKeybinds.CAST.isActiveAndMatches(key)) castInterrupted = false;
        if (!SkillKeybinds.SELECT.isActiveAndMatches(key) || minecraft.options.keyPlayerList.isDown()
                || !ClientSkillCache.isSystemEnabled() || ClientSkillCache.data().activeLoadout().isEmpty()) {
            return false;
        }

        if (wasCasting) cancelCharge();
        // Opening a Screen clears the game's KeyMappings. The wheel owns the physical input's release.
        SkillWheelScreen wheel = new SkillWheelScreen(key, ClientSkillCache.data().selectedSkill());
        minecraft.setScreen(wheel);
        if (minecraft.screen != wheel) return false;

        // GLFW can restore the position from the previous wheel after it releases the cursor.
        var window = minecraft.getWindow();
        GLFW.glfwSetCursorPos(window.getWindow(), window.getScreenWidth() / 2.0, window.getScreenHeight() / 2.0);
        return true;
    }

    private static void handleCastKey() {
        boolean casting = SkillKeybinds.CAST.isDown();
        if (castInterrupted) {
            if (casting) return;
            castInterrupted = false;
        }
        if (casting && !wasCasting) {
            chargeStartedAt = System.currentTimeMillis();
            castBounceAt = chargeStartedAt;
            PacketDistributor.sendToServer(CastActiveSkillPayload.PRESS);
        } else if (!casting && wasCasting) {
            castBounceAt = System.currentTimeMillis();
            PacketDistributor.sendToServer(CastActiveSkillPayload.RELEASE);
        }
        wasCasting = casting;
    }

    private static void cancelCharge() {
        PacketDistributor.sendToServer(CancelChargePayload.INSTANCE);
        wasCasting = false;
        castInterrupted = true;
    }

    @SubscribeEvent
    public static void renderCurrent(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        if (!ClientSkillCache.isSystemEnabled() || minecraft.options.hideGui) return;

        PlayerSkillData data = ClientSkillCache.data();
        String id = data.selectedSkill();
        if (id.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        long now = System.currentTimeMillis();
        float bounceT = Math.max(0.0F, 1.0F - (now - castBounceAt) / 220.0F);
        int bounceY = -Math.round(bounceT * 2.5F);

        int slotSize = 24;
        int x = g.guiWidth() / 2 + 91 + 6;
        int y = g.guiHeight() - slotSize - 1 + bounceY;
        boolean showingChargeBar = false;

        SkillDefinition def = ModSkills.find(id).orElse(null);
        int accent = def != null ? def.constellation().primaryColor() : SkillScreenStyle.GOLD;
        boolean onCooldown = data.onCooldown(id);
        long remainingMillis = data.cooldownRemainingMillis(id);
        long seconds = (remainingMillis + 999) / 1000;

        // 深空鎏金极简主动主槽（不堆砌额外小图标与被动列表）
        g.fill(x, y, x + slotSize, y + slotSize, 0xE00A0E1D);
        int border = onCooldown ? 0xFF34405E : (data.isAwakened(id) ? SkillScreenStyle.GOLD_BRIGHT : accent);
        SkillScreenStyle.outline(g, x, y, slotSize, slotSize, border);
        if (!onCooldown) {
            g.fill(x + 1, y + 1, x + 4, y + 2, SkillScreenStyle.GOLD_BRIGHT);
            g.fill(x + slotSize - 4, y + 1, x + slotSize - 1, y + 2, SkillScreenStyle.GOLD_BRIGHT);
        }

        SkillScreenStyle.skillIcon(g, id, x + 2, y + 2, 20, onCooldown);

        if (onCooldown) {
            long totalMillis = def != null ? def.cooldownTicks() * 50L : 1000L;
            int tianshuRes = data.resonanceTier(SkillConstellation.TIANSHU);
            float mult = 1.0F;
            if (tianshuRes >= 1) mult -= 0.18F;
            if (tianshuRes >= 3) mult -= 0.15F;
            if (data.passiveLoadout().contains(ModSkills.SPATIAL_ATTUNEMENT)) {
                mult -= 0.05F * data.learnedLevel(ModSkills.SPATIAL_ATTUNEMENT);
            }
            totalMillis = Math.max(500L, Math.round(totalMillis * Math.max(0.35F, mult)));
            float cdRatio = Math.max(0.0F, Math.min(1.0F, remainingMillis / (float) Math.max(1, totalMillis)));
            int sweepH = Math.round(20 * cdRatio);
            if (sweepH > 0) {
                g.fill(x + 2, y + 2 + (20 - sweepH), x + 22, y + 22, 0x9905070E);
            }
            String cdStr = seconds > 0 ? (seconds + "s") : "0s";
            g.drawCenteredString(minecraft.font, cdStr, x + slotSize / 2, y + 8, SkillScreenStyle.TEXT_RED);
        } else {
            String key = SkillKeybinds.CAST.getTranslatedKeyMessage().getString();
            int kw = minecraft.font.width(key);
            g.drawString(minecraft.font, key, x + slotSize - kw - 2, y + slotSize - 9, SkillScreenStyle.GOLD_BRIGHT, true);
        }

        // 蓄力进度条
        boolean allowAirStepBar = !ModSkills.AIR_STEP.equals(id) || !minecraft.player.onGround();
        if (def != null && (def.charged() || ModSkills.AIR_STEP.equals(id)) && allowAirStepBar && wasCasting && !onCooldown) {
            int chargeTicks = ModSkills.AIR_STEP.equals(id)
                    ? org.gwfx.universaltool.skill.SkillManager.airStepDurationTicks(data.learnedLevel(id))
                    : def.chargeTicks();
            long elapsed = now - chargeStartedAt;
            float ratio = Math.max(0.0F, Math.min(1.0F, elapsed / (chargeTicks * 50.0F)));
            int barW = Math.round(slotSize * ratio);
            g.fill(x, y - 5, x + slotSize, y - 2, 0xDD0A0E1D);
            g.fill(x, y - 5, x + barW, y - 2, SkillScreenStyle.TEXT_CYAN);
            showingChargeBar = true;
        }

        // 经验脉冲微条（获得经验后短暂浮现 3.2 秒）
        float xpAlpha = SkillToastOverlay.xpPulseAlpha();
        if (xpAlpha > 0.02F) {
            int nextXp = data.experienceForNextLevel();
            float xpRatio = nextXp == 0 ? 1.0F : Math.min(1.0F, data.experience() / (float) nextXp);
            int barY = showingChargeBar ? (y - 9) : (y - 4);
            int barW = slotSize;
            g.fill(x, barY, x + barW, barY + 2, SkillScreenStyle.withAlpha(0x0A0E1D, xpAlpha));
            int filled = Math.round(barW * xpRatio);
            if (filled > 0) {
                g.fill(x, barY, x + filled, barY + 2, SkillScreenStyle.withAlpha(SkillScreenStyle.GOLD_BRIGHT, xpAlpha));
            }
        }
    }

    private SkillClientController() {}
}
