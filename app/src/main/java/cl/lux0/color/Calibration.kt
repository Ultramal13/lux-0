package cl.lux0.color

/** Calibración de un punto: corrección en mired respecto de un K de referencia. */
object Calibration {
    fun offsetFor(measuredCct: Double, referenceK: Double): Double =
        ColorMath.mired(referenceK) - ColorMath.mired(measuredCct)

    fun apply(cct: Double, offsetMired: Double): Double =
        1e6 / (ColorMath.mired(cct) + offsetMired)
}
