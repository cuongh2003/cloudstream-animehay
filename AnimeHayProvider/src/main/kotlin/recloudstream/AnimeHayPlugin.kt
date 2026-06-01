package recloudstream

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class AnimeHayPlugin: BasePlugin() {
    override fun load() {
        // Đăng ký Provider xử lý cào dữ liệu chính
        registerMainAPI(AnimeHayProvider())
    }
}