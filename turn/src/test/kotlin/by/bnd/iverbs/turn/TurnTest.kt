package by.bnd.iverbs.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnTest {
    @Test
    fun `новый ход открывает главную с полосами Учим, Проверка и Глаголы`() {
        val turn = turnOf(draw = PredeterminedDraw(listOf(go)))

        val reaction = turn.shown()

        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), reaction.show)
        assertEquals(null, reaction.command)
    }

    @Test
    fun `Глаголы с главной показывают набор по порядку и не начинают ход`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val be = Verb("быть", Forms("be", "was", "been"))
        val turn = turnOf(
            set = listOf(go, eat, be),
            list = listOf(eat),
            draw = Draw { error("ход не начинается") },
        )

        val reaction = turn.on(Input.EnterVerbs)
        val verbs = reaction.show as Show.Verbs

        assertEquals(
            listOf(
                VerbRow(verb = go, marked = false),
                VerbRow(verb = eat, marked = true),
                VerbRow(verb = be, marked = false),
            ),
            verbs.rows,
        )
        assertEquals(listOf(Control.Home), verbs.controls)
        assertEquals(listOf("Главная"), verbs.controls.map { it.label })
        assertEquals(null, reaction.command)
        assertEquals(reaction.show, turn.shown().show)
        assertEquals(null, turn.shown().command)
    }

    @Test
    fun `снятие отметки убирает глагол из Учим и Проверки и оставляет его в наборе`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val learn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = Draw { verbs -> verbs.single() },
        )
        learn.on(Input.EnterVerbs)

        val page = learn.on(Input.Toggle(eat)).show as Show.Verbs
        assertEquals(
            listOf(
                VerbRow(verb = go, marked = true),
                VerbRow(verb = eat, marked = false),
            ),
            page.rows,
        )
        assertEquals("есть", page.rows[1].verb.meaning)
        assertEquals(Forms("eat", "ate", "eaten"), page.rows[1].verb.forms)

        val home = learn.on(Input.Home)
        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), home.show)
        assertEquals(null, home.command)

        val learned = learn.on(Input.EnterLearn)
        assertEquals("идти", (learned.show as Show.Learn).headline)
        assertEquals(Command.Speak(phrase("идти", Forms("go", "went", "gone"))), learned.command)

        val check = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = Draw { verbs -> verbs.single() },
        )
        check.on(Input.EnterVerbs)
        check.on(Input.Toggle(eat))
        check.on(Input.Home)
        val checked = check.on(Input.EnterCheck)
        assertEquals("go", (checked.show as Show.Check).headline)
        assertEquals(Command.Speak(infinitivePhrase("go")), checked.command)
    }

    @Test
    fun `повторная отметка возвращает глагол в Учим и в Проверку`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val learn = turnOf(
            set = listOf(go, eat),
            list = listOf(go),
            draw = Draw { verbs -> verbs.last() },
        )
        learn.on(Input.EnterVerbs)

        val page = learn.on(Input.Toggle(eat)).show as Show.Verbs
        assertEquals(
            listOf(
                VerbRow(verb = go, marked = true),
                VerbRow(verb = eat, marked = true),
            ),
            page.rows,
        )

        learn.on(Input.Home)
        val learned = learn.on(Input.EnterLearn)
        assertEquals("есть", (learned.show as Show.Learn).headline)
        assertEquals(Command.Speak(phrase("есть", Forms("eat", "ate", "eaten"))), learned.command)

        val check = turnOf(
            set = listOf(go, eat),
            list = listOf(go),
            draw = Draw { verbs -> verbs.last() },
        )
        check.on(Input.EnterVerbs)
        check.on(Input.Toggle(eat))
        check.on(Input.Home)
        val checked = check.on(Input.EnterCheck)
        assertEquals("eat", (checked.show as Show.Check).headline)
        assertEquals(Command.Speak(infinitivePhrase("eat")), checked.command)
    }

    @Test
    fun `чужой или исправленный глагол не меняет набор`() {
        val foreign = Verb("петь", Forms("sing", "sang", "sung"))
        val edited = Verb("идти", Forms("go", "goed", "gone"))
        val turn = turnOf(
            set = listOf(go),
            list = listOf(go),
            draw = Draw { error("ход не начинается") },
        )
        turn.on(Input.EnterVerbs)

        val added = turn.on(Input.Toggle(foreign)).show as Show.Verbs
        val changed = turn.on(Input.Toggle(edited)).show as Show.Verbs

        assertEquals(listOf(VerbRow(verb = go, marked = true)), added.rows)
        assertEquals(listOf(VerbRow(verb = go, marked = true)), changed.rows)
    }

    @Test
    fun `пустой список с главной открывает страницу глаголов и не начинает ход`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val rows = listOf(
            VerbRow(verb = go, marked = false),
            VerbRow(verb = eat, marked = false),
        )
        val learn = turnOf(
            set = listOf(go, eat),
            list = emptyList(),
            draw = Draw { error("ход не начинается") },
        )

        val learned = learn.on(Input.EnterLearn)

        assertEquals(Show.Verbs(rows = rows, controls = listOf(Control.Home)), learned.show)
        assertEquals(null, learned.command)

        val check = turnOf(
            set = listOf(go, eat),
            list = emptyList(),
            draw = Draw { error("ход не начинается") },
        )
        val checked = check.on(Input.EnterCheck)
        assertEquals(Show.Verbs(rows = rows, controls = listOf(Control.Home)), checked.show)
        assertEquals(null, checked.command)
    }

    @Test
    fun `Дальше при пустом списке открывает страницу глаголов и не начинает ход`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val unmarked = listOf(
            VerbRow(verb = go, marked = false),
            VerbRow(verb = eat, marked = false),
        )
        val turn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, eat)),
        )
        turn.on(Input.EnterLearn)
        turn.on(Input.PhraseEnded)

        val cleared = turn.on(Input.Toggle(eat))
        turn.on(Input.Toggle(go))
        assertEquals("идти", (cleared.show as Show.Learn).headline)
        assertEquals(null, cleared.command)

        val fromListening = turn.on(Input.Next)
        assertEquals(Show.Verbs(rows = unmarked, controls = listOf(Control.Home)), fromListening.show)
        assertEquals(Command.Stop, fromListening.command)

        val home = turn.on(Input.Home)
        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), home.show)
        assertEquals(null, home.command)

        val afterVerdict = turnOf(
            set = listOf(go),
            list = listOf(go),
            draw = PredeterminedDraw(listOf(go)),
        )
        afterVerdict.on(Input.EnterCheck)
        afterVerdict.on(Input.PhraseEnded)
        afterVerdict.on(Input.ListeningEnded("мимо"))
        afterVerdict.on(Input.Toggle(go))
        val fromVerdict = afterVerdict.on(Input.Next)
        assertEquals(
            Show.Verbs(
                rows = listOf(VerbRow(verb = go, marked = false)),
                controls = listOf(Control.Home),
            ),
            fromVerdict.show,
        )
        assertEquals(null, fromVerdict.command)
    }

    @Test
    fun `во время хода страница глаголов с Учим и Проверки не открывается`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.EnterLearn)
        learn.on(Input.PhraseEnded)

        val duringLearn = learn.on(Input.EnterVerbs)

        assertEquals(
            Show.Learn(
                kicker = null,
                headline = "идти",
                forms = null,
                recognised = null,
                controls = listOf(Control.Ready, Control.Again, Control.Next),
            ),
            duringLearn.show,
        )
        assertEquals(null, duringLearn.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        check.on(Input.ListeningEnded("go went gone"))
        val duringCheck = check.on(Input.EnterVerbs)
        val shown = duringCheck.show as Show.Check
        assertEquals("Совпало", shown.headline)
        assertEquals(null, duringCheck.command)
    }

    @Test
    fun `вход в Учим берёт глагол из списка и один раз говорит значение и три формы`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val turn = turnOf(
            set = listOf(go, eat),
            list = listOf(eat),
            draw = Draw { list -> list.single() },
        )

        val reaction = turn.on(Input.EnterLearn)

        assertEquals(
            Show.Learn(
                kicker = "Говорит",
                headline = "есть",
                forms = Forms("eat", "ate", "eaten"),
                recognised = null,
                controls = listOf(Control.Again),
            ),
            reaction.show,
        )
        assertEquals(Command.Speak(phrase("есть", Forms("eat", "ate", "eaten"))), reaction.command)
        assertEquals(reaction.show, turn.shown().show)
        assertEquals(null, turn.shown().command)
    }

    @Test
    fun `вход в Проверку берёт глагол из списка и один раз говорит только инфинитив`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val turn = turnOf(
            set = listOf(go, eat),
            list = listOf(eat),
            draw = Draw { list -> list.single() },
        )

        val reaction = turn.on(Input.EnterCheck)

        assertEquals(
            Show.Check(
                headline = "eat",
                forms = null,
                recognised = null,
                controls = listOf(Control.Again),
            ),
            reaction.show,
        )
        assertEquals(Command.Speak(infinitivePhrase("eat")), reaction.command)
        assertEquals(reaction.show, turn.shown().show)
        assertEquals(null, turn.shown().command)
    }

    @Test
    fun `конец фразы открывает слушание без трёх форм`() {
        val turn = turnOf(draw = PredeterminedDraw(listOf(go)))
        turn.on(Input.EnterLearn)

        val reaction = turn.on(Input.PhraseEnded)
        val learn = reaction.show as Show.Learn

        assertEquals(
            Show.Learn(
                kicker = null,
                headline = "идти",
                forms = null,
                recognised = null,
                controls = listOf(Control.Ready, Control.Again, Control.Next),
            ),
            learn,
        )
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), learn.controls.map { it.label })
        assertEquals(Command.Listen, reaction.command)
        assertEquals(null, turn.shown().command)
    }

    @Test
    fun `конец фразы Проверки открывает слушание и оставляет только инфинитив`() {
        val turn = turnOf(draw = PredeterminedDraw(listOf(go)))
        turn.on(Input.EnterCheck)

        val reaction = turn.on(Input.PhraseEnded)
        val check = reaction.show as Show.Check

        assertEquals(
            Show.Check(
                headline = "go",
                forms = null,
                recognised = null,
                controls = listOf(Control.Ready, Control.Again, Control.Next),
            ),
            check,
        )
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), check.controls.map { it.label })
        assertEquals(Command.Listen, reaction.command)
        assertEquals(null, turn.shown().command)
    }

    @Test
    fun `конец слушания ставит вердикт и показывает текст как его вернул слух`() {
        val be = Verb("быть", Forms("be", "was", "been"))
        val cases = listOf(
            go to "Go,  went, gone." to "Совпало",
            go to "GO WENT GONE" to "Совпало",
            go to "go   went   gone" to "Совпало",
            go to "to go went gone" to "Не совпало",
            go to "the go went gone" to "Не совпало",
            go to "a go went gone" to "Не совпало",
            go to "went go gone" to "Не совпало",
            go to "go went" to "Не совпало",
            go to "go went gon" to "Не совпало",
            go to "" to "Не совпало",
            be to "be were been" to "Не совпало",
            be to "be was been" to "Совпало",
        )
        cases.forEach { (verbAndText, headline) ->
            val (verb, text) = verbAndText
            val turn = turnOf(
                set = listOf(verb),
                list = listOf(verb),
                draw = PredeterminedDraw(listOf(verb)),
            )
            turn.on(Input.EnterLearn)
            turn.on(Input.PhraseEnded)

            val reaction = turn.on(Input.ListeningEnded(text))
            val learn = reaction.show as Show.Learn

            assertEquals(text, headline, learn.headline)
            assertEquals(text, verb.forms, learn.forms)
            assertEquals(text, text, learn.recognised)
            assertEquals(text, null, learn.kicker)
            assertEquals(text, listOf("Ещё раз", "Дальше"), learn.controls.map { it.label })
            assertEquals(text, null, reaction.command)
        }
    }

    @Test
    fun `конец слушания Проверки ставит вердикт и показывает текст как его вернул слух`() {
        val be = Verb("быть", Forms("be", "was", "been"))
        val cases = listOf(
            go to "Go,  went, gone." to "Совпало",
            go to "GO WENT GONE" to "Совпало",
            go to "go   went   gone" to "Совпало",
            go to "to go went gone" to "Не совпало",
            go to "the go went gone" to "Не совпало",
            go to "a go went gone" to "Не совпало",
            go to "went go gone" to "Не совпало",
            go to "go went" to "Не совпало",
            go to "go went gon" to "Не совпало",
            go to "" to "Не совпало",
            be to "be were been" to "Не совпало",
            be to "be was been" to "Совпало",
        )
        cases.forEach { (verbAndText, headline) ->
            val (verb, text) = verbAndText
            val turn = turnOf(
                set = listOf(verb),
                list = listOf(verb),
                draw = PredeterminedDraw(listOf(verb)),
            )
            turn.on(Input.EnterCheck)
            turn.on(Input.PhraseEnded)

            val reaction = turn.on(Input.ListeningEnded(text))
            val check = reaction.show as Show.Check

            assertEquals(text, headline, check.headline)
            assertEquals(text, verb.forms, check.forms)
            assertEquals(text, text, check.recognised)
            assertEquals(text, listOf("Ещё раз", "Дальше"), check.controls.map { it.label })
            assertEquals(text, null, reaction.command)
        }
    }

    @Test
    fun `Готово останавливает слушание и сразу ставит вердикт`() {
        val turn = turnOf(draw = PredeterminedDraw(listOf(go)))
        turn.on(Input.EnterLearn)
        turn.on(Input.PhraseEnded)

        val reaction = turn.on(Input.Ready)
        val learn = reaction.show as Show.Learn

        assertEquals("Не совпало", learn.headline)
        assertEquals(Forms("go", "went", "gone"), learn.forms)
        assertEquals("", learn.recognised)
        assertEquals(listOf("Ещё раз", "Дальше"), learn.controls.map { it.label })
        assertEquals(Command.Stop, reaction.command)
    }

    @Test
    fun `Готово в Проверке останавливает слушание и сразу ставит вердикт`() {
        val turn = turnOf(draw = PredeterminedDraw(listOf(go)))
        turn.on(Input.EnterCheck)
        turn.on(Input.PhraseEnded)

        val reaction = turn.on(Input.Ready)
        val check = reaction.show as Show.Check

        assertEquals("Не совпало", check.headline)
        assertEquals(Forms("go", "went", "gone"), check.forms)
        assertEquals("", check.recognised)
        assertEquals(listOf("Ещё раз", "Дальше"), check.controls.map { it.label })
        assertEquals(Command.Stop, reaction.command)
    }

    @Test
    fun `Ещё раз говорит тот же глагол с начала и снова открывает слушание`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val turn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, eat)),
        )
        val same = Command.Speak(phrase("идти", Forms("go", "went", "gone")))
        turn.on(Input.EnterLearn)

        val duringSpeech = turn.on(Input.Again)
        val speaking = duringSpeech.show as Show.Learn
        assertEquals("Говорит", speaking.kicker)
        assertEquals("идти", speaking.headline)
        assertEquals(Forms("go", "went", "gone"), speaking.forms)
        assertEquals(listOf("Ещё раз"), speaking.controls.map { it.label })
        assertEquals(same, duringSpeech.command)

        val spokenAgain = turn.on(Input.PhraseEnded)
        assertEquals(Command.Listen, spokenAgain.command)

        val duringListen = turn.on(Input.Again)
        assertEquals(same, duringListen.command)
        assertEquals("Говорит", (duringListen.show as Show.Learn).kicker)
        assertEquals("идти", (duringListen.show as Show.Learn).headline)

        val listeningAgain = turn.on(Input.PhraseEnded)
        assertEquals(Command.Listen, listeningAgain.command)
        assertEquals(null, (listeningAgain.show as Show.Learn).forms)
        assertEquals("идти", (listeningAgain.show as Show.Learn).headline)

        turn.on(Input.ListeningEnded("нет"))
        val afterVerdict = turn.on(Input.Again)
        assertEquals(same, afterVerdict.command)
        assertEquals("идти", (afterVerdict.show as Show.Learn).headline)
        assertEquals("Говорит", (afterVerdict.show as Show.Learn).kicker)
    }

    @Test
    fun `Дальше берёт новый глагол жеребьёвкой и тот же может повториться`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val turn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, go, eat, go)),
        )
        turn.on(Input.EnterLearn)
        turn.on(Input.PhraseEnded)

        val repeated = turn.on(Input.Next)
        val repeatedLearn = repeated.show as Show.Learn
        assertEquals("Говорит", repeatedLearn.kicker)
        assertEquals("идти", repeatedLearn.headline)
        assertEquals(Forms("go", "went", "gone"), repeatedLearn.forms)
        assertEquals(listOf("Ещё раз"), repeatedLearn.controls.map { it.label })
        assertEquals(Command.Speak(phrase("идти", Forms("go", "went", "gone"))), repeated.command)

        turn.on(Input.PhraseEnded)
        val another = turn.on(Input.Next)
        assertEquals("есть", (another.show as Show.Learn).headline)
        assertEquals(Forms("eat", "ate", "eaten"), (another.show as Show.Learn).forms)
        assertEquals(Command.Speak(phrase("есть", Forms("eat", "ate", "eaten"))), another.command)

        turn.on(Input.PhraseEnded)
        turn.on(Input.ListeningEnded("мимо"))
        val afterVerdict = turn.on(Input.Next)
        assertEquals("идти", (afterVerdict.show as Show.Learn).headline)
        assertEquals(Command.Speak(phrase("идти", Forms("go", "went", "gone"))), afterVerdict.command)
    }

    @Test
    fun `Ещё раз в Проверке снова говорит только инфинитив того же глагола`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val turn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, eat)),
        )
        val same = Command.Speak(infinitivePhrase("go"))
        turn.on(Input.EnterCheck)

        val duringSpeech = turn.on(Input.Again)
        val speaking = duringSpeech.show as Show.Check
        assertEquals("go", speaking.headline)
        assertEquals(null, speaking.forms)
        assertEquals(null, speaking.recognised)
        assertEquals(listOf("Ещё раз"), speaking.controls.map { it.label })
        assertEquals(same, duringSpeech.command)

        val spokenAgain = turn.on(Input.PhraseEnded)
        assertEquals(Command.Listen, spokenAgain.command)
        assertEquals("go", (spokenAgain.show as Show.Check).headline)
        assertEquals(null, (spokenAgain.show as Show.Check).forms)

        val duringListen = turn.on(Input.Again)
        assertEquals(same, duringListen.command)
        assertEquals("go", (duringListen.show as Show.Check).headline)
        assertEquals(null, (duringListen.show as Show.Check).forms)

        turn.on(Input.PhraseEnded)
        turn.on(Input.ListeningEnded("нет"))
        val afterVerdict = turn.on(Input.Again)
        assertEquals(same, afterVerdict.command)
        assertEquals("go", (afterVerdict.show as Show.Check).headline)
        assertEquals(null, (afterVerdict.show as Show.Check).forms)
        assertEquals(listOf("Ещё раз"), (afterVerdict.show as Show.Check).controls.map { it.label })
    }

    @Test
    fun `Дальше в Проверке берёт новый глагол и тот же может повториться`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val turn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, go, eat, go)),
        )
        turn.on(Input.EnterCheck)
        turn.on(Input.PhraseEnded)

        val repeated = turn.on(Input.Next)
        val repeatedCheck = repeated.show as Show.Check
        assertEquals("go", repeatedCheck.headline)
        assertEquals(null, repeatedCheck.forms)
        assertEquals(listOf("Ещё раз"), repeatedCheck.controls.map { it.label })
        assertEquals(Command.Speak(infinitivePhrase("go")), repeated.command)

        turn.on(Input.PhraseEnded)
        val another = turn.on(Input.Next)
        assertEquals("eat", (another.show as Show.Check).headline)
        assertEquals(null, (another.show as Show.Check).forms)
        assertEquals(Command.Speak(infinitivePhrase("eat")), another.command)

        turn.on(Input.PhraseEnded)
        turn.on(Input.ListeningEnded("мимо"))
        val afterVerdict = turn.on(Input.Next)
        assertEquals("go", (afterVerdict.show as Show.Check).headline)
        assertEquals(Command.Speak(infinitivePhrase("go")), afterVerdict.command)
    }

    @Test
    fun `новый запуск открывает главную и не хранит незаконченный ход`() {
        val first = turnOf(draw = PredeterminedDraw(listOf(go)))
        first.on(Input.EnterLearn)
        first.on(Input.PhraseEnded)
        first.on(Input.ListeningEnded("go went gone"))

        val second = turnOf(draw = PredeterminedDraw(listOf(go)))

        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), second.shown().show)
        assertEquals(null, second.shown().command)
    }
}

private val go = Verb("идти", Forms("go", "went", "gone"))

private fun turnOf(draw: Draw, list: List<Verb> = listOf(go), set: List<Verb> = listOf(go)) =
    Turn(set = set, list = list, draw = draw)

private fun infinitivePhrase(infinitive: String) = Phrase(
    parts = listOf(PhrasePart.Said(infinitive, Voice.English)),
)

private fun phrase(meaning: String, forms: Forms) = Phrase(
    parts = listOf(
        PhrasePart.Said(meaning, Voice.Russian),
        PhrasePart.Pause,
        PhrasePart.Said(forms.infinitive, Voice.English),
        PhrasePart.Pause,
        PhrasePart.Said(forms.pastSimple, Voice.English),
        PhrasePart.Pause,
        PhrasePart.Said(forms.pastParticiple, Voice.English),
    ),
)

private class PredeterminedDraw(private val verbs: List<Verb>) : Draw {
    private var index = 0

    override fun next(list: List<Verb>): Verb {
        check(index < verbs.size) { "жеребьёвка исчерпана" }
        return verbs[index++]
    }
}
