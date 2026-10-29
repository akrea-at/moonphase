package at.akrea.moonphase.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import at.akrea.moonphase.MainActivity
import at.akrea.moonphase.R
import at.akrea.moonphase.astro.MoonAstro
import at.akrea.moonphase.data.AppLanguage
import at.akrea.moonphase.data.Settings
import at.akrea.moonphase.render.MoonRenderer
import at.akrea.moonphase.ui.phaseNameRes
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MoonWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, MoonWidgetProvider::class.java))
                if (ids.isNotEmpty()) update(context, manager, ids)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = update(context, manager, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        update(context, manager, intArrayOf(id))

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(refreshIntent(context))
    }

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        thread(name = "moon-widget") {
            try {
                val settings = Settings.load(context)
                val now = Instant.now()
                val state = MoonAstro.state(now)
                val localised = AppLanguage.localised(context)
                val label = localised.getString(
                    R.string.widget_label,
                    localised.getString(phaseNameRes(MoonAstro.phaseNameAt(now, settings.zone))),
                    (state.illumination * 100).roundToInt(),
                )
                val rendered = HashMap<Int, Bitmap>()
                for (id in ids) {
                    val size = sizePx(context, manager.getAppWidgetOptions(id), settings.showWidgetLabel)
                    val bitmap = rendered.getOrPut(size) { MoonRenderer.render(context, state, size, settings.flip) }
                    manager.updateAppWidget(id, views(context, bitmap, label, settings.showWidgetLabel))
                }
                scheduleNext(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun views(context: Context, bitmap: Bitmap, label: String, showLabel: Boolean) =
        RemoteViews(context.packageName, R.layout.widget_moon).apply {
            setImageViewBitmap(R.id.widget_moon, bitmap)
            setContentDescription(R.id.widget_moon, label)
            setTextViewText(R.id.widget_label, label)
            setViewVisibility(R.id.widget_label, if (showLabel) View.VISIBLE else View.GONE)
            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }

    private fun sizePx(context: Context, options: Bundle, showLabel: Boolean): Int {
        val label = if (showLabel) LABEL_DP else 0
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val dp = maxOf(minOf(minW, maxH - label), minOf(maxW, minH - label))
            .takeIf { it > 0 } ?: DEFAULT_DP
        return (dp * context.resources.displayMetrics.density).roundToInt().coerceIn(64, MAX_PX)
    }

    private fun scheduleNext(context: Context) {
        context.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.RTC, System.currentTimeMillis() + REFRESH_MS, refreshIntent(context))
    }

    companion object {
        private const val ACTION_REFRESH = "at.akrea.moonphase.action.REFRESH_WIDGET"
        private const val REFRESH_MS = 3 * 60 * 60 * 1000L
        private const val LABEL_DP = 22
        private const val DEFAULT_DP = 120
        private const val MAX_PX = 1024

        fun refresh(context: Context) {
            context.sendBroadcast(Intent(context, MoonWidgetProvider::class.java).setAction(ACTION_REFRESH))
        }

        private fun refreshIntent(context: Context) = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, MoonWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
