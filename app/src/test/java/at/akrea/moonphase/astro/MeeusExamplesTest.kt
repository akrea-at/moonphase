package at.akrea.moonphase.astro

import org.junit.Assert.assertEquals
import org.junit.Test

class MeeusExamplesTest {

    private val t1992 = (2448724.5 - J2000) / 36525

    @Test
    fun moonPositionExample47a() {
        val moon = moonPosition(Fundamentals(t1992))
        assertEquals(133.162655, moon.longitude, 1e-5)
        assertEquals(-3.229126, moon.latitude, 1e-5)
        assertEquals(368409.7, moon.distanceKm, 0.1)

        val nut = nutation(t1992)
        val eq = toEquatorial(moon.longitude + nut.dPsi, moon.latitude, nut.eps)
        assertEquals(134.688470, eq.ra, 1e-3)
        assertEquals(13.768368, eq.dec, 1e-3)
    }

    @Test
    fun illuminationExample48a() {
        val nut = nutation(t1992)
        val moon = moonPosition(Fundamentals(t1992))
        val moonEq = toEquatorial(moon.longitude + nut.dPsi, moon.latitude, nut.eps)
        val sun = sunPosition(t1992, nut)
        val sunEq = toEquatorial(sun.longitude, 0.0, nut.eps)
        val illum = illumination(moonEq, moon.distanceKm, sunEq, sun.distanceKm)
        assertEquals(69.0756, illum.phaseAngle, 0.01)
        assertEquals(0.6786, illum.fraction, 2e-4)
        assertEquals(285.0, illum.brightLimbAngle, 0.1)
    }

    @Test
    fun newMoonExample49a() {
        assertEquals(2443192.65118, phaseJde(-283.0, PhaseType.NEW), 1e-5)
    }

    @Test
    fun librationExample53a() {
        val a = Fundamentals(t1992)
        val nut = nutation(t1992)
        val moon = moonPosition(a)
        val lambda = moon.longitude + nut.dPsi
        val ra = toEquatorial(lambda, moon.latitude, nut.eps).ra
        val lib = libration(a, nut, lambda, moon.latitude, ra)
        assertEquals(-1.23, lib.l, 0.01)
        assertEquals(4.20, lib.b, 0.01)
        assertEquals(15.08, lib.p, 0.01)
    }
}
