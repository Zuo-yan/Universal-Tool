package org.gwfx.universaltool.client;

import com.november.mcphone.feature.music.client.playback.KnownDuration;
import com.november.mcphone.feature.music.client.playback.PcmAudioStream;
import fr.delthas.javamp3.Sound;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModSounds;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side MP3 audio streamer for Naiwa's belly laugh, tracking the entity's
 * world position each tick and decoding {@code assets/universal_tool/sounds/entity/naiwa_laugh.mp3}
 * via JavaMP3 into MCphone's {@link PcmAudioStream}.
 */
public final class NaiwaLaughSoundPlayer {

    private static final ResourceLocation MP3_LOCATION = ResourceLocation.fromNamespaceAndPath(
            UniversalToolMod.MODID, "sounds/entity/naiwa_laugh.mp3");
    private static final int ID3V2_HEADER_BYTES = 10;
    private static final int MAX_AUDIO_TICKS = 200; // 10 seconds max

    private static final Map<Integer, NaiwaLaughSound> ACTIVE_SOUNDS = new ConcurrentHashMap<>();

    private NaiwaLaughSoundPlayer() {
    }

    /**
     * Starts or stops Naiwa's tracked MP3 laugh sound for {@code entityId}.
     */
    public static void handle(int entityId, int durationTicks, boolean stop) {
        Minecraft mc = Minecraft.getInstance();
        if (stop) {
            stopActive(mc, entityId);
            return;
        }
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(entityId);
        if (entity == null || !entity.isAlive()) {
            return;
        }

        stopActive(mc, entityId);
        NaiwaLaughSound instance = new NaiwaLaughSound(entity, durationTicks);
        ACTIVE_SOUNDS.put(entityId, instance);
        mc.getSoundManager().play(instance);
    }

    private static void stopActive(Minecraft mc, int entityId) {
        NaiwaLaughSound prev = ACTIVE_SOUNDS.remove(entityId);
        if (prev != null) {
            prev.cancel();
            mc.getSoundManager().stop(prev);
        }
    }

    private static AudioStream openMp3Stream() throws IOException {
        InputStream raw = Minecraft.getInstance().getResourceManager().open(MP3_LOCATION);
        BufferedInputStream buffered = new BufferedInputStream(raw);
        try {
            skipId3v2(buffered);
            Sound mp3Sound = new Sound(buffered);
            AudioFormat format = mp3Sound.getAudioFormat();
            if (format.getSampleSizeInBits() != 16
                    || !AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
                    || format.isBigEndian()) {
                mp3Sound.close();
                throw new IOException("Unsupported decoded MP3 PCM format: " + format);
            }
            return new PcmAudioStream(mp3Sound, format, "naiwa_laugh.mp3", KnownDuration.UNKNOWN);
        } catch (IOException | RuntimeException e) {
            try {
                buffered.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e instanceof IOException io ? io : new IOException(e);
        }
    }

    private static void skipId3v2(InputStream in) throws IOException {
        in.mark(ID3V2_HEADER_BYTES);
        byte[] head = in.readNBytes(ID3V2_HEADER_BYTES);
        if (head.length < ID3V2_HEADER_BYTES
                || head[0] != 'I' || head[1] != 'D' || head[2] != '3') {
            in.reset();
            return;
        }
        int size = ((head[6] & 0x7F) << 21)
                | ((head[7] & 0x7F) << 14)
                | ((head[8] & 0x7F) << 7)
                | (head[9] & 0x7F);
        in.skipNBytes(size);
    }

    private static final class NaiwaLaughSound extends AbstractTickableSoundInstance {

        private final Entity source;
        private final int maxTicks;
        private int ticks;

        private NaiwaLaughSound(Entity source, int durationTicks) {
            super(ModSounds.NAIWA_LAUGH.get(), SoundSource.NEUTRAL,
                    SoundInstance.createUnseededRandom());
            this.source = source;
            this.maxTicks = Math.min(MAX_AUDIO_TICKS, Math.max(1, durationTicks) + 6);
            this.volume = 1.8F;
            this.pitch = 1.0F;
            this.looping = false;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
            follow();
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !source.isAlive()) {
                finish();
                return;
            }
            ticks++;
            if (ticks > maxTicks) {
                finish();
                return;
            }
            follow();
        }

        private void follow() {
            this.x = source.getX();
            this.y = source.getY() + 1.4D;
            this.z = source.getZ();
        }

        private void finish() {
            stop();
            ACTIVE_SOUNDS.remove(source.getId(), this);
        }

        private void cancel() {
            stop();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers,
                                                        net.minecraft.client.resources.sounds.Sound sound,
                                                        boolean looping) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return openMp3Stream();
                } catch (Throwable t) {
                    UniversalToolMod.LOGGER.warn("Failed to open Naiwa laugh MP3 stream: {}", t.toString());
                    throw new CompletionException(t);
                }
            }, Util.backgroundExecutor());
        }
    }
}
