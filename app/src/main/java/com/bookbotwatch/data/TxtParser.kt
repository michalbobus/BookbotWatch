package com.bookbotwatch.data

import java.util.Locale

/**
 * Parser sledovaneho zoznamu. Ocakavany (a odporucany) format je tabulatormi
 * oddeleny stlpcovy zapis s volitelnou hlavickou:
 *
 *     Diel	Nazov	Cena
 *     V	V kruhu	8,49 €
 *     VI	Holky s holkama	6,99 €
 *
 * Tolerujeme aj oddelenie viacerymi medzerami, bodkociarkou alebo "|",
 * chybajuci stlpec dielu a chybajucu cenu (vtedy sa referencia vezme
 * z prvej videnej ceny na webe).
 */
object TxtParser {

    private const val NBSP = ' '
    private const val BOM = '﻿'

    private val ROMAN = Regex("^[IVXLCDM]{1,7}$", RegexOption.IGNORE_CASE)
    private val COLUMN_SPLIT = Regex("\t+| {2,}|\\s*[;|]\\s*")

    /** Cena na konci riadku - bud s desatinnou castou, alebo aspon so znackou meny. */
    private val TRAILING_PRICE = Regex(
        "(\\d{1,5}[.,]\\d{1,2}\\s*(?:€|EUR)?|\\d{1,5}\\s*(?:€|EUR))\\s*$",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): List<WatchItem> {
        val out = ArrayList<WatchItem>()
        for (raw in text.lines()) {
            val line = raw.replace(NBSP, ' ').replace(BOM.toString(), "").trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            if (isHeader(line)) continue

            var rest = line
            var cents: Int? = null
            TRAILING_PRICE.find(line)?.let { m ->
                val parsed = priceToCents(m.groupValues[1])
                if (parsed != null) {
                    cents = parsed
                    rest = line.substring(0, m.range.first).trim().trimEnd('\t', ';', '|', '-').trim()
                }
            }
            if (rest.isEmpty()) continue

            val cols = COLUMN_SPLIT.split(rest).map { it.trim() }.filter { it.isNotEmpty() }
            var volume = ""
            var name = rest
            if (cols.size >= 2 && ROMAN.matches(cols[0])) {
                volume = cols[0].uppercase(Locale.ROOT)
                name = cols.drop(1).joinToString(" ")
            } else if (cols.size >= 2) {
                name = cols.joinToString(" ")
            }
            if (name.isEmpty()) continue
            out.add(WatchItem(volume, name, cents))
        }
        return out
    }

    /**
     * Opacna operacia k [parse] - vytvori TXT obsah zo zoznamu poloziek,
     * aby ho appka vedela zapisat spat do suboru po pridani/zmazani/uprave.
     * Format je rovnaky ako referencny: Diel⇥Nazov⇥Cena, hlavicka na vrchu.
     */
    fun serialize(items: List<WatchItem>): String {
        val sb = StringBuilder("Diel\tNázov\tCena\n")
        for (item in items) {
            if (item.volume.isNotBlank()) {
                sb.append(item.volume).append('\t')
            }
            sb.append(item.name)
            item.refCents?.let { sb.append('\t').append(it.asEur()) }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun isHeader(line: String): Boolean {
        val n = Matcher.normalize(line)
        return n.contains("cena") && (n.contains("nazov") || n.contains("nazev") || n.contains("titul"))
    }

    /** "8,49 €" -> 849. Vrati null, ak to nie je cena. */
    fun priceToCents(raw: String): Int? {
        val s = raw.replace(NBSP, ' ')
            .replace(" ", "")
            .replace("€", "")
            .replace("EUR", "", ignoreCase = true)
            .trim()
        val m = Regex("^(\\d{1,5})(?:[.,](\\d{1,2}))?$").matchEntire(s) ?: return null
        val whole = m.groupValues[1].toIntOrNull() ?: return null
        val frac = m.groupValues[2].padEnd(2, '0')
        return whole * 100 + frac.toInt()
    }
}
