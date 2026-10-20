package cl.lux0.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExposureGelsTest {
    @Test fun ev100() {
        assertEquals(8.88, Exposure.ev100(2.8, 1 / 60.0, 100), 0.02)
        assertEquals(7.88, Exposure.ev100(2.8, 1 / 60.0, 200), 0.02)
    }

    @Test fun sceneEvForGrayAndWhite() {
        assertEquals(9.0, Exposure.sceneEv(10.0, 18), 1e-9)
        assertEquals(9.0 - 2.32, Exposure.sceneEv(10.0, 90), 0.01)
    }

    @Test fun shutters() {
        assertEquals(0.02, Exposure.shutterSeconds("1/50", 24.0)!!, 1e-9)
        assertEquals(1 / 48.0, Exposure.shutterSeconds("180°", 24.0)!!, 1e-9)
        assertNull(Exposure.shutterSeconds("auto", 24.0))
    }

    @Test fun apertureSuggestion() {
        val n = Exposure.apertureFor(9.0, 800, 0.02, 0, 0.0)
        assertEquals(9.05, n, 0.05)
        assertEquals(9.0, Exposure.nearestStop(n), 1e-9)
        // nd16 = 4 pasos menos: el diafragma se abre 2 números (≈ f/2.2)
        assertEquals(2.2, Exposure.nearestStop(Exposure.apertureFor(9.0, 800, 0.02, 4, 0.0)), 1e-9)
    }

    @Test fun stopsOffWithFixedAperture() {
        // pide f/9 pero la cámara está en f/4.5 → sobran 2 pasos
        assertEquals(2.0, Exposure.stopsOff(9.0, 800, 0.02, 0, 0.0, 4.5), 0.05)
    }

    @Test fun gels() {
        assertEquals("full ctb", Gels.suggest(3200.0, 0.0, 5600.0).temp?.name)
        assertEquals("full cto", Gels.suggest(5600.0, 0.0, 2900.0).temp?.name)
        assertEquals("¾ cto", Gels.suggest(5600.0, 0.0, 3200.0).temp?.name)
        val g = Gels.suggest(5600.0, -0.008, 5600.0)
        assertNull(g.temp)
        assertEquals("¼ plus green", g.green?.name)
    }
}
