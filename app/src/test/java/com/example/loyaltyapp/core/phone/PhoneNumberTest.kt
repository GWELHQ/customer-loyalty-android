package com.example.loyaltyapp.core.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberTest {

    @Test
    fun `normalizes leading zero form`() {
        val result = PhoneNumber.normalize("0722481903")
        assertEquals("+254722481903", result?.e164)
        assertEquals("+254 722 481 903", result?.displayGrouped)
    }

    @Test
    fun `normalizes bare national number`() {
        val result = PhoneNumber.normalize("722481903")
        assertEquals("+254722481903", result?.e164)
    }

    @Test
    fun `normalizes E164 form`() {
        val result = PhoneNumber.normalize("+254722481903")
        assertEquals("+254722481903", result?.e164)
    }

    @Test
    fun `normalizes 254 prefixed form without plus`() {
        val result = PhoneNumber.normalize("254722481903")
        assertEquals("+254722481903", result?.e164)
    }

    @Test
    fun `tolerates spaces and dashes`() {
        val result = PhoneNumber.normalize("0722-481-903")
        assertEquals("+254722481903", result?.e164)
    }

    @Test
    fun `rejects too-short numbers`() {
        assertNull(PhoneNumber.normalize("07224819"))
    }

    @Test
    fun `rejects too-long numbers`() {
        assertNull(PhoneNumber.normalize("072248190312"))
    }

    @Test
    fun `rejects non-mobile prefix`() {
        assertNull(PhoneNumber.normalize("0522481903"))
    }

    @Test
    fun `accepts 01 mobile range`() {
        val result = PhoneNumber.normalize("0100481903")
        assertEquals("+254100481903", result?.e164)
    }

    @Test
    fun `isValid reflects normalize result`() {
        assert(PhoneNumber.isValid("0722481903"))
        assert(!PhoneNumber.isValid("123"))
    }

    @Test
    fun `formatPartial groups digits as typed`() {
        assertEquals("722", PhoneNumber.formatPartial("722"))
        assertEquals("722 481", PhoneNumber.formatPartial("722481"))
        assertEquals("722 481 903", PhoneNumber.formatPartial("722481903"))
    }
}
