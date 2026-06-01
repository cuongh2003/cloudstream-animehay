package recloudstream

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType // Import thêm định dạng link của SDK
import java.util.ArrayList

class AnimeHayProvider : MainAPI() {
    override var mainUrl = "https://animehay04.site"
    override var name = "AnimeHay"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val hasMainPage = true
    override var lang = "vi"

    private val defHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = ArrayList<SearchResponse>()

        val url = if (page > 1) "$mainUrl/phim-moi-cap-nhap/trang-$page.html" else mainUrl
        val document = app.get(url, headers = defHeaders).document
        val elements = document.select("div.mc")

        for (element in elements) {
            val linkElement = element.selectFirst("a.mc__link") ?: continue
            val title = linkElement.attr("title").trim()
            val movieUrl = fixUrl(linkElement.attr("href"))
            val posterElement = element.selectFirst(".mc__poster img")
            val posterUrl = posterElement?.attr("src") ?: ""

            list.add(newAnimeSearchResponse(title, movieUrl, TvType.Anime) {
                this.posterUrl = posterUrl
            })
        }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Mới cập nhật",
                    list,
                    true
                )
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/tim-kiem/"
        val document = app.get(
            searchUrl,
            headers = defHeaders,
            params = mapOf("keyword" to query)
        ).document

        val searchResults = ArrayList<SearchResponse>()
        var elements = document.select("div.mc")
        if (elements.isEmpty()) {
            elements = document.select(".movies-list .movie-item, .movies-list .mc")
        }

        for (element in elements) {
            val linkElement = element.selectFirst("a.mc__link, a") ?: continue
            val title = (linkElement.attr("title").takeIf { it.isNotEmpty() }
                ?: element.selectFirst(".mc__name, .name")?.text())?.trim() ?: continue
            val movieUrl = fixUrl(linkElement.attr("href"))
            val posterElement = element.selectFirst("img")
            val posterUrl = posterElement?.attr("src") ?: ""

            searchResults.add(newAnimeSearchResponse(title, movieUrl, TvType.Anime) {
                this.posterUrl = posterUrl
            })
        }
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = defHeaders).document

        val title = (document.selectFirst(".aim-hero__title")?.text()
            ?: document.selectFirst("h1.heading")?.text()
            ?: "Anime").trim()

        val poster = document.selectFirst(".aim-hero__poster img")?.attr("src") ?: ""
        val description = document.selectFirst(".aim-body .description, .description")?.text()?.trim()
            ?: "Không có mô tả."

        val episodesList = ArrayList<com.lagradost.cloudstream3.Episode>()
        val epContainers = document.select(".aim-ep-group")

        if (epContainers.isNotEmpty()) {
            for (container in epContainers) {
                val epElements = container.select(".aim-ep-grid a, a.aim-ep-btn")
                for (ep in epElements) {
                    val epUrl = fixUrl(ep.attr("href"))
                    val epName = ep.selectFirst("span")?.text()?.trim() ?: ep.text().trim()

                    if (epUrl.isNotEmpty() && epUrl.contains("/xem-phim/")) {
                        episodesList.add(newEpisode(epUrl) {
                            this.name = "Tập $epName"
                        })
                    }
                }
            }
        } else {
            val backupElements = document.select(".list-episode a, .episodes a, a.aim-ep-btn")
            for (ep in backupElements) {
                val epUrl = fixUrl(ep.attr("href"))
                val epName = ep.text().trim()
                if (epUrl.isNotEmpty() && epUrl.contains("/xem-phim/")) {
                    episodesList.add(newEpisode(epUrl) {
                        this.name = if (epName.contains("Tập")) epName else "Tập $epName"
                    })
                }
            }
        }

        val sortedEpisodes = episodesList.reversed()

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.episodes = mutableMapOf(DubStatus.Subbed to sortedEpisodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = defHeaders).document
        var linkFound = false

        val scripts = document.select("script")
        for (script in scripts) {
            val htmlData = script.html()

            if (htmlData.contains("M3U8_URL")) {
                val m3u8VarRegex = """M3U8_URL\s*=\s*['"]([^'"]+)['"]""".toRegex()
                val match = m3u8VarRegex.find(htmlData)

                if (match != null) {
                    val rawVideoUrl = match.groupValues[1]
                    val videoUrl = rawVideoUrl.replace("\\/", "/")

                    if (videoUrl.isNotEmpty()) {
                        // FIX LỖI: Loại bỏ hoàn toàn param 'isM3u8' và định dạng qua biến 'type' bên trong block khởi tạo
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "AnimeHay Player",
                                url = videoUrl
                            ) {
                                this.quality = Qualities.Unknown.value
                                this.type = ExtractorLinkType.M3U8 // Xác định luồng phát định dạng M3U8/HLS phát trực tiếp công nghệ cao
                                this.headers = mapOf("Referer" to "$mainUrl/")
                            }
                        )
                        linkFound = true
                        break
                    }
                }
            }
        }

        return linkFound
    }
}