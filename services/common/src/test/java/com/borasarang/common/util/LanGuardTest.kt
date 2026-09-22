package com.borasarang.common.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.borasarang.common.server.LanGuard

class LanGuardTest {

    @Test
    fun `루프백_허용`() {
        assertTrue(LanGuard.isPrivateHost("127.0.0.1"))
        assertTrue(LanGuard.isPrivateHost("localhost"))
        assertTrue(LanGuard.isPrivateHost("::1"))
    }

    @Test
    fun `사설대역_허용`() {
        assertTrue(LanGuard.isPrivateHost("192.168.1.5"))
        assertTrue(LanGuard.isPrivateHost("10.0.0.2"))
        assertTrue(LanGuard.isPrivateHost("172.16.0.9"))
        assertTrue(LanGuard.isPrivateHost("172.31.255.1"))
        assertTrue(LanGuard.isPrivateHost("169.254.10.20"))
    }

    @Test
    fun `외부_차단`() {
        assertFalse(LanGuard.isPrivateHost("8.8.8.8"))
        assertFalse(LanGuard.isPrivateHost("172.15.0.1"))
        assertFalse(LanGuard.isPrivateHost("172.32.0.1"))
        assertFalse(LanGuard.isPrivateHost("11.0.0.1"))
        assertFalse(LanGuard.isPrivateHost(null))
        assertFalse(LanGuard.isPrivateHost(""))
    }
}
