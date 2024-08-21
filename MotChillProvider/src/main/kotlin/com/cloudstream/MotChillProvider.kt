package com.cloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse

import java.net.URLEncoder

class PhimMoiChillProvider(val plugin: PhimMoiChillPlugin) : MainAPI() {
    override var lang = "vi"
    override var name = "Mọt Chill"
    override var mainUrl = "https://motchill.onl"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        Pair("${mainUrl}/danh-sach/phim-moi", "Phim Mới/vertical"),
        Pair("${mainUrl}/danh-sach/phim-le", "Phim Lẻ/horizontal"),
        Pair("${mainUrl}/the-loai/tinh-cam", "Phim Tình Cảm/vertical"),
        Pair("${mainUrl}/quoc-gia/au-my", "Phim Âu - Mỹ/vertical"),
        Pair("${mainUrl}/quoc-gia/han-quoc", "Phim Hàn Quốc/vertical"),
        Pair("${mainUrl}/quoc-gia/trung-quoc", "Phim Trung Quốc/vertical"),
    )

    override val hasMainPage = true
    override val hasDownloadSupport = true

    var mainUrlImage = "https://motchill.onl"

    private suspend fun request(url: String): NiceResponse {
        return app.get(url)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return this.getMoviesList("${mainUrl}/?search=${query}", 1)!!
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

            val name = document.select(".myui-content__detail .title").text().trim()
            val content = document.select(".myui-movie-detail div[itemprop=\"description\"]").text().trim()
            val thumbUrl = document.select(".myui-content__thumb img").attr("src")
            val metaInfo = document.select(".myui-content__detail .myui-media-info h6")

            var type = "series"
            var casts = listOf<String>()
            var categories = listOf<String>()
            var publishYear = 0
            for ((index, metaInfoItem) in metaInfo.withIndex()) {
                val title = metaInfoItem?.text()?.trim().toString()
                if (index == 0) {
                    publishYear = """\(\d{4}\)""".toRegex().find(title)?.value?.removeSurrounding("(", ")")?.toInt()!!
                }
                if (title.contains("Thể loại:")) {
                    categories = metaInfoItem.select("a").mapNotNull { category ->  category?.text()?.trim() }
                }
                if (title.contains("Trạng thái:") && title.lowercase().contains("full")) {
                    type = "single"
                }
                if (title.contains("Diễn viên:")) {
                    casts = metaInfoItem.select("a").mapNotNull { cast ->  cast?.text()?.trim() }
                }
            }

            if (type == "single") {
                var dataUrl = "${url}@@@"

                return newMovieLoadResponse(name, url, TvType.Movie, dataUrl) {
                    this.plot = content
                    this.year = publishYear
                    this.tags = categories
                    this.recommendations = el.getMoviesList("${mainUrl}/danh-sach/phim-le", 1)
                    addPoster(el.getImageUrl(thumbUrl))
                    addActors(casts.mapNotNull { cast -> Actor(cast, "") })
                }
            }

            if (type == "series") {
                var dataUrl = "${url}@@@"

                return newTvSeriesLoadResponse(name, url, TvType.TvSeries, mutableListOf()) {
                    this.plot = content
                    this.year = publishYear
                    this.tags = categories
                    this.recommendations = el.getMoviesList("${mainUrl}/danh-sach/phim-bo", 1)
                    addPoster(el.getImageUrl(thumbUrl))
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
        val url = data.split("@@@")[0]
        val slug = data.split("@@@")[1]

        val text = request(url).text
        val response = tryParseJson<MovieResponse>(text)!!

        val episodes = this.mapEpisodesResponse(response.movie.episodes)
        val episodeItem = episodes.find { episode -> episode.slug == slug}

        if (episodeItem !== null) {
            episodeItem.episodes.forEach{ episode ->
                val episodeUrl = episode.linkEmbed.replace("embed.php", "get.php")
                callback.invoke(
                    ExtractorLink(
                        episode.server,
                        episode.server,
                        episodeUrl,
                        referer = url,
                        quality = Qualities.Unknown.value,
                        isM3u8 = true,
                        headers = mapOf("Referer" to episodeUrl)
                    )
                )
            }
        }

        return true
    }

    data class ListResponse (
        @JsonProperty("items") val items: List<MoviesResponse>
    )

    data class MoviesResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("thumb_url") val thumbUrl: String,
        @JsonProperty("poster_url") val posterUrl: String,
    )

    data class MovieResponse (
        @JsonProperty("movie") val movie: MovieDetailResponse,
    )

    data class MovieDetailResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("description") val content: String,
        @JsonProperty("thumb_url") val thumbUrl: String,
        @JsonProperty("poster_url") val posterUrl: String,
        @JsonProperty("casts") val casts: String,
        @JsonProperty("category") val category: MovieCategoryResponse,
        @JsonProperty("episodes") val episodes: List<MovieEpisodeResponse>,
    )

    data class MovieCategoryResponse (
        @JsonProperty("1") val category1: MovieCategoryItemResponse,
        @JsonProperty("2") val category2: MovieCategoryItemResponse,
        @JsonProperty("3") val category3: MovieCategoryItemResponse,
        @JsonProperty("4") val category4: MovieCategoryItemResponse
    )

    data class MovieCategoryItemResponse (
        @JsonProperty("list") val list: List<MovieCategoryListResponse>,
        @JsonProperty("group") val group: MovieCategoryGroupResponse,
    )

    data class MovieCategoryListResponse (
        @JsonProperty("id") val id: String,
        @JsonProperty("name") val name: String
    )

    data class MovieCategoryGroupResponse (
        @JsonProperty("id") val id: String,
        @JsonProperty("name") val name: String
    )

    data class MovieTaxonomyResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
    )

    data class MovieEpisodeResponse (
        @JsonProperty("server_name") val serverName: String,
        @JsonProperty("items") val serverData: List<MovieEpisodeDataResponse>,
    )

    data class MovieEpisodeDataResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("m3u8") val linkM3u8: String,
        @JsonProperty("embed") val linkEmbed: String,
    )

    data class MappedData (
        val name: String,
        val slug: String,
        val filename: String,
        val server: String,
        val linkM3u8: String,
        val linkEmbed: String
    )

    data class MappedEpisode (
        val name: String,
        val slug: String,
        val filename: String,
        val episodes: MutableList<MappedEpisodeItem> = mutableListOf()
    )

    data class MappedEpisodeItem (
        val server: String,
        val linkM3u8: String,
        val linkEmbed: String
    )

    private fun getImageUrl(url: String): String {
        var newUrl = url
        if (!url.contains("http")) {
            newUrl = "${mainUrlImage}${url}"
        }
        return newUrl
    }

    private suspend fun getMoviesList(url: String, page: Int, horizontal: Boolean = false): List<SearchResponse>? {
        val el = this

        try {
            var newUrl = "${url}?page=${page}"
            if (url.contains("?")) {
                newUrl = "${url}&page=${page}"
            }

            val document = request(newUrl).document
            val items = document.select(".myui-vodlist__box")

            return items.mapNotNull{ movie ->
                val name = movie.select(".myui-vodlist__detail .title a").text().trim()
                val movieUrl = movie.select(".myui-vodlist__detail .title a").attr("href")
                val thumbUrl = """url\((.*?)\)""".toRegex().find(movie.select("a").attr("style"))?.groups?.get(1)?.value
                newMovieSearchResponse(name, movieUrl, TvType.Movie, true) {
                    this.posterUrl = if (horizontal) el.getImageUrl(thumbUrl!!) else el.getImageUrl(thumbUrl!!)
                }
            }
        } catch (error: Exception) {}

        return mutableListOf<SearchResponse>()
    }

    private suspend fun findCategoryList(category: MovieCategoryResponse, name: String): List<MovieCategoryListResponse> {
        val categories = listOf(category.category1, category.category2, category.category3, category.category4)

        for (categoryItem in categories) {
            if (categoryItem.group.name == name) {
                return categoryItem.list
            }
        }

        return mutableListOf()
    }

    private suspend fun mapEpisodesResponse(episodes: List<MovieEpisodeResponse>): List<MappedEpisode> {
        return episodes
            .flatMap { episode ->
                episode.serverData.map { item ->
                    MappedData(
                        name = item.name,
                        slug = item.slug,
                        filename = "",
                        server = episode.serverName,
                        linkM3u8 = item.linkM3u8,
                        linkEmbed = item.linkEmbed
                    )
                }.filter { data ->
                    data.name.isNotEmpty()
                }
            }
            .fold(mutableMapOf<String, MappedEpisode>()) { accumulator, current ->
                val key = current.name
                val episode = accumulator.getOrPut(key) {
                    MappedEpisode(
                        name = current.name,
                        slug = current.slug,
                        filename = current.filename,
                    )
                }
                episode.episodes.add(
                    MappedEpisodeItem(
                        server = current.server,
                        linkM3u8 = current.linkM3u8,
                        linkEmbed = current.linkEmbed
                    )
                )
                accumulator
            }
            .values
            .sortedBy { it.name }
    }
}