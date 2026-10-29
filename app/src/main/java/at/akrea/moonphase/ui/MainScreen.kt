package at.akrea.moonphase.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.akrea.moonphase.R
import at.akrea.moonphase.astro.MoonAstro
import at.akrea.moonphase.astro.PhaseEvent
import at.akrea.moonphase.astro.PhaseType
import at.akrea.moonphase.data.AppSettings
import at.akrea.moonphase.data.LocationMode
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun MainScreen(settings: AppSettings, now: Instant, onOpenSettings: () -> Unit) {
    val zone = settings.zone
    val formats = rememberFormats()
    val state = remember(now) { MoonAstro.state(now) }
    val name = remember(now, zone) { MoonAstro.phaseNameAt(now, zone) }
    val nextNew = remember(now) { MoonAstro.next(PhaseType.NEW, now) }
    val nextFull = remember(now) { MoonAstro.next(PhaseType.FULL, now) }
    val phaseName = stringResource(phaseNameRes(name))

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.app_name).uppercase(formats.locale),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 3.sp,
                color = Sky.Muted,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings), tint = Sky.Muted)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(0.8f).widthIn(max = 400.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(
                        Brush.radialGradient(
                            0.0f to Sky.Moonlight.copy(alpha = 0.10f),
                            0.55f to Sky.Moonlight.copy(alpha = 0.04f),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = size.minDimension * 0.62f,
                        ),
                        radius = size.minDimension * 0.62f,
                    )
                }
                MoonImage(state, settings.flip, phaseName, Modifier.fillMaxWidth(0.86f).aspectRatio(1f))
            }
            Text(phaseName, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.illumination, (state.illumination * 100).roundToInt()) + "  ·  " +
                    stringResource(R.string.age_days, state.ageDays),
                style = MaterialTheme.typography.bodyMedium,
                color = Sky.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.padding(top = 24.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EventTile(R.string.next_new_moon, nextNew, zone, formats, Modifier.weight(1f))
                EventTile(R.string.next_full_moon, nextFull, zone, formats, Modifier.weight(1f))
            }
            LocationLine(settings, formats, onOpenSettings)
            Spacer(Modifier.height(20.dp))
            MoonCalendar(now.atZone(zone).toLocalDate(), zone, settings.flip, formats)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EventTile(label: Int, event: PhaseEvent, zone: ZoneId, formats: Formats, modifier: Modifier) {
    val color = if (event.type == PhaseType.FULL) Sky.FullMoon else Sky.NewMoon
    val local = event.instant.atZone(zone)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Sky.Panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = Sky.Muted,
                maxLines = 1,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
        Text(
            formats.shortDate.format(local),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(formats.time.format(local), style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun LocationLine(settings: AppSettings, formats: Formats, onClick: () -> Unit) {
    val zoneId = settings.zone.id
    val text = when (settings.mode) {
        LocationMode.NONE -> stringResource(R.string.location_none, zoneId)
        LocationMode.GPS -> stringResource(R.string.location_device, zoneId)
        LocationMode.PLACE -> settings.place?.let { stringResource(R.string.location_place, it.label(formats.locale), zoneId) }
            ?: stringResource(R.string.location_none, zoneId)
    }
    Row(
        Modifier
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocationOn, null, tint = Sky.Muted, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Sky.Muted,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
