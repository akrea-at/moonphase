package at.akrea.moonphase.render

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import at.akrea.moonphase.astro.MoonState
import java.lang.ref.SoftReference
import java.util.stream.IntStream
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Kotlin port of tools/render_moon.py. */
object MoonRenderer {

    private const val GAMMA = 2.2
    private const val RELIEF = 1.6
    private const val LIMB_FADE = 0.6
    private const val EARTHSHINE = 0.04
    private const val TINT_R = 0.82
    private const val TINT_G = 0.92
    private const val TINT_B = 1.10
    private const val KNEE = 0.8
    private const val SLOPE_K = 400.0

    private const val LIN_STEPS = 16
    private const val OUT_SIZE = 4096
    private const val OUT_MAX = 2.5
    private const val BAND = 16

    private val linear = DoubleArray(255 * LIN_STEPS + 2) { (it.toDouble() / LIN_STEPS / 255.0).coerceAtMost(1.0).pow(GAMMA) }

    // Indexed by sqrt(L / OUT_MAX) so the dark earthshine range keeps fine resolution.
    private val output = IntArray(OUT_SIZE + 1) {
        val l = OUT_MAX * (it.toDouble() / OUT_SIZE).pow(2)
        val t = if (l <= KNEE) l else KNEE + (1 - KNEE) * (1 - exp(-(l - KNEE) / (1 - KNEE)))
        (255 * t.pow(1 / GAMMA)).roundToInt().coerceIn(0, 255)
    }

    private class Maps(val width: Int, val height: Int, val albedo: ByteArray, val slopeE: ByteArray, val slopeN: ByteArray)

    private val cache = HashMap<Int, SoftReference<Maps>>()
    private var mapWidth = 0

    fun render(context: Context, state: MoonState, sizePx: Int, flip: Boolean): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val started = SystemClock.elapsedRealtime()
        val maps = maps(context, size)
        val decoded = SystemClock.elapsedRealtime()
        val pixels = IntArray(size * size)
        val frame = MoonFrame(state.librationLon, state.librationLat, state.axisPositionAngle)
        val sun = frame.sun(state.phaseAngle, state.brightLimbAngle)
        IntStream.range(0, (size + BAND - 1) / BAND).parallel().forEach { band ->
            shade(maps, frame, sun, size, flip, pixels, band * BAND, minOf(size, (band + 1) * BAND))
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        if (size >= 400 && context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d("MoonRenderer", "rendered ${size}px in ${SystemClock.elapsedRealtime() - started} ms (maps ${decoded - started} ms)")
        }
        return bitmap
    }

    private fun shade(maps: Maps, frame: MoonFrame, s: DoubleArray, size: Int, flip: Boolean, pixels: IntArray, fromRow: Int, toRow: Int) {
        val sx = s[0]
        val sy = s[1]
        val sz = s[2]
        val w = maps.width
        val h = maps.height
        val albedo = maps.albedo
        val slopeE = maps.slopeE
        val slopeN = maps.slopeN

        val radius = size / 2.0 - 1.0
        val c = size / 2.0
        val rMax = 1.0 - 0.5 / radius

        for (py in fromRow until toRow) {
            val y0 = (c - py - 0.5) / radius
            for (px in 0 until size) {
                var x = (px + 0.5 - c) / radius
                var y = y0
                val r = sqrt(x * x + y * y)
                val alpha = ((1.0 - r) * radius + 0.5).coerceIn(0.0, 1.0)
                if (alpha <= 0.0) continue
                if (r > rMax) {
                    x *= rMax / r
                    y *= rMax / r
                }
                val z = sqrt(maxOf(0.0, 1.0 - x * x - y * y))

                val nx = x * frame.xx + y * frame.yx + z * frame.zx
                val ny = x * frame.xy + y * frame.yy + z * frame.zy
                val nz = x * frame.xz + y * frame.yz + z * frame.zz
                val sinLat = ny.coerceIn(-1.0, 1.0)
                val cosLat = sqrt(maxOf(0.0, 1.0 - sinLat * sinLat))
                val lonDeg = Math.toDegrees(atan2(nx, nz))
                val latDeg = Math.toDegrees(asin(sinLat))
                val sinLon: Double
                val cosLon: Double
                if (cosLat > 1e-9) {
                    sinLon = nx / cosLat
                    cosLon = nz / cosLat
                } else {
                    sinLon = 0.0
                    cosLon = 1.0
                }

                val u = (lonDeg + 180.0) / 360.0 * w - 0.5
                val v = (90.0 - latDeg) / 180.0 * h - 0.5
                val ux = Math.floor(u)
                val vy = Math.floor(v)
                val fx = u - ux
                val fy = v - vy
                var xa = ux.toInt() % w
                if (xa < 0) xa += w
                val xb = if (xa + 1 == w) 0 else xa + 1
                val ya = vy.toInt().coerceIn(0, h - 1) * w
                val yb = (vy.toInt() + 1).coerceIn(0, h - 1) * w
                val w00 = (1 - fx) * (1 - fy)
                val w10 = fx * (1 - fy)
                val w01 = (1 - fx) * fy
                val w11 = fx * fy

                val a = sample(albedo, ya + xa, ya + xb, yb + xa, yb + xb, w00, w10, w01, w11)
                val lin = linear[(a * LIN_STEPS).toInt()]
                val se = (sample(slopeE, ya + xa, ya + xb, yb + xa, yb + xb, w00, w10, w01, w11) - 128.0) / SLOPE_K
                val sn = (sample(slopeN, ya + xa, ya + xb, yb + xa, yb + xb, w00, w10, w01, w11) - 128.0) / SLOPE_K

                val k = RELIEF * minOf(1.0, z / LIMB_FADE)
                val ke = k * se
                val kn = k * sn
                var mx = nx - ke * cosLon + kn * sinLat * sinLon
                var my = ny - kn * cosLat
                var mz = nz + ke * sinLon + kn * sinLat * cosLon
                val norm = sqrt(mx * mx + my * my + mz * mz)
                mx /= norm
                my /= norm
                mz /= norm

                val mu0 = mx * sx + my * sy + mz * sz
                val mu = maxOf(mx * frame.zx + my * frame.zy + mz * frame.zz, 0.0)
                val f = if (mu0 > 0) 2 * mu0 / (mu0 + mu + 1e-6) else 0.0

                val red = tone(lin * (f + EARTHSHINE * TINT_R))
                val green = tone(lin * (f + EARTHSHINE * TINT_G))
                val blue = tone(lin * (f + EARTHSHINE * TINT_B))
                val alphaByte = (alpha * 255).roundToInt()
                val index = if (flip) (size - 1 - py) * size + (size - 1 - px) else py * size + px
                pixels[index] = (alphaByte shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
    }

    private fun sample(map: ByteArray, i00: Int, i10: Int, i01: Int, i11: Int, w00: Double, w10: Double, w01: Double, w11: Double) =
        (map[i00].toInt() and 0xFF) * w00 + (map[i10].toInt() and 0xFF) * w10 +
            (map[i01].toInt() and 0xFF) * w01 + (map[i11].toInt() and 0xFF) * w11

    private fun tone(l: Double): Int {
        val index = (sqrt(l / OUT_MAX) * OUT_SIZE).toInt()
        return output[if (index > OUT_SIZE) OUT_SIZE else index]
    }

    private fun maps(context: Context, size: Int): Maps = synchronized(cache) {
        if (mapWidth == 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("moon_albedo.jpg").use { BitmapFactory.decodeStream(it, null, bounds) }
            mapWidth = bounds.outWidth
        }
        var factor = 1
        while (mapWidth / (factor * 2) >= 2 * size) factor *= 2
        cache[factor]?.get()?.let { return it }
        val albedo = decode(context, "moon_albedo.jpg", factor)
        val maps = Maps(
            albedo.width, albedo.height, albedo.grey,
            decode(context, "moon_slope_e.jpg", factor).grey,
            decode(context, "moon_slope_n.jpg", factor).grey,
        )
        cache[factor] = SoftReference(maps)
        maps
    }

    private class Grey(val width: Int, val height: Int, val grey: ByteArray)

    private fun decode(context: Context, name: String, factor: Int): Grey {
        val options = BitmapFactory.Options().apply {
            inSampleSize = factor
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.assets.open(name).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Cannot decode $name")
        val w = bitmap.width
        val h = bitmap.height
        val grey = ByteArray(w * h)
        val row = IntArray(w)
        for (y in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            val offset = y * w
            for (x in 0 until w) grey[offset + x] = row[x].toByte()
        }
        bitmap.recycle()
        return Grey(w, h, grey)
    }
}
