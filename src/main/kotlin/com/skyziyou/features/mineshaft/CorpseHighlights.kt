package com.skyziyou.features.mineshaft

import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.highlight.EntityHighlights
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand

/**
 * Highlights the frozen corpses of a Glacial Tunnels mineshaft.
 *
 * A corpse is a plain armour stand whose HEAD slot holds one of four SkyBlock helmets;
 * the "id" inside the stack's custom data component identifies the type reliably, so no
 * name matching is involved (corpse entities carry no useful name). Detection is
 * client-side only: we read entities the server already streams to us, never send
 * packets and never touch world data outside the client.
 *
 * While the "Frozen Corpses" tab widget is present (i.e. inside a mineshaft) the world is
 * scanned once per second; corpses inside [SkyziyouConfig.corpseHighlightRange] get the
 * vanilla glowing outline through [EntityHighlights.forceGlow]. The outline is visible
 * through walls, so the distance limit is what keeps it local — with a small extra
 * margin so walking along the boundary does not flicker.
 */
object CorpseHighlights {

    /** Configurable highlight distance bounds/step, shared with the settings page. */
    const val MIN_RANGE = 8
    const val MAX_RANGE = 48
    const val RANGE_STEP = 4

    /** Scan cadence in client ticks (1 second); armour stands are few, so this is cheap. */
    private const val SCAN_TICKS = 20

    /** Extra blocks kept before releasing, to stop the outline flickering at the boundary. */
    private const val HYSTERESIS = 6.0

    /** Corpse helmet item id (custom data "id") -> type label. */
    private val HELMET_TYPES = mapOf(
        "LAPIS_ARMOR_HELMET" to "Lapis",
        "MINERAL_HELMET" to "Tungsten",
        "ARMOR_OF_YOG_HELMET" to "Umber",
        "VANGUARD_HELMET" to "Vanguard",
    )

    /** Type label -> outline colour (a ChatFormatting/TeamColor name). */
    private val TYPE_COLORS = mapOf(
        "Lapis" to "DARK_BLUE",
        "Tungsten" to "GRAY",
        "Umber" to "GOLD",
        "Vanguard" to "BLUE",
    )

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** entityId -> corpse type, for the ones currently glowing because of this feature. */
    private val tracked = HashMap<Int, String>()

    private var tickCounter = 0
    private var scanLevel: ClientLevel? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
    }

    private fun onTick() {
        if (++tickCounter % SCAN_TICKS != 0) return

        val player = mc.player
        val level = mc.level
        if (player == null || level == null) {
            releaseAll()
            scanLevel = null
            return
        }
        // ids are only meaningful inside one level; a world change invalidates all of them
        if (level !== scanLevel) {
            tracked.clear()
            scanLevel = level
        }
        if (!SkyziyouConfig.corpseHighlightEnabled || !MineshaftCorpseReporter.inMineshaft()) {
            releaseAll()
            return
        }

        val range = SkyziyouConfig.corpseHighlightRange.coerceIn(MIN_RANGE, MAX_RANGE)
        val band = range + HYSTERESIS
        val seen = HashSet<Int>()
        for (entity in level.entitiesForRendering()) {
            val type = typeOf(entity) ?: continue
            val distance = player.distanceTo(entity)
            if (distance > band) continue
            if (distance <= range) {
                tracked[entity.id] = type
            } else if (tracked[entity.id] != type) {
                // outside the range but inside the band: keep an existing outline only
                continue
            }
            // re-assert every scan: the glow map can be reset by an external level/team swap
            EntityHighlights.forceGlow(entity, TYPE_COLORS.getValue(type))
            seen.add(entity.id)
        }

        // release anything that despawned, stopped being a corpse, or moved out of the
        // hysteresis band; only entities still inside the band keep their outline
        val iterator = tracked.iterator()
        while (iterator.hasNext()) {
            val (entityId, type) = iterator.next()
            if (entityId in seen) continue
            val entity = level.getEntity(entityId)
            val stillNear = entity != null &&
                typeOf(entity) == type &&
                player.distanceTo(entity) <= range + HYSTERESIS
            if (stillNear) continue
            EntityHighlights.releaseForced(entityId)
            iterator.remove()
        }
    }

    /** Corpse type of [entity], or null when it is not a corpse armour stand. */
    fun typeOf(entity: Entity): String? {
        val stand = entity as? ArmorStand ?: return null
        val head = stand.getItemBySlot(EquipmentSlot.HEAD)
        if (head.isEmpty) return null
        val id = head.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getString("id")?.orElse(null) ?: return null
        return HELMET_TYPES[id]
    }

    /** Drops every outline this feature owns (feature off, left the shaft, disconnected). */
    private fun releaseAll() {
        if (tracked.isEmpty()) return
        for (entityId in tracked.keys) EntityHighlights.releaseForced(entityId)
        tracked.clear()
    }

    // ---- diagnostics (shown by the Debug button on the Entity Highlights page) ----

    data class DebugCorpse(val type: String, val distance: Double, val glowing: Boolean)

    /**
     * Corpse armour stands around the player with type and distance, regardless of the
     * highlight range (a wider window so "why is that one not glowing" is answerable).
     */
    fun debugCorpses(): List<DebugCorpse> {
        val level = mc.level ?: return emptyList()
        val player = mc.player ?: return emptyList()
        val window = SkyziyouConfig.corpseHighlightRange.coerceIn(MIN_RANGE, MAX_RANGE) + 16.0
        val result = ArrayList<DebugCorpse>()
        for (entity in level.entitiesForRendering()) {
            val type = typeOf(entity) ?: continue
            val distance = player.distanceTo(entity).toDouble()
            if (distance > window) continue
            result.add(DebugCorpse(type, distance, EntityHighlights.isGlowing(entity.id)))
        }
        return result.sortedBy { it.distance }
    }
}
