package com.cloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AnimeHayProvider(val plugin: AnimeHayPlugin) : MainAPI() {
    override var lang = "vi"
    override var name = "Anime Hay"
    override var mainUrl = "https://animehay.in"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        Pair("${mainUrl}/phim-moi-cap-nhap", "Anime Mới/vertical"),
        Pair("${mainUrl}/loc-phim/W1tdLFtdLFs5OTk5XSxbXV0=", "Anime Lẻ/vertical"),
        Pair("${mainUrl}/loc-phim/W1tdLFsyMDI0XSxbXSxbXV0=", "Anime 2024/vertical"),
        Pair("${mainUrl}/the-loai/tinh-cam-4", "Anime Tình Cảm/vertical"),
        Pair("${mainUrl}/the-loai/hanh-dong-2", "Anime Hành Động/vertical"),
        Pair("${mainUrl}/the-loai/cn-animation-34", "China Animation/vertical"),
    )

    override val hasMainPage = true
    override val hasDownloadSupport = true

    var mainUrlImage = "https://animehay.in"

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

            val name = document.select(".info-movie .heading_movie").text().trim()
            val content = document.select(".info-movie .body .desc").text().trim()
            val posterUrl = document.select(".info-movie .head img").attr("src")

            var duration = document.select(".info-movie .head .duration div:nth-child(2)").text().trim()
            var categories = document.select(".info-movie .head .list_cate a").mapNotNull { category -> category.text().trim() }
            val publishYear = document.select(".info-movie .head .update_time div:nth-child(2)").text().trim().toIntOrNull()?: 0

            val epsDocument = document.select(".info-movie .body .list-item-episode a")
            val type = if (epsDocument.size > 1 || duration.lowercase().contains("tập")) "series" else "single"

            if (type == "single") {
                var dataUrl = document.select("a[title=\"Xem Ngay\"]").attr("href")

                return newMovieLoadResponse(name, url, TvType.Movie, dataUrl) {
                    this.plot = content
                    this.year = publishYear
                    this.tags = categories
                    this.recommendations = el.getMoviesList("${mainUrl}/phim-moi-cap-nhap", 1)
                    addPoster(el.getImageUrl(posterUrl))
                }
            }

            if (type == "series") {
                var episodes = epsDocument.mapNotNull { episode ->
                    Episode(
                        data = episode.attr("href"),
                        name = episode.text().trim(),
                        posterUrl = el.getImageUrl(posterUrl),
                    )
                }

                return newTvSeriesLoadResponse(name, url, TvType.TvSeries, episodes) {
                    this.plot = content
                    this.year = publishYear
                    this.tags = categories
                    this.recommendations = el.getMoviesList("${mainUrl}/phim-moi-cap-nhap", 1)
                    addPoster(el.getImageUrl(posterUrl))
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
        val requested = request(data)

        val text = requested.text.trimIndent()
        val document = requested.document

        val entries = document.select("#list_sv a")

        for ((index, entry) in entries.withIndex()) {
            val label = entry.text().trim()
            if (label.contains("TOK")) {
                val tikServer = """tik:\s*'([^']*)'""".toRegex().find(text)?.groups?.get(1)?.value?: ""
                callback.invoke(
                    ExtractorLink(
                        label,
                        label,
                        tikServer,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
            }
        }

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
            val newUrl = "${url}/trang-${page}.html"
            val document = request(newUrl).document

            val items = document.select(".movies-list .movie-item")

            return items.mapNotNull{ movie ->
                val name = movie.select("> a .name-movie").text().trim()
                val movieUrl = movie.select("> a").attr("href")
                val posterUrl = movie.select("> a img").attr("src")
                newMovieSearchResponse(name, movieUrl, TvType.Movie, true) {
                    this.posterUrl = if (horizontal) el.getImageUrl(posterUrl) else el.getImageUrl(posterUrl)
                }
            }
        } catch (error: Exception) {}

        return mutableListOf<SearchResponse>()
    }
}