package com.bookbotwatch.net

import com.bookbotwatch.data.StockInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Precita z detailu knihy (https://bookbot.sk/g/<gid>/b/<bid>) pocet kusov skladom.
 *
 * Cislo, ktore stranka renderuje ako "Skladom máme celkom 3 ks knihy X (2005).",
 * pochadza z `__NEXT_DATA__`:
 *   props.pageProps.componentProps.data.variants.mother[selected].inStockCount
 * a nazov/rok z toho isteho zaznamu (`mother.title`, `mother.year`).
 *
 * Zaloha: vytiahnut cislo priamo z tej vety v HTML.
 */
object StockClient {

    private val NEXT_DATA = Regex(
        "<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>",
        RegexOption.DOT_MATCHES_ALL
    )

    /** "Skladom máme celkom <button ...>3 ks</button> knihy <a ...>Titul</a> (2005)." */
    private val SENTENCE = Regex(
        "Sklad(?:om|em)\\s+m[áa]me\\s+celk(?:om|em).{0,400}?>\\s*(\\d+)\\s*ks\\s*<",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val SENTENCE_TITLE = Regex(
        "Sklad(?:om|em)\\s+m[áa]me\\s+celk(?:om|em).{0,400}?knih[yu]\\s*<a[^>]*>(.*?)</a>\\s*\\((\\d{4})\\)",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val TAGS = Regex("<[^>]+>")

    @Throws(IOException::class)
    fun fetchStock(url: String): StockInfo {
        val html = Http.get(url)
        val info = parseNextData(html) ?: parseHtml(html)
        return info ?: throw IOException("Na stránke sa nenašiel údaj o počte kusov skladom.")
    }

    private fun parseNextData(html: String): StockInfo? {
        val json = NEXT_DATA.find(html)?.groupValues?.get(1) ?: return null
        return try {
            val data = JSONObject(json)
                .getJSONObject("props")
                .getJSONObject("pageProps")
                .getJSONObject("componentProps")
                .getJSONObject("data")

            val mothers = data.getJSONObject("variants").getJSONArray("mother")
            val chosen = pickSelected(mothers, data) ?: return null

            val count = chosen.optInt("inStockCount", -1)
            if (count < 0) return null

            val mother = chosen.optJSONObject("mother")
            val price = chosen.optInt("minPriceX100", -1).takeIf { it > 0 }

            StockInfo(
                title = mother?.optString("title").orEmpty().ifBlank { "Kniha" },
                year = mother?.optString("year").orEmpty(),
                count = count,
                priceCents = price
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Beriem zaznam so `selected: true` - to je to vydanie, ktore stranka prave
     * zobrazuje. Ak by ziadny oznaceny nebol, skusim spárovat cez id knihy
     * z `data.product`, inak vezmem prvy.
     */
    private fun pickSelected(mothers: JSONArray, data: JSONObject): JSONObject? {
        if (mothers.length() == 0) return null
        val productId = data.optJSONObject("product")?.optString("id")

        for (i in 0 until mothers.length()) {
            val o = mothers.optJSONObject(i) ?: continue
            if (o.optBoolean("selected", false)) return o
        }
        if (!productId.isNullOrBlank()) {
            for (i in 0 until mothers.length()) {
                val o = mothers.optJSONObject(i) ?: continue
                if (o.optJSONObject("product")?.optString("id") == productId) return o
            }
        }
        return mothers.optJSONObject(0)
    }

    private fun parseHtml(html: String): StockInfo? {
        val count = SENTENCE.find(html)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val m = SENTENCE_TITLE.find(html)
        val title = m?.groupValues?.get(1)?.let { TAGS.replace(it, "").trim() }.orEmpty()
        val year = m?.groupValues?.get(2).orEmpty()
        return StockInfo(title.ifBlank { "Kniha" }, year, count, null)
    }
}
