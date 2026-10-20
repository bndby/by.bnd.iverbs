package by.bnd.iverbs

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import by.bnd.iverbs.turn.Command
import by.bnd.iverbs.turn.Control
import by.bnd.iverbs.turn.Forms
import by.bnd.iverbs.turn.Input
import by.bnd.iverbs.turn.Show
import by.bnd.iverbs.turn.Strip
import by.bnd.iverbs.turn.Turn
import by.bnd.iverbs.turn.Verb
import by.bnd.iverbs.turn.VerbRow
import java.util.Locale

private val Paper = Color(0xFFF3EFE6)
private val Ink = Color(0xFF1C1915)
private const val Microphone = Manifest.permission.RECORD_AUDIO
private const val ModelClosed = "Модель не открылась"
private const val SpeakingNow = "Говорит"
private const val ListeningNow = "Слушает"

@Composable
fun IverbsScreen(turn: Turn, speech: SpeechPort, onList: (List<Verb>) -> Unit) {
    val context = LocalContext.current
    val report = remember { mutableStateOf<(Input) -> Unit>({}) }
    val recheck = remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        reportPermission(report.value, context, granted)
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        recheck.value(false)
    }
    var reaction by remember {
        val missing = context.checkSelfPermission(Microphone) != PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (missing) turn.on(Input.PermissionMissing) else turn.shown())
    }
    val act: (Input) -> Unit = { input ->
        val next = turn.on(input)
        reaction = next
        when (val command = next.command) {
            Command.RequestPermission -> permissionLauncher.launch(Microphone)
            Command.OpenSettings -> settingsLauncher.launch(settingsIntent(context))
            else -> speech.perform(command)
        }
        val verbs = next.show as? Show.Verbs
        if (verbs != null) {
            onList(verbs.rows.filter { it.marked }.map { it.verb })
        }
        if (input == Input.Again && next.command == null && next.show.modelClosed()) {
            recheck.value(true)
        }
    }
    val press: (Control) -> Unit = { control ->
        when (control) {
            Control.Ready -> speech.finishUtterance()
            Control.Again -> if (reaction.show.modelClosed()) recheck.value(true) else act(Input.Again)
            else -> act(control.input())
        }
    }
    SideEffect {
        report.value = act
        speech.onOutcome = act
        recheck.value = { thenAgain ->
            speech.probe { opened ->
                val granted = context.checkSelfPermission(Microphone) == PackageManager.PERMISSION_GRANTED
                act(
                    Input.AbilitiesChecked(
                        russianSpeech = opened.russianSpeech,
                        englishSpeech = opened.englishSpeech,
                        englishRecognition = opened.englishRecognition,
                        microphone = granted,
                        permissionPrompt = promptWillShow(context, granted),
                    ),
                )
                if (thenAgain && reaction.show.blockCleared()) act(Input.Again)
            }
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
                    act(Strip.labeled(label).input())
                }
                is Show.Verbs -> VerbsColumn(
                    show,
                    onControl = press,
                    onToggle = { verb -> act(Input.Toggle(verb)) },
                )
                is Show.Learn -> LearnColumn(show, press)
                is Show.Check -> CheckColumn(show, press)
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
private fun ModeColumn(
    nav: List<Control>,
    controls: List<Control>,
    onControl: (Control) -> Unit,
    body: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(nav, onControl)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        ) {
            body()
        }
        Controls(controls, onControl)
    }
}

@Composable
private fun AnswerTail(forms: Forms?, recognised: String?) {
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

@Composable
private fun LearnColumn(show: Show.Learn, onControl: (Control) -> Unit) {
    ModeColumn(show.nav, show.controls, onControl) {
        val reason = show.reason
        if (reason != null) {
            BlockedText(infinitive = show.kicker.orEmpty(), reason = reason)
        } else {
            val kicker = show.kicker
            if (kicker != null) {
                StatusLine(kicker)
            }
            Text(
                text = show.headline,
                fontFamily = FontFamily.Serif,
                fontSize = 40.sp,
                lineHeight = 1.05.em,
                color = Ink,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            AnswerTail(show.forms, show.recognised)
        }
    }
}

@Composable
private fun CheckColumn(show: Show.Check, onControl: (Control) -> Unit) {
    ModeColumn(show.nav, show.controls, onControl) {
        val reason = show.reason
        if (reason != null) {
            BlockedText(infinitive = show.headline, reason = reason)
        } else {
            val kicker = show.kicker
            if (kicker != null) {
                StatusLine(kicker)
            }
            val beforeVerdict = show.forms == null && show.recognised == null
            Text(
                text = show.headline,
                fontFamily = FontFamily.Serif,
                fontSize = if (beforeVerdict) 36.sp else 40.sp,
                lineHeight = 1.05.em,
                color = Ink,
                modifier = Modifier.padding(bottom = if (beforeVerdict) 0.dp else 16.dp),
            )
            AnswerTail(show.forms, show.recognised)
        }
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

@Composable
private fun StatusLine(text: String) {
    val live = text == SpeakingNow || text == ListeningNow
    if (!live) {
        Text(
            text = text,
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            letterSpacing = 0.08.em,
            color = Ink,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ActivityMark(listening = text == ListeningNow)
        Text(
            text = text,
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            letterSpacing = 0.06.em,
            color = Ink,
        )
    }
}

@Composable
private fun ActivityMark(listening: Boolean) {
    val transition = rememberInfiniteTransition(label = "ход")
    val level by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "уровень",
    )
    Canvas(Modifier.size(14.dp)) {
        val stroke = 1.25.dp.toPx()
        if (listening) {
            drawRect(color = Ink, style = Stroke(width = stroke))
            val inset = size.minDimension * (0.5f - 0.28f * level)
            drawRect(
                color = Ink,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
            )
        } else {
            drawRect(color = Ink.copy(alpha = level))
        }
    }
}

@Composable
private fun BlockedText(infinitive: String, reason: String) {
    Text(
        text = infinitive.uppercase(Locale.ROOT),
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        letterSpacing = 0.08.em,
        color = Ink,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Text(
        text = reason,
        fontFamily = FontFamily.Serif,
        fontSize = 34.sp,
        lineHeight = 1.1.em,
        color = Ink,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

private fun Show.modelClosed(): Boolean = when (this) {
    is Show.Learn -> reason == ModelClosed
    is Show.Check -> reason == ModelClosed
    else -> false
}

private fun Show.blockCleared(): Boolean = when (this) {
    is Show.Learn -> reason == null
    is Show.Check -> reason == null
    else -> false
}

private fun promptWillShow(context: Context, granted: Boolean): Boolean {
    if (granted) return true
    val activity = context.findActivity() ?: return true
    return activity.shouldShowRequestPermissionRationale(Microphone)
}

private fun reportPermission(report: (Input) -> Unit, context: Context, granted: Boolean) {
    if (granted) {
        report(Input.PermissionGranted)
        return
    }
    val showAgain = context.findActivity()?.shouldShowRequestPermissionRationale(Microphone) ?: true
    if (!showAgain) report(Input.PermissionPromptWillNotShow)
    report(Input.PermissionMissing)
}

private fun settingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
