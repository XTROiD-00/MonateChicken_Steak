package com.example.monatechickensteak.util

import java.util.Calendar

/** Checks card details typed at checkout. Each function returns an error message, or null when fine. */
object CardValidator {

    fun digitsOnly(text: String): String = text.filter { it.isDigit() }

    /** The Luhn check every real card number passes. */
    fun passesLuhn(number: String): Boolean {
        val digits = digitsOnly(number)
        if (digits.isEmpty()) return false
        var sum = 0
        var double = false
        for (i in digits.length - 1 downTo 0) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    fun nameError(name: String): String? =
        if (name.trim().length < 2) "Enter the name on the card" else null

    fun numberError(number: String): String? {
        val digits = digitsOnly(number)
        return when {
            digits.isEmpty() -> "Card number is required"
            digits.length !in 13..19 -> "Card number must have 13 to 19 digits"
            !passesLuhn(digits) -> "That card number is not valid"
            else -> null
        }
    }

    /** [expiry] is typed as MM/YY. [nowYear] and [nowMonth] are passed in so tests do not depend on today. */
    fun expiryError(
        expiry: String,
        nowYear: Int = Calendar.getInstance().get(Calendar.YEAR),
        nowMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1
    ): String? {
        val parts = expiry.trim().split("/")
        if (parts.size != 2) return "Use the format MM/YY"
        val month = parts[0].toIntOrNull()
        val yy = parts[1].toIntOrNull()
        if (month == null || yy == null || parts[0].length != 2 || parts[1].length != 2) return "Use the format MM/YY"
        if (month !in 1..12) return "Month must be 01 to 12"
        val year = 2000 + yy
        return if (year < nowYear || (year == nowYear && month < nowMonth)) "This card has expired" else null
    }

    fun cvvError(cvv: String): String? =
        if (cvv.length in 3..4 && cvv.all { it.isDigit() }) null else "CVV is 3 or 4 digits"

    /** Only this is ever kept: the last four digits, for the receipt. */
    fun last4(number: String): String = digitsOnly(number).takeLast(4)

    /** Adds a space after every four digits while the customer types. */
    fun groupNumber(raw: String): String =
        digitsOnly(raw).take(19).chunked(4).joinToString(" ")
}
