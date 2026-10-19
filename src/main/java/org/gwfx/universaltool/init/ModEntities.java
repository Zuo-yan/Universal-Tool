package org.gwfx.universaltool.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.skill.LightningSpearProjectile;

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

    private ModEntities() {
    }
}
