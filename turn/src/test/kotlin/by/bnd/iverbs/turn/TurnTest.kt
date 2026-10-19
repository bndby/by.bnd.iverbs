package by.bnd.iverbs.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnTest {
    @Test
    fun `новый ход открывает главную только с полосой Учим`() {
        val turn = turnOf(draw = PredeterminedDraw(listOf(go)))

        val reaction = turn.shown()

        assertEquals(Show.Home(strips = listOf("Учим")), reaction.show)
        assertEquals(null, reaction.command)
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
    fun `новый запуск открывает главную и не хранит незаконченный ход`() {
        val first = turnOf(draw = PredeterminedDraw(listOf(go)))
        first.on(Input.EnterLearn)
        first.on(Input.PhraseEnded)
        first.on(Input.ListeningEnded("go went gone"))

        val second = turnOf(draw = PredeterminedDraw(listOf(go)))

        assertEquals(Show.Home(strips = listOf("Учим")), second.shown().show)
        assertEquals(null, second.shown().command)
    }
}

private val go = Verb("идти", Forms("go", "went", "gone"))

private fun turnOf(draw: Draw, list: List<Verb> = listOf(go), set: List<Verb> = listOf(go)) =
    Turn(set = set, list = list, draw = draw)

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
