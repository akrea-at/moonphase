package at.akrea.moonphase.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** App language: [SYSTEM] or one of [supported]. On API 33+ Android's per-app language is the source of truth. */
object AppLanguage {
    const val SYSTEM = ""
    val supported = listOf("en", "de")

    private const val FILE = "language"
    private const val KEY = "tag"

    fun normalise(tag: String?): String {
        val language = tag?.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it).language }
        return language?.takeIf { it in supported } ?: SYSTEM
    }

    fun get(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            normalise(if (locales.isEmpty) null else locales[0].toLanguageTag())
        } else {
            normalise(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, SYSTEM))
        }

    /** On API < 33 the caller must recreate the activity afterwards. */
    fun set(context: Context, tag: String) {
        val language = normalise(tag)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        } else {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, language).commit()
        }
    }

    fun localised(context: Context): Context {
        val locales = when (val language = get(context)) {
            SYSTEM -> Resources.getSystem().configuration.locales
            else -> LocaleList(Locale.forLanguageTag(language))
        }
        val config = Configuration(context.resources.configuration).apply { setLocales(locales) }
        return context.createConfigurationContext(config)
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        return localised(base).also { Locale.setDefault(it.resources.configuration.locales[0]) }
    }
}
