package hu.elmdash.trip
import org.junit.Assert.*
import org.junit.Test
class ReceiptParserTest {
    @Test fun handlesOcrUppercaseIForLiter() {
        val r=ReceiptParser.parse("Benzin 95 E10\n20,00 I 600 Ft/l 12 000 Ft\nKave 800 Ft\nBenzin kedvezmeny -300 Ft\nOsszesen 12 500 Ft")
        assertEquals(20.0,r.liters!!,0.0); assertEquals(11700.0,r.paidHuf!!,0.0)
    }
    @Test fun excludesOtherItemsAndGrandTotal() {
        val r=ReceiptParser.parse("Benzin 95 E10\n20,00 l 600 Ft/l 12 000 Ft\nKávé 800 Ft\nÖsszesen 12 800 Ft")
        assertEquals(20.0,r.liters!!,0.0); assertEquals(12000.0,r.paidHuf!!,0.0)
    }
    @Test fun explicitFuelDiscountAndAmbiguousCoupon() {
        val text="Benzin 20 l 600 Ft/l 12000 Ft\nKávé 800 Ft\n"
        assertEquals(11700.0,ReceiptParser.parse(text+"Benzin kedvezmény -300 Ft").paidHuf!!,0.0)
        assertNull(ReceiptParser.parse(text+"Kupon -300 Ft\nVégösszeg 12500 Ft").paidHuf)
        assertNull(ReceiptParser.parse(text+"Benzin kedvezmény 5%").paidHuf)
    }
    @Test fun neverGuessesFromTotalOrMultipleFuelLines() {
        assertNull(ReceiptParser.parse("Kávé 500 Ft\nÖsszesen 10000 Ft").paidHuf)
        assertNull(ReceiptParser.parse("Benzin\nÖsszesen 10000 Ft").paidHuf)
        assertNull(ReceiptParser.parse("Benzin 10 l 6000 Ft\nDiesel 10 l 6500 Ft").liters)
    }
}
