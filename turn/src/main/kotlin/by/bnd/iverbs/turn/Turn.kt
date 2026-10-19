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
}

sealed interface Show {
    data class Home(val strips: List<String>) : Show

    data class Learn(
        val kicker: String?,
        val headline: String,
        val forms: Forms?,
        val recognised: String?,
        val controls: List<Control>,
    ) : Show
}

sealed interface Input {
    data object EnterLearn : Input
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
    private val list: List<Verb>,
    private val draw: Draw,
) {
    private var phase: Phase = Phase.Home

    init {
        require(list.all { verb -> set.contains(verb) })
    }

    fun shown(): Reaction = Reaction(show = phase.toShow(), command = null)

    fun on(input: Input): Reaction {
        val command = when (input) {
            Input.EnterLearn -> enterLearn()
            Input.PhraseEnded -> phraseEnded()
            is Input.ListeningEnded -> listeningEnded(input.text)
            Input.Ready -> ready()
            Input.Again -> again()
            Input.Next -> next()
        }
        return Reaction(show = phase.toShow(), command = command)
    }

    private fun enterLearn(): Command? {
        if (phase !is Phase.Home) return null
        val drawn = draw.next(list)
        phase = Phase.Speaking(drawn)
        return Command.Speak(drawn.phrase())
    }

    private fun phraseEnded(): Command? {
        val speaking = phase as? Phase.Speaking ?: return null
        phase = Phase.Listening(speaking.verb)
        return Command.Listen
    }

    private fun listeningEnded(text: String): Command? {
        val listening = phase as? Phase.Listening ?: return null
        phase = Phase.Verdict(
            verb = listening.verb,
            recognised = text,
            matched = matches(text, listening.verb.forms),
        )
        return null
    }

    private fun ready(): Command? {
        val listening = phase as? Phase.Listening ?: return null
        phase = Phase.Verdict(
            verb = listening.verb,
            recognised = listening.recognised,
            matched = matches(listening.recognised, listening.verb.forms),
        )
        return Command.Stop
    }

    private fun again(): Command? {
        val current = phase.verbOrNull() ?: return null
        phase = Phase.Speaking(current)
        return Command.Speak(current.phrase())
    }

    private fun next(): Command? {
        if (phase !is Phase.Listening && phase !is Phase.Verdict) return null
        val drawn = draw.next(list)
        phase = Phase.Speaking(drawn)
        return Command.Speak(drawn.phrase())
    }
}

private sealed interface Phase {
    data object Home : Phase
    data class Speaking(val verb: Verb) : Phase
    data class Listening(val verb: Verb, val recognised: String = "") : Phase
    data class Verdict(val verb: Verb, val recognised: String, val matched: Boolean) : Phase
}

private fun Phase.verbOrNull(): Verb? = when (this) {
    Phase.Home -> null
    is Phase.Speaking -> verb
    is Phase.Listening -> verb
    is Phase.Verdict -> verb
}

private fun Phase.toShow(): Show = when (this) {
    Phase.Home -> Show.Home(strips = listOf("Учим"))
    is Phase.Speaking -> Show.Learn(
        kicker = "Говорит",
        headline = verb.meaning,
        forms = verb.forms,
        recognised = null,
        controls = listOf(Control.Again),
    )
    is Phase.Listening -> Show.Learn(
        kicker = null,
        headline = verb.meaning,
        forms = null,
        recognised = null,
        controls = listOf(Control.Ready, Control.Again, Control.Next),
    )
    is Phase.Verdict -> Show.Learn(
        kicker = null,
        headline = if (matched) "Совпало" else "Не совпало",
        forms = verb.forms,
        recognised = recognised,
        controls = listOf(Control.Again, Control.Next),
    )
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

private fun Verb.phrase(): Phrase {
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
