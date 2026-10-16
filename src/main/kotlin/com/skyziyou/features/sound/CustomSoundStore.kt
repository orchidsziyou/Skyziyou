package com.skyziyou.features.sound

import com.skyziyou.CustomSoundEntry
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Owns the files behind user-imported custom sounds.
 *
 * Layout: `.minecraft/config/skyziyou/sounds/<uuid>.<ogg|mp3>` — the original
 * uploaded file is kept verbatim on disk (plan A: import-time decode, internal
 * PCM playback, no re-encoding). Per-plan rules, nothing here ever changes
 * game resources or touches the network.
 *
 * Import is a blocking, background-thread operation: it copies the file, then
 * fully decodes it both to validate it and to hand the PCM straight to
 * [CustomSoundPlayer] so the sound is audible immediately with no second pass.
 */
object CustomSoundStore {

    /** Notification clips only: reject absurdly large uploads. */
    const val MAX_FILE_BYTES = 10L * 1024 * 1024

    /** Reject anything longer than a minute — keeps RAM use (PCM cache) bounded. */
    const val MAX_DURATION_SECONDS = 60.0

    private val supportedExtensions = setOf("ogg", "mp3")

    private val safeName = Regex("[A-Za-z0-9-]{8,}\\.(ogg|mp3)")

    private val directory
        get() = FabricLoader.getInstance().configDir.resolve("skyziyou").resolve("sounds")

    /** Resolves a stored file, rejecting anything that escapes the sounds directory (defence against hand-edited configs). */
    private fun storedPath(fileName: String): java.nio.file.Path? {
        if (!safeName.matches(fileName)) return null
        return directory.resolve(fileName).takeIf { it.parent == directory }
    }

    /** Suggested display name for a picked file, without extension. */
    fun displayNameOf(sourcePath: String): String =
        java.io.File(sourcePath).nameWithoutExtension.take(32).ifBlank { "sound" }

    /**
     * Copies and imports the file at [sourcePath], registering it under
     * [displayName]. Returns the metadata entry (still to be added to config)
     * plus the decoded PCM for instant preloading. Throws [SoundImportException]
     * with a localisable reason key on any failure; a half-copied file is cleaned up.
     */
    fun importFile(sourcePath: String, displayName: String): Pair<CustomSoundEntry, PcmSound> {
        val source = java.io.File(sourcePath)
        if (!source.isFile) throw SoundImportException("sounds.err_read")

        val format = source.extension.lowercase()
        if (format !in supportedExtensions) throw SoundImportException("sounds.err_format")
        if (source.length() <= 0) throw SoundImportException("sounds.err_read")
        if (source.length() > MAX_FILE_BYTES) throw SoundImportException("sounds.err_size")

        Files.createDirectories(directory)
        val id = UUID.randomUUID().toString()
        val storedName = "$id.$format"
        val target = directory.resolve(storedName)
        if (target.parent != directory) throw SoundImportException("sounds.err_read")
        try {
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
            val pcm = Files.newInputStream(target).buffered().use { AudioDecoders.decode(it, format) }
            if (pcm.durationSeconds <= 0.05) throw SoundImportException("sounds.err_decode")
            if (pcm.durationSeconds > MAX_DURATION_SECONDS) throw SoundImportException("sounds.err_length")

            val finalName = displayName.trim().ifBlank { displayNameOf(sourcePath) }
            return CustomSoundEntry(id = id, name = finalName, file = storedName, format = format) to pcm
        } catch (e: SoundImportException) {
            runCatching { Files.deleteIfExists(target) }
            throw e
        } catch (e: Throwable) {
            runCatching { Files.deleteIfExists(target) }
            throw SoundImportException("sounds.err_read", e)
        }
    }

    /** Decodes a stored sound from disk; null if the file is missing or corrupt. */
    fun loadPcm(entry: CustomSoundEntry): PcmSound? {
        val path = storedPath(entry.file) ?: return null
        return runCatching {
            Files.newInputStream(path).buffered().use { AudioDecoders.decode(it, entry.format) }
        }.getOrNull()
    }

    /** Best-effort deletion of the file backing [entry]. */
    fun deleteFile(entry: CustomSoundEntry) {
        val path = storedPath(entry.file) ?: return
        runCatching { Files.deleteIfExists(path) }
    }
}
