package com.skyziyou.features.mineshaft

import com.skyziyou.SkyziyouConfig
import com.skyziyou.features.ActionQueue
import com.skyziyou.features.chat.ChatClassifier
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Reports the frozen corpses of a Glacial Tunnels mineshaft to party chat once,
 * right after entering the shaft.
 *
 * Data source is the "Frozen Corpses:" widget in the tab list: every corpse is one
 * fake-player entry reading ` <type>: NOT LOOTED` (or `LOOTED` later), so the count
 * and types are available immediately on join — no entity scanning and no view
 * distance involved. The widget only exists inside a mineshaft, which doubles as
 * the area detection.
 *
 * A one-shot edge state machine fires a single `/pc ...` when the widget appears;
 * it rearms once the widget disappears (leaving the shaft) or on disconnect.
 */
object MineshaftCorpseReporter {

    /** Corpse types in canonical display order. */
    private val types = listOf("Lapis", "Tungsten", "Umber", "Vanguard")

    private val corpseLine = Regex("""^\s*(Lapis|Tungsten|Umber|Vanguard): (?:NOT )?LOOTED\s*$""", RegexOption.IGNORE_CASE)

    private val mc: Minecraft get() = Minecraft.getInstance()

    /** While false the report for the current shaft has already been sent. */
    private var armed = true
    private var tickCounter = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
    }

    private fun onTick() {
        // once per second is plenty: tab entries arrive asynchronously after joining
        if (++tickCounter % 20 != 0) return
        if (!SkyziyouConfig.corpseReportEnabled) {
            // feature off: stay armed so enabling it mid-shaft still reports once
            armed = true
            return
        }
        if (mc.player == null) {
            // disconnected: rearm for the next shaft
            armed = true
            return
        }

        val lines = readTabLines()
        val widgetVisible = lines.any { it.contains("Frozen Corpses", ignoreCase = true) }
        if (!widgetVisible) {
            armed = true
            return
        }

        if (!armed) return
        val counts = countCorpses(lines)
        if (counts.values.sum() == 0) return

        if (SkyziyouConfig.corpseReportHypixelOnly && !ChatClassifier.isOnHypixel()) return

        armed = false
        sendReport(counts)
    }

    /** Plain text of every listed tab entry (section colours are stripped). */
    private fun readTabLines(): List<String> {
        val connection = mc.player?.connection ?: return emptyList()
        return connection.onlinePlayers.mapNotNull { it.tabListDisplayName?.string }
    }

    /** Counts matching corpse lines per type; names are normalised to canonical case. */
    private fun countCorpses(lines: List<String>): Map<String, Int> {
        val counts = types.associateWith { 0 }.toMutableMap()
        for (line in lines) {
            val name = corpseLine.matchEntire(line)?.groupValues?.get(1) ?: continue
            val canonical = types.first { it.equals(name, ignoreCase = true) }
            counts[canonical] = (counts[canonical] ?: 0) + 1
        }
        return counts
    }

    /** Sends e.g. `/pc Frozen Corpses (4): 2x Lapis, 1x Umber, 1x Vanguard`. */
    private fun sendReport(counts: Map<String, Int>) {
        val present = types.filter { (counts[it] ?: 0) > 0 }
        val total = present.sumOf { counts[it] ?: 0 }
        val body = present.joinToString(", ") { "${counts[it]}x $it" }
        val message = "Frozen Corpses ($total): $body"

        ActionQueue.enqueueCommand("pc $message")
        mc.player?.sendSystemMessage(
            Component.literal("§7[Corpses] §aReported to party §7-> §f$message"),
        )
    }
}
