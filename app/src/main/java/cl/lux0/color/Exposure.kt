package cl.lux0.color

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Exposición reflejada: del EV que eligió el teléfono a lo que necesita cada cámara. */
object Exposure {
    /** Diafragmas en tercios de paso (los mismos de la ventana de captura). */
    val F_STOPS = listOf(
        1.2, 1.4, 1.6, 1.8, 2.0, 2.2, 2.5, 2.8, 3.2, 3.5, 4.0, 4.5, 5.0, 5.6, 6.3, 7.1, 8.0,
        9.0, 10.0, 11.0, 13.0, 14.0, 16.0, 18.0, 20.0, 22.0,
    )

    private fun log2(x: Double) = ln(x) / ln(2.0)

    /** EV a ISO 100 de los ajustes del teléfono (boost en %, 100 = sin ganancia extra). */
    fun ev100(aperture: Double, tSec: Double, iso: Int, boostPct: Int = 100): Double =
        log2(aperture * aperture / tSec) - log2(iso / 100.0) - log2(boostPct / 100.0)

    /**
     * EV de la escena: el teléfono mide el círculo con −1 EV de compensación, así que el gris medio
     * queda 1 paso arriba; además se corrige por la reflectancia de lo que se apunta (18 % = gris).
     */
    fun sceneEv(phoneEv100: Double, reflectancePct: Int, compensationEv: Double = -1.0, calibrationEv: Double = 0.0): Double =
        phoneEv100 + compensationEv - log2(reflectancePct / 18.0) + calibrationEv

    /** "1/50" → 0.02; "180°" con fps → 1/48; "auto" o desconocido → null. */
    fun shutterSeconds(shutter: String, fps: Double): Double? {
        val s = shutter.trim()
        return when {
            s.startsWith("1/") -> s.removePrefix("1/").toDoubleOrNull()?.let { 1.0 / it }
            s.endsWith("°") -> s.removeSuffix("°").toDoubleOrNull()?.let { if (fps > 0) it / 360.0 / fps else null }
            else -> null
        }
    }

    /** Diafragma exacto que expone bien la escena con estos ajustes (sin redondear). */
    fun apertureFor(sceneEv100: Double, iso: Int, tSec: Double, ndStops: Int, evComp: Double): Double {
        val ev = sceneEv100 + log2(iso / 100.0) - ndStops + evComp
        return sqrt(tSec * 2.0.pow(ev))
    }

    fun nearestStop(n: Double): Double = F_STOPS.minBy { abs(log2(it / n)) }

    /**
     * Diferencia en pasos entre lo que hay y lo que se necesita con un diafragma fijo:
     * positivo = sobra luz (cerrar, más nd), negativo = falta luz.
     */
    fun stopsOff(sceneEv100: Double, iso: Int, tSec: Double, ndStops: Int, evComp: Double, fStop: Double): Double =
        2 * log2(apertureFor(sceneEv100, iso, tSec, ndStops, evComp) / fStop)
}
