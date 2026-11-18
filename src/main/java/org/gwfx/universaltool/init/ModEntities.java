package org.gwfx.universaltool.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.naiwa.NaiwaEntity;
import org.gwfx.universaltool.skill.LightningSpearProjectile;
import org.gwfx.universaltool.sportscar.SportsCarEntity;
import org.gwfx.universaltool.waypoint.WormholeEntity;
import org.gwfx.universaltool.wheelchair.AllTerrainWheelchairEntity;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, UniversalToolMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<LightningSpearProjectile>>
            LIGHTNING_SPEAR_PROJECTILE = ENTITIES.register("lightning_spear_projectile",
            () -> EntityType.Builder.<LightningSpearProjectile>of(
                            LightningSpearProjectile::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("lightning_spear_projectile"));

    public static final DeferredHolder<EntityType<?>, EntityType<WormholeEntity>>
            WORMHOLE = ENTITIES.register("wormhole",
            () -> EntityType.Builder.<WormholeEntity>of(
                            WormholeEntity::new, MobCategory.MISC)
                    .sized(1.30F, 2.15F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("wormhole"));

    public static final DeferredHolder<EntityType<?>, EntityType<AllTerrainWheelchairEntity>>
            ALL_TERRAIN_WHEELCHAIR = ENTITIES.register("all_terrain_wheelchair",
            () -> EntityType.Builder.<AllTerrainWheelchairEntity>of(
                            AllTerrainWheelchairEntity::new, MobCategory.MISC)
                    .sized(1.1F, 1.25F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("all_terrain_wheelchair"));

    public static final DeferredHolder<EntityType<?>, EntityType<NaiwaEntity>>
            NAIWA = ENTITIES.register("naiwa",
            () -> EntityType.Builder.<NaiwaEntity>of(
                            NaiwaEntity::new, MobCategory.CREATURE)
                    .sized(0.95F, 2.05F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("naiwa"));

    public static final DeferredHolder<EntityType<?>, EntityType<SportsCarEntity>>
            GULF_GT_CAR = ENTITIES.register("gulf_gt_car",
            () -> EntityType.Builder.<SportsCarEntity>of(
                            SportsCarEntity::new, MobCategory.MISC)
                    .sized(2.2F, 1.55F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("gulf_gt_car"));

    private ModEntities() {
    }
}
