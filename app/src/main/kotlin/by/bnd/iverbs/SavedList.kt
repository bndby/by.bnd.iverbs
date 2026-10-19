package by.bnd.iverbs

import android.content.Context
import by.bnd.iverbs.turn.Verb

/** Нет записи — первый запуск, отмечен весь набор. Пустая строка — сняты все отметки. */
class SavedList(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(set: List<Verb>): List<Verb>? {
        if (!prefs.contains(KEY)) return null
        val raw = prefs.getString(KEY, "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        val infinitives = raw.split('\n').toSet()
        return set.filter { it.forms.infinitive in infinitives }
    }

    fun write(list: List<Verb>) {
        val raw = list.joinToString("\n") { it.forms.infinitive }
        prefs.edit().putString(KEY, raw).apply()
    }

    private companion object {
        const val PREFS = "iverbs"
        const val KEY = "list"
    }
}
