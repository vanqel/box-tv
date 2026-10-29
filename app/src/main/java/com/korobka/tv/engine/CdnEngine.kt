package com.korobka.tv.engine

import com.korobka.tv.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object CdnEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val CDN_HOSTS = listOf(
        "https://api.namy.ws",
        "https://api.stiven-king.com",
        "https://api.femd.ws"
    )

    suspend fun resolveStream(
        kpOrImdbId: String,
        isSeries: Boolean,
        seasonNum: Int = 1,
        episodeNum: Int = 1
    ): StreamSource? = withContext(Dispatchers.IO) {
        for (host in CDN_HOSTS) {
            val url = if (kpOrImdbId.startsWith("tt")) {
                "$host/embed/imdb/$kpOrImdbId"
            } else {
                "$host/embed/kp/$kpOrImdbId"
            }

            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "$host/")
                    .build()

                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue
                val html = resp.body?.string() ?: continue

                val seasons = mutableListOf<Season>()
                var episodeHls = ""
                val translations = mutableListOf<Translation>()

                val seasonsIdx = html.indexOf("seasons:[")
                if (seasonsIdx != -1) {
                    val start = seasonsIdx + 8
                    val stack = mutableListOf<Char>()
                    var end = -1
                    for (i in start until html.length) {
                        val ch = html[i]
                        if (ch == '[') stack.add(ch)
                        else if (ch == ']') {
                            if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                            if (stack.isEmpty()) {
                                end = i
                                break
                            }
                        }
                    }

                    if (end != -1) {
                        val sJson = html.substring(start, end + 1)
                        val arr = JSONArray(sJson)
                        for (i in 0 until arr.length()) {
                            val sObj = arr.getJSONObject(i)
                            val sNum = sObj.optInt("season", 1)
                            val epList = mutableListOf<Episode>()
                            val epsArr = sObj.optJSONArray("episodes") ?: JSONArray()
                            for (j in 0 until epsArr.length()) {
                                val epObj = epsArr.getJSONObject(j)
                                val epNum = epObj.optInt("episode", j + 1)
                                val hls = epObj.optString("hls", "")

                                if (sNum == seasonNum && epNum == episodeNum) {
                                    if (hls.isNotEmpty()) episodeHls = hls
                                    val audioObj = epObj.optJSONObject("audio")
                                    val namesArr = audioObj?.optJSONArray("names")
                                    if (namesArr != null) {
                                        for (k in 0 until namesArr.length()) {
                                            val name = namesArr.getString(k)
                                            if (name != "delete") {
                                                translations.add(Translation("tr_$k", name))
                                            }
                                        }
                                    }
                                }

                                epList.add(Episode(epNum, "Серия $epNum", hls))
                            }
                            seasons.add(Season(sNum, epList))
                        }
                    }
                }

                seasons.sortBy { it.seasonNumber }

                var movieHls = ""
                val hlsMatcher = Pattern.compile("hls\\s*:\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']").matcher(html)
                if (hlsMatcher.find()) {
                    movieHls = hlsMatcher.group(1) ?: ""
                }

                if (translations.isEmpty()) {
                    val audioIdx = html.indexOf("names\":[")
                    if (audioIdx != -1) {
                        val start = html.indexOf('[', audioIdx)
                        val end = html.indexOf(']', start)
                        if (start != -1 && end != -1) {
                            val arr = JSONArray(html.substring(start, end + 1))
                            for (k in 0 until arr.length()) {
                                val name = arr.getString(k)
                                if (name != "delete") {
                                    translations.add(Translation("tr_$k", name))
                                }
                            }
                        }
                    }
                }

                if (translations.isEmpty()) {
                    translations.add(Translation("default", "Русский дубляж"))
                }

                val finalStream = if (isSeries && episodeHls.isNotEmpty()) {
                    episodeHls
                } else if (movieHls.isNotEmpty()) {
                    movieHls
                } else {
                    url
                }

                val isHls = finalStream.contains(".m3u8")
                return@withContext StreamSource(
                    streamUrl = finalStream,
                    isHls = isHls,
                    referer = "$host/",
                    seasons = seasons,
                    translations = translations,
                    currentSeason = seasonNum,
                    currentEpisode = episodeNum,
                    currentTranslation = translations.firstOrNull()?.name ?: "Русский дубляж"
                )
            } catch (e: Exception) {
                continue
            }
        }
        null
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()

        if (q.all { it.isDigit() }) {
            return@withContext listOf(
                MediaItem(
                    id = q,
                    title = "Фильм ID $q",
                    poster = "https://st.kp.yandex.net/images/film_iphone/iphone360_$q.jpg",
                    ratingKp = 8.0f,
                    genres = listOf("Кино")
                )
            )
        }

        val results = mutableListOf<MediaItem>()
        try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val wikiUrl = "https://ru.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=8&format=json"

            val req = Request.Builder()
                .url(wikiUrl)
                .header("User-Agent", "KorobkaTV/1.0 (https://korobka.tv)")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()

            val wikiJson = JSONArray(resp.body?.string() ?: return@withContext emptyList())
            val titlesArr = wikiJson.optJSONArray(1) ?: return@withContext emptyList()

            val titles = mutableListOf<String>()
            for (i in 0 until titlesArr.length()) {
                titles.add(titlesArr.getString(i))
            }
            if (titles.isEmpty()) return@withContext emptyList()

            val titlesJoined = URLEncoder.encode(titles.joinToString("|"), "UTF-8")
            val entUrl = "https://www.wikidata.org/w/api.php?action=wbgetentities&sites=ruwiki&titles=$titlesJoined&props=claims|labels|descriptions&format=json"

            val entReq = Request.Builder()
                .url(entUrl)
                .header("User-Agent", "KorobkaTV/1.0 (https://korobka.tv)")
                .build()

            val entResp = client.newCall(entReq).execute()
            if (!entResp.isSuccessful) return@withContext emptyList()

            val entJson = JSONObject(entResp.body?.string() ?: return@withContext emptyList())
            val entities = entJson.optJSONObject("entities") ?: return@withContext emptyList()

            val keys = entities.keys()
            while (keys.hasNext()) {
                val qid = keys.next()
                val ent = entities.getJSONObject(qid)
                val claims = ent.optJSONObject("claims") ?: continue

                val kpClaims = claims.optJSONArray("P2603")
                val kpId = kpClaims?.optJSONObject(0)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")

                val imdbClaims = claims.optJSONArray("P345")
                val imdbId = imdbClaims?.optJSONObject(0)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")

                val finalId = kpId ?: imdbId ?: continue

                val labels = ent.optJSONObject("labels")
                val ruTitle = labels?.optJSONObject("ru")?.optString("value")
                val enTitle = labels?.optJSONObject("en")?.optString("value")
                val title = ruTitle ?: enTitle ?: continue

                val desc = ent.optJSONObject("descriptions")?.optJSONObject("ru")?.optString("value") ?: ""
                val isSeries = desc.contains("сериал", ignoreCase = true) || claims.has("P31") && claims.toString().contains("Q5398426")

                val poster = if (kpId != null) {
                    "https://st.kp.yandex.net/images/film_iphone/iphone360_$kpId.jpg"
                } else null

                results.add(
                    MediaItem(
                        id = finalId,
                        title = title,
                        origTitle = enTitle,
                        poster = poster,
                        backdrop = poster,
                        description = desc.ifEmpty { null },
                        isSeries = isSeries,
                        ratingKp = 8.0f
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }
}
