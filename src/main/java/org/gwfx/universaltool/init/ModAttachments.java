package org.gwfx.universaltool.init;

import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.skill.PlayerSkillData;
import org.gwfx.universaltool.skill.SkillAttachments;
import org.gwfx.universaltool.waypoint.WaypointData;

/**
 * Consolidated attachment registry hub.
 * Preserves existing {@link WaypointData#ATTACHMENT} ("waypoint_data") and
 * {@link SkillAttachments#SKILL_DATA} ("skill_data") keys and holders so existing
 * saves and callers remain 100% compatible.
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, UniversalToolMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WaypointData>> WAYPOINT_DATA =
            WaypointData.ATTACHMENT;

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerSkillData>> SKILL_DATA =
            SkillAttachments.SKILL_DATA;

    /** 玩家是否已通过手机「贪吃蛇」500 分成就领取过「侵晨」，死亡保留且持久化，防止重复刷取。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SNAKE_QINCHEN_CLAIMED =
            ATTACHMENTS.register("snake_qinchen_claimed", () -> AttachmentType
                    .builder(() -> false)
                    .serialize(Codec.BOOL)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }

    /**
     * Registers all mod attachment types onto the mod event bus in one place.
     * Replaces separate registration calls for {@link WaypointData#ATTACHMENTS} and
     * {@link SkillAttachments#ATTACHMENTS}.
     */
    public static void register(IEventBus modEventBus) {
        WaypointData.ATTACHMENTS.register(modEventBus);
        SkillAttachments.ATTACHMENTS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
    }
}
