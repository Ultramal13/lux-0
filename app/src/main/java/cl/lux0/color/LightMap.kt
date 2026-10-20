package cl.lux0.color

import kotlin.math.abs

/** Mapa de luces mezcladas: temperatura por celda, ya ordenada como se ve en pantalla. */
data class LightMap(val cols: Int, val rows: Int, val cct: DoubleArray) {
    fun at(col: Int, row: Int): Double = cct[row * cols + col]

    companion object {
        /**
         * Pasa una grilla en coordenadas del sensor (bufCols × bufRows, fila a fila) a la orientación de
         * pantalla vertical. orientation = SENSOR_ORIENTATION (90 o 270 en teléfonos).
         */
        fun toDisplay(buf: DoubleArray, bufCols: Int, bufRows: Int, orientation: Int): LightMap {
            if (orientation % 180 == 0) return LightMap(bufCols, bufRows, buf.copyOf())
            val cols = bufRows; val rows = bufCols
            val out = DoubleArray(cols * rows)
            for (by in 0 until bufRows) for (bx in 0 until bufCols) {
                // 90°: la imagen gira en sentido horario; 270°: antihorario.
                val (dx, dy) = if (orientation == 90) (bufRows - 1 - by) to bx else by to (bufCols - 1 - bx)
                out[dy * cols + dx] = buf[by * bufCols + bx]
            }
            return LightMap(cols, rows, out)
        }

        /** Diferencia en mired respecto de la referencia: positivo = la celda es más cálida. */
        fun warmth(cellK: Double, refK: Double): Double = ColorMath.mired(cellK) - ColorMath.mired(refK)

        /** Intensidad del color de la celda: 0 sin diferencia, 1 desde 100 mired. */
        fun strength(deltaMired: Double): Double = (abs(deltaMired) / 100.0).coerceIn(0.0, 1.0)
    }

    override fun equals(other: Any?) = other is LightMap && cols == other.cols && rows == other.rows && cct.contentEquals(other.cct)
    override fun hashCode() = 31 * (31 * cols + rows) + cct.contentHashCode()
}
