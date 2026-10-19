package cl.lux0.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CaptureOptionsTest {
    @Before fun spanish() { cl.lux0.I18n.force = "es" }

    @Test fun isoCutAtCameraMax() {
        assertEquals(51200, CaptureOptions.isoFor(51200).last())
        assertEquals(6400, CaptureOptions.isoFor(6400).last())
        assertEquals(100, CaptureOptions.isoFor(null).first())
    }

    @Test fun labels() {
        assertEquals("23,976", CaptureOptions.fpsLabel(23.976))
        assertEquals("24", CaptureOptions.fpsLabel(24.0))
        assertEquals("f/2.8", CaptureOptions.fLabel(2.8))
        assertEquals("f/8", CaptureOptions.fLabel(8.0))
        assertEquals("–", CaptureOptions.fLabel(null))
        assertEquals("nd8 · 3 pasos", CaptureOptions.ndLabel("nd8"))
        assertEquals("sin nd", CaptureOptions.ndLabel(""))
        assertEquals("+1⅓", CaptureOptions.evLabel(4 / 3.0))
        assertEquals("−⅔", CaptureOptions.evLabel(-2 / 3.0))
        assertEquals("0", CaptureOptions.evLabel(0.0))
        assertEquals(25, CaptureOptions.EV.size)
    }

    @Test fun englishLabels() {
        cl.lux0.I18n.force = "en"
        assertEquals("nd8 · 3 stops", CaptureOptions.ndLabel("nd8"))
        assertEquals("no nd", CaptureOptions.ndLabel(""))
        cl.lux0.I18n.force = "es"
    }
}
