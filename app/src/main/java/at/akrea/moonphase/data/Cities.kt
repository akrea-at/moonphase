package at.akrea.moonphase.data

import android.content.Context
import java.io.Reader
import java.lang.ref.SoftReference
import java.text.Normalizer
import java.util.Locale

data class Place(
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val nameEn: String = "",
    val nameDe: String = "",
    val regionEn: String = "",
    val regionDe: String = "",
) {
    fun displayName(locale: Locale) = localised(locale, name, nameEn, nameDe)

    fun details(locale: Locale): String {
        val city = displayName(locale)
        val countryName = Locale("", country).getDisplayCountry(locale).ifEmpty { country }
        val regionName = localised(locale, region, regionEn, regionDe).takeIf { it.isNotEmpty() && it != city }
        return listOfNotNull(regionName, countryName).joinToString(", ")
    }

    fun label(locale: Locale) = displayName(locale) + ", " + details(locale)

    private fun localised(locale: Locale, base: String, en: String, de: String) =
        (if (locale.language == "de") de else en).ifEmpty { base }
}

/** Offline city search over assets/cities.tsv (format: see tools/prepare_cities.py). */
class Cities private constructor(
    private val places: List<Place>,
    private val names: Array<String>,
    private val asciiNames: Array<String>,
    private val alternates: Array<Array<String>>,
) {

    fun search(query: String, limit: Int = 30): List<Place> {
        val folded = fold(query).trim()
        if (folded.length < 2) return emptyList()
        val variants = variants(folded)
        val buckets = Array(5) { ArrayList<Place>() }
        for (i in places.indices) {
            var best = buckets.size
            for (q in variants) best = minOf(best, rank(i, q))
            if (best < buckets.size && buckets[best].size < limit) buckets[best] += places[i]
        }
        return buckets.flatMap { it }.take(limit)
    }

    private fun rank(i: Int, q: String): Int {
        val name = names[i]
        val ascii = asciiNames[i]
        if (name == q || ascii == q) return 0
        val alts = alternates[i]
        if (alts.any { it == q }) return 1
        if (name.startsWith(q) || ascii.startsWith(q)) return 2
        if (alts.any { it.startsWith(q) }) return 3
        if (name.contains(q) || ascii.contains(q)) return 4
        return Int.MAX_VALUE
    }

    companion object {
        private var cached: SoftReference<Cities>? = null

        fun load(context: Context): Cities = synchronized(this) {
            cached?.get()?.let { return it }
            context.assets.open("cities.tsv").bufferedReader().use { parse(it) }.also { cached = SoftReference(it) }
        }

        fun parse(reader: Reader): Cities {
            val places = ArrayList<Place>(35_000)
            val names = ArrayList<String>(35_000)
            val asciiNames = ArrayList<String>(35_000)
            val alternates = ArrayList<Array<String>>(35_000)
            reader.forEachLine { line ->
                val f = line.split('\t')
                if (f.size < 9) return@forEachLine
                val extra = List(4) { f.getOrElse(9 + it) { "" } }
                places += Place(
                    f[0], f[3], f[4], f[5].toDouble(), f[6].toDouble(), f[7],
                    nameEn = extra[0], nameDe = extra[1], regionEn = extra[2], regionDe = extra[3],
                )
                val name = fold(f[0])
                names += name
                asciiNames += if (f[1].isEmpty()) name else fold(f[1])
                val alts = (if (f[2].isEmpty()) emptyList() else f[2].split(',')) + extra.take(2).filter { it.isNotEmpty() }
                alternates += alts.map(::fold).distinct().toTypedArray()
            }
            return Cities(places, names.toTypedArray(), asciiNames.toTypedArray(), alternates.toTypedArray())
        }

        internal fun variants(q: String): List<String> {
            val rest = when {
                q.startsWith("st. ") -> q.substring(4)
                q.startsWith("st.") -> q.substring(3)
                q.startsWith("st ") -> q.substring(3)
                else -> return listOf(q)
            }.trimStart()
            return listOf(q, "sankt $rest", "saint $rest", "st $rest")
        }

        internal fun fold(s: String): String {
            val lower = s.lowercase(Locale.ROOT)
            if (lower.all { it.code < 128 && it != '-' }) return lower
            val out = StringBuilder(lower.length)
            for (ch in Normalizer.normalize(lower, Normalizer.Form.NFD)) {
                when {
                    Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
                    ch == 'ß' -> out.append("ss")
                    ch == 'æ' -> out.append("ae")
                    ch == 'œ' -> out.append("oe")
                    ch == 'ø' -> out.append('o')
                    ch == 'ł' -> out.append('l')
                    ch == 'đ' -> out.append('d')
                    ch == 'ı' -> out.append('i')
                    ch == '-' -> out.append(' ')
                    else -> out.append(ch)
                }
            }
            return out.toString()
        }
    }
}
