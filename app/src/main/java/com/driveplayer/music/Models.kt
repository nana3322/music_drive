package com.driveplayer.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Node(val id: String, val name: String, val isFolder: Boolean)
data class Song(val id: String, val name: String)

/** 목록 안의 항목. type = "folder" | "song" */
data class Item(val type: String, val id: String, val name: String) {
    val isFolder: Boolean get() = type == "folder"
}

data class ListData(val id: String, val name: String, val items: List<Item>)

/** folderKind=false: 재생목록, true: 폴더 목록 */
data class ListRef(val folderKind: Boolean, val id: String)

fun titleOf(name: String): String = name.substringBeforeLast('.', name)
fun Node.toItem() = Item(if (isFolder) "folder" else "song", id, name)

fun countText(items: List<Item>): String {
    val f = items.count { it.isFolder }
    val s = items.size - f
    val parts = ArrayList<String>()
    if (f > 0) parts.add("폴더 ${f}개")
    if (s > 0) parts.add("곡 ${s}개")
    return if (parts.isEmpty()) "비어 있음" else parts.joinToString(" · ")
}

fun fmtTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0L)) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("drivemusic", Context.MODE_PRIVATE)

    var folderLink: String
        get() = sp.getString("link", "") ?: ""
        set(v) { sp.edit().putString("link", v).apply() }

    var apiKey: String
        get() = sp.getString("key", "") ?: ""
        set(v) { sp.edit().putString("key", v).apply() }

    private fun itemsToJson(items: List<Item>): JSONArray {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("t", it.type).put("id", it.id).put("n", it.name)) }
        return a
    }

    private fun jsonToItems(a: JSONArray): List<Item> =
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Item(o.getString("t"), o.getString("id"), o.getString("n"))
        }

    fun saveItems(key: String, items: List<Item>) {
        sp.edit().putString(key, itemsToJson(items).toString()).apply()
    }

    fun loadItems(key: String): List<Item> = try {
        jsonToItems(JSONArray(sp.getString(key, "[]")))
    } catch (e: Exception) {
        emptyList()
    }

    fun saveLists(key: String, lists: List<ListData>) {
        val arr = JSONArray()
        lists.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("items", itemsToJson(it.items)))
        }
        sp.edit().putString(key, arr.toString()).apply()
    }

    fun loadLists(key: String): List<ListData> = try {
        val a = JSONArray(sp.getString(key, "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            ListData(o.getString("id"), o.getString("name"), jsonToItems(o.getJSONArray("items")))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveArtists(m: Map<String, String>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("artists", o.toString()).apply()
    }

    fun loadArtists(): Map<String, String> = try {
        val o = JSONObject(sp.getString("artists", "{}"))
        val out = HashMap<String, String>()
        o.keys().forEach { k -> out[k] = o.getString(k) }
        out
    } catch (e: Exception) {
        emptyMap()
    }
}
