package com.driveplayer.music

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

interface MusicRepo {
    val rootId: String
    val rootName: String
    suspend fun list(folderId: String): List<Node>
    suspend fun songsIn(folderId: String): List<Song>
    suspend fun search(query: String): List<Node>
    suspend fun artist(songId: String): String?
    fun streamUri(songId: String): Uri
    fun refresh()
}

fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var si = i
            while (si < a.length && a[si].isDigit()) si++
            var sj = j
            while (sj < b.length && b[sj].isDigit()) sj++
            val na = a.substring(i, si).trimStart('0')
            val nb = b.substring(j, sj).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
            i = si
            j = sj
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++
            j++
        }
    }
    return (a.length - i) - (b.length - j)
}

fun sortNodes(list: List<Node>): List<Node> =
    list.sortedWith { x, y ->
        if (x.isFolder != y.isFolder) (if (x.isFolder) -1 else 1) else naturalCompare(x.name, y.name)
    }

fun parseFolderId(link: String): String {
    val s = link.trim()
    Regex("/folders/([a-zA-Z0-9_-]+)").find(s)?.let { return it.groupValues[1] }
    Regex("[?&]id=([a-zA-Z0-9_-]+)").find(s)?.let { return it.groupValues[1] }
    return if (s.startsWith("http")) "" else s
}

// ------------------------------------------------------------------ Google Drive (공유 링크 + API 키)

class DriveRepo(private val key: String, private val root: String) : MusicRepo {
    override val rootId: String get() = root
    override val rootName: String get() = "내 음악"

    private val cache = HashMap<String, List<Node>>()
    private var index: List<Node>? = null

    override fun refresh() {
        synchronized(cache) { cache.clear() }
        index = null
    }

    override fun streamUri(songId: String): Uri =
        Uri.parse("https://www.googleapis.com/drive/v3/files/$songId?alt=media&key=$key")

    private fun isAudio(name: String, mime: String): Boolean =
        mime.startsWith("audio/") ||
            Regex(".*\\.(mp3|m4a|aac|flac|wav|ogg|opus)$", RegexOption.IGNORE_CASE).matches(name)

    override suspend fun list(folderId: String): List<Node> = withContext(Dispatchers.IO) {
        if (key.isBlank() || root.isBlank()) {
            throw IOException("설정에서 폴더 링크와 API 키를 입력해 주세요")
        }
        synchronized(cache) { cache[folderId] }?.let { return@withContext it }
        val out = ArrayList<Node>()
        var token: String? = null
        do {
            val q = URLEncoder.encode("'$folderId' in parents and trashed=false", "UTF-8")
            val fields = URLEncoder.encode("nextPageToken,files(id,name,mimeType)", "UTF-8")
            var url = "https://www.googleapis.com/drive/v3/files?q=$q&fields=$fields&pageSize=1000&key=$key"
            if (token != null) url += "&pageToken=" + URLEncoder.encode(token, "UTF-8")
            val json = JSONObject(httpGet(url))
            val files = json.optJSONArray("files")
            if (files != null) {
                for (i in 0 until files.length()) {
                    val f = files.getJSONObject(i)
                    val name = f.getString("name")
                    val mime = f.optString("mimeType")
                    if (mime == "application/vnd.google-apps.folder") {
                        out.add(Node(f.getString("id"), name, true))
                    } else if (isAudio(name, mime)) {
                        out.add(Node(f.getString("id"), name, false))
                    }
                }
            }
            token = json.optString("nextPageToken").ifEmpty { null }
        } while (token != null)
        val sorted = sortNodes(out)
        synchronized(cache) { cache[folderId] = sorted }
        sorted
    }

    override suspend fun songsIn(folderId: String): List<Song> {
        val out = ArrayList<Song>()
        suspend fun walk(id: String) {
            val children = list(id)
            children.filter { !it.isFolder }.forEach { out.add(Song(it.id, it.name)) }
            children.filter { it.isFolder }.forEach { walk(it.id) }
        }
        walk(folderId)
        return out
    }

    override suspend fun search(query: String): List<Node> {
        val idx = index ?: buildIndex().also { index = it }
        val q = query.trim().lowercase()
        return idx.filter { it.name.lowercase().contains(q) }.take(200)
    }

    private suspend fun buildIndex(): List<Node> {
        val out = ArrayList<Node>()
        suspend fun walk(id: String) {
            for (n in list(id)) {
                out.add(n)
                if (n.isFolder) walk(n.id)
            }
        }
        walk(root)
        return out
    }

    /** 파일 앞부분(64KB)만 받아 ID3 태그에서 가수 이름을 읽는다 */
    override suspend fun artist(songId: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(streamUri(songId).toString()).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Range", "bytes=0-65535")
            val code = c.responseCode
            if (code != 200 && code != 206) {
                c.disconnect()
                return@withContext null
            }
            val buf = ByteArray(65536)
            var n = 0
            c.inputStream.use { ins ->
                while (n < buf.size) {
                    val r = ins.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
            }
            c.disconnect()
            Id3.artist(buf.copyOf(n))
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (code !in 200..299) throw IOException(explain(code, body))
        return body
    }

    private fun explain(code: Int, body: String): String {
        val msg = try {
            JSONObject(body).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            body.take(120)
        }
        return when (code) {
            400 -> "요청 오류(400): API 키와 폴더 링크를 확인하세요 ($msg)"
            403 -> "접근 거부(403): API 키 제한 또는 Drive API 사용 설정을 확인하세요 ($msg)"
            404 -> "폴더를 찾을 수 없어요(404): 공유가 '링크가 있는 모든 사용자'인지 확인하세요"
            else -> "오류 $code: $msg"
        }
    }
}

// ------------------------------------------------------------------ ID3v2 (가수 이름만)

object Id3 {
    private fun synch(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0x7F) shl 21) or ((b[p + 1].toInt() and 0x7F) shl 14) or
            ((b[p + 2].toInt() and 0x7F) shl 7) or (b[p + 3].toInt() and 0x7F)

    private fun be32(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
            ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)

    private fun be24(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0xFF) shl 16) or ((b[p + 1].toInt() and 0xFF) shl 8) or (b[p + 2].toInt() and 0xFF)

    fun artist(b: ByteArray): String? {
        if (b.size < 10) return null
        if (b[0] != 'I'.code.toByte() || b[1] != 'D'.code.toByte() || b[2] != '3'.code.toByte()) return null
        val ver = b[3].toInt()
        val end = minOf(b.size, 10 + synch(b, 6))
        var p = 10
        if (ver >= 3 && (b[5].toInt() and 0x40) != 0 && p + 4 <= end) {
            p += if (ver == 4) synch(b, p) else be32(b, p) + 4
        }
        while (true) {
            val id: String
            val size: Int
            val body: Int
            if (ver == 2) {
                if (p + 6 > end) break
                id = String(b, p, 3, Charsets.ISO_8859_1)
                size = be24(b, p + 3)
                body = p + 6
            } else {
                if (p + 10 > end) break
                id = String(b, p, 4, Charsets.ISO_8859_1)
                size = if (ver == 4) synch(b, p + 4) else be32(b, p + 4)
                body = p + 10
            }
            if (id[0].code == 0 || size <= 0 || body + size > end) break
            if (id == "TPE1" || id == "TP1") return decode(b, body, size)
            p = body + size
        }
        return null
    }

    private fun decode(b: ByteArray, start: Int, size: Int): String? {
        if (size < 2) return null
        val enc = b[start].toInt()
        val bytes = b.copyOfRange(start + 1, start + size)
        val cs: Charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> if (bytes.any { it.toInt() < 0 }) {
                try { Charset.forName("EUC-KR") } catch (e: Exception) { Charsets.ISO_8859_1 }
            } else Charsets.ISO_8859_1
        }
        val s = String(bytes, cs).split('\u0000').firstOrNull { it.isNotBlank() } ?: return null
        return s.trim { it == '\uFEFF' || it.isWhitespace() }.ifBlank { null }
    }
}
