package by.bnd.iverbs

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import by.bnd.iverbs.turn.Command
import by.bnd.iverbs.turn.Input
import by.bnd.iverbs.turn.ListedVoice
import by.bnd.iverbs.turn.Model
import by.bnd.iverbs.turn.Phrase
import by.bnd.iverbs.turn.PhrasePart
import by.bnd.iverbs.turn.Voice as PhraseVoice
import by.bnd.iverbs.turn.chooseVoice
import by.bnd.iverbs.turn.englishRecognitionTag
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class SpeechAbilities(
    val russianSpeech: Boolean,
    val englishSpeech: Boolean,
    val englishRecognition: Boolean,
)

sealed interface SpeechPrep {
    data class Working(val message: String) : SpeechPrep
    data class NeedsInstaller(val message: String, val intent: Intent) : SpeechPrep
    data object Ready : SpeechPrep
    data class Failed(val message: String) : SpeechPrep
}

/**
 * Озвучка и распознавание службами телефона.
 * При запуске докачивает русский голос, английский голос и английскую модель,
 * если их ещё нет. Ход говорит через TextToSpeech и слушает через SpeechRecognizer.
 */
class SpeechPort(context: Context) {
    private val app = context.applicationContext
    var onOutcome: (Input) -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private val epoch = AtomicInteger(0)

    private var tts: TextToSpeech? = null
    private var ttsUp = false
    private var startingTts = false
    private var russianVoice: Voice? = null
    private var englishVoice: Voice? = null
    private var russianLocale: Locale? = null
    private var englishLocale: Locale? = null

    private var recognizer: SpeechRecognizer? = null
    private var recognizerOnDevice = false
    private var onDevice = false
    private var recognitionLanguage = "en-US"

    private var prepGeneration = 0
    private var offerInstaller = false
    private var installerOffered = false
    private var onUpdate: ((SpeechPrep) -> Unit)? = null
    private var prepFinished = false
    private var failed = false
    private var voiceStageDone = false
    private var hearingStageDone = false
    private var voiceMessage = "Проверяем речь"
    private var awaitingInstaller = false

    @Volatile private var stopped = true
    @Volatile private var closed = false
    @Volatile private var finishRequested = false

    private var onUtteranceDone: ((String) -> Unit)? = null
    private var onUtteranceError: ((String) -> Unit)? = null

    fun perform(command: Command?) {
        if (closed) return
        main.post {
            if (closed) return@post
            when (command) {
                null, Command.RequestPermission, Command.OpenSettings -> Unit
                is Command.Speak -> speak(command.phrase)
                Command.Listen -> listen()
                Command.Stop -> stop()
            }
        }
    }

    /** «Готово»: закрыть сессию распознавания и отдать уже сказанное. */
    fun finishUtterance() {
        if (closed) return
        main.post {
            if (closed) return@post
            finishRequested = true
            try {
                recognizer?.stopListening()
            } catch (error: Throwable) {
                Log.e(TAG, "Слушание не остановилось", error)
            }
        }
    }

    /** Заново проверить, открыты ли русская речь, английская речь и английское распознавание. */
    fun probe(onResult: (SpeechAbilities) -> Unit) {
        if (closed) return
        main.post {
            if (closed) return@post
            refreshStoredVoices()
            onResult(
                SpeechAbilities(
                    russianSpeech = hasRussian(),
                    englishSpeech = hasEnglish(),
                    englishRecognition = hasRecognition(),
                ),
            )
        }
    }

    /**
     * Проверить речь и скачать недостающее.
     * [offerInstaller] открывает экран установки голоса, если служба сама не качает.
     */
    fun prepare(offerInstaller: Boolean, onUpdate: (SpeechPrep) -> Unit) {
        if (closed) return
        val ticket = ++prepGeneration
        this.offerInstaller = offerInstaller
        this.onUpdate = onUpdate
        installerOffered = false
        prepFinished = false
        failed = false
        voiceStageDone = false
        hearingStageDone = false
        voiceMessage = "Проверяем речь"
        awaitingInstaller = false
        main.post {
            if (ticket != prepGeneration || closed) return@post
            if (!offerInstaller && ttsUp) {
                try {
                    tts?.shutdown()
                } catch (_: Throwable) {
                }
                tts = null
                ttsUp = false
                startingTts = false
            }
            publish(SpeechPrep.Working(voiceMessage))
            startRecognition(ticket)
            startTts(ticket)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        prepGeneration++
        epoch.incrementAndGet()
        onUtteranceDone = null
        onUtteranceError = null
        main.removeCallbacksAndMessages(null)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        tts = null
        ttsUp = false
        recognizer = null
    }

    private fun startTts(ticket: Int) {
        if (ttsUp && tts != null) {
            tts?.setOnUtteranceProgressListener(progress)
            ensureVoices(ticket, tts ?: return)
            return
        }
        if (startingTts) return
        try {
            tts?.shutdown()
        } catch (_: Throwable) {
        }
        tts = null
        ttsUp = false
        startingTts = true
        val holder = arrayOfNulls<TextToSpeech>(1)
        holder[0] = TextToSpeech(app) { status ->
            main.post {
                val engine = holder[0] ?: return@post
                startingTts = false
                val current = prepGeneration
                if (closed) return@post
                if (status != TextToSpeech.SUCCESS) {
                    Log.e(TAG, "Озвучка не открылась: $status")
                    fail("На телефоне нет озвучки")
                    return@post
                }
                ttsUp = true
                engine.setOnUtteranceProgressListener(progress)
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                ensureVoices(current, engine)
            }
        }
        tts = holder[0]
    }

    private fun ensureVoices(ticket: Int, engine: TextToSpeech) {
        if (ticket != prepGeneration || closed || failed) return
        val listed = try {
            engine.voices.orEmpty().map { it to it.listed() }
        } catch (error: Throwable) {
            Log.e(TAG, "Список голосов не открылся", error)
            emptyList()
        }
        val russianChoice = chooseVoice(listed.map { it.second }, "ru", "RU", preferFemale = false)
        val englishChoice = chooseVoice(listed.map { it.second }, "en", "GB", preferFemale = true)
        if (russianChoice != null && englishChoice != null) {
            val russianEngine = listed.first { it.second.name == russianChoice.name }.first
            val englishEngine = listed.first { it.second.name == englishChoice.name }.first
            if (russianChoice.installed && englishChoice.installed) {
                rememberVoices(russianEngine, englishEngine)
                markVoicesReady(ticket)
                return
            }
            voiceMessage = "Скачиваем голос"
            publish(SpeechPrep.Working(voiceMessage))
            for (voice in listOf(russianEngine, englishEngine)) {
                if (voice.notInstalled()) engine.setVoice(voice)
            }
            pollVoices(
                ticket = ticket,
                engine = engine,
                russianName = russianChoice.name,
                englishName = englishChoice.name,
                started = SystemClock.elapsedRealtime(),
                lastNudge = SystemClock.elapsedRealtime(),
            )
            return
        }
        val russianState = languageState(engine, RUSSIAN_LOCALES)
        val englishState = languageState(engine, ENGLISH_LOCALES)
        if (russianState >= TextToSpeech.LANG_AVAILABLE && englishState >= TextToSpeech.LANG_AVAILABLE) {
            russianVoice = null
            englishVoice = null
            russianLocale = firstAvailable(engine, RUSSIAN_LOCALES)
            englishLocale = firstAvailable(engine, ENGLISH_LOCALES)
            markVoicesReady(ticket)
            return
        }
        if (russianState == TextToSpeech.LANG_MISSING_DATA || englishState == TextToSpeech.LANG_MISSING_DATA) {
            voiceMessage = "Скачиваем голос"
            publish(SpeechPrep.Working(voiceMessage))
            maybeOfferInstaller(engine)
            pollLanguage(ticket, engine, SystemClock.elapsedRealtime())
            return
        }
        fail("На телефоне нет нужного голоса")
    }

    private fun pollVoices(
        ticket: Int,
        engine: TextToSpeech,
        russianName: String,
        englishName: String,
        started: Long,
        lastNudge: Long,
    ) {
        main.postDelayed({
            if (ticket != prepGeneration || closed || failed) return@postDelayed
            val voices = try {
                engine.voices.orEmpty()
            } catch (_: Throwable) {
                emptySet()
            }
            val russian = voices.find { it.name == russianName }
            val english = voices.find { it.name == englishName }
            if (
                russian != null && english != null &&
                !russian.notInstalled() && !english.notInstalled()
            ) {
                rememberVoices(russian, english)
                markVoicesReady(ticket)
                return@postDelayed
            }
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - started
            if (elapsed >= INSTALLER_AFTER_MS) maybeOfferInstaller(engine)
            if (elapsed >= timeoutMs()) {
                val fallbackRussian = voices.firstOrNull { it.matchesLanguage("ru") && !it.notInstalled() }
                val fallbackEnglish = voices.firstOrNull { it.matchesLanguage("en") && !it.notInstalled() }
                if (fallbackRussian != null && fallbackEnglish != null) {
                    rememberVoices(fallbackRussian, fallbackEnglish)
                    markVoicesReady(ticket)
                } else {
                    fail("Голос не скачался")
                }
                return@postDelayed
            }
            var nudged = lastNudge
            if (now - lastNudge >= NUDGE_MS) {
                if (russian != null && russian.notInstalled()) engine.setVoice(russian)
                if (english != null && english.notInstalled()) engine.setVoice(english)
                nudged = now
            }
            pollVoices(ticket, engine, russianName, englishName, started, nudged)
        }, POLL_MS)
    }

    private fun pollLanguage(ticket: Int, engine: TextToSpeech, started: Long) {
        main.postDelayed({
            if (ticket != prepGeneration || closed || failed) return@postDelayed
            val russianState = languageState(engine, RUSSIAN_LOCALES)
            val englishState = languageState(engine, ENGLISH_LOCALES)
            if (russianState >= TextToSpeech.LANG_AVAILABLE && englishState >= TextToSpeech.LANG_AVAILABLE) {
                russianVoice = null
                englishVoice = null
                russianLocale = firstAvailable(engine, RUSSIAN_LOCALES)
                englishLocale = firstAvailable(engine, ENGLISH_LOCALES)
                markVoicesReady(ticket)
                return@postDelayed
            }
            if (SystemClock.elapsedRealtime() - started >= timeoutMs()) {
                fail("Голос не скачался")
                return@postDelayed
            }
            maybeOfferInstaller(engine)
            pollLanguage(ticket, engine, started)
        }, POLL_MS)
    }

    private fun startRecognition(ticket: Int) {
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            fail("На телефоне нет распознавания")
            return
        }
        val onDeviceAvailable = try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        } catch (_: Throwable) {
            false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && onDeviceAvailable) {
            val created = createRecognizer(onDevice = true)
            if (created == null) {
                useOnlineRecognizer(ticket)
            } else {
                checkEnglishSupport(ticket, created)
            }
            return
        }
        if (onDeviceAvailable && createRecognizer(onDevice = true) != null) {
            onDevice = true
            recognitionLanguage = "en-US"
            markHearingReady(ticket)
            return
        }
        useOnlineRecognizer(ticket)
    }

    private fun checkEnglishSupport(ticket: Int, hearing: SpeechRecognizer) {
        try {
            hearing.checkRecognitionSupport(
                recognizeIntent("en-US", preferOffline = true),
                app.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        if (ticket != prepGeneration || closed || failed) return
                        val installed = englishRecognitionTag(support.installedOnDeviceLanguages)
                        if (installed != null) {
                            recognitionLanguage = installed
                            onDevice = true
                            markHearingReady(ticket)
                            return
                        }
                        val download = englishRecognitionTag(support.supportedOnDeviceLanguages)
                            ?: englishRecognitionTag(support.pendingOnDeviceLanguages)
                        if (download != null) {
                            beginModelDownload(ticket, hearing, download)
                            return
                        }
                        useOnlineRecognizer(ticket)
                    }

                    override fun onError(error: Int) {
                        if (ticket != prepGeneration || closed || failed) return
                        Log.e(TAG, "Поддержка распознавания не проверена: $error")
                        useOnlineRecognizer(ticket)
                    }
                },
            )
        } catch (error: Throwable) {
            Log.e(TAG, "Проверка распознавания не стартовала", error)
            useOnlineRecognizer(ticket)
        }
    }

    private fun beginModelDownload(ticket: Int, hearing: SpeechRecognizer, tag: String) {
        recognitionLanguage = tag
        onDevice = true
        if (voiceStageDone) publish(SpeechPrep.Working("Скачиваем распознавание"))
        val intent = recognizeIntent(tag, preferOffline = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            downloadWithListener(ticket, hearing, intent, tag)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            useOnlineRecognizer(ticket)
            return
        }
        try {
            hearing.triggerModelDownload(intent)
        } catch (error: Throwable) {
            Log.e(TAG, "Скачивание распознавания не стартовало", error)
            useOnlineRecognizer(ticket)
            return
        }
        pollRecognition(ticket, tag, SystemClock.elapsedRealtime())
    }

    private fun downloadWithListener(
        ticket: Int,
        hearing: SpeechRecognizer,
        intent: Intent,
        tag: String,
    ) {
        try {
            hearing.triggerModelDownload(
                intent,
                app.mainExecutor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) {
                        if (ticket != prepGeneration || closed || failed || !voiceStageDone) return
                        publish(SpeechPrep.Working("Скачиваем распознавание"))
                    }

                    override fun onScheduled() {
                        if (ticket != prepGeneration || closed || failed || !voiceStageDone) return
                        publish(SpeechPrep.Working("Скачиваем распознавание"))
                    }

                    override fun onSuccess() {
                        if (ticket != prepGeneration || closed || failed) return
                        recognitionLanguage = tag
                        onDevice = true
                        markHearingReady(ticket)
                    }

                    override fun onError(error: Int) {
                        if (ticket != prepGeneration || closed || failed) return
                        Log.e(TAG, "Распознавание не скачалось: $error")
                        useOnlineRecognizer(ticket)
                    }
                },
            )
        } catch (error: Throwable) {
            Log.e(TAG, "Скачивание распознавания не стартовало", error)
            useOnlineRecognizer(ticket)
        }
    }

    private fun pollRecognition(ticket: Int, tag: String, started: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            useOnlineRecognizer(ticket)
            return
        }
        val hearing = recognizer
        if (hearing == null) {
            useOnlineRecognizer(ticket)
            return
        }
        try {
            hearing.checkRecognitionSupport(
                recognizeIntent(tag, preferOffline = true),
                app.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        if (ticket != prepGeneration || closed || failed) return
                        val installed = englishRecognitionTag(support.installedOnDeviceLanguages)
                        if (installed != null) {
                            recognitionLanguage = installed
                            onDevice = true
                            markHearingReady(ticket)
                            return
                        }
                        if (SystemClock.elapsedRealtime() - started >= timeoutMs()) {
                            useOnlineRecognizer(ticket)
                            return
                        }
                        main.postDelayed({ pollRecognition(ticket, tag, started) }, POLL_MS)
                    }

                    override fun onError(error: Int) {
                        if (ticket != prepGeneration || closed || failed) return
                        useOnlineRecognizer(ticket)
                    }
                },
            )
        } catch (error: Throwable) {
            Log.e(TAG, "Распознавание не проверилось", error)
            useOnlineRecognizer(ticket)
        }
    }

    private fun useOnlineRecognizer(ticket: Int) {
        if (ticket != prepGeneration || closed || failed) return
        val created = createRecognizer(onDevice = false)
        if (created == null) {
            fail("На телефоне нет распознавания")
            return
        }
        onDevice = false
        recognitionLanguage = "en-US"
        markHearingReady(ticket)
    }

    private fun createRecognizer(onDevice: Boolean, fresh: Boolean = false): SpeechRecognizer? {
        if (!fresh && recognizer != null && recognizerOnDevice == onDevice) return recognizer
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
        return try {
            val created = if (onDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
            } else {
                SpeechRecognizer.createSpeechRecognizer(app)
            }
            recognizer = created
            recognizerOnDevice = onDevice
            created
        } catch (error: Throwable) {
            Log.e(TAG, "Распознавание не открылось", error)
            null
        }
    }

    private fun markVoicesReady(ticket: Int) {
        if (ticket != prepGeneration || closed || failed) return
        voiceStageDone = true
        publishProgress(ticket)
    }

    private fun markHearingReady(ticket: Int) {
        if (ticket != prepGeneration || closed || failed) return
        hearingStageDone = true
        publishProgress(ticket)
    }

    private fun publishProgress(ticket: Int) {
        if (ticket != prepGeneration || closed || failed || prepFinished) return
        if (voiceStageDone && hearingStageDone) {
            prepFinished = true
            publish(SpeechPrep.Ready)
            return
        }
        val message = if (!voiceStageDone) voiceMessage else "Скачиваем распознавание"
        publish(SpeechPrep.Working(message))
    }

    private fun maybeOfferInstaller(engine: TextToSpeech) {
        if (!offerInstaller || installerOffered || prepFinished || failed) return
        val intent = installerIntent(engine) ?: return
        installerOffered = true
        awaitingInstaller = true
        publish(SpeechPrep.NeedsInstaller("Скачиваем голос", intent))
    }

    private fun installerIntent(engine: TextToSpeech): Intent? {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        val packageName = try {
            engine.defaultEngine
        } catch (_: Throwable) {
            null
        }
        if (!packageName.isNullOrBlank()) intent.setPackage(packageName)
        if (intent.resolveActivity(app.packageManager) != null) return intent
        intent.setPackage(null)
        return if (intent.resolveActivity(app.packageManager) != null) intent else null
    }

    private fun fail(message: String) {
        if (closed || failed || prepFinished) return
        failed = true
        prepFinished = true
        publish(SpeechPrep.Failed(message))
    }

    private fun publish(state: SpeechPrep) {
        if (closed) return
        if (prepFinished && state !is SpeechPrep.Ready) return
        if (state is SpeechPrep.Ready || state is SpeechPrep.Failed) awaitingInstaller = false
        if (awaitingInstaller && state is SpeechPrep.Working) return
        val callback = onUpdate ?: return
        val ticket = prepGeneration
        main.post {
            if (ticket != prepGeneration || closed) return@post
            callback(state)
        }
    }

    private fun timeoutMs(): Long = if (offerInstaller) DOWNLOAD_TIMEOUT_MS else RETURN_TIMEOUT_MS

    private fun rememberVoices(russian: Voice, english: Voice) {
        russianVoice = russian
        englishVoice = english
        russianLocale = russian.locale
        englishLocale = english.locale
    }

    private fun refreshStoredVoices() {
        val engine = tts
        if (!ttsUp || engine == null) return
        val voices = try {
            engine.voices.orEmpty()
        } catch (_: Throwable) {
            return
        }
        russianVoice?.name?.let { name -> voices.find { it.name == name }?.let { russianVoice = it } }
        englishVoice?.name?.let { name -> voices.find { it.name == name }?.let { englishVoice = it } }
    }

    private fun speak(phrase: Phrase) {
        val ticket = epoch.incrementAndGet()
        stopped = false
        finishRequested = false
        onUtteranceDone = null
        onUtteranceError = null
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }
        if (!prepared(phrase, ticket)) return
        playParts(phrase.parts, 0, ticket)
    }

    private fun prepared(phrase: Phrase, ticket: Int): Boolean {
        val needsRussian = phrase.parts.any { it is PhrasePart.Said && it.voice == PhraseVoice.Russian }
        val needsEnglish = phrase.parts.any { it is PhrasePart.Said && it.voice == PhraseVoice.English }
        if (needsRussian && !hasRussian()) {
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.RussianSpeech))
            return false
        }
        if (needsEnglish && !hasEnglish()) {
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.EnglishSpeech))
            return false
        }
        if (!hasRecognition()) {
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.EnglishRecognition))
            return false
        }
        return current(ticket)
    }

    private fun playParts(parts: List<PhrasePart>, index: Int, ticket: Int) {
        if (!current(ticket)) return
        if (index >= parts.size) {
            report(Input.PhraseEnded)
            return
        }
        when (val part = parts[index]) {
            PhrasePart.Pause -> pause(ticket) { playParts(parts, index + 1, ticket) }
            is PhrasePart.Said -> say(part, ticket) { playParts(parts, index + 1, ticket) }
        }
    }

    private fun say(part: PhrasePart.Said, ticket: Int, then: () -> Unit) {
        if (!current(ticket)) return
        if (part.text.isBlank()) {
            then()
            return
        }
        val engine = tts
        val model = when (part.voice) {
            PhraseVoice.Russian -> Model.RussianSpeech
            PhraseVoice.English -> Model.EnglishSpeech
        }
        if (engine == null || !applyVoice(engine, part.voice)) {
            if (current(ticket)) report(Input.ModelDidNotOpen(model))
            return
        }
        val id = "$ticket-${part.voice.name}-${part.text.hashCode()}"
        onUtteranceDone = { doneId ->
            if (doneId == id && current(ticket)) then()
        }
        onUtteranceError = { errorId ->
            if (errorId == id && current(ticket)) report(Input.ModelDidNotOpen(model))
        }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
        }
        val spoken = try {
            engine.speak(part.text, TextToSpeech.QUEUE_FLUSH, params, id)
        } catch (error: Throwable) {
            Log.e(TAG, "Часть фразы не началась", error)
            TextToSpeech.ERROR
        }
        if (spoken < TextToSpeech.SUCCESS && current(ticket)) {
            onUtteranceDone = null
            onUtteranceError = null
            report(Input.ModelDidNotOpen(model))
        }
    }

    private fun pause(ticket: Int, then: () -> Unit) {
        val engine = tts
        if (engine == null) {
            main.postDelayed({ if (current(ticket)) then() }, PAUSE_MS)
            return
        }
        val id = "$ticket-pause"
        onUtteranceDone = { doneId ->
            if (doneId == id && current(ticket)) then()
        }
        onUtteranceError = { errorId ->
            if (errorId == id && current(ticket)) then()
        }
        val played = try {
            engine.playSilentUtterance(PAUSE_MS, TextToSpeech.QUEUE_FLUSH, id)
        } catch (_: Throwable) {
            TextToSpeech.ERROR
        }
        if (played < TextToSpeech.SUCCESS) {
            onUtteranceDone = null
            onUtteranceError = null
            main.postDelayed({ if (current(ticket)) then() }, PAUSE_MS)
        }
    }

    private fun applyVoice(engine: TextToSpeech, voice: PhraseVoice): Boolean {
        val chosen = when (voice) {
            PhraseVoice.Russian -> russianVoice
            PhraseVoice.English -> englishVoice
        }
        val locale = when (voice) {
            PhraseVoice.Russian -> russianLocale
            PhraseVoice.English -> englishLocale
        }
        val set = when {
            chosen != null -> engine.setVoice(chosen)
            locale != null -> engine.setLanguage(locale)
            else -> TextToSpeech.ERROR
        }
        return set >= TextToSpeech.SUCCESS
    }

    private fun listen() {
        val ticket = epoch.incrementAndGet()
        stopped = false
        finishRequested = false
        onUtteranceDone = null
        onUtteranceError = null
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
        if (!hasRecognition()) {
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.EnglishRecognition))
            return
        }
        val hearing = createRecognizer(recognizerOnDevice, fresh = true)
        if (hearing == null) {
            if (current(ticket)) report(Input.RecognitionDidNotStart)
            return
        }
        hearing.setRecognitionListener(ListenSession(ticket))
        try {
            hearing.startListening(recognizeIntent(recognitionLanguage, preferOffline = onDevice))
        } catch (error: Throwable) {
            Log.e(TAG, "Распознавание не стартовало", error)
            if (current(ticket)) report(Input.RecognitionDidNotStart)
        }
    }

    private fun recognizeIntent(language: String, preferOffline: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }

    private fun stop() {
        epoch.incrementAndGet()
        stopped = true
        finishRequested = false
        onUtteranceDone = null
        onUtteranceError = null
        try {
            tts?.stop()
        } catch (_: Throwable) {
        }
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }
    }

    private fun hasRussian(): Boolean {
        val voice = russianVoice
        if (voice != null) return !voice.notInstalled()
        return russianLocale != null
    }

    private fun hasEnglish(): Boolean {
        val voice = englishVoice
        if (voice != null) return !voice.notInstalled()
        return englishLocale != null
    }

    private fun hasRecognition(): Boolean = hearingStageDone && recognizer != null

    private fun current(ticket: Int) = epoch.get() == ticket && !stopped && !closed

    private fun report(input: Input) {
        if (closed) return
        main.post {
            if (!closed) onOutcome(input)
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            val id = utteranceId ?: return
            main.post { onUtteranceDone?.invoke(id) }
        }

        override fun onError(utteranceId: String?) {
            val id = utteranceId ?: return
            main.post { onUtteranceError?.invoke(id) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e(TAG, "Озвучка не прошла: $errorCode")
            onError(utteranceId)
        }
    }

    private inner class ListenSession(val ticket: Int) : RecognitionListener {
        private var started = false
        private var partial = ""
        private var reported = false

        override fun onReadyForSpeech(params: Bundle?) {
            started = true
        }

        override fun onBeginningOfSpeech() {
            started = true
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            deliver(errorResult(error))
        }

        override fun onResults(results: Bundle?) {
            val text = results.bestText().ifBlank { partial }
            deliver(Input.ListeningEnded(text))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.bestText()
            if (text.isNotBlank()) partial = text
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun deliver(input: Input) {
            if (reported || !current(ticket)) return
            reported = true
            finishRequested = false
            report(input)
        }

        private fun errorResult(error: Int): Input {
            val text = partial.trim()
            return when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> Input.ListeningEnded(if (finishRequested) text else "")
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                -> Input.ModelDidNotOpen(Model.EnglishRecognition)
                SpeechRecognizer.ERROR_CLIENT,
                -> when {
                    finishRequested -> Input.ListeningEnded(text)
                    started -> Input.ListeningBroke
                    else -> Input.RecognitionDidNotStart
                }
                else -> if (started) Input.ListeningBroke else Input.RecognitionDidNotStart
            }
        }
    }

    private companion object {
        const val TAG = "iverbs"
        const val POLL_MS = 1_000L
        const val INSTALLER_AFTER_MS = 2_000L
        const val DOWNLOAD_TIMEOUT_MS = 180_000L
        const val RETURN_TIMEOUT_MS = 20_000L
        const val NUDGE_MS = 15_000L
        const val PAUSE_MS = 400L
        val RUSSIAN_LOCALES: List<Locale> = listOf(
            Locale.forLanguageTag("ru-RU"),
            Locale.forLanguageTag("ru"),
        )
        val ENGLISH_LOCALES: List<Locale> = listOf(Locale.UK, Locale.US, Locale.ENGLISH)
    }
}

private fun Voice.listed(): ListedVoice = ListedVoice(
    name = name,
    language = locale?.language.orEmpty(),
    country = locale?.country.orEmpty(),
    installed = !notInstalled(),
    needsNetwork = isNetworkConnectionRequired,
    quality = quality,
    female = name.contains("female", ignoreCase = true),
)

private fun Voice.notInstalled(): Boolean =
    features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

private fun Voice.matchesLanguage(language: String): Boolean =
    locale?.language.equals(language, ignoreCase = true)

private fun languageState(engine: TextToSpeech, locales: List<Locale>): Int {
    var best = TextToSpeech.LANG_NOT_SUPPORTED
    for (locale in locales) {
        val state = engine.isLanguageAvailable(locale)
        if (state >= TextToSpeech.LANG_AVAILABLE) return state
        if (state == TextToSpeech.LANG_MISSING_DATA) best = state
    }
    return best
}

private fun firstAvailable(engine: TextToSpeech, locales: List<Locale>): Locale? =
    locales.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }

private fun Bundle?.bestText(): String =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }.orEmpty()
