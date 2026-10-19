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
import kotlin.math.min

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
    private var track: AudioTrack? = null
    private var recorder: AudioRecord? = null
    private var framesWritten = 0

    @Volatile private var stopped = true
    @Volatile private var closed = false

    fun perform(command: Command?) {
        if (closed) return
        when (command) {
            null, Command.RequestPermission, Command.OpenSettings -> Unit
            is Command.Speak -> begin { ticket -> speak(command.phrase, ticket) }
            Command.Listen -> begin { ticket -> listen(ticket) }
            Command.Stop -> {
                haltAudio()
                epoch.incrementAndGet()
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
        framesWritten = 0
        for (part in phrase.parts) {
            if (!current(ticket)) return false
            val played = when (part) {
                PhrasePart.Pause -> writeSilence(ticket)
                is PhrasePart.Said -> say(part, ticket)
            }
            if (!played) return false
        }
        awaitDrain(ticket)
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
        write(audio.samples, audio.sampleRate, ticket)
        return current(ticket)
    }

    private fun writeSilence(ticket: Int): Boolean {
        val rate = track?.sampleRate ?: DEFAULT_RATE
        val count = (rate * PAUSE_SECONDS).toInt()
        write(FloatArray(count), rate, ticket)
        return current(ticket)
    }

    private fun write(samples: FloatArray, rate: Int, ticket: Int) {
        if (!current(ticket) || samples.isEmpty()) return
        val playing = trackAt(rate)
        var offset = 0
        while (offset < samples.size && current(ticket)) {
            val count = min(CHUNK, samples.size - offset)
            val wrote = try {
                playing.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            } catch (_: IllegalStateException) {
                break
            }
            if (wrote <= 0) break
            offset += wrote
            framesWritten += wrote
        }
    }

    private fun awaitDrain(ticket: Int) {
        val playing = track ?: return
        val target = framesWritten
        val deadline = SystemClock.elapsedRealtime() + DRAIN_LIMIT_MS
        while (current(ticket) && playing.playbackHeadPosition < target) {
            if (SystemClock.elapsedRealtime() >= deadline) break
            val remaining = target - playing.playbackHeadPosition
            val sleepMs = (remaining * 1000L / playing.sampleRate).coerceIn(20L, 200L)
            Thread.sleep(sleepMs)
        }
    }

    private fun listen(ticket: Int) {
        if (!openHearing()) {
            if (current(ticket)) report(Input.ModelDidNotOpen(Model.EnglishRecognition))
            return
        }
        val hearing = vad ?: return
        val reader = recognizer ?: return
        hearing.reset()
        val mine = try {
            openRecorder()
        } catch (error: Throwable) {
            Log.e(TAG, "Микрофон не стартовал", error)
            null
        }
        if (mine == null) {
            if (current(ticket)) report(Input.RecognitionDidNotStart)
            return
        }
        recorder = mine
        var started = false
        try {
            mine.startRecording()
            started = mine.recordingState == AudioRecord.RECORDSTATE_RECORDING
            if (!started) {
                if (current(ticket)) report(Input.RecognitionDidNotStart)
                return
            }
            val buffer = ShortArray(hearing.config.sileroVadModelConfig.windowSize)
            while (current(ticket)) {
                val read = mine.read(buffer, 0, buffer.size)
                if (!current(ticket)) break
                if (read < 0) {
                    report(Input.ListeningBroke)
                    break
                }
                if (read == 0) continue
                val samples = FloatArray(read) { buffer[it] / 32768.0f }
                hearing.acceptWaveform(samples)
                if (!hearing.empty()) {
                    val segment = hearing.front()
                    val text = transcribe(reader, segment.samples)
                    hearing.pop()
                    if (current(ticket)) report(Input.ListeningEnded(text))
                    break
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Слушание оборвалось", error)
            if (started && current(ticket)) report(Input.ListeningBroke)
            else if (current(ticket)) report(Input.RecognitionDidNotStart)
        } finally {
            if (recorder === mine) recorder = null
            try {
                if (mine.recordingState == AudioRecord.RECORDSTATE_RECORDING) mine.stop()
            } catch (_: IllegalStateException) {
            }
            mine.release()
        }
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
                            minSilenceDuration = 0.25f,
                            minSpeechDuration = 0.25f,
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

    private fun openRecorder(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(
            HEARING_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return null
        val created = AudioRecord(
            MediaRecorder.AudioSource.MIC,
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

    private fun trackAt(rate: Int): AudioTrack {
        val existing = track
        if (existing != null && existing.sampleRate == rate) {
            if (existing.playState != AudioTrack.PLAYSTATE_PLAYING) existing.play()
            return existing
        }
        existing?.release()
        val min = AudioTrack.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(rate)
            .build()
        val created = AudioTrack(
            attributes,
            format,
            min.coerceAtLeast(rate),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        created.play()
        track = created
        framesWritten = 0
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

    private fun haltAudio() {
        stopped = true
        track?.let { playing ->
            try {
                playing.pause()
                playing.flush()
            } catch (_: IllegalStateException) {
            }
        }
        recorder?.let { recording ->
            try {
                if (recording.recordingState == AudioRecord.RECORDSTATE_RECORDING) recording.stop()
            } catch (_: IllegalStateException) {
            }
        }
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
        const val PAUSE_SECONDS = 0.4f
        const val DEFAULT_RATE = 22050
        const val HEARING_RATE = 16000
        const val CHUNK = 4096
        const val DRAIN_LIMIT_MS = 15_000L
        const val RU_MODEL = "ru/ru_RU-dmitri-medium.onnx"
        const val RU_TOKENS = "ru/tokens.txt"
        const val EN_MODEL = "en/en_GB-cori-medium.onnx"
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
