package by.bnd.iverbs.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionBiasTest {
    @Test
    fun keepsEachFormOnceInSetOrder() {
        val verbs = listOf(
            Verb("идти", Forms("go", "went", "gone")),
            Verb("резать", Forms("cut", "cut", "cut")),
            Verb("быть", Forms("be", "was", "been")),
        )
        assertEquals(
            listOf("go", "went", "gone", "cut", "be", "was", "been"),
            recognitionBias(verbs),
        )
    }

    @Test
    fun beginnerSetBiasesEveryFormAndNotOnlyTheCurrentVerb() {
        val phrases = recognitionBias(beginnerVerbs)
        assertEquals(phrases.distinct(), phrases)
        assertEquals("be", phrases.first())
        assertTrue("ate" in phrases)
        assertTrue("felt" in phrases)
        assertFalse("fell" in phrases)
        assertFalse("go, went, gone" in phrases)
    }
}
