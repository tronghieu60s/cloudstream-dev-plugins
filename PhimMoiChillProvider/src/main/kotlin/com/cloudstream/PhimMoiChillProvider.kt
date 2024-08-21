package com.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.NiceResponse

class PhimMoiChillProvider(val plugin: PhimMoiChillPlugin) : MainAPI() {
    override var lang = "vi"
    override var name = "Phim Mới Chill"
    override var mainUrl = "https://phimmoichillv.net"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        Pair("${mainUrl}/list/phim-le", "Phim Lẻ/vertical"),
        Pair("${mainUrl}/list/phim-bo", "Phim Bộ/horizontal"),
        Pair("${mainUrl}/genre/phim-tinh-cam", "Phim Tình Cảm/vertical"),
        Pair("${mainUrl}/country/phim-au-my", "Phim Âu - Mỹ/vertical"),
        Pair("${mainUrl}/country/phim-han-quoc", "Phim Hàn Quốc/vertical"),
        Pair("${mainUrl}/country/phim-trung-quoc", "Phim Trung Quốc/vertical"),
    )

    override val hasMainPage = true
    override val hasDownloadSupport = true

    var mainUrlImage = "https://img.phimmoichillv.net"

    private suspend fun request(url: String): NiceResponse {
        return app.get(url)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return this.getMoviesList("${mainUrl}/tim-kiem/${query}", 1)!!
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val name = request.name.split("/")[0]
        val horizontal = request.name.split("/")[1] == "horizontal"

        val homePageList = HomePageList(
            name = name,
            list = this.getMoviesList(request.data, page, horizontal)!!,
            isHorizontalImages = horizontal
        )

        return newHomePageResponse(list = homePageList, hasNext = true)
    }

    override suspend fun load(url: String): LoadResponse {
        val el = this

        try {
            val document = request(url).document

            val name = document.select(".film-info h1[itemprop=name]").text().trim()
            val content = document.select(".film-content #film-content").text().trim()
            val posterUrl = """url\((.*?)\)""".toRegex().find(document.select(".film-info .image").attr("style"))?.groups?.get(1)?.value?: ""

            var casts = listOf<String>()
            var categories = listOf<String>()
            var duration = ""
            val publishYear = """\(\d{4}\)""".toRegex().find(document.select(".film-info h2").text())?.value?.removeSurrounding("(", ")")?.toInt()!!

            val entries = document.select(".entry-meta li")
            val latestEpisode = document.select(".latest-episode")

            for ((index, entry) in entries.withIndex()) {
                val label = entry.select("label").text().trim().lowercase()
                if (label.contains("diễn viên")) {
                    casts = entry.select("a").mapNotNull { it.text().trim() }
                }
                if (label.contains("thể loại")) {
                    categories = entry.select("a").mapNotNull { it.text().trim() }
                }
                if (label.contains("thời lượng")) {
                    duration = entry.text().replace(entry.select("label").text(), "").trim()
                }
            }

            val type = if (latestEpisode.size > 0 || duration.lowercase().contains("tập")) "series" else "single"

            if (type == "single") {
                var dataUrl = document.select(".list-button .btn.btn-see").attr("href")

                return newMovieLoadResponse(name, url, TvType.Movie, dataUrl) {
                    this.plot = content
                    this.year = publishYear
                    this.tags = categories
                    this.recommendations = el.getMoviesList("${mainUrl}/list/phim-le", 1)
                    addPoster(el.getImageUrl(posterUrl))
                    addActors(casts.mapNotNull { cast -> Actor(cast, "") })
                }
            }

            if (type == "series") {
                var episodes = listOf<Episode>()
                var dataUrl = document.select(".list-button .btn.btn-see").attr("href")
                if (!dataUrl.isNullOrEmpty()) {
                    val document = request(dataUrl).document

                    episodes = document.select(".episodes a").mapNotNull { episode ->
                        Episode(
                            data = episode.attr("href"),
                            name = episode.text().trim(),
                            posterUrl = el.getImageUrl(posterUrl),
                        )
                    }
                }

                return newTvSeriesLoadResponse(name, url, TvType.TvSeries, episodes) {
                    this.plot = content
                    this.year = publishYear
                    this.tags = categories
                    this.recommendations = el.getMoviesList("${mainUrl}/danh-sach/phim-bo", 1)
                    addPoster(el.getImageUrl(posterUrl))
                    addActors(casts.mapNotNull { cast -> Actor(cast, "") })
                }
            }
        } catch (error: Exception) {}

        val codeText = "(CODE: ${url.split("/").lastOrNull()})"
        return newMovieLoadResponse("Something went wrong!", url, TvType.Movie, "") {
            this.plot = "There's a problem loading this content. $codeText"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return true
    }

    private fun getImageUrl(url: String): String {
        var newUrl = url
        if (!url.contains("http")) {
            newUrl = if (url.first() == '/')
                "${mainUrlImage}${url}" else "${mainUrlImage}/${url}"
        }
        return newUrl
    }

    private suspend fun getMoviesList(url: String, page: Int, horizontal: Boolean = false): List<SearchResponse>? {
        val el = this

        try {
            val newUrl = "${url}/page-${page}"
            val document = request(newUrl).document

            val items = document.select(".list-film .item")

            return items.mapNotNull{ movie ->
                val name = movie.select("a h3").text().trim()
                val movieUrl = movie.select("a").attr("href")
                val posterUrl = movie.select("a img").attr("src")
                newMovieSearchResponse(name, movieUrl, TvType.Movie, true) {
                    this.posterUrl = if (horizontal) el.getImageUrl(posterUrl!!) else el.getImageUrl(posterUrl!!)
                }
            }
        } catch (error: Exception) {}

        return mutableListOf<SearchResponse>()
    }
}