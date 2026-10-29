package at.akrea.moonphase.astro

import java.time.Instant
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal const val J2000 = 2451545.0
private const val UNIX_EPOCH_JD = 2440587.5
private const val SECONDS_PER_DAY = 86400.0
private const val AU_KM = 149597870.7
private const val LUNAR_INCLINATION = 1.54242

internal fun sind(x: Double) = sin(Math.toRadians(x))
internal fun cosd(x: Double) = cos(Math.toRadians(x))
private fun tand(x: Double) = tan(Math.toRadians(x))
private fun asind(x: Double) = Math.toDegrees(asin(x))
private fun atan2d(y: Double, x: Double) = Math.toDegrees(atan2(y, x))

internal fun norm360(x: Double): Double {
    val r = x % 360.0
    return if (r < 0) r + 360.0 else r
}

internal fun norm180(x: Double): Double {
    val r = norm360(x)
    return if (r >= 180.0) r - 360.0 else r
}

internal fun julianDay(instant: Instant): Double =
    UNIX_EPOCH_JD + (instant.epochSecond + instant.nano / 1e9) / SECONDS_PER_DAY

internal fun instantOfJulianDay(jd: Double): Instant =
    Instant.ofEpochMilli(((jd - UNIX_EPOCH_JD) * SECONDS_PER_DAY * 1000.0).roundToLong())

/** Espenak–Meeus polynomial approximation of TT − UT in seconds. */
internal fun deltaTSeconds(jd: Double): Double {
    val y = 2000.0 + (jd - J2000) / 365.25
    return when {
        y < 1900 -> {
            val u = (y - 1820) / 100
            -20 + 32 * u * u
        }
        y < 1920 -> {
            val t = y - 1900
            -2.79 + t * (1.494119 + t * (-0.0598939 + t * (0.0061966 - t * 0.000197)))
        }
        y < 1941 -> {
            val t = y - 1920
            21.20 + t * (0.84493 + t * (-0.076100 + t * 0.0020936))
        }
        y < 1961 -> {
            val t = y - 1950
            29.07 + 0.407 * t - t * t / 233 + t * t * t / 2547
        }
        y < 1986 -> {
            val t = y - 1975
            45.45 + 1.067 * t - t * t / 260 - t * t * t / 718
        }
        y < 2005 -> {
            val t = y - 2000
            63.86 + t * (0.3345 + t * (-0.060374 + t * (0.0017275 + t * (0.000651814 + t * 0.00002373599))))
        }
        y < 2050 -> {
            val t = y - 2000
            62.92 + 0.32217 * t + 0.005589 * t * t
        }
        y < 2150 -> {
            val u = (y - 1820) / 100
            -20 + 32 * u * u - 0.5628 * (2150 - y)
        }
        else -> {
            val u = (y - 1820) / 100
            -20 + 32 * u * u
        }
    }
}

internal fun ephemerisDay(jd: Double) = jd + deltaTSeconds(jd) / SECONDS_PER_DAY

internal class Fundamentals(val t: Double) {
    private val t2 = t * t
    private val t3 = t2 * t
    private val t4 = t3 * t
    val lp = norm360(218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841 - t4 / 65194000)
    val d = norm360(297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868 - t4 / 113065000)
    val m = norm360(357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000)
    val mp = norm360(134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699 - t4 / 14712000)
    val f = norm360(93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000 + t4 / 863310000)
    val omega = norm360(125.0445479 - 1934.1362891 * t + 0.0020754 * t2 + t3 / 467441 - t4 / 60616000)
    val e = 1 - 0.002516 * t - 0.0000074 * t2
}

internal class Nutation(val dPsi: Double, val dEps: Double, val eps0: Double) {
    val eps get() = eps0 + dEps
}

internal fun nutation(t: Double): Nutation {
    val omega = 125.04452 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000
    val l = 280.4665 + 36000.7698 * t
    val lp = 218.3165 + 481267.8813 * t
    val dPsi = -17.20 * sind(omega) - 1.32 * sind(2 * l) - 0.23 * sind(2 * lp) + 0.21 * sind(2 * omega)
    val dEps = 9.20 * cosd(omega) + 0.57 * cosd(2 * l) + 0.10 * cosd(2 * lp) - 0.09 * cosd(2 * omega)
    val eps0 = 84381.448 - 46.8150 * t - 0.00059 * t * t + 0.001813 * t * t * t
    return Nutation(dPsi / 3600, dEps / 3600, eps0 / 3600)
}

internal class Ecliptic(val longitude: Double, val latitude: Double, val distanceKm: Double)

internal class Equatorial(val ra: Double, val dec: Double)

internal fun toEquatorial(lambda: Double, beta: Double, eps: Double) = Equatorial(
    ra = norm360(atan2d(sind(lambda) * cosd(eps) - tand(beta) * sind(eps), cosd(lambda))),
    dec = asind(sind(beta) * cosd(eps) + cosd(beta) * sind(eps) * sind(lambda)),
)

/** Apparent geocentric longitude of the Sun (ch. 25, low precision). */
internal fun sunPosition(t: Double, nut: Nutation): Ecliptic {
    val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
    val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
    val e = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t
    val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sind(m) +
        (0.019993 - 0.000101 * t) * sind(2 * m) + 0.000289 * sind(3 * m)
    val r = 1.000001018 * (1 - e * e) / (1 + e * cosd(m + c))
    return Ecliptic(norm360(l0 + c - 0.00569 + nut.dPsi), 0.0, r * AU_KM)
}

// D, M, M', F, Σl, Σr (ch. 47, table 47.A)
private val LR_TERMS = intArrayOf(
    0, 0, 1, 0, 6288774, -20905355,
    2, 0, -1, 0, 1274027, -3699111,
    2, 0, 0, 0, 658314, -2955968,
    0, 0, 2, 0, 213618, -569925,
    0, 1, 0, 0, -185116, 48888,
    0, 0, 0, 2, -114332, -3149,
    2, 0, -2, 0, 58793, 246158,
    2, -1, -1, 0, 57066, -152138,
    2, 0, 1, 0, 53322, -170733,
    2, -1, 0, 0, 45758, -204586,
    0, 1, -1, 0, -40923, -129620,
    1, 0, 0, 0, -34720, 108743,
    0, 1, 1, 0, -30383, 104755,
    2, 0, 0, -2, 15327, 10321,
    0, 0, 1, 2, -12528, 0,
    0, 0, 1, -2, 10980, 79661,
    4, 0, -1, 0, 10675, -34782,
    0, 0, 3, 0, 10034, -23210,
    4, 0, -2, 0, 8548, -21636,
    2, 1, -1, 0, -7888, 24208,
    2, 1, 0, 0, -6766, 30824,
    1, 0, -1, 0, -5163, -8379,
    1, 1, 0, 0, 4987, -16675,
    2, -1, 1, 0, 4036, -12831,
    2, 0, 2, 0, 3994, -10445,
    4, 0, 0, 0, 3861, -11650,
    2, 0, -3, 0, 3665, 14403,
    0, 1, -2, 0, -2689, -7003,
    2, 0, -1, 2, -2602, 0,
    2, -1, -2, 0, 2390, 10056,
    1, 0, 1, 0, -2348, 6322,
    2, -2, 0, 0, 2236, -9884,
    0, 1, 2, 0, -2120, 5751,
    0, 2, 0, 0, -2069, 0,
    2, -2, -1, 0, 2048, -4950,
    2, 0, 1, -2, -1773, 4130,
    2, 0, 0, 2, -1595, 0,
    4, -1, -1, 0, 1215, -3958,
    0, 0, 2, 2, -1110, 0,
    3, 0, -1, 0, -892, 3258,
    2, 1, 1, 0, -810, 2616,
    4, -1, -2, 0, 759, -1897,
    0, 2, -1, 0, -713, -2117,
    2, 2, -1, 0, -700, 2354,
    2, 1, -2, 0, 691, 0,
    2, -1, 0, -2, 596, 0,
    4, 0, 1, 0, 549, -1423,
    0, 0, 4, 0, 537, -1117,
    4, -1, 0, 0, 520, -1571,
    1, 0, -2, 0, -487, -1739,
    2, 1, 0, -2, -399, 0,
    0, 0, 2, -2, -381, -4421,
    1, 1, 1, 0, 351, 0,
    3, 0, -2, 0, -340, 0,
    4, 0, -3, 0, 330, 0,
    2, -1, 2, 0, 327, 0,
    0, 2, 1, 0, -323, 1165,
    1, 1, -1, 0, 299, 0,
    2, 0, 3, 0, 294, 0,
    2, 0, -1, -2, 0, 8752,
)

// D, M, M', F, Σb (ch. 47, table 47.B)
private val B_TERMS = intArrayOf(
    0, 0, 0, 1, 5128122,
    0, 0, 1, 1, 280602,
    0, 0, 1, -1, 277693,
    2, 0, 0, -1, 173237,
    2, 0, -1, 1, 55413,
    2, 0, -1, -1, 46271,
    2, 0, 0, 1, 32573,
    0, 0, 2, 1, 17198,
    2, 0, 1, -1, 9266,
    0, 0, 2, -1, 8822,
    2, -1, 0, -1, 8216,
    2, 0, -2, -1, 4324,
    2, 0, 1, 1, 4200,
    2, 1, 0, -1, -3359,
    2, -1, -1, 1, 2463,
    2, -1, 0, 1, 2211,
    2, -1, -1, -1, 2065,
    0, 1, -1, -1, -1870,
    4, 0, -1, -1, 1828,
    0, 1, 0, 1, -1794,
    0, 0, 0, 3, -1749,
    0, 1, -1, 1, -1565,
    1, 0, 0, 1, -1491,
    0, 1, 1, 1, -1475,
    0, 1, 1, -1, -1410,
    0, 1, 0, -1, -1344,
    1, 0, 0, -1, -1335,
    0, 0, 3, 1, 1107,
    4, 0, 0, -1, 1021,
    4, 0, -1, 1, 833,
    0, 0, 1, -3, 777,
    4, 0, -2, 1, 671,
    2, 0, 0, -3, 607,
    2, 0, 2, -1, 596,
    2, -1, 1, -1, 491,
    2, 0, -2, 1, -451,
    0, 0, 3, -1, 439,
    2, 0, 2, 1, 422,
    2, 0, -3, -1, 421,
    2, 1, -1, 1, -366,
    2, 1, 0, 1, -351,
    4, 0, 0, 1, 331,
    2, -1, 1, 1, 315,
    2, -2, 0, -1, 302,
    0, 0, 1, 3, -283,
    2, 1, 1, -1, -229,
    1, 1, 0, -1, 223,
    1, 1, 0, 1, 223,
    0, 1, -2, -1, -220,
    2, 1, -1, -1, -220,
    1, 0, 1, 1, -185,
    2, -1, -2, -1, 181,
    0, 1, 2, 1, -177,
    4, 0, -2, -1, 176,
    4, -1, -1, -1, 166,
    1, 0, 1, -1, -164,
    4, 0, 1, -1, 132,
    1, 0, -1, -1, -119,
    4, -1, 0, -1, 115,
    2, -2, 0, 1, 107,
)

private fun eccentricityFactor(a: Fundamentals, m: Int) = when (abs(m)) {
    1 -> a.e
    2 -> a.e * a.e
    else -> 1.0
}

/** Geometric geocentric ecliptic position of the Moon, mean equinox of date (ch. 47). */
internal fun moonPosition(a: Fundamentals): Ecliptic {
    var sl = 0.0
    var sr = 0.0
    for (i in LR_TERMS.indices step 6) {
        val arg = LR_TERMS[i] * a.d + LR_TERMS[i + 1] * a.m + LR_TERMS[i + 2] * a.mp + LR_TERMS[i + 3] * a.f
        val ef = eccentricityFactor(a, LR_TERMS[i + 1])
        sl += LR_TERMS[i + 4] * ef * sind(arg)
        sr += LR_TERMS[i + 5] * ef * cosd(arg)
    }
    var sb = 0.0
    for (i in B_TERMS.indices step 5) {
        val arg = B_TERMS[i] * a.d + B_TERMS[i + 1] * a.m + B_TERMS[i + 2] * a.mp + B_TERMS[i + 3] * a.f
        sb += B_TERMS[i + 4] * eccentricityFactor(a, B_TERMS[i + 1]) * sind(arg)
    }
    val a1 = 119.75 + 131.849 * a.t
    val a2 = 53.09 + 479264.290 * a.t
    val a3 = 313.45 + 481266.484 * a.t
    sl += 3958 * sind(a1) + 1962 * sind(a.lp - a.f) + 318 * sind(a2)
    sb += -2235 * sind(a.lp) + 382 * sind(a3) + 175 * sind(a1 - a.f) + 175 * sind(a1 + a.f) +
        127 * sind(a.lp - a.mp) - 115 * sind(a.lp + a.mp)
    return Ecliptic(norm360(a.lp + sl / 1e6), sb / 1e6, 385000.56 + sr / 1000)
}

internal class Illumination(val phaseAngle: Double, val fraction: Double, val brightLimbAngle: Double)

/** Phase angle, illuminated fraction and bright-limb position angle (ch. 48). */
internal fun illumination(moon: Equatorial, moonKm: Double, sun: Equatorial, sunKm: Double): Illumination {
    val dRa = sun.ra - moon.ra
    val cosPsi = sind(sun.dec) * sind(moon.dec) + cosd(sun.dec) * cosd(moon.dec) * cosd(dRa)
    val psi = acos(cosPsi.coerceIn(-1.0, 1.0))
    val i = Math.toDegrees(atan2(sunKm * sin(psi), moonKm - sunKm * cos(psi)))
    val chi = atan2d(
        cosd(sun.dec) * sind(dRa),
        sind(sun.dec) * cosd(moon.dec) - cosd(sun.dec) * sind(moon.dec) * cosd(dRa),
    )
    return Illumination(i, (1 + cosd(i)) / 2, norm360(chi))
}

internal class Libration(val l: Double, val b: Double, val p: Double)

/** Optical plus physical libration and position angle of the axis (ch. 53). */
internal fun libration(a: Fundamentals, nut: Nutation, apparentLambda: Double, beta: Double, ra: Double): Libration {
    val inc = LUNAR_INCLINATION
    val w = apparentLambda - nut.dPsi - a.omega
    val bigA = atan2d(sind(w) * cosd(beta) * cosd(inc) - sind(beta) * sind(inc), cosd(w) * cosd(beta))
    val lOpt = norm180(bigA - a.f)
    val bOpt = asind(-sind(w) * cosd(beta) * sind(inc) - sind(beta) * cosd(inc))

    val t = a.t
    val k1 = 119.75 + 131.849 * t
    val k2 = 72.56 + 20.186 * t
    val mp = a.mp
    val f = a.f
    val d = a.d
    val rho = -0.02752 * cosd(mp) - 0.02245 * sind(f) + 0.00684 * cosd(mp - 2 * f) -
        0.00293 * cosd(2 * f) - 0.00085 * cosd(2 * f - 2 * d) - 0.00054 * cosd(mp - 2 * d) -
        0.00020 * sind(mp + f) - 0.00020 * cosd(mp + 2 * f) - 0.00020 * cosd(mp - f) +
        0.00014 * cosd(mp + 2 * f - 2 * d)
    val sigma = -0.02816 * sind(mp) + 0.02244 * cosd(f) - 0.00682 * sind(mp - 2 * f) -
        0.00279 * sind(2 * f) - 0.00083 * sind(2 * f - 2 * d) + 0.00069 * sind(mp - 2 * d) +
        0.00040 * cosd(mp + f) - 0.00025 * sind(2 * mp) - 0.00023 * sind(mp + 2 * f) +
        0.00020 * cosd(mp - f) + 0.00019 * sind(mp - f) + 0.00013 * sind(mp + 2 * f - 2 * d) -
        0.00010 * cosd(mp - 3 * f)
    val tau = 0.02520 * a.e * sind(a.m) + 0.00473 * sind(2 * mp - 2 * f) - 0.00467 * sind(mp) +
        0.00396 * sind(k1) + 0.00276 * sind(2 * mp - 2 * d) + 0.00196 * sind(a.omega) -
        0.00183 * cosd(mp - f) + 0.00115 * sind(mp - 2 * d) - 0.00096 * sind(mp - d) +
        0.00046 * sind(2 * f - 2 * d) - 0.00039 * sind(mp - f) - 0.00032 * sind(mp - a.m - d) +
        0.00027 * sind(2 * mp - a.m - 2 * d) + 0.00023 * sind(k2) - 0.00014 * sind(2 * d) +
        0.00014 * cosd(2 * mp - 2 * f) - 0.00012 * sind(mp - 2 * f) - 0.00012 * sind(2 * mp) +
        0.00011 * sind(2 * mp - 2 * a.m - 2 * d)

    val l = lOpt - tau + (rho * cosd(bigA) + sigma * sind(bigA)) * tand(bOpt)
    val b = bOpt + sigma * cosd(bigA) - rho * sind(bigA)

    val v = a.omega + nut.dPsi + sigma / sind(inc)
    val x = sind(inc + rho) * sind(v)
    val y = sind(inc + rho) * cosd(v) * cosd(nut.eps) - cosd(inc + rho) * sind(nut.eps)
    val omega = atan2d(x, y)
    val p = asind((sqrt(x * x + y * y) * cosd(ra - omega) / cosd(b)).coerceIn(-1.0, 1.0))
    return Libration(l, b, p)
}

internal fun floorK(jd: Double) = floor((jd - 2451550.09766) / 29.530588861)
