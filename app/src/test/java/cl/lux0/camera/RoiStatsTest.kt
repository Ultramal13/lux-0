package cl.lux0.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoiStatsTest {
    private val black = floatArrayOf(64f, 64f, 64f, 64f)

    // Construye un mosaico 8x8 con el patrón dado (0 RGGB ... 3 BGGR).
    private fun mosaic(cfa: Int, r: Int, g: Int, b: Int): ShortArray {
        val layouts = arrayOf(intArrayOf(0, 1, 1, 2), intArrayOf(1, 0, 2, 1), intArrayOf(1, 2, 0, 1), intArrayOf(2, 1, 1, 0))
        val vals = intArrayOf(r, g, b)
        return ShortArray(64) { i -> vals[layouts[cfa][(i / 8 % 2) * 2 + (i % 8 % 2)]].toShort() }
    }

    private fun roi(data: ShortArray, cfa: Int) = computeRoi(data, 8, 8, 8, cfa, black, 1023, 4, 4, 8)

    @Test fun gbrgChannels() {
        val res = roi(mosaic(2, 500, 300, 200), 2)
        assertEquals(436.0 / 959, res.r, 1e-6)
        assertEquals(236.0 / 959, res.g, 1e-6)
        assertEquals(136.0 / 959, res.b, 1e-6)
        assertNull(res.warning())
    }

    @Test fun rggbSameChannels() {
        val res = roi(mosaic(0, 500, 300, 200), 0)
        assertEquals(436.0 / 959, res.r, 1e-6)
        assertEquals(136.0 / 959, res.b, 1e-6)
    }

    @Test fun clipped() {
        val d = mosaic(0, 500, 300, 200)
        for (i in 0 until 32) d[i] = 1023
        val res = roi(d, 0)
        assert(res.clippedFraction >= 0.02)
        assertEquals("zona quemada", res.warning())
    }

    @Test fun dark() {
        assertEquals("muy oscuro", roi(mosaic(0, 70, 70, 70), 0).warning())
    }
}
