package at.akrea.moonphase.astro

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PhaseNameTest {

    private val vienna = ZoneId.of("Europe/Vienna")
    private val newYork = ZoneId.of("America/New_York")
    private val auckland = ZoneId.of("Pacific/Auckland")
    private val utc = ZoneId.of("UTC")

    private fun date(s: String) = LocalDate.parse(s)

    @Test
    fun fullMoonInVienna() {
        // Full moon 2026-10-26 04:12 UTC, 05:12 CET
        assertEquals(PhaseName.WAXING_GIBBOUS, MoonAstro.phaseName(date("2026-10-25"), vienna))
        assertEquals(PhaseName.FULL_MOON, MoonAstro.phaseName(date("2026-10-26"), vienna))
        assertEquals(PhaseName.WANING_GIBBOUS, MoonAstro.phaseName(date("2026-10-27"), vienna))
    }

    @Test
    fun quartersAndCrescents() {
        assertEquals(PhaseName.FIRST_QUARTER, MoonAstro.phaseName(date("2026-10-18"), vienna))
        assertEquals(PhaseName.WAXING_CRESCENT, MoonAstro.phaseName(date("2026-10-14"), vienna))
        assertEquals(PhaseName.LAST_QUARTER, MoonAstro.phaseName(date("2026-11-01"), vienna))
        assertEquals(PhaseName.WANING_CRESCENT, MoonAstro.phaseName(date("2026-11-05"), vienna))
    }

    @Test
    fun newMoonDependsOnLocalDate() {
        // New moon 2026-12-09 00:52 UTC: 8 December in New York, 9 December in Vienna
        assertEquals(PhaseName.NEW_MOON, MoonAstro.phaseName(date("2026-12-08"), newYork))
        assertEquals(PhaseName.WAXING_CRESCENT, MoonAstro.phaseName(date("2026-12-09"), newYork))
        assertEquals(PhaseName.WANING_CRESCENT, MoonAstro.phaseName(date("2026-12-08"), vienna))
        assertEquals(PhaseName.NEW_MOON, MoonAstro.phaseName(date("2026-12-09"), vienna))
    }

    @Test
    fun southernEasternZoneCrossesDateLine() {
        // Full moon 2026-06-29 23:57 UTC is 30 June 11:57 in Auckland
        assertEquals(PhaseName.FULL_MOON, MoonAstro.phaseName(date("2026-06-29"), utc))
        assertEquals(PhaseName.WANING_GIBBOUS, MoonAstro.phaseName(date("2026-06-30"), utc))
        assertEquals(PhaseName.WAXING_GIBBOUS, MoonAstro.phaseName(date("2026-06-29"), auckland))
        assertEquals(PhaseName.FULL_MOON, MoonAstro.phaseName(date("2026-06-30"), auckland))
    }

    @Test
    fun phaseNameAtUsesLocalDateOfInstant() {
        assertEquals(PhaseName.FULL_MOON, MoonAstro.phaseNameAt(Instant.parse("2026-10-26T22:30:00Z"), vienna))
        assertEquals(PhaseName.WANING_GIBBOUS, MoonAstro.phaseNameAt(Instant.parse("2026-10-26T23:30:00Z"), vienna))
        assertEquals(PhaseName.WAXING_GIBBOUS, MoonAstro.phaseNameAt(Instant.parse("2026-10-25T22:30:00Z"), vienna))
    }
}
