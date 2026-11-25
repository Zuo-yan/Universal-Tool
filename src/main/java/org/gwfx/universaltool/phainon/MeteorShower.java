package org.gwfx.universaltool.phainon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Snapshot of the terrain beneath the volley, shared by damage and rendering. */
public record MeteorShower(Vec3 center, List<Vec3> landings) {
    public static final int COUNT = 18;
    public static final float MINOR_RADIUS = 12, GIANT_RADIUS = 48, VISIBLE_RADIUS = 144;
    public static final int FLIGHT = 21;

    public MeteorShower {
        landings = List.copyOf(landings);
        if (landings.size() != COUNT) throw new IllegalArgumentException("Expected 18 minor impacts");
    }

    public static int impactTick(int index) {
        return PhainonMeteor.RELEASE + FLIGHT + Math.round(index * 2.85F);
    }

    public static float flightProgress(float age, int index) {
        return 1 + (age - impactTick(index)) / FLIGHT;
    }

    static MeteorShower plan(ServerPlayer caster, Vec3 aim) {
        Vec3 center = findSurface(caster, aim, .25);
        var landings = new ArrayList<Vec3>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            float angle = i * 2.39996F;
            float radius = 40 * Mth.sqrt((i + .5F) / COUNT);
            landings.add(findSurface(caster, center.add(radius * Mth.cos(angle), 0, radius * Mth.sin(angle)), 6));
        }
        return new MeteorShower(center, landings);
    }

    private static Vec3 findSurface(ServerPlayer caster, Vec3 point, double rise) {
        var hit = caster.level().clip(new ClipContext(point.add(0, rise, 0), point.add(0, -24, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
        return hit.getType() == HitResult.Type.MISS ? point : hit.getLocation();
    }
}
