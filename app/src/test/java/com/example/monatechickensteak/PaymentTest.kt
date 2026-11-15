package com.example.monatechickensteak

import com.example.monatechickensteak.data.CardDetails
import com.example.monatechickensteak.data.MockPaymentGateway
import com.example.monatechickensteak.data.PaymentResult
import com.example.monatechickensteak.util.CardValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardValidatorTest {

    @Test fun testCardPassesLuhn() = assertTrue(CardValidator.passesLuhn("4242 4242 4242 4242"))

    @Test fun changedDigitFailsLuhn() = assertFalse(CardValidator.passesLuhn("4242 4242 4242 4243"))

    @Test fun validNumberAccepted() = assertNull(CardValidator.numberError("4242424242424242"))

    @Test fun shortNumberRejected() = assertNotNull(CardValidator.numberError("4242"))

    @Test fun emptyNumberRejected() = assertNotNull(CardValidator.numberError(""))

    @Test fun futureExpiryAccepted() = assertNull(CardValidator.expiryError("12/30", 2026, 11))

    @Test fun sameMonthIsStillValid() = assertNull(CardValidator.expiryError("11/26", 2026, 11))

    @Test fun pastExpiryRejected() {
        assertNotNull(CardValidator.expiryError("10/26", 2026, 11))
        assertNotNull(CardValidator.expiryError("12/25", 2026, 11))
    }

    @Test fun badExpiryFormatRejected() {
        assertNotNull(CardValidator.expiryError("1230", 2026, 11))
        assertNotNull(CardValidator.expiryError("13/30", 2026, 11))
        assertNotNull(CardValidator.expiryError("1/30", 2026, 11))
    }

    @Test fun cvvMustBeThreeOrFourDigits() {
        assertNull(CardValidator.cvvError("123"))
        assertNull(CardValidator.cvvError("1234"))
        assertNotNull(CardValidator.cvvError("12"))
        assertNotNull(CardValidator.cvvError("12a"))
    }

    @Test fun nameIsRequired() {
        assertNotNull(CardValidator.nameError(" "))
        assertNull(CardValidator.nameError("T Nkosi"))
    }

    @Test fun onlyLastFourDigitsAreKept() = assertEquals("4242", CardValidator.last4("4242 4242 4242 4242"))

    @Test fun numberIsGroupedInFours() =
        assertEquals("4242 4242 42", CardValidator.groupNumber("424242424242"))
}

class MockPaymentGatewayTest {

    private val gateway = MockPaymentGateway(delayMs = 0)
    private fun card(number: String) = CardDetails("T Nkosi", number, "12/30", "123")

    @Test fun approvedCardGivesAReference() = runTest {
        val result = gateway.charge(100.0, card("4242 4242 4242 4242"))
        assertTrue(result is PaymentResult.Approved)
        assertEquals("4242", (result as PaymentResult.Approved).last4)
    }

    @Test fun declinedTestCardIsDeclined() = runTest {
        assertTrue(gateway.charge(100.0, card("4000 0000 0000 0002")) is PaymentResult.Declined)
    }

    @Test fun insufficientFundsTestCardIsDeclined() = runTest {
        val result = gateway.charge(100.0, card("4000000000009995"))
        assertEquals("Insufficient funds", (result as PaymentResult.Declined).reason)
    }

    @Test fun zeroAmountIsDeclined() = runTest {
        assertTrue(gateway.charge(0.0, card("4242424242424242")) is PaymentResult.Declined)
    }
}
