package by.bnd.iverbs

import by.bnd.iverbs.turn.Command

/** Заглушка: не говорит, не слушает, не ходит в сеть и не придумывает текст. */
class SpeechPort {
    fun perform(command: Command?) {
        when (command) {
            null -> Unit
            is Command.Speak -> Unit
            Command.Listen -> Unit
            Command.Stop -> Unit
        }
    }
}
