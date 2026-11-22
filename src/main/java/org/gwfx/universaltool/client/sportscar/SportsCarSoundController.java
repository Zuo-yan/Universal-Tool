package org.gwfx.universaltool.client.sportscar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.gwfx.universaltool.init.ModSounds;
import org.gwfx.universaltool.sportscar.SportsCarEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海湾 GT 跑车客户端动态音效控制器，基于 {@link AbstractTickableSoundInstance}
 * 实时驱动引擎转速变调（含水上巡航低沉音色）、陆地手刹漂移轮胎摩擦声、
 * 水面滑行破浪声与氮气涡轮喷射声。
 */
public final class SportsCarSoundController {

    private static final Map<Integer, CarSoundGroup> ACTIVE_GROUPS = new ConcurrentHashMap<>();

    private SportsCarSoundController() {
    }

    /**
     * 每客户端刻同步附近及当前乘坐跑车的循环音效实例。
     */
    public static void updateNearbyCars(Minecraft mc, Iterable<SportsCarEntity> nearbyCars) {
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        SoundManager soundManager = mc.getSoundManager();

        for (SportsCarEntity car : nearbyCars) {
            if (!car.isAlive()) {
                continue;
            }
            CarSoundGroup group = ACTIVE_GROUPS.computeIfAbsent(car.getId(), id -> new CarSoundGroup());

            boolean engineShouldPlay = !car.getPassengers().isEmpty()
                    || Math.abs(car.getForwardSpeed()) > 0.025F
                    || Math.abs(car.getThrottle()) > 0.05F;
            if (engineShouldPlay && (group.engine == null || group.engine.isStopped())) {
                group.engine = new EngineSoundInstance(car);
                soundManager.play(group.engine);
            }

            boolean onWater = isHydroSoundActive(car);
            if (!onWater && car.isDrifting() && car.onGround()
                    && (group.drift == null || group.drift.isStopped())) {
                group.drift = new DriftSoundInstance(car);
                soundManager.play(group.drift);
            }

            boolean glidingOnWater = onWater && (Math.abs(car.getForwardSpeed()) > 0.03F || car.isDrifting());
            if (glidingOnWater && (group.waterGlide == null || group.waterGlide.isStopped())) {
                group.waterGlide = new WaterGlideSoundInstance(car);
                soundManager.play(group.waterGlide);
            }

            if (car.isNitroActive() && (group.nitro == null || group.nitro.isStopped())) {
                group.nitro = new NitroSoundInstance(car);
                soundManager.play(group.nitro);
            }
        }

        // 清理已停止或实体已移除的空分组
        Iterator<Map.Entry<Integer, CarSoundGroup>> it = ACTIVE_GROUPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, CarSoundGroup> entry = it.next();
            if (entry.getValue().isAllStopped()) {
                it.remove();
            }
        }
    }

    /**
     * 停止并清空所有跑车音效（用于退出世界或断线重连）。
     */
    public static void clearAll() {
        Minecraft mc = Minecraft.getInstance();
        SoundManager soundManager = mc.getSoundManager();
        for (CarSoundGroup group : ACTIVE_GROUPS.values()) {
            group.stopAll(soundManager);
        }
        ACTIVE_GROUPS.clear();
    }

    private static boolean isHydroSoundActive(SportsCarEntity car) {
        return car.isInWaterMode() || car.isWaterSurfaceActive() || car.getWaterMorphProgress() > 0.35F;
    }

    private static final class CarSoundGroup {
        EngineSoundInstance engine;
        DriftSoundInstance drift;
        WaterGlideSoundInstance waterGlide;
        NitroSoundInstance nitro;

        boolean isAllStopped() {
            return (engine == null || engine.isStopped())
                    && (drift == null || drift.isStopped())
                    && (waterGlide == null || waterGlide.isStopped())
                    && (nitro == null || nitro.isStopped());
        }

        void stopAll(SoundManager soundManager) {
            if (engine != null) {
                soundManager.stop(engine);
                engine.finish();
            }
            if (drift != null) {
                soundManager.stop(drift);
                drift.finish();
            }
            if (waterGlide != null) {
                soundManager.stop(waterGlide);
                waterGlide.finish();
            }
            if (nitro != null) {
                soundManager.stop(nitro);
                nitro.finish();
            }
        }
    }

    /**
     * 引擎循环音：随车速、油门及氮气激活实时变调，并在水上模式呈现更平顺低沉的巡航音色。
     */
    private static final class EngineSoundInstance extends BaseCarLoopSound {

        private EngineSoundInstance(SportsCarEntity car) {
            super(car, ModSounds.SPORTS_CAR_ENGINE.get());
            this.volume = 0.08F;
            this.pitch = 0.70F;
        }

        @Override
        protected void updateAudio(SportsCarEntity car) {
            boolean hasPassenger = !car.getPassengers().isEmpty();
            float speedRatio = Mth.clamp(Math.abs(car.getForwardSpeed()) / 0.95F, 0.0F, 1.5F);
            float absThrottle = Math.abs(car.getThrottle());
            boolean running = hasPassenger || speedRatio > 0.03F || absThrottle > 0.05F;

            if (!running) {
                this.volume = this.volume * 0.78F - 0.025F;
                if (this.volume <= 0.02F) {
                    finish();
                }
                return;
            }

            boolean onWater = isHydroSoundActive(car);
            float targetPitch = 0.66F
                    + 0.60F * Mth.clamp(speedRatio, 0.0F, 1.25F)
                    + 0.16F * absThrottle
                    + (car.isNitroActive() ? 0.24F : 0.0F);
            if (onWater) {
                targetPitch *= 0.88F;
            }
            targetPitch = Mth.clamp(targetPitch, 0.52F, 1.85F);

            float targetVolume = (hasPassenger ? 0.36F : 0.22F)
                    + 0.34F * Mth.clamp(speedRatio, 0.0F, 1.0F)
                    + 0.14F * absThrottle
                    + (car.isNitroActive() ? 0.12F : 0.0F);
            if (onWater) {
                targetVolume *= 0.86F;
            }
            targetVolume = Mth.clamp(targetVolume, 0.14F, 0.90F);

            this.pitch = Mth.lerp(onWater ? 0.18F : 0.24F, this.pitch, targetPitch);
            this.volume = Mth.lerp(0.20F, this.volume, targetVolume);
        }
    }

    /**
     * 陆地漂移轮胎摩擦声：手刹漂移且接地时平滑淡入，进入水上或退出漂移后快速收音。
     */
    private static final class DriftSoundInstance extends BaseCarLoopSound {

        private DriftSoundInstance(SportsCarEntity car) {
            super(car, ModSounds.SPORTS_CAR_DRIFT.get());
            this.volume = 0.08F;
            this.pitch = 0.95F;
        }

        @Override
        protected void updateAudio(SportsCarEntity car) {
            if (isHydroSoundActive(car) || !car.isDrifting() || !car.onGround()) {
                this.volume = this.volume * 0.68F - 0.04F;
                if (this.volume <= 0.02F) {
                    finish();
                }
                return;
            }

            float speedRatio = Mth.clamp(Math.abs(car.getForwardSpeed()) / 0.90F, 0.0F, 1.2F);
            float targetVolume = Mth.clamp(0.28F + 0.52F * speedRatio, 0.25F, 0.80F);
            float targetPitch = Mth.clamp(0.88F + 0.26F * speedRatio, 0.85F, 1.25F);

            this.volume = Mth.lerp(0.30F, this.volume, targetVolume);
            this.pitch = Mth.lerp(0.25F, this.pitch, targetPitch);
        }
    }

    /**
     * 水面滑行破浪声：随水上车速、甩尾激浪与氮气实时调节音量和音调。
     */
    private static final class WaterGlideSoundInstance extends BaseCarLoopSound {

        private WaterGlideSoundInstance(SportsCarEntity car) {
            super(car, ModSounds.SPORTS_CAR_WATER_GLIDE.get());
            this.volume = 0.08F;
            this.pitch = 0.85F;
        }

        @Override
        protected void updateAudio(SportsCarEntity car) {
            float speedRatio = Mth.clamp(Math.abs(car.getForwardSpeed()) / 0.88F, 0.0F, 1.5F);
            boolean active = isHydroSoundActive(car) && (speedRatio > 0.03F || car.isDrifting());
            if (!active) {
                this.volume = this.volume * 0.72F - 0.035F;
                if (this.volume <= 0.02F) {
                    finish();
                }
                return;
            }

            float targetVolume = Mth.clamp(
                    0.22F + 0.56F * Mth.clamp(speedRatio, 0.0F, 1.15F)
                            + (car.isDrifting() ? 0.14F : 0.0F)
                            + (car.isNitroActive() ? 0.12F : 0.0F),
                    0.18F, 0.92F);
            float targetPitch = Mth.clamp(
                    0.78F + 0.44F * Mth.clamp(speedRatio, 0.0F, 1.25F)
                            + (car.isDrifting() ? 0.12F : 0.0F)
                            + (car.isNitroActive() ? 0.16F : 0.0F),
                    0.74F, 1.55F);

            this.volume = Mth.lerp(0.25F, this.volume, targetVolume);
            this.pitch = Mth.lerp(0.22F, this.pitch, targetPitch);
        }
    }

    /**
     * 氮气涡轮喷射声：氮气开启期间持续高亢喷射，结束后平滑衰减停止。
     */
    private static final class NitroSoundInstance extends BaseCarLoopSound {

        private NitroSoundInstance(SportsCarEntity car) {
            super(car, ModSounds.SPORTS_CAR_NITRO.get());
            this.volume = 0.10F;
            this.pitch = 1.02F;
        }

        @Override
        protected void updateAudio(SportsCarEntity car) {
            if (!car.isNitroActive()) {
                this.volume = this.volume * 0.72F - 0.04F;
                if (this.volume <= 0.02F) {
                    finish();
                }
                return;
            }

            float speedRatio = Mth.clamp(Math.abs(car.getForwardSpeed()) / 1.45F, 0.0F, 1.0F);
            float targetVolume = 0.86F;
            float targetPitch = 1.02F + 0.20F * speedRatio;

            this.volume = Mth.lerp(0.32F, this.volume, targetVolume);
            this.pitch = Mth.lerp(0.22F, this.pitch, targetPitch);
        }
    }

    private abstract static class BaseCarLoopSound extends AbstractTickableSoundInstance {

        protected final SportsCarEntity car;

        protected BaseCarLoopSound(SportsCarEntity car, SoundEvent event) {
            super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.car = car;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
            followPosition();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public final void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !car.isAlive() || car.level() != mc.level) {
                finish();
                return;
            }
            followPosition();
            updateAudio(car);
        }

        protected abstract void updateAudio(SportsCarEntity car);

        protected final void followPosition() {
            this.x = car.getX();
            this.y = car.getY() + 0.6D;
            this.z = car.getZ();
        }

        protected final void finish() {
            stop();
        }
    }
}
