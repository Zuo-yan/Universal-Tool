package org.gwfx.universaltool.client.phainon;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;

import static org.gwfx.universaltool.client.phainon.PhainonEnhancedBasicEffects.smooth;
import static org.gwfx.universaltool.phainon.PhainonMeteor.*;

@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonMeteorAtmosphere {
    /** Separate zenith and horizon hues retain the silhouette of ridges and distant buildings. */
    private record Palette(Vec3 zenith, Vec3 haze, Vec3 light, float ambient) {
        Palette(int sky, int fog, Vec3 light, float ambient) {
            this(Vec3.fromRGB24(sky), Vec3.fromRGB24(fog), light, ambient);
        }
        Palette mix(Palette other, float t) {
            return new Palette(zenith.lerp(other.zenith, t), haze.lerp(other.haze, t),
                    light.lerp(other.light, t), Mth.lerp(t, ambient, other.ambient));
        }
    }

    private static final Palette ECLIPSE = new Palette(0x1B1734, 0x613345, new Vec3(.57, .41, .59), 0);
    private static final Palette DESCENT = new Palette(0x461E39, 0xD35B2B, new Vec3(1.05, .59, .40), .035F);
    private static final Palette FLASH = new Palette(0xFFDB9F, 0xFFEFD1, new Vec3(1.32, 1.15, .90), .30F);
    private static final Palette EMBERS = new Palette(0x512638, 0xE17B42, new Vec3(1.10, .71, .53), .055F);
    static final Scene CLEAR = new Scene(ECLIPSE, 0);

    private PhainonMeteorAtmosphere() {}

    public static final class Scene {
        private final Palette palette;
        private final float strength;

        private Scene(Palette palette, float strength) {
            this.palette = palette;
            this.strength = strength;
        }

        public Vec3 sky(Vec3 original) {
            return original.lerp(palette.zenith, strength * .94F);
        }

        public Vec3 clouds(Vec3 original) {
            return original.lerp(palette.haze.scale(.77), strength * .88F);
        }

        /** NativeImage stores lightmap pixels in ABGR order. Preserve the original luminance gradients. */
        public int lighting(int abgr) {
            if (strength == 0) return abgr;
            Vec3 light = palette.light;
            return 0xFF000000 | channel(abgr, light.x) | channel(abgr >>> 8, light.y) << 8
                    | channel(abgr >>> 16, light.z) << 16;
        }

        private int channel(int bits, double gain) {
            int base = bits & 255;
            return Mth.clamp((int)Math.round(base + strength * (base * (gain - 1) + 255 * palette.ambient)), 0, 255);
        }
    }

    static Scene sample(float age, float weight) {
        if (weight <= 0) return CLEAR;
        Palette palette;
        if (age < IMPACT - 2) {
            palette = ECLIPSE.mix(DESCENT, smooth((age - RELEASE) / (IMPACT - RELEASE - 6)));
        } else if (age < IMPACT + 1) {
            palette = DESCENT.mix(FLASH, smooth((age - IMPACT + 2) / 3));
        } else {
            palette = FLASH.mix(EMBERS, smooth((age - IMPACT - 1) / 13));
        }
        return new Scene(palette, weight);
    }

    public static Scene current(float partial) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return PhainonMeteorEffects.scene(camera.getPosition(), partial);
    }

    public static boolean active(Scene scene) {
        return scene != CLEAR;
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        var scene = current((float)event.getPartialTick());
        if (scene == CLEAR) return;
        var color = new Vec3(event.getRed(), event.getGreen(), event.getBlue())
                .lerp(scene.palette.haze, .91F * scene.strength);
        event.setRed((float)color.x);
        event.setGreen((float)color.y);
        event.setBlue((float)color.z);
    }
}
