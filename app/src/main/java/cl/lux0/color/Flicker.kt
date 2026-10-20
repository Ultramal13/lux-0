package cl.lux0.color

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parpadeo por bandas del rolling shutter: con exposición muy corta, una luz que pulsa deja franjas
 * horizontales; cada fila se lee un poco después que la anterior, así que el perfil por filas es la
 * forma de onda de la luz en el tiempo.
 */
object Flicker {
    data class Result(val hz: Double, val depth: Double)

    /**
     * rows = luminancia media de cada fila (en orden de lectura); rowTimeSec = tiempo entre filas.
     * Devuelve null si no hay parpadeo visible (profundidad < 3 %).
     */
    fun detect(rows: DoubleArray, rowTimeSec: Double, minDepth: Double = 0.03): Result? {
        val n = rows.size
        if (n < 32 || rowTimeSec <= 0) return null
        val mean = rows.average()
        if (mean <= 1e-6) return null
        // Ajuste conjunto por mínimos cuadrados: tendencia cuadrática (viñeteo, degradados) + onda a la
        // frecuencia probada, para que la tendencia no "se coma" la onda con pocos ciclos en el cuadro.
        // Lo que no depende de la frecuencia se calcula una vez; seno y coseno van por recurrencia.
        val t0 = DoubleArray(n) { 1.0 }
        val t1 = DoubleArray(n) { (it - n / 2.0) / n }
        val t2 = DoubleArray(n) { t1[it] * t1[it] }
        val trend = arrayOf(t0, t1, t2)
        val gram = Array(3) { a -> DoubleArray(3) { b -> dot(trend[a], trend[b]) } }
        val ty = DoubleArray(3) { dot(trend[it], rows) }
        val yy = dot(rows, rows)
        val m = Array(5) { DoubleArray(6) }
        val trendSin = DoubleArray(3); val trendCos = DoubleArray(3)

        fun evaluate(f: Double): Pair<Double, Double>? {
            val w = 2 * PI * f * rowTimeSec
            val cw = cos(w); val sw = sin(w)
            var s = 0.0; var c = 1.0
            var ss = 0.0; var cc = 0.0; var sc = 0.0; var ys = 0.0; var yc = 0.0
            trendSin.fill(0.0); trendCos.fill(0.0)
            for (i in 0 until n) {
                ss += s * s; cc += c * c; sc += s * c; ys += rows[i] * s; yc += rows[i] * c
                for (k in 0..2) { trendSin[k] += trend[k][i] * s; trendCos[k] += trend[k][i] * c }
                val ns = s * cw + c * sw; c = c * cw - s * sw; s = ns
            }
            for (r in 0..2) { for (k in 0..2) m[r][k] = gram[r][k]; m[r][3] = trendSin[r]; m[r][4] = trendCos[r]; m[r][5] = ty[r] }
            for (k in 0..2) { m[3][k] = trendSin[k]; m[4][k] = trendCos[k] }
            m[3][3] = ss; m[3][4] = sc; m[3][5] = ys
            m[4][3] = sc; m[4][4] = cc; m[4][5] = yc
            val rhs = DoubleArray(5) { m[it][5] }
            val coef = solve(m) ?: return null
            // Error cuadrático de la solución de mínimos cuadrados: yᵀy − coefᵀ·(Aᵀy).
            var explained = 0.0
            for (k in 0 until 5) explained += coef[k] * rhs[k]
            return (yy - explained) to sqrt(coef[3] * coef[3] + coef[4] * coef[4])
        }

        var bestHz = 0.0; var bestAmp = 0.0; var bestRes = Double.MAX_VALUE
        fun scan(from: Double, to: Double, step: Double) {
            var f = from
            while (f <= to) {
                evaluate(f)?.let { (res, amp) -> if (res < bestRes) { bestRes = res; bestHz = f; bestAmp = amp } }
                f += step
            }
        }
        scan(60.0, 1000.0, 5.0)                                          // búsqueda gruesa
        scan(maxOf(60.0, bestHz - 5), minOf(1000.0, bestHz + 5), 0.5)    // refinamiento
        val depth = 2 * bestAmp / mean
        if (depth < minDepth) return null
        // Las luces de red parpadean a 100 o 120 Hz: si está cerca, se informa el valor exacto.
        val snapped = listOf(100.0, 120.0).firstOrNull { abs(bestHz - it) / it < 0.06 } ?: bestHz
        return Result(snapped, depth)
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double { var s = 0.0; for (i in a.indices) s += a[i] * b[i]; return s }

    /** Eliminación gaussiana con pivoteo sobre una matriz aumentada k×(k+1) (se modifica en el lugar). */
    private fun solve(m: Array<DoubleArray>): DoubleArray? {
        val k = m.size
        for (col in 0 until k) {
            val piv = (col until k).maxBy { abs(m[it][col]) }
            if (abs(m[piv][col]) < 1e-12) return null
            val t = m[col]; m[col] = m[piv]; m[piv] = t
            for (r in 0 until k) if (r != col) {
                val fct = m[r][col] / m[col][col]
                for (c in col..k) m[r][c] -= fct * m[col][c]
            }
        }
        return DoubleArray(k) { m[it][k] / m[it][it] }
    }

    /** Una exposición es segura si dura un número entero de ciclos de la luz. */
    fun isSafe(tSec: Double, hz: Double): Boolean {
        val cycles = tSec * hz
        return cycles >= 0.95 && abs(cycles - cycles.roundToInt()) < 0.05
    }

    /** Hasta 3 obturaciones seguras que caben en un cuadro a esos fps: (1/x, ángulo). */
    fun safeShutters(fps: Double, hz: Double): List<Pair<String, String>> {
        val frame = 1.0 / fps
        return (1..8).map { it / hz }.filter { it <= frame * 1.0001 }.reversed().take(3).map { t ->
            val denom = 1.0 / t
            val name = if (abs(denom - denom.roundToInt()) < 0.01) "1/${denom.roundToInt()}" else String.format(java.util.Locale.US, "1/%.1f", denom)
            val angle = t * fps * 360
            val a = if (abs(angle - floor(angle)) < 0.05) "${angle.roundToInt()}°" else String.format(java.util.Locale.US, "%.1f°", angle)
            name to a
        }
    }
}
