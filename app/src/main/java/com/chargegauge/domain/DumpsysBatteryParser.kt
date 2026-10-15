package com.chargegauge.domain

object DumpsysBatteryParser {
    fun parse(text: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        var reading = false
        var seenMarker = false
        for (raw in text.lineSequence()) {
            val trimmed = raw.trim()
            if (trimmed == "---DUMPSYS---") {
                seenMarker = true
                continue
            }
            if (trimmed.startsWith("Current Battery Service state")) {
                reading = true
                continue
            }
            if (!reading && seenMarker && trimmed.contains(":")) {
                reading = true
            }
            if (!reading) continue
            val separator = trimmed.indexOf(':')
            if (separator <= 0) continue
            val key = trimmed.substring(0, separator).trim().lowercase()
            val value = trimmed.substring(separator + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) values[key] = value
        }
        return values
    }
}
