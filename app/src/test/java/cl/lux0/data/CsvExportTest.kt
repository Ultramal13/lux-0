package cl.lux0.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CsvExportTest {
    @Before fun spanish() { cl.lux0.I18n.force = "es" }

    private fun m(comment: String) = Measurement(
        1, 7, 0L, "blanco/gris", 4550.4, -0.005, "canon r5", "4600 · g2", "insta360 luna ultra", "4600",
        CaptureSettings(800, 24.0, "1/50", 4.0, "nd 0.6"), CaptureSettings(), null, comment,
    )

    @Test fun header() {
        val lines = toCsv(emptyList(), emptyMap()).lines()
        assertEquals(CSV_HEADER, lines[0])
    }

    @Test fun escapesSemicolonAndQuotes() {
        val csv = toCsv(listOf(m("luz \"cálida\"; ventana")), mapOf(7L to "día 1"))
        val row = csv.lines()[1]
        assertTrue(row.endsWith(";\"luz \"\"cálida\"\"; ventana\""))
        assertTrue(row.contains(";día 1;blanco/gris;4550;-0.0050;canon r5;"))
        assertTrue(row.contains(";800;24;1/50;4.0;nd 0.6;0;"))
    }

    @Test fun englishHeader() {
        cl.lux0.I18n.force = "en"
        assertEquals(CSV_HEADER_EN, toCsv(emptyList(), emptyMap()).lines()[0])
        cl.lux0.I18n.force = "es"
    }

    @Test fun formulasAreNeutralized() {
        val row = toCsv(listOf(m("=HYPERLINK(\"x\")")), mapOf(7L to "+sesion")).lines()[1]
        assertTrue(row.contains(";'+sesion;"))
        assertTrue(row.endsWith(";\"'=HYPERLINK(\"\"x\"\")\""))
        assertTrue(row.contains(";-0.0050;"))   // los números negativos no se tocan
    }
}
