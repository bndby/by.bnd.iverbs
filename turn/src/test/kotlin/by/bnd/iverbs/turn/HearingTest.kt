package by.bnd.iverbs.turn

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HearingTest {
    @Test
    fun `голос с микрофона распознаётся, даже если VAD не отдал сегмент`() {
        val recorded = floatArrayOf(0.1f, -0.2f, 0.3f)

        assertArrayEquals(recorded, utteranceSamples(emptyList(), recorded), 0f)
    }

    @Test
    fun `сегменты VAD идут в распознавание целиком и по порядку`() {
        val first = floatArrayOf(0.1f)
        val second = floatArrayOf(0.2f, 0.3f)

        assertArrayEquals(
            floatArrayOf(0.1f, 0.2f, 0.3f),
            utteranceSamples(listOf(first, second), floatArrayOf(9f, 9f)),
            0f,
        )
    }
}
