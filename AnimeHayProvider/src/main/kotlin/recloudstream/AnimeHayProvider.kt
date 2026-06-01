package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import java.util.ArrayList

class AnimeHayProvider : MainAPI() {
    override var mainUrl = "https://animehay04.site"
    override var name = "AnimeHay"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = false

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie
    )

    // Cấu hình Header giả lập trình duyệt để tránh bị chặn kết nối
    private val defHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to mainUrl
    )

    // 1. LUỒNG XỬ LÝ TRANG CHỦ (Bám sát theo cấu trúc HTML mới div.mc)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page > 1) "$mainUrl/phim-moi-cap-nhap/trang-$page.html" else mainUrl
        val document = app.get(url, headers = defHeaders).document
        val homeProducts = ArrayList<SearchResponse>()

        val elements = document.select("div.mc")
        for (element in elements) {
            val linkElement = element.selectFirst("a.mc__link") ?: continue
            val title = linkElement.attr("title").trim()
            val movieUrl = fixUrl(linkElement.attr("href"))

            val posterElement = element.selectFirst(".mc__poster img")
            val posterUrl = posterElement?.attr("src") ?: ""

            val epBadge = element.selectFirst(".mc__ep-badge")?.text()?.trim() ?: ""

            homeProducts.add(newAnimeSearchResponse(title, movieUrl, TvType.Anime) {
                this.posterUrl = posterUrl
                this.meta = epBadge
            })
        }

        return if (homeProducts.isNotEmpty()) {
            newHomePageResponse(listOf(HomePageList("Mới cập nhật", homeProducts)), hasNext = true)
        } else {
            null
        }
    }

    // 2. LUỒNG XỬ LÝ TÌM KIẾM (Đồng bộ cấu trúc dạng thẻ .mc nếu trang tìm kiếm dùng chung giao diện)
    override suspend fun search(query: String): List<SearchResponse> {
        // Encode từ khóa tìm kiếm để tránh lỗi font tiếng Việt khi gửi lên URL
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$mainUrl/tim-kiem/$encodedQuery"

        val document = app.get(searchUrl, headers = defHeaders).document
        val searchResults = ArrayList<SearchResponse>()

        // Thử tìm theo cấu trúc mới 'div.mc', nếu không có sẽ tự động fallback sang cấu trúc cũ đề phòng
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

    // 3. LUỒNG TẢI CHI TIẾT PHIM & DANH SÁCH TẬP
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = defHeaders).document

        // Bóc tách tiêu đề phim từ trang thông tin phim
        val title = (document.selectFirst("h1.heading, .info-movie .title")?.text()
            ?: document.selectFirst(".name")?.text() ?: "Anime").trim()

        val poster = document.selectFirst(".info-movie .poster img, .poster img")?.attr("src") ?: ""
        val description = document.selectFirst(".description, .info-movie .summary")?.text()?.trim()

        val episodes = ArrayList<Episode>()

        // Tìm các thẻ chứa danh sách tập phim (thường nằm trong cụm nút bấm chọn tập)
        val epElements = document.select(".list-episode a, .episodes a")
        for (ep in epElements) {
            val epUrl = fixUrl(ep.attr("href"))
            val epName = ep.text().trim()
            epEpisodes.add(newEpisode(epUrl) {
                this.name = epName
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.episodes = epEpisodes
        }
    }

    // 4. LUỒNG LẤY LINK VIDEO ĐỂ PHÁT PHIM
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = defHeaders).document

        // Tìm các đoạn mã script chứa cấu hình luồng phát phát video (M3U8 hoặc MP4)
        val scripts = document.select("script")
        for (script in scripts) {
            val htmlData = script.html()
            if (htmlData.contains("file:") || htmlData.contains("source")) {
                // Sử dụng Regex tìm chuỗi link định dạng phát trực tuyến
                val m3u8Regex = """(https?://[^\s"'<>]+?\.m3u8[^\s"']*)""".toRegex()
                val match = m3u8Regex.find(htmlData)
                if (match != null) {
                    val videoUrl = match.value
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            this.name,
                            videoUrl,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8 = true
                        )
                    )
                    return true
                }
            }
        }
        return false
    }
}