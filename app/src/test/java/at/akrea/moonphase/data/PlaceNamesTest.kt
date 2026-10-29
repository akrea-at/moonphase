package at.akrea.moonphase.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PlaceNamesTest {

    private val cities = Cities.parse(
        listOf(
            "Vienna\t\tWien,Viena\tVienna\tAT\t48.2085\t16.3721\tEurope/Vienna\t1691468\t\tWien\t\tWien",
            "Munich\t\tMonaco di Baviera\tBavaria\tDE\t48.1374\t11.5755\tEurope/Berlin\t1260391\t\tMünchen\t\tBayern",
            "Köln\tKoeln\t\tNorth Rhine-Westphalia\tDE\t50.9333\t6.9500\tEurope/Berlin\t963395\tCologne\t\t\tNordrhein-Westfalen",
            "Sankt Pölten\tSankt Poelten\t\tLower Austria\tAT\t48.2049\t15.6256\tEurope/Vienna\t55044\t\tSt. Pölten",
            "Linz\t\tLinec\tUpper Austria\tAT\t48.3064\t14.2861\tEurope/Vienna\t204846",
        ).joinToString("\n").reader(),
    )

    private fun place(query: String) = cities.search(query).first()

    @Test
    fun labelsFollowLanguage() {
        assertEquals("Vienna, Austria", place("Vienna").label(Locale.UK))
        assertEquals("Wien, Österreich", place("Vienna").label(Locale.GERMAN))
        assertEquals("Munich, Bavaria, Germany", place("Munich").label(Locale.UK))
        assertEquals("München, Bayern, Deutschland", place("Munich").label(Locale("de", "AT")))
        assertEquals("Cologne, North Rhine-Westphalia, Germany", place("Köln").label(Locale.UK))
        assertEquals("Köln, Nordrhein-Westfalen, Deutschland", place("Köln").label(Locale.GERMAN))
    }

    @Test
    fun missingColumnsFallBackToGeoNames() {
        assertEquals("St. Pölten, Lower Austria, Österreich", place("Sankt Pölten").label(Locale.GERMAN))
        assertEquals("Linz, Upper Austria, Austria", place("Linz").label(Locale.UK))
        assertEquals("Upper Austria, Österreich", place("Linz").details(Locale.GERMAN))
    }

    @Test
    fun searchMatchesLocalisedNames() {
        assertEquals("Munich", place("München").name)
        assertEquals("Köln", place("Cologne").name)
        assertEquals("Sankt Pölten", place("St. Pölten").name)
    }
}
