package com.chargegauge.data

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.IBinder
import android.util.Log
import com.chargegauge.IPrivilegedBatteryService
import com.chargegauge.domain.DumpsysBatteryParser
import com.chargegauge.domain.PrivilegedExtras
import com.chargegauge.domain.PrivilegedExtrasBuilder
import com.chargegauge.domain.ShizukuAvailability
import com.chargegauge.domain.SysfsDumpParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShizukuGateway(private val context: Context) {
    private val availability = MutableStateFlow(readAvailability())

    val states: StateFlow<ShizukuAvailability> = availability.asStateFlow()

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky { availability.value = readAvailability() }
            Shizuku.addBinderDeadListener { availability.value = readAvailability() }
            Shizuku.addRequestPermissionResultListener { _, result ->
                availability.value = if (result == PackageManager.PERMISSION_GRANTED) {
                    ShizukuAvailability.READY
                } else {
                    ShizukuAvailability.DENIED
                }
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Shizuku listeners were not installed", error)
        }
    }

    fun availability(): ShizukuAvailability = readAvailability().also { availability.value = it }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (error: Throwable) {
            Log.w(TAG, "Shizuku permission request failed", error)
        }
    }

    fun openShizukuApp(): Boolean {
        val packageName = shizukuPackageName() ?: return false
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Could not open Shizuku", error)
            false
        }
    }

    fun readExtras(): PrivilegedExtras? {
        val output = try {
            remote()?.readFuelGauge()
        } catch (error: Throwable) {
            Log.w(TAG, "Fuel gauge read failed", error)
            null
        }?.takeIf { it.isNotBlank() } ?: return null
        return PrivilegedExtrasBuilder.build(
            sysfs = SysfsDumpParser.parse(output),
            dumpsys = DumpsysBatteryParser.parse(output),
        )
    }

    fun readBatterystats(): String? {
        return try {
            remote()?.readBatteryStats()?.takeIf { it.isNotBlank() }
        } catch (error: Throwable) {
            Log.w(TAG, "Battery stats read failed", error)
            null
        }
    }

    private fun readAvailability(): ShizukuAvailability {
        if (!isInstalled()) return ShizukuAvailability.NOT_INSTALLED
        return try {
            if (!Shizuku.pingBinder()) return ShizukuAvailability.STOPPED
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuAvailability.READY
            } else {
                ShizukuAvailability.DENIED
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Shizuku is not ready", error)
            ShizukuAvailability.STOPPED
        }
    }

    private fun isInstalled(): Boolean = shizukuPermission() != null

    private fun shizukuPackageName(): String? = shizukuPermission()?.packageName

    private fun shizukuPermission(): PermissionInfo? {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
        }.getOrNull()
    }

    private fun remote(): IPrivilegedBatteryService? {
        synchronized(bindLock) {
            val current = service
            if (current != null && current.asBinder().isBinderAlive) return current
            if (availability() != ShizukuAvailability.READY) return null
            val latch = CountDownLatch(1)
            var created: IPrivilegedBatteryService? = null
            val nextConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    created = IPrivilegedBatteryService.Stub.asInterface(binder)
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }
            return try {
                Shizuku.bindUserService(userServiceArgs(), nextConnection)
                if (!latch.await(8, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Timed out waiting for the Shizuku battery service")
                    return null
                }
                service = created
                connection = nextConnection
                created
            } catch (error: Throwable) {
                Log.w(TAG, "Could not bind the Shizuku battery service", error)
                null
            }
        }
    }

    private fun userServiceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(ComponentName(context, PrivilegedBatteryUserService::class.java))
            .daemon(false)
            .processNameSuffix("gauge")
            .debuggable(false)
            .version(1)
    }

    private companion object {
        const val TAG = "ShizukuGateway"
        const val REQUEST_CODE = 100
        val bindLock = Any()
    }

    @Volatile
    private var service: IPrivilegedBatteryService? = null
    private var connection: ServiceConnection? = null
}
