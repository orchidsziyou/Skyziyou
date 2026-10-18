package com.skyziyou.features.chat

import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Guards against user-supplied "catastrophic backtracking" (ReDoS) regexes.
 *
 * Limiting the matched chat line to 256 chars is NOT sufficient: patterns like
 * `(.*a){12}b` have exponential cost in the input length even on modern JDKs
 * (measured: 9 ms on 16 chars, ~300 ms on 24, effectively forever on 256), so a
 * single matching chat line would freeze the client. Before a freshly typed
 * pattern is trusted, it is run against short adversarial probes on a daemon
 * thread with a wall-clock budget; any probe that does not finish in time marks
 * the pattern unsafe and the calling feature disables it once, with a warning.
 *
 * A timed-out probe thread cannot be killed (java.util.regex does not honour
 * interrupts), so probes stay deliberately short (24 chars): even the worst
 * observed exponential pattern finishes the abandoned work in well under a
 * second of one background core, and an unsafe pattern is cached forever, so
 * this happens at most once per pattern.
 */
object RegexSafety {

    private const val BUDGET_MS = 100L
    private const val PROBE_LEN = 24

    /**
     * Adversarial inputs for the classic overlapping-quantifier shapes:
     * runs of one char, alnum/alternation pairs and whitespace, each with a
     * terminator the pattern cannot match (plus the bare run for anchored forms).
     */
    private val probes: List<String> = run {
        val a = "a".repeat(PROBE_LEN)
        buildList {
            add(a + "!")
            add(a)
            add("1".repeat(PROBE_LEN) + "!")
            add("a1".repeat(PROBE_LEN) + "!")
            add("ab".repeat(PROBE_LEN) + "!")
            add(" ".repeat(PROBE_LEN) + "x")
        }
    }

    /**
     * True when every probe matches (or rejects) within [BUDGET_MS]. Runs the
     * check on a throwaway daemon thread so a runaway engine never blocks the
     * client thread beyond the wait budget.
     */
    fun isSafe(regex: Regex): Boolean {
        for (probe in probes) {
            val task = FutureTask { runCatching { regex.containsMatchIn(probe) } }
            val worker = Thread(task, "skyziyou-regex-check").apply { isDaemon = true }
            worker.start()
            try {
                task.get(BUDGET_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                return false
            } catch (_: Throwable) {
                // interruption or unexpected engine error: treat the pattern as untrusted
                return false
            }
        }
        return true
    }
}
