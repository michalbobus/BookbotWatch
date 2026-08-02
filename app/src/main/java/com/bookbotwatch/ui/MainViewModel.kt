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
import com.bookbotwatch.data.StockWatch
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
            val count = withContext(Dispatchers.IO) {
                PriceChecker.loadItems(getApplication(), prefs).size
            }
            _state.value = _state.value.copy(
                url = prefs.url,
                txtName = prefs.txtName,
                itemCount = count,
                emailEnabled = prefs.emailEnabled,
                recipient = prefs.recipient,
                smtpHost = prefs.smtpHost,
                smtpPort = prefs.smtpPort.toString(),
                smtpUser = prefs.smtpUser,
                smtpPass = prefs.smtpPass,
                autoCheck = prefs.autoCheck,
                intervalHours = prefs.intervalHours,
                stockWatches = prefs.stockWatches(),
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
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                prefs.txtUri = uri.toString()
                prefs.txtName = displayName(uri)
                prefs.resetHistory()
                PriceChecker.loadItems(ctx, prefs).size
            }
            _state.value = _state.value.copy(
                txtName = prefs.txtName,
                itemCount = result,
                toast = if (result == 0) "V súbore sa nenašiel žiadny použiteľný riadok."
                else "Načítaných $result položiek."
            )
        }
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
