package com.borasarang.common.util

import com.borasarang.common.server.AdminAuth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAuthTest {

    @Test
    fun `루프백_판정`() {
        assertTrue(AdminAuth.isLoopback("127.0.0.1"))
        assertTrue(AdminAuth.isLoopback("localhost"))
        assertTrue(AdminAuth.isLoopback("::1"))
        assertFalse(AdminAuth.isLoopback("192.168.1.5"))
        assertFalse(AdminAuth.isLoopback("8.8.8.8"))
        assertFalse(AdminAuth.isLoopback(null))
    }
}
