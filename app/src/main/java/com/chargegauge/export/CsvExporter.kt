package com.chargegauge.export

import android.content.Context
import android.net.Uri
import com.chargegauge.data.db.ChargeDao
import com.chargegauge.domain.Readout

class CsvExporter(
    private val context: Context,
    private val dao: ChargeDao,
) {
    suspend fun writeSessions(uri: Uri) {
        val rows = buildString {
            appendLine("id,started_at,ended_at,start_percent,end_percent,plug,duration_seconds,energy_wh,peak_watts,average_watts,max_temp_c,samples")
            for (session in dao.allSessions()) {
                val end = session.endedAt
                val duration = if (end != null) ((end - session.startedAt) / 1000L).toString() else ""
                appendLine(
                    listOf(
                        session.id.toString(),
                        Readout.iso(session.startedAt),
                        end?.let(Readout::iso).orEmpty(),
                        session.startLevel?.toString().orEmpty(),
                        session.endLevel?.toString().orEmpty(),
                        session.plugType,
                        duration,
                        session.energyWh.toString(),
                        session.peakWatts?.toString().orEmpty(),
                        session.averageWatts?.toString().orEmpty(),
                        session.maxTempC?.toString().orEmpty(),
                        session.sampleCount.toString(),
                    ).joinToString(",") { csv(it) },
                )
            }
        }
        write(uri, rows)
    }

    suspend fun writeSamples(uri: Uri) {
        val rows = buildString {
            appendLine("session_id,timestamp,voltage_mv,current_ma,power_w,level_percent,temp_c")
            for (sample in dao.allSamples()) {
                appendLine(
                    listOf(
                        sample.sessionId.toString(),
                        Readout.iso(sample.timestamp),
                        sample.voltageMv?.toString().orEmpty(),
                        sample.currentMa?.toString().orEmpty(),
                        sample.watts?.toString().orEmpty(),
                        sample.level?.toString().orEmpty(),
                        sample.tempC?.toString().orEmpty(),
                    ).joinToString(",") { csv(it) },
                )
            }
        }
        write(uri, rows)
    }

    private fun write(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open the export file")
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' }) "\"$escaped\"" else escaped
    }
}
