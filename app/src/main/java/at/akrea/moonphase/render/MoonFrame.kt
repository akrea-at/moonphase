package at.akrea.moonphase.render

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Screen axes expressed in the lunar body frame (see tools/render_moon.py).
 * Screen: x right, y up, z towards the viewer, celestial north up.
 * Body: X towards (lon 90° E, lat 0), Y towards the north pole, Z towards (0, 0).
 */
internal class MoonFrame(librationLon: Double, librationLat: Double, axisAngle: Double) {
    val xx: Double
    val xy: Double
    val xz: Double
    val yx: Double
    val yy: Double
    val yz: Double
    val zx: Double
    val zy: Double
    val zz: Double

    init {
        val l = Math.toRadians(librationLon)
        val b = Math.toRadians(librationLat)
        zx = cos(b) * sin(l)
        zy = sin(b)
        zz = cos(b) * cos(l)

        var y0x = -zy * zx
        var y0y = 1.0 - zy * zy
        var y0z = -zy * zz
        val len = sqrt(y0x * y0x + y0y * y0y + y0z * y0z)
        y0x /= len
        y0y /= len
        y0z /= len

        val x0x = y0y * zz - y0z * zy
        val x0y = y0z * zx - y0x * zz
        val x0z = y0x * zy - y0y * zx

        val p = Math.toRadians(axisAngle)
        val cp = cos(p)
        val sp = sin(p)
        xx = cp * x0x - sp * y0x
        xy = cp * x0y - sp * y0y
        xz = cp * x0z - sp * y0z
        yx = sp * x0x + cp * y0x
        yy = sp * x0y + cp * y0y
        yz = sp * x0z + cp * y0z
    }

    /** Sun direction in the body frame from phase angle i and bright-limb position angle χ. */
    fun sun(phaseAngle: Double, brightLimbAngle: Double): DoubleArray {
        val i = Math.toRadians(phaseAngle)
        val chi = Math.toRadians(brightLimbAngle)
        val sx = -sin(i) * sin(chi)
        val sy = sin(i) * cos(chi)
        val sz = cos(i)
        return doubleArrayOf(
            sx * xx + sy * yx + sz * zx,
            sx * xy + sy * yy + sz * zy,
            sx * xz + sy * yz + sz * zz,
        )
    }

    /** Selenographic longitude and latitude in degrees of the visible point at screen (x, y) on the unit disc. */
    fun selenographic(x: Double, y: Double): DoubleArray {
        val z = sqrt(maxOf(0.0, 1.0 - x * x - y * y))
        val nx = x * xx + y * yx + z * zx
        val ny = x * xy + y * yy + z * zy
        val nz = x * xz + y * yz + z * zz
        return doubleArrayOf(Math.toDegrees(atan2(nx, nz)), Math.toDegrees(asin(ny.coerceIn(-1.0, 1.0))))
    }
}
