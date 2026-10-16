package com.skyziyou.features.sound

/**
 * Decoded user sound ready for OpenAL upload: signed 16-bit interleaved PCM.
 * For mono [samples] is one channel; for stereo samples alternate L/R.
 */
class PcmSound(
    val sampleRate: Int,
    val channels: Int,
    val samples: ShortArray,
) {
    /** Duration in seconds; 0 for an empty/unknown decode. */
    val durationSeconds: Double
        get() = if (sampleRate <= 0 || channels <= 0) 0.0 else samples.size.toDouble() / channels / sampleRate
}

/**
 * A user-supplied audio file failed import. [reasonKey] is an I18n key shown in
 * the import dialog; it exists so the UI can localise the failure while the
 * decoders stay free of any UI dependency.
 */
class SoundImportException(val reasonKey: String, cause: Throwable? = null) : Exception(reasonKey, cause)
