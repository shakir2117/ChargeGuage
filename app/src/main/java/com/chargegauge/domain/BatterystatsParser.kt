package com.chargegauge.domain

data class DrainEntry(
    val label: String,
    val uid: Int?,
    val mah: Double,
)

data class DrainReport(
    val computedDrainMah: Double?,
    val entries: List<DrainEntry>,
)

object BatterystatsParser {
    private val computedDrain = Regex("""Computed drain:\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val userApp = Regex("""Uid u(\d+)a(\d+):\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val rawUid = Regex("""Uid (\d+):\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val named = Regex("""^([^:]+):\s*(-?\d+(?:\.\d+)?)""")

    fun parse(text: String): DrainReport {
        val lines = text.lineSequence().toList()
        val start = lines.indexOfFirst { it.contains("Estimated power use", ignoreCase = true) }
        if (start < 0) return DrainReport(null, emptyList())

        val section = mutableListOf<String>()
        for (line in lines.drop(start + 1)) {
            if (line.isBlank()) {
                if (section.isNotEmpty()) break
                continue
            }
            if (!line.first().isWhitespace()) break
            section += line.trim()
        }

        var computed: Double? = null
        val entries = mutableListOf<DrainEntry>()
        for (line in section) {
            computedDrain.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { computed = it }
            val userMatch = userApp.find(line)
            if (userMatch != null) {
                val userId = userMatch.groupValues[1].toInt()
                val appNumber = userMatch.groupValues[2].toInt()
                val mah = userMatch.groupValues[3].toDouble()
                val uid = userId * 100_000 + 10_000 + appNumber
                if (mah > 0.0) entries += DrainEntry("UID $uid", uid, mah)
                continue
            }
            val rawMatch = rawUid.find(line)
            if (rawMatch != null) {
                val uid = rawMatch.groupValues[1].toInt()
                val mah = rawMatch.groupValues[2].toDouble()
                if (mah > 0.0) entries += DrainEntry(labelForRawUid(uid), uid, mah)
                continue
            }
            val namedMatch = named.matchEntire(line) ?: continue
            val label = namedMatch.groupValues[1].trim()
            if (label.startsWith("Capacity", ignoreCase = true)) continue
            val mah = namedMatch.groupValues[2].toDoubleOrNull() ?: continue
            if (mah > 0.0) entries += DrainEntry(label, null, mah)
        }
        return DrainReport(computed, entries.sortedByDescending { it.mah })
    }

    private fun labelForRawUid(uid: Int): String {
        return when (uid) {
            1000 -> "Android system"
            else -> "UID $uid"
        }
    }
}
