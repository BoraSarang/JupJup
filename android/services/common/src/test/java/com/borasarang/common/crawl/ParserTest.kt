package com.borasarang.common.crawl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    @Test
    fun `TimeParser 상대시간 파싱`() {
        val now = System.currentTimeMillis()
        val min = TimeParser.parse("5분 전")!!
        assertTrue(min in now - 400_000..now)
        val hour = TimeParser.parse("3시간 전")!!
        assertTrue(hour in now - 11_000_000..now)
        val day = TimeParser.parse("2일 전")!!
        assertTrue(day in now - 175_000_000..now)
    }

    @Test
    fun `TimeParser 절댓값 파싱`() {
        assertNotNull(TimeParser.parse("2026-09-21 10:30"))
        assertNotNull(TimeParser.parse("2026.09.21"))
        assertNotNull(TimeParser.parse("10:23"))
        assertNotNull(TimeParser.parse("26/09/21"))
        assertNull(TimeParser.parse(null))
        assertNull(TimeParser.parse(""))
        assertNull(TimeParser.parse("???"))
    }

    @Test
    fun `PriceParser 핫딜 추출`() {
        val deal = PriceParser.parse("[쿠팡] 삼성 SSD 1TB 89,900원 25%", null)
        assertEquals(89900, deal.salePrice)
        assertEquals(25, deal.discountRate)
        assertEquals("쿠팡", deal.mallName)
        assertNull(deal.dealStatus)
    }

    @Test
    fun `PriceParser 중고 상태 추출`() {
        assertEquals("sold", PriceParser.parse("아이폰 판매완료", null).dealStatus)
        assertEquals("reserved", PriceParser.parse("예약중) 갤럭시", null).dealStatus)
        assertEquals("selling", PriceParser.parse("판매중 노트북 300,000원", null).dealStatus)
        val soldOut = PriceParser.parse("핫딜 품절 9,900원", null)
        assertEquals(true, soldOut.isSoldOut)
        assertEquals("sold", soldOut.dealStatus)
    }

    @Test
    fun `SelectorConfig 깨진 JSON은 기본값`() {
        val cfg = SelectorConfig.parse("{broken")
        assertEquals(SelectorConfig.defaults().listRow, cfg.listRow)
        val empty = SelectorConfig.parse(null)
        assertEquals(SelectorConfig.defaults().title, empty.title)
    }

    @Test
    fun `SelectorConfig main 필드 파싱`() {
        val cfg = SelectorConfig.parse("""{"main":"apple","listRow":"a.post-row"}""")
        assertEquals("apple", cfg.main)
        assertEquals("a.post-row", cfg.listRow)
    }

    @Test
    fun `CrawlHttp charset 판정`() {
        val client = CrawlHttpClient(service = "test", userAgent = "test")
        val utf8 = "한글".toByteArray(Charsets.UTF_8)
        assertEquals(Charsets.UTF_8, client.detectCharset("text/html; charset=UTF-8", utf8))
        assertEquals(
            charset("EUC-KR"),
            client.detectCharset("text/html; charset=euc_kr", utf8),
        )
        val meta = """<html><head><meta charset="euc_kr"></head></html>""".toByteArray(Charsets.US_ASCII)
        assertEquals(charset("EUC-KR"), client.detectCharset(null, meta))
        assertEquals(Charsets.UTF_8, client.detectCharset(null, "plain".toByteArray()))
    }
}
