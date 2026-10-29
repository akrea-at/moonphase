package at.akrea.moonphase.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/** Reference values from Astronomy Engine 2.x; phase times and elongation use apparent longitudes. */
class ReferenceTest {

    private val referenceEvents = """
            FULL 2026-01-03T10:02:49.982Z
            LAST_QUARTER 2026-01-10T15:48:17.681Z
            NEW 2026-01-18T19:51:55.818Z
            FIRST_QUARTER 2026-01-26T04:47:17.874Z
            FULL 2026-02-01T22:09:11.594Z
            LAST_QUARTER 2026-02-09T12:43:00.234Z
            NEW 2026-02-17T12:01:06.383Z
            FIRST_QUARTER 2026-02-24T12:27:31.127Z
            FULL 2026-03-03T11:37:51.678Z
            LAST_QUARTER 2026-03-11T09:38:22.563Z
            NEW 2026-03-19T01:23:27.828Z
            FIRST_QUARTER 2026-03-25T19:17:38.539Z
            FULL 2026-04-02T02:11:54.458Z
            LAST_QUARTER 2026-04-10T04:51:34.228Z
            NEW 2026-04-17T11:51:45.949Z
            FIRST_QUARTER 2026-04-24T02:31:39.351Z
            FULL 2026-05-01T17:23:03.834Z
            LAST_QUARTER 2026-05-09T21:10:20.270Z
            NEW 2026-05-16T20:00:58.189Z
            FIRST_QUARTER 2026-05-23T11:10:50.028Z
            FULL 2026-05-31T08:45:03.953Z
            LAST_QUARTER 2026-06-08T10:00:19.574Z
            NEW 2026-06-15T02:54:05.263Z
            FIRST_QUARTER 2026-06-21T21:55:17.052Z
            FULL 2026-06-29T23:56:33.627Z
            LAST_QUARTER 2026-07-07T19:28:50.404Z
            NEW 2026-07-14T09:43:29.878Z
            FIRST_QUARTER 2026-07-21T11:05:28.762Z
            FULL 2026-07-29T14:35:35.971Z
            LAST_QUARTER 2026-08-06T02:21:21.349Z
            NEW 2026-08-12T17:36:35.297Z
            FIRST_QUARTER 2026-08-20T02:46:14.392Z
            FULL 2026-08-28T04:18:24.864Z
            LAST_QUARTER 2026-09-04T07:51:05.314Z
            NEW 2026-09-11T03:26:50.134Z
            FIRST_QUARTER 2026-09-18T20:43:39.742Z
            FULL 2026-09-26T16:48:53.221Z
            LAST_QUARTER 2026-10-03T13:24:56.397Z
            NEW 2026-10-10T15:49:55.753Z
            FIRST_QUARTER 2026-10-18T16:12:34.248Z
            FULL 2026-10-26T04:11:38.595Z
            LAST_QUARTER 2026-11-01T20:28:20.407Z
            NEW 2026-11-09T07:01:58.822Z
            FIRST_QUARTER 2026-11-17T11:47:42.647Z
            FULL 2026-11-24T14:53:28.690Z
            LAST_QUARTER 2026-12-01T06:08:34.653Z
            NEW 2026-12-09T00:51:45.697Z
            FIRST_QUARTER 2026-12-17T05:42:32.079Z
            FULL 2026-12-24T01:28:10.099Z
            LAST_QUARTER 2026-12-30T18:59:25.783Z
            NEW 2027-01-07T20:24:19.569Z
            FIRST_QUARTER 2027-01-15T20:34:24.323Z
            FULL 2027-01-22T12:17:15.026Z
            LAST_QUARTER 2027-01-29T10:55:23.007Z
            NEW 2027-02-06T15:56:02.038Z
            FIRST_QUARTER 2027-02-14T07:58:23.076Z
            FULL 2027-02-20T23:23:31.043Z
            LAST_QUARTER 2027-02-28T05:16:22.775Z
            NEW 2027-03-08T09:29:24.445Z
            FIRST_QUARTER 2027-03-15T16:25:06.005Z
            FULL 2027-03-22T10:43:41.643Z
            LAST_QUARTER 2027-03-30T00:53:50.828Z
            NEW 2027-04-06T23:51:05.449Z
            FIRST_QUARTER 2027-04-13T22:56:35.225Z
            FULL 2027-04-20T22:27:05.217Z
            LAST_QUARTER 2027-04-28T20:17:48.330Z
            NEW 2027-05-06T10:58:33.355Z
            FIRST_QUARTER 2027-05-13T04:43:50.939Z
            FULL 2027-05-20T10:58:57.872Z
            LAST_QUARTER 2027-05-28T13:57:51.519Z
            NEW 2027-06-04T19:40:15.919Z
            FIRST_QUARTER 2027-06-11T10:56:05.002Z
            FULL 2027-06-19T00:44:17.887Z
            LAST_QUARTER 2027-06-27T04:54:15.089Z
            NEW 2027-07-04T03:01:58.771Z
            FIRST_QUARTER 2027-07-10T18:38:57.925Z
            FULL 2027-07-18T15:44:51.512Z
            LAST_QUARTER 2027-07-26T16:54:48.683Z
            NEW 2027-08-02T10:05:08.214Z
            FIRST_QUARTER 2027-08-09T04:54:07.753Z
            FULL 2027-08-17T07:28:35.435Z
            LAST_QUARTER 2027-08-25T02:27:16.159Z
            NEW 2027-08-31T17:41:06.083Z
            FIRST_QUARTER 2027-09-07T18:31:14.560Z
            FULL 2027-09-15T23:03:26.068Z
            LAST_QUARTER 2027-09-23T10:20:22.683Z
            NEW 2027-09-30T02:35:58.242Z
            FIRST_QUARTER 2027-10-07T11:47:18.590Z
            FULL 2027-10-15T13:46:52.643Z
            LAST_QUARTER 2027-10-22T17:29:04.943Z
            NEW 2027-10-29T13:36:25.859Z
            FIRST_QUARTER 2027-11-06T07:59:54.488Z
            FULL 2027-11-14T03:25:46.421Z
            LAST_QUARTER 2027-11-21T00:48:07.966Z
            NEW 2027-11-28T03:24:17.706Z
            FIRST_QUARTER 2027-12-06T05:22:01.453Z
            FULL 2027-12-13T16:08:41.180Z
            LAST_QUARTER 2027-12-20T09:10:48.852Z
            NEW 2027-12-27T20:12:11.887Z
    """.trimIndent().lines().map { line ->
        val (type, time) = line.split(' ')
        PhaseEvent(PhaseType.valueOf(type), Instant.parse(time))
    }

    @Test
    fun eventsMatchReferenceFor2026And2027() {
        val events = MoonAstro.events(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2028-01-01T00:00:00Z"))
        assertEquals(referenceEvents.map { it.type }, events.map { it.type })
        var worst = 0L
        events.zip(referenceEvents).forEach { (actual, expected) ->
            val diff = abs(Duration.between(expected.instant, actual.instant).seconds)
            worst = maxOf(worst, diff)
            assertTrue("$actual vs $expected", diff <= 60)
        }
        println("Worst phase time error: $worst s")
    }

    @Test
    fun nextAndPreviousAgreeWithEvents() {
        for (i in 1 until referenceEvents.size - 1) {
            val ref = referenceEvents[i]
            val next = MoonAstro.next(ref.type, ref.instant.minusSeconds(3600))
            val previous = MoonAstro.previous(ref.type, ref.instant.plusSeconds(3600))
            assertEquals(next, previous)
            assertTrue(abs(Duration.between(ref.instant, next.instant).seconds) <= 60)
            assertEquals(next.instant, MoonAstro.next(ref.type, next.instant.minusMillis(1)).instant)
            assertTrue(MoonAstro.next(ref.type, next.instant).instant.isAfter(next.instant.plus(Duration.ofDays(29))))
            assertTrue(MoonAstro.previous(ref.type, next.instant).instant.isBefore(next.instant.minus(Duration.ofDays(29))))
        }
    }

    @Test
    fun elongationIsPrincipalAtEventInstants() {
        referenceEvents.forEach { event ->
            val state = MoonAstro.state(event.instant)
            val expected = event.type.ordinal * 90.0
            assertEquals(0.0, norm180(state.elongation - expected), 0.05)
        }
    }

    private class RefState(
        val instant: String, val illumination: Double, val phaseAngle: Double, val elongation: Double,
        val chi: Double, val p: Double, val l: Double, val b: Double, val distanceKm: Double, val dec: Double,
    )

    private val referenceStates = listOf(
        RefState("2026-01-01T00:00:00Z", 0.91399, 34.1089, 146.1480, 267.365, -9.965, -1.2789, -6.5320, 361013.4, 26.4039),
        RefState("2026-03-15T12:00:00Z", 0.14133, 135.8360, 316.0020, 71.352, -14.714, -5.4956, 3.2455, 390157.9, -19.8790),
        RefState("2026-06-21T06:30:00Z", 0.43439, 97.5405, 82.3176, 293.458, 21.771, 7.4737, 2.3319, 384711.1, 1.4505),
        RefState("2026-10-28T18:00:00Z", 0.90814, 35.2866, 215.0754, 74.455, -8.963, -0.3205, -6.3568, 364396.7, 26.8609),
        RefState("2027-02-14T03:00:00Z", 0.47832, 92.4856, 87.3629, 254.461, -15.332, -6.2570, -6.7915, 376653.0, 23.5149),
        RefState("2027-08-01T21:45:00Z", 0.00415, 172.6107, 352.6843, 109.755, 11.550, -0.5601, -1.0923, 357478.8, 20.5313),
        RefState("2026-04-23T09:15:00Z", 0.42062, 99.1346, 80.7136, 279.670, 9.107, 5.1953, -4.4950, 371921.9, 24.7747),
        RefState("2027-11-30T16:40:00Z", 0.06627, 150.1651, 29.6885, 270.405, -2.093, 3.7545, 2.9411, 397596.6, -25.4159),
    )

    @Test
    fun stateMatchesReference() {
        val worst = DoubleArray(9)
        referenceStates.forEach { ref ->
            val s = MoonAstro.state(Instant.parse(ref.instant))
            val errors = doubleArrayOf(
                abs(s.illumination - ref.illumination),
                abs(s.phaseAngle - ref.phaseAngle),
                abs(norm180(s.elongation - ref.elongation)),
                abs(norm180(s.brightLimbAngle - ref.chi)),
                abs(norm180(s.axisPositionAngle - ref.p)),
                abs(s.librationLon - ref.l),
                abs(s.librationLat - ref.b),
                abs(s.distanceKm - ref.distanceKm),
                abs(s.declination - ref.dec),
            )
            val limits = doubleArrayOf(0.002, 0.1, 0.1, 1.0, 0.2, 0.1, 0.1, 30.0, 0.05)
            errors.forEachIndexed { i, e ->
                worst[i] = maxOf(worst[i], e)
                assertTrue("${ref.instant} field $i error $e", e <= limits[i])
            }
            assertEquals(s.elongation < 180, s.waxing)
        }
        println("Worst errors (illum, i, elong, chi, P, l, b, km, dec): ${worst.joinToString { "%.4f".format(it) }}")
    }

    @Test
    fun ageIsMeasuredFromPreviousNewMoon() {
        val newMoon = Instant.parse("2026-10-10T15:50:36Z")
        val event = MoonAstro.next(PhaseType.NEW, newMoon.minusSeconds(3600))
        assertEquals(0.0, MoonAstro.state(event.instant).ageDays, 1e-9)
        assertEquals(5.0, MoonAstro.state(event.instant.plus(Duration.ofDays(5))).ageDays, 1e-9)
        assertTrue(MoonAstro.state(event.instant.minusSeconds(60)).ageDays > 29.0)
    }
}
