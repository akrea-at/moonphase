package at.akrea.moonphase.astro

import java.time.Instant

enum class PhaseType { NEW, FIRST_QUARTER, FULL, LAST_QUARTER }

data class PhaseEvent(val type: PhaseType, val instant: Instant)

enum class PhaseName {
    NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
    FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT,
}

/**
 * Geocentric state of the Moon. All angles in degrees.
 *
 * [phaseAngle] is 0 at full and 180 at new moon; [elongation] is the apparent ecliptic
 * longitude of the Moon minus that of the Sun in [0, 360). [brightLimbAngle] and
 * [axisPositionAngle] are measured from celestial north through east. Libration follows
 * Meeus: positive [librationLon] puts the sub-Earth point at east selenographic longitude.
 */
data class MoonState(
    val instant: Instant,
    val illumination: Double,
    val phaseAngle: Double,
    val elongation: Double,
    val waxing: Boolean,
    val ageDays: Double,
    val brightLimbAngle: Double,
    val axisPositionAngle: Double,
    val librationLon: Double,
    val librationLat: Double,
    val distanceKm: Double,
    val declination: Double,
)
