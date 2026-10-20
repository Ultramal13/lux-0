package cl.lux0.color

import kotlin.math.abs

/** Geles genéricos (sin marcas) para igualar una luz con otra o con un objetivo. */
object Gels {
    data class Gel(val name: String, val mired: Double = 0.0, val duv: Double = 0.0)

    /** Temperatura: valores mired típicos publicados para CTO/CTB. */
    val TEMP = listOf(
        Gel("⅛ cto", 20.0), Gel("¼ cto", 42.0), Gel("½ cto", 81.0), Gel("¾ cto", 124.0), Gel("full cto", 159.0),
        Gel("⅛ ctb", -12.0), Gel("¼ ctb", -30.0), Gel("½ ctb", -68.0), Gel("¾ ctb", -100.0), Gel("full ctb", -131.0),
    )

    /** Verde/magenta: equivalencia en Duv aproximada. */
    val GREEN = listOf(
        Gel("⅛ plus green", duv = 0.004), Gel("¼ plus green", duv = 0.008), Gel("½ plus green", duv = 0.015), Gel("full plus green", duv = 0.03),
        Gel("⅛ minus green", duv = -0.004), Gel("¼ minus green", duv = -0.008), Gel("½ minus green", duv = -0.015), Gel("full minus green", duv = -0.03),
    )

    data class Suggestion(val temp: Gel?, val green: Gel?, val residualMired: Double, val residualDuv: Double) {
        val label: String get() = listOfNotNull(temp?.name, green?.name?.let { "≈$it" }).joinToString(" + ").ifEmpty { "—" }
    }

    /** Qué poner sobre la luz "from" para que se parezca a "to" (o al objetivo). */
    fun suggest(fromK: Double, fromDuv: Double, toK: Double, toDuv: Double = 0.0): Suggestion {
        val dMired = ColorMath.mired(toK) - ColorMath.mired(fromK)
        val dDuv = toDuv - fromDuv
        val temp = (listOf<Gel?>(null) + TEMP).minBy { abs(dMired - (it?.mired ?: 0.0)) }
        val green = (listOf<Gel?>(null) + GREEN).minBy { abs(dDuv - (it?.duv ?: 0.0)) }
        return Suggestion(temp, green, dMired - (temp?.mired ?: 0.0), dDuv - (green?.duv ?: 0.0))
    }
}
