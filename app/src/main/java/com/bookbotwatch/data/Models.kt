package com.bookbotwatch.data

import java.util.Locale

/** Jeden riadok zo sledovaneho TXT suboru. */
data class WatchItem(
    /** Rimske cislo dielu, napr. "V". Moze byt prazdne. */
    val volume: String,
    /** Nazov, napr. "V kruhu". */
    val name: String,
    /** Referencna cena z TXT v centoch, null ak v subore nebola. */
    val refCents: Int?
) {
    /** Stabilny kluc pre ukladanie stavu medzi kontrolami. */
    val key: String get() = (volume + "|" + name).lowercase(Locale.ROOT)

    val label: String get() = if (volume.isBlank()) name else "$volume. $name"
}

/** Jedna ponuka vytiahnuta z vypisu na bookbot.sk. */
data class Offer(
    val id: Long,
    val title: String,
    val cents: Int,
    val inStock: Boolean
) {
    val url: String get() = "https://bookbot.sk/g/$id"
}

/** Vysledok porovnania jednej polozky zo zoznamu. */
data class CheckRow(
    val item: WatchItem,
    val offer: Offer?,
    /** Cena, voci ktorej sa porovnavalo (z TXT, resp. prva videna cena). */
    val refCents: Int?,
    val dropped: Boolean
) {
    val diffCents: Int?
        get() = if (offer != null && refCents != null) offer.cents - refCents else null
}

/** Strazeny odkaz na konkretne vydanie - sleduje sa pocet kusov skladom. */
data class StockWatch(
    val url: String,
    /** Upozorni, ked pocet kusov klesne POD tuto hodnotu. */
    val threshold: Int
)

/** Aktualny stav skladu vytiahnuty z detailu knihy. */
data class StockInfo(
    val title: String,
    val year: String,
    val count: Int,
    val priceCents: Int?
)

/** Vysledok kontroly jedneho strazeneho odkazu. */
data class StockRow(
    val url: String,
    val threshold: Int,
    val title: String,
    val year: String,
    /** -1 = nepodarilo sa zistit. */
    val count: Int,
    val priceCents: Int?,
    val alert: Boolean,
    val error: String? = null
) {
    val label: String get() = if (year.isBlank()) title else "$title ($year)"
}

/**
 * Strazeny odkaz na konkretne vydanie na restorio.sk - sleduje sa aj pocet
 * kusov (skladom/vypredane, restorio nezvykne udavat presny pocet) aj cena.
 */
data class RestorioWatch(
    val url: String,
    /** Upozorni, ked pocet kusov klesne POD tuto hodnotu (zvycajne 0 alebo 1 ks). */
    val countThreshold: Int,
    /** Upozorni, ked cena klesne NA alebo POD tuto hodnotu. Null = cena sa nesleduje. */
    val priceThresholdCents: Int?
)

/** Aktualny stav vytiahnuty z detailu knihy na restorio.sk. */
data class RestorioInfo(
    val title: String,
    val priceCents: Int?,
    val inStock: Boolean
) {
    /** Restorio zvycajne predava unikatne kusy - skladom = 1, vypredane = 0. */
    val count: Int get() = if (inStock) 1 else 0
}

/** Vysledok kontroly jedneho strazeneho odkazu na restorio.sk. */
data class RestorioRow(
    val url: String,
    val countThreshold: Int,
    val priceThresholdCents: Int?,
    val title: String,
    /** -1 = nepodarilo sa zistit. */
    val count: Int,
    val priceCents: Int?,
    val stockAlert: Boolean,
    val priceAlert: Boolean,
    val error: String? = null
) {
    val alert: Boolean get() = stockAlert || priceAlert
    val label: String get() = title.ifBlank { url.removePrefix("https://www.restorio.sk/") }
}

data class CheckReport(
    val timeMs: Long,
    val rows: List<CheckRow> = emptyList(),
    val drops: List<CheckRow> = emptyList(),
    val stockRows: List<StockRow> = emptyList(),
    val stockAlerts: List<StockRow> = emptyList(),
    val restorioRows: List<RestorioRow> = emptyList(),
    val restorioAlerts: List<RestorioRow> = emptyList(),
    val error: String? = null
)

fun Int.asEur(): String = String.format(Locale("sk", "SK"), "%.2f €", this / 100.0)

fun Int.asEurSigned(): String {
    val sign = if (this > 0) "+" else ""
    return sign + String.format(Locale("sk", "SK"), "%.2f €", this / 100.0)
}

/** 1 ks / 2 ks / 5 ks - jednotka je v slovencine rovnaka, riesime len "nezistene". */
fun Int.asPieces(): String = if (this < 0) "?" else "$this ks"
