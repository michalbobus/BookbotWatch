package com.bookbotwatch.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Vsetko nastavenie + posledny vysledok v jednom SharedPreferences subore. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("bookbot_watch", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_URL =
            "https://bookbot.sk/p/q/y%20posledn%C3%AD%20z%20mu%C5%BE%C5%AF/language/1"
        const val DEFAULT_RECIPIENT = "fajnes@gmail.com"

        /** Predvyplneny strazeny odkaz pri prvom spusteni. */
        const val DEFAULT_STOCK_URL = "https://bookbot.sk/g/180963/b/22784943"
        const val DEFAULT_STOCK_THRESHOLD = 3

        /** Predvyplneny strazeny odkaz na restorio.sk pri prvom spusteni. */
        const val DEFAULT_RESTORIO_URL = "https://www.restorio.sk/9788076794306"
        const val DEFAULT_RESTORIO_COUNT_THRESHOLD = 1
        const val DEFAULT_RESTORIO_PRICE_CENTS = 1496
    }

    var url: String
        get() = sp.getString("url", DEFAULT_URL) ?: DEFAULT_URL
        set(v) = sp.edit().putString("url", v.trim()).apply()

    /** SAF URI na TXT subor, aby sa cital vzdy aktualny obsah. */
    var txtUri: String?
        get() = sp.getString("txt_uri", null)
        set(v) = sp.edit().putString("txt_uri", v).apply()

    var txtName: String
        get() = sp.getString("txt_name", "") ?: ""
        set(v) = sp.edit().putString("txt_name", v).apply()

    /** Zaloha obsahu TXT, keby SAF URI prestalo byt citatelne. */
    var txtSnapshot: String
        get() = sp.getString("txt_snapshot", "") ?: ""
        set(v) = sp.edit().putString("txt_snapshot", v).apply()

    var emailEnabled: Boolean
        get() = sp.getBoolean("email_enabled", true)
        set(v) = sp.edit().putBoolean("email_enabled", v).apply()

    var recipient: String
        get() = sp.getString("recipient", DEFAULT_RECIPIENT) ?: DEFAULT_RECIPIENT
        set(v) = sp.edit().putString("recipient", v.trim()).apply()

    var smtpHost: String
        get() = sp.getString("smtp_host", "smtp.gmail.com") ?: "smtp.gmail.com"
        set(v) = sp.edit().putString("smtp_host", v.trim()).apply()

    var smtpPort: Int
        get() = sp.getInt("smtp_port", 587)
        set(v) = sp.edit().putInt("smtp_port", v).apply()

    var smtpUser: String
        get() = sp.getString("smtp_user", "") ?: ""
        set(v) = sp.edit().putString("smtp_user", v.trim()).apply()

    var smtpPass: String
        get() = sp.getString("smtp_pass", "") ?: ""
        set(v) = sp.edit().putString("smtp_pass", v).apply()

    var autoCheck: Boolean
        get() = sp.getBoolean("auto_check", false)
        set(v) = sp.edit().putBoolean("auto_check", v).apply()

    var intervalHours: Int
        get() = sp.getInt("interval_hours", 6)
        set(v) = sp.edit().putInt("interval_hours", v).apply()

    var lastCheckMs: Long
        get() = sp.getLong("last_check_ms", 0L)
        set(v) = sp.edit().putLong("last_check_ms", v).apply()

    var lastError: String?
        get() = sp.getString("last_error", null)
        set(v) = sp.edit().putString("last_error", v).apply()

    /** Ceny, pri ktorych sme uz upozornili - aby to nespamovalo pri kazdej kontrole. */
    fun notified(): MutableMap<String, Int> = readIntMap("notified")

    fun saveNotified(map: Map<String, Int>) = writeIntMap("notified", map)

    /** Prve videne ceny - sluzia ako referencia pre polozky bez ceny v TXT. */
    fun baseline(): MutableMap<String, Int> = readIntMap("baseline")

    fun saveBaseline(map: Map<String, Int>) = writeIntMap("baseline", map)

    private fun readIntMap(key: String): MutableMap<String, Int> {
        val json = sp.getString(key, "{}") ?: "{}"
        val obj = runCatching { JSONObject(json) }.getOrElse { JSONObject() }
        val map = HashMap<String, Int>()
        obj.keys().forEach { k -> map[k] = obj.optInt(k) }
        return map
    }

    private fun writeIntMap(key: String, map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        sp.edit().putString(key, obj.toString()).apply()
    }

    /**
     * Strazene odkazy na detail knihy (pocet kusov skladom).
     * Pri uplne prvom citani sa predvyplni jeden odkaz, aby appka nebola prazdna.
     */
    fun stockWatches(): List<StockWatch> {
        if (!sp.getBoolean("stock_seeded", false)) {
            val seed = listOf(StockWatch(DEFAULT_STOCK_URL, DEFAULT_STOCK_THRESHOLD))
            sp.edit().putBoolean("stock_seeded", true).apply()
            saveStockWatches(seed)
            return seed
        }
        val json = sp.getString("stock_watches", "[]") ?: "[]"
        val arr = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
        val out = ArrayList<StockWatch>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            out.add(StockWatch(url, o.optInt("threshold", DEFAULT_STOCK_THRESHOLD)))
        }
        return out
    }

    fun saveStockWatches(list: List<StockWatch>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(JSONObject().apply {
                put("url", w.url)
                put("threshold", w.threshold)
            })
        }
        sp.edit().putString("stock_watches", arr.toString()).putBoolean("stock_seeded", true).apply()
    }

    /** Pocty kusov, pri ktorych sme uz upozornili. */
    fun notifiedStock(): MutableMap<String, Int> = readIntMap("notified_stock")

    fun saveNotifiedStock(map: Map<String, Int>) = writeIntMap("notified_stock", map)

    /**
     * Strazene odkazy na restorio.sk (pocet kusov aj cena).
     * Rovnaky princip ako [stockWatches] - pri prvom citani sa predvyplni jeden priklad.
     */
    fun restorioWatches(): List<RestorioWatch> {
        if (!sp.getBoolean("restorio_seeded", false)) {
            val seed = listOf(
                RestorioWatch(
                    DEFAULT_RESTORIO_URL,
                    DEFAULT_RESTORIO_COUNT_THRESHOLD,
                    DEFAULT_RESTORIO_PRICE_CENTS
                )
            )
            sp.edit().putBoolean("restorio_seeded", true).apply()
            saveRestorioWatches(seed)
            return seed
        }
        val json = sp.getString("restorio_watches", "[]") ?: "[]"
        val arr = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
        val out = ArrayList<RestorioWatch>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url")
            if (url.isBlank()) continue
            out.add(
                RestorioWatch(
                    url,
                    o.optInt("countThreshold", DEFAULT_RESTORIO_COUNT_THRESHOLD),
                    if (o.isNull("price") || !o.has("price")) null else o.optInt("price")
                )
            )
        }
        return out
    }

    fun saveRestorioWatches(list: List<RestorioWatch>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(JSONObject().apply {
                put("url", w.url)
                put("countThreshold", w.countThreshold)
                put("price", w.priceThresholdCents ?: JSONObject.NULL)
            })
        }
        sp.edit().putString("restorio_watches", arr.toString()).putBoolean("restorio_seeded", true).apply()
    }

    /** Pocty kusov na restorio.sk, pri ktorych sme uz upozornili. */
    fun notifiedRestorioStock(): MutableMap<String, Int> = readIntMap("notified_restorio_stock")

    fun saveNotifiedRestorioStock(map: Map<String, Int>) = writeIntMap("notified_restorio_stock", map)

    /** Ceny na restorio.sk, pri ktorych sme uz upozornili. */
    fun notifiedRestorioPrice(): MutableMap<String, Int> = readIntMap("notified_restorio_price")

    fun saveNotifiedRestorioPrice(map: Map<String, Int>) = writeIntMap("notified_restorio_price", map)

    /** Posledny vysledok, aby ho GUI vedelo ukazat aj po kontrole na pozadi. */
    fun saveReport(report: CheckReport) {
        val arr = JSONArray()
        for (r in report.rows) {
            arr.put(JSONObject().apply {
                put("volume", r.item.volume)
                put("name", r.item.name)
                put("ref", r.refCents ?: JSONObject.NULL)
                put("dropped", r.dropped)
                if (r.offer != null) {
                    put("title", r.offer.title)
                    put("cents", r.offer.cents)
                    put("stock", r.offer.inStock)
                    put("id", r.offer.id)
                }
            })
        }
        val stock = JSONArray()
        for (s in report.stockRows) {
            stock.put(JSONObject().apply {
                put("url", s.url)
                put("threshold", s.threshold)
                put("title", s.title)
                put("year", s.year)
                put("count", s.count)
                put("price", s.priceCents ?: JSONObject.NULL)
                put("alert", s.alert)
                put("error", s.error ?: JSONObject.NULL)
            })
        }

        val restorio = JSONArray()
        for (r in report.restorioRows) {
            restorio.put(JSONObject().apply {
                put("url", r.url)
                put("countThreshold", r.countThreshold)
                put("priceThreshold", r.priceThresholdCents ?: JSONObject.NULL)
                put("title", r.title)
                put("count", r.count)
                put("price", r.priceCents ?: JSONObject.NULL)
                put("stockAlert", r.stockAlert)
                put("priceAlert", r.priceAlert)
                put("error", r.error ?: JSONObject.NULL)
            })
        }

        sp.edit()
            .putString("report", arr.toString())
            .putString("report_stock", stock.toString())
            .putString("report_restorio", restorio.toString())
            .putLong("last_check_ms", report.timeMs)
            .putString("last_error", report.error)
            .apply()
    }

    fun loadReport(): CheckReport? {
        val json = sp.getString("report", null)
        val stockJson = sp.getString("report_stock", null)
        val restorioJson = sp.getString("report_restorio", null)
        if (json == null && stockJson == null && restorioJson == null) return null

        val arr = runCatching { JSONArray(json ?: "[]") }.getOrElse { JSONArray() }
        val rows = ArrayList<CheckRow>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val item = WatchItem(
                o.optString("volume"),
                o.optString("name"),
                if (o.isNull("ref")) null else o.optInt("ref")
            )
            val offer = if (o.has("cents")) Offer(
                o.optLong("id"), o.optString("title"), o.optInt("cents"), o.optBoolean("stock", true)
            ) else null
            rows.add(
                CheckRow(
                    item,
                    offer,
                    if (o.isNull("ref")) null else o.optInt("ref"),
                    o.optBoolean("dropped")
                )
            )
        }
        val stockArr = runCatching { JSONArray(stockJson ?: "[]") }.getOrElse { JSONArray() }
        val stockRows = ArrayList<StockRow>()
        for (i in 0 until stockArr.length()) {
            val o = stockArr.optJSONObject(i) ?: continue
            stockRows.add(
                StockRow(
                    url = o.optString("url"),
                    threshold = o.optInt("threshold", DEFAULT_STOCK_THRESHOLD),
                    title = o.optString("title"),
                    year = o.optString("year"),
                    count = o.optInt("count", -1),
                    priceCents = if (o.isNull("price")) null else o.optInt("price"),
                    alert = o.optBoolean("alert"),
                    error = if (o.isNull("error")) null else o.optString("error")
                )
            )
        }

        val restorioArr = runCatching { JSONArray(restorioJson ?: "[]") }.getOrElse { JSONArray() }
        val restorioRows = ArrayList<RestorioRow>()
        for (i in 0 until restorioArr.length()) {
            val o = restorioArr.optJSONObject(i) ?: continue
            restorioRows.add(
                RestorioRow(
                    url = o.optString("url"),
                    countThreshold = o.optInt("countThreshold", DEFAULT_RESTORIO_COUNT_THRESHOLD),
                    priceThresholdCents = if (o.isNull("priceThreshold") || !o.has("priceThreshold")) null
                    else o.optInt("priceThreshold"),
                    title = o.optString("title"),
                    count = o.optInt("count", -1),
                    priceCents = if (o.isNull("price")) null else o.optInt("price"),
                    stockAlert = o.optBoolean("stockAlert"),
                    priceAlert = o.optBoolean("priceAlert"),
                    error = if (o.isNull("error")) null else o.optString("error")
                )
            )
        }

        return CheckReport(
            timeMs = lastCheckMs,
            rows = rows,
            drops = rows.filter { it.dropped },
            stockRows = stockRows,
            stockAlerts = stockRows.filter { it.alert },
            restorioRows = restorioRows,
            restorioAlerts = restorioRows.filter { it.alert },
            error = lastError
        )
    }

    fun resetHistory() {
        sp.edit()
            .remove("notified")
            .remove("baseline")
            .remove("notified_stock")
            .remove("notified_restorio_stock")
            .remove("notified_restorio_price")
            .apply()
    }
}
