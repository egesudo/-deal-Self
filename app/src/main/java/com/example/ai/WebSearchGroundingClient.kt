package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "WebSearchGrounding"
private const val WEB_LOOKUP_TIMEOUT_MS = 2_800L

data class WebGroundingSnapshot(
    val isGrounded: Boolean,
    val query: String,
    val currentDateTimeSummary: String,
    val snippets: List<String>,
    val sourcesSummary: String
) {
    fun formatForPrompt(): String {
        if (snippets.isEmpty()) {
            return """
                LIVE INTERNET & TIME CONTEXT:
                - Current Local Date & Time: $currentDateTimeSummary
                - Note: Live web search returned no specific snippets for "$query"; rely on verified factual knowledge and clearly state if real-time data is unavailable.
            """.trimIndent()
        }
        val bulletLines = snippets.take(5).joinToString("\n") { "- $it" }
        return """
            LIVE INTERNET & WEB GROUNDING RESULTS (Retrieved in real time for "$query" at $currentDateTimeSummary):
            $bulletLines
            Sources: $sourcesSummary
            INSTRUCTION: Use these up-to-date web facts naturally in your reply while staying in character. Do not read out raw URLs.
        """.trimIndent()
    }
}

/**
 * Lightweight, zero-API-key real-time web grounding client.
 * Queries Google News RSS (for breaking news, weather, sports, prices, current events),
 * Wikipedia REST Search API (for factual entities, history, science, people), and
 * DuckDuckGo Instant Answer API in parallel with a strict 2.8s timeout so normal
 * conversation speed is never compromised.
 */
object WebSearchGroundingClient {

    suspend fun fetchLiveWebContext(
        rawQuery: String,
        preferTurkish: Boolean
    ): WebGroundingSnapshot = withContext(Dispatchers.IO) {
        val cleanedQuery = sanitizeSearchQuery(rawQuery)
        val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.US).format(Date())

        if (cleanedQuery.isBlank()) {
            return@withContext WebGroundingSnapshot(
                isGrounded = false,
                query = rawQuery,
                currentDateTimeSummary = nowFormatted,
                snippets = emptyList(),
                sourcesSummary = ""
            )
        }

        val combinedSnippets = mutableListOf<String>()
        val activeSources = mutableListOf<String>()

        withTimeoutOrNull(WEB_LOOKUP_TIMEOUT_MS) {
            coroutineScope {
                val newsDeferred = async { fetchGoogleNewsHeadlines(cleanedQuery, preferTurkish) }
                val wikiDeferred = async { fetchWikipediaSnippets(cleanedQuery, preferTurkish) }
                val ddgDeferred = async { fetchDuckDuckGoInstantAnswer(cleanedQuery) }

                val ddgResults = ddgDeferred.await()
                if (ddgResults.isNotEmpty()) {
                    combinedSnippets.addAll(ddgResults)
                    activeSources.add("DuckDuckGo Instant")
                }

                val newsResults = newsDeferred.await()
                if (newsResults.isNotEmpty()) {
                    combinedSnippets.addAll(newsResults)
                    activeSources.add("Google News")
                }

                val wikiResults = wikiDeferred.await()
                if (wikiResults.isNotEmpty()) {
                    combinedSnippets.addAll(wikiResults)
                    activeSources.add(if (preferTurkish) "Vikipedi" else "Wikipedia")
                }
            }
        }

        val distinctSnippets = combinedSnippets.distinct().take(5)
        WebGroundingSnapshot(
            isGrounded = distinctSnippets.isNotEmpty(),
            query = cleanedQuery,
            currentDateTimeSummary = nowFormatted,
            snippets = distinctSnippets,
            sourcesSummary = if (activeSources.isEmpty()) "Live Web" else activeSources.joinToString(", ")
        )
    }

    internal fun sanitizeSearchQuery(raw: String): String {
        val conversationalPrefixes = listOf(
            "bana söyle", "sence", "acaba", "peki", "hey nova", "selam nova",
            "internette araştır", "webde ara", "google'da ara", "tell me",
            "can you search", "look up", "what is the", "do you know"
        )
        var working = raw.trim()
        for (prefix in conversationalPrefixes) {
            if (working.lowercase().startsWith(prefix)) {
                working = working.substring(prefix.length).trim()
            }
        }
        return working.replace(Regex("[?!.]+$"), "").trim().take(120)
    }

    private fun fetchGoogleNewsHeadlines(query: String, preferTurkish: Boolean): List<String> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val langParams = if (preferTurkish) "hl=tr&gl=TR&ceid=TR:tr" else "hl=en-US&gl=US&ceid=US:en"
            val urlString = "https://news.google.com/rss/search?q=$encoded&$langParams"
            val xml = httpGetText(urlString) ?: return emptyList()
            parseRssItems(xml, maxItems = 3)
        } catch (e: Exception) {
            Log.d(TAG, "Google News RSS lookup skipped: ${e.message}")
            emptyList()
        }
    }

    private fun fetchWikipediaSnippets(query: String, preferTurkish: Boolean): List<String> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val host = if (preferTurkish) "tr.wikipedia.org" else "en.wikipedia.org"
            val urlString =
                "https://$host/w/api.php?action=query&list=search&srsearch=$encoded&utf8=1&format=json&srlimit=2"
            val jsonStr = httpGetText(urlString) ?: return emptyList()
            val root = JSONObject(jsonStr)
            val searchArr = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            val results = mutableListOf<String>()
            for (i in 0 until searchArr.length()) {
                val item = searchArr.getJSONObject(i)
                val title = item.optString("title", "")
                val snippetHtml = item.optString("snippet", "")
                val cleanSnippet = snippetHtml
                    .replace(Regex("<[^>]*>"), "")
                    .replace("&quot;", "\"")
                    .replace("&#039;", "'")
                    .replace("&amp;", "&")
                    .trim()
                if (title.isNotBlank() && cleanSnippet.isNotBlank()) {
                    results.add("[Wiki: $title] $cleanSnippet")
                }
            }
            results
        } catch (e: Exception) {
            Log.d(TAG, "Wikipedia lookup skipped: ${e.message}")
            emptyList()
        }
    }

    private fun fetchDuckDuckGoInstantAnswer(query: String): List<String> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val jsonStr = httpGetText(urlString) ?: return emptyList()
            val json = JSONObject(jsonStr)
            val results = mutableListOf<String>()

            val abstractText = json.optString("AbstractText", "").trim()
            if (abstractText.isNotBlank()) {
                results.add(abstractText.take(280))
            }
            val answer = json.optString("Answer", "").trim()
            if (answer.isNotBlank()) {
                results.add(answer.take(220))
            }
            val related = json.optJSONArray("RelatedTopics")
            if (related != null && results.isEmpty()) {
                for (i in 0 until minOf(2, related.length())) {
                    val topic = related.optJSONObject(i)
                    val text = topic?.optString("Text", "").orEmpty().trim()
                    if (text.isNotBlank()) {
                        results.add(text.take(220))
                    }
                }
            }
            results
        } catch (e: Exception) {
            Log.d(TAG, "DuckDuckGo lookup skipped: ${e.message}")
            emptyList()
        }
    }

    private fun httpGetText(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1_800
                readTimeout = 1_800
                setRequestProperty("User-Agent", "IdealSelfCompanion/1.0 (Android)")
                setRequestProperty("Accept", "application/json, application/rss+xml, text/xml, */*")
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseRssItems(xmlContent: String, maxItems: Int = 3): List<String> {
        val headlines = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var insideItem = false
            var currentTitle = ""
            var currentPubDate = ""

            while (eventType != XmlPullParser.END_DOCUMENT && headlines.size < maxItems) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name.lowercase()
                        if (tagName == "item") {
                            insideItem = true
                            currentTitle = ""
                            currentPubDate = ""
                        } else if (insideItem && tagName == "title") {
                            currentTitle = parser.nextText().trim()
                        } else if (insideItem && tagName == "pubdate") {
                            currentPubDate = parser.nextText().trim()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.lowercase() == "item" && insideItem) {
                            insideItem = false
                            if (currentTitle.isNotBlank()) {
                                val dateSuffix = if (currentPubDate.isNotBlank()) " (${currentPubDate.take(16)})" else ""
                                headlines.add("[News] $currentTitle$dateSuffix")
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }
        return headlines
    }
}
