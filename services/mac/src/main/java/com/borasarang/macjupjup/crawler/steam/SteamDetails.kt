package com.borasarang.macjupjup.crawler.steam

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

/** Steam appdetails 공용 파서 — Steam·Epic 크롤러에서 본문/스크린샷/언어/요건 공유 */
object SteamDetails {
    const val GAME_DESC_MAX = com.borasarang.macjupjup.util.Constants.APP_BODY_MAX
    const val README_MARKER = "— README —"
    const val SYSREQ_MARKER = "— SYSREQ —"
    const val DETAIL_URL =
        "https://store.steampowered.com/api/appdetails?" +
            "filters=basic,genres,platforms,header_image,supported_languages," +
            "about_the_game,detailed_description,screenshots,categories," +
            "pc_requirements,mac_requirements&appids="
    const val STORESEARCH_URL =
        "https://store.steampowered.com/api/storesearch/?term="
    private val STEAM_ID = Regex("""steam-appid:(\d+)""")

    fun steamAppIdOf(topics: String?): String? =
        STEAM_ID.find(topics ?: "")?.groupValues?.get(1)

    /** appdetails JSON 루트에서 data 객체 추출 (appid 지정 가능) */
    fun dataOf(detailBody: String, appid: String? = null): JsonObject? {
        return try {
            val root = Json.parseToJsonElement(detailBody).jsonObject
            val node: JsonObject = when {
                appid != null -> (root[appid] as? JsonObject) ?: root
                else -> root.values.firstOrNull() as? JsonObject ?: root
            }
            if (node["success"]?.jsonPrimitive?.content == "false") return null
            node["data"]?.jsonObject
        } catch (_: Exception) {
            null
        }
    }

    /** storesearch 응답에서 이름 정확 일치 app id */
    fun searchAppId(detailSearchBody: String, name: String): Int? {
        return try {
            val root = Json.parseToJsonElement(detailSearchBody).jsonObject
            val items = root["items"] as? JsonArray ?: return null
            val want = name.trim().lowercase()
            items.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val n = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val id = o["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null
                if (n.trim().lowercase() == want && o["type"]?.jsonPrimitive?.content == "app") id else null
            }.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun composeDescription(
        shortDesc: String?,
        aboutMd: String,
        sysReqHtml: String,
        fallback: String?,
    ): String? {
        val short = shortDesc?.trim().orEmpty()
        val about = aboutMd.trim()
        val base = when {
            short.isNotBlank() && about.isNotBlank() && short != about ->
                "$short\n\n$README_MARKER\n\n$about"
            about.isNotBlank() -> about
            short.isNotBlank() -> short
            else -> fallback
        } ?: return null
        val withSys = if (sysReqHtml.isNotBlank()) {
            base.trimEnd() + "\n\n$SYSREQ_MARKER\n\n" + sysReqHtml.trim()
        } else {
            base.trim()
        }
        return withSys.take(GAME_DESC_MAX)
    }

    fun aboutHtmlToMarkdown(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return try {
            val doc = Jsoup.parse(html)
            doc.select("script,style").remove()
            val nodes = doc.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre")
            if (nodes.isEmpty()) {
                doc.wholeText().trim().replace(Regex("\\n{3,}"), "\n\n")
            } else {
                nodes.joinToString("\n\n") { el ->
                    val t = el.text().trim()
                    when (el.tagName().lowercase()) {
                        "h1", "h2", "h3" -> "### $t"
                        "h4", "h5", "h6" -> "#### $t"
                        "li" -> "- $t"
                        "blockquote" -> "> $t"
                        else -> t
                    }
                }.trim()
            }
        } catch (_: Exception) {
            Jsoup.parse(html).text().trim()
        }
    }

    fun buildSysReqHtml(macReq: kotlinx.serialization.json.JsonElement?, pcReq: kotlinx.serialization.json.JsonElement?): String {
        val parts = mutableListOf<String>()
        val macHtml = reqBlockHtml(macReq)
        if (macHtml.isNotBlank()) {
            parts += "<div class=\"sysreq-block\"><p><strong>macOS</strong></p>$macHtml</div>"
        } else {
            parts += "<div class=\"sysreq-block\"><p><strong>macOS:</strong> 지원</p></div>"
        }
        val pcHtml = reqBlockHtml(pcReq)
        if (pcHtml.isNotBlank()) {
            parts += "<div class=\"sysreq-block\"><p><strong>Windows</strong></p>$pcHtml</div>"
        }
        return parts.joinToString("\n")
    }

    private fun reqBlockHtml(raw: kotlinx.serialization.json.JsonElement?): String {
        return when (raw) {
            is JsonObject -> {
                val min = raw["minimum"]?.jsonPrimitive?.content?.trim().orEmpty()
                val rec = raw["recommended"]?.jsonPrimitive?.content?.trim().orEmpty()
                listOfNotNull(
                    min.takeIf { it.isNotBlank() },
                    rec.takeIf { it.isNotBlank() }?.let { "<p><strong>권장</strong></p>$it" },
                ).joinToString("\n")
            }
            is JsonPrimitive -> raw.content.trim()
            is JsonArray -> raw.joinToString("") { reqBlockHtml(it) }
            else -> ""
        }
    }

    fun parseScreenshots(raw: kotlinx.serialization.json.JsonElement?): String? {
        val arr = raw as? JsonArray ?: return null
        val urls = arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            o["path_thumbnail"]?.jsonPrimitive?.content
                ?: o["path_full"]?.jsonPrimitive?.content
        }.filter { it.isNotBlank() }.take(10)
        return urls.joinToString("\n").takeIf { it.isNotBlank() }
    }

    fun parseSupportedLanguages(raw: kotlinx.serialization.json.JsonElement?): String? {
        val found = LinkedHashSet<String>()
        when (raw) {
            is JsonObject -> raw.keys.forEach { key -> isoOfSteamLang(key)?.let { found.add(it) } }
            is JsonPrimitive -> {
                val text = raw.content
                    .replace(Regex("<[^>]*>"), " ")
                    .replace(Regex("&[a-zA-Z]+;"), " ")
                    .lowercase()
                LANG_ALIAS.forEach { (iso, aliases) ->
                    if (aliases.any { alias ->
                            Regex("""(?<![a-z])${Regex.escape(alias)}(?![a-z])""").containsMatchIn(text)
                        }
                    ) found.add(iso)
                }
            }
            else -> return null
        }
        return found.joinToString(",").takeIf { it.isNotBlank() }
    }

    private fun isoOfSteamLang(key: String): String? {
        val k = key.lowercase()
        LANG_ALIAS.firstOrNull { (iso, aliases) -> iso == k || aliases.contains(k) }?.let {
            return it.first
        }
        return if (k.length == 2 && k.all { it in 'a'..'z' }) k else null
    }

    private val LANG_ALIAS: List<Pair<String, List<String>>> = listOf(
        "en" to listOf("english"),
        "ko" to listOf("koreana", "korean"),
        "ja" to listOf("japanese"),
        "zh" to listOf("schinese", "tchinese", "chinese", "simplified chinese", "traditional chinese"),
        "fr" to listOf("french"),
        "de" to listOf("german"),
        "it" to listOf("italian"),
        "es" to listOf("spanish", "latam", "latin american spanish"),
        "ru" to listOf("russian"),
        "pt" to listOf("portuguese", "brazilian", "brazilian portuguese"),
        "pl" to listOf("polish"),
        "tr" to listOf("turkish"),
        "nl" to listOf("dutch"),
        "cs" to listOf("czech"),
        "da" to listOf("danish"),
        "fi" to listOf("finnish"),
        "el" to listOf("greek"),
        "hu" to listOf("hungarian"),
        "no" to listOf("norwegian"),
        "sv" to listOf("swedish"),
        "th" to listOf("thai"),
        "vi" to listOf("vietnamese"),
        "uk" to listOf("ukrainian"),
        "ar" to listOf("arabic"),
        "hi" to listOf("hindi"),
        "id" to listOf("indonesian"),
        "he" to listOf("hebrew"),
        "ro" to listOf("romanian"),
        "sk" to listOf("slovak"),
        "sr" to listOf("serbian"),
        "hr" to listOf("croatian"),
        "bg" to listOf("bulgarian"),
    )
}
