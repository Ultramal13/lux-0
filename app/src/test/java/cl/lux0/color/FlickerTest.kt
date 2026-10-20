package cl.lux0.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FlickerTest {
    private val rowTime = 33e-6   // ~16 ms por cuadro de 480 filas

    private fun rows(hz: Double, depth: Double, gradient: Double = 0.0) =
        DoubleArray(480) { i -> 100.0 * (1 + depth / 2 * sin(2 * PI * hz * i * rowTime)) + gradient * i }

    @Test fun detects100Hz() {
        val r = Flicker.detect(rows(100.0, 0.2), rowTime)
        assertNotNull(r)
        assertEquals(100.0, r!!.hz, 0.5)
        assertEquals(0.2, r.depth, 0.05)
    }

    @Test fun detects120HzWithGradient() {
        val r = Flicker.detect(rows(120.0, 0.15, gradient = 0.05), rowTime)
        assertEquals(120.0, r!!.hz, 0.5)
    }

    @Test fun steadyLightIsNull() {
        assertNull(Flicker.detect(rows(100.0, 0.0, gradient = 0.03), rowTime))
    }

    @Test fun safety() {
        assertTrue(Flicker.isSafe(1 / 50.0, 100.0))
        assertTrue(Flicker.isSafe(1 / 100.0, 100.0))
        assertFalse(Flicker.isSafe(1 / 60.0, 100.0))
        assertFalse(Flicker.isSafe(1 / 48.0, 100.0))
        assertTrue(Flicker.isSafe(1 / 60.0, 120.0))
    }

    @Test fun safeShuttersAt24p() {
        assertEquals(listOf("1/25" to "345.6°", "1/33.3" to "259.2°", "1/50" to "172.8°"), Flicker.safeShutters(24.0, 100.0))
        assertTrue(Flicker.safeShutters(120.0, 100.0).isEmpty())
    }
}
