package cl.lux0.color

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Asistente de exposición: con la luz medida, el lente, la estabilización, el sujeto, el soporte y la
 * intención, propone la combinación obturación · diafragma · ISO para una cámara.
 *
 * Foto: la obturación más lenta permitida es la más exigente entre el sujeto (congelar movimiento) y
 * el pulso (1/(focal × formato), con los pasos de estabilización a favor). Se prefiere el ISO más bajo.
 * Video: manda la obturación de la cámara (regla de 180°) y se resuelve el ISO o el ND.
 */
object ExposureAssistant {
    enum class Subject(val maxShutterSec: Double) {
        STATIC(Double.POSITIVE_INFINITY), PERSON(1 / 250.0), KIDS_PETS(1 / 500.0), SPORT(1 / 1000.0), FAST(1 / 2000.0)
    }

    enum class Note { NEEDS_APERTURE, UNDEREXPOSED, OVEREXPOSED, HANDHELD_LIMIT, SUBJECT_LIMIT }

    data class Input(
        val sceneEv100: Double,
        val fStop: Double?,
        val ndStops: Int,
        val isoMax: Int,
        /** Intención en pasos: + más claro (clave alta), − más oscuro (clave baja). */
        val intentEv: Double,
        val video: Boolean,
        val fps: Double,
        /** Obturación de la cámara en segundos (video); null = auto. */
        val cameraShutterSec: Double?,
        val subject: Subject,
        val tripod: Boolean,
        val focalMm: Int?,
        val crop: Double?,
        val stabStops: Int,
    )

    data class Result(
        val shutterSec: Double,
        val fStop: Double,
        val iso: Int,
        /** Pasos que faltan (+) o sobran (−) aun con el ISO en su límite; 0 si calza. */
        val stopsOff: Double,
        /** Diafragma o ND que lo arreglaría, si aplica. */
        val fixAperture: Double? = null,
        val fixNdStops: Int? = null,
        val notes: Set<Note> = emptySet(),
    )

    private fun log2(x: Double) = ln(x) / ln(2.0)

    /** Velocidades estándar en tercios, de 1/8000 a 30 s. */
    val SHUTTERS: List<Double> = (-39..15).map { 2.0.pow(it / 3.0) }

    /** "1/250", "1/8", "0,5 s", "2 s"… con los valores nominales habituales. */
    fun shutterLabel(t: Double): String {
        if (t >= 0.4) {
            val s = if (t >= 2) Math.round(t).toDouble() else Math.round(t * 10) / 10.0
            return (if (s == Math.floor(s)) "${s.toInt()}" else "$s".replace('.', ',')) + " s"
        }
        val nominal = listOf(8000, 6400, 5000, 4000, 3200, 2500, 2000, 1600, 1250, 1000, 800, 640, 500, 400, 320, 250,
            200, 160, 125, 100, 80, 60, 50, 40, 30, 25, 20, 15, 13, 10, 8, 6, 5, 4, 3)
        val d = nominal.minBy { abs(log2(it.toDouble()) - log2(1 / t)) }
        return "1/$d"
    }

    private val ISOS = listOf(100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000,
        5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600, 32000, 40000, 51200, 64000, 80000, 102400)

    private fun roundIsoUp(iso: Double): Int = ISOS.firstOrNull { it >= iso * 0.94 } ?: ISOS.last()
    private fun nearestShutter(t: Double) = SHUTTERS.minBy { abs(log2(it) - log2(t)) }

    /** Obturación más lenta que se puede usar a pulso (null = sin límite, trípode o sin lente). */
    fun handheldLimit(focalMm: Int?, crop: Double?, stabStops: Int, tripod: Boolean): Double? {
        if (tripod || focalMm == null || focalMm <= 0) return null
        val eq = focalMm * (crop ?: 1.0)
        return (1.0 / eq * 2.0.pow(stabStops)).coerceAtMost(0.5)   // nunca más de 1/2 s a pulso
    }

    fun suggest(i: Input): Result? {
        val n = i.fStop ?: return null
        // EV efectivo: el ND quita luz y la intención positiva pide más exposición.
        val ev = i.sceneEv100 - i.ndStops - i.intentEv
        val isoMax = i.isoMax.coerceAtLeast(100)
        val notes = mutableSetOf<Note>()
        if (i.video) {
            val t = i.cameraShutterSec ?: (1.0 / (2 * i.fps))
            val isoNeed = 100 * 2.0.pow(log2(n * n / t) - ev)
            return when {
                isoNeed > isoMax * 1.06 -> {
                    val off = log2(isoNeed / isoMax)
                    Result(t, n, isoMax, off, fixAperture = Exposure.nearestStop(n / sqrt(2.0.pow(off))), notes = setOf(Note.UNDEREXPOSED))
                }
                isoNeed < 100 / 1.06 -> {
                    val off = log2(isoNeed / 100)                       // negativo: sobra luz
                    Result(t, n, 100, off, fixNdStops = Math.ceil(-off - 0.17).toInt().coerceAtLeast(1), notes = setOf(Note.OVEREXPOSED))
                }
                else -> Result(t, n, roundIsoUp(isoNeed), 0.0)
            }
        }
        // Foto: obturación más lenta permitida.
        val hand = handheldLimit(i.focalMm, i.crop, i.stabStops, i.tripod)
        val subj = i.subject.maxShutterSec
        var tMax = minOf(hand ?: Double.POSITIVE_INFINITY, subj, 30.0)
        if (hand != null && hand <= subj) notes += Note.HANDHELD_LIMIT else if (subj.isFinite()) notes += Note.SUBJECT_LIMIT
        val tAt100 = n * n / 2.0.pow(ev)
        if (tAt100 <= tMax) {
            val t = nearestShutter(tAt100)
            if (t < 1 / 8000.0 * 1.06 && tAt100 < 1 / 8000.0) {
                val off = log2(tAt100 * 8000)                             // negativo: sobra luz
                return Result(1 / 8000.0, n, 100, off, fixNdStops = Math.ceil(-off - 0.17).toInt().coerceAtLeast(1),
                    notes = notes + Note.OVEREXPOSED)
            }
            return Result(t, n, 100, 0.0, notes = notes)
        }
        tMax = nearestShutter(tMax).let { if (it > tMax * 1.06) SHUTTERS.last { s -> s <= tMax * 1.06 } else it }
        val isoNeed = 100 * tAt100 / tMax
        if (isoNeed <= isoMax * 1.06) return Result(tMax, n, roundIsoUp(isoNeed), 0.0, notes = notes)
        val off = log2(isoNeed / isoMax)
        return Result(tMax, n, isoMax, off, fixAperture = Exposure.nearestStop(n / sqrt(2.0.pow(off))), notes = notes + Note.UNDEREXPOSED)
    }
}
