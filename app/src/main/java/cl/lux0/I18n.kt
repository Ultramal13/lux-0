package cl.lux0

import java.util.Locale

/**
 * Idioma de la app: el elegido en Ajustes, o en "auto" el del teléfono (español, japonés; inglés en cualquier otro caso).
 * Se lee en cada llamada; al cambiarlo se recrea la pantalla.
 */
object I18n {
    const val AUTO = "auto"
    val OPTIONS = listOf(AUTO, "es", "en", "ja")

    /** Solo para pruebas: fija "es", "en" o "ja". */
    @Volatile var force: String? = null

    /** Elegido en Ajustes. */
    @Volatile var choice: String = AUTO

    val code: String
        get() = force ?: choice.takeIf { it != AUTO && it in OPTIONS } ?: when (Locale.getDefault().language) {
            "es" -> "es"
            "ja" -> "ja"
            else -> "en"
        }

    /** Inglés o japonés: lo que no tiene versión japonesa se muestra en inglés. */
    val en: Boolean get() = code != "es"
}

/** Texto en el idioma actual. En japonés se busca la traducción del texto en inglés (`JA`); si falta, queda en inglés. */
fun tr(es: String, en: String): String = when (I18n.code) {
    "es" -> es
    "ja" -> JA[en] ?: en
    else -> en
}

/** Texto con las tres versiones a mano (textos largos o compuestos). */
fun tr(es: String, en: String, ja: String): String = when (I18n.code) {
    "es" -> es
    "ja" -> ja
    else -> en
}
