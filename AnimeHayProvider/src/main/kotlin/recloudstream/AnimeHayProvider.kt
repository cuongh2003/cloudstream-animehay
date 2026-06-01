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
import com.lagradost.cloudstream3.utils.ExtractorLinkType
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

        // SỬA SELECTOR TRANG CHỦ: Quét đa lớp bao gồm cả các khối chứa movie mới
        val elements = document.select(".movies-list .movie-item, div.mc, .list-films .film-item")

        for (element in elements) {
            val linkElement = element.selectFirst("a.mc__link, a") ?: continue
            val title = (linkElement.attr("title").takeIf { it.isNotEmpty() }
                ?: element.selectFirst(".mc__name, .name, .title")?.text())?.trim() ?: continue
            val movieUrl = fixUrl(linkElement.attr("href"))

            val posterElement = element.selectFirst("img")
            val posterUrl = posterElement?.attr("src") ?: posterElement?.attr("data-src") ?: ""

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

        // SỬA SELECTOR TÌM KIẾM: Đa dạng hóa bộ chọn để bọc hết các thẻ phim
        val elements = document.select(".movies-list .movie-item, div.mc, .list-films .film-item")

        for (element in elements) {
            val linkElement = element.selectFirst("a.mc__link, a") ?: continue
            val title = (linkElement.attr("title").takeIf { it.isNotEmpty() }
                ?: element.selectFirst(".mc__name, .name, .title")?.text())?.trim() ?: continue
            val movieUrl = fixUrl(linkElement.attr("href"))

            val posterElement = element.selectFirst("img")
            val posterUrl = posterElement?.attr("src") ?: posterElement?.attr("data-src") ?: ""

            searchResults.add(newAnimeSearchResponse(title, movieUrl, TvType.Anime) {
                this.posterUrl = posterUrl
            })
        }
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = defHeaders).document

        val title = (document.selectFirst(".aim-hero__title")?.text()
            ?: document.selectFirst("h1.heading, .info-movie .title")?.text()
            ?: "Anime").trim()

        val poster = document.selectFirst(".aim-hero__poster img, .info-movie img")?.attr("src") ?: ""
        val description = document.selectFirst(".aim-body .description, .description, .content-film")?.text()?.trim()
            ?: "Không có mô tả."

        val episodesList = ArrayList<com.lagradost.cloudstream3.Episode>()

        // SỬA SELECTOR DANH SÁCH TẬP: Quét sạch cả cấu trúc cũ lẫn mới của server
        val epElements = document.select(".aim-ep-group .aim-ep-grid a, a.aim-ep-btn, .list-episode a, .episodes a")

        for (ep in epElements) {
            val epUrl = fixUrl(ep.attr("href"))
            val epName = ep.selectFirst("span")?.text()?.trim() ?: ep.text().trim()

            if (epUrl.isNotEmpty() && (epUrl.contains("/xem-phim/") || epUrl.contains("/tap-"))) {
                episodesList.add(newEpisode(epUrl) {
                    this.name = if (epName.contains("Tập")) epName else "Tập $epName"
                })
            }
        }

        // Kiểm tra thứ tự tập để tránh bị đảo lộn trên giao diện
        val sortedEpisodes = if (episodesList.isNotEmpty() && episodesList.first().name?.contains("1") == false) {
            episodesList.reversed()
        } else {
            episodesList
        }

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
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "AnimeHay Player",
                                url = videoUrl
                            ) {
                                this.quality = Qualities.Unknown.value
                                this.type = ExtractorLinkType.M3U8
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