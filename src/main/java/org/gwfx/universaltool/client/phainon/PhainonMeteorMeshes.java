package org.gwfx.universaltool.client.phainon;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.gwfx.universaltool.UniversalToolMod;

import javax.annotation.Nullable;

/**
 * 觉醒战技「支柱·死星天裁」专属的 4 套 Blockbench 3D 特效模型与 5 张高清贴图缓存。
 * <p>提供 {@link #getOrLoad()} 延迟加载与 {@link #reload(ResourceManager)} 热重载能力，
 * 在游戏内按下 {@code F3+T} 即可立即刷新模型与贴图表现。</p>
 */
public record PhainonMeteorMeshes(
        PhainonVfxModelData.BakedMesh starCore,
        PhainonVfxModelData.BakedMesh flameTail,
        PhainonVfxModelData.BakedMesh skyRift,
        PhainonVfxModelData.BakedMesh blastDome
) {
    /** 暗黑焦岩死星陨石实体贴图（水平无缝，用于实体压暗与立体岩石质感）。 */
    static final ResourceLocation CORE_BODY_TEX = asset("textures/misc/phainon_meteor_core_body.png");
    /** 同 UV 对齐的炽熔岩浆裂纹叠加贴图（水平无缝，Additive 发光）。 */
    static final ResourceLocation CORE_GLOW_TEX = asset("textures/misc/phainon_meteor_core_glow.png");
    /** 超长炽金-炎橙-白炽双层螺旋彗尾焰幔贴图（水平无缝，Additive）。 */
    static final ResourceLocation TRAIL_TEX = asset("textures/misc/phainon_meteor_trail.png");
    /** 高空雷暴云层破洞漩涡与金红天幕裂隙贴图（径向居中，Translucent）。 */
    static final ResourceLocation SKY_VORTEX_TEX = asset("textures/misc/phainon_meteor_sky_vortex.png");
    /** 命中后的烈金白炽爆炸蘑菇云穹与放射光浪贴图（水平无缝，Additive）。 */
    static final ResourceLocation BLAST_DOME_TEX = asset("textures/misc/phainon_meteor_blast_dome.png");
    /** 复用白厄日轮圣印贴图（用于起手脑后日冕与命中瞬间地面圣印爆发）。 */
    static final ResourceLocation SUNBURST_TEX = asset("textures/misc/phainon_sunburst_sigil.png");
    /** 复用白厄炽金剑弧贴图（用于起手升空能量刃构造）。 */
    static final ResourceLocation SLASH_ARC_TEX = asset("textures/misc/phainon_slash_arc.png");

    private static final ResourceLocation STAR_CORE_OBJ = asset("models/vfx/phainon_meteor_star_core.obj");
    private static final ResourceLocation FLAME_TAIL_OBJ = asset("models/vfx/phainon_meteor_flame_tail.obj");
    private static final ResourceLocation SKY_RIFT_OBJ = asset("models/vfx/phainon_meteor_sky_rift.obj");
    private static final ResourceLocation BLAST_DOME_OBJ = asset("models/vfx/phainon_meteor_blast_dome.obj");

    private static volatile PhainonMeteorMeshes current;

    /**
     * 在资源包加载或 {@code F3+T} 热重载时，重新解析并预烘焙全部 4 套陨石 3D OBJ 网格。
     */
    public static void reload(ResourceManager manager) {
        var core = PhainonVfxModelData.loadObjMesh(manager, STAR_CORE_OBJ);
        var tail = PhainonVfxModelData.loadObjMesh(manager, FLAME_TAIL_OBJ);
        var rift = PhainonVfxModelData.loadObjMesh(manager, SKY_RIFT_OBJ);
        var dome = PhainonVfxModelData.loadObjMesh(manager, BLAST_DOME_OBJ);
        current = (core == null || tail == null || rift == null || dome == null)
                ? null
                : new PhainonMeteorMeshes(core, tail, rift, dome);
    }

    /**
     * 获取已缓存的陨石 3D 网格集合；若尚未初始化则从客户端 {@link ResourceManager} 延迟加载。
     */
    @Nullable
    public static PhainonMeteorMeshes getOrLoad() {
        PhainonMeteorMeshes cached = current;
        if (cached != null) {
            return cached;
        }
        synchronized (PhainonMeteorMeshes.class) {
            if (current != null) {
                return current;
            }
            reload(Minecraft.getInstance().getResourceManager());
            return current;
        }
    }

    private static ResourceLocation asset(String path) {
        return ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, path);
    }
}
