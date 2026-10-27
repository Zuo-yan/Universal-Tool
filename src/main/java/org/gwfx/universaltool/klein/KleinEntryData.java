package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import java.math.BigInteger;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record KleinEntryData(long id, ItemStack template, BigInteger amount) {
    public static final StreamCodec<RegistryFriendlyByteBuf, KleinEntryData> STREAM_CODEC = new StreamCodec<>() {
        @Override public KleinEntryData decode(RegistryFriendlyByteBuf buffer) {
            long id = ByteBufCodecs.VAR_LONG.decode(buffer);
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buffer);
            String amount = ByteBufCodecs.STRING_UTF8.decode(buffer);
            if (amount.length() > KleinWarehouse.MAX_AMOUNT_DIGITS) throw new IllegalArgumentException("Klein amount too long");
            return new KleinEntryData(id, stack, new BigInteger(amount));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, KleinEntryData value) {
            ByteBufCodecs.VAR_LONG.encode(buffer, value.id);
            ItemStack.STREAM_CODEC.encode(buffer, value.template.copyWithCount(1));
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.amount.toString());
        }
    };
}
