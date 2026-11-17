package org.gwfx.universaltool.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Custom full-bright Arcane-Cosmic spark particle for wormhole rims, item teleports, and spatial bursts.
 */
public class WormholeSparkParticle extends TextureSheetParticle {
    private final float baseSize;

    protected WormholeSparkParticle(ClientLevel level, double x, double y, double z,
                                    double xSpeed, double ySpeed, double zSpeed,
                                    SpriteSet sprites, boolean warmExit) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.94F;
        this.lifetime = warmExit ? (14 + this.random.nextInt(8)) : (10 + this.random.nextInt(6));
        this.baseSize = (warmExit ? 0.16F : 0.12F) + this.random.nextFloat() * 0.08F;
        this.quadSize = this.baseSize;
        float tint = this.random.nextFloat();
        if (warmExit) {
            this.rCol = 1.0F;
            this.gCol = Mth.lerp(tint, 0.58F, 0.88F);
            this.bCol = Mth.lerp(tint, 0.25F, 0.65F);
        } else {
            this.rCol = Mth.lerp(tint, 0.55F, 0.85F);
            this.gCol = Mth.lerp(tint, 0.92F, 0.68F);
            this.bCol = 1.0F;
        }
        this.alpha = 0.95F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.friction;
        this.yd *= this.friction;
        this.zd *= this.friction;

        float lifeProgress = (float) this.age / (float) this.lifetime;
        this.quadSize = this.baseSize * (1.0F - lifeProgress * 0.65F);
        this.alpha = lifeProgress < 0.15F
                ? Mth.clamp(lifeProgress / 0.15F, 0.1F, 0.95F)
                : Math.max(0.0F, 0.95F * (1.0F - (lifeProgress - 0.15F) / 0.85F));
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new WormholeSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites, false);
        }
    }

    public static class ExitProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ExitProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new WormholeSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites, true);
        }
    }
}
