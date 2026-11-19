package com.md.mdprint

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("mdprint", Context.MODE_PRIVATE)

    var selected: String?
        get() = sp.getString("selected", null)
        set(v) { sp.edit().putString("selected", v).apply() }

    fun loadPrinters(): List<Printer> = runCatching {
        val a = JSONArray(sp.getString("printers", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Printer(o.getString("id"), o.getString("name"), o.getString("host"),
                o.getInt("port"), o.getString("path"), o.optBoolean("manual"))
        }
    }.getOrDefault(emptyList())

    fun savePrinters(l: List<Printer>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host)
                .put("port", it.port).put("path", it.path).put("manual", it.manual))
        }
        sp.edit().putString("printers", a.toString()).apply()
    }

    fun loadHistory(): List<HistoryItem> = runCatching {
        val a = JSONArray(sp.getString("history", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            HistoryItem(o.getLong("id"), o.getString("name"), o.getString("kind"),
                o.getString("target"), o.getBoolean("ok"))
        }
    }.getOrDefault(emptyList())

    fun saveHistory(l: List<HistoryItem>) {
        val a = JSONArray()
        l.take(200).forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("kind", it.kind)
                .put("target", it.target).put("ok", it.ok))
        }
        sp.edit().putString("history", a.toString()).apply()
    }

    fun loadSettings(): PrintSettings = PrintSettings(
        copies = sp.getInt("copies", 1).coerceIn(1, 999),
        paper = runCatching { Paper.valueOf(sp.getString("paper", "A4")!!) }.getOrDefault(Paper.A4),
        color = sp.getBoolean("color", true),
        orientation = runCatching { Orient.valueOf(sp.getString("orient", "AUTO")!!) }.getOrDefault(Orient.AUTO),
        sides = runCatching { Sides.valueOf(sp.getString("sides", "ONE")!!) }.getOrDefault(Sides.ONE),
        asImage = sp.getBoolean("asImage", false),
        quality = runCatching { Quality.valueOf(sp.getString("quality", "NORMAL")!!) }.getOrDefault(Quality.NORMAL)
    )

    fun saveSettings(s: PrintSettings) {
        sp.edit().putInt("copies", s.copies).putString("paper", s.paper.name)
            .putBoolean("color", s.color).putString("orient", s.orientation.name)
            .putString("sides", s.sides.name).putBoolean("asImage", s.asImage).putString("quality", s.quality.name).apply()
    }
}
