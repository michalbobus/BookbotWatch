package com.bookbotwatch.net

import com.bookbotwatch.data.Offer
import org.json.JSONObject
import java.io.IOException

/**
 * Stiahne vypis z bookbot.sk a vytiahne z neho ponuky.
 *
 * Stranka je Next.js SSR, takze vsetky produkty su v JSON bloku
 * `<script id="__NEXT_DATA__">` pod `props.pageProps.componentProps.items`:
 *   grandmothers_id / grandmothers_title / highlight_price_eur (v centoch) / is_in_stock
 *
 * To je stabilnejsie ako parsovat HTML, kde su CSS triedy hashovane.
 * Ak by sa struktura zmenila, mame zalozne parsovanie HTML kariet.
 */
object BookbotClient {

    private val NEXT_DATA = Regex(
        "<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>",
        RegexOption.DOT_MATCHES_ALL
    )

    private val CARD = Regex(
        "<a class=\"ProductCardLink_styles_root__[^\"]*\" href=\"https://bookbot\\.sk/g/(\\d+)\">(.*?)</a>(.*?)(?=<a class=\"ProductCardLink_styles_root__|\\z)",
        RegexOption.DOT_MATCHES_ALL
    )

    private val CARD_PRICE = Regex("(\\d{1,5}),(\\d{2})\\s*(?:&nbsp;|\\s)?\\s*€")
    private val TAGS = Regex("<[^>]+>")

    @Throws(IOException::class)
    fun fetchOffers(url: String): List<Offer> {
        val html = Http.get(url)
        val fromJson = parseNextData(html)
        if (fromJson.isNotEmpty()) return fromJson
        return parseHtmlCards(html)
    }

    private fun parseNextData(html: String): List<Offer> {
        val json = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return emptyList()
        return try {
            val items = JSONObject(json)
                .getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("componentProps")
                .getJSONArray("items")
            val out = ArrayList<Offer>(items.length())
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                val cents = o.optInt("highlight_price_eur", -1)
                val id = o.optLong("grandmothers_id", 0L)
                val title = o.optString("grandmothers_title", "")
                if (cents <= 0 || id == 0L || title.isEmpty()) continue
                out.add(Offer(id, title, cents, o.optBoolean("is_in_stock", true)))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Zaloha: vytiahnut nazov + cenu priamo z vyrenderovanych kariet. */
    private fun parseHtmlCards(html: String): List<Offer> {
        val out = LinkedHashMap<Long, Offer>()
        for (m in CARD.findAll(html)) {
            val id = m.groupValues[1].toLongOrNull() ?: continue
            val title = TAGS.replace(m.groupValues[2], "").trim()
            if (title.isEmpty()) continue
            val pm = CARD_PRICE.find(m.groupValues[3]) ?: continue
            val cents = pm.groupValues[1].toInt() * 100 + pm.groupValues[2].toInt()
            val inStock = !m.groupValues[3].contains("Vypredan", ignoreCase = true)
            out.putIfAbsent(id, Offer(id, title, cents, inStock))
        }
        return out.values.toList()
    }
}
