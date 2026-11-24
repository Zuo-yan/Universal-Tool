package org.gwfx.universaltool.phainon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModSounds;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PhainonUltimateHandler {
    public static final int AWAKEN_TICK = 82;
    public static final int OPENING_TICKS = 112;
    /** 过场结束后的觉醒持续时长延长至 20 秒（400 ticks），便于实战与欣赏双翼及觉醒大剑 */
    public static final int TOTAL_TICKS = OPENING_TICKS + 400;

    private record Cast(InteractionHand hand, ItemStack sword, ServerLevel level, long startTick,
                        PhainonUltimateMotion motion) {}

    private static final Map<UUID, Cast> ACTIVE = new HashMap<>();

    private PhainonUltimateHandler() {}

    static boolean isActive(UUID casterId) {
        return ACTIVE.containsKey(casterId);
    }

    public static boolean isMovementLocked(ServerPlayer caster) {
        Cast cast = ACTIVE.get(caster.getUUID());
        return cast != null && cast.motion().player == caster && caster.isAlive() && !caster.isSpectator()
                && caster.serverLevel() == cast.level() && !caster.isPassenger()
                && cast.motion().ownsPosition();
    }

    public static InteractionResultHolder<ItemStack> tryStart(Level level, Player player, InteractionHand hand) {
        ItemStack sword = player.getItemInHand(hand);
        if (!ModDataComponents.getPhainonWeaponState(sword).canAwaken()
                || player.getCooldowns().isOnCooldown(sword.getItem()) || player.isSpectator() || !player.isAlive()
                || player.isPassenger() || player.isSleeping() || player.isFallFlying()) {
            return InteractionResultHolder.fail(sword);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.consume(sword);
        }
        if (!(player instanceof ServerPlayer caster) || isActive(caster.getUUID())
                || PhainonNormalSkillHandler.isCasting(caster.getUUID())) {
            return InteractionResultHolder.fail(sword);
        }

        ServerLevel serverLevel = caster.serverLevel();
        var motion = new PhainonUltimateMotion(caster);
        ACTIVE.put(caster.getUUID(), new Cast(hand, sword, serverLevel, serverLevel.getGameTime(), motion));
        caster.getCooldowns().addCooldown(sword.getItem(), OPENING_TICKS);
        PhainonSkillEffectPayload.broadcast(caster, PhainonSkillEffectPayload.Action.ULTIMATE_START,
                hand, TOTAL_TICKS, motion.height);
        play(caster, ModSounds.PHAINON_ULTIMATE_ACTIVATE.get(), 1.0F, 1.0F);
        play(caster, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.27F, 0.8F);
        return InteractionResultHolder.consume(sword);
    }

    @SubscribeEvent
    public static void holdPositionBeforeTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer caster && isMovementLocked(caster)) {
            ACTIVE.get(caster.getUUID()).motion().stopMomentum();
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)) return;
        Cast cast = ACTIVE.get(caster.getUUID());
        if (cast == null) return;
        if (!caster.isAlive() || caster.isSpectator() || caster.serverLevel() != cast.level()
                || cast.motion().locked() && (caster.isPassenger() || caster.isSleeping() || caster.isFallFlying())
                || caster.getItemInHand(cast.hand()) != cast.sword() || cast.sword().isEmpty()) {
            end(caster, true);
            return;
        }

        long age = cast.level().getGameTime() - cast.startTick();
        if (cast.motion().locked()) {
            if (!cast.motion().positionAt(Math.min(age, OPENING_TICKS))) {
                end(caster, true);
                return;
            }
            if (age >= OPENING_TICKS) cast.motion().release();
        }
        if (age >= TOTAL_TICKS) {
            end(caster, false);
        } else if (age == AWAKEN_TICK - 12) {
            play(caster, SoundEvents.BEACON_ACTIVATE, 0.33F, 0.75F);
        } else if (age >= AWAKEN_TICK && ModDataComponents.getPhainonWeaponState(cast.sword()).canAwaken()) {
            ModDataComponents.setPhainonWeaponState(cast.sword(), new PhainonWeaponState(0, true));
            caster.swing(cast.hand(), true);
            play(caster, ModSounds.PHAINON_ULTIMATE_RELEASE.get(), 1.0F, 1.0F);
            play(caster, SoundEvents.TRIDENT_THUNDER.value(), 0.30F, 1.25F);
            play(caster, SoundEvents.BEACON_POWER_SELECT, 0.42F, 0.85F);
            caster.displayClientMessage(Component.translatable("message.universal_tool.phainon.ultimate_awakened"), true);
        } else if (age == AWAKEN_TICK + 9) {
            play(caster, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.52F, 1.08F);
        }
    }

    public static void clearOrphanedForm(ServerPlayer owner, ItemStack sword) {
        Cast cast = ACTIVE.get(owner.getUUID());
        if (cast == null || cast.sword() != sword) {
            clearForm(sword);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer caster) end(caster, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer caster) end(caster, true);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer caster) end(caster, true);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        for (Cast cast : ACTIVE.values()) {
            cast.motion().release();
            clearForm(cast.sword());
        }
        ACTIVE.clear();
    }

    private static void end(ServerPlayer caster, boolean cancelled) {
        Cast cast = ACTIVE.remove(caster.getUUID());
        if (cast == null) return;
        cast.motion().release();
        clearForm(cast.sword());
        if (cancelled && caster.serverLevel() == cast.level()) {
            PhainonSkillEffectPayload.broadcast(caster, PhainonSkillEffectPayload.Action.ULTIMATE_CANCEL,
                    cast.hand(), TOTAL_TICKS);
        }
    }

    private static void clearForm(ItemStack sword) {
        PhainonWeaponState state = ModDataComponents.getPhainonWeaponState(sword);
        if (state.transformed()) {
            ModDataComponents.setPhainonWeaponState(sword, state.withTransformed(false));
        }
    }

    private static void play(ServerPlayer caster, SoundEvent sound, float volume, float pitch) {
        caster.serverLevel().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }
}
