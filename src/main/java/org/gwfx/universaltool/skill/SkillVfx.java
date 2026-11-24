package org.gwfx.universaltool.skill;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Visual-only motifs. Keep packet counts fixed per cue, not proportional to range or targets.
 * Uses ordinary particles, so clients keep their own particle settings and need no new packet. */
final class SkillVfx {
    private static final double TAU = Math.PI * 2.0;
    enum Tint {
        GUARD(0x59FFF2, 0x5281FF, ParticleTypes.ENCHANTED_HIT),
        GRAVITY(0xEFA4FF, 0x6830BD, ParticleTypes.PORTAL),
        ICE(0xEDFDFF, 0x58C8FF, ParticleTypes.SNOWFLAKE),
        DREAD(0xFF744D, 0x8A174D, ParticleTypes.CRIMSON_SPORE),
        ORE(0xF8E3FF, 0xB36BFF, ParticleTypes.ENCHANT),
        AIR(0xEDFFFF, 0x75C9DF, ParticleTypes.CLOUD),
        SPEAR(0xFAF0FF, 0x8F66FF, ParticleTypes.ELECTRIC_SPARK),
        RAGE(0xFFC264, 0xD92E20, ParticleTypes.FLAME),
        ASTRAL(0xEFF0FF, 0x9B7BFF, ParticleTypes.END_ROD),
        THUNDER(0xFFF7B0, 0xFF9A34, ParticleTypes.ELECTRIC_SPARK),
        SANCTUARY(0xF4FFDC, 0x42DDBD, ParticleTypes.GLOW),
        VOID(0xF0A9FF, 0x5442F2, ParticleTypes.REVERSE_PORTAL),
        VEIN(0xC4FFF1, 0x2CB89E, ParticleTypes.WAX_ON),
        ALCHEMY(0xFFF3AE, 0xFF7139, ParticleTypes.FLAME);

        final DustColorTransitionOptions dust;
        final ParticleOptions accent;

        Tint(int bright, int afterglow, ParticleOptions accent) {
            this.dust = new DustColorTransitionOptions(rgb(bright), rgb(afterglow), 1.05F);
            this.accent = accent;
        }
    }

    private SkillVfx() {}

    private static Vector3f rgb(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    /** A crisp ring at the indicated height, with no radius-dependent emission count. */
    static void ring(ServerLevel world, Vec3 center, double radius, double phase, int points, Tint tint) {
        int count = Math.clamp(points, 3, 16);
        for (int i = 0; i < count; i++) {
            double angle = TAU * i / count + phase;
            Vec3 p = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            point(world, p, tint.dust);
        }
    }

    /** Three curled arms suggest a moving vortex with only twelve point emissions. */
    static void spiral(ServerLevel world, Vec3 center, double radius, double height, double phase, Tint tint) {
        for (int arm = 0; arm < 3; arm++) {
            for (int step = 0; step < 4; step++) {
                double t = step / 3.0;
                double angle = phase + TAU * arm / 3.0 + t * 1.45;
                double r = radius * (0.24 + t * 0.76);
                point(world, center.add(Math.cos(angle) * r, t * height, Math.sin(angle) * r), tint.dust);
            }
        }
    }

    /** Soft, bounded sparks at the core; no full-screen flash. */
    static void sparks(ServerLevel world, Vec3 p, double horizontal, double vertical, int count, Tint tint) {
        world.sendParticles(tint.accent, p.x, p.y, p.z, Math.clamp(count, 1, 28),
                horizontal, vertical, horizontal, 0.045);
    }

    static void point(ServerLevel world, Vec3 p, ParticleOptions particle) {
        world.sendParticles(particle, p.x, p.y, p.z, 1, 0.015, 0.025, 0.015, 0.0);
    }

    /** Twin edges outline the travel direction rather than putting a cloud on the reticle. */
    static void wake(ServerLevel world, Vec3 origin, Vec3 direction, double length, double width,
                     boolean echo, Tint tint) {
        Vec3 forward = direction.normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        int steps = echo ? 5 : 8;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double spread = (0.12 + Math.sin(t * Math.PI) * 0.70) * width;
            Vec3 p = origin.add(forward.scale(t * length)).add(up.scale(Math.sin(t * Math.PI) * 0.15));
            point(world, p.add(right.scale(spread)), tint.dust);
            point(world, p.add(right.scale(-spread)), tint.dust);
        }
        sparks(world, origin.add(forward.scale(length * 0.86)), 0.24, 0.25, echo ? 5 : 10, tint);
    }

    /** Arched rails and a legible boundary without filling the dome's interior. */
    static void canopy(ServerLevel world, Vec3 center, double radius, double phase, Tint tint) {
        ring(world, center.add(0, 0.20, 0), radius, phase, 12, tint);
        for (int arm = 0; arm < 6; arm++) {
            double angle = phase + TAU * arm / 6.0;
            point(world, center.add(Math.cos(angle) * radius * 0.70, radius * 0.50,
                    Math.sin(angle) * radius * 0.70), tint.dust);
        }
        point(world, center.add(0, radius * 0.73, 0), tint.accent);
    }

    static void portal(ServerLevel world, Vec3 feet, boolean exit) {
        Vec3 center = feet.add(0, 0.10, 0);
        spiral(world, center, exit ? 0.9 : 0.65, 1.5, exit ? 0.0 : Math.PI, Tint.VOID);
        ring(world, center, exit ? 1.2 : 0.72, 0.0, 8, Tint.VOID);
        sparks(world, center.add(0, 0.8, 0), 0.34, 0.50, 12, Tint.VOID);
    }
}
