package com.example.monatechickensteak.data

import kotlinx.coroutines.delay

data class CardDetails(val name: String, val number: String, val expiry: String, val cvv: String)

sealed class PaymentResult {
    data class Approved(val reference: String, val last4: String) : PaymentResult()
    data class Declined(val reason: String) : PaymentResult()
}

/**
 * The one place the app talks to a payment provider. The screens only use this interface, so a real
 * provider (PayFast, Yoco, Stripe, ...) can replace [MockPaymentGateway] without touching the screens.
 * A real provider must be called from a server or use the provider's own SDK, so that card numbers
 * never sit in the app and the secret keys stay off the phone.
 */
interface PaymentGateway {
    suspend fun charge(amountInRand: Double, card: CardDetails): PaymentResult
}

/**
 * A pretend gateway for the demo: nothing is charged. Test cards:
 *  - 4242 4242 4242 4242 is approved
 *  - 4000 0000 0000 0002 is declined (card declined)
 *  - 4000 0000 0000 9995 is declined (insufficient funds)
 */
class MockPaymentGateway(private val delayMs: Long = 1500) : PaymentGateway {

    override suspend fun charge(amountInRand: Double, card: CardDetails): PaymentResult {
        delay(delayMs)
        if (amountInRand <= 0) return PaymentResult.Declined("Nothing to pay")
        val digits = card.number.filter { it.isDigit() }
        return when (digits) {
            "4000000000000002" -> PaymentResult.Declined("Your card was declined")
            "4000000000009995" -> PaymentResult.Declined("Insufficient funds")
            else -> PaymentResult.Approved(
                reference = "MOCK-" + System.currentTimeMillis().toString(36).uppercase(),
                last4 = digits.takeLast(4)
            )
        }
    }
}

object Payments {
    /** Swap this for a real gateway when one is available. */
    var gateway: PaymentGateway = MockPaymentGateway()
}
