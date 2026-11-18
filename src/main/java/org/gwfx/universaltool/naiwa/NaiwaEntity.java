package org.gwfx.universaltool.naiwa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Naiwa ("奶蛙") bodyguard summon entity for the "滴滴代打" MCphone app.
 * Defends its owner via follow/patrol modes and unleashes piercing multi-ring sonic boom waves
 * synchronized with its belly-laugh animation and MP3 audio.
 */
public class NaiwaEntity extends PathfinderMob implements OwnableEntity {

    public static final int MODE_FOLLOW = 0;
    public static final int MODE_PATROL = 1;

    public static final int DEFAULT_LAUGH_TICKS = 48;
    public static final int ULTIMATE_LAUGH_TICKS = 64;

    private static final int ATTACK_COOLDOWN_TICKS = 56;
    private static final int SONIC_WAVE_INTERVAL_TICKS = 8;
    private static final double ATTACK_TRIGGER_RANGE = 18.0D;
    private static final double TARGET_SEARCH_RANGE = 24.0D;
    private static final double SONIC_BEAM_RANGE = 16.0D;
    private static final double SONIC_BEAM_RADIUS = 2.6D;
    private static final double ULTIMATE_AOE_RADIUS = 6.0D;
    private static final float SONIC_DAMAGE = 14.0F;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(NaiwaEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_ORDER_MODE =
            SynchedEntityData.defineId(NaiwaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LAUGHING_TICKS =
            SynchedEntityData.defineId(NaiwaEntity.class, EntityDataSerializers.INT);

    private int attackCooldown;
    private boolean sonicCombatActive;
    private boolean ultimateMode;
    private boolean pendingAirdropLanding;
    @Nullable
    private UUID didiOrderId;

    // Client-side animation timer tracking smooth progress within a laugh burst
    private int clientLaughAnimTicks;
    private boolean wasLaughingClient;

    public NaiwaEntity(EntityType<? extends NaiwaEntity> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_ORDER_MODE, MODE_FOLLOW);
        builder.define(DATA_LAUGHING_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new NaiwaSonicCombatGoal(this));
        this.goalSelector.addGoal(2, new NaiwaFollowOwnerGoal(this, 1.15D, 6.0F, 24.0F));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new NaiwaOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NaiwaOwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new NaiwaHurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new NaiwaNearestEnemyGoal(this));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LAUGHING_TICKS.equals(key) && this.level().isClientSide) {
            boolean laughingNow = getLaughingTicks() > 0;
            if (laughingNow && !this.wasLaughingClient) {
                this.clientLaughAnimTicks = 0;
            } else if (!laughingNow) {
                this.clientLaughAnimTicks = 0;
            }
            this.wasLaughingClient = laughingNow;
        }
    }

    @Override
    public void tick() {
        if (didiOrderId != null && isAlive() && level() instanceof ServerLevel server
                && !DidiOrderBook.get(server).isServing(server, this)) {
            withdrawFromOrder();
            return;
        }
        super.tick();
        if (this.level().isClientSide) {
            if (isLaughing()) {
                if (!this.wasLaughingClient) {
                    this.clientLaughAnimTicks = 0;
                }
                this.clientLaughAnimTicks++;
                this.wasLaughingClient = true;
            } else {
                this.clientLaughAnimTicks = 0;
                this.wasLaughingClient = false;
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        if (this.pendingAirdropLanding && this.onGround()) {
            this.pendingAirdropLanding = false;
            spawnAirdropLandingParticles();
        }

        int laughing = getLaughingTicks();
        if (laughing > 0) {
            if (this.sonicCombatActive && laughing % SONIC_WAVE_INTERVAL_TICKS == 0) {
                fireSonicBoomWave(getTarget());
            } else if (!this.sonicCombatActive && laughing % 12 == 0) {
                spawnFriendlyLaughParticles();
            }

            int next = laughing - 1;
            this.entityData.set(DATA_LAUGHING_TICKS, next);
            if (next <= 0) {
                this.sonicCombatActive = false;
                this.ultimateMode = false;
            }
        }
    }

    /**
     * Starts the belly-laugh animation and sound for {@code durationTicks}.
     * When {@code sonicCombat} is true, periodic piercing sonic boom waves are fired.
     */
    public void startLaughing(int durationTicks, boolean sonicCombat) {
        int clamped = Math.max(1, durationTicks);
        this.entityData.set(DATA_LAUGHING_TICKS, clamped);
        this.sonicCombatActive = sonicCombat;
        if (!sonicCombat) {
            this.ultimateMode = false;
        }
        if (!this.level().isClientSide) {
            broadcastLaughSound(clamped, false);
        }
    }

    /**
     * Triggers the ultimate full-power belly-laugh sonic burst with default 64-tick duration.
     */
    public void triggerUltimateLaugh() {
        triggerUltimateLaugh(ULTIMATE_LAUGH_TICKS);
    }

    /**
     * Triggers the ultimate full-power belly-laugh sonic burst for the given duration.
     */
    public void triggerUltimateLaugh(int durationTicks) {
        this.ultimateMode = true;
        this.attackCooldown = Math.max(this.attackCooldown, ATTACK_COOLDOWN_TICKS);
        startLaughing(durationTicks, true);
    }

    /**
     * Sends a stop-sound packet to tracking players if this entity is currently tracked on the server.
     */
    public void stopLaughingSound() {
        this.entityData.set(DATA_LAUGHING_TICKS, 0);
        this.sonicCombatActive = false;
        this.ultimateMode = false;
        if (!this.level().isClientSide) {
            broadcastLaughSound(0, true);
        }
    }

    private void broadcastLaughSound(int durationTicks, boolean stop) {
        if (!(this.level() instanceof ServerLevel serverLevel) || serverLevel.players().isEmpty()) {
            return;
        }
        try {
            PacketDistributor.sendToPlayersTrackingEntity(
                    this, new NaiwaLaughSoundPayload(this.getId(), durationTicks, stop));
        } catch (RuntimeException ignored) {
            // Chunk map tracking may not be active in isolated unit/GameTest runs.
        }
    }

    /**
     * Fires a single sonic boom wave from Naiwa's mouth toward {@code target} (or forward if null),
     * dealing armor- and block-piercing sonic damage, knockback, and debuffs to hostile/valid targets.
     */
    public void fireSonicBoomWave(@Nullable LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 mouthPos = new Vec3(this.getX(), this.getY() + 1.5D, this.getZ());
        Vec3 dir = computeSonicBeamDirection(target, mouthPos);
        Vec3 beamEnd = mouthPos.add(dir.scale(SONIC_BEAM_RANGE));

        if (target != null && target.isAlive()) {
            facePosition(target.getX(), target.getEyeY(), target.getZ());
        }

        spawnSonicBeamParticles(serverLevel, mouthPos, dir);
        serverLevel.playSound(null, this.getX(), this.getY() + 1.5D, this.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.NEUTRAL, 0.45F, 1.35F);

        applySonicBoomDamage(serverLevel, mouthPos, beamEnd, dir, target);
    }

    private Vec3 computeSonicBeamDirection(@Nullable LivingEntity target, Vec3 mouthPos) {
        if (target != null && target.isAlive()) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(mouthPos);
            if (delta.lengthSqr() > 1.0E-5D) {
                return delta.normalize();
            }
        }
        Vec3 look = this.getLookAngle();
        return look.lengthSqr() > 1.0E-5D ? look.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    private void facePosition(double x, double y, double z) {
        double dx = x - this.getX();
        double dy = y - this.getEyeY();
        double dz = z - this.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horiz) * (180.0D / Math.PI)));
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.setXRot(Mth.clamp(pitch, -60.0F, 60.0F));
    }

    private void spawnSonicBeamParticles(ServerLevel serverLevel, Vec3 mouthPos, Vec3 dir) {
        int steps = 14;
        for (int i = 1; i <= steps; i++) {
            double dist = (SONIC_BEAM_RANGE / steps) * i;
            Vec3 p = mouthPos.add(dir.scale(dist));
            if (i % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            serverLevel.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.15D, 0.15D, 0.15D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.NOTE, p.x, p.y, p.z, 1, 0.25D, 0.25D, 0.25D, 1.0D);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 2, 0.18D, 0.18D, 0.18D, 0.01D);
        }

        if (this.ultimateMode) {
            for (int i = 0; i < 12; i++) {
                double angle = (Math.PI * 2.0D * i) / 12.0D;
                double rx = this.getX() + Math.cos(angle) * 2.8D;
                double rz = this.getZ() + Math.sin(angle) * 2.8D;
                serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, rx, this.getY() + 1.2D, rz,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private void applySonicBoomDamage(
            ServerLevel serverLevel,
            Vec3 mouthPos,
            Vec3 beamEnd,
            Vec3 dir,
            @Nullable LivingEntity primaryTarget
    ) {
        AABB searchBox = new AABB(mouthPos, beamEnd).inflate(SONIC_BEAM_RADIUS + 1.5D);
        if (this.ultimateMode) {
            searchBox = searchBox.minmax(this.getBoundingBox().inflate(ULTIMATE_AOE_RADIUS + 1.0D));
        }

        Set<UUID> hitIds = new HashSet<>();
        DamageSource sonicSource = this.damageSources().sonicBoom(this);
        List<LivingEntity> candidates = serverLevel.getEntitiesOfClass(LivingEntity.class, searchBox);

        for (LivingEntity candidate : candidates) {
            if (!hitIds.add(candidate.getUUID())) {
                continue;
            }
            if (!isValidSonicVictim(candidate, primaryTarget)) {
                continue;
            }

            Vec3 center = candidate.getBoundingBox().getCenter();
            double beamDist = distancePointToSegment(center, mouthPos, beamEnd);
            boolean inBeam = beamDist <= SONIC_BEAM_RADIUS;
            boolean inUltimateBurst = this.ultimateMode && candidate.distanceTo(this) <= ULTIMATE_AOE_RADIUS;

            if (!inBeam && !inUltimateBurst) {
                continue;
            }

            candidate.hurt(sonicSource, SONIC_DAMAGE);
            Vec3 away = candidate.position().subtract(this.position());
            Vec3 horizontalAway = new Vec3(away.x, 0.0D, away.z);
            Vec3 knockDir = horizontalAway.lengthSqr() > 1.0E-5D
                    ? horizontalAway.normalize()
                    : new Vec3(dir.x, 0.0D, dir.z).normalize();
            candidate.push(knockDir.x * 1.3D, 0.45D, knockDir.z * 1.3D);
            candidate.hurtMarked = true;

            candidate.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            candidate.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
        }
    }

    private boolean isValidSonicVictim(LivingEntity candidate, @Nullable LivingEntity primaryTarget) {
        if (!canAttackTarget(candidate)) {
            return false;
        }
        if (candidate == primaryTarget || candidate == getLastHurtByMob()) {
            return true;
        }
        return candidate instanceof Enemy;
    }

    private static double distancePointToSegment(Vec3 point, Vec3 segStart, Vec3 segEnd) {
        Vec3 ab = segEnd.subtract(segStart);
        double lenSq = ab.lengthSqr();
        if (lenSq < 1.0E-6D) {
            return point.distanceTo(segStart);
        }
        double t = Mth.clamp(point.subtract(segStart).dot(ab) / lenSq, 0.0D, 1.0D);
        Vec3 projection = segStart.add(ab.scale(t));
        return point.distanceTo(projection);
    }

    private void spawnFriendlyLaughParticles() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.NOTE,
                    getX(), getY() + 2.0D, getZ(), 2, 0.4D, 0.25D, 0.4D, 1.0D);
        }
    }

    private void spawnAirdropLandingParticles() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() + 0.15D, getZ(), 14, 0.65D, 0.1D, 0.65D, 0.05D);
            serverLevel.sendParticles(ParticleTypes.FIREWORK,
                    getX(), getY() + 0.4D, getZ(), 8, 0.45D, 0.2D, 0.45D, 0.04D);
        }
    }

    /**
     * Marks this entity as freshly airdropped so its first touchdown emits landing particles.
     */
    public void markAirdropSpawn() {
        this.pendingAirdropLanding = true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof SpawnEggItem) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            if (getOwnerUuid() == null) {
                setOwnerUuid(player.getUUID());
            }
            startLaughing(DEFAULT_LAUGH_TICKS, false);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HEART,
                        getX(), getY() + 1.9D, getZ(), 6, 0.45D, 0.3D, 0.45D, 0.02D);
                serverLevel.sendParticles(ParticleTypes.NOTE,
                        getX(), getY() + 2.1D, getZ(), 5, 0.5D, 0.3D, 0.5D, 1.0D);
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void die(DamageSource cause) {
        stopLaughingSound();
        super.die(cause);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other == null) {
            return false;
        }
        if (other == this || other instanceof NaiwaEntity) {
            return true;
        }
        UUID ownerId = getOwnerUuid();
        if (ownerId != null && other.getUUID().equals(ownerId)) {
            return true;
        }
        LivingEntity owner = getOwner();
        if (owner != null && other.isAlliedTo(owner)) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return canAttackTarget(target) && super.canAttack(target);
    }

    /**
     * Strict bodyguard target filter: never attacks owner, other Naiwas, allies,
     * creative/spectator players, or non-hostile players who did not attack the owner.
     */
    public boolean canAttackTarget(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target == this) {
            return false;
        }
        if (isAlliedTo(target)) {
            return false;
        }
        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            return isPlayerHostileToOwner(player);
        }
        return true;
    }

    private boolean isPlayerHostileToOwner(Player player) {
        LivingEntity owner = getOwner();
        if (owner == null) {
            return false;
        }
        return owner.getLastHurtByMob() == player
                && (owner.tickCount - owner.getLastHurtByMobTimestamp()) <= 200;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && !canAttackTarget(target)) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && isAlliedTo(attacker)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /**
     * Safely teleports this Naiwa near {@code owner} when in follow mode or summoned back.
     */
    public void teleportNearOwner(LivingEntity owner) {
        BlockPos origin = owner.blockPosition();
        for (int attempt = 0; attempt < 16; attempt++) {
            int dx = this.random.nextInt(7) - 3;
            int dy = this.random.nextInt(5) - 2;
            int dz = this.random.nextInt(7) - 3;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                continue;
            }
            BlockPos candidate = origin.offset(dx, dy, dz);
            if (canTeleportTo(candidate)) {
                moveAfterTeleport(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                return;
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = -2; dy <= 1; dy++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (canTeleportTo(candidate)) {
                        moveAfterTeleport(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                        return;
                    }
                }
            }
        }
        // Fallback when owner is hovering, over void, or on a tiny platform without surrounding solid blocks.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos candidate = origin.offset(dx, 0, dz);
                if (hasNoCollisionAt(candidate)) {
                    moveAfterTeleport(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                    return;
                }
            }
        }
        moveAfterTeleport(owner.getX(), owner.getY(), owner.getZ());
    }

    private void moveAfterTeleport(double x, double y, double z) {
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
        this.getNavigation().stop();
    }

    private boolean canTeleportTo(BlockPos pos) {
        BlockPos below = pos.below();
        if (!this.level().getBlockState(below).blocksMotion()) {
            return false;
        }
        return hasNoCollisionAt(pos);
    }

    private boolean hasNoCollisionAt(BlockPos pos) {
        AABB boxAtPos = this.getType().getDimensions().makeBoundingBox(
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        return this.level().noCollision(this, boxAtPos);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID ownerId = getOwnerUuid();
        if (ownerId != null) {
            tag.putUUID("OwnerUUID", ownerId);
        }
        tag.putInt("OrderMode", getOrderMode());
        if (didiOrderId != null) {
            tag.putUUID("DidiOrder", didiOrderId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerUUID")) {
            setOwnerUuid(tag.getUUID("OwnerUUID"));
        } else {
            setOwnerUuid(null);
        }
        if (tag.contains("OrderMode")) {
            setOrderMode(tag.getInt("OrderMode"));
        }
        didiOrderId = tag.hasUUID("DidiOrder") ? tag.getUUID("DidiOrder") : null;
    }

    @Nullable
    UUID getDidiOrderId() {
        return didiOrderId;
    }

    void setDidiOrderId(UUID id) {
        didiOrderId = id;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return didiOrderId == null && super.canUsePortal(allowPassengers);
    }

    void withdrawFromOrder() {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.9, getZ(), 10, 0.45, 0.45, 0.45, 0.05);
        }
        stopLaughingSound();
        discard();
    }

    public boolean isLaughing() {
        return getLaughingTicks() > 0;
    }

    public int getLaughingTicks() {
        return this.entityData.get(DATA_LAUGHING_TICKS);
    }

    public boolean isSonicCombatActive() {
        return this.sonicCombatActive;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return this.entityData.get(DATA_OWNER_UUID).orElse(null);
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return getOwnerUuid();
    }

    public void setOwnerUuid(@Nullable UUID ownerUuid) {
        this.entityData.set(DATA_OWNER_UUID, Optional.ofNullable(ownerUuid));
    }

    public int getOrderMode() {
        return this.entityData.get(DATA_ORDER_MODE);
    }

    public void setOrderMode(int mode) {
        int normalized = (mode == MODE_PATROL) ? MODE_PATROL : MODE_FOLLOW;
        this.entityData.set(DATA_ORDER_MODE, normalized);
    }

    /**
     * Smooth client-side animation time in seconds for {@code 捧腹大笑} (while laughing)
     * or {@code 呼吸待机} (while idle).
     */
    public float getLaughAnimTimeSeconds(float partialTick) {
        if (isLaughing()) {
            return (this.clientLaughAnimTicks + partialTick) / 20.0F;
        }
        return (this.tickCount + partialTick) / 20.0F;
    }

    private static final class NaiwaSonicCombatGoal extends Goal {
        private final NaiwaEntity naiwa;
        private int recalcPathTicks;

        private NaiwaSonicCombatGoal(NaiwaEntity naiwa) {
            this.naiwa = naiwa;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.naiwa.getTarget();
            return target != null && target.isAlive() && this.naiwa.canAttackTarget(target);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.recalcPathTicks = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.naiwa.getTarget();
            if (target == null || !target.isAlive() || !this.naiwa.canAttackTarget(target)) {
                return;
            }

            this.naiwa.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distSqr = this.naiwa.distanceToSqr(target);

            if (distSqr <= ATTACK_TRIGGER_RANGE * ATTACK_TRIGGER_RANGE) {
                this.naiwa.facePosition(target.getX(), target.getEyeY(), target.getZ());
                if (distSqr <= 10.0D * 10.0D) {
                    this.naiwa.getNavigation().stop();
                } else if (--this.recalcPathTicks <= 0) {
                    this.recalcPathTicks = 8;
                    this.naiwa.getNavigation().moveTo(target, 1.05D);
                }

                if (this.naiwa.attackCooldown <= 0
                        && (!this.naiwa.isLaughing() || !this.naiwa.isSonicCombatActive())) {
                    this.naiwa.attackCooldown = ATTACK_COOLDOWN_TICKS;
                    this.naiwa.startLaughing(DEFAULT_LAUGH_TICKS, true);
                }
            } else if (--this.recalcPathTicks <= 0) {
                this.recalcPathTicks = 8;
                this.naiwa.getNavigation().moveTo(target, 1.15D);
            }
        }
    }

    private static final class NaiwaFollowOwnerGoal extends Goal {
        private final NaiwaEntity naiwa;
        private final double speedModifier;
        private final float startDistance;
        private final float teleportDistance;
        @Nullable
        private LivingEntity owner;
        private int timeToRecalcPath;

        private NaiwaFollowOwnerGoal(
                NaiwaEntity naiwa, double speedModifier, float startDistance, float teleportDistance) {
            this.naiwa = naiwa;
            this.speedModifier = speedModifier;
            this.startDistance = startDistance;
            this.teleportDistance = teleportDistance;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.naiwa.getOrderMode() != MODE_FOLLOW) {
                return false;
            }
            LivingEntity resolvedOwner = this.naiwa.getOwner();
            if (resolvedOwner == null || resolvedOwner.isSpectator()) {
                return false;
            }
            double distSqr = this.naiwa.distanceToSqr(resolvedOwner);
            if (distSqr < (double) (this.startDistance * this.startDistance)) {
                return false;
            }
            if (this.naiwa.getTarget() != null
                    && distSqr <= (double) (this.teleportDistance * this.teleportDistance)) {
                return false;
            }
            this.owner = resolvedOwner;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.owner == null || !this.owner.isAlive() || this.naiwa.getOrderMode() != MODE_FOLLOW) {
                return false;
            }
            double distSqr = this.naiwa.distanceToSqr(this.owner);
            if (distSqr <= 16.0D) {
                return false;
            }
            return !this.naiwa.getNavigation().isDone()
                    || distSqr > (double) (this.teleportDistance * this.teleportDistance);
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            this.naiwa.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            this.naiwa.getLookControl().setLookAt(this.owner, 10.0F, (float) this.naiwa.getMaxHeadXRot());
            double distSqr = this.naiwa.distanceToSqr(this.owner);
            if (distSqr > (double) (this.teleportDistance * this.teleportDistance)) {
                this.naiwa.teleportNearOwner(this.owner);
                LivingEntity currentTarget = this.naiwa.getTarget();
                if (currentTarget != null
                        && currentTarget.distanceToSqr(this.owner) > (double) (this.teleportDistance * this.teleportDistance)) {
                    this.naiwa.setTarget(null);
                }
                return;
            }
            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = 10;
                this.naiwa.getNavigation().moveTo(this.owner, this.speedModifier);
            }
        }
    }

    private static final class NaiwaOwnerHurtByTargetGoal extends TargetGoal {
        private final NaiwaEntity naiwa;
        @Nullable
        private LivingEntity ownerLastHurtBy;
        private int timestamp;

        private NaiwaOwnerHurtByTargetGoal(NaiwaEntity naiwa) {
            super(naiwa, false);
            this.naiwa = naiwa;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.naiwa.getOwner();
            if (owner == null) {
                return false;
            }
            this.ownerLastHurtBy = owner.getLastHurtByMob();
            int hurtTime = owner.getLastHurtByMobTimestamp();
            if (hurtTime == this.timestamp || this.ownerLastHurtBy == null) {
                return false;
            }
            return this.naiwa.canAttackTarget(this.ownerLastHurtBy)
                    && this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.ownerLastHurtBy);
            LivingEntity owner = this.naiwa.getOwner();
            if (owner != null) {
                this.timestamp = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    private static final class NaiwaOwnerHurtTargetGoal extends TargetGoal {
        private final NaiwaEntity naiwa;
        @Nullable
        private LivingEntity ownerLastHurt;
        private int timestamp;

        private NaiwaOwnerHurtTargetGoal(NaiwaEntity naiwa) {
            super(naiwa, false);
            this.naiwa = naiwa;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.naiwa.getOwner();
            if (owner == null) {
                return false;
            }
            this.ownerLastHurt = owner.getLastHurtMob();
            int hurtTime = owner.getLastHurtMobTimestamp();
            if (hurtTime == this.timestamp || this.ownerLastHurt == null) {
                return false;
            }
            return this.naiwa.canAttackTarget(this.ownerLastHurt)
                    && this.canAttack(this.ownerLastHurt, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.mob.setTarget(this.ownerLastHurt);
            LivingEntity owner = this.naiwa.getOwner();
            if (owner != null) {
                this.timestamp = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    private static final class NaiwaHurtByTargetGoal extends HurtByTargetGoal {
        private final NaiwaEntity naiwa;

        private NaiwaHurtByTargetGoal(NaiwaEntity naiwa) {
            super(naiwa);
            this.naiwa = naiwa;
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = this.naiwa.getLastHurtByMob();
            if (attacker != null && !this.naiwa.canAttackTarget(attacker)) {
                return false;
            }
            return super.canUse();
        }
    }

    private static final class NaiwaNearestEnemyGoal extends NearestAttackableTargetGoal<Mob> {
        private NaiwaNearestEnemyGoal(NaiwaEntity naiwa) {
            super(naiwa, Mob.class, 0, false, false,
                    living -> living instanceof Enemy && naiwa.canAttackTarget(living));
        }

        @Override
        protected double getFollowDistance() {
            return TARGET_SEARCH_RANGE;
        }
    }
}
