package org.gwfx.universaltool.skill;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.gwfx.universaltool.UniversalToolMod;

/** Registers the player growth attachment. Kept separate so the data class stays loader-free. */
public final class SkillAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, UniversalToolMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerSkillData>> SKILL_DATA =
            ATTACHMENTS.register("skill_data", () -> AttachmentType
                    .builder(PlayerSkillData::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, PlayerSkillData>() {
                        @Override
                        public PlayerSkillData read(IAttachmentHolder holder, CompoundTag tag,
                                                   HolderLookup.Provider provider) {
                            PlayerSkillData data = new PlayerSkillData();
                            data.deserialize(tag);
                            return data;
                        }

                        @Override
                        public CompoundTag write(PlayerSkillData attachment, HolderLookup.Provider provider) {
                            return attachment.serialize();
                        }
                    })
                    .copyOnDeath()
                    .build());

    private SkillAttachments() {
    }
}
