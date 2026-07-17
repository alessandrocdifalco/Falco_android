package com.alessandro.falco.desktop

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

class WebDavClient(private val config: WebDavConfig) {
    private val http = OkHttpClient.Builder().build()
    private val auth = Credentials.basic(config.username, config.password)
    private val formats = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "aiff", "aif")

    /** Intentionally read-only: this class exposes only PROPFIND and GET. */
    fun scan(onTrack: (Track) -> Unit) {
        val queue = ArrayDeque<String>().apply { add(config.url.trimEnd('/') + "/") }
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val url = queue.removeFirst()
            if (!visited.add(url)) continue
            list(url).forEach { item ->
                if (item.collection) queue.add(item.url)
                else if (item.extension in formats) onTrack(Track(source = "WEBDAV", uri = item.url, title = item.name.substringBeforeLast('.'), sizeBytes = item.size, format = item.extension.uppercase()))
            }
        }
    }

    fun download(url: String, target: Path): Path {
        val request = Request.Builder().url(url).header("Authorization", auth).get().build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "WebDAV GET ${response.code}" }
            Files.createDirectories(target.parent)
            response.body!!.byteStream().use { input -> Files.newOutputStream(target).use(input::copyTo) }
        }
        return target
    }

    private fun list(url: String): List<Entry> {
        val xml = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>"""
        val request = Request.Builder().url(url).header("Authorization", auth).header("Depth", "1")
            .method("PROPFIND", xml.toRequestBody("application/xml".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            check(response.code == 207 || response.isSuccessful) { "WebDAV PROPFIND ${response.code}" }
            val bytes = response.body!!.bytes()
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val nodes = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).getElementsByTagNameNS("DAV:", "response")
            (0 until nodes.length).mapNotNull { i ->
                val element = nodes.item(i) as Element
                val href = element.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: return@mapNotNull null
                val resolved = URI(url).resolve(href).toString()
                if (resolved.trimEnd('/') == url.trimEnd('/')) return@mapNotNull null
                val collection = element.getElementsByTagNameNS("DAV:", "collection").length > 0
                val name = URLDecoder.decode(URI(resolved).path.substringAfterLast('/').ifBlank { URI(resolved).path.trimEnd('/').substringAfterLast('/') }, StandardCharsets.UTF_8)
                val size = element.getElementsByTagNameNS("DAV:", "getcontentlength").item(0)?.textContent?.toLongOrNull() ?: 0
                Entry(if (collection) resolved.trimEnd('/') + "/" else resolved, name, collection, size, name.substringAfterLast('.', "").lowercase())
            }
        }
    }

    private data class Entry(val url: String, val name: String, val collection: Boolean, val size: Long, val extension: String)
}

