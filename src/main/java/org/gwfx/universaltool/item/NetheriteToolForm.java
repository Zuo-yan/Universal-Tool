package org.gwfx.universaltool.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.gwfx.universaltool.init.ModDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 下界合金万能工具的动态形态枚举与状态组件辅助逻辑：
 * 默认维持折叠星仪基础态，执行挖矿/砍伐/挖掘/耕作/战斗功能时切换为对应兵器形态，超时后自动收拢还原。
 */
public enum NetheriteToolForm {
    BASE("base", 0.0F),
    PICKAXE("pickaxe", 0.2F),
    AXE("axe", 0.4F),
    SHOVEL("shovel", 0.6F),
    HOE("hoe", 0.8F),
    SWORD("sword", 1.0F);

    public static final long MORPH_DURATION_TICKS = 36L;

    private final String id;
    private final float propertyValue;

    NetheriteToolForm(String id, float propertyValue) {
        this.id = id;
        this.propertyValue = propertyValue;
    }

    public String id() {
        return id;
    }

    public float propertyValue() {
        return propertyValue;
    }

    public static NetheriteToolForm byId(String id) {
        if (id == null || id.isEmpty()) {
            return BASE;
        }
        String norm = id.toLowerCase(Locale.ROOT);
        for (NetheriteToolForm form : values()) {
            if (form.id.equals(norm)) {
                return form;
            }
        }
        return BASE;
    }

    public static NetheriteToolForm detectForBlock(BlockState state) {
        if (state.is(Blocks.COBWEB) || state.is(BlockTags.SWORD_EFFICIENT)) {
            return SWORD;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return PICKAXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return AXE;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return SHOVEL;
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return HOE;
        }
        return PICKAXE;
    }

    public static boolean supportsDynamicMorph(ItemStack stack) {
        return stack.getItem() instanceof UniversalToolItem tool && tool.getTier() == Tiers.NETHERITE;
    }

    public static void activate(ItemStack stack, @Nullable Level level, NetheriteToolForm form) {
        if (!supportsDynamicMorph(stack) || level == null || form == BASE) {
            return;
        }
        long now = level.getGameTime();
        long newExpire = now + MORPH_DURATION_TICKS;
        FormData current = stack.get(ModDataComponents.NETHERITE_TOOL_FORM.get());
        // 同一形态且剩余时间充裕时跳过组件写入，减少无谓发包
        if (current != null && current.form() == form && (current.expireGameTime() - now) > (MORPH_DURATION_TICKS - 8L)) {
            return;
        }
        stack.set(ModDataComponents.NETHERITE_TOOL_FORM.get(), new FormData(form.id, newExpire));
    }

    public static NetheriteToolForm resolveActiveForm(ItemStack stack, @Nullable Level level) {
        if (!supportsDynamicMorph(stack)) {
            return BASE;
        }
        FormData data = stack.get(ModDataComponents.NETHERITE_TOOL_FORM.get());
        if (data == null || data.form() == BASE) {
            return BASE;
        }
        if (level != null && level.getGameTime() > data.expireGameTime()) {
            return BASE;
        }
        return data.form();
    }

    public record FormData(String formId, long expireGameTime) {
        public static final Codec<FormData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("form").forGetter(FormData::formId),
                Codec.LONG.fieldOf("expire").forGetter(FormData::expireGameTime)
        ).apply(inst, FormData::new));

        public static final StreamCodec<ByteBuf, FormData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FormData::formId,
                ByteBufCodecs.VAR_LONG, FormData::expireGameTime,
                FormData::new
        );

        public FormData {
            formId = NetheriteToolForm.byId(formId).id;
        }

        public NetheriteToolForm form() {
            return NetheriteToolForm.byId(formId);
        }
    }
}
