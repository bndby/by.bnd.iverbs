package by.bnd.iverbs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import by.bnd.iverbs.turn.Control
import by.bnd.iverbs.turn.Input
import by.bnd.iverbs.turn.Show
import by.bnd.iverbs.turn.Turn
import by.bnd.iverbs.turn.Verb
import by.bnd.iverbs.turn.VerbRow

private val Paper = Color(0xFFF3EFE6)
private val Ink = Color(0xFF1C1915)

@Composable
fun IverbsScreen(turn: Turn, speech: SpeechPort, onList: (List<Verb>) -> Unit) {
    var reaction by remember { mutableStateOf(turn.shown()) }
    val act: (Input) -> Unit = { input ->
        val next = turn.on(input)
        reaction = next
        speech.perform(next.command)
        val verbs = next.show as? Show.Verbs
        if (verbs != null) {
            onList(verbs.rows.filter { it.marked }.map { it.verb })
        }
    }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Paper,
            background = Paper,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
            outline = Ink,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            color = Paper,
            contentColor = Ink,
        ) {
            when (val show = reaction.show) {
                is Show.Home -> HomeColumn(show) { label ->
                    when (label) {
                        "Учим" -> act(Input.EnterLearn)
                        "Проверка" -> act(Input.EnterCheck)
                        "Глаголы" -> act(Input.EnterVerbs)
                    }
                }
                is Show.Verbs -> VerbsColumn(
                    show,
                    onControl = { control -> act(control.input()) },
                    onToggle = { verb -> act(Input.Toggle(verb)) },
                )
                is Show.Learn -> LearnColumn(show) { control -> act(control.input()) }
                is Show.Check -> CheckColumn(show) { control -> act(control.input()) }
            }
        }
    }
}

@Composable
private fun HomeColumn(show: Show.Home, onStrip: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        show.strips.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (index == show.strips.lastIndex) {
                            Modifier
                        } else {
                            Modifier.drawBehind {
                                val stroke = 1.dp.toPx()
                                drawLine(
                                    color = Ink,
                                    start = Offset(0f, size.height - stroke / 2f),
                                    end = Offset(size.width, size.height - stroke / 2f),
                                    strokeWidth = stroke,
                                )
                            }
                        },
                    )
                    .clickable(onClick = { onStrip(label) })
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = label,
                    fontFamily = FontFamily.Serif,
                    fontSize = 48.sp,
                    color = Ink,
                )
            }
        }
    }
}

@Composable
private fun LearnColumn(show: Show.Learn, onControl: (Control) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(show.nav, onControl)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        ) {
            val kicker = show.kicker
            if (kicker != null) {
                Text(
                    text = kicker,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    letterSpacing = 0.08.em,
                    color = Ink,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Text(
                text = show.headline,
                fontFamily = FontFamily.Serif,
                fontSize = 40.sp,
                lineHeight = 1.05.em,
                color = Ink,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            val forms = show.forms
            if (forms != null) {
                Column(Modifier.padding(top = 12.dp)) {
                    listOf(forms.infinitive, forms.pastSimple, forms.pastParticiple).forEach { form ->
                        Text(
                            text = form,
                            fontFamily = FontFamily.Serif,
                            fontSize = 32.sp,
                            lineHeight = 1.15.em,
                            color = Ink,
                        )
                    }
                }
            }
            val recognised = show.recognised
            if (recognised != null) {
                Text(
                    text = recognised,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 18.sp,
                    color = Ink,
                    minLines = 1,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
        Controls(show.controls, onControl)
    }
}

@Composable
private fun CheckColumn(show: Show.Check, onControl: (Control) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(show.nav, onControl)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        ) {
            val beforeVerdict = show.forms == null && show.recognised == null
            Text(
                text = show.headline,
                fontFamily = FontFamily.Serif,
                fontSize = if (beforeVerdict) 36.sp else 40.sp,
                lineHeight = 1.05.em,
                color = Ink,
                modifier = Modifier.padding(bottom = if (beforeVerdict) 0.dp else 16.dp),
            )
            val forms = show.forms
            if (forms != null) {
                Column(Modifier.padding(top = 12.dp)) {
                    listOf(forms.infinitive, forms.pastSimple, forms.pastParticiple).forEach { form ->
                        Text(
                            text = form,
                            fontFamily = FontFamily.Serif,
                            fontSize = 32.sp,
                            lineHeight = 1.15.em,
                            color = Ink,
                        )
                    }
                }
            }
            val recognised = show.recognised
            if (recognised != null) {
                Text(
                    text = recognised,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 18.sp,
                    color = Ink,
                    minLines = 1,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
        Controls(show.controls, onControl)
    }
}

@Composable
private fun TopBar(controls: List<Control>, onControl: (Control) -> Unit) {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        controls.forEach { control ->
            OutlinedButton(
                onClick = { onControl(control) },
                shape = RectangleShape,
                contentPadding = PaddingValues(12.dp),
                border = BorderStroke(1.dp, Ink),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Ink,
                ),
            ) {
                Text(text = control.label, fontFamily = FontFamily.Serif, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Controls(controls: List<Control>, onControl: (Control) -> Unit) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        controls.forEach { control ->
            LearnButton(control, onClick = { onControl(control) })
        }
    }
}

@Composable
private fun VerbsColumn(
    show: Show.Verbs,
    onControl: (Control) -> Unit,
    onToggle: (Verb) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(show.controls, onControl)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        ) {
            show.rows.forEach { row ->
                VerbLine(row, onToggle)
            }
        }
    }
}

@Composable
private fun VerbLine(row: VerbRow, onToggle: (Verb) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = Ink,
                    start = Offset(0f, size.height - stroke / 2f),
                    end = Offset(size.width, size.height - stroke / 2f),
                    strokeWidth = stroke,
                )
            }
            .clickable { onToggle(row.verb) }
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(1.dp, Ink)
                .background(if (row.marked) Ink else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (row.marked) {
                Text(
                    text = "✓",
                    color = Paper,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                )
            }
        }
        Column {
            Text(
                text = row.verb.meaning,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                color = Ink,
            )
            Text(
                text = row.verb.forms.infinitive,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                color = Ink,
            )
        }
    }
}

private fun Control.input(): Input = when (this) {
    Control.Ready -> Input.Ready
    Control.Again -> Input.Again
    Control.Next -> Input.Next
    Control.Home -> Input.Home
    Control.Verbs -> Input.EnterVerbs
    Control.Back -> Input.Back
}

@Composable
private fun LearnButton(control: Control, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth()
    val padding = PaddingValues(12.dp)
    if (control == Control.Ready) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RectangleShape,
            contentPadding = padding,
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
            ),
            colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
        ) {
            Text(text = control.label, fontFamily = FontFamily.Serif, fontSize = 22.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RectangleShape,
            contentPadding = padding,
            border = BorderStroke(1.dp, Ink),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = Ink,
            ),
        ) {
            Text(text = control.label, fontFamily = FontFamily.Serif, fontSize = 16.sp)
        }
    }
}
