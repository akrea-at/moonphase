package at.akrea.moonphase.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import at.akrea.moonphase.astro.MoonAstro
import at.akrea.moonphase.astro.MoonState
import at.akrea.moonphase.render.MoonRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val dayIcons = LruCache<String, ImageBitmap>(100)
private val heroes = LruCache<String, ImageBitmap>(2)

@Composable
fun MoonImage(state: MoonState, flip: Boolean, contentDescription: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val px = with(LocalDensity.current) { minOf(maxWidth, maxHeight).roundToPx() }
        val key = "${state.instant}|$px|$flip"
        val bitmap by produceState(heroes.get(key), key) {
            value = heroes.get(key) ?: withContext(Dispatchers.Default) {
                MoonRenderer.render(context, state, px, flip).asImageBitmap().also { heroes.put(key, it) }
            }
        }
        bitmap?.let { Image(it, contentDescription, Modifier.fillMaxSize()) }
    }
}

/** The moon at local noon of [date], cached across recompositions and pager pages. */
@Composable
fun DayMoon(date: LocalDate, zone: ZoneId, flip: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier) {
        val px = with(LocalDensity.current) { minOf(maxWidth, maxHeight).roundToPx() }
        val key = "$date|$zone|$px|$flip"
        val bitmap by produceState(dayIcons.get(key), key) {
            value = dayIcons.get(key) ?: withContext(Dispatchers.Default) {
                val noon = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
                MoonRenderer.render(context, MoonAstro.state(noon), px, flip).asImageBitmap().also { dayIcons.put(key, it) }
            }
        }
        bitmap?.let { Image(it, null, Modifier.fillMaxSize()) }
    }
}
