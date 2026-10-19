package cl.lux0.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ColorMathTest {
    @Test fun d65() {
        val r = ColorMath.xyToReading(0.31271, 0.32902)
        assertEquals(6504.0, r.cct, 15.0)
        assertEquals(0.0032, r.duv, 0.0005)
    }

    @Test fun illuminantA() {
        val r = ColorMath.xyToReading(0.44757, 0.40745)
        assertEquals(2856.0, r.cct, 15.0)
        assertTrue(abs(r.duv) < 0.0005)
    }

    private fun uvToXy(u: Double, v: Double): Pair<Double, Double> {
        val d = 2 * u - 8 * v + 4
        return 3 * u / d to 2 * v / d
    }

    @Test fun duvSign() {
        val (u, v) = ColorMath.planckUv(4000.0)
        val (u2, v2) = ColorMath.planckUv(4010.0)
        val len = kotlin.math.hypot(u2 - u, v2 - v)
        // Normal al locus apuntando hacia +v (verde).
        var nu = -(v2 - v) / len; var nv = (u2 - u) / len
        if (nv < 0) { nu = -nu; nv = -nv }
        val (gx, gy) = uvToXy(u + 0.01 * nu, v + 0.01 * nv)
        assertEquals(0.01, ColorMath.xyToReading(gx, gy).duv, 0.001)
        val (mx, my) = uvToXy(u - 0.01 * nu, v - 0.01 * nv)
        assertEquals(-0.01, ColorMath.xyToReading(mx, my).duv, 0.001)
    }

    @Test fun neutralToXyRecoversD65() {
        // XYZ -> sRGB lineal: una cámara "sRGB" ve el blanco D65 como (1,1,1).
        val xyzToSrgb = doubleArrayOf(
            3.2404542, -1.5371385, -0.4985314,
            -0.9692660, 1.8760108, 0.0415560,
            0.0556434, -0.2040259, 1.0572252,
        )
        val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val (x, y) = ColorMath.cameraNeutralToXy(
            doubleArrayOf(1.0, 1.0, 1.0), xyzToSrgb, xyzToSrgb, id, id, 2856.0, 6504.0,
        )
        assertEquals(0.3127, x, 0.002)
        assertEquals(0.3290, y, 0.002)
    }

    @Test fun calibrationOnePoint() {
        val off = Calibration.offsetFor(5000.0, 5200.0)
        assertEquals(5200.0, Calibration.apply(5000.0, off), 1.0)
    }

    @Test fun outOfRangeFlag() {
        assertTrue(ColorMath.xyToReading(0.22, 0.21).outOfRange)
    }
}
