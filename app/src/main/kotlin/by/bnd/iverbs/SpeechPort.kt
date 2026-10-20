package by.bnd.iverbs

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import by.bnd.iverbs.turn.Command
import by.bnd.iverbs.turn.Input
import by.bnd.iverbs.turn.Model
import by.bnd.iverbs.turn.Phrase
import by.bnd.iverbs.turn.PhrasePart
import by.bnd.iverbs.turn.Voice
import by.bnd.iverbs.turn.matchedSpeech
import by.bnd.iverbs.turn.pcm16
import by.bnd.iverbs.turn.phraseStillPlaying
import by.bnd.iverbs.turn.staticClipAcceptsWrite
import by.bnd.iverbs.turn.staticClipBytes
import by.bnd.iverbs.turn.staticClipCanPlay
import by.bnd.iverbs.turn.utteranceSamples
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

data class SpeechAbilities(
    val russianSpeech: Boolean,
    val englishSpeech: Boolean,
    val englishRecognition: Boolean,
)

/**
 * Sherpa-ONNX на устройстве. Одна команда «говорить» — одна фраза:
 * части по порядку на уже открытом голосе, короткая пауза между ними.
 * Разрешение и настройки выполняет экран.
 */
class SpeechPort(context: android.content.Context) {
    private val app = context.applicationContext
    var onOutcome: (Input) -> Unit = {}

    private val main = Handler(android.os.Looper.getMainLooper())
    private val worker = HandlerThread("iverbs-speech").apply { start() }
    private val speech = Handler(worker.looper)
    private val epoch = AtomicInteger(0)

    private var russian: OfflineTts? = null
    private var english: OfflineTts? = null
    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private val audioLock = Any()
    private var track: AudioTrack? = null

    @Volatile private var stopped = true
    @Volatile private var closed = false
    @Volatile private var finishRequested = false
    @Volatile private var recorder: AudioRecord? = null

    fun perform(command: Command?) {
        if (closed) return
        when (command) {
            null, Command.RequestPermission, Command.OpenSettings -> Unit
            is Command.Speak -> begin { ticket -> speak(command.phrase, ticket) }
            Command.Listen -> begin { ticket -> listen(ticket) }
            Command.Stop -> {
                finishRequested = false
                haltAudio()
                epoch.incrementAndGet()
            }
        }
    }

    /** «Готово»: остановить запись. Слушание само распознает уже собранный звук. */
    fun finishUtterance() {
        if (closed) return
        finishRequested = true
        stopRecorder()
    }

    /** Заново открыть русскую речь, английскую речь и английское распознавание. */
    fun probe(onResult: (SpeechAbilities) -> Unit) {
        if (closed) return
        speech.post {
            if (closed) return@post
            val opened = SpeechAbilities(
                russianSpeech = openRussian() != null,
                englishSpeech = openEnglish() != null,
                englishRecognition = openHearing(),
            )
            main.post {
                if (!closed) onResult(opened)
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        haltAudio()
        epoch.incrementAndGet()
        speech.post {
            releaseEngines()
            worker.quitSafely()
        }
    }

    private fun begin(block: (Int) -> Unit) {
        finishRequested = false
        haltAudio()
        stopped = false
        val ticket = epoch.incrementAndGet()
        speech.post { block(ticket) }
    }

    private fun speak(phrase: Phrase, ticket: Int) {
        if (!prepare(phrase, ticket)) return
        try {
            if (playPhrase(phrase, ticket)) report(Input.PhraseEnded)
        } catch (error: Throwable) {
            Log.e(TAG, "Фраза оборвалась", error)
        }
    }

    private fun prepare(phrase: Phrase, ticket: Int): Boolean {
        val needsRussian = phrase.parts.any { it is PhrasePart.Said && it.voice == Voice.Russian }
        val needsEnglish = phrase.parts.any { it is PhrasePart.Said && it.voice == Voice.English }
        if (needsRussian && openRussian() == null) {
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.RussianSpeech))
            return false
        }
        if (needsEnglish && openEnglish() == null) {
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.EnglishSpeech))
            return false
        }
        if (!openHearing()) {
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.EnglishRecognition))
            return false
        }
        return current(ticket)
    }

    private fun playPhrase(phrase: Phrase, ticket: Int): Boolean {
        for (part in phrase.parts) {
            if (!current(ticket)) return false
            val played = when (part) {
                PhrasePart.Pause -> writeSilence(ticket)
                is PhrasePart.Said -> say(part, ticket)
            }
            if (!played) return false
        }
        return current(ticket)
    }

    private fun say(part: PhrasePart.Said, ticket: Int): Boolean {
        if (part.text.isBlank()) return true
        val tts = when (part.voice) {
            Voice.Russian -> russian
            Voice.English -> english
        } ?: return false
        val model = when (part.voice) {
            Voice.Russian -> Model.RussianSpeech
            Voice.English -> Model.EnglishSpeech
        }
        val audio = try {
            tts.generate(text = part.text, sid = 0, speed = 1.0f)
        } catch (error: Throwable) {
            Log.e(TAG, "Часть фразы не собралась", error)
            if (current(ticket)) report(Input.ModelDidNotOpen(model))
            return false
        }
        if (audio.samples.isEmpty()) {
            if (current(ticket)) report(Input.ModelDidNotOpen(model))
            return false
        }
        if (!current(ticket)) return false
        return write(matchedSpeech(audio.samples), audio.sampleRate, ticket)
    }

    private fun writeSilence(ticket: Int): Boolean {
        val silenceMs = (PAUSE_SECONDS * 1000L).toLong()
        val started = SystemClock.elapsedRealtime()
        while (current(ticket) && SystemClock.elapsedRealtime() - started < silenceMs) {
            Thread.sleep(20)
        }
        return current(ticket)
    }

    private fun write(samples: FloatArray, rate: Int, ticket: Int): Boolean {
        if (!current(ticket) || samples.isEmpty()) return current(ticket)
        val pcm = ShortArray(samples.size) { index -> pcm16(samples[index]) }
        val playing = staticTrack(rate, pcm.size)
        synchronized(audioLock) {
            if (!current(ticket)) {
                playing.release()
                return false
            }
            track = playing
        }
        try {
            val accepted = try {
                playing.write(pcm, 0, pcm.size)
            } catch (_: IllegalStateException) {
                0
            }
            if (accepted <= 0 || !staticClipCanPlay(playing.state)) return false
            try {
                playing.play()
            } catch (_: IllegalStateException) {
                return false
            }
            val clipFrames = accepted.toLong()
            val clipRate = rate.coerceAtLeast(1)
            val clipMs = clipFrames * 1000L / clipRate + DRAIN_PAD_MS
            val clipStarted = SystemClock.elapsedRealtime()
            val clipHead = playing.playbackHeadPosition.toLong()
            val deadline = clipStarted + clipMs
            while (current(ticket)) {
                val still = phraseStillPlaying(
                    headAtStart = clipHead,
                    headNow = playing.playbackHeadPosition.toLong(),
                    framesWritten = clipFrames,
                    elapsedMs = SystemClock.elapsedRealtime() - clipStarted,
                    durationMs = clipMs,
                )
                if (!still) break
                if (SystemClock.elapsedRealtime() >= deadline) break
                Thread.sleep(20)
            }
            return current(ticket)
        } finally {
            synchronized(audioLock) {
                try {
                    if (playing.playState == AudioTrack.PLAYSTATE_PLAYING) playing.pause()
                    playing.stop()
                } catch (_: IllegalStateException) {
                }
                playing.release()
                if (track === playing) track = null
            }
        }
    }

    private fun listen(ticket: Int) {
        if (!current(ticket)) return
        if (!openHearing()) {
            finishRequested = false
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.EnglishRecognition))
            return
        }
        val hearing = vad
        val reader = recognizer
        if (hearing == null || reader == null) {
            finishRequested = false
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.EnglishRecognition))
            return
        }
        hearing.reset()
        if (finishRequested) {
            if (current(ticket)) report(Input.ListeningEnded(""))
            finishRequested = false
            return
        }
        val mine = try {
            openRecorder()
        } catch (error: Throwable) {
            Log.e(TAG, "Микрофон не стартовал", error)
            null
        }
        if (mine == null) {
            finishRequested = false
            if (current(ticket)) report(Input.RecognitionDidNotStart)
            return
        }
        recorder = mine
        var started = false
        var broke = false
        val recorded = ArrayList<FloatArray>()
        try {
            mine.startRecording()
            started = mine.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (!started) {
                if (current(ticket)) report(Input.RecognitionDidNotStart)
                return
            }
            val buffer = ShortArray(hearing.config.sileroVadModelConfig.windowSize)
            var taken = 0
            val limit = (HEARING_RATE * MAX_UTTERANCE_SECONDS).toInt()
            while (current(ticket) && !finishRequested) {
                val read = mine.read(buffer, 0, buffer.size)
                if (!current(ticket)) break
                if (read < 0) {
                    if (finishRequested) break
                    if (current(ticket)) report(Input.ListeningBroke)
                    broke = true
                    break
                }
                if (read == 0) continue
                val heard = FloatArray(read) { buffer[it] / 32768.0f }
                recorded.add(heard)
                taken += heard.size
                hearing.acceptWaveform(heard)
                if (!hearing.empty() && !hearing.isSpeechDetected()) break
                if (taken >= limit) break
            }
            if (!broke && current(ticket)) {
                stopRecorder()
                try {
                    hearing.flush()
                } catch (error: Throwable) {
                    Log.e(TAG, "Хвост фразы не закрылся", error)
                }
                val samples = utteranceSamples(drainSegments(hearing), join(recorded))
                val text = if (samples.isEmpty()) "" else transcribe(reader, samples)
                if (current(ticket)) report(Input.ListeningEnded(text))
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Слушание оборвалось", error)
            if (started && current(ticket)) report(Input.ListeningBroke)
            else if (current(ticket)) report(Input.RecognitionDidNotStart)
        } finally {
            finishRequested = false
            if (recorder === mine) recorder = null
            try {
                if (mine.recordingState == AudioRecord.RECORDSTATE_RECORDING) mine.stop()
            } catch (_: IllegalStateException) {
            }
            mine.release()
        }
    }

    private fun drainSegments(hearing: Vad): List<FloatArray> {
        val segments = ArrayList<FloatArray>()
        while (!hearing.empty()) {
            segments.add(hearing.front().samples.copyOf())
            hearing.pop()
        }
        return segments
    }

    private fun join(chunks: List<FloatArray>): FloatArray {
        val count = chunks.sumOf { it.size }
        if (count == 0) return FloatArray(0)
        val samples = FloatArray(count)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(samples, offset)
            offset += chunk.size
        }
        return samples
    }

    private fun transcribe(reader: OfflineRecognizer, samples: FloatArray): String {
        val stream = reader.createStream()
        return try {
            stream.acceptWaveform(samples, HEARING_RATE)
            reader.decode(stream)
            reader.getResult(stream).text
        } finally {
            stream.release()
        }
    }

    private fun openRussian(): OfflineTts? {
        russian?.let { return it }
        return try {
            val opened = OfflineTts(assetManager = app.assets, config = vitsConfig(RU_MODEL, RU_TOKENS))
            russian = opened
            opened
        } catch (error: Throwable) {
            Log.e(TAG, "Русская озвучка не открылась", error)
            null
        }
    }

    private fun openEnglish(): OfflineTts? {
        english?.let { return it }
        return try {
            val opened = OfflineTts(assetManager = app.assets, config = vitsConfig(EN_MODEL, EN_TOKENS))
            english = opened
            opened
        } catch (error: Throwable) {
            Log.e(TAG, "Английская озвучка не открылась", error)
            null
        }
    }

    private fun vitsConfig(model: String, tokens: String): OfflineTtsConfig {
        return OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model,
                    tokens = tokens,
                    dataDir = espeakDir().absolutePath,
                ),
                numThreads = 2,
                provider = "cpu",
            ),
        )
    }

    private fun openHearing(): Boolean {
        if (recognizer != null && vad != null) return true
        return try {
            if (recognizer == null) {
                recognizer = OfflineRecognizer(
                    assetManager = app.assets,
                    config = OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = HEARING_RATE, featureDim = 80),
                        modelConfig = OfflineModelConfig(
                            moonshine = OfflineMoonshineModelConfig(
                                encoder = MOONSHINE_ENCODER,
                                mergedDecoder = MOONSHINE_DECODER,
                            ),
                            tokens = MOONSHINE_TOKENS,
                        ),
                    ),
                )
            }
            if (vad == null) {
                vad = Vad(
                    assetManager = app.assets,
                    config = VadModelConfig(
                        sileroVadModelConfig = SileroVadModelConfig(
                            model = SILERO,
                            threshold = 0.5f,
                            minSilenceDuration = UTTERANCE_SILENCE_SECONDS,
                            minSpeechDuration = 0.25f,
                            maxSpeechDuration = MAX_UTTERANCE_SECONDS,
                            windowSize = 512,
                        ),
                        sampleRate = HEARING_RATE,
                        numThreads = 1,
                        provider = "cpu",
                    ),
                )
            }
            true
        } catch (error: Throwable) {
            Log.e(TAG, "Распознавание не открылось", error)
            recognizer?.release()
            recognizer = null
            vad?.release()
            vad = null
            false
        }
    }

    private fun openRecorder(): AudioRecord? =
        openRecorder(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            ?: openRecorder(MediaRecorder.AudioSource.MIC)

    private fun openRecorder(source: Int): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(
            HEARING_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return null
        val created = AudioRecord(
            source,
            HEARING_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min * 2,
        )
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return null
        }
        return created
    }

    private fun staticTrack(rate: Int, sampleCount: Int): AudioTrack {
        // Статическая дорожка после создания ещё без клипа: состояние «нет данных», не сбой.
        // Прежняя проверка ждала «клип уже в буфере» до записи, бросала исключение и
        // оставляла экран на «Говорит» без звука. Буфер равен самому клипу: минимум потока
        // больше короткого слова, и такая дорожка не начинает играть.
        val bytesPerFrame = 2
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(rate)
            .build()
        val created = AudioTrack(
            attributes,
            format,
            staticClipBytes(sampleCount, bytesPerFrame),
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        if (!staticClipAcceptsWrite(created.state)) {
            created.release()
            throw IllegalStateException("Озвучка не открылась")
        }
        created.setVolume(1f)
        return created
    }

    private fun espeakDir(): File {
        val dest = File(app.filesDir, "espeak-ng-data")
        val stamp = File(app.filesDir, "espeak-ng-data.ready")
        if (!stamp.exists()) {
            if (dest.exists()) dest.deleteRecursively()
            copyAssetTree(app.assets, "espeak-ng-data", dest)
            stamp.writeText("ok")
        }
        return dest
    }

    private fun stopRecorder() {
        recorder?.let { recording ->
            try {
                if (recording.recordingState == AudioRecord.RECORDSTATE_RECORDING) recording.stop()
            } catch (_: IllegalStateException) {
            }
        }
    }

    private fun haltAudio() {
        stopped = true
        synchronized(audioLock) {
            track?.let { playing ->
                try {
                    if (playing.playState == AudioTrack.PLAYSTATE_PLAYING) playing.pause()
                    playing.flush()
                    playing.stop()
                } catch (_: IllegalStateException) {
                }
            }
        }
        stopRecorder()
    }

    private fun releaseEngines() {
        russian?.release()
        english?.release()
        recognizer?.release()
        vad?.release()
        track?.release()
        russian = null
        english = null
        recognizer = null
        vad = null
        track = null
    }

    private fun current(ticket: Int) = epoch.get() == ticket && !stopped && !closed

    private fun report(input: Input) {
        if (closed) return
        main.post {
            if (!closed) onOutcome(input)
        }
    }

    private companion object {
        const val TAG = "iverbs"
        const val UTTERANCE_SILENCE_SECONDS = 1.2f
        const val MAX_UTTERANCE_SECONDS = 30f
        const val PAUSE_SECONDS = 0.4f
        const val HEARING_RATE = 16000
        const val DRAIN_PAD_MS = 150L
        const val RU_MODEL = "ru/ru_RU-dmitri-medium.onnx"
        const val RU_TOKENS = "ru/tokens.txt"
        const val EN_MODEL = "en/en_GB-cori-high.onnx"
        const val EN_TOKENS = "en/tokens.txt"
        const val MOONSHINE_ENCODER = "moonshine/encoder_model.ort"
        const val MOONSHINE_DECODER = "moonshine/decoder_model_merged.ort"
        const val MOONSHINE_TOKENS = "moonshine/tokens.txt"
        const val SILERO = "silero_vad.onnx"
    }
}

private fun copyAssetTree(assets: AssetManager, assetPath: String, dest: File) {
    val children = assets.list(assetPath).orEmpty()
    if (children.isEmpty()) {
        dest.parentFile?.mkdirs()
        assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return
    }
    dest.mkdirs()
    for (child in children) {
        copyAssetTree(assets, "$assetPath/$child", File(dest, child))
    }
}
