package com.chargegauge.domain

object SysfsDumpParser {
    private val linePattern = Regex("""^(\S+)\s+(\S+)\s+(-?\d+(?:\.\d+)?)\s*$""")

    fun parse(text: String): Map<String, Map<String, Double>> {
        val supplies = linkedMapOf<String, MutableMap<String, Double>>()
        var reading = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line == "---SYSFS---") {
                reading = true
                continue
            }
            if (line == "---DUMPSYS---") break
            if (!reading || line.isEmpty()) continue
            val match = linePattern.matchEntire(line) ?: continue
            val supply = match.groupValues[1]
            val key = match.groupValues[2]
            val value = match.groupValues[3].toDoubleOrNull() ?: continue
            supplies.getOrPut(supply) { linkedMapOf() }[key] = value
        }
        return supplies
    }
}
