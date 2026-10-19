package cl.lux0.camera

/** Promedio RAW dentro del círculo. Canales normalizados 0..1 tras restar el negro. */
data class RoiResult(
    val r: Double,
    val g: Double,
    val b: Double,
    val clippedFraction: Double,
    val meanLevel: Double,
)

// Canal de cada posición del 2x2 (índice = fila%2*2 + col%2); 0=R 1=G 2=B.
// El orden sigue las constantes de Camera2: 0 RGGB, 1 GRBG, 2 GBRG, 3 BGGR.
private val CFA_LAYOUT = arrayOf(
    intArrayOf(0, 1, 1, 2),
    intArrayOf(1, 0, 2, 1),
    intArrayOf(1, 2, 0, 1),
    intArrayOf(2, 1, 1, 0),
)

fun computeRoi(
    data: ShortArray,
    width: Int,
    height: Int,
    rowStridePx: Int,
    cfa: Int,
    black: FloatArray,
    white: Int,
    cx: Int,
    cy: Int,
    radius: Int,
): RoiResult {
    val layout = CFA_LAYOUT[cfa.coerceIn(0, 3)]
    val sum = DoubleArray(3)
    val cnt = IntArray(3)
    var clipped = 0
    var total = 0
    val stride = 2 * maxOf(1, radius / 64)
    val r2 = radius.toLong() * radius
    var by = ((cy - radius) / 2 * 2).coerceAtLeast(0)
    while (by + 1 < height && by <= cy + radius) {
        var bx = ((cx - radius) / 2 * 2).coerceAtLeast(0)
        while (bx + 1 < width && bx <= cx + radius) {
            val dx = (bx + 1 - cx).toLong(); val dy = (by + 1 - cy).toLong()
            if (dx * dx + dy * dy <= r2) {
                for (p in 0 until 4) {
                    val x = bx + (p and 1); val y = by + (p shr 1)
                    val raw = data[y * rowStridePx + x].toInt() and 0xFFFF
                    total++
                    if (raw >= white) clipped++
                    val v = (raw - black[p]) / (white - black[p])
                    val ch = layout[p]
                    sum[ch] += v.toDouble(); cnt[ch]++
                }
            }
            bx += stride
        }
        by += stride
    }
    if (total == 0) return RoiResult(0.0, 0.0, 0.0, 0.0, 0.0)
    val r = sum[0] / cnt[0]; val g = sum[1] / cnt[1]; val b = sum[2] / cnt[2]
    return RoiResult(r, g, b, clipped.toDouble() / total, (r + g + b) / 3)
}

fun RoiResult.warning(): String? = when {
    clippedFraction >= 0.02 -> "zona quemada"
    meanLevel < 0.02 -> "muy oscuro"
    else -> null
}
