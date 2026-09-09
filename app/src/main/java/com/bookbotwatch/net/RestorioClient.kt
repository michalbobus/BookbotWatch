package com.bookbotwatch.net

import com.bookbotwatch.data.RestorioInfo
import org.json.JSONObject
import java.io.IOException

/**
 * Precita z detailu knihy na restorio.sk (napr. https://www.restorio.sk/9788076794306)
 * cenu a dostupnost.
 *
 * Restorio je Magento e-shop s pouzitymi knihami - kazdy vypis je zvycajne
 * jeden unikatny kus, takze stranka neuvadza presny "pocet kusov" ako bookbot.
 * Dostupnost sa rovna "skladom / vypredane".
 *
 * Cena: meta tag `<meta property="product:price:amount" content="14.96">`
 * (Open Graph, generuje sa server-side, takze je v HTML aj bez JS).
 * Zaloha: JSON-LD blok `<script type="application/ld+json">` so schema.org
 * Product/offers.price, dalsia zaloha regex na prvu cenu v HTML.
 *
 * Dostupnost: stranka pri vypredanom kuse zobrazuje vetu
 * "Upozornite ma, keď bude tento produkt na sklade" (JS placeholder
 * "Kontrolujem stav skladu" sa nahradi az na klientovi, ale tato veta
 * sa v HTML renderuje uz zo servera len ked kus nie je dostupny).
 */
object RestorioClient {

    private val META_PRICE = Regex(
        "<meta[^>]+property=\"product:price:amount\"[^>]+content=\"([\\d.,]+)\"",
        RegexOption.IGNORE_CASE
    )
    private val META_TITLE = Regex(
        "<meta[^>]+property=\"og:title\"[^>]+content=\"(.*?)\"",
        RegexOption.IGNORE_CASE
    )
    private val JSONLD = Regex(
        "<script[^>]+type=\"application/ld\\+json\"[^>]*>(.*?)</script>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val PRICE_FALLBACK = Regex("(\\d{1,4}[.,]\\d{2})\\s*(?:&nbsp;|\\s)?€")
    private val OUT_OF_STOCK_MARKER = Regex(
        "Upozornite\\s+ma,\\s*ke[ďd]\\s+bude\\s+tento\\s+produkt\\s+na\\s+sklade",
        RegexOption.IGNORE_CASE
    )

    @Throws(IOException::class)
    fun fetchInfo(url: String): RestorioInfo {
        val html = Http.get(url)

        val title = META_TITLE.find(html)?.groupValues?.get(1)
            ?.let(::unescapeHtml)
            ?.removeSuffix(" - Restorio.sk")
            ?.trim()
            .orEmpty()

        val price = metaPriceCents(html) ?: jsonLdPriceCents(html) ?: fallbackPriceCents(html)
        val inStock = !OUT_OF_STOCK_MARKER.containsMatchIn(html)

        if (title.isEmpty() && price == null) {
            throw IOException("Na stránke sa nenašiel názov ani cena produktu.")
        }

        return RestorioInfo(
            title = title.ifBlank { "Kniha" },
            priceCents = price,
            inStock = inStock
        )
    }

    private fun metaPriceCents(html: String): Int? {
        val raw = META_PRICE.find(html)?.groupValues?.get(1) ?: return null
        return decimalToCents(raw)
    }

    private fun jsonLdPriceCents(html: String): Int? {
        for (m in JSONLD.findAll(html)) {
            val json = m.groupValues[1]
            val cents = runCatching {
                val obj = JSONObject(json)
                val offers = obj.optJSONObject("offers")
                val raw = offers?.opt("price")?.toString()
                raw?.let { decimalToCents(it) }
            }.getOrNull()
            if (cents != null) return cents
        }
        return null
    }

    private fun fallbackPriceCents(html: String): Int? {
        val raw = PRICE_FALLBACK.find(html)?.groupValues?.get(1) ?: return null
        return decimalToCents(raw)
    }

    /** "14.96" alebo "14,96" -> 1496. */
    private fun decimalToCents(raw: String): Int? {
        val norm = raw.trim().replace(',', '.')
        val value = norm.toDoubleOrNull() ?: return null
        return Math.round(value * 100).toInt()
    }

    private fun unescapeHtml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}
