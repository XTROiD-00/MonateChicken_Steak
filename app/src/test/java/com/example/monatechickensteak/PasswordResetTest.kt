package com.example.monatechickensteak

import com.example.monatechickensteak.data.AuthRepository
import com.example.monatechickensteak.data.AuthResult
import com.example.monatechickensteak.data.ResetRequest
import com.example.monatechickensteak.util.Validators
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PasswordResetTest {

    private lateinit var users: FakeUserStore
    private var now = 1_000_000L

    @Before
    fun setUp() {
        users = FakeUserStore()
        AuthRepository.store = users
        AuthRepository.clock = { now }
    }

    @After
    fun tearDown() {
        AuthRepository.clock = { System.currentTimeMillis() }
    }

    private suspend fun registered() = AuthRepository.register("Test User", "test@monate.co.za", "Old@Pass1")

    private suspend fun codeFor(email: String) =
        (AuthRepository.requestPasswordReset(email) as ResetRequest.CodeCreated).code

    @Test fun unknownEmailIsRejected() = runTest {
        assertTrue(AuthRepository.requestPasswordReset("nobody@monate.co.za") is ResetRequest.Error)
    }

    @Test fun googleAccountsCannotReset() = runTest {
        AuthRepository.signInWithGoogle("g@monate.co.za", "G User")
        assertTrue(AuthRepository.requestPasswordReset("g@monate.co.za") is ResetRequest.Error)
    }

    @Test fun codeHasSixDigits() = runTest {
        registered()
        val code = codeFor("test@monate.co.za")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test fun correctCodeChangesThePassword() = runTest {
        registered()
        val code = codeFor("test@monate.co.za")
        assertTrue(AuthRepository.resetPassword("test@monate.co.za", code, "New@Pass2") is AuthResult.Success)
        assertTrue(AuthRepository.login("test@monate.co.za", "New@Pass2") is AuthResult.Success)
        assertTrue(AuthRepository.login("test@monate.co.za", "Old@Pass1") is AuthResult.Error)
    }

    @Test fun codeWorksOnlyOnce() = runTest {
        registered()
        val code = codeFor("test@monate.co.za")
        AuthRepository.resetPassword("test@monate.co.za", code, "New@Pass2")
        assertTrue(AuthRepository.resetPassword("test@monate.co.za", code, "Other@Pass3") is AuthResult.Error)
    }

    @Test fun wrongCodeIsRejectedAndPasswordStaysTheSame() = runTest {
        registered()
        val code = codeFor("test@monate.co.za")
        val wrong = if (code == "000000") "111111" else "000000"
        assertTrue(AuthRepository.resetPassword("test@monate.co.za", wrong, "New@Pass2") is AuthResult.Error)
        assertTrue(AuthRepository.login("test@monate.co.za", "Old@Pass1") is AuthResult.Success)
    }

    @Test fun expiredCodeIsRejected() = runTest {
        registered()
        val code = codeFor("test@monate.co.za")
        now += 11 * 60 * 1000L
        assertTrue(AuthRepository.resetPassword("test@monate.co.za", code, "New@Pass2") is AuthResult.Error)
    }

    @Test fun fiveWrongTriesBurnTheCode() = runTest {
        registered()
        val code = codeFor("test@monate.co.za")
        val wrong = if (code == "000000") "111111" else "000000"
        repeat(5) { AuthRepository.resetPassword("test@monate.co.za", wrong, "New@Pass2") }
        assertTrue(AuthRepository.resetPassword("test@monate.co.za", code, "New@Pass2") is AuthResult.Error)
    }

    @Test fun resetWithoutRequestingACodeFails() = runTest {
        registered()
        assertTrue(AuthRepository.resetPassword("test@monate.co.za", "123456", "New@Pass2") is AuthResult.Error)
    }

    @Test fun codeFormatIsValidated() {
        assertNotNull(Validators.resetCodeError(""))
        assertNotNull(Validators.resetCodeError("12345"))
        assertNotNull(Validators.resetCodeError("12a456"))
        assertNull(Validators.resetCodeError("123456"))
    }
}
