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

class AnimeHayProvider : MainAPI() {
    override var mainUrl = "https://animehay04.site"
    override var name = "AnimeHay"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = ArrayList<SearchResponse>()
        // Cấu trúc trả về chuẩn theo mẫu Dailymotion
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
        return arrayListOf()
    }

    override suspend fun load(url: String): LoadResponse? {
        return null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean, // Sửa thành isCasting theo signature mới của mẫu
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return true
    }
}