package at.akrea.moonphase.astro

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object MoonAstro {

    private const val MILLIS_PER_DAY = 86_400_000.0

    fun state(instant: Instant): MoonState {
        val t = centuries(instant)
        val a = Fundamentals(t)
        val nut = nutation(t)
        val moon = moonPosition(a)
        val lambda = norm360(moon.longitude + nut.dPsi)
        val moonEq = toEquatorial(lambda, moon.latitude, nut.eps)
        val sun = sunPosition(t, nut)
        val sunEq = toEquatorial(sun.longitude, 0.0, nut.eps)
        val illum = illumination(moonEq, moon.distanceKm, sunEq, sun.distanceKm)
        val lib = libration(a, nut, lambda, moon.latitude, moonEq.ra)
        val elongation = norm360(lambda - sun.longitude)
        val lastNew = previous(PhaseType.NEW, instant.plusNanos(1)).instant
        return MoonState(
            instant = instant,
            illumination = illum.fraction,
            phaseAngle = illum.phaseAngle,
            elongation = elongation,
            waxing = elongation < 180,
            ageDays = (instant.toEpochMilli() - lastNew.toEpochMilli()) / MILLIS_PER_DAY,
            brightLimbAngle = illum.brightLimbAngle,
            axisPositionAngle = lib.p,
            librationLon = lib.l,
            librationLat = lib.b,
            distanceKm = moon.distanceKm,
            declination = moonEq.dec,
        )
    }

    fun events(from: Instant, to: Instant): List<PhaseEvent> {
        if (!from.isBefore(to)) return emptyList()
        val result = ArrayList<PhaseEvent>()
        var k = floorK(julianDay(from)) - 1
        val kEnd = floorK(julianDay(to)) + 1
        while (k <= kEnd) {
            for (type in PhaseType.entries) {
                val instant = eventInstant(k + type.ordinal * 0.25, type)
                if (!instant.isBefore(from) && instant.isBefore(to)) result += PhaseEvent(type, instant)
            }
            k += 1.0
        }
        return result
    }

    fun next(type: PhaseType, after: Instant): PhaseEvent {
        var k = floorK(julianDay(after)) - 1 + type.ordinal * 0.25
        while (true) {
            val instant = eventInstant(k, type)
            if (instant.isAfter(after)) return PhaseEvent(type, instant)
            k += 1.0
        }
    }

    fun previous(type: PhaseType, before: Instant): PhaseEvent {
        var k = floorK(julianDay(before)) + 1 + type.ordinal * 0.25
        while (true) {
            val instant = eventInstant(k, type)
            if (instant.isBefore(before)) return PhaseEvent(type, instant)
            k -= 1.0
        }
    }

    /** Principal phase if its event falls on [date] in [zone], otherwise the phase at local noon. */
    fun phaseName(date: LocalDate, zone: ZoneId): PhaseName =
        principalOn(date, zone)
            ?: intermediate(elongation(date.atTime(LocalTime.NOON).atZone(zone).toInstant()))

    /** Principal phase if its event falls on the local date of [instant], otherwise the phase at [instant]. */
    fun phaseNameAt(instant: Instant, zone: ZoneId): PhaseName =
        principalOn(instant.atZone(zone).toLocalDate(), zone) ?: intermediate(elongation(instant))

    private fun principalOn(date: LocalDate, zone: ZoneId): PhaseName? {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return when (events(start, end).firstOrNull()?.type) {
            PhaseType.NEW -> PhaseName.NEW_MOON
            PhaseType.FIRST_QUARTER -> PhaseName.FIRST_QUARTER
            PhaseType.FULL -> PhaseName.FULL_MOON
            PhaseType.LAST_QUARTER -> PhaseName.LAST_QUARTER
            null -> null
        }
    }

    private fun intermediate(elongation: Double) = when {
        elongation < 90 -> PhaseName.WAXING_CRESCENT
        elongation < 180 -> PhaseName.WAXING_GIBBOUS
        elongation < 270 -> PhaseName.WANING_GIBBOUS
        else -> PhaseName.WANING_CRESCENT
    }

    private fun elongation(instant: Instant): Double {
        val t = centuries(instant)
        val nut = nutation(t)
        return norm360(moonPosition(Fundamentals(t)).longitude - sunPosition(t, nut).longitude + nut.dPsi)
    }

    private fun centuries(instant: Instant) = (ephemerisDay(julianDay(instant)) - J2000) / 36525

    private fun eventInstant(k: Double, type: PhaseType): Instant {
        val jde = phaseJde(k, type)
        return instantOfJulianDay(jde - deltaTSeconds(jde) / 86400.0)
    }
}
