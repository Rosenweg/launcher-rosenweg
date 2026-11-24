package org.fossify.home.helpers

import android.appwidget.AppWidgetHost
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import org.fossify.home.extensions.config
import org.fossify.home.extensions.homeScreenGridItemsDB
import org.fossify.home.models.HomeScreenGridItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rosenweg (Regel 7): Die Startseite gehoert der Person und liegt auf der Anlage
 * (Einstellungen des Pakets ch.rosenweg4303.home, Weg ueber die Geraete-App,
 * AnlageProvider.call — der Launcher braucht kein Netz).
 *
 * - Nach der Anmeldung (onResume, solange fuer diese Sitzung noch nicht geladen):
 *   Startseite der Person holen und anwenden; hat sie keine, bleibt der Standard.
 * - Beim Verlassen der Startseite (onPause): geaenderte Startseite hochladen.
 * - Abmelden (Nachricht der Geraete-App): zurueck auf den Standard.
 * Ohne Geraete-App (privates Handy) passiert nichts.
 */
object RosenwegStartseite {
    private const val TAG = "RosenwegStartseite"
    private val ANLAGE = Uri.parse("content://ch.rosenweg4303.telefon.anlage")
    const val AKTION = "ch.rosenweg4303.home.STARTSEITE"
    private const val PREFS = "rosenweg_launcher"
    private const val GELADEN = "startseite_geladen"
    private const val SIGNATUR = "startseite_signatur"
    const val AUSSTEHEND = "ausstehende_widgets"
    private const val NEU_AUFBAUEN = "neu_aufbauen"

    /** Startseite wurde ersetzt/zurueckgesetzt: Ansicht beim naechsten Anzeigen ganz neu aufbauen. */
    fun neuAufbauenNoetig(c: Context): Boolean {
        val p = prefs(c)
        if (!p.getBoolean(NEU_AUFBAUEN, false)) return false
        p.edit().putBoolean(NEU_AUFBAUEN, false).apply()
        return true
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun rufen(c: Context, methode: String, extras: Bundle?): Bundle? = try {
        c.contentResolver.call(ANLAGE, methode, c.packageName, extras)
            ?.takeIf { !it.containsKey("fehler") && !it.isEmpty }
    } catch (e: Exception) {   // keine Geraete-App, keine Berechtigung
        null
    }

    /** Startseite als JSON (ohne Widget-Kennungen, die gelten nur auf diesem Geraet). */
    fun exportieren(c: Context): JSONObject {
        val items = c.homeScreenGridItemsDB.getAllItems().filter { it.type != ITEM_TYPE_SHORTCUT }
        val a = JSONArray()
        items.sortedWith(compareBy({ it.parentId != null }, { it.page }, { it.top }, { it.left })).forEach {
            a.put(JSONObject()
                .put("k", it.id).put("p", it.parentId ?: JSONObject.NULL)
                .put("l", it.left).put("t", it.top).put("r", it.right).put("b", it.bottom).put("pg", it.page)
                .put("pkg", it.packageName).put("act", it.activityName).put("titel", it.title)
                .put("typ", it.type).put("cls", it.className).put("dock", it.docked))
        }
        return JSONObject()
            .put("spalten", c.config.homeColumnCount).put("zeilen", c.config.homeRowCount)
            .put("elemente", a)
    }

    /** Vergleichbar ohne die lokalen Kennungen. */
    private fun signatur(j: JSONObject): String {
        val k = JSONObject(j.toString())
        val a = k.getJSONArray("elemente")
        val ids = HashMap<Long, Int>()
        for (i in 0 until a.length()) ids[a.getJSONObject(i).optLong("k")] = i
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            e.put("k", i)
            if (!e.isNull("p")) e.put("p", ids[e.optLong("p")] ?: -1)
        }
        return k.toString().hashCode().toString()
    }

    /**
     * Nach der Anmeldung einmal pro Sitzung: Startseite der Person holen.
     * @return true, wenn die Startseite ersetzt wurde (Ansicht neu aufbauen)
     */
    fun ladenWennNoetig(c: Context): Boolean {
        val p = prefs(c)
        if (p.getBoolean(GELADEN, false)) return false
        val b = rufen(c, "einstellungen_holen", null) ?: return false   // niemand angemeldet
        p.edit().putBoolean(GELADEN, true).apply()
        // Launcher-Einstellungen der Person (eigener Schluessel auf der Anlage)
        if (AnlageEinstellungen.jetztHolen(c)) p.edit().putBoolean(NEU_AUFBAUEN, true).apply()
        val werte = JSONObject(b.getString("werte") ?: "{}")
        val startseite = werte.optJSONObject("startseite")?.optString("v")?.takeIf { it.isNotEmpty() }
            ?: return false   // Person hat noch keine: Standard bleibt, wird beim Verlassen gespeichert
        return try {
            anwenden(c, JSONObject(startseite))
            p.edit().putString(SIGNATUR, signatur(exportieren(c))).apply()
            Log.i(TAG, "Startseite der Person uebernommen")
            true
        } catch (e: Exception) {
            Log.w(TAG, "anwenden: $e")
            false
        }
    }

    /** Beim Verlassen der Startseite: hochladen, wenn geaendert (nur nach dem Laden). */
    fun senden(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean(GELADEN, false)) return   // sonst ueberschriebe der Standard die Startseite der Person
        val j = exportieren(c)
        val sig = signatur(j)
        if (sig == p.getString(SIGNATUR, null)) return
        val werte = JSONObject().put("startseite", JSONObject().put("t", "s").put("v", j.toString()))
        val b = rufen(c, "einstellungen_senden", Bundle().apply {
            putLong("geaendert", System.currentTimeMillis())
            putString("werte", werte.toString())
        })
        if (b != null) {
            p.edit().putString(SIGNATUR, sig).apply()
            Log.i(TAG, "Startseite gespeichert")
        }
    }

    private fun alleEntfernen(c: Context) {
        val db = c.homeScreenGridItemsDB
        val host = AppWidgetHost(c, WIDGET_HOST_ID)
        db.getAllItems().forEach {
            if (it.type == ITEM_TYPE_WIDGET && it.widgetId > 0) {
                try { host.deleteAppWidgetId(it.widgetId) } catch (_: Exception) {}
            }
            db.deleteById(it.id!!)
        }
    }

    /** Startseite ersetzen. Widgets werden beim naechsten Aufbau neu eingebunden. */
    private fun anwenden(c: Context, j: JSONObject) {
        alleEntfernen(c)
        val db = c.homeScreenGridItemsDB
        val a = j.getJSONArray("elemente")
        val neueIds = HashMap<Long, Long>()
        val widgets = JSONArray()
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            val typ = e.getInt("typ")
            if (typ == ITEM_TYPE_WIDGET) {
                widgets.put(e)
                continue
            }
            val parent = if (e.isNull("p")) null else neueIds[e.getLong("p")] ?: continue
            val id = db.insert(element(e, parent))
            neueIds[e.getLong("k")] = id
        }
        prefs(c).edit().putString(AUSSTEHEND, widgets.toString()).putBoolean("standard_v1", true)
            .putBoolean(NEU_AUFBAUEN, true).apply()
        c.config.wasHomeScreenInit = true
    }

    fun element(e: JSONObject, parent: Long?) = HomeScreenGridItem(
        id = null, left = e.getInt("l"), top = e.getInt("t"), right = e.getInt("r"), bottom = e.getInt("b"),
        page = e.getInt("pg"), packageName = e.getString("pkg"), activityName = e.optString("act"),
        title = e.optString("titel"), type = e.getInt("typ"), className = e.optString("cls"), widgetId = -1,
        shortcutId = "", icon = null, docked = e.optBoolean("dock"), parentId = parent
    )

    /** Abmelden: zurueck auf den Standard (Standard-Symbole, Uhr, Kamera). */
    fun zuruecksetzen(c: Context) {
        alleEntfernen(c)
        AnlageEinstellungen.zuruecksetzen(c)
        prefs(c).edit().remove(GELADEN).remove(SIGNATUR).remove(AUSSTEHEND).putBoolean("standard_v1", false)
            .putBoolean(NEU_AUFBAUEN, true).apply()
        c.config.wasHomeScreenInit = false
        Log.i(TAG, "Startseite auf Standard zurueckgesetzt")
    }

    /** Nachricht der Geraete-App (nur mit Signatur-Berechtigung ANLAGE). */
    class Empfaenger : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action != AKTION) return
            val fertig = goAsync()
            Thread {
                try {
                    when (i.getStringExtra("was")) {
                        "abmelden" -> zuruecksetzen(c)
                        "anmelden" -> prefs(c).edit().remove(GELADEN).apply()
                    }
                } finally {
                    fertig.finish()
                }
            }.start()
        }
    }
}
