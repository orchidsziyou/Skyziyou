package com.skyziyou.features.sound

import it.unimi.dsi.fastutil.floats.FloatConsumer
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import net.minecraft.client.sounds.JOrbisAudioStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns user-imported audio files (Ogg Vorbis or MP3) into 16-bit interleaved
 * PCM for Java Sound playback. OGG is decoded with the JOrbis copy shipped
 * inside Minecraft; MP3 with the bundled JLayer library.
 *
 * These run on a background worker thread during import (never the render
 * thread), so stream IO cost does not stall the game.
 */
object AudioDecoders {

    /**
     * Hard ceiling on interleaved 16-bit samples: 60 s of 48 kHz stereo.
     * The file-size limit alone cannot bound PCM size (a tiny low-bitrate MP3
     * can expand to a very long stream), so this guards memory on pathological
     * or corrupt files during decode, before the duration check runs.
     */
    private const val MAX_SAMPLES = 60 * 48000 * 2

    fun decode(input: InputStream, format: String): PcmSound = when (format.lowercase()) {
        "ogg" -> decodeOgg(input)
        "mp3" -> decodeMp3(input)
        else -> throw SoundImportException("sounds.err_format")
    }

    /**
     * JOrbis emits float [-1,1] samples grouped in arbitrary chunk sizes; we
     * grow one backing array and convert everything to signed 16-bit at the end.
     */
    private fun decodeOgg(raw: InputStream): PcmSound =
        try {
            JOrbisAudioStream(raw).use { stream ->
                val format = stream.format // javax.sound.sampled.AudioFormat
                var data = FloatArray(8192)
                var size = 0
                val collector = FloatConsumer { value ->
                    if (size >= MAX_SAMPLES) throw SoundImportException("sounds.err_length")
                    if (size == data.size) data = data.copyOf(data.size * 2)
                    data[size++] = value
                }
                while (stream.readChunk(collector)) {
                    // readChunk returns false at end of stream
                }
                if (size == 0) throw SoundImportException("sounds.err_decode")

                // playback accepts mono or interleaved stereo only; reject surround (>2 ch)
                // streams rather than coercing the channel count and playing garbage.
                if (format.channels !in 1..2) throw SoundImportException("sounds.err_decode")
                val channels = format.channels
                val rate = format.sampleRate.toInt()
                if (rate !in 8000..192000) throw SoundImportException("sounds.err_decode")

                val samples = ShortArray(size)
                for (i in 0 until size) {
                    val clamped = data[i].coerceIn(-1f, 1f)
                    samples[i] = (clamped * 32767f).toInt().toShort()
                }
                PcmSound(rate, channels, samples)
            }
        } catch (e: SoundImportException) {
            throw e
        } catch (e: Throwable) {
            throw SoundImportException("sounds.err_decode", e)
        }

    /**
     * JLayer hands back one [SampleBuffer] per decoded MP3 frame; its short
     * buffer is already interleaved L/R for stereo files.
     */
    private fun decodeMp3(raw: InputStream): PcmSound =
        try {
            // JLayer's Bitstream has close() but predates AutoCloseable, so no .use{}
            val bitstream = Bitstream(raw)
            try {
                val decoder = Decoder()
                val pcmBytes = java.io.ByteArrayOutputStream()

                var sampleRate = 0
                var channels = 0
                var totalSamples = 0
                while (true) {
                    val header = bitstream.readFrame() ?: break
                    val output = decoder.decodeFrame(header, bitstream) as? SampleBuffer
                        ?: throw SoundImportException("sounds.err_decode")
                    sampleRate = output.sampleFrequency
                    channels = output.channelCount
                    totalSamples += output.bufferLength
                    if (totalSamples > MAX_SAMPLES) throw SoundImportException("sounds.err_length")
                    val frame = output.buffer
                    for (i in 0 until output.bufferLength) {
                        val sample = frame[i].toInt()
                        pcmBytes.write(sample and 0xFF)
                        pcmBytes.write((sample shr 8) and 0xFF)
                    }
                    bitstream.closeFrame()
                }

                if (sampleRate <= 0 || channels <= 0 || pcmBytes.size() == 0) {
                    throw SoundImportException("sounds.err_decode")
                }
                if (channels !in 1..2 || sampleRate !in 8000..192000) {
                    throw SoundImportException("sounds.err_decode")
                }
                val bytes = pcmBytes.toByteArray()
                val shorts = ShortArray(bytes.size / 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                PcmSound(sampleRate, channels.coerceIn(1, 2), shorts)
            } finally {
                runCatching { bitstream.close() }
            }
        } catch (e: SoundImportException) {
            throw e
        } catch (e: Throwable) {
            throw SoundImportException("sounds.err_decode", e)
        }
}
