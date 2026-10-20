package by.bnd.iverbs.turn

/**
 * Слова, которые ученик может сказать в ответ.
 * Все формы набора, каждая один раз, в порядке набора.
 * Три формы только текущего глагола сюда не входят: иначе близкий чужой глагол
 * распознавалка может подменить записью хода.
 */
fun recognitionBias(verbs: List<Verb>): List<String> {
    val phrases = LinkedHashSet<String>()
    for (verb in verbs) {
        phrases += verb.forms.infinitive
        phrases += verb.forms.pastSimple
        phrases += verb.forms.pastParticiple
    }
    return phrases.filter { it.isNotBlank() }
}
