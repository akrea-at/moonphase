package at.akrea.moonphase.ui

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import at.akrea.moonphase.R
import at.akrea.moonphase.astro.PhaseName
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
fun phaseNameRes(name: PhaseName) = when (name) {
    PhaseName.NEW_MOON -> R.string.phase_new_moon
    PhaseName.WAXING_CRESCENT -> R.string.phase_waxing_crescent
    PhaseName.FIRST_QUARTER -> R.string.phase_first_quarter
    PhaseName.WAXING_GIBBOUS -> R.string.phase_waxing_gibbous
    PhaseName.FULL_MOON -> R.string.phase_full_moon
    PhaseName.WANING_GIBBOUS -> R.string.phase_waning_gibbous
    PhaseName.LAST_QUARTER -> R.string.phase_last_quarter
    PhaseName.WANING_CRESCENT -> R.string.phase_waning_crescent
}

class Formats(val locale: Locale, is24Hour: Boolean) {
    private val clock = if (is24Hour) "Hm" else "hma"

    val time = pattern(clock)
    val shortDate = pattern("EEEdMMM")
    val longDate = pattern("EEEEdMMMMyyyy")
    val month = pattern("LLLLyyyy")
    val dateTime = pattern("dMMM$clock")

    private fun pattern(skeleton: String) =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

@Composable
fun rememberFormats(): Formats {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) { Formats(locale, is24Hour) }
}
