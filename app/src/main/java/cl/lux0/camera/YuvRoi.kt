package cl.lux0.camera

import cl.lux0.tr
import java.nio.ByteBuffer

/** Promedio dentro del círculo. Canales lineales normalizados 0..1. */
data class RoiResult(
    val r: Double,
    val g: Double,
    val b: Double,
    val clippedFraction: Double,
    val meanLevel: Double,
)

fun RoiResult.warning(): String? = when {
    clippedFraction >= 0.02 -> tr("zona quemada", "clipped area")
    meanLevel < 0.02 -> tr("muy oscuro", "too dark")
    else -> null
}

/** Un plano de YUV_420_888 tal como lo entrega Image.Plane. */
class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

/**
 * Promedia el círculo central de un cuadro YUV_420_888 (rango completo, BT.601 como JFIF).
 * Con curva de tono lineal y matriz de color neutra, el RGB resultante es el del sensor.
 */
fun computeRoiYuv(y: Plane, u: Plane, v: Plane, width: Int, height: Int, radius: Int): RoiResult {
    val cx = width / 2; val cy = height / 2
    val r2 = radius * radius
    var sr = 0.0; var sg = 0.0; var sb = 0.0
    var n = 0; var clipped = 0
    var py = cy - radius
    while (py <= cy + radius) {
        if (py in 0 until height) {
            var px = cx - radius
            while (px <= cx + radius) {
                val dx = px - cx; val dy = py - cy
                if (px in 0 until width && dx * dx + dy * dy <= r2) {
                    val yy = y.buffer.get(py * y.rowStride + px * y.pixelStride).toInt() and 0xFF
                    val cu = (u.buffer.get((py / 2) * u.rowStride + (px / 2) * u.pixelStride).toInt() and 0xFF) - 128
                    val cv = (v.buffer.get((py / 2) * v.rowStride + (px / 2) * v.pixelStride).toInt() and 0xFF) - 128
                    val rr = yy + 1.402 * cv
                    val gg = yy - 0.344136 * cu - 0.714136 * cv
                    val bb = yy + 1.772 * cu
                    if (yy >= 250 || rr >= 254 || gg >= 254 || bb >= 254) clipped++
                    sr += rr; sg += gg; sb += bb; n++
                }
                px += 2
            }
        }
        py += 2
    }
    if (n == 0) return RoiResult(0.0, 0.0, 0.0, 0.0, 0.0)
    val r = (sr / n / 255).coerceAtLeast(0.0)
    val g = (sg / n / 255).coerceAtLeast(0.0)
    val b = (sb / n / 255).coerceAtLeast(0.0)
    return RoiResult(r, g, b, clipped.toDouble() / n, (r + g + b) / 3)
}
