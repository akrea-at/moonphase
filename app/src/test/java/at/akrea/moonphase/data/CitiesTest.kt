package at.akrea.moonphase.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitiesTest {

    private val cities = Cities.parse(
        listOf(
            "Liuzhou\t\tLiu-chou\tGuangxi\tCN\t24.3264\t109.4281\tAsia/Shanghai\t1436599",
            "Vienna\t\tWien,Viena\tVienna\tAT\t48.2085\t16.3721\tEurope/Vienna\t1691468",
            "Wiener Neustadt\t\t\tLower Austria\tAT\t47.8151\t16.2465\tEurope/Vienna\t45024",
            "Munich\t\tMünchen,Monaco di Baviera\tBavaria\tDE\t48.1374\t11.5755\tEurope/Berlin\t1260391",
            "Linz\t\tLinec\tUpper Austria\tAT\t48.3064\t14.2861\tEurope/Vienna\t204846",
            "Sankt Pölten\tSankt Poelten\t\tLower Austria\tAT\t48.2049\t15.6256\tEurope/Vienna\t55044",
        ).joinToString("\n").reader(),
    )

    private fun first(query: String) = cities.search(query).first().name

    @Test
    fun matchesAlternatesAndFoldsAccents() {
        assertEquals(listOf("Vienna", "Wiener Neustadt"), cities.search("wien").map { it.name })
        assertEquals("Munich", first("Munchen"))
        assertEquals("Munich", first("MÜNCHEN"))
    }

    @Test
    fun prefixOnNameBeatsSubstring() {
        assertEquals("Linz", first("Lin"))
        assertEquals(listOf("Liuzhou", "Linz"), cities.search("li").map { it.name })
    }

    @Test
    fun expandsSaintAbbreviation() {
        assertEquals("Sankt Pölten", first("St. Pölten"))
        assertEquals("Sankt Pölten", first("st polten"))
    }

    @Test
    fun ignoresTooShortQueries() {
        assertTrue(cities.search("v").isEmpty())
    }
}
