package cl.lux0.color

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/** Resultado de una medición de color. duv > 0 = verde, < 0 = magenta. */
data class Reading(
    val cct: Double,
    val duv: Double,
    val x: Double,
    val y: Double,
    val outOfRange: Boolean,
)

object ColorMath {
    private const val MIN_K = 1000.0
    private const val MAX_K = 20000.0

    fun mired(k: Double): Double = 1e6 / k

    /** Locus de Planck en uv CIE 1960 (aproximación de Krystek, 1985). */
    fun planckUv(t: Double): Pair<Double, Double> {
        val u = (0.860117757 + 1.54118254e-4 * t + 1.28641212e-7 * t * t) /
            (1 + 8.42420235e-4 * t + 7.08145163e-7 * t * t)
        val v = (0.317398726 + 4.22806245e-5 * t + 4.20481691e-8 * t * t) /
            (1 - 2.89741816e-5 * t + 1.61456053e-7 * t * t)
        return u to v
    }

    fun xyToUv(x: Double, y: Double): Pair<Double, Double> {
        val d = -2 * x + 12 * y + 3
        return 4 * x / d to 6 * y / d
    }

    private fun distAt(u: Double, v: Double, mired: Double): Double {
        val (pu, pv) = planckUv(1e6 / mired)
        return hypot(u - pu, v - pv)
    }

    /** CCT por mínima distancia al locus (barrido grueso en mired + sección áurea). */
    fun xyToReading(x: Double, y: Double): Reading {
        val (u, v) = xyToUv(x, y)
        val lo = mired(MAX_K)
        val hi = mired(MIN_K)
        var best = lo
        var bestD = Double.MAX_VALUE
        var m = lo
        while (m <= hi) {
            val d = distAt(u, v, m)
            if (d < bestD) { bestD = d; best = m }
            m += 2.0
        }
        var a = (best - 2.0).coerceAtLeast(lo)
        var b = (best + 2.0).coerceAtMost(hi)
        val g = 0.6180339887498949
        repeat(60) {
            val c = b - g * (b - a)
            val e = a + g * (b - a)
            if (distAt(u, v, c) < distAt(u, v, e)) b = e else a = c
        }
        val mBest = (a + b) / 2
        val cct = 1e6 / mBest
        val (pu, pv) = planckUv(cct)
        val duv = hypot(u - pu, v - pv) * sign(v - pv)
        val outOfRange = abs(mBest - lo) < 0.5 || abs(mBest - hi) < 0.5
        return Reading(cct, duv, x, y, outOfRange)
    }

    // --- Matrices 3x3 por filas ---
    fun mul(a: DoubleArray, b: DoubleArray): DoubleArray = DoubleArray(9) { i ->
        val r = i / 3; val c = i % 3
        a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
    }

    fun mulVec(a: DoubleArray, v: DoubleArray): DoubleArray = DoubleArray(3) { r ->
        a[r * 3] * v[0] + a[r * 3 + 1] * v[1] + a[r * 3 + 2] * v[2]
    }

    fun inverse(m: DoubleArray): DoubleArray {
        val (a, b, c) = Triple(m[0], m[1], m[2])
        val (d, e, f) = Triple(m[3], m[4], m[5])
        val (g, h, i) = Triple(m[6], m[7], m[8])
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        require(abs(det) > 1e-12) { "matriz singular" }
        return doubleArrayOf(
            (e * i - f * h), -(b * i - c * h), (b * f - c * e),
            -(d * i - f * g), (a * i - c * g), -(a * f - c * d),
            (d * h - e * g), -(a * h - b * g), (a * e - b * d),
        ).map { it / det }.toDoubleArray()
    }

    private fun lerp(a: DoubleArray, b: DoubleArray, w: Double) =
        DoubleArray(9) { a[it] * w + b[it] * (1 - w) }

    /**
     * Neutral de cámara (RGB del blanco medido) → xy del iluminante, como en DNG:
     * cámara = CC · CM · XYZ, con CM/CC interpolados por 1/T entre los iluminantes de referencia.
     */
    fun cameraNeutralToXy(
        neutral: DoubleArray,
        cm1: DoubleArray, cm2: DoubleArray,
        cc1: DoubleArray, cc2: DoubleArray,
        t1: Double, t2: Double,
    ): Pair<Double, Double> {
        var w = 0.5
        var xy = 0.3127 to 0.3290
        repeat(5) {
            val m = mul(lerp(cc1, cc2, w), lerp(cm1, cm2, w))
            val xyz = mulVec(inverse(m), neutral)
            val s = xyz[0] + xyz[1] + xyz[2]
            xy = xyz[0] / s to xyz[1] / s
            val t = xyToReading(xy.first, xy.second).cct
            w = if (t1 == t2) 0.5
            else ((1 / t - 1 / t2) / (1 / t1 - 1 / t2)).coerceIn(0.0, 1.0)
        }
        return xy
    }
}
