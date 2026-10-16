package com.skyziyou.features.sound

import com.skyziyou.I18n
import com.skyziyou.SkyziyouConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent

/**
 * Curated pick list of built-in vanilla sound events for [ChatSounds]: a handful
 * of pleasant "ding"-style UI sounds, funny animal calls and a few harsh alerts.
 * Only sounds shipped with the game are used, so there is no resource pack, no
 * OGG import and no asset reload — every id below already exists in the sound
 * registry once the game has loaded.
 *
 * Each [Choice] stores the sound's identifier path under the "minecraft"
 * namespace; the [Holder] is resolved lazily from [BuiltInRegistries.SOUND_EVENT]
 * (constants moved around between versions, the id stays stable).
 */
object SoundLibrary {

    data class Choice(val path: String, val labelKey: String)

    val CHOICES: List<Choice> = listOf(
        // gentle dings / chimes
        Choice("entity.experience_orb.pickup", "sounds.choice.experience_orb"),
        Choice("block.note_block.pling", "sounds.choice.pling"),
        Choice("block.note_block.bell", "sounds.choice.bell"),
        Choice("block.note_block.chime", "sounds.choice.chime"),
        Choice("block.note_block.harp", "sounds.choice.harp"),
        Choice("block.note_block.bit", "sounds.choice.bit"),
        Choice("block.note_block.cow_bell", "sounds.choice.cow_bell"),
        Choice("entity.player.levelup", "sounds.choice.levelup"),
        Choice("ui.toast.in", "sounds.choice.toast"),
        Choice("ui.button.click", "sounds.choice.click"),
        // animals / funny
        Choice("entity.cat.ambient", "sounds.choice.cat"),
        Choice("entity.wolf.ambient", "sounds.choice.wolf"),
        Choice("entity.chicken.ambient", "sounds.choice.chicken"),
        Choice("entity.pig.ambient", "sounds.choice.pig"),
        Choice("entity.cow.ambient", "sounds.choice.cow"),
        Choice("entity.sheep.ambient", "sounds.choice.sheep"),
        Choice("entity.fox.ambient", "sounds.choice.fox"),
        Choice("entity.frog.ambient", "sounds.choice.frog"),
        Choice("entity.villager.yes", "sounds.choice.villager_yes"),
        Choice("entity.villager.no", "sounds.choice.villager_no"),
        // harsh / unmissable
        Choice("entity.arrow.hit_player", "sounds.choice.arrow"),
        Choice("block.anvil.land", "sounds.choice.anvil"),
        Choice("entity.ender_dragon.growl", "sounds.choice.dragon"),
    )

    /** path -> resolved holder, or null for an unknown/missing id; cached after first lookup. */
    private val cache = HashMap<String, Holder<SoundEvent>?>()

    /** Pseudo-namespace marking a rule's sound as a user-imported custom sound id. */
    const val CUSTOM_PREFIX = "custom:"

    fun isCustom(path: String): Boolean = path.startsWith(CUSTOM_PREFIX)

    /** Default choice for a newly added rule. */
    val DEFAULT: Choice = CHOICES.first()

    fun resolve(path: String): Holder<SoundEvent>? =
        cache.getOrPut(path) {
            runCatching {
                BuiltInRegistries.SOUND_EVENT.get(Identifier.withDefaultNamespace(path)).orElse(null)
            }.getOrNull()
        }

    /** Plays the sound as a non-positional UI sound; an unknown id is ignored silently. */
    fun play(path: String, volume: Float = 1.0f, pitch: Float = 1.0f) {
        val event = resolve(path)?.value() ?: return
        val mc = Minecraft.getInstance()
        // forUI = 2D MASTER-channel sound: no distance falloff, no panning
        mc.soundManager.play(SimpleSoundInstance.forUI(event, pitch, volume))
    }

    /**
     * Plays either a vanilla registry sound or a user-imported custom sound,
     * depending on whether [path] carries the [CUSTOM_PREFIX].
     */
    fun playAny(path: String, volume: Float = 1.0f, pitch: Float = 1.0f) {
        if (isCustom(path)) {
            CustomSoundPlayer.play(path.removePrefix(CUSTOM_PREFIX))
        } else {
            play(path, volume, pitch)
        }
    }

    /** Localised short label for the picker; custom sounds show their user-given name. */
    fun label(path: String): String {
        if (isCustom(path)) {
            val id = path.removePrefix(CUSTOM_PREFIX)
            return SkyziyouConfig.customSounds.firstOrNull { it.id == id }?.name
                ?: I18n.t("sounds.custom_missing")
        }
        val key = CHOICES.firstOrNull { it.path == path }?.labelKey ?: return path
        return I18n.t(key)
    }

    /** Reference string stored in a rule for one imported custom sound. */
    fun customRef(id: String): String = CUSTOM_PREFIX + id
}
