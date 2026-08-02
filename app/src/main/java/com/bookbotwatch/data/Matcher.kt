package com.bookbotwatch.data

import java.text.Normalizer
import java.util.Locale

/** Parovanie riadkov zo zoznamu na konkretne ponuky z webu. */
object Matcher {

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** "Y: poslední z mužů - V kruhu (díl V)" -> "y posledni z muzu v kruhu dil v" */
    fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return NON_ALNUM.replace(DIACRITICS.replace(decomposed, ""), " ").trim()
    }

    /**
     * Vyberie najlepsiu ponuku pre polozku.
     * Skore: zhoda dielu = 2 body, zhoda nazvu = 1 bod. Pri rovnakom skore
     * vyhrava skladom + nizsia cena.
     */
    fun best(item: WatchItem, offers: List<Offer>): Offer? {
        val nName = normalize(item.name)
        val volRegex = if (item.volume.isBlank()) null else
            Regex("\\b(?:dil|diel|die)\\s+${normalize(item.volume)}\\b")

        var bestOffer: Offer? = null
        var bestScore = 0

        for (offer in offers) {
            val nTitle = normalize(offer.title)
            var score = 0
            if (volRegex != null && volRegex.containsMatchIn(nTitle)) score += 2
            if (nName.isNotEmpty() && nTitle.contains(nName)) score += 1
            if (score == 0) continue

            val current = bestOffer
            val better = when {
                current == null -> true
                score != bestScore -> score > bestScore
                offer.inStock != current.inStock -> offer.inStock
                else -> offer.cents < current.cents
            }
            if (better) {
                bestOffer = offer
                bestScore = score
            }
        }
        return bestOffer
    }
}
