package at.akrea.moonphase.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.akrea.moonphase.R
import at.akrea.moonphase.astro.MoonAstro
import at.akrea.moonphase.astro.PhaseEvent
import at.akrea.moonphase.astro.PhaseType
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlin.math.roundToInt

private const val PAGES = 2400
private const val START = PAGES / 2

@Composable
fun MoonCalendar(today: LocalDate, zone: ZoneId, flip: Boolean, formats: Formats) {
    val base = remember { YearMonth.from(today) }
    val pager = rememberPagerState(initialPage = START) { PAGES }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(today) }
    val month = base.plusMonths((pager.currentPage - START).toLong())
    val firstDay = remember(formats.locale) { WeekFields.of(formats.locale).firstDayOfWeek }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formats.month.format(month).replaceFirstChar { it.titlecase(formats.locale) },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        selected = today
                        scope.launch { pager.animateScrollToPage(START + monthsBetween(base, YearMonth.from(today))) }
                    }
                    .padding(vertical = 6.dp),
            )
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month))
            }
            IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
            }
        }
        Row(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
            for (i in 0L until 7L) {
                Text(
                    firstDay.plus(i).getDisplayName(TextStyle.SHORT_STANDALONE, formats.locale).trimEnd('.'),
                    style = MaterialTheme.typography.labelSmall,
                    color = Sky.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cellWidth = maxWidth / 7
            val cellHeight = cellWidth * 1.5f
            val gridHeight by animateDpAsState(cellHeight * weeks(month, firstDay), label = "calendar height")
            HorizontalPager(pager, Modifier.fillMaxWidth().height(gridHeight), verticalAlignment = Alignment.Top) { page ->
                MonthGrid(
                    month = base.plusMonths((page - START).toLong()),
                    firstDay = firstDay,
                    today = today,
                    selected = selected,
                    zone = zone,
                    flip = flip,
                    formats = formats,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    onSelect = { selected = it },
                )
            }
        }
        DayDetail(selected, zone, flip, formats, Modifier.padding(top = 8.dp))
    }
}

private fun offset(month: YearMonth, firstDay: DayOfWeek) = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7

private fun weeks(month: YearMonth, firstDay: DayOfWeek) = (offset(month, firstDay) + month.lengthOfMonth() + 6) / 7

private fun monthsBetween(a: YearMonth, b: YearMonth) = (b.year - a.year) * 12 + (b.monthValue - a.monthValue)

private fun majorEvents(from: LocalDate, until: LocalDate, zone: ZoneId): Map<LocalDate, PhaseEvent> =
    MoonAstro.events(from.atStartOfDay(zone).toInstant(), until.atStartOfDay(zone).toInstant())
        .filter { it.type == PhaseType.NEW || it.type == PhaseType.FULL }
        .associateBy { it.instant.atZone(zone).toLocalDate() }

private fun accent(type: PhaseType) = if (type == PhaseType.FULL) Sky.FullMoon else Sky.NewMoon

@Composable
private fun MonthGrid(
    month: YearMonth,
    firstDay: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    zone: ZoneId,
    flip: Boolean,
    formats: Formats,
    cellWidth: Dp,
    cellHeight: Dp,
    onSelect: (LocalDate) -> Unit,
) {
    val events = remember(month, zone) { majorEvents(month.atDay(1), month.plusMonths(1).atDay(1), zone) }
    val offset = offset(month, firstDay)
    Column {
        for (row in 0 until weeks(month, firstDay)) {
            Row {
                for (col in 0 until 7) {
                    val day = row * 7 + col - offset + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        DayCell(
                            date, events[date], date == today, date == selected, zone, flip, formats,
                            Modifier.size(cellWidth, cellHeight),
                        ) { onSelect(date) }
                    } else {
                        Spacer(Modifier.size(cellWidth, cellHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    event: PhaseEvent?,
    isToday: Boolean,
    isSelected: Boolean,
    zone: ZoneId,
    flip: Boolean,
    formats: Formats,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .padding(2.dp)
            .clip(shape)
            .background(if (isSelected) Sky.PanelHigh else Color.Transparent)
            .then(if (isToday) Modifier.border(1.dp, Sky.Moonlight.copy(alpha = 0.55f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) Sky.Moonlight else Sky.Text,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Box(
            Modifier
                .padding(top = 3.dp)
                .fillMaxWidth(0.64f)
                .aspectRatio(1f)
                .then(if (event != null) Modifier.border(1.dp, accent(event.type).copy(alpha = 0.7f), CircleShape) else Modifier)
                .padding(if (event != null) 2.dp else 0.dp),
        ) {
            DayMoon(date, zone, flip, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        if (event != null) {
            Text(
                formats.time.format(event.instant.atZone(zone)),
                color = accent(event.type),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DayDetail(date: LocalDate, zone: ZoneId, flip: Boolean, formats: Formats, modifier: Modifier = Modifier) {
    val state = remember(date, zone) { MoonAstro.state(date.atTime(LocalTime.NOON).atZone(zone).toInstant()) }
    val name = remember(date, zone) { MoonAstro.phaseName(date, zone) }
    val event = remember(date, zone) { majorEvents(date, date.plusDays(1), zone)[date] }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Sky.Panel)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayMoon(date, zone, flip, Modifier.size(52.dp))
        Column(Modifier.padding(start = 14.dp)) {
            Text(formats.longDate.format(date), style = MaterialTheme.typography.labelLarge, color = Sky.Muted)
            Text(stringResource(phaseNameRes(name)), style = MaterialTheme.typography.titleMedium)
            if (event != null) {
                val time = formats.time.format(event.instant.atZone(zone))
                Text(
                    stringResource(R.string.peak_at, time),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent(event.type),
                )
            }
            Text(
                stringResource(R.string.illumination_noon, (state.illumination * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = Sky.Muted,
            )
        }
    }
}
