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

enum class Model { RussianSpeech, EnglishSpeech, EnglishRecognition }

sealed interface PhrasePart {
    data class Said(val text: String, val voice: Voice) : PhrasePart
    data object Pause : PhrasePart
}

data class Phrase(val parts: List<PhrasePart>)

sealed interface Command {
    data class Speak(val phrase: Phrase) : Command
    data object Listen : Command
    data object Stop : Command
    data object RequestPermission : Command
    data object OpenSettings : Command
}

enum class Control(val label: String) {
    Ready("Готово"),
    Again("Ещё раз"),
    Next("Дальше"),
    Home("Главная"),
    Verbs("Глаголы"),
    Back("К ходу"),
}

enum class Strip(val label: String) {
    Learn("Учим"),
    Check("Проверка"),
    Verbs("Глаголы"),
    ;

    fun input(): Input = when (this) {
        Learn -> Input.EnterLearn
        Check -> Input.EnterCheck
        Verbs -> Input.EnterVerbs
    }

    companion object {
        fun labeled(label: String): Strip = entries.first { it.label == label }
    }
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
        val nav: List<Control>,
        val reason: String? = null,
    ) : Show

    data class Check(
        val headline: String,
        val forms: Forms?,
        val recognised: String?,
        val controls: List<Control>,
        val nav: List<Control>,
        val reason: String? = null,
        val kicker: String? = null,
    ) : Show
}

sealed interface Input {
    data object EnterLearn : Input
    data object EnterCheck : Input
    data object EnterVerbs : Input
    data object Home : Input
    data object Back : Input
    data class Toggle(val verb: Verb) : Input
    data object PhraseEnded : Input
    data class ListeningEnded(val text: String) : Input
    data class ModelDidNotOpen(val model: Model) : Input
    data object PermissionMissing : Input
    data object PermissionGranted : Input
    data object PermissionPromptWillNotShow : Input
    data object RecognitionDidNotStart : Input
    data object ListeningBroke : Input
    data class AbilitiesChecked(
        val russianSpeech: Boolean,
        val englishSpeech: Boolean,
        val englishRecognition: Boolean,
        val microphone: Boolean,
        val permissionPrompt: Boolean,
    ) : Input
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
    private var abilities = Abilities()

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
            Input.Back -> back()
            is Input.Toggle -> toggle(input.verb)
            Input.PhraseEnded -> phraseEnded()
            is Input.ListeningEnded -> listeningEnded(input.text)
            is Input.ModelDidNotOpen -> modelDidNotOpen(input.model)
            Input.PermissionMissing -> permissionMissing()
            Input.PermissionGranted -> permissionGranted()
            Input.PermissionPromptWillNotShow -> permissionPromptWillNotShow()
            Input.RecognitionDidNotStart -> recognitionDidNotStart()
            Input.ListeningBroke -> listeningBroke()
            is Input.AbilitiesChecked -> abilitiesChecked(input)
            Input.Ready -> ready()
            Input.Again -> again()
            Input.Next -> next()
        }
        return Reaction(show = phase.toShow(set, list), command = command)
    }

    private fun enterVerbs(): Command? {
        val covered = when (val current = phase) {
            Phase.Home -> null
            is Phase.Verbs -> return null
            is Phase.Speaking -> Covered.Speaking(current.verb, current.mode, current.playing)
            is Phase.Listening -> Covered.Listening(current.verb, current.mode)
            is Phase.Verdict -> Covered.Verdict(
                verb = current.verb,
                mode = current.mode,
                recognised = current.recognised,
                matched = current.matched,
            )
            is Phase.Blocked -> Covered.Blocked(current.verb, current.mode, current.reason)
        }
        phase = Phase.Verbs(covered)
        val quiet = covered == null ||
            covered is Covered.Blocked ||
            (covered is Covered.Speaking && !covered.playing)
        return if (quiet) null else Command.Stop
    }

    private fun back(): Command? {
        val verbs = phase as? Phase.Verbs ?: return null
        when (val covered = verbs.covered) {
            null -> return null
            is Covered.Speaking -> phase = Phase.Speaking(covered.verb, covered.mode, playing = false)
            is Covered.Listening -> {
                phase = Phase.Listening(covered.verb, covered.mode, recognised = "")
                return Command.Listen
            }
            is Covered.Verdict -> phase = Phase.Verdict(
                verb = covered.verb,
                mode = covered.mode,
                recognised = covered.recognised,
                matched = covered.matched,
            )
            is Covered.Blocked -> phase = Phase.Blocked(covered.verb, covered.mode, covered.reason)
        }
        return null
    }

    private fun home(): Command? {
        if (phase is Phase.Home) return null
        val stop = when (val current = phase) {
            is Phase.Speaking -> current.playing
            is Phase.Listening -> true
            is Phase.Verbs -> when (val covered = current.covered) {
                is Covered.Speaking -> covered.playing
                is Covered.Listening -> true
                else -> false
            }
            else -> false
        }
        phase = Phase.Home
        return if (stop) Command.Stop else null
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
            phase = Phase.Verbs(covered = null)
            return null
        }
        val drawn = draw.next(list)
        return begin(drawn, mode, stopListening = false)
    }

    private fun modelDidNotOpen(model: Model): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        abilities = abilities.closed(model)
        if (phase is Phase.Listening || phase is Phase.Verdict) return null
        return holdBlocked()
    }

    private fun permissionMissing(): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        abilities = abilities.copy(microphone = false)
        if (phase is Phase.Listening || phase is Phase.Verdict) return null
        return holdBlocked()
    }

    private fun permissionGranted(): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        abilities = abilities.copy(microphone = true, permissionPrompt = true)
        if (phase is Phase.Listening || phase is Phase.Verdict) return null
        return recompute()
    }

    private fun permissionPromptWillNotShow(): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        abilities = abilities.copy(permissionPrompt = false)
        return null
    }

    private fun abilitiesChecked(input: Input.AbilitiesChecked): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        abilities = Abilities(
            russianSpeech = input.russianSpeech,
            englishSpeech = input.englishSpeech,
            englishRecognition = input.englishRecognition,
            microphone = input.microphone,
            permissionPrompt = input.permissionPrompt,
        )
        if (phase is Phase.Listening || phase is Phase.Verdict) return null
        return recompute()
    }

    private fun recompute(): Command? {
        val mode = phase.modeOrNull() ?: return null
        val verb = phase.verbOrNull() ?: return null
        if (phase !is Phase.Speaking && phase !is Phase.Blocked) return null
        val reason = blockReason(mode)
        val playing = (phase as? Phase.Speaking)?.playing == true
        if (reason == null) {
            if (playing) return null
            phase = Phase.Speaking(verb, mode, playing = false)
            return null
        }
        phase = Phase.Blocked(verb, mode, reason)
        return if (playing) Command.Stop else null
    }

    private fun recognitionDidNotStart(): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        val listening = phase as? Phase.Listening ?: return null
        val reason = blockReason(listening.mode) ?: Reason.Recognition
        phase = Phase.Blocked(listening.verb, listening.mode, reason)
        return Command.Stop
    }

    private fun listeningBroke(): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        return null
    }

    private fun holdBlocked(): Command? {
        val mode = phase.modeOrNull() ?: return null
        val verb = phase.verbOrNull() ?: return null
        val reason = blockReason(mode) ?: return null
        val stop = (phase as? Phase.Speaking)?.playing == true
        phase = Phase.Blocked(verb, mode, reason)
        return if (stop) Command.Stop else null
    }

    private fun blockReason(mode: Mode): Reason? {
        val modelOpen = when (mode) {
            Mode.Learn -> abilities.russianSpeech && abilities.englishSpeech && abilities.englishRecognition
            Mode.Check -> abilities.englishSpeech && abilities.englishRecognition
        }
        if (!modelOpen) return Reason.Model
        if (!abilities.microphone) return Reason.Microphone
        return null
    }

    private fun begin(verb: Verb, mode: Mode, stopListening: Boolean): Command? {
        val reason = blockReason(mode)
        if (reason != null) {
            phase = Phase.Blocked(verb, mode, reason)
            return if (stopListening) Command.Stop else null
        }
        phase = Phase.Speaking(verb, mode)
        return Command.Speak(verb.phrase(mode))
    }

    private fun phraseEnded(): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        val speaking = phase as? Phase.Speaking ?: return null
        if (!speaking.playing) return null
        phase = Phase.Listening(speaking.verb, speaking.mode)
        return Command.Listen
    }

    private fun listeningEnded(text: String): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
        val listening = phase as? Phase.Listening ?: return null
        phase = Phase.Verdict(
            verb = listening.verb,
            mode = listening.mode,
            recognised = text,
            matched = matches(text, listening.verb.forms),
        )
        return null
    }

    private fun stopIfCovered(): Command? {
        val verbs = phase as? Phase.Verbs ?: return null
        return if (verbs.covered == null) null else Command.Stop
    }

    private fun ready(): Command? {
        if (phase is Phase.Verbs) return stopIfCovered()
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
        val reason = blockReason(mode)
        if (reason != null) {
            phase = Phase.Blocked(current, mode, reason)
            return if (reason == Reason.Microphone && abilities.permissionPrompt) {
                Command.RequestPermission
            } else if (reason == Reason.Microphone) {
                Command.OpenSettings
            } else {
                null
            }
        }
        phase = Phase.Speaking(current, mode)
        return Command.Speak(current.phrase(mode))
    }

    private fun next(): Command? {
        val mode = phase.modeOrNull() ?: return null
        if (phase !is Phase.Listening && phase !is Phase.Verdict) return null
        if (list.isEmpty()) {
            val stop = if (phase is Phase.Listening) Command.Stop else null
            phase = Phase.Verbs(covered = null)
            return stop
        }
        val drawn = draw.next(list)
        return begin(drawn, mode, stopListening = phase is Phase.Listening)
    }
}

private enum class Mode { Learn, Check }

private sealed interface Covered {
    data class Speaking(val verb: Verb, val mode: Mode, val playing: Boolean) : Covered
    data class Listening(val verb: Verb, val mode: Mode) : Covered
    data class Verdict(
        val verb: Verb,
        val mode: Mode,
        val recognised: String,
        val matched: Boolean,
    ) : Covered
    data class Blocked(val verb: Verb, val mode: Mode, val reason: Reason) : Covered
}

private sealed interface Phase {
    data object Home : Phase
    data class Verbs(val covered: Covered?) : Phase
    data class Speaking(val verb: Verb, val mode: Mode, val playing: Boolean = true) : Phase
    data class Listening(val verb: Verb, val mode: Mode, val recognised: String = "") : Phase
    data class Verdict(
        val verb: Verb,
        val mode: Mode,
        val recognised: String,
        val matched: Boolean,
    ) : Phase
    data class Blocked(val verb: Verb, val mode: Mode, val reason: Reason) : Phase
}

private enum class Reason(val text: String) {
    Model("Модель не открылась"),
    Microphone("Нет разрешения микрофона"),
    Recognition("Распознавание не стартовало"),
}

private data class Abilities(
    val russianSpeech: Boolean = true,
    val englishSpeech: Boolean = true,
    val englishRecognition: Boolean = true,
    val microphone: Boolean = true,
    val permissionPrompt: Boolean = true,
)

private fun Abilities.closed(model: Model): Abilities = when (model) {
    Model.RussianSpeech -> copy(russianSpeech = false)
    Model.EnglishSpeech -> copy(englishSpeech = false)
    Model.EnglishRecognition -> copy(englishRecognition = false)
}

private fun Phase.modeOrNull(): Mode? = when (this) {
    Phase.Home, is Phase.Verbs -> null
    is Phase.Speaking -> mode
    is Phase.Listening -> mode
    is Phase.Verdict -> mode
    is Phase.Blocked -> mode
}

private fun Phase.verbOrNull(): Verb? = when (this) {
    Phase.Home, is Phase.Verbs -> null
    is Phase.Speaking -> verb
    is Phase.Listening -> verb
    is Phase.Verdict -> verb
    is Phase.Blocked -> verb
}

private val turnNav = listOf(Control.Home, Control.Verbs)

private fun Phase.toShow(set: List<Verb>, list: List<Verb>): Show = when (this) {
    Phase.Home -> Show.Home(strips = Strip.entries.map { it.label })
    is Phase.Verbs -> Show.Verbs(
        rows = set.map { verb -> VerbRow(verb = verb, marked = verb in list) },
        controls = if (covered == null) {
            listOf(Control.Home)
        } else {
            listOf(Control.Home, Control.Back)
        },
    )
    is Phase.Speaking -> when (mode) {
        Mode.Learn -> Show.Learn(
            kicker = if (playing) "Говорит" else "Ход ждёт",
            headline = verb.meaning,
            forms = verb.forms,
            recognised = null,
            controls = listOf(Control.Again),
            nav = turnNav,
        )
        Mode.Check -> Show.Check(
            headline = verb.forms.infinitive,
            forms = null,
            recognised = null,
            controls = listOf(Control.Again),
            nav = turnNav,
            kicker = if (playing) "Говорит" else null,
        )
    }
    is Phase.Listening -> when (mode) {
        Mode.Learn -> Show.Learn(
            kicker = "Слушает",
            headline = verb.meaning,
            forms = null,
            recognised = null,
            controls = listOf(Control.Ready, Control.Again, Control.Next),
            nav = turnNav,
        )
        Mode.Check -> Show.Check(
            headline = verb.forms.infinitive,
            forms = null,
            recognised = null,
            controls = listOf(Control.Ready, Control.Again, Control.Next),
            nav = turnNav,
            kicker = "Слушает",
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
                nav = turnNav,
            )
            Mode.Check -> Show.Check(
                headline = headline,
                forms = verb.forms,
                recognised = recognised,
                controls = controls,
                nav = turnNav,
            )
        }
    }
    is Phase.Blocked -> when (mode) {
        Mode.Learn -> Show.Learn(
            kicker = verb.forms.infinitive,
            headline = reason.text,
            forms = null,
            recognised = null,
            controls = listOf(Control.Again),
            nav = turnNav,
            reason = reason.text,
        )
        Mode.Check -> Show.Check(
            headline = verb.forms.infinitive,
            forms = null,
            recognised = null,
            controls = listOf(Control.Again),
            nav = turnNav,
            reason = reason.text,
        )
    }
}

private fun matches(text: String, forms: Forms): Boolean {
    val stripped = text.replace(Regex("\\p{P}+"), " ")
    val collapsed = stripped.trim().replace(Regex("\\s+"), " ")
    if (collapsed.isEmpty()) return false
    val words = collapsed.split(" ")
    val expected = listOf(forms.infinitive, forms.pastSimple, forms.pastParticiple)
    return words.size == 3 && words.zip(expected).all { (heard, written) ->
        sameSpokenWord(heard, written)
    }
}

/** Созвучие, которое не является другой формой набора. Текст на экране не подменяется. */
private val soundAlikes: Map<String, Set<String>> = mapOf(
    "ate" to setOf("eight"),
    "been" to setOf("bin", "bean"),
    "won" to setOf("one"),
    "heard" to setOf("herd"),
    "knew" to setOf("new"),
    "know" to setOf("no"),
    "sent" to setOf("cent", "scent"),
    "seen" to setOf("scene"),
    "meet" to setOf("meat"),
    "break" to setOf("brake"),
    "write" to setOf("right", "rite"),
    "wear" to setOf("where"),
    "worn" to setOf("warn"),
    "buy" to setOf("by", "bye"),
    "saw" to setOf("sore", "soar"),
)

private fun sameSpokenWord(heard: String, written: String): Boolean {
    if (heard.equals(written, ignoreCase = true)) return true
    val alikes = soundAlikes[written.lowercase()] ?: return false
    return heard.lowercase() in alikes
}

private fun Verb.phrase(mode: Mode): Phrase = when (mode) {
    Mode.Learn -> learnPhrase()
    Mode.Check -> Phrase(parts = listOf(PhrasePart.Said(forms.infinitive, Voice.English)))
}

private fun Verb.learnPhrase(): Phrase = Phrase(
    parts = listOf(
        PhrasePart.Said(meaning, Voice.Russian),
        PhrasePart.Pause,
        PhrasePart.Said(englishForms(), Voice.English),
    ),
)

/** Три формы одной фразой: у модели есть интонация, а не три отдельных слога. */
private fun Verb.englishForms(): String =
    "${forms.infinitive}, ${forms.pastSimple}, ${forms.pastParticiple}"
