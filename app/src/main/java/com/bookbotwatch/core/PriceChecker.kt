package com.bookbotwatch.core

import android.content.Context
import android.net.Uri
import com.bookbotwatch.data.CheckReport
import com.bookbotwatch.data.CheckRow
import com.bookbotwatch.data.Matcher
import com.bookbotwatch.data.Prefs
import com.bookbotwatch.data.RestorioRow
import com.bookbotwatch.data.StockRow
import com.bookbotwatch.data.TxtParser
import com.bookbotwatch.data.WatchItem
import com.bookbotwatch.mail.Mailer
import com.bookbotwatch.net.BookbotClient
import com.bookbotwatch.net.RestorioClient
import com.bookbotwatch.net.StockClient
import com.bookbotwatch.notify.Notifier

/**
 * Jadro appky. Jedna kontrola pokryva dve nezavisle veci:
 *
 *  1. CENY - TXT zoznam vs. vypis na bookbot.sk. Upozorni, ked cena klesne
 *     pod cenu zo zoznamu.
 *  2. SKLAD - strazene odkazy na detail knihy. Upozorni, ked pocet kusov
 *     skladom klesne pod nastaveny prah.
 *
 * Aby to pri kazdej kontrole neupozornovalo dokola, pamataju sa hodnoty,
 * pri ktorych sa uz upozornilo; znovu sa posle az pri dalsom poklese.
 * Ked sa hodnota vrati nad prah, pamat sa vycisti.
 */
object PriceChecker {

    /** Vola sa aj z UI, aj z workera. Blokujuce - patri na vlakno na pozadi. */
    fun run(context: Context, notifyOnEmpty: Boolean = false): CheckReport {
        val prefs = Prefs(context)
        val now = System.currentTimeMillis()
        val problems = ArrayList<String>()

        val (rows, drops) = checkPrices(context, prefs, problems)
        val (stockRows, stockAlerts) = checkStock(prefs, problems)
        val (restorioRows, restorioAlerts) = checkRestorio(prefs, problems)

        if (drops.isNotEmpty() || stockAlerts.isNotEmpty() || restorioAlerts.isNotEmpty()) {
            Notifier.showAlerts(context, drops, stockAlerts, restorioAlerts, prefs.url)
            if (prefs.emailEnabled) {
                try {
                    Mailer.sendAlerts(prefs, drops, stockAlerts, restorioAlerts, prefs.url)
                } catch (e: Exception) {
                    val msg = "E-mail sa neodoslal: ${e.message ?: e.javaClass.simpleName}"
                    problems.add(msg)
                    Notifier.showInfo(context, "E-mail sa neodoslal", msg)
                }
            }
        } else if (notifyOnEmpty) {
            Notifier.showInfo(
                context,
                "Bez zmeny",
                "Skontrolovaných ${rows.size} položiek, ${stockRows.size} odkazov na bookbot " +
                    "a ${restorioRows.size} na restorio, nič nové."
            )
        }

        val report = CheckReport(
            timeMs = now,
            rows = rows,
            drops = drops,
            stockRows = stockRows,
            stockAlerts = stockAlerts,
            restorioRows = restorioRows,
            restorioAlerts = restorioAlerts,
            error = problems.joinToString("\n").ifBlank { null }
        )
        prefs.saveReport(report)
        return report
    }

    // ---------------------------------------------------------------- ceny

    private fun checkPrices(
        context: Context,
        prefs: Prefs,
        problems: MutableList<String>
    ): Pair<List<CheckRow>, List<CheckRow>> {
        val items = loadItems(context, prefs)
        if (items.isEmpty()) {
            // Ked TXT nikdy nebol vybraty, appka moze fungovat len na sledovanie
            // skladu - nema zmysel hlasit to ako chybu.
            if (prefs.txtUri != null || prefs.txtSnapshot.isNotBlank()) {
                problems.add("TXT zoznam sa nepodarilo prečítať alebo neobsahuje žiadnu položku.")
            }
            return emptyList<CheckRow>() to emptyList()
        }

        val offers = try {
            BookbotClient.fetchOffers(prefs.url)
        } catch (e: Exception) {
            problems.add("Nepodarilo sa načítať výpis: ${e.message ?: e.javaClass.simpleName}")
            return (prefs.loadReport()?.rows ?: emptyList<CheckRow>()) to emptyList()
        }
        if (offers.isEmpty()) {
            problems.add("Výpis sa načítal, ale nenašli sa žiadne ponuky (zmenila sa štruktúra?).")
            return emptyList<CheckRow>() to emptyList()
        }

        val baseline = prefs.baseline()
        val notified = prefs.notified()
        val rows = ArrayList<CheckRow>(items.size)
        val drops = ArrayList<CheckRow>()

        for (item in items) {
            val offer = Matcher.best(item, offers)
            if (offer == null) {
                rows.add(CheckRow(item, null, item.refCents, false))
                continue
            }
            // Referencia: cena z TXT ma prednost, inak prva videna cena.
            val ref = item.refCents ?: baseline.getOrPut(item.key) { offer.cents }
            val already = notified[item.key]
            val dropped = offer.cents < ref && (already == null || offer.cents < already)

            val row = CheckRow(item, offer, ref, dropped)
            rows.add(row)
            if (dropped) {
                drops.add(row)
                notified[item.key] = offer.cents
            } else if (offer.cents >= ref) {
                notified.remove(item.key)
            }
        }

        prefs.saveBaseline(baseline)
        prefs.saveNotified(notified)
        return rows to drops
    }

    // --------------------------------------------------------------- sklad

    private fun checkStock(
        prefs: Prefs,
        problems: MutableList<String>
    ): Pair<List<StockRow>, List<StockRow>> {
        val watches = prefs.stockWatches()
        if (watches.isEmpty()) return emptyList<StockRow>() to emptyList()

        val notified = prefs.notifiedStock()
        val rows = ArrayList<StockRow>(watches.size)
        val alerts = ArrayList<StockRow>()

        for (w in watches) {
            val info = try {
                StockClient.fetchStock(w.url)
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                problems.add("Sklad – ${shortUrl(w.url)}: $msg")
                rows.add(StockRow(w.url, w.threshold, shortUrl(w.url), "", -1, null, false, msg))
                continue
            }

            val already = notified[w.url]
            val alert = info.count < w.threshold && (already == null || info.count < already)

            val row = StockRow(
                url = w.url,
                threshold = w.threshold,
                title = info.title,
                year = info.year,
                count = info.count,
                priceCents = info.priceCents,
                alert = alert
            )
            rows.add(row)
            if (alert) {
                alerts.add(row)
                notified[w.url] = info.count
            } else if (info.count >= w.threshold) {
                notified.remove(w.url)
            }
        }

        prefs.saveNotifiedStock(notified)
        return rows to alerts
    }

    private fun shortUrl(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("bookbot.sk")

    // ------------------------------------------------------------ restorio

    private fun checkRestorio(
        prefs: Prefs,
        problems: MutableList<String>
    ): Pair<List<RestorioRow>, List<RestorioRow>> {
        val watches = prefs.restorioWatches()
        if (watches.isEmpty()) return emptyList<RestorioRow>() to emptyList()

        val notifiedStock = prefs.notifiedRestorioStock()
        val notifiedPrice = prefs.notifiedRestorioPrice()
        val rows = ArrayList<RestorioRow>(watches.size)
        val alerts = ArrayList<RestorioRow>()

        for (w in watches) {
            val info = try {
                RestorioClient.fetchInfo(w.url)
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                problems.add("Restorio – ${shortRestorioUrl(w.url)}: $msg")
                rows.add(
                    RestorioRow(
                        w.url, w.countThreshold, w.priceThresholdCents,
                        shortRestorioUrl(w.url), -1, null, false, false, msg
                    )
                )
                continue
            }

            val alreadyStock = notifiedStock[w.url]
            val stockAlert = info.count < w.countThreshold &&
                (alreadyStock == null || info.count < alreadyStock)

            val alreadyPrice = notifiedPrice[w.url]
            val priceAlert = w.priceThresholdCents != null && info.priceCents != null &&
                info.priceCents <= w.priceThresholdCents &&
                (alreadyPrice == null || info.priceCents < alreadyPrice)

            val row = RestorioRow(
                url = w.url,
                countThreshold = w.countThreshold,
                priceThresholdCents = w.priceThresholdCents,
                title = info.title,
                count = info.count,
                priceCents = info.priceCents,
                stockAlert = stockAlert,
                priceAlert = priceAlert
            )
            rows.add(row)
            if (row.alert) alerts.add(row)

            if (stockAlert) notifiedStock[w.url] = info.count
            else if (info.count >= w.countThreshold) notifiedStock.remove(w.url)

            if (priceAlert) notifiedPrice[w.url] = info.priceCents ?: 0
            else if (w.priceThresholdCents == null ||
                (info.priceCents != null && info.priceCents > w.priceThresholdCents)
            ) notifiedPrice.remove(w.url)
        }

        prefs.saveNotifiedRestorioStock(notifiedStock)
        prefs.saveNotifiedRestorioPrice(notifiedPrice)
        return rows to alerts
    }

    private fun shortRestorioUrl(url: String): String =
        url.removePrefix("https://").removePrefix("http://").removePrefix("www.restorio.sk")

    // ----------------------------------------------------------------- TXT

    /** Nacita TXT zo SAF URI (aby sa videli aj neskorsie upravy suboru). */
    fun loadItems(context: Context, prefs: Prefs): List<WatchItem> {
        val uri = prefs.txtUri
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    val text = input.readBytes().toString(Charsets.UTF_8)
                    if (text.isNotBlank()) {
                        prefs.txtSnapshot = text
                        return TxtParser.parse(text)
                    }
                }
            } catch (e: Exception) {
                // Spadneme na poslednu ulozenu kopiu.
            }
        }
        val snapshot = prefs.txtSnapshot
        return if (snapshot.isBlank()) emptyList() else TxtParser.parse(snapshot)
    }
}
