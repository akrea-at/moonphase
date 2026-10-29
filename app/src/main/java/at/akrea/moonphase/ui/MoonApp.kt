package at.akrea.moonphase.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import at.akrea.moonphase.data.AppSettings
import at.akrea.moonphase.data.LocationMode
import at.akrea.moonphase.data.Settings
import at.akrea.moonphase.location.DeviceLocation
import at.akrea.moonphase.widget.MoonWidgetProvider
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

private const val DAY_MS = 24 * 60 * 60 * 1000L
private val REFRESH = Duration.ofMinutes(10)
private val STALE = Duration.ofMinutes(1)

@Composable
fun MoonApp() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(Settings.load(context)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val now by rememberNow()

    val update: (AppSettings) -> Unit = { changed ->
        Settings.save(context, changed)
        settings = changed
        MoonWidgetProvider.refresh(context)
    }

    LaunchedEffect(Unit) {
        val stale = System.currentTimeMillis() - settings.gpsTime > DAY_MS
        if (settings.mode == LocationMode.GPS && stale && DeviceLocation.hasPermission(context)) {
            DeviceLocation.fetch(context)?.let {
                update(settings.copy(gpsLatitude = it.latitude, gpsLongitude = it.longitude, gpsTime = it.time))
            }
        }
    }

    BackHandler(showSettings) { showSettings = false }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Crossfade(showSettings, label = "screen") { inSettings ->
            if (inSettings) {
                SettingsScreen(settings, update) { showSettings = false }
            } else {
                MainScreen(settings, now) { showSettings = true }
            }
        }
    }
}

@Composable
private fun rememberNow(): State<Instant> {
    val now = remember { mutableStateOf(Instant.now()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val current = Instant.now()
                if (Duration.between(now.value, current) >= STALE) now.value = current
                delay(REFRESH.toMillis())
            }
        }
    }
    return now
}
