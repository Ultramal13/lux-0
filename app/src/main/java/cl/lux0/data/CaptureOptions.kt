package cl.lux0.data

import cl.lux0.tr
/** Valores fijos para elegir en la ventana de captura (sin escribir a mano). */
object CaptureOptions {
    /** ISO en tercios de paso, de 100 a 51200. Se corta según el máximo de cada cámara. */
    val ISO = listOf(
        100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500,
        3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600, 32000, 40000, 51200,
    )

    val FPS = listOf(23.976, 24.0, 25.0, 29.97, 30.0, 48.0, 50.0, 59.94, 60.0, 100.0, 119.88, 120.0)

    val SHUTTER = listOf(
        "auto", "1/24", "1/25", "1/30", "1/48", "1/50", "1/60", "1/80", "1/96", "1/100", "1/120",
        "1/125", "1/160", "1/200", "1/240", "1/250", "1/320", "1/400", "1/500", "1/640", "1/800",
        "1/1000", "1/1250", "1/1600", "1/2000", "1/4000", "1/8000",
        "360°", "180°", "172.8°", "90°",
    )

    /** Diafragma en tercios de paso; null = no aplica (lente fijo). */
    val F_STOP: List<Double?> = listOf(
        null, 1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0,
        9.0, 10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0,
    )

    /** Filtros ND: nombre y pasos de luz que restan. */
    val ND: List<Pair<String, Int>> = listOf(
        "" to 0, "nd2" to 1, "nd4" to 2, "nd8" to 3, "nd16" to 4, "nd32" to 5, "nd64" to 6,
        "nd128" to 7, "nd256" to 8, "nd1000" to 10,
    )

    /** Compensación EV de −4 a +4 en tercios. */
    val EV: List<Double> = (-12..12).map { it / 3.0 }

    fun evLabel(ev: Double): String {
        val thirds = Math.round(ev * 3).toInt()
        if (thirds == 0) return "0"
        val sign = if (thirds > 0) "+" else "−"
        val a = Math.abs(thirds); val whole = a / 3
        val frac = when (a % 3) { 1 -> "⅓"; 2 -> "⅔"; else -> "" }
        return sign + (if (whole > 0) "$whole" else "") + frac
    }

    fun isoFor(max: Int?): List<Int> = if (max == null) ISO else ISO.filter { it <= max }

    fun fpsLabel(f: Double): String = when (f) {
        23.976 -> "23,976"; 29.97 -> "29,97"; 59.94 -> "59,94"; 119.88 -> "119,88"
        else -> f.toInt().toString()
    }

    fun fLabel(f: Double?): String = if (f == null) "–" else "f/" + (if (f == Math.floor(f)) f.toInt().toString() else f.toString())

    fun ndLabel(nd: String): String {
        val stops = ND.firstOrNull { it.first == nd }?.second ?: return nd.ifBlank { tr("sin nd", "no nd") }
        return if (nd.isBlank()) tr("sin nd", "no nd") else "$nd · $stops " + tr("pasos", "stops")
    }
}
