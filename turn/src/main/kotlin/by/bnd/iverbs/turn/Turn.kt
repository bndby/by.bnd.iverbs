package by.bnd.iverbs.turn

data class Forms(
    val infinitive: String,
    val pastSimple: String,
    val pastParticiple: String,
)

data class Verb(
    val meaning: String,
    val forms: Forms,
)

fun interface Draw {
    fun next(list: List<Verb>): Verb
}

fun uniformDraw(random: kotlin.random.Random = kotlin.random.Random.Default): Draw =
    Draw { verbs -> verbs[random.nextInt(verbs.size)] }

enum class Voice { Russian, English }

sealed interface PhrasePart {
    data class Said(val text: String, val voice: Voice) : PhrasePart
    data object Pause : PhrasePart
}

data class Phrase(val parts: List<PhrasePart>)

sealed interface Command {
    data class Speak(val phrase: Phrase) : Command
    data object Listen : Command
    data object Stop : Command
}

enum class Control(val label: String) {
    Ready("Готово"),
    Again("Ещё раз"),
    Next("Дальше"),
    Home("Главная"),
}

data class VerbRow(
    val verb: Verb,
    val marked: Boolean,
)

sealed interface Show {
    data class Home(val strips: List<String>) : Show

    data class Verbs(
        val rows: List<VerbRow>,
        val controls: List<Control>,
    ) : Show

    data class Learn(
        val kicker: String?,
        val headline: String,
        val forms: Forms?,
        val recognised: String?,
        val controls: List<Control>,
    ) : Show

    data class Check(
        val headline: String,
        val forms: Forms?,
        val recognised: String?,
        val controls: List<Control>,
    ) : Show
}

sealed interface Input {
    data object EnterLearn : Input
    data object EnterCheck : Input
    data object EnterVerbs : Input
    data object Home : Input
    data class Toggle(val verb: Verb) : Input
    data object PhraseEnded : Input
    data class ListeningEnded(val text: String) : Input
    data object Ready : Input
    data object Again : Input
    data object Next : Input
}

data class Reaction(
    val show: Show,
    val command: Command?,
)

class Turn(
    private val set: List<Verb>,
    private var list: List<Verb>,
    private val draw: Draw,
) {
    private var phase: Phase = Phase.Home

    init {
        require(list.all { verb -> set.contains(verb) })
    }

    fun shown(): Reaction = Reaction(show = phase.toShow(set, list), command = null)

    fun on(input: Input): Reaction {
        val command = when (input) {
            Input.EnterLearn -> enter(Mode.Learn)
            Input.EnterCheck -> enter(Mode.Check)
            Input.EnterVerbs -> enterVerbs()
            Input.Home -> home()
            is Input.Toggle -> toggle(input.verb)
            Input.PhraseEnded -> phraseEnded()
            is Input.ListeningEnded -> listeningEnded(input.text)
            Input.Ready -> ready()
            Input.Again -> again()
            Input.Next -> next()
        }
        return Reaction(show = phase.toShow(set, list), command = command)
    }

    private fun enterVerbs(): Command? {
        if (phase !is Phase.Home) return null
        phase = Phase.Verbs
        return null
    }

    private fun home(): Command? {
        if (phase !is Phase.Verbs) return null
        phase = Phase.Home
        return null
    }

    private fun toggle(verb: Verb): Command? {
        if (verb !in set) return null
        list = if (verb in list) {
            list.filter { it != verb }
        } else {
            set.filter { it == verb || it in list }
        }
        return null
    }

    private fun enter(mode: Mode): Command? {
        if (phase !is Phase.Home) return null
        if (list.isEmpty()) {
            phase = Phase.Verbs
            return null
        }
        val drawn = draw.next(list)
        phase = Phase.Speaking(drawn, mode)
        return Command.Speak(drawn.phrase(mode))
    }

    private fun phraseEnded(): Command? {
        val speaking = phase as? Phase.Speaking ?: return null
        phase = Phase.Listening(speaking.verb, speaking.mode)
        return Command.Listen
    }

    private fun listeningEnded(text: String): Command? {
        val listening = phase as? Phase.Listening ?: return null
        phase = Phase.Verdict(
            verb = listening.verb,
            mode = listening.mode,
            recognised = text,
            matched = matches(text, listening.verb.forms),
        )
        return null
    }

    private fun ready(): Command? {
        val listening = phase as? Phase.Listening ?: return null
        phase = Phase.Verdict(
            verb = listening.verb,
            mode = listening.mode,
            recognised = listening.recognised,
            matched = matches(listening.recognised, listening.verb.forms),
        )
        return Command.Stop
    }

    private fun again(): Command? {
        val mode = phase.modeOrNull() ?: return null
        val current = phase.verbOrNull() ?: return null
        phase = Phase.Speaking(current, mode)
        return Command.Speak(current.phrase(mode))
    }

    private fun next(): Command? {
        val mode = phase.modeOrNull() ?: return null
        if (phase !is Phase.Listening && phase !is Phase.Verdict) return null
        if (list.isEmpty()) {
            val stop = if (phase is Phase.Listening) Command.Stop else null
            phase = Phase.Verbs
            return stop
        }
        val drawn = draw.next(list)
        phase = Phase.Speaking(drawn, mode)
        return Command.Speak(drawn.phrase(mode))
    }
}

private enum class Mode { Learn, Check }

private sealed interface Phase {
    data object Home : Phase
    data object Verbs : Phase
    data class Speaking(val verb: Verb, val mode: Mode) : Phase
    data class Listening(val verb: Verb, val mode: Mode, val recognised: String = "") : Phase
    data class Verdict(
        val verb: Verb,
        val mode: Mode,
        val recognised: String,
        val matched: Boolean,
    ) : Phase
}

private fun Phase.modeOrNull(): Mode? = when (this) {
    Phase.Home, Phase.Verbs -> null
    is Phase.Speaking -> mode
    is Phase.Listening -> mode
    is Phase.Verdict -> mode
}

private fun Phase.verbOrNull(): Verb? = when (this) {
    Phase.Home, Phase.Verbs -> null
    is Phase.Speaking -> verb
    is Phase.Listening -> verb
    is Phase.Verdict -> verb
}

private fun Phase.toShow(set: List<Verb>, list: List<Verb>): Show = when (this) {
    Phase.Home -> Show.Home(strips = listOf("Учим", "Проверка", "Глаголы"))
    Phase.Verbs -> Show.Verbs(
        rows = set.map { verb -> VerbRow(verb = verb, marked = verb in list) },
        controls = listOf(Control.Home),
    )
    is Phase.Speaking -> when (mode) {
        Mode.Learn -> Show.Learn(
            kicker = "Говорит",
            headline = verb.meaning,
            forms = verb.forms,
            recognised = null,
            controls = listOf(Control.Again),
        )
        Mode.Check -> Show.Check(
            headline = verb.forms.infinitive,
            forms = null,
            recognised = null,
            controls = listOf(Control.Again),
        )
    }
    is Phase.Listening -> when (mode) {
        Mode.Learn -> Show.Learn(
            kicker = null,
            headline = verb.meaning,
            forms = null,
            recognised = null,
            controls = listOf(Control.Ready, Control.Again, Control.Next),
        )
        Mode.Check -> Show.Check(
            headline = verb.forms.infinitive,
            forms = null,
            recognised = null,
            controls = listOf(Control.Ready, Control.Again, Control.Next),
        )
    }
    is Phase.Verdict -> {
        val headline = if (matched) "Совпало" else "Не совпало"
        val controls = listOf(Control.Again, Control.Next)
        when (mode) {
            Mode.Learn -> Show.Learn(
                kicker = null,
                headline = headline,
                forms = verb.forms,
                recognised = recognised,
                controls = controls,
            )
            Mode.Check -> Show.Check(
                headline = headline,
                forms = verb.forms,
                recognised = recognised,
                controls = controls,
            )
        }
    }
}

private fun matches(text: String, forms: Forms): Boolean {
    val stripped = text.replace(Regex("\\p{P}+"), " ")
    val collapsed = stripped.trim().replace(Regex("\\s+"), " ")
    if (collapsed.isEmpty()) return false
    val words = collapsed.split(" ")
    val expected = listOf(forms.infinitive, forms.pastSimple, forms.pastParticiple)
    return words.size == 3 && words.zip(expected).all { (heard, written) ->
        heard.equals(written, ignoreCase = true)
    }
}

private fun Verb.phrase(mode: Mode): Phrase = when (mode) {
    Mode.Learn -> learnPhrase()
    Mode.Check -> Phrase(parts = listOf(PhrasePart.Said(forms.infinitive, Voice.English)))
}

private fun Verb.learnPhrase(): Phrase {
    val said = listOf(
        PhrasePart.Said(meaning, Voice.Russian),
        PhrasePart.Said(forms.infinitive, Voice.English),
        PhrasePart.Said(forms.pastSimple, Voice.English),
        PhrasePart.Said(forms.pastParticiple, Voice.English),
    )
    return Phrase(
        parts = said.flatMapIndexed { index, part ->
            if (index == 0) listOf(part) else listOf(PhrasePart.Pause, part)
        },
    )
}
