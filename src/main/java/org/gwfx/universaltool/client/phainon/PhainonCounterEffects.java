package org.gwfx.universaltool.client.phainon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phainon.PhainonCounter;
import org.gwfx.universaltool.phainon.PhainonSkillEffectPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * 白厄变身弹反「灾厄·弑魂焚诏」客户端电影级 3D 视觉特效、手持/全身姿态与克制镜头反馈主控。
 * <ul>
 *   <li><b>防反架势（GUARD）</b>：胸前横剑格挡 -> 举剑高扬蓄能姿态 + 立体纷争护盾法环 + 暗红炽金蓄能气浪 +
 *       80% 免伤受击时的格挡闪光冲击与爆裂火星；</li>
 *   <li><b>反击斩落（RIPOSTE）</b>：格挡爆发瞬闪 -> 巨型黑曜石+炽金裁决巨剑凌空显形（实体与 Additive 双 Pass 混绘）->
 *       3D 竖直重劈斩弧 + 克制 FOV/镜头微震 -> 落地裂地熔岩喷涌光锥 + 辐射火焰环 + 32 块不透明黑曜石碎石飞溅！</li>
 * </ul>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonCounterEffects {

    private static final class Scene {
        final long start;
        final PhainonSkillEffectPayload cue;
        final PhainonCounterStrikeVisuals.Strike path;
        int lastHurtTime;
        long lastBlockFlashTick = -9999L;

        Scene(long start, PhainonSkillEffectPayload cue, PhainonCounterStrikeVisuals.Strike path) {
            this.start = start;
            this.cue = cue;
            this.path = path;
        }

        float age(long now, float partial) {
            return (now - start) + partial;
        }

        boolean strike() {
            return cue.action() == PhainonSkillEffectPayload.Action.RIPOSTE;
        }

        /** 架势与反击共用的举剑高度曲线（0 = 胸前横剑格挡，1 = 高举蓄能/劈斩蓄势）。 */
        float lift(float t) {
            return strike()
                    ? smooth(t / 18.0F) * (1.0F - smooth((t - 31.0F) / 14.0F))
                    : smooth((t - 13.0F) / 21.0F);
        }

        float blockFlashAge(long now, float partial) {
            if (lastBlockFlashTick < 0L) {
                return -1.0F;
            }
            float dt = (now - lastBlockFlashTick) + partial;
            return (dt >= 0.0F && dt <= 11.0F) ? dt : -1.0F;
        }
    }

    private static ClientLevel level;
    private static final Map<Integer, Scene> SCENES = new HashMap<>();

    private PhainonCounterEffects() {
    }

    /**
     * 资源包热重载回调：同步重载全部 4 套反击 3D 自由网格模型。
     */
    public static void reload(ResourceManager manager) {
        PhainonCounterMeshes.reload(manager);
    }

    private static float smooth(float t) {
        return PhainonEnhancedBasicEffects.smooth(t);
    }

    private static float easeOutCubic(float x) {
        float inv = 1.0F - Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - inv * inv * inv;
    }

    private static float easeOutBack(float x) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float xm1 = Mth.clamp(x, 0.0F, 1.0F) - 1.0F;
        return 1.0F + c3 * xm1 * xm1 * xm1 + c1 * xm1 * xm1;
    }

    private static void sync() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) {
            SCENES.clear();
            level = current;
        }
    }

    static void apply(PhainonSkillEffectPayload cue) {
        sync();
        if (cue.action() == PhainonSkillEffectPayload.Action.GUARD_END) {
            SCENES.remove(cue.casterId());
            return;
        }
        if (level == null) {
            return;
        }
        Entity owner = level.getEntity(cue.casterId());
        Vec3 hit = new Vec3(cue.originX(), cue.originY(), cue.originZ());
        Vec3 origin = owner == null ? hit : owner.position();
        Vec3 direction = hit.subtract(origin);
        float yaw = direction.horizontalDistanceSqr() < 0.001D
                ? cue.yawDeg()
                : (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        boolean mainRight = !(owner instanceof Player player) || player.getMainArm() == HumanoidArm.RIGHT;
        int side = cue.mainHand() == mainRight ? -1 : 1;
        var path = new PhainonCounterStrikeVisuals.Strike(origin, hit, yaw, side);
        long now = level.getGameTime();
        Scene scene = new Scene(now, cue, path);
        if (owner instanceof LivingEntity living) {
            scene.lastHurtTime = living.hurtTime;
        }
        if (scene.strike()) {
            // 触发反击瞬间同步引爆一次格挡成功受击火花
            scene.lastBlockFlashTick = now;
            spawnParryImpactSparks(level, origin, yaw, 20);
        }
        SCENES.put(cue.casterId(), scene);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        sync();
        if (level == null || SCENES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        SCENES.values().removeIf(s -> s.age(now, 0.0F) >= s.cue.totalTicks());

        for (Scene s : SCENES.values()) {
            Entity entity = level.getEntity(s.cue.casterId());
            if (!(entity instanceof LivingEntity owner) || !owner.isAlive()) {
                continue;
            }
            float age = s.age(now, 0.0F);
            if (!s.strike()) {
                // 检测架势 80% 免伤期间受击，立刻爆发格挡火星与护盾闪光冲击
                if (owner.hurtTime > s.lastHurtTime && owner.hurtTime > 0) {
                    s.lastBlockFlashTick = now;
                    spawnParryImpactSparks(level, owner.position(), owner.getYRot(), 16);
                }
                s.lastHurtTime = owner.hurtTime;

                // 架势蓄能期间环绕升腾的暗金/炽红火星粒子
                if (now % 2 == 0) {
                    double ang = now * 0.42D;
                    double rad = 1.18D + 0.16D * Math.sin(age * 0.2D);
                    level.addParticle(
                            ParticleTypes.FLAME,
                            owner.getX() + Math.cos(ang) * rad,
                            owner.getY() + 0.35D + (age % 18) * 0.08D,
                            owner.getZ() + Math.sin(ang) * rad,
                            0.0D, 0.045D, 0.0D
                    );
                    if (age >= PhainonCounter.READY && now % 4 == 0) {
                        level.addParticle(
                                ParticleTypes.END_ROD,
                                owner.getX() - Math.cos(ang) * rad * 0.85D,
                                owner.getY() + 1.15D,
                                owner.getZ() - Math.sin(ang) * rad * 0.85D,
                                0.0D, 0.03D, 0.0D
                        );
                    }
                }
            } else if (age >= PhainonCounter.IMPACT && age <= PhainonCounter.IMPACT + 18 && now % 2 == 0) {
                // 反击落地期间向四周飞溅炽红熔岩火星
                for (int i = 0; i < 4; i++) {
                    double ang = (now * 0.7D) + i * (Math.PI * 0.5D);
                    double spd = 0.18D + (i % 2) * 0.09D;
                    level.addParticle(
                            ParticleTypes.LAVA,
                            s.path.hit().x + Math.cos(ang) * 0.65D,
                            s.path.hit().y + 0.35D,
                            s.path.hit().z + Math.sin(ang) * 0.65D,
                            Math.cos(ang) * spd, 0.24D, Math.sin(ang) * spd
                    );
                }
            }
        }
    }

    /**
     * 在格挡受击/触发反击瞬间喷发高密度金红爆裂火星。
     */
    private static void spawnParryImpactSparks(ClientLevel lvl, Vec3 pos, float yawDeg, int count) {
        float rad = (float) Math.toRadians(yawDeg);
        double fx = -Mth.sin(rad);
        double fz = Mth.cos(rad);
        double cx = pos.x + fx * 0.82D;
        double cy = pos.y + 1.22D;
        double cz = pos.z + fz * 0.82D;
        for (int i = 0; i < count; i++) {
            double a = (i / (double) count) * Math.PI * 2.0D;
            double vRadial = 0.16D + (i % 3) * 0.07D;
            double vx = Math.cos(a) * vRadial + fx * 0.08D;
            double vy = Math.sin(a) * vRadial * 0.72D + 0.06D;
            double vz = Math.sin(a) * vRadial * 0.55D + fz * 0.08D;
            lvl.addParticle(i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.FLAME, cx, cy, cz, vx, vy, vz);
        }
    }

    /**
     * 第一人称手持动画：横剑防反架势 -> 举剑蓄能 -> 受击格挡回震 -> 反击霸气竖劈（保持中心准星通透不遮挡）。
     */
    static boolean hand(PoseStack p, Player player, InteractionHand hand, HumanoidArm arm, float partial, float equip) {
        Scene s = SCENES.get(player.getId());
        if (s == null || level == null || player.level() != level
                || s.cue.mainHand() != (hand == InteractionHand.MAIN_HAND)) {
            return false;
        }
        long now = level.getGameTime();
        float t = s.age(now, partial);
        float lift = s.lift(t);
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float weight = s.strike() ? 1.0F - smooth((t - 50.0F) / 18.0F) : smooth(t / 6.0F);

        // 受击格挡瞬间的短促手部反震
        float blockAge = s.blockFlashAge(now, partial);
        float recoil = (blockAge >= 0.0F && blockAge < 6.0F)
                ? Mth.sin((blockAge / 6.0F) * Mth.PI) * (1.0F - blockAge / 6.0F)
                : 0.0F;

        // 蓄力高点极轻微张力震颤
        float tremor = (lift > 0.35F ? Mth.sin(t * 3.1F) * 0.008F * lift : 0.0F);
        // 反击下劈前倾突进曲线
        float cleaveDown = s.strike() ? easeOutCubic(Mth.clamp((t - 32.0F) / 14.0F, 0.0F, 1.0F)) * weight : 0.0F;

        p.translate(
                side * (0.56F - 0.14F * weight + 0.05F * recoil) + tremor,
                -0.52F - 0.60F * equip + (0.10F + 0.26F * lift - 0.24F * cleaveDown) * weight,
                -0.72F + 0.08F * recoil - 0.14F * cleaveDown
        );
        p.mulPose(Axis.ZP.rotationDegrees(side * (58.0F - 82.0F * lift + 26.0F * cleaveDown) * weight));
        p.mulPose(Axis.XP.rotationDegrees((16.0F + 34.0F * lift - 68.0F * cleaveDown + 14.0F * recoil) * weight));
        return true;
    }

    /**
     * 第三人称角色模型全身姿态：横剑护胸架势 -> 高举蓄能 -> 雷霆俯身重劈。
     */
    public static void pose(PlayerModel<?> model, Player player) {
        Scene s = SCENES.get(player.getId());
        if (s == null || level == null || level != player.level()) {
            return;
        }
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float t = s.age(level.getGameTime(), partial);
        float lift = s.lift(t);
        float w = s.strike() ? 1.0F - smooth((t - 50.0F) / 17.0F) : smooth(t / 7.0F);
        boolean right = (player.getMainArm() == HumanoidArm.RIGHT) == s.cue.mainHand();
        var mainArm = right ? model.rightArm : model.leftArm;
        var offArm = right ? model.leftArm : model.rightArm;

        float cleaveDown = s.strike() ? smooth((t - 31.0F) / 13.0F) * w : 0.0F;
        mainArm.xRot = Mth.lerp(w, mainArm.xRot, -1.18F - 1.58F * lift + 1.45F * cleaveDown);
        mainArm.zRot = Mth.lerp(w, mainArm.zRot, (right ? -1.0F : 1.0F) * (0.76F - 0.54F * lift));
        // 副手同步配合防反蓄势微抬护身（参考 Preview_t0.5s 掌心向前凝聚圣盾）
        offArm.xRot = Mth.lerp(w * 0.65F, offArm.xRot, -0.92F + 0.35F * lift);
        offArm.zRot = Mth.lerp(w * 0.65F, offArm.zRot, (right ? 0.32F : -0.32F));

        model.body.xRot += s.strike() ? 0.28F * cleaveDown : -0.06F * lift * w;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    /**
     * 克制的 FOV 动感冲击：蓄势平滑微收（-4.5°）-> 下劈落地瞬推（+9.5°）-> 三次缓出回正。
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || level == null || mc.level != level) {
            return;
        }
        Scene s = SCENES.get(mc.player.getId());
        if (s == null) {
            return;
        }
        float fovScale = mc.options.fovEffectScale().get().floatValue();
        if (fovScale <= 0.001F) {
            return;
        }
        float age = s.age(level.getGameTime(), (float) event.getPartialTick());
        double delta = 0.0D;
        if (s.strike()) {
            if (age >= 6.0F && age < 32.0F) {
                delta = -4.5D * easeOutCubic((age - 6.0F) / 26.0F);
            } else if (age >= 32.0F && age < PhainonCounter.IMPACT + 14.0F) {
                float u = (age - 32.0F) / (PhainonCounter.IMPACT + 14.0F - 32.0F);
                if (u < 0.48F) {
                    delta = Mth.lerp(easeOutCubic(u / 0.48F), -4.5F, 9.5F);
                } else {
                    delta = Mth.lerp(easeOutCubic((u - 0.48F) / 0.52F), 9.5F, 0.0F);
                }
            }
        } else {
            // 防反架势蓄力微收 -2.0° 增添沉稳凝神感
            float hold = smooth(age / 18.0F) * (1.0F - smooth((age - (s.cue.totalTicks() - 10.0F)) / 10.0F));
            delta = -2.0D * hold;
        }
        if (Math.abs(delta) > 0.01D) {
            event.setFOV(Math.max(30.0D, event.getFOV() + delta * fovScale));
        }
    }

    /**
     * 克制的镜头微震反馈：受击格挡 4 ticks 短震，巨剑落地 7 ticks 阻尼重击微震（绝不眩晕）。
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || level == null || mc.level != level
                || event.getCamera().getEntity() != mc.player) {
            return;
        }
        Scene s = SCENES.get(mc.player.getId());
        if (s == null) {
            return;
        }
        float strength = mc.options.fovEffectScale().get().floatValue();
        if (strength <= 0.001F) {
            return;
        }
        long now = level.getGameTime();
        float partial = (float) event.getPartialTick();
        float age = s.age(now, partial);

        // 1. 格挡成功/架势受击短促微震（<= 4 ticks）
        float blockAge = s.blockFlashAge(now, partial);
        if (blockAge >= 0.0F && blockAge <= 4.0F) {
            float damp = (1.0F - blockAge / 4.0F) * strength;
            event.setPitch(event.getPitch() + Mth.sin(blockAge * 14.0F) * 0.48F * damp);
            event.setYaw(event.getYaw() + Mth.cos(blockAge * 15.0F) * 0.42F * damp);
        }

        // 2. 巨剑落地重击微震与斜倾（<= 7 ticks）
        if (s.strike() && age >= PhainonCounter.IMPACT && age <= PhainonCounter.IMPACT + 7.0F) {
            float local = age - PhainonCounter.IMPACT;
            float damp = (1.0F - local / 7.0F) * strength;
            float rollEnv = Mth.sin((local / 7.0F) * Mth.PI);
            event.setRoll(event.getRoll() + s.path.side() * 2.1F * rollEnv * strength);
            event.setPitch(event.getPitch() + Mth.sin(local * 12.5F) * 0.85F * damp);
            event.setYaw(event.getYaw() + Mth.cos(local * 13.8F) * 0.72F * damp);
        }
    }

    /**
     * 柔和克制的屏幕边缘暖金红瞬闪反馈（最大持续 <= 6 ticks，Alpha <= 72/255，绝不刺眼）。
     */
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || level == null || mc.level != level || mc.options.hideGui) {
            return;
        }
        Scene s = SCENES.get(mc.player.getId());
        if (s == null) {
            return;
        }
        long now = level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float age = s.age(now, partial);

        float edgeIntensity = 0.0F;
        float flashIntensity = 0.0F;
        float blockAge = s.blockFlashAge(now, partial);
        if (blockAge >= 0.0F && blockAge <= 5.0F) {
            float t = 1.0F - blockAge / 5.0F;
            edgeIntensity = Math.max(edgeIntensity, t * 0.24F);
        }
        if (s.strike() && age >= PhainonCounter.IMPACT && age <= PhainonCounter.IMPACT + 6.0F) {
            float t = 1.0F - (age - PhainonCounter.IMPACT) / 6.0F;
            flashIntensity = t * t;
            edgeIntensity = Math.max(edgeIntensity, t * 0.28F);
        }
        if (edgeIntensity <= 0.005F && flashIntensity <= 0.005F) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        if (edgeIntensity > 0.005F) {
            int edge = Math.max(14, (int) (Math.min(width, height) * 0.07F));
            int outerAlpha = Math.min(64, (int) (edgeIntensity * 255.0F));
            int outerCol = (outerAlpha << 24) | 0x00FF7B29;
            int innerCol = 0x00FF7B29;
            gui.fillGradient(0, 0, width, edge, outerCol, innerCol);
            gui.fillGradient(0, height - edge, width, height, innerCol, outerCol);
        }
        if (flashIntensity > 0.005F) {
            int flashAlpha = Math.min(72, (int) (flashIntensity * 72.0F));
            gui.fill(0, 0, width, height, (flashAlpha << 24) | 0x00FFE7A6);
        }
    }

    /**
     * 严格分层多 Pass 3D 渲染：
     * Pass 1(Solid 碎石) -> Pass 2(Translucent 黑曜石巨剑实体) -> Pass 3A(Additive 巨剑炽焰) ->
     * Pass 3B(Additive 竖劈斩弧与裂地熔岩痕) -> Pass 3C(Additive 火山熔岩喷涌与架势气浪) -> Pass 3D(Additive 纷争护盾法环与冲击环)。
     */
    static boolean hasWorldEffects() {
        return !SCENES.isEmpty();
    }

    static void renderWorld(RenderLevelStageEvent e) {
        if (SCENES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (level == null || level != mc.level) {
            return;
        }
        PhainonCounterMeshes counterMeshes = PhainonCounterMeshes.getOrLoad();
        PhainonVfxModelData sharedModels = PhainonVfxModelData.getOrLoad();
        if (counterMeshes == null || sharedModels == null) {
            return;
        }

        var buffers = mc.renderBuffers().bufferSource();
        var pose = e.getPoseStack();
        Vec3 camera = e.getCamera().getPosition();
        Entity camEntity = e.getCamera().getEntity();
        boolean camFirstPerson = mc.options.getCameraType().isFirstPerson();
        long now = level.getGameTime();
        float partial = e.getPartialTick().getGameTimeDeltaPartialTick(false);

        // Pass 1：不透明受光黑曜石碎石块（Solid）
        RenderType debrisLayer = PhainonVfxRenderTypes.solidTriangles(PhainonCounterMeshes.DEBRIS_ROCK_TEX);
        VertexConsumer debrisBuf = buffers.getBuffer(debrisLayer);
        for (Scene s : SCENES.values()) {
            if (s.strike()) {
                PhainonCounterStrikeVisuals.renderShatteredDebris(
                        pose, debrisBuf, sharedModels, mc, s.path, camera, s.age(now, partial));
            }
        }
        buffers.endBatch(debrisLayer);

        // Pass 2：暗色黑曜石锻铁裁决巨剑实体剪影（Translucent，不写深度，先压暗白天背景）
        RenderType swordBodyLayer = PhainonVfxRenderTypes.translucentTriangles(PhainonCounterMeshes.GREATSWORD_BODY_TEX);
        VertexConsumer swordBodyBuf = buffers.getBuffer(swordBodyLayer);
        for (Scene s : SCENES.values()) {
            Entity owner = level.getEntity(s.cue.casterId());
            boolean firstPerson = owner != null && owner == camEntity && camFirstPerson;
            float age = s.age(now, partial);
            if (s.strike()) {
                PhainonCounterStrikeVisuals.renderGreatswordBody(pose, swordBodyBuf, counterMeshes, s.path, camera, age, firstPerson);
            } else if (owner != null) {
                renderGuardBladeManifest(pose, swordBodyBuf, counterMeshes, owner, s, camera, age, partial, firstPerson, false);
            }
        }
        buffers.endBatch(swordBodyLayer);

        // Pass 3A：同轮廓炽金/熔红巨剑高光与残影（Additive）
        RenderType swordGlowLayer = PhainonVfxRenderTypes.additiveGlow(PhainonCounterMeshes.GREATSWORD_GLOW_TEX);
        VertexConsumer swordGlowBuf = buffers.getBuffer(swordGlowLayer);
        for (Scene s : SCENES.values()) {
            Entity owner = level.getEntity(s.cue.casterId());
            boolean firstPerson = owner != null && owner == camEntity && camFirstPerson;
            float age = s.age(now, partial);
            if (s.strike()) {
                PhainonCounterStrikeVisuals.renderGreatswordGlow(pose, swordGlowBuf, counterMeshes, s.path, camera, age, firstPerson);
            } else if (owner != null) {
                renderGuardBladeManifest(pose, swordGlowBuf, counterMeshes, owner, s, camera, age, partial, firstPerson, true);
            }
        }
        buffers.endBatch(swordGlowLayer);

        // Pass 3B：3D 竖直重劈裂空斩弧与地面炽金熔岩裂纹（Additive）
        RenderType cleaveLayer = PhainonVfxRenderTypes.additiveGlow(PhainonCounterMeshes.CLEAVE_FLAME_TEX);
        VertexConsumer cleaveBuf = buffers.getBuffer(cleaveLayer);
        for (Scene s : SCENES.values()) {
            if (!s.strike()) {
                continue;
            }
            Entity owner = level.getEntity(s.cue.casterId());
            boolean firstPerson = owner != null && owner == camEntity && camFirstPerson;
            PhainonCounterStrikeVisuals.renderCleaveAndFissures(
                    pose, cleaveBuf, counterMeshes, sharedModels, s.path, camera, s.age(now, partial), firstPerson);
        }
        buffers.endBatch(cleaveLayer);

        // Pass 3C：裂地火山熔岩喷涌光锥（RIPOSTE）与架势暗红炽金蓄能气浪（GUARD）（Additive）
        RenderType lavaLayer = PhainonVfxRenderTypes.additiveGlow(PhainonCounterMeshes.CRATER_LAVA_TEX);
        VertexConsumer lavaBuf = buffers.getBuffer(lavaLayer);
        for (Scene s : SCENES.values()) {
            Entity owner = level.getEntity(s.cue.casterId());
            boolean firstPerson = owner != null && owner == camEntity && camFirstPerson;
            float age = s.age(now, partial);
            if (s.strike()) {
                PhainonCounterStrikeVisuals.renderCraterEruption(pose, lavaBuf, counterMeshes, s.path, camera, age, firstPerson);
            } else if (owner != null) {
                renderGuardAuraPlume(pose, lavaBuf, counterMeshes, owner, s, camera, age, partial, firstPerson);
            }
        }
        buffers.endBatch(lavaLayer);

        // Pass 3D：立体纷争护盾法环（GUARD）、80% 免伤受击闪光冲击、巨剑日核与地面扩散火焰环（Additive）
        RenderType sigilLayer = PhainonVfxRenderTypes.additiveGlow(PhainonCounterMeshes.EDICT_SIGIL_TEX);
        VertexConsumer sigilBuf = buffers.getBuffer(sigilLayer);
        for (Scene s : SCENES.values()) {
            Entity owner = level.getEntity(s.cue.casterId());
            boolean firstPerson = owner != null && owner == camEntity && camFirstPerson;
            float age = s.age(now, partial);
            if (s.strike()) {
                PhainonCounterStrikeVisuals.renderStrikeSigils(pose, sigilBuf, counterMeshes, s.path, camera, age, firstPerson);
            } else if (owner != null) {
                renderGuardSigilsAndBlockFlash(pose, sigilBuf, counterMeshes, owner, s, camera, now, age, partial, firstPerson);
            }
        }
        buffers.endBatch(sigilLayer);
    }

    /**
     * GUARD 架势阶段：高举蓄能时于剑锋处凝聚渐显的半透黑金+炽焰裁决巨剑虚影（参考 Preview_t3.5s）。
     */
    private static void renderGuardBladeManifest(PoseStack pose, VertexConsumer buffer, PhainonCounterMeshes meshes,
                                                 Entity owner, Scene s, Vec3 camera, float age, float partial,
                                                 boolean firstPerson, boolean glowPass) {
        float manifest = smooth((age - 16.0F) / 18.0F);
        if (manifest <= 0.01F) {
            return;
        }
        float fadeOut = 1.0F - smooth((age - (s.cue.totalTicks() - 8.0F)) / 8.0F);
        float alpha = manifest * fadeOut * (firstPerson ? (glowPass ? 0.34F : 0.22F) : (glowPass ? 0.86F : 0.62F));
        if (alpha <= 0.01F) {
            return;
        }

        Vec3 pos = owner.getPosition(partial).subtract(camera);
        float yaw = Mth.lerp(partial, owner.yRotO, owner.getYRot());
        float lift = s.lift(age);
        float readyPulse = age >= PhainonCounter.READY ? (0.90F + 0.10F * Mth.sin(age * 0.42F)) : 1.0F;

        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        // 置于持剑侧上方斜指向前外侧，第一人称侧移避免遮挡准星
        pose.translate(s.path.side() * (firstPerson ? 0.88F : 0.56F), 1.32F + lift * 0.55F, firstPerson ? 0.58F : 0.36F);
        pose.mulPose(Axis.ZP.rotationDegrees(s.path.side() * (68.0F - 96.0F * lift)));
        pose.mulPose(Axis.XP.rotationDegrees(18.0F + 12.0F * lift));

        float scale = Mth.lerp(manifest, 0.36F, firstPerson ? 0.66F : 1.08F) * (glowPass ? 1.018F : 1.0F);
        pose.scale(scale, scale, scale);
        if (glowPass) {
            meshes.greatsword().renderAdditive(pose, buffer, 1.0F, 0.90F, 0.52F, alpha * readyPulse, 0.0F, 0.0F);
        } else {
            meshes.greatsword().renderAdditive(pose, buffer, 1.0F, 1.0F, 1.0F, alpha, 0.0F, 0.0F);
        }
        pose.popPose();
    }

    /**
     * GUARD 架势阶段：脚下升腾的暗红炽金蓄能威压气浪（使用无缝火山喷涌壳体低矮缓旋）。
     */
    private static void renderGuardAuraPlume(PoseStack pose, VertexConsumer buffer, PhainonCounterMeshes meshes,
                                             Entity owner, Scene s, Vec3 camera, float age, float partial,
                                             boolean firstPerson) {
        float ramp = smooth(age / 10.0F);
        float fadeOut = 1.0F - smooth((age - (s.cue.totalTicks() - 10.0F)) / 10.0F);
        float readyBoost = age >= PhainonCounter.READY ? 1.18F : 0.78F;
        float alpha = ramp * fadeOut * readyBoost * (firstPerson ? 0.24F : 0.56F);
        if (alpha <= 0.01F) {
            return;
        }
        Vec3 pos = owner.getPosition(partial).subtract(camera);
        pose.pushPose();
        pose.translate(pos.x, pos.y + 0.02D, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(age * 3.2F));
        float xz = 0.72F + 0.16F * smooth(age / 24.0F);
        float y = 0.52F + 0.26F * smooth((age - 10.0F) / 22.0F);
        pose.scale(xz, y, xz);
        meshes.craterEruption().renderAdditive(pose, buffer, 1.0F, 0.44F, 0.16F, alpha, age * 0.015F, age * 0.048F);
        pose.popPose();
    }

    /**
     * GUARD 架势阶段：立体纷争护盾法环（身前威压盾环 + 脚下双层法阵）与 80% 免伤受击时的格挡闪光冲击。
     */
    private static void renderGuardSigilsAndBlockFlash(PoseStack pose, VertexConsumer buffer, PhainonCounterMeshes meshes,
                                                       Entity owner, Scene s, Vec3 camera, long now,
                                                       float age, float partial, boolean firstPerson) {
        float enter = easeOutBack(Mth.clamp(age / 9.0F, 0.0F, 1.0F));
        float fadeOut = 1.0F - smooth((age - (s.cue.totalTicks() - 8.0F)) / 8.0F);
        float baseAlpha = smooth(age / 5.0F) * fadeOut;
        if (baseAlpha <= 0.01F) {
            return;
        }

        Vec3 pos = owner.getPosition(partial).subtract(camera);
        float yaw = Mth.lerp(partial, owner.yRotO, owner.getYRot());
        float readyGlow = smooth((age - (PhainonCounter.READY - 6.0F)) / 8.0F);
        float pulse = 0.92F + 0.08F * Mth.sin(age * 0.36F);

        // 1. 身前立体纷争护盾法环（第一人称降低中心 Alpha 并略下移，确保视野清晰不糊脸）
        pose.pushPose();
        pose.translate(pos.x, pos.y + (firstPerson ? 0.96D : 1.18D), pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0.0F, 0.0F, firstPerson ? 1.12F : 0.84F);

        float shieldScale = Mth.lerp(enter, 0.25F, firstPerson ? 0.72F : 1.06F) * (1.0F + 0.06F * readyGlow);
        float frontAlpha = baseAlpha * pulse * (firstPerson ? 0.34F : 0.88F);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(age * 2.4F));
        pose.scale(shieldScale, shieldScale, shieldScale);
        meshes.edictSigil().renderAdditive(pose, buffer, 1.0F, 0.76F + 0.16F * readyGlow, 0.34F + 0.20F * readyGlow, frontAlpha, 0.0F, 0.0F);
        pose.popPose();

        // 反向旋转的炽金内盾棘冠
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.05F);
        pose.mulPose(Axis.ZP.rotationDegrees(-age * 4.2F));
        float innerScale = shieldScale * 0.68F;
        pose.scale(innerScale, innerScale, innerScale * 0.75F);
        meshes.edictSigil().renderAdditive(pose, buffer, 1.0F, 0.96F, 0.68F, frontAlpha * 0.72F, 0.0F, 0.0F);
        pose.popPose();

        // 80% 免伤受击时的格挡闪光冲击环（向外爆裂扩张）
        float blockAge = s.blockFlashAge(now, partial);
        if (blockAge >= 0.0F) {
            float bt = Mth.clamp(blockAge / 11.0F, 0.0F, 1.0F);
            float bScale = Mth.lerp(easeOutCubic(bt), shieldScale * 0.95F, shieldScale * 2.35F);
            float bAlpha = (1.0F - bt) * (1.0F - 0.35F * bt) * (firstPerson ? 0.68F : 1.0F);
            pose.pushPose();
            pose.translate(0.0F, 0.0F, 0.10F);
            pose.mulPose(Axis.ZP.rotationDegrees(blockAge * 13.0F));
            pose.scale(bScale, bScale, bScale * 0.6F);
            meshes.edictSigil().renderAdditive(pose, buffer, 1.0F, 0.98F, 0.82F, bAlpha, 0.0F, 0.0F);
            pose.popPose();
        }
        pose.popPose();

        // 2. 脚下立体纷争威压法阵（平铺于地面 y + 0.06）
        pose.pushPose();
        pose.translate(pos.x, pos.y + 0.06D, pos.z);
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(-age * 2.1F));
        float groundScale = Mth.lerp(enter, 0.45F, 1.58F + 0.24F * readyGlow);
        pose.scale(groundScale, groundScale, 0.35F);
        meshes.edictSigil().renderAdditive(pose, buffer, 1.0F, 0.56F + 0.22F * readyGlow, 0.20F, baseAlpha * (firstPerson ? 0.52F : 0.84F), 0.0F, 0.0F);
        pose.popPose();
    }
}
