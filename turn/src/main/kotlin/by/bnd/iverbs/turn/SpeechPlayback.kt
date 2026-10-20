package by.bnd.iverbs.turn

/** Отсчёт синтеза [-1, 1] в 16-бит. Дробь без умножения на дорожке была бы тишиной. */
fun pcm16(sample: Float): Short {
    val clamped = sample.coerceIn(-1f, 1f)
    return (clamped * 32767f).toInt().toShort()
}

/**
 * Состояния статической дорожки. Числа те же, что у AudioTrack:
 * 0 — не открылась, 1 — клип уже в буфере, 2 — создана и ждёт клип.
 */
const val TRACK_UNOPENED = 0
const val TRACK_CLIP_LOADED = 1
const val TRACK_AWAITING_CLIP = 2

/**
 * Статическая дорожка сразу после создания ещё без клипа.
 * Платформа зовёт это «нет данных», а не сбой: писать можно.
 * Играть можно только когда клип уже лежит в буфере.
 */
fun staticClipAcceptsWrite(trackState: Int): Boolean =
    trackState == TRACK_AWAITING_CLIP || trackState == TRACK_CLIP_LOADED

fun staticClipCanPlay(trackState: Int): Boolean = trackState == TRACK_CLIP_LOADED

/** Байты статического клипа: ровно отсчёт, кратный кадру. Минимум потока сюда не входит. */
fun staticClipBytes(sampleCount: Int, bytesPerFrame: Int): Int {
    val frame = bytesPerFrame.coerceAtLeast(1)
    val bytes = sampleCount.coerceAtLeast(0).toLong() * frame
    if (bytes == 0L) return frame
    return (((bytes + frame - 1) / frame) * frame).toInt()
}

/** Байт в буфере дорожки: не короче минимума платформы и кратен кадру. */
fun speechBufferBytes(minBufferBytes: Int, sampleRate: Int, bytesPerFrame: Int): Int {
    val frame = bytesPerFrame.coerceAtLeast(1)
    val second = sampleRate.coerceAtLeast(1).toLong() * frame
    val floor = if (minBufferBytes > 0) minBufferBytes.toLong() else second
    val rounded = ((floor + frame - 1) / frame) * frame
    return rounded.coerceAtLeast(frame.toLong()).toInt()
}

/**
 * Фраза ещё звучит.
 * Головка считается от старта этой фразы, без знака: абсолютное положение после прошлой
 * фразы не значит, что новая уже доиграла. Пока не вышло время самой фразы, она тоже звучит.
 */
fun phraseStillPlaying(
    headAtStart: Long,
    headNow: Long,
    framesWritten: Long,
    elapsedMs: Long,
    durationMs: Long,
): Boolean {
    if (framesWritten <= 0) return false
    if (elapsedMs < durationMs) return true
    return playedFrames(headAtStart, headNow) < framesWritten
}

private fun playedFrames(headAtStart: Long, headNow: Long): Long {
    val start = headAtStart and 0xFFFFFFFFL
    val now = headNow and 0xFFFFFFFFL
    return if (now >= start) now - start else (0x1_0000_0000L - start) + now
}
