package cl.lux0.color

import cl.lux0.color.ExposureAssistant.Input
import cl.lux0.color.ExposureAssistant.Note
import cl.lux0.color.ExposureAssistant.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureAssistantTest {
    private fun photo(ev: Double, f: Double?, subject: Subject, focal: Int? = 50, crop: Double? = 1.0, stab: Int = 0,
                      tripod: Boolean = false, isoMax: Int = 51200, intent: Double = 0.0, nd: Int = 0) =
        Input(ev, f, nd, isoMax, intent, false, 24.0, null, subject, tripod, focal, crop, stab)

    @Test fun brightDayPersonIso100() {
        val r = ExposureAssistant.suggest(photo(12.0, 4.0, Subject.PERSON))!!
        assertEquals("1/250", ExposureAssistant.shutterLabel(r.shutterSec))
        assertEquals(100, r.iso)
        assertEquals(0.0, r.stopsOff, 1e-9)
    }

    @Test fun indoorPersonRaisesIso() {
        val r = ExposureAssistant.suggest(photo(7.0, 2.8, Subject.PERSON, focal = 85))!!
        assertEquals("1/250", ExposureAssistant.shutterLabel(r.shutterSec))
        assertEquals(1600, r.iso)
        assertTrue(Note.SUBJECT_LIMIT in r.notes)
    }

    @Test fun darkSportIsUnderexposed() {
        val r = ExposureAssistant.suggest(photo(2.0, 2.8, Subject.SPORT, isoMax = 6400))!!
        assertEquals(6400, r.iso)
        assertEquals(4.9, r.stopsOff, 0.1)
        assertTrue(Note.UNDEREXPOSED in r.notes)
        assertEquals(1.2, r.fixAperture!!, 1e-9)
    }

    @Test fun tripodNightLongExposure() {
        val r = ExposureAssistant.suggest(photo(3.0, 8.0, Subject.STATIC, tripod = true))!!
        assertEquals("8 s", ExposureAssistant.shutterLabel(r.shutterSec))
        assertEquals(100, r.iso)
    }

    @Test fun intentAndHandheld() {
        // clave alta (+1) pide un paso más de luz que normal
        val normal = ExposureAssistant.suggest(photo(7.0, 2.8, Subject.PERSON, focal = 85))!!
        val high = ExposureAssistant.suggest(photo(7.0, 2.8, Subject.PERSON, focal = 85, intent = 1.0))!!
        assertEquals(normal.iso * 2, high.iso)
        assertEquals(0.1, ExposureAssistant.handheldLimit(50, 1.6, 3, false)!!, 1e-9)
        assertEquals(0.5, ExposureAssistant.handheldLimit(10, 1.0, 8, false)!!, 1e-9)
        assertNull(ExposureAssistant.handheldLimit(50, 1.0, 0, true))
    }

    @Test fun videoBrightSuggestsNd() {
        val r = ExposureAssistant.suggest(Input(13.0, 2.8, 0, 25600, 0.0, true, 24.0, 1 / 50.0, Subject.STATIC, false, 50, 1.0, 0))!!
        assertEquals(100, r.iso)
        assertTrue(Note.OVEREXPOSED in r.notes)
        assertEquals(5, r.fixNdStops)
    }

    @Test fun needsAperture() {
        assertNull(ExposureAssistant.suggest(photo(10.0, null, Subject.STATIC)))
    }

    @Test fun shutterLabels() {
        assertEquals("1/50", ExposureAssistant.shutterLabel(1 / 48.0))
        assertEquals("0,5 s", ExposureAssistant.shutterLabel(0.5))
        assertEquals("2 s", ExposureAssistant.shutterLabel(2.0))
    }

    // ISO fijo en la tarjeta: el asistente no lo cambia; ajusta obturación y avisa cuánto subir o bajar el ISO.
    private fun photoIso(ev: Double, f: Double, subject: Subject, iso: Int, focal: Int? = 50) =
        Input(ev, f, 0, 51200, 0.0, false, 24.0, null, subject, false, focal, 1.0, 0, iso = iso)

    @Test fun fixedIsoPhotoAdjustsShutter() {
        val r = ExposureAssistant.suggest(photoIso(12.0, 4.0, Subject.PERSON, 400))!!
        assertEquals(400, r.iso)
        assertEquals("1/1000", ExposureAssistant.shutterLabel(r.shutterSec))
        assertEquals(0.0, r.stopsOff, 0.17)
    }

    @Test fun fixedIsoPhotoKeepsIsoWhenDark() {
        val r = ExposureAssistant.suggest(photoIso(7.0, 2.8, Subject.PERSON, 100, focal = 85))!!
        assertEquals(100, r.iso)
        assertEquals("1/250", ExposureAssistant.shutterLabel(r.shutterSec))
        assertEquals(3.9, r.stopsOff, 0.1)
        assertTrue(Note.UNDEREXPOSED in r.notes)
        assertEquals(1600, r.fixIso)
    }

    @Test fun fixedIsoVideoKeepsIso() {
        val r = ExposureAssistant.suggest(Input(13.0, 2.8, 0, 25600, 0.0, true, 24.0, 1 / 50.0, Subject.STATIC, false, 50, 1.0, 0, iso = 800))!!
        assertEquals(800, r.iso)
        assertEquals(-7.4, r.stopsOff, 0.1)
        assertTrue(Note.OVEREXPOSED in r.notes)
        assertEquals(8, r.fixNdStops)
        assertEquals(100, r.fixIso)
    }
}
