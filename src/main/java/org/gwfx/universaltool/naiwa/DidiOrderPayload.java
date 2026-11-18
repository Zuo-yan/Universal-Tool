package org.gwfx.universaltool.naiwa;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModEntities;

import java.util.List;
import java.util.UUID;

/**
 * Client-to-server payload issued from the "滴滴代打" MCphone app to summon or command Naiwa bodyguards.
 */
public record DidiOrderPayload(int action) implements CustomPacketPayload {

    public static final int ACTION_SUMMON_ONE = 0;
    public static final int ACTION_SUMMON_THREE = 1;
    public static final int ACTION_MODE_FOLLOW = 2;
    public static final int ACTION_MODE_PATROL = 3;
    public static final int ACTION_ULTIMATE_LAUGH = 4;
    public static final int ACTION_DISMISS_ALL = 5;

    private static final double ORDER_SEARCH_RADIUS = 96.0D;
    private static final double FOLLOW_TELEPORT_DIST_SQR = 20.0D * 20.0D;
    private static final double AIRDROP_HEIGHT = 6.0D;

    public static final Type<DidiOrderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "didi_order"));

    public static final StreamCodec<ByteBuf, DidiOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DidiOrderPayload::action,
            DidiOrderPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DidiOrderPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                executeOrder(player, payload.action());
            }
        });
    }

    /**
     * Executes a Didi order on the server for {@code player}. Exposed for both payload handling and GameTests.
     */
    public static void executeOrder(ServerPlayer player, int action) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        switch (action) {
            case ACTION_SUMMON_ONE -> handleSummonOne(player, level);
            case ACTION_SUMMON_THREE -> handleSummonThree(player, level);
            case ACTION_MODE_FOLLOW -> handleSetFollowMode(player, level);
            case ACTION_MODE_PATROL -> handleSetPatrolMode(player, level);
            case ACTION_ULTIMATE_LAUGH -> handleUltimateLaugh(player, level);
            case ACTION_DISMISS_ALL -> handleDismissAll(player, level);
            default -> {
            }
        }
    }

    private static void handleSummonOne(ServerPlayer player, ServerLevel level) {
        Vec3 lookHoriz = horizontalLook(player);
        double spawnX = player.getX() + lookHoriz.x * 3.0D;
        double spawnZ = player.getZ() + lookHoriz.z * 3.0D;
        spawnAirdropNaiwa(player, level, spawnX, spawnZ);
        playAirdropSound(level, spawnX, player.getY() + 2.0D, spawnZ);
        player.displayClientMessage(Component.translatable("message.universal_tool.didi.summon_one"), true);
    }

    private static void handleSummonThree(ServerPlayer player, ServerLevel level) {
        double baseAngle = Math.atan2(horizontalLook(player).z, horizontalLook(player).x);
        double radius = 3.5D;
        for (int i = 0; i < 3; i++) {
            double angle = baseAngle + i * (Math.PI * 2.0D / 3.0D);
            double spawnX = player.getX() + Math.cos(angle) * radius;
            double spawnZ = player.getZ() + Math.sin(angle) * radius;
            spawnAirdropNaiwa(player, level, spawnX, spawnZ);
        }
        playAirdropSound(level, player.getX(), player.getY() + 2.0D, player.getZ());
        player.displayClientMessage(Component.translatable("message.universal_tool.didi.summon_three"), true);
    }

    private static void handleSetFollowMode(ServerPlayer player, ServerLevel level) {
        List<NaiwaEntity> owned = findOwnedNaiwas(player, level);
        if (owned.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.universal_tool.didi.none_nearby"), true);
            return;
        }
        for (NaiwaEntity naiwa : owned) {
            naiwa.setOrderMode(NaiwaEntity.MODE_FOLLOW);
            if (naiwa.distanceToSqr(player) > FOLLOW_TELEPORT_DIST_SQR) {
                naiwa.teleportNearOwner(player);
            }
        }
        player.displayClientMessage(
                Component.translatable("message.universal_tool.didi.mode_follow", owned.size()), true);
    }

    private static void handleSetPatrolMode(ServerPlayer player, ServerLevel level) {
        List<NaiwaEntity> owned = findOwnedNaiwas(player, level);
        if (owned.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.universal_tool.didi.none_nearby"), true);
            return;
        }
        for (NaiwaEntity naiwa : owned) {
            naiwa.setOrderMode(NaiwaEntity.MODE_PATROL);
        }
        player.displayClientMessage(
                Component.translatable("message.universal_tool.didi.mode_patrol", owned.size()), true);
    }

    private static void handleUltimateLaugh(ServerPlayer player, ServerLevel level) {
        List<NaiwaEntity> owned = findOwnedNaiwas(player, level);
        if (owned.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.universal_tool.didi.none_nearby"), true);
            return;
        }
        for (NaiwaEntity naiwa : owned) {
            naiwa.triggerUltimateLaugh(NaiwaEntity.ULTIMATE_LAUGH_TICKS);
        }
        player.displayClientMessage(
                Component.translatable("message.universal_tool.didi.ultimate_laugh", owned.size()), true);
    }

    private static void handleDismissAll(ServerPlayer player, ServerLevel level) {
        List<NaiwaEntity> owned = findOwnedNaiwas(player, level);
        if (owned.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.universal_tool.didi.none_nearby"), true);
            return;
        }
        for (NaiwaEntity naiwa : owned) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    naiwa.getX(), naiwa.getY() + 1.1D, naiwa.getZ(), 12, 0.5D, 0.5D, 0.5D, 0.05D);
            level.sendParticles(ParticleTypes.HEART,
                    naiwa.getX(), naiwa.getY() + 1.8D, naiwa.getZ(), 5, 0.4D, 0.3D, 0.4D, 0.02D);
            level.sendParticles(ParticleTypes.CLOUD,
                    naiwa.getX(), naiwa.getY() + 0.9D, naiwa.getZ(), 10, 0.45D, 0.45D, 0.45D, 0.04D);
            naiwa.stopLaughingSound();
            naiwa.discard();
        }
        player.displayClientMessage(
                Component.translatable("message.universal_tool.didi.dismiss_all", owned.size()), true);
    }

    private static List<NaiwaEntity> findOwnedNaiwas(ServerPlayer player, ServerLevel level) {
        UUID ownerId = player.getUUID();
        AABB area = player.getBoundingBox().inflate(ORDER_SEARCH_RADIUS);
        return level.getEntitiesOfClass(NaiwaEntity.class, area,
                naiwa -> naiwa.isAlive() && ownerId.equals(naiwa.getOwnerUuid()));
    }

    private static void spawnAirdropNaiwa(ServerPlayer player, ServerLevel level, double x, double z) {
        NaiwaEntity naiwa = ModEntities.NAIWA.get().create(level);
        if (naiwa == null) {
            return;
        }
        double spawnY = resolveAirdropY(level, naiwa, x, player.getY(), z);
        float yaw = Mth.wrapDegrees(player.getYRot() + 180.0F);
        naiwa.moveTo(x, spawnY, z, yaw, 0.0F);
        naiwa.yBodyRot = yaw;
        naiwa.yHeadRot = yaw;
        naiwa.setOwnerUuid(player.getUUID());
        naiwa.setOrderMode(NaiwaEntity.MODE_FOLLOW);
        naiwa.markAirdropSpawn();
        level.addFreshEntity(naiwa);

        level.sendParticles(ParticleTypes.CLOUD, x, spawnY + 0.8D, z, 18, 0.65D, 0.45D, 0.65D, 0.06D);
        level.sendParticles(ParticleTypes.FIREWORK, x, spawnY + 1.0D, z, 14, 0.55D, 0.55D, 0.55D, 0.08D);
        naiwa.startLaughing(NaiwaEntity.DEFAULT_LAUGH_TICKS, false);
    }

    private static double resolveAirdropY(
            ServerLevel level, NaiwaEntity naiwa, double x, double playerY, double z) {
        for (double offset = AIRDROP_HEIGHT; offset >= 0.5D; offset -= 1.0D) {
            double candidateY = playerY + offset;
            AABB box = naiwa.getType().getDimensions().makeBoundingBox(x, candidateY, z);
            if (level.noCollision(naiwa, box)) {
                return candidateY;
            }
        }
        return playerY + 0.5D;
    }

    private static void playAirdropSound(ServerLevel level, double x, double y, double z) {
        level.playSound(null, x, y, z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.9F, 1.15F);
        level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 1.25F);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.35F, 1.6F);
    }

    private static Vec3 horizontalLook(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horiz = new Vec3(look.x, 0.0D, look.z);
        if (horiz.lengthSqr() > 1.0E-5D) {
            return horiz.normalize();
        }
        return new Vec3(0.0D, 0.0D, 1.0D);
    }
}
