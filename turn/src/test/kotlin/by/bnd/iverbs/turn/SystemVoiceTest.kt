package by.bnd.iverbs.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemVoiceTest {
    @Test
    fun prefersOfflineVoiceOverLouderNetworkVoice() {
        val network = voice(name = "net", needsNetwork = true, quality = 500)
        val offline = voice(name = "local", needsNetwork = false, quality = 300)
        assertEquals("local", chooseVoice(listOf(network, offline), "ru", "RU", false)?.name)
    }

    @Test
    fun prefersHigherQualityAndUsesCountryAsATilt() {
        val usHigh = voice(name = "us", language = "en", country = "US", quality = 400)
        val gbHigh = voice(name = "gb", language = "en", country = "GB", quality = 400)
        val usBest = voice(name = "us-best", language = "en", country = "US", quality = 500)
        assertEquals("gb", chooseVoice(listOf(usHigh, gbHigh), "en", "GB", false)?.name)
        assertEquals("us-best", chooseVoice(listOf(usBest, gbHigh), "en", "GB", false)?.name)
    }

    @Test
    fun choosesVoiceThatStillNeedsDownload() {
        val installed = voice(name = "old", quality = 200, installed = true)
        val better = voice(name = "new", quality = 400, installed = false)
        assertEquals("new", chooseVoice(listOf(installed, better), "ru", "RU", false)?.name)
    }

    @Test
    fun femaleBonusDoesNotBeatQuality() {
        val female = voice(name = "f", language = "en", quality = 300, female = true)
        val male = voice(name = "m", language = "en", quality = 400, female = false)
        assertEquals("m", chooseVoice(listOf(female, male), "en", "GB", true)?.name)
    }

    @Test
    fun ignoresAnotherLanguage() {
        val english = voice(name = "en", language = "en")
        assertNull(chooseVoice(listOf(english), "ru", "RU", false))
    }

    @Test
    fun picksAmericanEnglishTagFirst() {
        assertEquals("en-US", englishRecognitionTag(listOf("ru-RU", "en-GB", "en-US")))
        assertEquals("en-GB", englishRecognitionTag(listOf("en-GB")))
        assertEquals("en", englishRecognitionTag(listOf("en")))
        assertNull(englishRecognitionTag(listOf("ru-RU")))
    }

    private fun voice(
        name: String,
        language: String = "ru",
        country: String = "RU",
        installed: Boolean = true,
        needsNetwork: Boolean = false,
        quality: Int = 400,
        female: Boolean = false,
    ) = ListedVoice(
        name = name,
        language = language,
        country = country,
        installed = installed,
        needsNetwork = needsNetwork,
        quality = quality,
        female = female,
    )
}
