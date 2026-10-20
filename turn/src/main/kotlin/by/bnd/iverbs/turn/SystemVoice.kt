package by.bnd.iverbs.turn

/**
 * Голос из списка движка телефона.
 * Имя нужно, чтобы после скачивания найти тот же голос снова.
 */
data class ListedVoice(
    val name: String,
    val language: String,
    val country: String,
    val installed: Boolean,
    val needsNetwork: Boolean,
    val quality: Int,
    val female: Boolean,
)

/**
 * Какой голос взять для языка.
 * Сначала голос без сети, затем качество. Совпадение страны и женский голос
 * лишь склоняют выбор: более качественный голос другой страны остаётся выше.
 * Нескачанный голос тоже выбирается: его ещё предстоит скачать.
 */
fun chooseVoice(
    voices: List<ListedVoice>,
    language: String,
    country: String,
    preferFemale: Boolean,
): ListedVoice? {
    val matching = voices.filter { it.language.equals(language, ignoreCase = true) }
    return matching.maxWithOrNull(
        compareBy(
            { !it.needsNetwork },
            { it.quality + countryBonus(it, country) + femaleBonus(it, preferFemale) },
            { it.installed },
        ),
    )
}

/** Какой английский тег просить у распознавания. Сначала en-US: его модель на телефоне обычно есть. */
fun englishRecognitionTag(tags: List<String>): String? {
    val english = tags.filter { tag ->
        val lower = tag.lowercase()
        lower == "en" || lower.startsWith("en-") || lower.startsWith("en_")
    }
    return english.firstOrNull { it.equals("en-US", ignoreCase = true) }
        ?: english.firstOrNull { it.equals("en_US", ignoreCase = true) }
        ?: english.firstOrNull { it.equals("en-GB", ignoreCase = true) }
        ?: english.firstOrNull { it.equals("en_GB", ignoreCase = true) }
        ?: english.firstOrNull()
}

private fun countryBonus(voice: ListedVoice, country: String): Int =
    if (voice.country.equals(country, ignoreCase = true)) 50 else 0

private fun femaleBonus(voice: ListedVoice, preferFemale: Boolean): Int =
    if (preferFemale && voice.female) 25 else 0
