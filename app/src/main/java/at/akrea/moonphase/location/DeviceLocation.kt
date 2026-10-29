package at.akrea.moonphase.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Fix(val latitude: Double, val longitude: Double, val time: Long)

/** One-shot position rounded to about 1 km; never runs in the background. */
object DeviceLocation {

    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private const val TIMEOUT_MS = 30_000L

    fun hasPermission(context: Context) =
        permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    suspend fun fetch(context: Context): Fix? =
        locate(context)?.let { Fix(round(it.latitude), round(it.longitude), it.time) }

    private fun round(value: Double) = Math.round(value * 100) / 100.0

    private suspend fun locate(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = manager.getProviders(true)
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { it in enabled }

        return withTimeoutOrNull(TIMEOUT_MS) { firstFix(context, manager, candidates) }
            ?: enabled.mapNotNull { lastKnown(manager, it) }.maxByOrNull { it.time }
    }

    private suspend fun firstFix(context: Context, manager: LocationManager, providers: List<String>): Location? =
        coroutineScope {
            val first = CompletableDeferred<Location?>()
            val requests = providers.map { provider ->
                launch { current(context, manager, provider)?.let { first.complete(it) } }
            }
            launch {
                requests.joinAll()
                first.complete(null)
            }
            first.await().also { coroutineContext.cancelChildren() }
        }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, provider: String) =
        try {
            manager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        }

    @SuppressLint("MissingPermission")
    private suspend fun current(context: Context, manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                    cont.invokeOnCancellation { signal.cancel() }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                }
            } catch (e: SecurityException) {
                cont.resume(null)
            } catch (e: IllegalArgumentException) {
                cont.resume(null)
            }
        }
}
