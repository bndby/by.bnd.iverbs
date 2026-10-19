package by.bnd.iverbs

import by.bnd.iverbs.turn.Command

/** Заглушка речи: не говорит, не слушает и не ходит в сеть. Разрешение и настройки выполняет экран. */
class SpeechPort {
    fun perform(command: Command?) {
        when (command) {
            null -> Unit
            is Command.Speak -> Unit
            Command.Listen -> Unit
            Command.Stop -> Unit
            Command.RequestPermission -> Unit
            Command.OpenSettings -> Unit
        }
    }
}
