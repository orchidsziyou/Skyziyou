package com.skyziyou.features.highlight

import com.skyziyou.SkyziyouConfig
import com.skyziyou.mixin.EntityAccessor
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.npc.villager.AbstractVillager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.scores.PlayerTeam
import kotlin.math.sqrt

/**
 * Glows living entities whose names match user-configured entries, using only vanilla
 * mechanics (no render Mixins, works identically on 26.1/26.2):
 *
 *  - [net.minecraft.world.entity.Entity.setGlowingTag] makes the vanilla outline
 *    post-processor render the entity's silhouette, visible through walls;
 *  - the outline colour comes from the entity's scoreboard team colour
 *    (`Entity.getTeamColor()`), so we create one private client-side fake team per
 *    colour ("skyziyou_hl_<COLOR>") and add matched entities by scoreboard name
 *    (their UUID string).
 *
 * Matching is **spawn-driven** (SkyHanni-style) instead of scanning every entity
 * every tick: Fabric's [ClientEntityEvents.ENTITY_LOAD] enqueues a newly tracked
 * living entity once. Hypixel sends the custom name in a metadata packet that can
 * arrive a few ticks AFTER the spawn packet, so each pending entity is retried for
 * up to [RETRY_TICKS]; once matched it leaves the queue permanently. A full world
 * scan happens only once on world join and whenever the user edits the config, so
 * steady-state per-tick work is proportional to the (small) set of glowing mobs,
 * not to every entity in the world.
 *
 * Trade-off: only the 16 [ChatFormatting] colours exist and the team also tints
 * the entity's own name tag (on Hypixel mob names are usually separate armour
 * stands, so this is rarely visible).
 */
object EntityHighlights {

    /** Colours offered in the settings cycle, in button order; black is useless for an outline. */
    val COLORS: List<ChatFormatting> = listOf(
        ChatFormatting.RED,
        ChatFormatting.GOLD,
        ChatFormatting.YELLOW,
        ChatFormatting.GREEN,
        ChatFormatting.AQUA,
        ChatFormatting.BLUE,
        ChatFormatting.LIGHT_PURPLE,
        ChatFormatting.WHITE,
        ChatFormatting.DARK_RED,
        ChatFormatting.DARK_GREEN,
        ChatFormatting.DARK_AQUA,
        ChatFormatting.DARK_BLUE,
        ChatFormatting.DARK_PURPLE,
        ChatFormatting.GRAY,
        ChatFormatting.DARK_GRAY,
    )

    /** How long (client ticks) a freshly spawned entity keeps being name-matched before giving up. */
    private const val RETRY_TICKS = 40

    /** Max horizontal gap between a mob and a hologram armour stand for the name to count as its own. */
    private const val STAND_HORIZONTAL = 2.0

    /** Allowed vertical range of a name stand relative to the mob's feet (name tags sit ~2-3 blocks up). */
    private const val STAND_UP = 3.5
    private const val STAND_DOWN = 1.0

    private const val TEAM_PREFIX = "skyziyou_hl_"

    /** Entity shared-data flag bit for the glowing outline (Entity#setSharedFlag index 6). */
    private const val GLOW_FLAG = 6

    private val mc: Minecraft = Minecraft.getInstance()

    private data class Glow(val colorName: String, val scoreName: String)

    /** entityId -> currently applied colour + scoreboard name (member of our team + glowing). */
    private val glowing = HashMap<Int, Glow>()

    /** Newly tracked entityId -> ticks left in the name-retry window. */
    private val pending = HashMap<Int, Int>()

    /** colour name -> lazily created team on the current scoreboard. */
    private val teams = HashMap<String, PlayerTeam>()

    /** Level whose scoreboard [teams] belong to; a different level forces a cache reset. */
    private var teamLevel: ClientLevel? = null

    /** Last seen signature of the highlight config; a change forces one full world scan. */
    private var configSignature: String? = null

    /**
     * Client-tick counter for the periodic safety-net reconcile. Hologram stands or
     * their name metadata can stream in well after the mob's own spawn event (Hypixel
     * hub batches), so spawn-time retries alone can miss; a cheap full reconcile every
     * [RESCAN_TICKS] makes everything self-heal (0.5 world passes/sec, not per tick).
     */
    private var tickCounter = 0
    private const val RESCAN_TICKS = 40

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        ClientEntityEvents.ENTITY_LOAD.register { entity, _ -> onEntityLoad(entity) }
    }

    /** Enqueues a newly tracked living entity for spawn-time matching (cheap filter only here). */
    private fun onEntityLoad(entity: net.minecraft.world.entity.Entity) {
        if (!SkyziyouConfig.entityHighlightsEnabled) return
        if (SkyziyouConfig.entityHighlights.none { it.enabled && it.name.isNotBlank() }) return
        if (entity !is LivingEntity || entity is ArmorStand) return
        if (entity === mc.player) return
        pending[entity.id] = RETRY_TICKS
    }

    private fun onTick() {
        val level = mc.level
        if (level == null) {
            glowing.clear()
            pending.clear()
            teams.clear()
            teamLevel = null
            configSignature = null
            tickCounter = 0
            return
        }
        // scoreboard object is replaced on world change / relog; our fake teams die with it
        if (level !== teamLevel) {
            glowing.clear()
            teams.clear()
            teamLevel = level
            // world join: entities already present must get one full scan;
            // entities arriving afterwards are handled through ENTITY_LOAD
            configSignature = null
            pending.clear()
            fullScan(level)
            return
        }

        val enabled = SkyziyouConfig.entityHighlightsEnabled &&
            SkyziyouConfig.entityHighlights.any { it.enabled && it.name.isNotBlank() }

        if (!enabled) {
            pending.clear()
            clearAll(level)
            return
        }

        // editing/enabling/disabling/removing entries must affect mobs already spawned:
        // detect it cheaply and do one full reconcile scan; otherwise the periodic
        // safety-net reconcile catches late-arriving hologram names by itself
        val signature = signature()
        val configChanged = signature != configSignature
        if (configChanged) configSignature = signature

        tickCounter++
        if (configChanged || tickCounter >= RESCAN_TICKS) {
            tickCounter = 0
            fullScan(level)
        }

        processPending(level)
        maintainGlows(level)
    }

    /** Tries each queued spawn-time entity against the entries; drops it after the retry window. */
    private fun processPending(level: ClientLevel) {
        if (pending.isEmpty()) return
        val entries = SkyziyouConfig.entityHighlights
        // hologram stands can load before/after the mob; rebuild the (tiny) candidate list once per tick
        val nameStands = collectNameStands(level, entries)
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val (entityId, ticksLeft) = iterator.next()
            val entity = level.getEntity(entityId)
            if (entity == null) {
                // despawned before its name ever mattered
                iterator.remove()
                continue
            }
            val colorName = matchColor(entity, entries, nameStands)
            if (colorName != null && entity is LivingEntity) {
                // pending only ever contains living entities (filtered on load); cast is safe
                applyGlow(level, entity, colorName)
                iterator.remove()
            } else if (ticksLeft <= 1) {
                iterator.remove()
            } else {
                pending[entityId] = ticksLeft - 1
            }
        }
    }

    /**
     * Cheap upkeep of the already-highlighted set only: re-assert the glow flag
     * (a server metadata packet can reset the shared flags byte) and evict entries
     * whose entity has despawned, cleaning their team membership.
     */
    private fun maintainGlows(level: ClientLevel) {
        if (glowing.isEmpty()) return
        val iterator = glowing.iterator()
        while (iterator.hasNext()) {
            val (entityId, glow) = iterator.next()
            val entity = level.getEntity(entityId)
            if (entity == null) {
                removeFromTeam(level, glow)
                iterator.remove()
                continue
            }
            (entity as? LivingEntity)?.setGlowFlag(true)
        }
    }

    /** One-shot reconcile over all living entities; used on world join and config edits only. */
    private fun fullScan(level: ClientLevel) {
        val entries = SkyziyouConfig.entityHighlights
        // Hypixel NPC names live on invisible hologram armour stands, not on the mob itself
        val nameStands = collectNameStands(level, entries)
        val wanted = HashMap<Int, String>()
        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || entity is ArmorStand) continue
            if (entity === mc.player) continue
            val colorName = matchColor(entity, entries, nameStands) ?: continue
            wanted[entity.id] = colorName
        }

        for ((entityId, colorName) in wanted) {
            val entity = level.getEntity(entityId) as? LivingEntity ?: continue
            if (glowing[entityId]?.colorName != colorName) applyGlow(level, entity, colorName)
            entity.setGlowFlag(true)
            // already matched now, no need to keep retrying it
            pending.remove(entityId)
        }

        val iterator = glowing.iterator()
        while (iterator.hasNext()) {
            val (entityId, glow) = iterator.next()
            if (wanted.containsKey(entityId)) continue
            removeFromTeam(level, glow)
            (level.getEntity(entityId) as? LivingEntity)?.setGlowFlag(false)
            iterator.remove()
        }
    }

    /** A hologram holder (armour stand or text display) whose text mentions a configured target. */
    private data class NamedStand(val x: Double, val y: Double, val z: Double, val text: String)

    /**
     * Collects the (usually very few) hologram holders whose text contains any
     * configured target. On Hypixel the visible names of NPCs/mobs are rendered by
     * invisible armour stands (older) or TextDisplay entities (1.20.2+); the mob
     * entity underneath only has a generic name.
     */
    private fun collectNameStands(
        level: ClientLevel,
        entries: List<com.skyziyou.HighlightEntry>,
    ): List<NamedStand> {
        val active = entries.filter { it.enabled && it.name.isNotBlank() }
        if (active.isEmpty()) return emptyList()
        val result = ArrayList<NamedStand>()
        for (entity in level.entitiesForRendering()) {
            val text: String = when (entity) {
                is ArmorStand -> entity.customName?.string
                is Display.TextDisplay -> entity.text.string
                else -> null
            } ?: continue
            if (active.any { text.contains(it.name, ignoreCase = true) }) {
                result.add(NamedStand(entity.x, entity.y, entity.z, text))
            }
        }
        return result
    }

    /**
     * Returns the colour name of the first matching enabled entry, or null.
     * A match is the entity's own display name OR a nearby hologram stand's name
     * (SkyBlock NPCs/mobs carry their visible name on armour stands above the body).
     */
    private fun matchColor(
        entity: net.minecraft.world.entity.Entity,
        entries: List<com.skyziyou.HighlightEntry>,
        nameStands: List<NamedStand>,
    ): String? {
        val ownName = entity.name.string
        return entries.firstOrNull { entry ->
            entry.enabled && entry.name.isNotBlank() && (
                ownName.contains(entry.name, ignoreCase = true) ||
                    nameStands.any { stand ->
                        stand.text.contains(entry.name, ignoreCase = true) && isNameTagOf(entity, stand)
                    }
                )
        }?.color
    }

    /** Whether [stand] sits where [entity]'s floating name tag would be (small box column, up to ~3.5 blocks). */
    private fun isNameTagOf(entity: net.minecraft.world.entity.Entity, stand: NamedStand): Boolean {
        if (kotlin.math.abs(stand.x - entity.x) > STAND_HORIZONTAL) return false
        if (kotlin.math.abs(stand.z - entity.z) > STAND_HORIZONTAL) return false
        val dy = stand.y - entity.y
        return dy >= -STAND_DOWN && dy <= STAND_UP
    }

    /** Puts [entity] into the colour team (recolouring when needed) and flags it glowing. */
    private fun applyGlow(level: ClientLevel, entity: LivingEntity, colorName: String) {
        glowing[entity.id]?.let { if (it.colorName != colorName) removeFromTeam(level, it) }
        val team = teamFor(level, colorName)
        // Membership can move out from under us between scans: Hypixel itself puts some
        // mobs on scoreboard teams, and other glow mods (SkyHanni/SBA/...) may own the
        // holder too. addPlayerToTeam throws if it already belongs to ANY team, so only
        // claim holders that are currently unassigned; an externally owned holder keeps
        // its team's colour while the glow flag alone still gives it a white outline.
        val currentTeam = level.scoreboard.getPlayersTeam(entity.scoreboardName)
        if (currentTeam == null) {
            level.scoreboard.addPlayerToTeam(entity.scoreboardName, team)
        }
        glowing[entity.id] = Glow(colorName, entity.scoreboardName)
        entity.setGlowFlag(true)
    }

    /**
     * Forces the glowing shared flag directly. Entity#setGlowingTag cannot be used on
     * the physical client: it writes the flag from isCurrentlyGlowing(), which reads
     * the same flag back there, so setting it true while false is a no-op.
     */
    private fun LivingEntity.setGlowFlag(glow: Boolean) {
        (this as EntityAccessor).`skyziyou$setSharedFlag`(GLOW_FLAG, glow)
    }

    /** Returns the cached fake team for [colorName], creating it on [level]'s scoreboard. */
    private fun teamFor(level: ClientLevel, colorName: String): PlayerTeam {
        teams[colorName]?.let { return it }
        val scoreboard = level.scoreboard
        val teamName = TEAM_PREFIX + colorName
        // reuse if one somehow already exists, otherwise create + colour it;
        // 26.2 decoupled team colour from ChatFormatting into a TeamColor enum wrapped in Optional
        val team = scoreboard.getPlayerTeam(teamName) ?: scoreboard.addPlayerTeam(teamName).apply {
            //~ if < 26.2 'java.util.Optional.of(net.minecraft.world.scores.TeamColor.valueOf(colorName))' -> 'ChatFormatting.valueOf(colorName)'
            color = java.util.Optional.of(net.minecraft.world.scores.TeamColor.valueOf(colorName))
        }
        teams[colorName] = team
        return team
    }

    /**
     * Removes the glow membership, but only when the holder is still on OUR team
     * object. The server (team join/leave packets on Hypixel) or another glow mod
     * may have moved the holder to another team or removed it meanwhile; the 2-arg
     * removePlayerFromTeam throws IllegalStateException in that case and crashes the
     * game, so the identity check is mandatory.
     */
    private fun removeFromTeam(level: ClientLevel, glow: Glow) {
        val team = teams[glow.colorName] ?: return
        if (level.scoreboard.getPlayersTeam(glow.scoreName) === team) {
            level.scoreboard.removePlayerFromTeam(glow.scoreName, team)
        }
    }

    /** Feature disabled: undo every highlight; empty teams stay registered harmlessly. */
    private fun clearAll(level: ClientLevel) {
        if (glowing.isEmpty()) return
        val iterator = glowing.iterator()
        while (iterator.hasNext()) {
            val (entityId, glow) = iterator.next()
            removeFromTeam(level, glow)
            (level.getEntity(entityId) as? LivingEntity)?.setGlowFlag(false)
            iterator.remove()
        }
    }

    /** Cheap value-signature of the parts of the config that affect matching (master flag included). */
    private fun signature(): String =
        SkyziyouConfig.entityHighlightsEnabled.toString() + "#" +
            SkyziyouConfig.entityHighlights.joinToString(";") { "${it.enabled}|${it.name}|${it.color}" }

    // ---- temporary diagnostics (shown by the Debug button on the settings screen) ----

    data class NearbyEntity(
        val type: String,
        val ownName: String,
        val hologramText: String,
        val dx: Double,
        val dy: Double,
        val dz: Double,
        val distance: Double,
        val state: String,
        /** For living entities: nearest configured-name hologram and fusion verdict. */
        val near: String,
    )

    /**
     * Entities within 6 blocks of the client player with friendly type labels and
     * match state; used by the on-screen Debug overlay to diagnose missed highlights.
     */
    fun debugNearby(): List<NearbyEntity> {
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()
        val entries = SkyziyouConfig.entityHighlights
        val nameStands = collectNameStands(level, entries)
        val result = ArrayList<NearbyEntity>()
        for (entity in level.entitiesForRendering()) {
            if (entity === player) continue
            val dx = entity.x - player.x
            val dy = entity.y - player.y
            val dz = entity.z - player.z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            if (distance > 6.0) continue
            val hologramText = when (entity) {
                is ArmorStand -> entity.customName?.string.orEmpty()
                is Display.TextDisplay -> entity.text.string
                else -> ""
            }
            val near = if (entity is LivingEntity && entity !is ArmorStand) {
                nameStands.map { stand ->
                    val sx = kotlin.math.abs(stand.x - entity.x)
                    val sz = kotlin.math.abs(stand.z - entity.z)
                    val sy = stand.y - entity.y
                    val horizontal = maxOf(sx, sz)
                    val inRange = isNameTagOf(entity, stand)
                    "near:'${stand.text}' dy=${"%.1f".format(sy)} horiz=${"%.1f".format(horizontal)} " +
                        if (inRange) "§aIN" else "§cOUT"
                }.minByOrNull {
                    // closest horizontally within a sane lookup radius
                    Regex("horiz=([0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() ?: 99.0
                }.orEmpty()
            } else ""
            val type = when (entity) {
                is Display.TextDisplay -> "TextDisplay"
                is ArmorStand -> "ArmorStand"
                is AbstractVillager -> "Villager"
                is Player -> "Player"
                is LivingEntity -> "Living"
                is Display -> "Display"
                else -> "Entity"
            }
            val state = when {
                glowing.containsKey(entity.id) -> "GLOW"
                pending.containsKey(entity.id) -> "WAIT"
                else -> "-"
            }
            result.add(
                NearbyEntity(
                    type,
                    entity.name.string,
                    hologramText,
                    dx, dy, dz, distance, state, near,
                ),
            )
        }
        return result.sortedBy { it.distance }.take(14)
    }
}
