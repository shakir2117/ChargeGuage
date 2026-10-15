package com.chargegauge.data

import com.chargegauge.IPrivilegedBatteryService
import java.util.concurrent.TimeUnit

class PrivilegedBatteryUserService : IPrivilegedBatteryService.Stub() {
    override fun readFuelGauge(): String = runScript(FUEL_SCRIPT, 200_000)

    override fun readBatteryStats(): String = runScript("/system/bin/dumpsys batterystats", 3_000_000)

    override fun destroy() {
        System.exit(0)
    }

    private fun runScript(script: String, maxChars: Int): String {
        val process = ProcessBuilder("/system/bin/sh", "-c", script)
            .redirectErrorStream(false)
            .start()
        val stderr = Thread { runCatching { process.errorStream.readBytes() } }
        stderr.isDaemon = true
        stderr.start()
        val text = readLimited(process.inputStream, maxChars)
        if (!process.waitFor(20, TimeUnit.SECONDS)) process.destroyForcibly()
        stderr.join(500)
        return text
    }

    private fun readLimited(input: java.io.InputStream, maxChars: Int): String {
        input.bufferedReader().use { reader ->
            val buffer = CharArray(4096)
            val text = StringBuilder()
            var total = 0
            while (total < maxChars) {
                val count = reader.read(buffer)
                if (count < 0) break
                val take = minOf(count, maxChars - total)
                text.append(buffer, 0, take)
                total += count
                if (total >= maxChars) break
            }
            return text.toString()
        }
    }

    private companion object {
        val FUEL_SCRIPT = """
            echo '---SYSFS---'
            for d in /sys/class/power_supply/*; do
              [ -d "${"$"}d" ] || continue
              name=${"$"}(basename "${"$"}d")
              for key in voltage_now current_now charge_full charge_full_design cycle_count health_index fg_asoc battery_cycle; do
                f="${"$"}d/${"$"}key"
                if [ -r "${"$"}f" ]; then
                  printf '%s %s ' "${"$"}name" "${"$"}key"
                  tr -d '\n' < "${"$"}f"
                  printf '\n'
                fi
              done
            done
            echo '---DUMPSYS---'
            /system/bin/dumpsys battery
        """.trimIndent()
    }
}
