package org.gwfx.universaltool.client.phainon;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.PhoenixCinematicController;
import org.gwfx.universaltool.phainon.PhainonUltimateHandler;

/**
 * 白厄终结技「永劫燔世，其将背负」三幕式正面+环绕电影运镜控制器：
 * <ul>
 *   <li>第一幕（0–38 ticks）：正面略仰拍（THIRD_PERSON_FRONT），呈现高举侵晨与白蓝圣翼展开；</li>
 *   <li>第二幕（38–82 ticks）：向右环绕拉远并仰视，呈现金色旋转焚世茧球与漫天飞石全景；</li>
 *   <li>第三幕（82–112 ticks）：破茧瞬间切回正面神明全景，定格左金右黑金不对称巨翼与 12 枚泰坦神徽，
 *       结束后平滑交回原视角；演出途中按 F5 可随时主动退出电影运镜。</li>
 * </ul>
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID, value = Dist.CLIENT)
public final class PhainonUltimateCamera {
    private static CameraType original;

    private PhainonUltimateCamera() {
    }

    static void begin() {
        finish();
        Minecraft game = Minecraft.getInstance();
        CameraType view = game.options.getCameraType();
        if (game.player == null || game.player.isPassenger() || game.player.isFallFlying()
                || game.screen != null || PhoenixCinematicController.isActive()
                || game.options.fovEffectScale().get() <= 0) {
            return;
        }
        original = view;
        game.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }

    static void tick() {
        if (original == null) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        if (PhainonUltimateEffectRenderer.localOpeningAge(0) < 0 || game.screen != null
                || game.player == null || !game.player.isAlive() || game.player.isPassenger()
                || game.player.isFallFlying() || game.options.fovEffectScale().get() <= 0
                || PhoenixCinematicController.isActive()
                || game.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) {
            finish();
        }
    }

    static void finish() {
        if (original == null) {
            return;
        }
        Minecraft game = Minecraft.getInstance();
        if (game.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            game.options.setCameraType(original);
        }
        original = null;
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft game = Minecraft.getInstance();
        if (original == null || game.options.getCameraType() != CameraType.THIRD_PERSON_FRONT
                || event.getCamera().getEntity() != game.player) {
            return;
        }
        float age = PhainonUltimateEffectRenderer.localOpeningAge((float) event.getPartialTick());
        if (age < 0.0F) {
            return;
        }

        float awaken = PhainonUltimateHandler.AWAKEN_TICK;
        float openingEnd = PhainonUltimateHandler.OPENING_TICKS;
        float strength = game.options.fovEffectScale().get().floatValue();
        float enter = ease(age / 12.0F);
        float exit = 1.0F - ease((age - (openingEnd - 8.0F)) / 8.0F);
        float weight = enter * exit * strength;

        float yawOffset;
        float targetPitch;
        float rollOffset = 0.0F;
        float shakePitch = 0.0F;
        float shakeYaw = 0.0F;

        if (age < 38.0F) {
            // 第一幕：正面微偏 + 略微仰拍举剑升空
            float t = ease(age / 38.0F);
            yawOffset = Mth.lerp(t, 8.0F, 16.0F);
            targetPitch = Mth.lerp(t, -6.0F, -11.0F);
        } else if (age < awaken) {
            // 第二幕：环绕斜侧仰拍金色旋转焚世茧球
            float t = ease((age - 38.0F) / (awaken - 38.0F));
            yawOffset = Mth.lerp(t, 16.0F, 44.0F);
            targetPitch = Mth.lerp(t, -11.0F, -15.0F);
            // 破茧前最后 8 tick 坍缩蓄势微震
            if (age > awaken - 8.0F) {
                float pre = (age - (awaken - 8.0F)) / 8.0F;
                shakePitch = Mth.sin(age * 9.5F) * 0.45F * pre;
                shakeYaw = Mth.cos(age * 11.2F) * 0.45F * pre;
            }
        } else {
            // 第三幕：破茧瞬间切回正面神明全景，伴随短促爆发冲击微震
            float local = age - awaken;
            float snap = ease(local / 9.0F);
            yawOffset = Mth.lerp(snap, 44.0F, -6.0F);
            targetPitch = Mth.lerp(snap, -15.0F, -11.5F);
            if (local < 7.0F) {
                float damp = 1.0F - (local / 7.0F);
                rollOffset = Mth.sin(local * 1.4F) * 2.4F * damp;
                shakePitch = Mth.sin(local * 12.0F) * 0.75F * damp;
                shakeYaw = Mth.cos(local * 13.5F) * 0.70F * damp;
            }
        }

        event.setYaw(event.getYaw() + (yawOffset + shakeYaw) * weight);
        event.setPitch(Mth.lerp(weight, event.getPitch(), targetPitch) + shakePitch * strength);
        event.setRoll(event.getRoll() + rollOffset * strength);
    }

    @SubscribeEvent
    public static void distance(CalculateDetachedCameraDistanceEvent event) {
        Minecraft game = Minecraft.getInstance();
        if (original == null || event.getCamera().getEntity() != game.player
                || game.options.getCameraType() != CameraType.THIRD_PERSON_FRONT) {
            return;
        }
        float age = PhainonUltimateEffectRenderer.localOpeningAge(event.getCamera().getPartialTickTime());
        if (age < 0.0F) {
            return;
        }

        float awaken = PhainonUltimateHandler.AWAKEN_TICK;
        float openingEnd = PhainonUltimateHandler.OPENING_TICKS;
        float base = original.isFirstPerson() ? 2.4F : event.getDistance();

        float dist;
        if (age < 38.0F) {
            // 第一幕：中近景展示举剑与白蓝天翼
            dist = Mth.lerp(ease(age / 16.0F), base, 4.35F);
        } else if (age < awaken) {
            // 第二幕：拉远至 7.3 格完整框住金色旋转茧球与漫天飞石
            float t = ease((age - 38.0F) / 28.0F);
            float crushPull = ease((age - (awaken - 8.0F)) / 8.0F);
            dist = Mth.lerp(t, 4.35F, 7.35F) - 0.55F * crushPull;
        } else {
            // 第三幕：破茧瞬推广角至 7.6 格后优雅缓推至 5.8 格定格双翼与泰坦神徽
            float local = age - awaken;
            float boom = ease(local / 5.0F);
            float glideIn = ease((local - 6.0F) / 16.0F);
            dist = Mth.lerp(boom, 6.80F, 7.65F);
            dist = Mth.lerp(glideIn, dist, 5.85F);
            float settle = ease((age - (openingEnd - 8.0F)) / 8.0F);
            dist = Mth.lerp(settle, dist, base);
        }

        event.setDistance(dist);
    }

    private static float ease(float x) {
        return PhainonUltimateEffectRenderer.ease(x);
    }
}
