package at.akrea.moonphase.astro

// A1..A14 (ch. 49): constant, coefficient of k, amplitude
private val PLANETARY = doubleArrayOf(
    299.77, 0.107408, 0.000325,
    251.88, 0.016321, 0.000165,
    251.83, 26.651886, 0.000164,
    349.42, 36.412478, 0.000126,
    84.66, 18.206239, 0.000110,
    141.74, 53.303771, 0.000062,
    207.14, 2.453732, 0.000060,
    154.84, 7.306860, 0.000056,
    34.52, 27.261239, 0.000047,
    207.19, 0.121824, 0.000042,
    291.34, 1.844379, 0.000040,
    161.72, 24.198154, 0.000037,
    239.56, 25.513099, 0.000035,
    331.55, 3.592518, 0.000023,
)

/**
 * JDE (TT) of a principal phase (Meeus ch. 49). [k] must be an integer for new moon
 * plus 0.25, 0.5 or 0.75 for the other phases; k = 0 is the new moon of 2000-01-06.
 */
internal fun phaseJde(k: Double, type: PhaseType): Double {
    val t = k / 1236.85
    val t2 = t * t
    val t3 = t2 * t
    val t4 = t3 * t
    var jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4
    val e = 1 - 0.002516 * t - 0.0000074 * t2
    val m = 2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3
    val mp = 201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4
    val f = 160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4
    val om = 124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3

    jde += when (type) {
        PhaseType.NEW -> syzygy(
            e, m, mp, f, om,
            -0.40720, 0.17241, 0.01608, 0.01039, 0.00739, -0.00514, 0.00208,
        )
        PhaseType.FULL -> syzygy(
            e, m, mp, f, om,
            -0.40614, 0.17302, 0.01614, 0.01043, 0.00734, -0.00515, 0.00209,
        )
        PhaseType.FIRST_QUARTER -> quadrature(e, m, mp, f, om) + quarterW(e, m, mp, f)
        PhaseType.LAST_QUARTER -> quadrature(e, m, mp, f, om) - quarterW(e, m, mp, f)
    }

    for (i in PLANETARY.indices step 3) {
        val arg = PLANETARY[i] + PLANETARY[i + 1] * k - if (i == 0) 0.009173 * t2 else 0.0
        jde += PLANETARY[i + 2] * sind(arg)
    }
    return jde
}

private fun syzygy(
    e: Double, m: Double, mp: Double, f: Double, om: Double,
    c1: Double, c2: Double, c3: Double, c4: Double, c5: Double, c6: Double, c7: Double,
): Double =
    c1 * sind(mp) +
        c2 * e * sind(m) +
        c3 * sind(2 * mp) +
        c4 * sind(2 * f) +
        c5 * e * sind(mp - m) +
        c6 * e * sind(mp + m) +
        c7 * e * e * sind(2 * m) -
        0.00111 * sind(mp - 2 * f) -
        0.00057 * sind(mp + 2 * f) +
        0.00056 * e * sind(2 * mp + m) -
        0.00042 * sind(3 * mp) +
        0.00042 * e * sind(m + 2 * f) +
        0.00038 * e * sind(m - 2 * f) -
        0.00024 * e * sind(2 * mp - m) -
        0.00017 * sind(om) -
        0.00007 * sind(mp + 2 * m) +
        0.00004 * sind(2 * mp - 2 * f) +
        0.00004 * sind(3 * m) +
        0.00003 * sind(mp + m - 2 * f) +
        0.00003 * sind(2 * mp + 2 * f) -
        0.00003 * sind(mp + m + 2 * f) +
        0.00003 * sind(mp - m + 2 * f) -
        0.00002 * sind(mp - m - 2 * f) -
        0.00002 * sind(3 * mp + m) +
        0.00002 * sind(4 * mp)

private fun quadrature(e: Double, m: Double, mp: Double, f: Double, om: Double): Double =
    -0.62801 * sind(mp) +
        0.17172 * e * sind(m) -
        0.01183 * e * sind(mp + m) +
        0.00862 * sind(2 * mp) +
        0.00804 * sind(2 * f) +
        0.00454 * e * sind(mp - m) +
        0.00204 * e * e * sind(2 * m) -
        0.00180 * sind(mp - 2 * f) -
        0.00070 * sind(mp + 2 * f) -
        0.00040 * sind(3 * mp) -
        0.00034 * e * sind(2 * mp - m) +
        0.00032 * e * sind(m + 2 * f) +
        0.00032 * e * sind(m - 2 * f) -
        0.00028 * e * e * sind(mp + 2 * m) +
        0.00027 * e * sind(2 * mp + m) -
        0.00017 * sind(om) -
        0.00005 * sind(mp - m - 2 * f) +
        0.00004 * sind(2 * mp + 2 * f) -
        0.00004 * sind(mp + m + 2 * f) +
        0.00004 * sind(mp - 2 * m) +
        0.00003 * sind(mp + m - 2 * f) +
        0.00003 * sind(3 * m) +
        0.00002 * sind(2 * mp - 2 * f) +
        0.00002 * sind(mp - m + 2 * f) -
        0.00002 * sind(3 * mp + m)

private fun quarterW(e: Double, m: Double, mp: Double, f: Double): Double =
    0.00306 - 0.00038 * e * cosd(m) + 0.00026 * cosd(mp) - 0.00002 * cosd(mp - m) +
        0.00002 * cosd(mp + m) + 0.00002 * cosd(2 * f)
