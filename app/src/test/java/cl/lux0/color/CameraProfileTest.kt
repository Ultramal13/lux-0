package cl.lux0.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraProfileTest {
    private val r5 = CameraProfile("canon-r5", "Canon", "R5", 2500, 10000, 100, TintSystem.CANON_GM9, true, "")
    private val luna = CameraProfile("luna", "Insta360", "Luna Ultra", 2000, 10000, 100, TintSystem.NONE, false, "")

    @Test fun r5Magenta() {
        assertEquals(Recommendation(4600, 4400, 4700, "g2"), r5.recommend(4550.0, -0.005, 150.0))
    }

    @Test fun r5Green() {
        assertEquals("m3", r5.recommend(5600.0, 0.0075, 150.0).tintLabel)
    }

    @Test fun clampsToRange() {
        assertEquals(2500, r5.recommend(1500.0, 0.0, 150.0).kSet)
    }

    @Test fun lunaNoTint() {
        val r = luna.recommend(4550.0, 0.0, 150.0)
        assertEquals(4600, r.kSet)
        assertNull(r.tintLabel)
    }

    @Test fun parsesBundledJson() {
        val json = java.io.File("src/main/assets/cameras.json").readText()
        val list = parseProfiles(json)
        assertEquals(listOf("canon-r5", "insta360-luna-ultra"), list.map { it.id })
    }
}
