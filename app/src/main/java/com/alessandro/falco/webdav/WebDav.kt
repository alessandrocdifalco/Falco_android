package com.alessandro.falco.webdav

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alessandro.falco.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

data class WebDavConfig(val name: String = "Server WebDAV", val serverUrl: String = "", val username: String = "", val password: String = "") {
    val ready get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}
data class WebDavItem(val name: String, val path: String, val url: String, val directory: Boolean, val size: Long = 0, val modified: Long = 0)

class WebDavStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "falco_webdav", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun load() = WebDavConfig(prefs.getString("name", "Server WebDAV").orEmpty(), prefs.getString("url", "").orEmpty(), prefs.getString("user", "").orEmpty(), prefs.getString("password", "").orEmpty())
    fun save(c: WebDavConfig) { prefs.edit().putString("name", c.name).putString("url", c.serverUrl.trimEnd('/')).putString("user", c.username).putString("password", c.password).apply() }
    fun clear() = prefs.edit().clear().apply()
}

class WebDavClient(private val config: WebDavConfig) {
    private val http = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
    private val audio = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "aif", "aiff", "wma", "alac")
    fun authorization() = "Basic " + Base64.encodeToString("${config.username}:${config.password}".toByteArray(), Base64.NO_WRAP)

    suspend fun test(): Result<Unit> = withContext(Dispatchers.IO) { runCatching { list("/"); Unit } }
    suspend fun list(path: String): List<WebDavItem> = withContext(Dispatchers.IO) {
        val requestUrl = url(path)
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
        val request = Request.Builder().url(requestUrl).header("Authorization", Credentials.basic(config.username, config.password)).header("Depth", "1").method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 207) error("WebDAV HTTP ${response.code}: ${response.message}")
            parse(response.body?.string().orEmpty(), requestUrl).filterNot { normalize(it.url) == normalize(requestUrl) }
        }
    }

    suspend fun audioFiles(path: String, recursive: Boolean, progress: (Int, String) -> Unit = { _, _ -> }): List<WebDavItem> = withContext(Dispatchers.IO) {
        val output = mutableListOf<WebDavItem>(); val queue = ArrayDeque<String>(); queue += path; var visited = 0
        while (queue.isNotEmpty()) { val current = queue.removeFirst(); progress(++visited, current); list(current).forEach { if (it.directory && recursive) queue += it.path else if (!it.directory && it.name.substringAfterLast('.', "").lowercase() in audio) output += it } }
        output
    }

    fun toTrack(item: WebDavItem): TrackEntity = TrackEntity(
        uri = item.url, folderUri = "webdav:${config.name}", displayName = item.name, title = item.name.substringBeforeLast('.'),
        artist = "Artista da revisionare", album = "WebDAV", genre = "Da classificare", year = null, durationMs = 0,
        relativePath = item.path, sizeBytes = item.size, modifiedAt = item.modified, format = item.name.substringAfterLast('.', "").uppercase(), workStatus = "DA_VALUTARE"
    )

    private fun url(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = config.serverUrl.trimEnd('/')
        return base + "/" + path.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }
    }
    private fun parse(xml: String, requestUrl: String): List<WebDavItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(StringReader(xml)) }
        val out = mutableListOf<WebDavItem>(); var href = ""; var name = ""; var size = 0L; var modified = 0L; var directory = false; var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) when (parser.name.substringAfter(':').lowercase()) {
                "response" -> { href = ""; name = ""; size = 0; modified = 0; directory = false }
                "href" -> href = parser.nextText()
                "displayname" -> name = parser.nextText()
                "collection" -> directory = true
                "getcontentlength" -> size = parser.nextText().toLongOrNull() ?: 0
                "getlastmodified" -> modified = runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(parser.nextText())?.time ?: 0 }.getOrDefault(0)
            } else if (event == XmlPullParser.END_TAG && parser.name.substringAfter(':').equals("response", true) && href.isNotBlank()) {
                val decoded = URLDecoder.decode(href, "UTF-8"); val absolute = if (decoded.startsWith("http")) decoded else Uri.parse(requestUrl).buildUpon().encodedPath(Uri.parse(decoded).encodedPath).build().toString()
                val cleanPath = decoded.substringAfter(Uri.parse(config.serverUrl).path.orEmpty()).let { "/" + it.trim('/') + if (directory) "/" else "" }
                out += WebDavItem(name.ifBlank { decoded.trimEnd('/').substringAfterLast('/') }, cleanPath, absolute, directory, size, modified)
            }
            event = parser.next()
        }
        return out
    }
    private fun normalize(v: String) = v.trimEnd('/').let { Uri.decode(it) }
}
