package by.bnd.iverbs.turn

/**
 * Что отдать распознаванию.
 * Сегменты VAD — сказанная речь без окружающей тишины. Если детектор ничего не отдал,
 * в ход идёт вся запись микрофона: голос не выбрасывается.
 */
fun utteranceSamples(segments: List<FloatArray>, recorded: FloatArray): FloatArray {
    var count = 0
    for (segment in segments) count += segment.size
    if (count == 0) return recorded
    val voiced = FloatArray(count)
    var offset = 0
    for (segment in segments) {
        segment.copyInto(voiced, offset)
        offset += segment.size
    }
    return voiced
}
