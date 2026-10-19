package by.bnd.iverbs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import by.bnd.iverbs.turn.Turn
import by.bnd.iverbs.turn.beginnerVerbs
import by.bnd.iverbs.turn.uniformDraw

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = SavedList(this)
        val list = saved.read(beginnerVerbs) ?: beginnerVerbs
        val turn = Turn(
            set = beginnerVerbs,
            list = list,
            draw = uniformDraw(),
        )
        val speech = SpeechPort()
        setContent {
            IverbsScreen(turn = turn, speech = speech, onList = saved::write)
        }
    }
}
