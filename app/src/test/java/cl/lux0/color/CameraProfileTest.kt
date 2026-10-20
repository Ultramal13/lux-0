package cl.lux0.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val list = parseProfiles(java.io.File("src/main/assets/cameras.json").readText())
        assertTrue(list.size >= 40)
        assertEquals(list.size, list.map { it.id }.toSet().size)          // ids únicos
        assertTrue(list.any { it.id == "canon-r5" } && list.any { it.id == "insta360-luna-ultra" })
        assertTrue(list.all { it.kMin < it.kMax && it.source.isNotBlank() })
    }

    private fun json(s: String) = parseProfiles("[$s]").first()

    @Test fun nikonSnapsInMired() {
        val z = json("""{"id":"n","brand":"Nikon","model":"Z","kMin":2500,"kMax":10000,"kStep":10,"tint":"GM","kMode":"MIRED","tintMin":-1.5,"tintMax":1.5,"tintStep":0.25,"duvPerUnit":0.015}""")
        val r = z.recommend(4550.0, -0.008, 150.0)
        assertEquals(4550, r.kSet)            // 1e6/220 mired ≈ 4545 → 4550
        assertEquals("g0.5", r.tintLabel)     // 0.008/0.015 = 0.53 → 0.5
    }

    @Test fun goproPicksNearestPreset() {
        val g = json("""{"id":"g","brand":"GoPro","model":"H","kMin":2300,"kMax":6500,"kStep":100,"tint":"NONE","kMode":"LIST","kValues":[2300,2800,3200,4000,4500,5000,5500,6000,6500]}""")
        assertEquals(4500, g.recommend(4550.0, 0.0, 150.0).kSet)
        assertEquals(3200, g.recommend(3350.0, 0.0, 150.0).kSet)
    }

    @Test fun fujiRbAndBlackmagicTint() {
        val f = json("""{"id":"f","brand":"Fujifilm","model":"X","kMin":2500,"kMax":10000,"kStep":10,"tint":"RB"}""")
        assertEquals(4550, f.recommend(4553.0, 0.0, 150.0).kSet)
        assertEquals("r-2 b-2", f.recommend(4550.0, -0.005, 150.0).tintLabel)   // luz magenta → bajar R y B
        val b = json("""{"id":"b","brand":"Blackmagic","model":"P","kMin":2500,"kMax":10000,"kStep":50,"tint":"NUMERIC","tintMin":-50,"tintMax":50,"duvPerUnit":0.0005}""")
        assertEquals(4550, b.recommend(4560.0, 0.0, 150.0).kSet)
        assertEquals("tint -10", b.recommend(4550.0, -0.005, 150.0).tintLabel)
    }

    @Test fun sonyQuarterStepsAndPanasonicDirection() {
        val s = json("""{"id":"s","brand":"Sony","model":"A","kMin":2500,"kMax":9900,"kStep":100,"tint":"GM","tintMin":-7,"tintMax":7,"tintStep":0.25,"duvPerUnit":0.0032}""")
        assertEquals("g1.5", s.recommend(5000.0, -0.0048, 150.0).tintLabel)
        assertEquals(9900, s.recommend(12000.0, 0.0, 150.0).kSet)
        val p = json("""{"id":"p","brand":"Panasonic","model":"S","kMin":2500,"kMax":10000,"kStep":100,"tint":"GM_DIR"}""")
        assertEquals("→g", p.recommend(5000.0, -0.006, 150.0).tintLabel)
        assertEquals("0", p.recommend(5000.0, 0.001, 150.0).tintLabel)
    }
}
