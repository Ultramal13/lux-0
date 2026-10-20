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

    // Filas de arriba quemadas (una lámpara en la escena) y el resto con color.
    private fun partlyClipped(whiteRows: Int, r: Double, g: Double, b: Double): Triple<Plane, Plane, Plane> {
        fun yuv(r: Double, g: Double, b: Double) = Triple(0.299 * r + 0.587 * g + 0.114 * b,
            128 - 0.168736 * r - 0.331264 * g + 0.5 * b, 128 + 0.5 * r - 0.418688 * g - 0.081312 * b)
        val white = yuv(255.0, 255.0, 255.0); val color = yuv(r, g, b)
        val yb = ByteBuffer.allocate(w * h).apply { repeat(w * h) { putD(it, if (it / w < whiteRows) white.first else color.first) } }
        val cw = w / 2
        val ub = ByteBuffer.allocate(cw * h / 2).apply { repeat(cw * h / 2) { putD(it, if (it / cw * 2 < whiteRows) white.second else color.second) } }
        val vb = ByteBuffer.allocate(cw * h / 2).apply { repeat(cw * h / 2) { putD(it, if (it / cw * 2 < whiteRows) white.third else color.third) } }
        return Triple(Plane(yb, w, 1), Plane(ub, cw, 1), Plane(vb, cw, 1))
    }

    @Test fun sceneIgnoresClippedPart() {
        val (y, u, v) = partlyClipped(6, 180.0, 120.0, 90.0)
        val res = computeRoiYuv(y, u, v, w, h, 6)
        assertEquals("zona quemada", res.warning())          // blanco/gris sigue siendo estricto
        val scene = res.withoutClipped()!!
        assertNull(scene.warning())
        assertEquals(180.0 / 255, scene.r, 0.01)
        assertEquals(120.0 / 255, scene.g, 0.01)
        assertEquals(90.0 / 255, scene.b, 0.01)
    }

    @Test fun sceneMostlyClippedStillFails() {
        val (y, u, v) = frame(255.0, 255.0, 255.0, 1)
        assertNull(computeRoiYuv(y, u, v, w, h, 6).withoutClipped())
    }

    @Test fun dark() {
        val (y, u, v) = frame(2.0, 2.0, 2.0, 1)
        assertEquals("muy oscuro", computeRoiYuv(y, u, v, w, h, 6).warning())
    }
}
