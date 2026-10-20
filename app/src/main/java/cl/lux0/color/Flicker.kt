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
        // frecuencia probada. Así la tendencia no "se come" la onda cuando el cuadro cubre pocos ciclos.
        val x = DoubleArray(n) { (it - n / 2.0) / n }
        var bestHz = 0.0; var bestAmp = 0.0; var bestRes = Double.MAX_VALUE
        var f = 60.0
        while (f <= 1000.0) {
            val w = 2 * PI * f * rowTimeSec
            val fit = fit(n) { i -> doubleArrayOf(1.0, x[i], x[i] * x[i], sin(w * i), cos(w * i)) }.let { it(rows) }
            if (fit != null && fit.second < bestRes) {
                bestRes = fit.second; bestHz = f
                bestAmp = sqrt(fit.first[3] * fit.first[3] + fit.first[4] * fit.first[4])
            }
            f += 1.0
        }
        val depth = 2 * bestAmp / mean
        if (depth < minDepth) return null
        // Las luces de red parpadean a 100 o 120 Hz: si está cerca, se informa el valor exacto.
        val snapped = listOf(100.0, 120.0).firstOrNull { abs(bestHz - it) / it < 0.06 } ?: bestHz
        return Result(snapped, depth)
    }

    /** Mínimos cuadrados con una base por fila; devuelve (coeficientes, error cuadrático). */
    private fun fit(n: Int, basis: (Int) -> DoubleArray): (DoubleArray) -> Pair<DoubleArray, Double>? = { y ->
        val k = 5
        val m = Array(k) { DoubleArray(k + 1) }
        val rowsB = Array(n) { basis(it) }
        for (i in 0 until n) {
            val p = rowsB[i]
            for (a in 0 until k) { m[a][k] += p[a] * y[i]; for (b in 0 until k) m[a][b] += p[a] * p[b] }
        }
        solve(m)?.let { c ->
            var res = 0.0
            for (i in 0 until n) { var e = y[i]; for (a in 0 until k) e -= c[a] * rowsB[i][a]; res += e * e }
            c to res
        }
    }

    /** Eliminación gaussiana con pivoteo sobre una matriz aumentada k×(k+1). */
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
