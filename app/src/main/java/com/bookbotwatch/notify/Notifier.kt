package com.bookbotwatch.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bookbotwatch.R
import com.bookbotwatch.data.CheckRow
import com.bookbotwatch.data.RestorioRow
import com.bookbotwatch.data.StockRow
import com.bookbotwatch.data.asEur

object Notifier {

    private const val CHANNEL_DROPS = "price_drops"
    private const val CHANNEL_STOCK = "stock_low"
    private const val CHANNEL_RESTORIO = "restorio_watch"
    private const val CHANNEL_INFO = "info"

    private const val ID_DROPS = 1001
    private const val ID_INFO = 1002
    private const val ID_STOCK = 1003
    private const val ID_RESTORIO = 1004

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DROPS,
                "Zlacnenia",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Cena klesla pod cenu zo zoznamu." }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STOCK,
                "Dochádzajúce skladom",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Počet kusov skladom klesol pod nastavený prah." }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESTORIO,
                "Restorio – sledovanie",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Kus na restorio.sk je skladom alebo cena klesla na cieľovú hodnotu." }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INFO,
                "Priebeh kontroly",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Výsledok kontroly na pozadí, chyby." }
        )
    }

    /** Zlacnenia, dochadzajuce kusy aj restorio upozornenia - kazde ako samostatna notifikacia. */
    fun showAlerts(
        context: Context,
        drops: List<CheckRow>,
        stockAlerts: List<StockRow>,
        restorioAlerts: List<RestorioRow>,
        pageUrl: String
    ) {
        showDrops(context, drops, pageUrl)
        showStockAlerts(context, stockAlerts)
        showRestorioAlerts(context, restorioAlerts)
    }

    fun showDrops(context: Context, drops: List<CheckRow>, pageUrl: String) {
        if (drops.isEmpty()) return
        ensureChannels(context)

        val lines = drops.map { row ->
            val old = row.refCents?.asEur() ?: "?"
            val now = row.offer?.cents?.asEur() ?: "?"
            "${row.item.label}: $old → $now"
        }
        val title = if (drops.size == 1) "Zlacnilo: ${drops[0].item.label}"
        else "Zlacnilo ${drops.size} kníh"

        notify(
            context, ID_DROPS, CHANNEL_DROPS, title, lines,
            drops.firstOrNull()?.offer?.url ?: pageUrl
        )
    }

    fun showStockAlerts(context: Context, alerts: List<StockRow>) {
        if (alerts.isEmpty()) return
        ensureChannels(context)

        val lines = alerts.map { row ->
            val parts = mutableListOf<String>()
            if (row.stockAlert) parts.add("skladom už len ${row.count} ks (prah ${row.threshold})")
            if (row.priceAlert) parts.add("cena ${row.priceCents?.asEur() ?: "?"}")
            "${row.label}: ${parts.joinToString(", ")}"
        }
        val title = if (alerts.size == 1) "Dochádza: ${alerts[0].title}"
        else "Dochádza ${alerts.size} kníh"

        notify(context, ID_STOCK, CHANNEL_STOCK, title, lines, alerts.first().url)
    }

    fun showRestorioAlerts(context: Context, alerts: List<RestorioRow>) {
        if (alerts.isEmpty()) return
        ensureChannels(context)

        val lines = alerts.map { row ->
            val parts = mutableListOf<String>()
            if (row.stockAlert) parts.add("skladom")
            if (row.priceAlert) parts.add("cena ${row.priceCents?.asEur() ?: "?"}")
            "${row.label}: ${parts.joinToString(", ")}"
        }
        val title = if (alerts.size == 1) "Restorio: ${alerts[0].label}"
        else "Restorio: ${alerts.size} upozornení"

        notify(context, ID_RESTORIO, CHANNEL_RESTORIO, title, lines, alerts.first().url)
    }

    fun showInfo(context: Context, title: String, text: String) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        post(context, ID_INFO, n)
    }

    private fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        lines: List<String>,
        openUrl: String
    ) {
        val pi = PendingIntent.getActivity(
            context, id,
            Intent(Intent.ACTION_VIEW, Uri.parse(openUrl)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        style.setSummaryText("BookBot hliadka")

        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_tag)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" • "))
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        post(context, id, n)
    }

    private fun post(context: Context, id: Int, n: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Pouzivatel nepovolil notifikacie - kontrola aj tak dobehne.
        }
    }
}
