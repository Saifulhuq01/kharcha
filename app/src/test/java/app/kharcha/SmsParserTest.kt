package app.kharcha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun auBankDebit() {
        val p = SmsParser.parse("Dr INR 160.00 - AU A/c XXXX1234 12-NOV-26")!!
        assertEquals(160.0, p.amount, 0.001)
    }

    @Test
    fun creditIsIgnored() {
        assertNull(SmsParser.parse("Credited INR 3,093.35 to A/c XXXX1234 on 13-NOV-26 Ref NEFT. Bal INR 13,521.58."))
    }

    @Test
    fun upiPaymentGetsMerchantAndCategory() {
        val p = SmsParser.parse("Rs.250.00 paid to Zomato via UPI Ref 123456")!!
        assertEquals(250.0, p.amount, 0.001)
        assertTrue(p.merchant!!.startsWith("Zomato"))
        assertEquals("Food", p.category)
    }

    @Test
    fun indianDigitGrouping() {
        val p = SmsParser.parse("INR 1,23,456.50 debited from your account")!!
        assertEquals(123456.5, p.amount, 0.001)
    }

    @Test
    fun messageWithoutAmountIsIgnored() {
        assertNull(SmsParser.parse("Your OTP for login is 482913. Do not share it."))
    }

    @Test
    fun categorizerGuessesAndFallsBack() {
        assertEquals("Travel", Categorizer.guess("UBER TRIP 4521"))
        assertEquals("Groceries", Categorizer.guess("Paid to Blinkit"))
        assertEquals("Other", Categorizer.guess("Dr INR 6.00 - AU A/c"))
    }
}
