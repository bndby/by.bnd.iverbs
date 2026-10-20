package by.bnd.iverbs.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechPlaybackTest {
    @Test
    fun `отсчёт синтеза становится слышимым 16-битным, а не нулём`() {
        assertEquals(0, pcm16(0f).toInt())
        assertEquals(32767, pcm16(1f).toInt())
        assertEquals(-32767, pcm16(-1f).toInt())
        assertEquals(16383, pcm16(0.5f).toInt())
        assertEquals(32767, pcm16(2f).toInt())
    }

    @Test
    fun `буфер озвучки кратен кадру и не подставляет частоту вместо байтов`() {
        assertEquals(8820, speechBufferBytes(minBufferBytes = 8820, sampleRate = 22050, bytesPerFrame = 4))
        assertEquals(22052, speechBufferBytes(minBufferBytes = 22050, sampleRate = 22050, bytesPerFrame = 4))
        assertEquals(88200, speechBufferBytes(minBufferBytes = -1, sampleRate = 22050, bytesPerFrame = 4))
        assertEquals(8820, speechBufferBytes(minBufferBytes = 8820, sampleRate = 22050, bytesPerFrame = 2))
    }

    @Test
    fun `статическая дорожка без клипа принимает запись и не считается закрытой`() {
        assertEquals(true, staticClipAcceptsWrite(TRACK_AWAITING_CLIP))
        assertEquals(true, staticClipAcceptsWrite(TRACK_CLIP_LOADED))
        assertEquals(false, staticClipAcceptsWrite(TRACK_UNOPENED))
        assertEquals(false, staticClipCanPlay(TRACK_AWAITING_CLIP))
        assertEquals(true, staticClipCanPlay(TRACK_CLIP_LOADED))
    }

    @Test
    fun `статический клип равен байтам отсчёта и не раздувается до минимума потока`() {
        assertEquals(44100, staticClipBytes(sampleCount = 22050, bytesPerFrame = 2))
        assertEquals(2, staticClipBytes(sampleCount = 1, bytesPerFrame = 2))
    }

    @Test
    fun `фраза звучит, пока не вышло её время и не сыграны её кадры`() {
        assertEquals(
            true,
            phraseStillPlaying(
                headAtStart = 80_000,
                headNow = 80_000,
                framesWritten = 50_000,
                elapsedMs = 100,
                durationMs = 2_267,
            ),
        )
        assertEquals(
            true,
            phraseStillPlaying(
                headAtStart = 0,
                headNow = 100,
                framesWritten = 50_000,
                elapsedMs = 2_267,
                durationMs = 2_267,
            ),
        )
        assertEquals(
            false,
            phraseStillPlaying(
                headAtStart = 80_000,
                headNow = 130_000,
                framesWritten = 50_000,
                elapsedMs = 2_267,
                durationMs = 2_267,
            ),
        )
        assertEquals(
            false,
            phraseStillPlaying(
                headAtStart = -16,
                headNow = 20,
                framesWritten = 36,
                elapsedMs = 10,
                durationMs = 2,
            ),
        )
    }
}
