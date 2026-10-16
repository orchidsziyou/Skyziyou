package com.skyziyou.features.sound

import com.skyziyou.CustomSoundEntry
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundSource
import org.lwjgl.openal.AL10.AL_BUFFER
import org.lwjgl.openal.AL10.AL_FORMAT_MONO16
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO16
import org.lwjgl.openal.AL10.AL_GAIN
import org.lwjgl.openal.AL10.AL_NO_ERROR
import org.lwjgl.openal.AL10.AL_PITCH
import org.lwjgl.openal.AL10.AL_PLAYING
import org.lwjgl.openal.AL10.AL_POSITION
import org.lwjgl.openal.AL10.AL_ROLLOFF_FACTOR
import org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE
import org.lwjgl.openal.AL10.AL_SOURCE_STATE
import org.lwjgl.openal.AL10.AL_TRUE
import org.lwjgl.openal.AL10.alBufferData
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.openal.AL10.alDeleteSources
import org.lwjgl.openal.AL10.alGenBuffers
import org.lwjgl.openal.AL10.alGenSources
import org.lwjgl.openal.AL10.alGetError
import org.lwjgl.openal.AL10.alGetSourcei
import org.lwjgl.openal.AL10.alIsBuffer
import org.lwjgl.openal.AL10.alSource3f
import org.lwjgl.openal.AL10.alSourcePlay
import org.lwjgl.openal.AL10.alSourcef
import org.lwjgl.openal.AL10.alSourcei
import org.lwjgl.system.MemoryUtil.memAllocShort
import org.lwjgl.system.MemoryUtil.memFree

/**
 * Plays user-imported PCM sounds straight through the OpenAL context Minecraft
 * already owns — bypassing the vanilla sound registry, so imports work at
 * runtime without a resource pack or an F3+T reload.
 *
 * Sources are generated per play and reaped in [tick] once they finish (a
 * simple, leak-free pool). Buffers are cached per sound id. F3+T destroys the
 * whole AL context: buffer names then fail [alIsBuffer], so we lazily rebuild
 * them from the in-memory PCM cache, and dead source names are dropped on the
 * next tick. Every AL call is guarded because the audio device may be missing
 * entirely (user disabled sound / OpenAL failed to init).
 */
object CustomSoundPlayer {

    /** Never let chat spam create unbounded simultaneous sources. */
    private const val MAX_ACTIVE_SOURCES = 16

    /** ~10 s of client ticks: beyond this, stop retrying an undeletable buffer. */
    private const val ZOMBIE_RETRY_TICKS = 200

    /** Total decoded PCM kept warm at startup before falling back to lazy decode. */
    private const val WARMUP_BUDGET_BYTES = 64L * 1024 * 1024

    /** id -> OpenAL buffer name (valid only for the current AL context). */
    private val buffers = HashMap<String, Int>()

    /** id -> decoded PCM kept for context-loss rebuilds for the session. */
    private val pcmCache = HashMap<String, PcmSound>()

    private val activeSources = ArrayList<Int>()

    /** Buffer names whose sound was deleted while a source was still using them. */
    private val zombieBuffers = ArrayList<Pair<Int, Int>>()

    /** Registers freshly imported PCM so the first preview needs no re-decode. */
    fun preload(id: String, pcm: PcmSound) {
        pcmCache[id] = pcm
    }

    /**
     * Decodes every imported sound off the client thread at startup so the first
     * chat-triggered playback never blocks on disk IO / MP3 decoding. Results are
     * handed back to [preload] on the client thread (all AL state stays
     * single-threaded there).
     *
     * The aggregate preload is capped at [WARMUP_BUDGET_BYTES] (~64 MB of PCM):
     * beyond that, remaining sounds are simply decoded lazily on first play, so a
     * large library of max-length clips cannot bloat startup memory.
     */
    fun warmUp() {
        val entries = SkyziyouConfig.customSounds.toList()
        if (entries.isEmpty()) return
        Thread({
            val decoded = ArrayList<Pair<String, PcmSound>>()
            var budget = WARMUP_BUDGET_BYTES
            for (entry in entries) {
                if (budget <= 0) break
                val pcm = CustomSoundStore.loadPcm(entry) ?: continue
                budget -= pcm.samples.size * 2L
                decoded.add(entry.id to pcm)
            }
            if (decoded.isNotEmpty()) {
                Minecraft.getInstance().execute {
                    decoded.forEach { (id, pcm) ->
                        // don't overwrite fresher data (e.g. imported during warm-up)
                        if (!pcmCache.containsKey(id)) preload(id, pcm)
                    }
                }
            }
        }, "skyziyou-sound-warmup").apply { isDaemon = true }.start()
    }

    fun play(id: String) {
        if (activeSources.size >= MAX_ACTIVE_SOURCES) return
        val entry = SkyziyouConfig.customSounds.firstOrNull { it.id == id } ?: return
        val buffer = ensureBuffer(entry) ?: return

        val source = runCatching { alGenSources() }.getOrDefault(0)
        if (source == 0 || clearError()) {
            runCatching { if (source != 0) alDeleteSources(source) }
            return
        }

        alSourcei(source, AL_BUFFER, buffer)
        alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE) // 2D: always relative to the listener
        alSource3f(source, AL_POSITION, 0f, 0f, 0f)
        alSourcef(source, AL_ROLLOFF_FACTOR, 0f) // no distance attenuation
        alSourcef(source, AL_PITCH, 1f)
        alSourcef(source, AL_GAIN, masterVolume())
        if (clearError()) {
            runCatching { alDeleteSources(source) }
            return
        }
        alSourcePlay(source)
        if (!clearError()) activeSources.add(source) else runCatching { alDeleteSources(source) }
    }

    /** Reaps finished sources; called every client tick from ChatSounds. */
    fun tick() {
        if (activeSources.isNotEmpty()) {
            val iterator = activeSources.iterator()
            while (iterator.hasNext()) {
                val source = iterator.next()
                val state = runCatching { alGetSourcei(source, AL_SOURCE_STATE) }.getOrDefault(0)
                if (state != AL_PLAYING) {
                    runCatching { alDeleteSources(source) }
                    clearError()
                    iterator.remove()
                }
            }
        }
        // sources that were using them are gone now, so buffers deleted mid-playback
        // can finally be released
        if (zombieBuffers.isNotEmpty()) {
            val iterator = zombieBuffers.listIterator()
            while (iterator.hasNext()) {
                val (name, attempts) = iterator.next()
                val stillExistsInThisContext = runCatching { alIsBuffer(name) }.getOrDefault(false)
                if (!stillExistsInThisContext) {
                    clearError()
                    iterator.remove() // died with an old AL context (F3+T); nothing to free
                } else {
                    alDeleteBuffers(name)
                    if (!clearError()) {
                        iterator.remove()
                    } else if (attempts + 1 > ZOMBIE_RETRY_TICKS) {
                        iterator.remove() // give up: tiny bounded leak rather than retrying forever
                    } else {
                        iterator.set(name to (attempts + 1))
                    }
                }
            }
        }
    }

    /** Drops all cached state for a deleted sound. */
    fun evict(id: String) {
        pcmCache.remove(id)
        // If the buffer is still attached to a playing source, OpenAL refuses the
        // delete with AL_INVALID_OPERATION — defer it to [tick] instead of leaking.
        buffers.remove(id)?.let { name ->
            alDeleteBuffers(name)
            if (clearError()) zombieBuffers.add(name to 0)
        }
    }

    private fun ensureBuffer(entry: CustomSoundEntry): Int? {
        val existing = buffers[entry.id]
        if (existing != null && runCatching { alIsBuffer(existing) }.getOrDefault(false)) return existing

        val pcm = pcmCache[entry.id]
            ?: CustomSoundStore.loadPcm(entry)?.also { pcmCache[entry.id] = it }
            ?: return null

        return runCatching { upload(entry.id, pcm) }.getOrNull()
    }

    private fun upload(id: String, pcm: PcmSound): Int? {
        val buffer = alGenBuffers()
        if (buffer == 0 || clearError()) return null

        val shortBuffer = memAllocShort(pcm.samples.size)
        try {
            shortBuffer.put(pcm.samples)
            shortBuffer.flip()
            val format = if (pcm.channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
            alBufferData(buffer, format, shortBuffer, pcm.sampleRate)
        } finally {
            memFree(shortBuffer)
        }
        if (clearError()) {
            runCatching { alDeleteBuffers(buffer) }
            return null
        }
        buffers[id] = buffer
        return buffer
    }

    private fun masterVolume(): Float =
        runCatching {
            Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.MASTER)
        }.getOrDefault(1f)

    /** Reads and clears the AL error flag; true if an error was pending. */
    private fun clearError(): Boolean = alGetError() != AL_NO_ERROR
}
