package at.akrea.moonphase.render

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class MoonFrameTest {

    private val eps = 1e-9

    @Test
    fun plainViewIsIdentity() {
        val frame = MoonFrame(0.0, 0.0, 0.0)
        val centre = frame.selenographic(0.0, 0.0)
        assertEquals(0.0, centre[0], eps)
        assertEquals(0.0, centre[1], eps)
        val right = frame.selenographic(1.0, 0.0)
        assertEquals(90.0, right[0], eps)
        assertEquals(0.0, right[1], eps)
        assertEquals(90.0, frame.selenographic(0.0, 1.0)[1], eps)
    }

    @Test
    fun librationMovesSubEarthPoint() {
        val centre = MoonFrame(5.0, -3.0, 0.0).selenographic(0.0, 0.0)
        assertEquals(5.0, centre[0], eps)
        assertEquals(-3.0, centre[1], eps)
    }

    @Test
    fun positiveAxisAngleTurnsNorthPoleCounterClockwise() {
        val p = Math.toRadians(20.0)
        val r = 0.9
        val point = MoonFrame(0.0, 0.0, 20.0).selenographic(-sin(p) * r, cos(p) * r)
        assertEquals(0.0, point[0], 1e-7)
        assertEquals(Math.toDegrees(asin(r)), point[1], 1e-7)
    }

    @Test
    fun sunDirection() {
        val frame = MoonFrame(0.0, 0.0, 0.0)
        val full = frame.sun(0.0, 270.0)
        assertEquals(1.0, full[2], eps)
        val firstQuarter = frame.sun(90.0, 270.0)
        assertEquals(1.0, firstQuarter[0], eps)
        assertEquals(0.0, firstQuarter[2], eps)
    }
}
