package org.gwfx.universaltool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.DimensionType;
import org.gwfx.universaltool.network.PersonalDimensionSyncPayload;
import org.gwfx.universaltool.network.PersonalDimensionReadyPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.space.PersonalRegistryUtil;

public final class PersonalDimensionClientRegistry {
    private PersonalDimensionClientRegistry() {}

    public static void apply(PersonalDimensionSyncPayload payload) {
        Minecraft client = Minecraft.getInstance();
        var connection = client.getConnection();
        if (connection == null) return;
        var key = ResourceKey.create(Registries.DIMENSION, payload.id());
        var dimensions = connection.levels();
        var types = connection.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
        if (payload.remove()) {
            dimensions.remove(key);
            return;
        }
        DimensionType type = DimensionType.DIRECT_CODEC.decode(NbtOps.INSTANCE, payload.serializedType())
                .getOrThrow().getFirst();
        if (!types.containsKey(payload.id())) PersonalRegistryUtil.register(types, payload.id(), type);
        dimensions.add(key);
        PacketDistributor.sendToServer(new PersonalDimensionReadyPayload(payload.id()));
    }
}
