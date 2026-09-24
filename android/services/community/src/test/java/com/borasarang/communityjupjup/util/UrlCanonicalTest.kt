package com.borasarang.communityjupjup.util

import com.borasarang.communityjupjup.util.text.UrlCanonical
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlCanonicalTest {

    @Test
    fun `휘발 파라미터 제거`() {
        assertEquals(
            "https://www.clien.net/service/board/park/19267244",
            UrlCanonical.normalize("https://www.clien.net/service/board/park/19267244?od=T31&po=0&category=0&groupCd="),
        )
        assertEquals(
            "https://bbs.ruliweb.com/best/board/300143/read/76750367",
            UrlCanonical.normalize("https://bbs.ruliweb.com/best/board/300143/read/76750367?m=humor_only&t=now"),
        )
    }

    @Test
    fun `식별 파라미터 유지`() {
        assertEquals(
            "https://www.fmkorea.com/index.php?document_srl=123&mid=humor",
            UrlCanonical.normalize("https://www.fmkorea.com/index.php?mid=humor&document_srl=123&page=2"),
        )
    }

    @Test
    fun `쿼리 없음·빈값 처리`() {
        assertEquals("https://a.com/x", UrlCanonical.normalize("https://a.com/x"))
        assertEquals("https://a.com/x", UrlCanonical.normalize("https://a.com/x?po=1"))
        assertEquals("", UrlCanonical.normalize(""))
    }
}
