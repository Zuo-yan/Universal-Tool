package org.gwfx.universaltool.phainon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * 白厄武器（侵晨）的状态组件，记录当前【火种 / Coreflame】数量、形态标记（普通 / 觉醒）以及累计击杀生物数。
 * <p>每累计击杀 {@value #KILLS_PER_DAMAGE} 个生物，基础攻击伤害 +1，且全部技能伤害同步按
 * {@code (7 + bonusDamage) / 7} 等比例成长。</p>
 */
public record PhainonWeaponState(int coreflame, boolean transformed, int kills) {

    /** 火种上限 */
    public static final int MAX_COREFLAME = 12;

    /** 普通战技「黎明创世，地辟天开」单次施放获得的火种点数 */
    public static final int NORMAL_SKILL_COREFLAME_GAIN = 2;

    /** 每累计击杀多少个生物提升 1 点基础攻击伤害 */
    public static final int KILLS_PER_DAMAGE = 12;

    /** 侵晨初始总攻击伤害（玩家基础 1 + 下界合金 4 + 武器 2 = 7） */
    public static final float BASE_ATTACK_DAMAGE = 7.0F;

    /** 默认初始状态：0 点火种，普通形态，0 击杀 */
    public static final PhainonWeaponState DEFAULT = new PhainonWeaponState(0, false, 0);

    public static final Codec<PhainonWeaponState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("coreflame").forGetter(PhainonWeaponState::coreflame),
            Codec.BOOL.fieldOf("transformed").forGetter(PhainonWeaponState::transformed),
            Codec.INT.optionalFieldOf("kills", 0).forGetter(PhainonWeaponState::kills)
    ).apply(inst, PhainonWeaponState::new));

    public static final StreamCodec<ByteBuf, PhainonWeaponState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhainonWeaponState::coreflame,
            ByteBufCodecs.BOOL, PhainonWeaponState::transformed,
            ByteBufCodecs.VAR_INT, PhainonWeaponState::kills,
            PhainonWeaponState::new
    );

    public PhainonWeaponState {
        coreflame = Mth.clamp(coreflame, 0, MAX_COREFLAME);
        kills = Math.max(0, kills);
    }

    /**
     * 兼容旧构造器（默认 0 击杀）。
     */
    public PhainonWeaponState(int coreflame, boolean transformed) {
        this(coreflame, transformed, 0);
    }

    /**
     * 当前是否处于普通（未变身）形态。
     */
    public boolean isNormalForm() {
        return !transformed;
    }

    public boolean canAwaken() {
        return isNormalForm() && coreflame == MAX_COREFLAME;
    }

    /**
     * 当前由击杀成长提供的额外基础攻击伤害（每 12 杀 +1）。
     */
    public int bonusDamage() {
        return kills / KILLS_PER_DAMAGE;
    }

    /**
     * 当前阶段内已累计的击杀进度（0 ~ 11）。
     */
    public int killsInCurrentStage() {
        return kills % KILLS_PER_DAMAGE;
    }

    /**
     * 距离下一次攻击伤害 +1 还需击杀的生物数（1 ~ 12）。
     */
    public int killsToNextDamage() {
        return KILLS_PER_DAMAGE - killsInCurrentStage();
    }

    /**
     * 全部技能与基础攻击同步共享的伤害成长倍率：{@code (7 + bonusDamage) / 7}。
     */
    public float damageMultiplier() {
        return (BASE_ATTACK_DAMAGE + bonusDamage()) / BASE_ATTACK_DAMAGE;
    }

    /**
     * 按击杀成长倍率缩放技能伤害。
     */
    public float scaleSkillDamage(float baseDamage) {
        return baseDamage * damageMultiplier();
    }

    /**
     * 增加或减少火种并返回新状态（保留累计击杀数）。
     */
    public PhainonWeaponState withAddedCoreflame(int delta) {
        return new PhainonWeaponState(coreflame + delta, transformed, kills);
    }

    /**
     * 切换变身状态并返回新状态（保留累计击杀数）。
     */
    public PhainonWeaponState withTransformed(boolean nextTransformed) {
        return new PhainonWeaponState(coreflame, nextTransformed, kills);
    }

    /**
     * 消耗火种进入觉醒形态（清零火种、开启觉醒，保留累计击杀数）。
     */
    public PhainonWeaponState withAwakened() {
        return new PhainonWeaponState(0, true, kills);
    }

    /**
     * 增加击杀数并返回新状态。
     */
    public PhainonWeaponState withAddedKills(int delta) {
        long next = (long) kills + delta;
        int clamped = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, next));
        return new PhainonWeaponState(coreflame, transformed, clamped);
    }
}
