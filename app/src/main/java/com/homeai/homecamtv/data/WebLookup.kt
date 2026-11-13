package com.homeai.homecamtv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Crawls the web for a camera's serial number / name and returns the matching
 * results (title, link, source, snippet).
 *
 * Uses Bing's RSS output, which is a stable XML feed of the same results the
 * search engine shows. No API key required.
 */
object WebLookup {

    data class Result(
        val title: String,
        val url: String,
        val snippet: String
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val SEARCH_URL = "https://www.bing.com/search?format=rss&q="

    /** Runs the crawl. Returns an empty list when the lookup is unavailable. */
    fun search(query: String): List<Result> {
        if (query.isBlank()) return emptyList()
        val url = SEARCH_URL + URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
            )
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            return parse(body)
        }
    }

    /** Parses the RSS `<item>` entries. */
    private fun parse(xml: String): List<Result> {
        val results = mutableListOf<Result>()
        try {
            val parser = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = false
            }.newPullParser()
            parser.setInput(StringReader(xml))

            var event = parser.eventType
            var inItem = false
            var title = ""
            var link = ""
            var description = ""

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "item" -> { inItem = true; title = ""; link = ""; description = "" }
                        "title" -> if (inItem) title = parser.nextText().trim()
                        "link" -> if (inItem) link = parser.nextText().trim()
                        "description" -> if (inItem) description = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (parser.name.equals("item", ignoreCase = true) && inItem) {
                        if (title.isNotEmpty() && link.isNotEmpty()) {
                            results.add(Result(clean(title), link, clean(description)))
                        }
                        inItem = false
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            return results
        }
        return results
    }

    private fun clean(text: String): String =
        text.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
}
