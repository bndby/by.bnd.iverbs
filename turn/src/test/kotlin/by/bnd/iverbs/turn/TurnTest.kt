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
    fun `во время слушания и после вердикта страница глаголов открывается и ход стоит`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.EnterLearn)
        val listening = learn.on(Input.PhraseEnded).show as Show.Learn
        assertEquals(listOf(Control.Home, Control.Verbs), listening.nav)
        assertEquals(listOf("Главная", "Глаголы"), listening.nav.map { it.label })

        val duringLearn = learn.on(Input.EnterVerbs)
        val page = duringLearn.show as Show.Verbs

        assertEquals(listOf(VerbRow(verb = go, marked = true)), page.rows)
        assertEquals(listOf(Control.Home, Control.Back), page.controls)
        assertEquals(listOf("Главная", "К ходу"), page.controls.map { it.label })
        assertEquals(Command.Stop, duringLearn.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        val checked = check.on(Input.ListeningEnded("go went gone")).show as Show.Check
        assertEquals(listOf(Control.Home, Control.Verbs), checked.nav)

        val duringCheck = check.on(Input.EnterVerbs)
        val verbs = duringCheck.show as Show.Verbs
        assertEquals(listOf(Control.Home, Control.Back), verbs.controls)
        assertEquals(Command.Stop, duringCheck.command)
    }

    @Test
    fun `смена отметок не меняет глагол хода, а К ходу возвращает ту же фазу`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val learn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go)),
        )
        learn.on(Input.EnterLearn)
        learn.on(Input.PhraseEnded)
        learn.on(Input.EnterVerbs)

        val cleared = learn.on(Input.Toggle(go)).show as Show.Verbs
        assertEquals(
            listOf(
                VerbRow(verb = go, marked = false),
                VerbRow(verb = eat, marked = true),
            ),
            cleared.rows,
        )
        assertEquals(null, learn.on(Input.Toggle(eat)).command)

        val back = learn.on(Input.Back)
        val shown = back.show as Show.Learn
        assertEquals("идти", shown.headline)
        assertEquals(null, shown.forms)
        assertEquals("Слушает", shown.kicker)
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), shown.controls.map { it.label })
        assertEquals(Command.Listen, back.command)

        val check = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go)),
        )
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        check.on(Input.ListeningEnded("go went gone"))
        check.on(Input.EnterVerbs)
        check.on(Input.Toggle(go))

        val returned = check.on(Input.Back)
        val verdict = returned.show as Show.Check
        assertEquals("Совпало", verdict.headline)
        assertEquals(Forms("go", "went", "gone"), verdict.forms)
        assertEquals("go went gone", verdict.recognised)
        assertEquals(listOf("Ещё раз", "Дальше"), verdict.controls.map { it.label })
        assertEquals(null, returned.command)
    }

    @Test
    fun `под страницей глаголов исход речи не ставит вердикт и не двигает фазу`() {
        val speaking = turnOf(draw = PredeterminedDraw(listOf(go)))
        speaking.on(Input.EnterLearn)
        speaking.on(Input.EnterVerbs)

        val phrase = speaking.on(Input.PhraseEnded)
        assertEquals(listOf("Главная", "К ходу"), (phrase.show as Show.Verbs).controls.map { it.label })
        assertEquals(Command.Stop, phrase.command)

        val listening = turnOf(draw = PredeterminedDraw(listOf(go)))
        listening.on(Input.EnterCheck)
        listening.on(Input.PhraseEnded)
        listening.on(Input.EnterVerbs)

        val heard = listening.on(Input.ListeningEnded("go went gone"))
        assertEquals(listOf("Главная", "К ходу"), (heard.show as Show.Verbs).controls.map { it.label })
        assertEquals(Command.Stop, heard.command)
    }

    @Test
    fun `прерванная озвучка сама не начинается, в Учим ход ждёт, в Проверке только инфинитив`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.EnterLearn)
        learn.on(Input.EnterVerbs)
        learn.on(Input.PhraseEnded)

        val back = learn.on(Input.Back)
        val shown = back.show as Show.Learn
        assertEquals("Ход ждёт", shown.kicker)
        assertEquals("идти", shown.headline)
        assertEquals(Forms("go", "went", "gone"), shown.forms)
        assertEquals(null, shown.recognised)
        assertEquals(listOf("Ещё раз"), shown.controls.map { it.label })
        assertEquals(null, back.command)

        val stray = learn.on(Input.PhraseEnded)
        assertEquals("Ход ждёт", (stray.show as Show.Learn).kicker)
        assertEquals(Forms("go", "went", "gone"), (stray.show as Show.Learn).forms)
        assertEquals(null, stray.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.EnterCheck)
        check.on(Input.EnterVerbs)

        val returned = check.on(Input.Back)
        val checked = returned.show as Show.Check
        assertEquals("go", checked.headline)
        assertEquals(null, checked.kicker)
        assertEquals(null, checked.forms)
        assertEquals(null, checked.recognised)
        assertEquals(listOf("Ещё раз"), checked.controls.map { it.label })
        assertEquals(null, returned.command)
    }

    @Test
    fun `ожидающий ход снова открывает страницу глаголов и не начинает фразу`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.EnterLearn)
        learn.on(Input.EnterVerbs)
        learn.on(Input.Back)

        val page = learn.on(Input.EnterVerbs)
        assertEquals(listOf("Главная", "К ходу"), (page.show as Show.Verbs).controls.map { it.label })
        assertEquals(null, page.command)

        val back = learn.on(Input.Back)
        val shown = back.show as Show.Learn
        assertEquals("Ход ждёт", shown.kicker)
        assertEquals("идти", shown.headline)
        assertEquals(Forms("go", "went", "gone"), shown.forms)
        assertEquals(listOf("Ещё раз"), shown.controls.map { it.label })
        assertEquals(null, back.command)

        val home = learn.on(Input.Home)
        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), home.show)
        assertEquals(null, home.command)
    }

    @Test
    fun `возврат в слушание слушает заново и сбрасывает текст, сказанный до ухода`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.EnterLearn)
        learn.on(Input.PhraseEnded)
        learn.on(Input.EnterVerbs)
        learn.on(Input.ListeningEnded("go went gone"))

        val back = learn.on(Input.Back)
        val shown = back.show as Show.Learn
        assertEquals("идти", shown.headline)
        assertEquals(null, shown.forms)
        assertEquals(null, shown.recognised)
        assertEquals("Слушает", shown.kicker)
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), shown.controls.map { it.label })
        assertEquals(Command.Listen, back.command)

        val verdict = learn.on(Input.ListeningEnded("мимо"))
        val learned = verdict.show as Show.Learn
        assertEquals("Не совпало", learned.headline)
        assertEquals("мимо", learned.recognised)
        assertEquals(Forms("go", "went", "gone"), learned.forms)
        assertEquals(null, verdict.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        check.on(Input.EnterVerbs)
        check.on(Input.ListeningEnded("go went gone"))

        val returned = check.on(Input.Back)
        assertEquals("go", (returned.show as Show.Check).headline)
        assertEquals("Слушает", (returned.show as Show.Check).kicker)
        assertEquals(null, (returned.show as Show.Check).forms)
        assertEquals(Command.Listen, returned.command)

        val ready = check.on(Input.Ready)
        val checked = ready.show as Show.Check
        assertEquals("Не совпало", checked.headline)
        assertEquals("", checked.recognised)
        assertEquals(Command.Stop, ready.command)
    }

    @Test
    fun `Ещё раз повторяет глагол этого хода, даже если отметку сняли и список пуст`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.EnterLearn)
        learn.on(Input.EnterVerbs)
        learn.on(Input.Toggle(go))
        val waiting = learn.on(Input.Back).show as Show.Learn
        assertEquals("Ход ждёт", waiting.kicker)

        val again = learn.on(Input.Again)
        val shown = again.show as Show.Learn
        assertEquals("Говорит", shown.kicker)
        assertEquals("идти", shown.headline)
        assertEquals(Forms("go", "went", "gone"), shown.forms)
        assertEquals(Command.Speak(phrase("идти", Forms("go", "went", "gone"))), again.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        check.on(Input.ListeningEnded("нет"))
        check.on(Input.EnterVerbs)
        check.on(Input.Toggle(go))
        check.on(Input.Back)

        val repeated = check.on(Input.Again)
        assertEquals("go", (repeated.show as Show.Check).headline)
        assertEquals(null, (repeated.show as Show.Check).forms)
        assertEquals(Command.Speak(infinitivePhrase("go")), repeated.command)
    }

    @Test
    fun `Дальше берёт глагол из уже нового списка, а пустой список открывает страницу без хода`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val seen = mutableListOf<List<String>>()
        val learn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = Draw { verbs ->
                seen += verbs.map { it.forms.infinitive }
                verbs.last()
            },
        )
        learn.on(Input.EnterLearn)
        learn.on(Input.PhraseEnded)
        learn.on(Input.EnterVerbs)
        learn.on(Input.Toggle(eat))
        learn.on(Input.Back)

        val next = learn.on(Input.Next)
        assertEquals(listOf(listOf("go", "eat"), listOf("go")), seen)
        assertEquals("идти", (next.show as Show.Learn).headline)
        assertEquals(Forms("go", "went", "gone"), (next.show as Show.Learn).forms)
        assertEquals(Command.Speak(phrase("идти", Forms("go", "went", "gone"))), next.command)

        val check = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go)),
        )
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        check.on(Input.EnterVerbs)
        check.on(Input.Toggle(go))
        check.on(Input.Toggle(eat))
        check.on(Input.Back)

        val empty = check.on(Input.Next)
        assertEquals(
            Show.Verbs(
                rows = listOf(
                    VerbRow(verb = go, marked = false),
                    VerbRow(verb = eat, marked = false),
                ),
                controls = listOf(Control.Home),
            ),
            empty.show,
        )
        assertEquals(Command.Stop, empty.command)
        assertEquals(null, check.on(Input.Back).command)
        assertEquals(listOf("Главная"), (check.shown().show as Show.Verbs).controls.map { it.label })
    }

    @Test
    fun `Главная из режима и со страницы глаголов бросает ход и оставляет три полосы`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val learn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, eat)),
        )
        learn.on(Input.EnterLearn)

        val home = learn.on(Input.Home)
        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), home.show)
        assertEquals(Command.Stop, home.command)

        val restarted = learn.on(Input.EnterLearn)
        assertEquals("есть", (restarted.show as Show.Learn).headline)
        assertEquals("Говорит", (restarted.show as Show.Learn).kicker)
        assertEquals(Command.Speak(phrase("есть", Forms("eat", "ate", "eaten"))), restarted.command)

        val fromPage = turnOf(draw = PredeterminedDraw(listOf(go)))
        fromPage.on(Input.EnterCheck)
        fromPage.on(Input.PhraseEnded)
        fromPage.on(Input.EnterVerbs)

        val left = fromPage.on(Input.Home)
        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), left.show)
        assertEquals(Command.Stop, left.command)

        val verbs = fromPage.on(Input.EnterVerbs)
        assertEquals(listOf("Главная"), (verbs.show as Show.Verbs).controls.map { it.label })
        assertEquals(null, verbs.command)

        val verdict = turnOf(draw = PredeterminedDraw(listOf(go)))
        verdict.on(Input.EnterLearn)
        verdict.on(Input.PhraseEnded)
        verdict.on(Input.ListeningEnded("нет"))
        val dropped = verdict.on(Input.Home)
        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), dropped.show)
        assertEquals(null, dropped.command)
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
                nav = listOf(Control.Home, Control.Verbs),
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
                nav = listOf(Control.Home, Control.Verbs),
                kicker = "Говорит",
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
                kicker = "Слушает",
                headline = "идти",
                forms = null,
                recognised = null,
                controls = listOf(Control.Ready, Control.Again, Control.Next),
                nav = listOf(Control.Home, Control.Verbs),
            ),
            learn,
        )
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), learn.controls.map { it.label })
        assertEquals(Command.Listen, reaction.command)
        assertEquals(null, turn.shown().command)
    }

    @Test
    fun `конец фразы Проверки открывает слушание, пишет Слушает и оставляет инфинитив`() {
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
                nav = listOf(Control.Home, Control.Verbs),
                kicker = "Слушает",
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
        assertEquals("Говорит", speaking.kicker)
        assertEquals("go", speaking.headline)
        assertEquals(null, speaking.forms)
        assertEquals(null, speaking.recognised)
        assertEquals(listOf("Ещё раз"), speaking.controls.map { it.label })
        assertEquals(same, duringSpeech.command)

        val spokenAgain = turn.on(Input.PhraseEnded)
        assertEquals(Command.Listen, spokenAgain.command)
        assertEquals("Слушает", (spokenAgain.show as Show.Check).kicker)
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
    fun `Учим не начинает фразу без русской озвучки и остаётся без вердикта`() {
        val turn = turnOf(draw = PredeterminedDraw(listOf(go)))
        turn.on(Input.ModelDidNotOpen(Model.RussianSpeech))

        val reaction = turn.on(Input.EnterLearn)
        val learn = reaction.show as Show.Learn

        assertEquals(
            Show.Learn(
                kicker = "go",
                headline = "Модель не открылась",
                forms = null,
                recognised = null,
                controls = listOf(Control.Again),
                nav = listOf(Control.Home, Control.Verbs),
                reason = "Модель не открылась",
            ),
            learn,
        )
        assertEquals(listOf("Ещё раз"), learn.controls.map { it.label })
        assertEquals(null, reaction.command)
        assertEquals(reaction.show, turn.shown().show)
        assertEquals(null, turn.shown().command)

        val next = turn.on(Input.Next)
        assertEquals("go", (next.show as Show.Learn).kicker)
        assertEquals("Модель не открылась", (next.show as Show.Learn).reason)
        assertEquals(null, next.command)

        val ended = turn.on(Input.PhraseEnded)
        assertEquals("Модель не открылась", (ended.show as Show.Learn).reason)
        assertEquals(null, ended.command)
    }

    @Test
    fun `Проверка начинается без русской озвучки и не начинается без английской`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val russian = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(eat)),
        )
        russian.on(Input.ModelDidNotOpen(Model.RussianSpeech))

        val check = russian.on(Input.EnterCheck)
        assertEquals("eat", (check.show as Show.Check).headline)
        assertEquals(null, (check.show as Show.Check).reason)
        assertEquals(null, (check.show as Show.Check).forms)
        assertEquals(Command.Speak(infinitivePhrase("eat")), check.command)

        val learn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(eat)),
        )
        learn.on(Input.ModelDidNotOpen(Model.EnglishSpeech))
        val blockedLearn = learn.on(Input.EnterLearn)
        assertEquals("eat", (blockedLearn.show as Show.Learn).kicker)
        assertEquals("Модель не открылась", (blockedLearn.show as Show.Learn).reason)
        assertEquals(null, blockedLearn.command)

        val recognition = turnOf(draw = PredeterminedDraw(listOf(go)))
        recognition.on(Input.ModelDidNotOpen(Model.EnglishRecognition))
        val blockedCheck = recognition.on(Input.EnterCheck)
        val shown = blockedCheck.show as Show.Check
        assertEquals("go", shown.headline)
        assertEquals("Модель не открылась", shown.reason)
        assertEquals(listOf("Ещё раз"), shown.controls.map { it.label })
        assertEquals(null, shown.forms)
        assertEquals(null, blockedCheck.command)
    }

    @Test
    fun `без разрешения микрофона фраза не начинается и вердикта нет`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.PermissionMissing)

        val learned = learn.on(Input.EnterLearn)
        val shown = learned.show as Show.Learn
        assertEquals(
            Show.Learn(
                kicker = "go",
                headline = "Нет разрешения микрофона",
                forms = null,
                recognised = null,
                controls = listOf(Control.Again),
                nav = listOf(Control.Home, Control.Verbs),
                reason = "Нет разрешения микрофона",
            ),
            shown,
        )
        assertEquals(null, learned.command)
        assertEquals(shown, learn.shown().show)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.PermissionMissing)
        val checked = check.on(Input.EnterCheck)
        val checkShow = checked.show as Show.Check
        assertEquals("go", checkShow.headline)
        assertEquals("Нет разрешения микрофона", checkShow.reason)
        assertEquals(listOf("Ещё раз"), checkShow.controls.map { it.label })
        assertEquals(null, checkShow.forms)
        assertEquals(null, checked.command)
    }

    @Test
    fun `закрытая модель важнее отсутствующего разрешения`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.PermissionMissing)
        learn.on(Input.ModelDidNotOpen(Model.EnglishRecognition))

        val learned = learn.on(Input.EnterLearn)
        assertEquals("go", (learned.show as Show.Learn).kicker)
        assertEquals("Модель не открылась", (learned.show as Show.Learn).reason)
        assertEquals(listOf("Ещё раз"), (learned.show as Show.Learn).controls.map { it.label })
        assertEquals(null, learned.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.ModelDidNotOpen(Model.RussianSpeech))
        check.on(Input.PermissionMissing)
        val checked = check.on(Input.EnterCheck)
        assertEquals("go", (checked.show as Show.Check).headline)
        assertEquals("Нет разрешения микрофона", (checked.show as Show.Check).reason)
        assertEquals(null, checked.command)
    }

    @Test
    fun `уже прозвучавшая фраза сама не повторяется, если распознавание не стартовало`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.EnterLearn)
        assertEquals(Command.Listen, learn.on(Input.PhraseEnded).command)

        val blocked = learn.on(Input.RecognitionDidNotStart)
        val shown = blocked.show as Show.Learn
        assertEquals("go", shown.kicker)
        assertEquals("Распознавание не стартовало", shown.reason)
        assertEquals(listOf("Ещё раз"), shown.controls.map { it.label })
        assertEquals(null, shown.forms)
        assertEquals(null, shown.recognised)
        assertEquals(Command.Stop, blocked.command)

        val repeated = learn.on(Input.PhraseEnded)
        assertEquals("Распознавание не стартовало", (repeated.show as Show.Learn).reason)
        assertEquals(null, repeated.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        val checked = check.on(Input.RecognitionDidNotStart)
        val checkShow = checked.show as Show.Check
        assertEquals("go", checkShow.headline)
        assertEquals("Распознавание не стартовало", checkShow.reason)
        assertEquals(null, checkShow.forms)
        assertEquals(Command.Stop, checked.command)
    }

    @Test
    fun `Ещё раз просит разрешение, а если запрос больше не показывается — открывает настройки`() {
        val ask = turnOf(draw = PredeterminedDraw(listOf(go)))
        ask.on(Input.PermissionMissing)
        ask.on(Input.EnterLearn)

        val requested = ask.on(Input.Again)
        val asking = requested.show as Show.Learn
        assertEquals("go", asking.kicker)
        assertEquals("Нет разрешения микрофона", asking.reason)
        assertEquals(listOf("Ещё раз"), asking.controls.map { it.label })
        assertEquals(Command.RequestPermission, requested.command)

        val remembered = ask.on(Input.PermissionPromptWillNotShow)
        assertEquals("Нет разрешения микрофона", (remembered.show as Show.Learn).reason)
        assertEquals(null, remembered.command)

        val opened = ask.on(Input.Again)
        assertEquals("Нет разрешения микрофона", (opened.show as Show.Learn).reason)
        assertEquals(listOf("Ещё раз"), (opened.show as Show.Learn).controls.map { it.label })
        assertEquals(Command.OpenSettings, opened.command)

        val model = turnOf(draw = PredeterminedDraw(listOf(go)))
        model.on(Input.ModelDidNotOpen(Model.EnglishSpeech))
        model.on(Input.PermissionMissing)
        model.on(Input.EnterLearn)
        val stayed = model.on(Input.Again)
        assertEquals("Модель не открылась", (stayed.show as Show.Learn).reason)
        assertEquals(null, stayed.command)
    }

    @Test
    fun `возврат из настроек показывает текущую причину, а без помехи ждёт Ещё раз`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.PermissionMissing)
        learn.on(Input.PermissionPromptWillNotShow)
        learn.on(Input.EnterLearn)
        assertEquals(Command.OpenSettings, learn.on(Input.Again).command)

        val model = learn.on(
            Input.AbilitiesChecked(
                russianSpeech = false,
                englishSpeech = true,
                englishRecognition = true,
                microphone = true,
                permissionPrompt = true,
            ),
        )
        val modelShown = model.show as Show.Learn
        assertEquals("go", modelShown.kicker)
        assertEquals("Модель не открылась", modelShown.reason)
        assertEquals(listOf("Ещё раз"), modelShown.controls.map { it.label })
        assertEquals(null, model.command)

        val microphone = learn.on(
            Input.AbilitiesChecked(
                russianSpeech = true,
                englishSpeech = true,
                englishRecognition = true,
                microphone = false,
                permissionPrompt = false,
            ),
        )
        assertEquals("Нет разрешения микрофона", (microphone.show as Show.Learn).reason)
        assertEquals(null, microphone.command)

        val clear = learn.on(
            Input.AbilitiesChecked(
                russianSpeech = true,
                englishSpeech = true,
                englishRecognition = true,
                microphone = true,
                permissionPrompt = true,
            ),
        )
        val waiting = clear.show as Show.Learn
        assertEquals(null, waiting.reason)
        assertEquals("Ход ждёт", waiting.kicker)
        assertEquals("идти", waiting.headline)
        assertEquals(Forms("go", "went", "gone"), waiting.forms)
        assertEquals(null, waiting.recognised)
        assertEquals(listOf("Ещё раз"), waiting.controls.map { it.label })
        assertEquals(null, clear.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.PermissionMissing)
        check.on(Input.EnterCheck)
        val cleared = check.on(
            Input.AbilitiesChecked(
                russianSpeech = false,
                englishSpeech = true,
                englishRecognition = true,
                microphone = true,
                permissionPrompt = true,
            ),
        )
        val checked = cleared.show as Show.Check
        assertEquals("go", checked.headline)
        assertEquals(null, checked.kicker)
        assertEquals(null, checked.reason)
        assertEquals(null, checked.forms)
        assertEquals(listOf("Ещё раз"), checked.controls.map { it.label })
        assertEquals(null, cleared.command)
    }

    @Test
    fun `когда Ещё раз проходит, тот же глагол один раз звучит и затем слушается`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.PermissionMissing)
        learn.on(Input.EnterLearn)
        learn.on(
            Input.AbilitiesChecked(
                russianSpeech = true,
                englishSpeech = true,
                englishRecognition = true,
                microphone = true,
                permissionPrompt = true,
            ),
        )

        val again = learn.on(Input.Again)
        val speaking = again.show as Show.Learn
        assertEquals("Говорит", speaking.kicker)
        assertEquals("идти", speaking.headline)
        assertEquals(Forms("go", "went", "gone"), speaking.forms)
        assertEquals(null, speaking.reason)
        assertEquals(listOf("Ещё раз"), speaking.controls.map { it.label })
        assertEquals(Command.Speak(phrase("идти", Forms("go", "went", "gone"))), again.command)

        val listened = learn.on(Input.PhraseEnded)
        val hearing = listened.show as Show.Learn
        assertEquals("Слушает", hearing.kicker)
        assertEquals("идти", hearing.headline)
        assertEquals(null, hearing.forms)
        assertEquals(null, hearing.reason)
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), hearing.controls.map { it.label })
        assertEquals(Command.Listen, listened.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        check.on(Input.RecognitionDidNotStart)
        val repeated = check.on(Input.Again)
        val checked = repeated.show as Show.Check
        assertEquals("Говорит", checked.kicker)
        assertEquals("go", checked.headline)
        assertEquals(null, checked.forms)
        assertEquals(null, checked.reason)
        assertEquals(listOf("Ещё раз"), checked.controls.map { it.label })
        assertEquals(Command.Speak(infinitivePhrase("go")), repeated.command)

        val hearingCheck = check.on(Input.PhraseEnded)
        assertEquals("Слушает", (hearingCheck.show as Show.Check).kicker)
        assertEquals("go", (hearingCheck.show as Show.Check).headline)
        assertEquals(null, (hearingCheck.show as Show.Check).forms)
        assertEquals(Command.Listen, hearingCheck.command)
    }

    @Test
    fun `из помехи можно уйти на главную и на страницу глаголов, а К ходу не начинает попытку`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val learn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, eat)),
        )
        learn.on(Input.ModelDidNotOpen(Model.RussianSpeech))
        learn.on(Input.EnterLearn)

        val page = learn.on(Input.EnterVerbs)
        assertEquals(listOf("Главная", "К ходу"), (page.show as Show.Verbs).controls.map { it.label })
        assertEquals(null, page.command)

        val stray = learn.on(Input.ListeningEnded("go went gone"))
        assertEquals(listOf("Главная", "К ходу"), (stray.show as Show.Verbs).controls.map { it.label })
        assertEquals(Command.Stop, stray.command)

        val back = learn.on(Input.Back)
        val shown = back.show as Show.Learn
        assertEquals("go", shown.kicker)
        assertEquals("Модель не открылась", shown.reason)
        assertEquals(listOf("Ещё раз"), shown.controls.map { it.label })
        assertEquals(null, shown.forms)
        assertEquals(null, back.command)

        val home = learn.on(Input.Home)
        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), home.show)
        assertEquals(null, home.command)

        val again = learn.on(Input.EnterLearn)
        assertEquals("eat", (again.show as Show.Learn).kicker)
        assertEquals("Модель не открылась", (again.show as Show.Learn).reason)
        assertEquals(null, again.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.PermissionMissing)
        check.on(Input.EnterCheck)
        val left = check.on(Input.Home)
        assertEquals(Show.Home(strips = listOf("Учим", "Проверка", "Глаголы")), left.show)
        assertEquals(null, left.command)
        val verbs = check.on(Input.EnterVerbs)
        assertEquals(listOf("Главная"), (verbs.show as Show.Verbs).controls.map { it.label })
        assertEquals(null, verbs.command)
    }

    @Test
    fun `обрыв после старта слушания не показывает три причины и оставляет Готово`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.EnterLearn)
        learn.on(Input.PhraseEnded)

        val broke = learn.on(Input.ListeningBroke)
        val shown = broke.show as Show.Learn
        assertEquals("идти", shown.headline)
        assertEquals("Слушает", shown.kicker)
        assertEquals(null, shown.forms)
        assertEquals(null, shown.reason)
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), shown.controls.map { it.label })
        assertEquals(null, broke.command)

        val model = learn.on(Input.ModelDidNotOpen(Model.EnglishRecognition))
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), (model.show as Show.Learn).controls.map { it.label })
        assertEquals(null, (model.show as Show.Learn).reason)
        assertEquals(null, model.command)

        val permission = learn.on(Input.PermissionMissing)
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), (permission.show as Show.Learn).controls.map { it.label })
        assertEquals(null, (permission.show as Show.Learn).reason)

        val ready = learn.on(Input.Ready)
        assertEquals("Не совпало", (ready.show as Show.Learn).headline)
        assertEquals("", (ready.show as Show.Learn).recognised)
        assertEquals(listOf("Ещё раз", "Дальше"), (ready.show as Show.Learn).controls.map { it.label })
        assertEquals(Command.Stop, ready.command)

        val check = turnOf(draw = PredeterminedDraw(listOf(go)))
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        val broken = check.on(Input.ListeningBroke)
        val checked = broken.show as Show.Check
        assertEquals("go", checked.headline)
        assertEquals("Слушает", checked.kicker)
        assertEquals(null, checked.reason)
        assertEquals(null, checked.forms)
        assertEquals(listOf("Готово", "Ещё раз", "Дальше"), checked.controls.map { it.label })
        assertEquals(null, broken.command)

        val again = check.on(Input.Again)
        assertEquals("go", (again.show as Show.Check).headline)
        assertEquals(null, (again.show as Show.Check).reason)
        assertEquals(listOf("Ещё раз"), (again.show as Show.Check).controls.map { it.label })
        assertEquals(Command.Speak(infinitivePhrase("go")), again.command)
    }

    @Test
    fun `Дальше не начинает фразу, пока режим не может и говорить, и слушать`() {
        val eat = Verb("есть", Forms("eat", "ate", "eaten"))
        val learn = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, eat)),
        )
        learn.on(Input.EnterLearn)
        learn.on(Input.PhraseEnded)
        learn.on(Input.PermissionMissing)

        val next = learn.on(Input.Next)
        val shown = next.show as Show.Learn
        assertEquals("eat", shown.kicker)
        assertEquals("Нет разрешения микрофона", shown.reason)
        assertEquals(listOf("Ещё раз"), shown.controls.map { it.label })
        assertEquals(Command.Stop, next.command)

        val check = turnOf(
            set = listOf(go, eat),
            list = listOf(go, eat),
            draw = PredeterminedDraw(listOf(go, eat)),
        )
        check.on(Input.EnterCheck)
        check.on(Input.PhraseEnded)
        check.on(Input.ListeningEnded("мимо"))
        check.on(Input.ModelDidNotOpen(Model.EnglishSpeech))
        val blocked = check.on(Input.Next)
        val checked = blocked.show as Show.Check
        assertEquals("eat", checked.headline)
        assertEquals("Модель не открылась", checked.reason)
        assertEquals(listOf("Ещё раз"), checked.controls.map { it.label })
        assertEquals(null, blocked.command)
    }

    @Test
    fun `выданное разрешение снимает только эту причину и не начинает фразу`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        learn.on(Input.PermissionMissing)
        learn.on(Input.PermissionPromptWillNotShow)
        learn.on(Input.EnterLearn)

        val granted = learn.on(Input.PermissionGranted)
        val waiting = granted.show as Show.Learn
        assertEquals(null, waiting.reason)
        assertEquals("Ход ждёт", waiting.kicker)
        assertEquals("идти", waiting.headline)
        assertEquals(Forms("go", "went", "gone"), waiting.forms)
        assertEquals(listOf("Ещё раз"), waiting.controls.map { it.label })
        assertEquals(null, granted.command)

        val model = turnOf(draw = PredeterminedDraw(listOf(go)))
        model.on(Input.ModelDidNotOpen(Model.EnglishSpeech))
        model.on(Input.PermissionMissing)
        model.on(Input.EnterCheck)
        val still = model.on(Input.PermissionGranted)
        val checked = still.show as Show.Check
        assertEquals("go", checked.headline)
        assertEquals("Модель не открылась", checked.reason)
        assertEquals(listOf("Ещё раз"), checked.controls.map { it.label })
        assertEquals(null, still.command)
    }

    @Test
    fun `если модель закрылась, пока фраза звучит, слушание не открывается`() {
        val learn = turnOf(draw = PredeterminedDraw(listOf(go)))
        assertEquals(
            Command.Speak(phrase("идти", Forms("go", "went", "gone"))),
            learn.on(Input.EnterLearn).command,
        )

        val blocked = learn.on(Input.ModelDidNotOpen(Model.RussianSpeech))
        assertEquals("go", (blocked.show as Show.Learn).kicker)
        assertEquals("Модель не открылась", (blocked.show as Show.Learn).reason)
        assertEquals(Command.Stop, blocked.command)

        val ended = learn.on(Input.PhraseEnded)
        assertEquals("Модель не открылась", (ended.show as Show.Learn).reason)
        assertEquals(null, ended.command)
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
