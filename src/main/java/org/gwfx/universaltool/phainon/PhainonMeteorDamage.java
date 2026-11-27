package org.gwfx.universaltool.phainon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModItems;

@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class PhainonMeteorDamage {
    public static final float MINOR_DAMAGE = 24.0F, GIANT_DAMAGE = 144.0F;
    private static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "phainon_meteor"));
    private static final Map<ServerLevel, List<Volley>> ACTIVE = new HashMap<>();

    private PhainonMeteorDamage() {}

    static void start(ServerPlayer player, InteractionHand hand, Vec3 aim) {
        ServerLevel level = player.serverLevel();
        var packet = new MeteorShowerPayload(player.getId(), hand, level.getGameTime(), MeteorShower.plan(player, aim), false);
        float damageMultiplier = ModDataComponents.getPhainonWeaponState(player.getItemInHand(hand)).damageMultiplier();
        var volley = new Volley(player.getUUID(), packet, damageMultiplier);
        ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>()).add(volley);
        volley.syncObservers(level);
    }

    private static final class Volley {
        final UUID owner;
        final MeteorShowerPayload cast;
        final float baseDamageMultiplier;
        final Set<UUID> observers = new HashSet<>();
        int nextImpact;
        boolean giantHit;

        Volley(UUID owner, MeteorShowerPayload cast, float baseDamageMultiplier) {
            this.owner = owner;
            this.cast = cast;
            this.baseDamageMultiplier = baseDamageMultiplier;
        }

        private float currentDamageMultiplier(ServerPlayer caster) {
            var held = caster.getItemInHand(cast.hand());
            if (held.is(ModItems.QINCHEN_SWORD.get())) {
                return Math.max(baseDamageMultiplier, ModDataComponents.getPhainonWeaponState(held).damageMultiplier());
            }
            return baseDamageMultiplier;
        }

        void syncObservers(ServerLevel level) {
            observers.removeIf(id -> level.getPlayerByUUID(id) == null);
            for (ServerPlayer player : level.players()) {
                if (player.position().distanceToSqr(cast.shower().center()) <= MeteorShower.VISIBLE_RADIUS * MeteorShower.VISIBLE_RADIUS && observers.add(player.getUUID())) {
                    PacketDistributor.sendToPlayer(player, cast);
                }
            }
        }

        boolean finished(ServerLevel level) {
            long age = level.getGameTime() - cast.start();
            if (age >= PhainonMeteor.DURATION) return true;
            var player = level.getPlayerByUUID(owner);
            if (!(player instanceof ServerPlayer caster) || !caster.isAlive() || caster.isSpectator()
                    || caster.getId() != cast.casterId()) {
                var cancel = new MeteorShowerPayload(cast.casterId(), cast.hand(), cast.start(), cast.shower(), true);
                for (var watcher : level.players()) {
                    if (observers.contains(watcher.getUUID())) PacketDistributor.sendToPlayer(watcher, cancel);
                }
                return true;
            }
            syncObservers(level);
            while (nextImpact < MeteorShower.COUNT && age >= MeteorShower.impactTick(nextImpact)) {
                strike(caster, cast.shower().landings().get(nextImpact++), MeteorShower.MINOR_RADIUS,
                        MINOR_DAMAGE * currentDamageMultiplier(caster));
            }
            if (!giantHit && age >= PhainonMeteor.IMPACT) {
                giantHit = true;
                strike(caster, cast.shower().center(), MeteorShower.GIANT_RADIUS,
                        GIANT_DAMAGE * currentDamageMultiplier(caster));
            }
            return false;
        }
    }

    private static void strike(ServerPlayer caster, Vec3 point, double radius, float damage) {
        var type = caster.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE);
        var source = new DamageSource(type, null, caster, point);
        var bounds = new AABB(point, point).inflate(radius);
        for (var target : caster.serverLevel().getEntitiesOfClass(LivingEntity.class, bounds,
                target -> PhainonNormalSkillHandler.canHitTarget(caster, target) && !target.isAlliedTo(caster))) {
            if (target.getBoundingBox().distanceToSqr(point) <= radius * radius) target.hurt(source, damage);
        }
    }

    @SubscribeEvent
    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var volleys = ACTIVE.get(level);
        if (volleys != null) {
            volleys.removeIf(volley -> volley.finished(level));
            if (volleys.isEmpty()) ACTIVE.remove(level);
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) ACTIVE.remove(level);
    }
}
