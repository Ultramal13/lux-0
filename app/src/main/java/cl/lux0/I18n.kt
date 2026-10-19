package cl.lux0

import java.util.Locale

/**
 * Idioma de la app: español si el teléfono está en español; inglés en cualquier otro caso.
 * Se lee en cada llamada, así un cambio de idioma del sistema se refleja al recrear la pantalla.
 */
object I18n {
    /** Solo para pruebas: fija "es" o "en". */
    @Volatile var force: String? = null

    val en: Boolean get() = (force ?: Locale.getDefault().language) != "es"
}

/** Texto en el idioma actual. */
fun tr(es: String, en: String): String = if (I18n.en) en else es
