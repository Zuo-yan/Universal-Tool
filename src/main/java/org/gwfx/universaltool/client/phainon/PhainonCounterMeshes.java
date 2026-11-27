package org.gwfx.universaltool.client.phainon;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.gwfx.universaltool.UniversalToolMod;

import javax.annotation.Nullable;

/**
 * 白厄觉醒弹反「灾厄·弑魂焚诏」专属 4 套 3D 自由网格模型与 5 张高清贴图缓存。
 * <p>提供 {@link #getOrLoad()} 线程安全懒加载与 {@link #reload(ResourceManager)} 热重载接口，
 * 支持游戏内 {@code F3+T} 即时预览模型与贴图迭代。</p>
 */
public record PhainonCounterMeshes(
        PhainonVfxModelData.BakedMesh greatsword,
        PhainonVfxModelData.BakedMesh cleaveArc,
        PhainonVfxModelData.BakedMesh edictSigil,
        PhainonVfxModelData.BakedMesh craterEruption
) {
    /** 裁决巨剑 OBJ 几何模型中剑尖在局部 +Y 轴的高度（基准单位：方块）。 */
    static final float GREATSWORD_TIP_Y = 4.62F;

    /** 黑曜石暗铁裁决巨剑实体剪影贴图（Translucent Pass 2）。 */
    static final ResourceLocation GREATSWORD_BODY_TEX = asset("textures/misc/phainon_counter_greatsword_body.png");
    /** 同轮廓裁决巨剑炽金/熔红烈焰高光贴图（Additive Pass 3A）。 */
    static final ResourceLocation GREATSWORD_GLOW_TEX = asset("textures/misc/phainon_counter_greatsword_glow.png");
    /** 竖直重劈熔金烈焰刀光展开带（Additive Pass 3B）。 */
    static final ResourceLocation CLEAVE_FLAME_TEX = asset("textures/misc/phainon_counter_cleave_flame.png");
    /** 裂地岩浆喷涌与水平无缝熔岩流光贴图（Additive Pass 3C）。 */
    static final ResourceLocation CRATER_LAVA_TEX = asset("textures/misc/phainon_counter_crater_lava.png");
    /** 纷争神纹立体护盾法环贴图（Additive Pass 3D）。 */
    static final ResourceLocation EDICT_SIGIL_TEX = asset("textures/misc/phainon_counter_edict_sigil.png");
    /** 飞溅玄武岩/黑曜石碎石贴图（Solid Pass 1）。 */
    static final ResourceLocation DEBRIS_ROCK_TEX = asset("textures/misc/phainon_debris_rock.png");

    private static final ResourceLocation GREATSWORD_OBJ = asset("models/vfx/phainon_counter_greatsword.obj");
    private static final ResourceLocation CLEAVE_ARC_OBJ = asset("models/vfx/phainon_counter_cleave_arc.obj");
    private static final ResourceLocation EDICT_SIGIL_OBJ = asset("models/vfx/phainon_counter_edict_sigil.obj");
    private static final ResourceLocation CRATER_ERUPTION_OBJ = asset("models/vfx/phainon_counter_crater_eruption.obj");

    private static volatile PhainonCounterMeshes current;

    /**
     * 清理缓存以便下次渲染时懒加载。
     */
    public static void invalidate() {
        current = null;
    }

    /**
     * 在资源包重载时重新解析并预烘焙全部 4 套反击 3D OBJ 网格。
     */
    public static void reload(ResourceManager manager) {
        synchronized (PhainonCounterMeshes.class) {
            current = loadInternal(manager);
        }
    }

    /**
     * 获取已烘焙的反击 3D 模型缓存；若尚未加载则自动从当前资源管理器懒加载。
     */
    @Nullable
    public static PhainonCounterMeshes getOrLoad() {
        PhainonCounterMeshes cached = current;
        if (cached != null) {
            return cached;
        }
        synchronized (PhainonCounterMeshes.class) {
            if (current != null) {
                return current;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getResourceManager() == null) {
                return null;
            }
            current = loadInternal(mc.getResourceManager());
            return current;
        }
    }

    @Nullable
    private static PhainonCounterMeshes loadInternal(ResourceManager manager) {
        var greatsword = PhainonVfxModelData.loadObjMesh(manager, GREATSWORD_OBJ);
        var cleaveArc = PhainonVfxModelData.loadObjMesh(manager, CLEAVE_ARC_OBJ);
        var edictSigil = PhainonVfxModelData.loadObjMesh(manager, EDICT_SIGIL_OBJ);
        var craterEruption = PhainonVfxModelData.loadObjMesh(manager, CRATER_ERUPTION_OBJ);
        if (greatsword == null || cleaveArc == null || edictSigil == null || craterEruption == null) {
            return null;
        }
        return new PhainonCounterMeshes(greatsword, cleaveArc, edictSigil, craterEruption);
    }

    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, path);
    }
}
