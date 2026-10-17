package com.skyziyou.features.sound

import com.skyziyou.SkyziyouConfig
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * Plays user-imported sounds through a **private Java Sound playback path**
 * (`javax.sound.sampled.SourceDataLine`), deliberately independent of Minecraft's
 * own OpenAL device/context.
 *
 * Why not OpenAL: Minecraft owns a single AL context whose static channel pool
 * lazily grows to ~255 sources, and OpenAL Soft's per-context voice budget is
 * 256 mono / only 2 stereo; sharing that context meant our sources could be
 * starved later in a long session, and every F3+T resource reload or Windows
 * audio-device switch (headphones / monitor sleep / Bluetooth) destroys and
 * rebuilds the context on Minecraft's own sound thread, leaving our cached
 * buffer/source names invalid. A Java Sound line instead opens against the OS
 * mixer per play, so it is immune to all of that and self-recovers simply by
 * opening a fresh line next time.
 *
 * All blocking work (disk decode, line IO/drain) runs on short-lived daemon
 * worker threads; [play] itself only snapshots config/volume on the client
 * thread and returns immediately. Concurrency is capped at [MAX_CONCURRENT].
 */
object CustomSoundPlayer {

    /** Never let chat spam open an unbounded number of mixer lines at once. */
    private const val MAX_CONCURRENT = 16

    /** Total decoded PCM kept warm at startup before falling back to lazy decode. */
    private const val WARMUP_BUDGET_BYTES = 64L * 1024 * 1024

    /** Frames written per mixer write (~85 ms at 48 kHz), keeps line latency low. */
    private const val FRAMES_PER_CHUNK = 4096

    private const val FALLBACK_RATE = 48000

    /** id -> decoded PCM for the session (decode once, play many). Thread-safe: workers + UI threads touch it. */
    private val pcmCache = ConcurrentHashMap<String, PcmSound>()

    /** Lines currently playing; bounds simultaneous playback. */
    private val activeCount = AtomicInteger()

    /** Registers freshly imported PCM so the first preview needs no re-decode. */
    fun preload(id: String, pcm: PcmSound) {
        pcmCache[id] = pcm
    }

    /** Drops cached PCM for a deleted sound (already-open lines finish their own copy). */
    fun evict(id: String) {
        pcmCache.remove(id)
    }

    /**
     * Decodes every imported sound off the client thread at startup so the first
     * chat-triggered playback never blocks on disk IO / MP3 decoding. The
     * aggregate preload is capped at [WARMUP_BUDGET_BYTES] (~64 MB of PCM):
     * beyond that, remaining sounds decode lazily on first play.
     */
    fun warmUp() {
        val entries = SkyziyouConfig.customSounds.toList()
        if (entries.isEmpty()) return
        Thread({
            var budget = WARMUP_BUDGET_BYTES
            for (entry in entries) {
                if (budget <= 0) break
                val pcm = CustomSoundStore.loadPcm(entry) ?: continue
                budget -= pcm.samples.size * 2L
                pcmCache.putIfAbsent(entry.id, pcm) // don't overwrite fresher data (imported during warm-up)
            }
        }, "skyziyou-sound-warmup").apply { isDaemon = true }.start()
    }

    /**
     * Plays the sound identified by [id] once. Called on the client thread; this
     * only snapshots what the worker needs (entry lookup + master volume) and
     * returns immediately — never decodes or blocks the game.
     */
    fun play(id: String) {
        if (!tryAcquire()) return
        val entry = SkyziyouConfig.customSounds.firstOrNull { it.id == id }
        val gain = masterVolume()
        if (entry == null || gain <= 0f) {
            activeCount.decrementAndGet()
            return
        }
        Thread({
            try {
                val pcm = pcmCache[id]
                    ?: CustomSoundStore.loadPcm(entry)?.also { pcmCache.putIfAbsent(id, it) }
                    ?: return@Thread
                playThroughLine(pcm, gain)
            } catch (_: Throwable) {
                // a dead/disconnected mixer or transient IO error must never crash the client
            } finally {
                activeCount.decrementAndGet()
            }
        }, "skyziyou-audio").apply { isDaemon = true }.start()
    }

    private fun tryAcquire(): Boolean {
        while (true) {
            val current = activeCount.get()
            if (current >= MAX_CONCURRENT) return false
            if (activeCount.compareAndSet(current, current + 1)) return true
        }
    }

    /**
     * Opens a line for [pcm] and streams it. Some mixers reject exotic sample
     * rates (or, rarely, stereo), so on failure we retry with a 48 kHz
     * resample, then a mono downmix, then both — all cheap linear conversions.
     */
    private fun playThroughLine(original: PcmSound, gain: Float) {
        val resampled = if (original.sampleRate != FALLBACK_RATE) resample(original, FALLBACK_RATE) else original
        val candidates = buildList {
            add(original)
            if (resampled !== original) add(resampled)
            if (original.channels == 2) {
                add(downmixToMono(original))
                if (resampled !== original) add(downmixToMono(resampled))
            }
        }.distinctBy { it.sampleRate to it.channels }

        for (candidate in candidates) {
            val line = openLine(candidate) ?: continue
            try {
                line.start()
                writePcm(line, candidate, gain)
                line.drain() // block the worker until every queued frame has played
                return
            } catch (_: Throwable) {
                // try the next format candidate
            } finally {
                runCatching { line.close() }
            }
        }
        // every candidate rejected by the mixer (no audio device etc.) — fail quietly
    }

    private fun openLine(pcm: PcmSound): SourceDataLine? =
        runCatching {
            // signed, 16-bit, little-endian, mono/stereo at the clip's own rate
            val format = AudioFormat(pcm.sampleRate.toFloat(), 16, pcm.channels, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) return null
            val line = AudioSystem.getLine(info) as SourceDataLine
            line.open(format, FRAMES_PER_CHUNK * pcm.channels * 2 * 4)
            line
        }.getOrNull()

    /** Writes the whole clip to [line] as little-endian 16-bit frames, applying [gain]. */
    private fun writePcm(line: SourceDataLine, pcm: PcmSound, gain: Float) {
        val channels = pcm.channels
        val src = pcm.samples
        val totalFrames = src.size / channels
        val bytesPerChunk = FRAMES_PER_CHUNK * channels * 2
        val buffer = ByteArray(bytesPerChunk)
        val scaled = gain < 0.9999f

        var frame = 0
        while (frame < totalFrames) {
            val frames = minOf(FRAMES_PER_CHUNK, totalFrames - frame)
            val sampleCount = frames * channels
            val byteCount = sampleCount * 2
            val base = frame * channels
            if (scaled) {
                var b = 0
                for (i in 0 until sampleCount) {
                    val v = (src[base + i].toInt() * gain).toInt().coerceIn(-32768, 32767)
                    buffer[b++] = (v and 0xFF).toByte()
                    buffer[b++] = ((v shr 8) and 0xFF).toByte()
                }
            } else {
                // unity gain: bulk little-endian conversion, no per-sample math
                ByteBuffer.wrap(buffer, 0, byteCount)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .put(src, base, sampleCount)
            }
            // write() blocks while the mixer buffer is full — that's the playback clock
            line.write(buffer, 0, byteCount)
            frame += frames
        }
    }

    /** Linear-interpolation resample; used only as a mixer-compatibility fallback. */
    private fun resample(pcm: PcmSound, targetRate: Int): PcmSound {
        if (pcm.sampleRate == targetRate) return pcm
        val channels = pcm.channels
        val srcFrames = pcm.samples.size / channels
        val dstFrames = (srcFrames.toLong() * targetRate / pcm.sampleRate).toInt().coerceAtLeast(1)
        val dst = ShortArray(dstFrames * channels)
        val step = pcm.sampleRate.toDouble() / targetRate
        for (i in 0 until dstFrames) {
            val position = i * step
            val i0 = position.toInt().coerceAtMost(srcFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(srcFrames - 1)
            val fraction = (position - i0).toFloat()
            for (c in 0 until channels) {
                val a = pcm.samples[i0 * channels + c].toInt()
                val b = pcm.samples[i1 * channels + c].toInt()
                dst[i * channels + c] = (a + (b - a) * fraction).toInt().toShort()
            }
        }
        return PcmSound(targetRate, channels, dst)
    }

    /** Equal-power-ish simple average fold; only used as a last-resort fallback. */
    private fun downmixToMono(pcm: PcmSound): PcmSound {
        if (pcm.channels == 1) return pcm
        val srcFrames = pcm.samples.size / pcm.channels
        val dst = ShortArray(srcFrames)
        for (i in 0 until srcFrames) {
            val left = pcm.samples[i * pcm.channels].toInt()
            val right = pcm.samples[i * pcm.channels + 1].toInt()
            dst[i] = ((left + right) / 2).toShort()
        }
        return PcmSound(pcm.sampleRate, 1, dst)
    }

    private fun masterVolume(): Float =
        runCatching {
            Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.MASTER)
        }.getOrDefault(1f).coerceIn(0f, 1f)
}
