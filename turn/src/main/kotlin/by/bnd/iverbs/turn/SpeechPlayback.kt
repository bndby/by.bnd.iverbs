package by.bnd.iverbs.turn

/** Отсчёт синтеза [-1, 1] в 16-бит. Дробь без умножения на дорожке была бы тишиной. */
fun pcm16(sample: Float): Short {
    val clamped = sample.coerceIn(-1f, 1f)
    return (clamped * 32767f).toInt().toShort()
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
