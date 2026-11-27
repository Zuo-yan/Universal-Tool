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

/**
 * 「支柱·死星天裁」电影级天色、雾效与全局光照调色管线。
 * <p>完整还原关键帧 01-08 五段氛围演进：
 * 暗紫蚀日蓄力 → 天幕漩涡炽燃 → 巨型死星破云俯冲 → 命中白金爆闪 → 灼热绯红残效。</p>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonMeteorAtmosphere {
    /** 分离天顶与地平线雾色，既能让白昼瞬间天地失色，又保留远山与建筑剪影层次。 */
    private record Palette(Vec3 zenith, Vec3 haze, Vec3 light, float ambient) {
        Palette(int sky, int fog, Vec3 light, float ambient) {
            this(Vec3.fromRGB24(sky), Vec3.fromRGB24(fog), light, ambient);
        }

        Palette mix(Palette other, float t) {
            return new Palette(
                    zenith.lerp(other.zenith, t),
                    haze.lerp(other.haze, t),
                    light.lerp(other.light, t),
                    Mth.lerp(t, ambient, other.ambient)
            );
        }
    }

    /** 关键帧 01-02：深邃暗紫黑曜石穹宇 + 暗红微光地平，突显脑后金色日冕。 */
    private static final Palette ECLIPSE = new Palette(0x140E28, 0x56283B, new Vec3(0.54, 0.38, 0.56), 0.0F);
    /** 关键帧 03-04：天幕撕开漩涡，紫红雷云与熔金辉光交织。 */
    private static final Palette RIFT = new Palette(0x2B1432, 0x9E3E26, new Vec3(0.86, 0.52, 0.44), 0.02F);
    /** 关键帧 05-06：巨型死星陨石破云压境，天地被炽橙熔金火光照彻。 */
    private static final Palette DESCENT = new Palette(0x42182F, 0xDE6127, new Vec3(1.12, 0.62, 0.36), 0.048F);
    /** 关键帧 07：命中瞬间白金高能爆闪，强光穿透整片战场。 */
    private static final Palette FLASH = new Palette(0xFFE4AA, 0xFFF4D6, new Vec3(1.38, 1.18, 0.92), 0.32F);
    /** 关键帧 08：爆炸火云蘑菇穹顶与灼热绯红余烬残效。 */
    private static final Palette EMBERS = new Palette(0x4A1D2D, 0xE6783A, new Vec3(1.12, 0.68, 0.46), 0.062F);

    static final Scene CLEAR = new Scene(ECLIPSE, 0.0F);

    private PhainonMeteorAtmosphere() {}

    public static final class Scene {
        private final Palette palette;
        private final float strength;

        private Scene(Palette palette, float strength) {
            this.palette = palette;
            this.strength = strength;
        }

        public Vec3 sky(Vec3 original) {
            return original.lerp(palette.zenith, strength * 0.95F);
        }

        public Vec3 clouds(Vec3 original) {
            return original.lerp(palette.haze.scale(0.80), strength * 0.90F);
        }

        /** NativeImage 以 ABGR 顺序存储光照图，按色彩增益压暗或照亮全局明暗梯度。 */
        public int lighting(int abgr) {
            if (strength <= 0.001F) {
                return abgr;
            }
            Vec3 light = palette.light;
            return 0xFF000000
                    | channel(abgr, light.x)
                    | (channel(abgr >>> 8, light.y) << 8)
                    | (channel(abgr >>> 16, light.z) << 16);
        }

        private int channel(int bits, double gain) {
            int base = bits & 255;
            return Mth.clamp((int) Math.round(base + strength * (base * (gain - 1.0) + 255.0 * palette.ambient)), 0, 255);
        }
    }

    static Scene sample(float age, float weight) {
        if (weight <= 0.001F) {
            return CLEAR;
        }
        Palette palette;
        if (age < RELEASE) {
            palette = ECLIPSE.mix(RIFT, smooth((age - 16.0F) / (RELEASE - 16.0F)) * 0.72F);
        } else if (age < GIANT) {
            palette = RIFT.mix(DESCENT, smooth((age - RELEASE) / (GIANT - RELEASE)) * 0.55F);
        } else if (age < IMPACT - 2.0F) {
            palette = RIFT.mix(DESCENT, 0.55F + 0.45F * smooth((age - GIANT) / (IMPACT - GIANT - 4.0F)));
        } else if (age < IMPACT + 1.5F) {
            palette = DESCENT.mix(FLASH, smooth((age - IMPACT + 2.0F) / 3.5F));
        } else {
            palette = FLASH.mix(EMBERS, smooth((age - IMPACT - 1.5F) / 11.5F));
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
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        var scene = current((float) event.getPartialTick());
        if (scene == CLEAR) {
            return;
        }
        var color = new Vec3(event.getRed(), event.getGreen(), event.getBlue())
                .lerp(scene.palette.haze, 0.92F * scene.strength);
        event.setRed((float) color.x);
        event.setGreen((float) color.y);
        event.setBlue((float) color.z);
    }
}
