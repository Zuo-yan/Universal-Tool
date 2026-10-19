package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
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
import org.gwfx.universaltool.skill.SkillDefinition;

/**
 * Client-side skill input controller and in-game hotbar HUD.
 *
 * <p>Pressing the select key opens {@link SkillWheelScreen} for responsive, mouse-guided active skill selection.
 * Pressing/releasing the cast key handles instant and charged casting.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class SkillClientController {
    private static boolean wasCasting;
    private static long chargeStartedAt;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SkillKeybinds.SELECT);
        event.register(SkillKeybinds.CAST);
        event.register(SkillKeybinds.OPEN);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            if (wasCasting) cancelCharge();
            return;
        }

        if (minecraft.screen != null) {
            if (wasCasting) cancelCharge();
            return;
        }

        handleCastKey();

        while (SkillKeybinds.OPEN.consumeClick()) {
            if (wasCasting) cancelCharge();
            PacketDistributor.sendToServer(RequestSkillScreenPayload.INSTANCE);
        }

        if (SkillKeybinds.SELECT.isDown() && !minecraft.options.keyPlayerList.isDown()) {
            if (wasCasting) cancelCharge();
            PlayerSkillData data = ClientSkillCache.data();
            if (!data.activeLoadout().isEmpty()) {
                minecraft.setScreen(new SkillWheelScreen());
            }
        }
    }

    /** Sends a press edge to start a cast, and a release edge to finish a charge. */
    private static void handleCastKey() {
        boolean casting = SkillKeybinds.CAST.isDown();
        if (casting && !wasCasting) {
            chargeStartedAt = System.currentTimeMillis();
            PacketDistributor.sendToServer(CastActiveSkillPayload.PRESS);
        } else if (!casting && wasCasting) {
            PacketDistributor.sendToServer(CastActiveSkillPayload.RELEASE);
        }
        wasCasting = casting;
    }

    private static void cancelCharge() {
        PacketDistributor.sendToServer(CancelChargePayload.INSTANCE);
        wasCasting = false;
    }

    public static boolean wheelOpen() {
        return Minecraft.getInstance().screen instanceof SkillWheelScreen;
    }

    @SubscribeEvent
    public static void renderCurrent(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || wheelOpen()) return;
        PlayerSkillData data = ClientSkillCache.data();
        String id = data.selectedSkill();
        if (id.isEmpty()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int slotSize = 22;
        int x = graphics.guiWidth() / 2 + 91 + 5;
        int y = graphics.guiHeight() - slotSize;

        boolean onCooldown = data.onCooldown(id);
        long remainingMillis = data.cooldownRemainingMillis(id);
        long seconds = (remainingMillis + 999) / 1000;

        // Slot background & ornate frame
        graphics.fill(x, y, x + slotSize, y + slotSize, 0xDD120D18);
        int border = onCooldown ? 0xFF3D2C42 : 0xFFFFD15C;
        SkillScreenStyle.outline(graphics, x, y, slotSize, slotSize, border);
        if (!onCooldown) {
            graphics.fill(x + 1, y + 1, x + 3, y + 2, 0xFFFFEFA6);
            graphics.fill(x + slotSize - 3, y + 1, x + slotSize - 1, y + 2, 0xFFFFEFA6);
        }

        // Skill icon scaled to 18x18
        SkillScreenStyle.skillIcon(graphics, id, x + 2, y + 2, 18, onCooldown);

        if (onCooldown) {
            SkillDefinition def = ModSkills.find(id).orElse(null);
            long totalMillis = def != null ? def.cooldownTicks() * 50L : 1000L;
            float cdRatio = Math.max(0.0F, Math.min(1.0F, remainingMillis / (float) Math.max(1, totalMillis)));
            int sweepH = Math.round(18 * cdRatio);
            if (sweepH > 0) {
                graphics.fill(x + 2, y + 2 + (18 - sweepH), x + 20, y + 20, 0x88000000);
            }
            String cdStr = seconds > 0 ? (seconds + "s") : "0s";
            graphics.drawCenteredString(minecraft.font, cdStr, x + slotSize / 2, y + 7, 0xFFFF5555);
        } else {
            String key = SkillKeybinds.CAST.getTranslatedKeyMessage().getString();
            int kw = minecraft.font.width(key);
            graphics.drawString(minecraft.font, key, x + slotSize - kw - 2, y + slotSize - 8, 0xFFE0D5C0, true);
        }

        SkillDefinition definition = ModSkills.find(id).orElse(null);
        if (definition != null && (definition.charged() || ModSkills.AIR_STEP.equals(id)) && wasCasting && !onCooldown) {
            int chargeTicks = ModSkills.AIR_STEP.equals(id)
                    ? org.gwfx.universaltool.skill.SkillManager.airStepDurationTicks(data.learnedLevel(id))
                    : definition.chargeTicks();
            long elapsed = System.currentTimeMillis() - chargeStartedAt;
            float ratio = Math.max(0.0F, Math.min(1.0F, elapsed / (chargeTicks * 50.0F)));
            int barW = Math.round(slotSize * ratio);
            graphics.fill(x, y - 4, x + slotSize, y - 1, 0xCC100D18);
            graphics.fill(x, y - 4, x + barW, y - 2, 0xFF66CCFF);
            graphics.fill(x, y - 4, x + barW, y - 3, 0xFFFFFFFF);
        }
    }

    public static void forceCloseWheel() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SkillWheelScreen) {
            mc.setScreen(null);
        }
        wasCasting = false;
    }

    private SkillClientController() {
    }
}