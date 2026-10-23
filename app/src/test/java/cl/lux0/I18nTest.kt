package cl.lux0

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class I18nTest {
    @After fun reset() { I18n.force = null; I18n.choice = I18n.AUTO }

    @Test fun japaneseUsesDictionaryAndFallsBackToEnglish() {
        I18n.force = "ja"
        assertEquals("キャンセル", tr("cancelar", "cancel"))
        assertEquals("text not in the dictionary", tr("texto sin traducir", "text not in the dictionary"))
        assertEquals("日本語", tr("español", "english", "日本語"))
    }

    @Test fun choiceOverridesPhoneLanguage() {
        I18n.choice = "es"
        assertEquals("cancelar", tr("cancelar", "cancel"))
        I18n.choice = "en"
        assertEquals("cancel", tr("cancelar", "cancel"))
        I18n.choice = "ja"
        assertEquals("ja", I18n.code)
    }

    @Test fun everyEntryIsTranslated() {
        assertEquals(true, JA.values.none { it.isBlank() })
    }
}
