package com.bookbotwatch.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bookbotwatch.core.PriceChecker
import com.bookbotwatch.data.CheckReport
import com.bookbotwatch.data.Prefs
import com.bookbotwatch.data.RestorioWatch
import com.bookbotwatch.data.StockWatch
import com.bookbotwatch.data.TxtParser
import com.bookbotwatch.data.WatchItem
import com.bookbotwatch.mail.Mailer
import com.bookbotwatch.work.CheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val url: String = "",
    val txtName: String = "",
    val items: List<WatchItem> = emptyList(),
    val itemCount: Int = 0,
    val emailEnabled: Boolean = true,
    val recipient: String = "",
    val smtpHost: String = "",
    val smtpPort: String = "587",
    val smtpUser: String = "",
    val smtpPass: String = "",
    val autoCheck: Boolean = false,
    val intervalHours: Int = 6,
    val stockWatches: List<StockWatch> = emptyList(),
    val restorioWatches: List<RestorioWatch> = emptyList(),
    val running: Boolean = false,
    val report: CheckReport? = null,
    val toast: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                PriceChecker.loadItems(getApplication(), prefs)
            }
            _state.value = _state.value.copy(
                url = prefs.url,
                txtName = prefs.txtName,
                items = items,
                itemCount = items.size,
                emailEnabled = prefs.emailEnabled,
                recipient = prefs.recipient,
                smtpHost = prefs.smtpHost,
                smtpPort = prefs.smtpPort.toString(),
                smtpUser = prefs.smtpUser,
                smtpPass = prefs.smtpPass,
                autoCheck = prefs.autoCheck,
                intervalHours = prefs.intervalHours,
                stockWatches = prefs.stockWatches(),
                restorioWatches = prefs.restorioWatches(),
                report = prefs.loadReport()
            )
        }
    }

    fun onUrlChange(v: String) {
        prefs.url = v
        _state.value = _state.value.copy(url = v)
    }

    fun onRecipientChange(v: String) {
        prefs.recipient = v
        _state.value = _state.value.copy(recipient = v)
    }

    fun onSmtpHostChange(v: String) {
        prefs.smtpHost = v
        _state.value = _state.value.copy(smtpHost = v)
    }

    fun onSmtpPortChange(v: String) {
        val digits = v.filter { it.isDigit() }.take(5)
        digits.toIntOrNull()?.let { prefs.smtpPort = it }
        _state.value = _state.value.copy(smtpPort = digits)
    }

    fun onSmtpUserChange(v: String) {
        prefs.smtpUser = v
        _state.value = _state.value.copy(smtpUser = v)
    }

    fun onSmtpPassChange(v: String) {
        prefs.smtpPass = v
        _state.value = _state.value.copy(smtpPass = v)
    }

    fun onEmailEnabledChange(v: Boolean) {
        prefs.emailEnabled = v
        _state.value = _state.value.copy(emailEnabled = v)
    }

    fun onAutoCheckChange(v: Boolean) {
        prefs.autoCheck = v
        _state.value = _state.value.copy(autoCheck = v)
        applySchedule()
    }

    fun onIntervalChange(hours: Int) {
        prefs.intervalHours = hours
        _state.value = _state.value.copy(intervalHours = hours)
        applySchedule()
    }

    private fun applySchedule() {
        val ctx = getApplication<Application>()
        if (prefs.autoCheck) CheckWorker.schedule(ctx, prefs.intervalHours)
        else CheckWorker.cancel(ctx)
    }

    fun onTxtPicked(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                prefs.txtUri = uri.toString()
                prefs.txtName = displayName(uri)
                prefs.resetHistory()
                PriceChecker.loadItems(ctx, prefs)
            }
            _state.value = _state.value.copy(
                txtName = prefs.txtName,
                items = items,
                itemCount = items.size,
                toast = if (items.isEmpty()) "V súbore sa nenašiel žiadny použiteľný riadok."
                else "Načítaných ${items.size} položiek."
            )
        }
    }

    // ------------------------------------------------- editacia zoznamu

    /** Zapise aktualny zoznam do vybraneho TXT suboru (alebo aspon do internej zalohy). */
    private fun persistItems(items: List<WatchItem>, toast: String) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val text = TxtParser.serialize(items)
                prefs.txtSnapshot = text
                val uri = prefs.txtUri
                if (uri != null) {
                    runCatching {
                        ctx.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use {
                            it.write(text.toByteArray(Charsets.UTF_8))
                        }
                    }.isSuccess
                } else true
            }
            _state.value = _state.value.copy(
                items = items,
                itemCount = items.size,
                toast = if (ok) toast
                else "$toast (zápis do súboru zlyhal, zmena je len v appke)"
            )
        }
    }

    fun addItem(volume: String, name: String, priceText: String) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(toast = "Vyplň názov položky.")
            return
        }
        if (prefs.txtUri == null && prefs.txtSnapshot.isBlank()) {
            _state.value = _state.value.copy(
                toast = "Najprv vyber alebo vytvor TXT súbor tlačidlom „Vybrať TXT“."
            )
            return
        }
        val cents = priceText.trim().takeIf { it.isNotEmpty() }?.let { TxtParser.priceToCents(it) }
        val item = WatchItem(volume.trim().uppercase(), name.trim(), cents)
        val updated = _state.value.items + item
        persistItems(updated, "Položka pridaná.")
    }

    fun removeItem(item: WatchItem) {
        val updated = _state.value.items.filterNot { it.key == item.key }
        val notified = prefs.notified().apply { remove(item.key) }
        prefs.saveNotified(notified)
        val baseline = prefs.baseline().apply { remove(item.key) }
        prefs.saveBaseline(baseline)
        persistItems(updated, "Položka odstránená.")
    }

    fun updateItemPrice(item: WatchItem, priceText: String) {
        val cents = priceText.trim().takeIf { it.isNotEmpty() }?.let { TxtParser.priceToCents(it) }
        val updated = _state.value.items.map {
            if (it.key == item.key) it.copy(refCents = cents) else it
        }
        // Cena sa zmenila - nech to vie znova upozornit na novu referenciu.
        val notified = prefs.notified().apply { remove(item.key) }
        prefs.saveNotified(notified)
        val baseline = prefs.baseline().apply { remove(item.key) }
        prefs.saveBaseline(baseline)
        persistItems(updated, "Cena upravená.")
    }

    /** Otvori systemovy vyber na vytvorenie noveho TXT suboru (ked appka este ziadny nema). */
    fun onTxtCreated(uri: Uri) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.txtUri = uri.toString()
        prefs.txtName = displayName(uri)
        persistItems(emptyList(), "Nový zoznam vytvorený.")
    }

    private fun displayName(uri: Uri): String {
        val ctx = getApplication<Application>()
        return runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "zoznam.txt"
    }

    // ------------------------------------------------- strazenie skladu

    /** Prijme aj plne URL, aj samotne "180963/b/22784943". */
    fun addStockWatch(rawUrl: String, threshold: Int) {
        val url = normalizeBookUrl(rawUrl)
        if (url == null) {
            _state.value = _state.value.copy(
                toast = "Nerozpoznaný odkaz. Očakávam napr. https://bookbot.sk/g/180963/b/22784943"
            )
            return
        }
        val current = prefs.stockWatches()
        if (current.any { it.url == url }) {
            _state.value = _state.value.copy(toast = "Tento odkaz už je v zozname.")
            return
        }
        val updated = current + StockWatch(url, threshold.coerceIn(1, 999))
        prefs.saveStockWatches(updated)
        _state.value = _state.value.copy(stockWatches = updated, toast = "Odkaz pridaný.")
    }

    fun removeStockWatch(url: String) {
        val updated = prefs.stockWatches().filterNot { it.url == url }
        prefs.saveStockWatches(updated)
        val notified = prefs.notifiedStock().apply { remove(url) }
        prefs.saveNotifiedStock(notified)
        _state.value = _state.value.copy(stockWatches = updated, toast = "Odkaz odstránený.")
    }

    fun setStockThreshold(url: String, threshold: Int) {
        val updated = prefs.stockWatches().map {
            if (it.url == url) it.copy(threshold = threshold.coerceIn(1, 999)) else it
        }
        prefs.saveStockWatches(updated)
        // Zmena prahu = nova situacia, nech to vie upozornit znova.
        val notified = prefs.notifiedStock().apply { remove(url) }
        prefs.saveNotifiedStock(notified)
        _state.value = _state.value.copy(stockWatches = updated)
    }

    private fun normalizeBookUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val m = Regex("(?:bookbot\\.sk)?/?g/(\\d+)(?:/b/(\\d+))?").find(s) ?: return null
        val g = m.groupValues[1]
        val b = m.groupValues[2]
        return if (b.isBlank()) "https://bookbot.sk/g/$g"
        else "https://bookbot.sk/g/$g/b/$b"
    }

    // ---------------------------------------------- strazenie na restorio.sk

    /** Prijme aj plne URL, aj holy kod produktu (napr. "9788076794306"). */
    fun addRestorioWatch(rawUrl: String, countThreshold: Int, priceText: String) {
        val url = normalizeRestorioUrl(rawUrl)
        if (url == null) {
            _state.value = _state.value.copy(
                toast = "Nerozpoznaný odkaz. Očakávam napr. https://www.restorio.sk/9788076794306"
            )
            return
        }
        val current = prefs.restorioWatches()
        if (current.any { it.url == url }) {
            _state.value = _state.value.copy(toast = "Tento odkaz už je v zozname.")
            return
        }
        val priceCents = priceText.trim().takeIf { it.isNotEmpty() }?.let { TxtParser.priceToCents(it) }
        val updated = current + RestorioWatch(url, countThreshold.coerceIn(0, 999), priceCents)
        prefs.saveRestorioWatches(updated)
        _state.value = _state.value.copy(restorioWatches = updated, toast = "Odkaz pridaný.")
    }

    fun removeRestorioWatch(url: String) {
        val updated = prefs.restorioWatches().filterNot { it.url == url }
        prefs.saveRestorioWatches(updated)
        val stock = prefs.notifiedRestorioStock().apply { remove(url) }
        prefs.saveNotifiedRestorioStock(stock)
        val price = prefs.notifiedRestorioPrice().apply { remove(url) }
        prefs.saveNotifiedRestorioPrice(price)
        _state.value = _state.value.copy(restorioWatches = updated, toast = "Odkaz odstránený.")
    }

    fun setRestorioCountThreshold(url: String, threshold: Int) {
        val updated = prefs.restorioWatches().map {
            if (it.url == url) it.copy(countThreshold = threshold.coerceIn(0, 999)) else it
        }
        prefs.saveRestorioWatches(updated)
        val stock = prefs.notifiedRestorioStock().apply { remove(url) }
        prefs.saveNotifiedRestorioStock(stock)
        _state.value = _state.value.copy(restorioWatches = updated)
    }

    fun setRestorioPriceThreshold(url: String, priceText: String) {
        val cents = priceText.trim().takeIf { it.isNotEmpty() }?.let { TxtParser.priceToCents(it) }
        val updated = prefs.restorioWatches().map {
            if (it.url == url) it.copy(priceThresholdCents = cents) else it
        }
        prefs.saveRestorioWatches(updated)
        val price = prefs.notifiedRestorioPrice().apply { remove(url) }
        prefs.saveNotifiedRestorioPrice(price)
        _state.value = _state.value.copy(restorioWatches = updated)
    }

    private fun normalizeRestorioUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        // Cely odkaz na restorio.sk.
        Regex("https?://(?:www\\.)?restorio\\.sk/[\\w-]+").find(s)?.let {
            return it.value.replace(Regex("^https?://(?:www\\.)?"), "https://www.")
        }
        // Samotny kod produktu (ISBN a pod.), bez URL.
        if (Regex("^[\\w-]+$").matches(s)) return "https://www.restorio.sk/$s"
        return null
    }

    fun checkNow() {
        if (_state.value.running) return
        _state.value = _state.value.copy(running = true, toast = null)
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                PriceChecker.run(getApplication())
            }
            val found = buildList {
                if (report.drops.isNotEmpty()) add("${report.drops.size}× zlacnenie")
                if (report.stockAlerts.isNotEmpty()) add("${report.stockAlerts.size}× dochádza skladom")
                if (report.restorioAlerts.isNotEmpty()) add("${report.restorioAlerts.size}× restorio")
            }
            val toast = when {
                found.isNotEmpty() -> found.joinToString(", ")
                report.error != null -> report.error
                else -> "Bez zmeny."
            }
            _state.value = _state.value.copy(running = false, report = report, toast = toast)
        }
    }

    fun sendTestEmail() {
        _state.value = _state.value.copy(running = true, toast = null)
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching { Mailer.sendTest(prefs) }
                    .fold(
                        onSuccess = { "Testovací e-mail odoslaný na ${prefs.recipient}." },
                        onFailure = { "Chyba: ${it.message ?: it.javaClass.simpleName}" }
                    )
            }
            _state.value = _state.value.copy(running = false, toast = msg)
        }
    }

    fun resetHistory() {
        prefs.resetHistory()
        _state.value = _state.value.copy(toast = "História upozornení vymazaná.")
    }

    fun toastShown() {
        _state.value = _state.value.copy(toast = null)
    }
}
