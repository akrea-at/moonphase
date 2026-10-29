package at.akrea.moonphase.data

import android.content.Context
import java.time.ZoneId

enum class LocationMode { NONE, GPS, PLACE }

data class AppSettings(
    val mode: LocationMode = LocationMode.NONE,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsTime: Long = 0,
    val place: Place? = null,
    val showWidgetLabel: Boolean = false,
) {
    val zone: ZoneId
        get() = place?.takeIf { mode == LocationMode.PLACE }
            ?.let { runCatching { ZoneId.of(it.zoneId) }.getOrNull() }
            ?: ZoneId.systemDefault()

    val latitude: Double?
        get() = when (mode) {
            LocationMode.GPS -> gpsLatitude
            LocationMode.PLACE -> place?.latitude
            LocationMode.NONE -> null
        }

    val flip: Boolean get() = (latitude ?: 0.0) < 0
}

object Settings {
    private const val FILE = "settings"

    fun load(context: Context): AppSettings {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val place = p.getString("place_name", null)?.let {
            Place(
                name = it,
                region = p.getString("place_region", "")!!,
                country = p.getString("place_country", "")!!,
                latitude = p.getDouble("place_lat"),
                longitude = p.getDouble("place_lon"),
                zoneId = p.getString("place_zone", "UTC")!!,
            )
        }
        return AppSettings(
            mode = runCatching { LocationMode.valueOf(p.getString("mode", null)!!) }.getOrDefault(LocationMode.NONE),
            gpsLatitude = p.getDouble("gps_lat").takeIf { p.contains("gps_lat") },
            gpsLongitude = p.getDouble("gps_lon").takeIf { p.contains("gps_lon") },
            gpsTime = p.getLong("gps_time", 0),
            place = place,
            showWidgetLabel = p.getBoolean("widget_label", false),
        )
    }

    fun save(context: Context, s: AppSettings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            clear()
            putString("mode", s.mode.name)
            s.gpsLatitude?.let { putDouble("gps_lat", it) }
            s.gpsLongitude?.let { putDouble("gps_lon", it) }
            putLong("gps_time", s.gpsTime)
            s.place?.let {
                putString("place_name", it.name)
                putString("place_region", it.region)
                putString("place_country", it.country)
                putDouble("place_lat", it.latitude)
                putDouble("place_lon", it.longitude)
                putString("place_zone", it.zoneId)
            }
            putBoolean("widget_label", s.showWidgetLabel)
        }.commit()
    }

    private fun android.content.SharedPreferences.getDouble(key: String) =
        Double.fromBits(getLong(key, 0))

    private fun android.content.SharedPreferences.Editor.putDouble(key: String, value: Double) =
        putLong(key, value.toRawBits())
}
