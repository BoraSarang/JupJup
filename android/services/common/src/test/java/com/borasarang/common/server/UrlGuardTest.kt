package com.borasarang.common.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlGuardTest {

    @Test
    fun `허용 판정`() {
        assertTrue(isAllowedThumbUrl("https://i3.ruliweb.com/img/a.webp"))
        assertTrue(isAllowedThumbUrl("http://todayhumor.co.kr/x.jpg"))
        assertFalse(isAllowedThumbUrl(""))
        assertFalse(isAllowedThumbUrl("ftp://a.com/x.jpg"))
        assertFalse(isAllowedThumbUrl("http://127.0.0.1:3003/x.jpg"))
        assertFalse(isAllowedThumbUrl("http://192.168.0.2/x.jpg"))
        assertFalse(isAllowedThumbUrl("not a url"))
        assertFalse(isAllowedThumbUrl("http://169.254.169.254/meta"))
        assertFalse(isAllowedThumbUrl("http://10.0.0.1/x.jpg"))
        assertFalse(isAllowedThumbUrl("http://metadata.google.internal/x"))
    }
}
