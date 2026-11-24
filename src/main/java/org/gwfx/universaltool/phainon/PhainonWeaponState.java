package org.gwfx.universaltool.phainon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * 白厄武器（侵晨）的状态组件，记录当前【火种 / Coreflame】数量与形态标记（普通 / 变身）。
 */
public record PhainonWeaponState(int coreflame, boolean transformed) {

    /** 火种上限 */
    public static final int MAX_COREFLAME = 12;

    /** 普通战技「黎明创世，地辟天开」单次施放获得的火种点数 */
    public static final int NORMAL_SKILL_COREFLAME_GAIN = 2;

    /** 默认初始状态：0 点火种，普通形态 */
    public static final PhainonWeaponState DEFAULT = new PhainonWeaponState(0, false);

    public static final Codec<PhainonWeaponState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("coreflame").forGetter(PhainonWeaponState::coreflame),
            Codec.BOOL.fieldOf("transformed").forGetter(PhainonWeaponState::transformed)
    ).apply(inst, PhainonWeaponState::new));

    public static final StreamCodec<ByteBuf, PhainonWeaponState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhainonWeaponState::coreflame,
            ByteBufCodecs.BOOL, PhainonWeaponState::transformed,
            PhainonWeaponState::new
    );

    public PhainonWeaponState {
        coreflame = Mth.clamp(coreflame, 0, MAX_COREFLAME);
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
     * 增加或减少火种并返回钳制在 {@code [0, MAX_COREFLAME]} 的新状态实例。
     */
    public PhainonWeaponState withAddedCoreflame(int delta) {
        return new PhainonWeaponState(coreflame + delta, transformed);
    }

    /**
     * 切换变身状态并返回新状态实例。
     */
    public PhainonWeaponState withTransformed(boolean nextTransformed) {
        return new PhainonWeaponState(coreflame, nextTransformed);
    }
}
