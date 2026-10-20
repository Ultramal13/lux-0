package cl.lux0.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightMapTest {
    // Grilla del sensor 3×2: valores = índice, para seguir dónde cae cada celda.
    private val buf = doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)

    @Test fun rotate90() {
        val m = LightMap.toDisplay(buf, 3, 2, 90)
        assertEquals(2, m.cols); assertEquals(3, m.rows)
        // Con giro horario, la esquina superior izquierda del sensor (0) queda arriba a la derecha.
        assertEquals(0.0, m.at(1, 0), 0.0)
        assertEquals(3.0, m.at(0, 0), 0.0)
        assertEquals(5.0, m.at(0, 2), 0.0)
    }

    @Test fun rotate270() {
        val m = LightMap.toDisplay(buf, 3, 2, 270)
        // Con giro antihorario, el 0 queda abajo a la izquierda.
        assertEquals(0.0, m.at(0, 2), 0.0)
        assertEquals(2.0, m.at(0, 0), 0.0)
    }

    @Test fun warmthSign() {
        assertTrue(LightMap.warmth(3200.0, 5600.0) > 0)
        assertTrue(LightMap.warmth(6500.0, 5600.0) < 0)
        assertEquals(1.0, LightMap.strength(150.0), 0.0)
        assertEquals(0.5, LightMap.strength(-50.0), 1e-9)
    }
}
