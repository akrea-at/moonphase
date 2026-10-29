package at.akrea.moonphase.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun emptyOrMissingMeansSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalise(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalise(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalise("und"))
    }

    @Test
    fun regionalTagsMapToSupportedLanguage() {
        assertEquals("de", AppLanguage.normalise("de"))
        assertEquals("de", AppLanguage.normalise("de-AT"))
        assertEquals("de", AppLanguage.normalise("DE-ch"))
        assertEquals("en", AppLanguage.normalise("en-GB"))
    }

    @Test
    fun unsupportedLanguageFallsBackToSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.normalise("fr-FR"))
    }
}
