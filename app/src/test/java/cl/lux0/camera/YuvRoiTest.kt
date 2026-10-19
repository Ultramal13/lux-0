package cl.lux0.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer

class YuvRoiTest {
    @Before fun spanish() { cl.lux0.I18n.force = "es" }

    private val w = 16; private val h = 16

    // Cuadro uniforme a partir de un RGB 0..255 (conversión JFIF directa).
    private fun frame(r: Double, g: Double, b: Double, uvPixelStride: Int): Triple<Plane, Plane, Plane> {
        val yv = 0.299 * r + 0.587 * g + 0.114 * b
        val uv = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
        val vv = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
        val yb = ByteBuffer.allocate(w * h).apply { repeat(w * h) { putD(it, yv) } }
        val cw = w / 2 * uvPixelStride
        val ub = ByteBuffer.allocate(cw * h / 2).apply { repeat(cw * h / 2) { putD(it, uv) } }
        val vb = ByteBuffer.allocate(cw * h / 2).apply { repeat(cw * h / 2) { putD(it, vv) } }
        return Triple(Plane(yb, w, 1), Plane(ub, cw, uvPixelStride), Plane(vb, cw, uvPixelStride))
    }

    private fun ByteBuffer.putD(i: Int, v: Double) { put(i, Math.round(v).toInt().toByte()) }

    @Test fun recoversRgbPlanar() {
        val (y, u, v) = frame(180.0, 120.0, 90.0, 1)
        val res = computeRoiYuv(y, u, v, w, h, 6)
        assertEquals(180.0 / 255, res.r, 0.01)
        assertEquals(120.0 / 255, res.g, 0.01)
        assertEquals(90.0 / 255, res.b, 0.01)
        assertNull(res.warning())
    }

    @Test fun recoversRgbSemiPlanar() {
        val (y, u, v) = frame(100.0, 140.0, 200.0, 2)
        val res = computeRoiYuv(y, u, v, w, h, 6)
        assertEquals(100.0 / 255, res.r, 0.01)
        assertEquals(200.0 / 255, res.b, 0.01)
    }

    @Test fun clipped() {
        val (y, u, v) = frame(255.0, 255.0, 255.0, 1)
        assertEquals("zona quemada", computeRoiYuv(y, u, v, w, h, 6).warning())
    }

    @Test fun dark() {
        val (y, u, v) = frame(2.0, 2.0, 2.0, 1)
        assertEquals("muy oscuro", computeRoiYuv(y, u, v, w, h, 6).warning())
    }
}
