package com.bookbotwatch.mail

import com.bookbotwatch.data.CheckRow
import com.bookbotwatch.data.Prefs
import com.bookbotwatch.data.StockRow
import com.bookbotwatch.data.asEur
import com.bookbotwatch.data.asEurSigned
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** Odoslanie e-mailu cez SMTP (JavaMail). Volat len z vlakna na pozadi. */
object Mailer {

    class NotConfigured(message: String) : Exception(message)

    /** Jeden e-mail za kontrolu - obsahuje zlacnenia aj dochadzajuce kusy. */
    fun sendAlerts(
        prefs: Prefs,
        drops: List<CheckRow>,
        stockAlerts: List<StockRow>,
        pageUrl: String
    ) {
        if (drops.isEmpty() && stockAlerts.isEmpty()) return
        send(prefs, subjectFor(drops, stockAlerts), alertsHtml(drops, stockAlerts, pageUrl))
    }

    private fun subjectFor(drops: List<CheckRow>, stockAlerts: List<StockRow>): String = when {
        drops.isNotEmpty() && stockAlerts.isNotEmpty() ->
            "BookBot: ${drops.size} zlacnení a ${stockAlerts.size}× dochádzajúce skladom"

        drops.size == 1 -> "BookBot: zlacnilo ${drops[0].item.label}"
        drops.size > 1 -> "BookBot: zlacnilo ${drops.size} kníh"
        stockAlerts.size == 1 ->
            "BookBot: ${stockAlerts[0].title} – skladom už len ${stockAlerts[0].count} ks"

        else -> "BookBot: ${stockAlerts.size} kníh dochádza skladom"
    }

    fun sendTest(prefs: Prefs) {
        send(
            prefs,
            "BookBot hliadka - testovací e-mail",
            "<p>Funguje to. Toto je testovacia správa z aplikácie <b>BookBot hliadka</b>.</p>"
        )
    }

    fun send(prefs: Prefs, subject: String, html: String) {
        val host = prefs.smtpHost.trim()
        val user = prefs.smtpUser.trim()
        val pass = prefs.smtpPass
        val to = prefs.recipient.trim()
        if (host.isEmpty()) throw NotConfigured("Nie je vyplnený SMTP server.")
        if (user.isEmpty() || pass.isEmpty()) throw NotConfigured("Nie je vyplnené SMTP prihlásenie.")
        if (to.isEmpty()) throw NotConfigured("Nie je vyplnený príjemca.")

        val port = prefs.smtpPort
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "25000")
            put("mail.smtp.writetimeout", "25000")
            if (port == 465) {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.socketFactory.port", port.toString())
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.fallback", "false")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(user, pass)
        })

        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(user, "BookBot hliadka"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            sentDate = Date()
            setContent(html, "text/html; charset=UTF-8")
        }
        Transport.send(msg)
    }

    private fun alertsHtml(
        drops: List<CheckRow>,
        stockAlerts: List<StockRow>,
        pageUrl: String
    ): String = """
        <div style="font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#222">
          ${if (drops.isNotEmpty()) dropsHtml(drops, pageUrl) else ""}
          ${if (stockAlerts.isNotEmpty()) stockHtml(stockAlerts) else ""}
          <p style="margin:20px 0 0;color:#888;font-size:12px">
            Odoslané aplikáciou BookBot hliadka.
          </p>
        </div>
    """.trimIndent()

    private fun stockHtml(alerts: List<StockRow>): String {
        val rows = alerts.joinToString("") { r ->
            """
            <tr>
              <td style="padding:8px 12px;border-bottom:1px solid #eee"><a href="${esc(r.url)}">${esc(r.label)}</a></td>
              <td style="padding:8px 12px;border-bottom:1px solid #eee;font-weight:bold;color:#B3261E">${r.count} ks</td>
              <td style="padding:8px 12px;border-bottom:1px solid #eee;color:#666">pod ${r.threshold} ks</td>
              <td style="padding:8px 12px;border-bottom:1px solid #eee">${r.priceCents?.asEur() ?: "-"}</td>
            </tr>
            """.trimIndent()
        }
        return """
          <h2 style="margin:24px 0 4px">Dochádza skladom</h2>
          <p style="margin:0 0 16px;color:#666">Počet kusov klesol pod nastavený prah.</p>
          <table cellspacing="0" cellpadding="0" style="border-collapse:collapse;min-width:420px">
            <tr style="background:#f5f5f5">
              <th align="left" style="padding:8px 12px">Kniha</th>
              <th align="left" style="padding:8px 12px">Skladom</th>
              <th align="left" style="padding:8px 12px">Prah</th>
              <th align="left" style="padding:8px 12px">Cena od</th>
            </tr>
            $rows
          </table>
        """.trimIndent()
    }

    private fun dropsHtml(drops: List<CheckRow>, pageUrl: String): String {
        val rows = drops.joinToString("") { r ->
            val old = r.refCents?.asEur() ?: "-"
            val now = r.offer?.cents?.asEur() ?: "-"
            val diff = r.diffCents?.asEurSigned() ?: "-"
            val link = r.offer?.url ?: pageUrl
            """
            <tr>
              <td style="padding:8px 12px;border-bottom:1px solid #eee"><a href="$link">${esc(r.item.label)}</a></td>
              <td style="padding:8px 12px;border-bottom:1px solid #eee;text-decoration:line-through;color:#888">$old</td>
              <td style="padding:8px 12px;border-bottom:1px solid #eee;font-weight:bold;color:#137333">$now</td>
              <td style="padding:8px 12px;border-bottom:1px solid #eee;color:#137333">$diff</td>
            </tr>
            """.trimIndent()
        }
        return """
          <h2 style="margin:0 0 4px">Zlacnené položky na BookBot</h2>
          <p style="margin:0 0 16px;color:#666">Ceny klesli pod hodnoty z tvojho zoznamu.</p>
          <table cellspacing="0" cellpadding="0" style="border-collapse:collapse;min-width:420px">
            <tr style="background:#f5f5f5">
              <th align="left" style="padding:8px 12px">Položka</th>
              <th align="left" style="padding:8px 12px">Pôvodne</th>
              <th align="left" style="padding:8px 12px">Teraz</th>
              <th align="left" style="padding:8px 12px">Rozdiel</th>
            </tr>
            $rows
          </table>
          <p style="margin:16px 0 0"><a href="${esc(pageUrl)}">Otvoriť výpis na bookbot.sk</a></p>
        """.trimIndent()
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
