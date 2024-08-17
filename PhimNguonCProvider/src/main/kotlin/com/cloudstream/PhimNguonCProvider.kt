package com.cloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse

import java.net.URLEncoder

class PhimNguonCProvider(val plugin: PhimNguonCPlugin) : MainAPI() {
    override var lang = "vi"
    override var name = "Phim Nguồn C"
    override var mainUrl = "https://phim.nguonc.com/api"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        Pair("${mainUrl}/films/phim-moi-cap-nhat", "Phim Mới/vertical"),
        Pair("${mainUrl}/films/the-loai/hanh-dong", "Phim Hành Động/vertical"),
        Pair("${mainUrl}/films/the-loai/tinh-cam", "Phim Tình Cảm/vertical"),
        Pair("${mainUrl}/films/quoc-gia/au-my", "Phim Âu - Mỹ/vertical"),
        Pair("${mainUrl}/films/quoc-gia/han-quoc", "Phim Hàn Quốc/vertical"),
        Pair("${mainUrl}/films/quoc-gia/trung-quoc", "Phim Trung Quốc/vertical"),
    )

    override val hasMainPage = true
    override val hasDownloadSupport = true

    var mainUrlImage = "https://phim.nguonc.com"

    private suspend fun request(url: String): NiceResponse {
        return app.get(url)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return this.getMoviesList("${mainUrl}/films/search?keyword=${query}", 1)!!
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
            val text = request(url).text
            val response = tryParseJson<MovieResponse>(text)!!

            val movie = response.movie
            val episodes = this.mapEpisodesResponse(response.movie.episodes)


            var type = ""
            if (type != "single" && type != "series") {
                type = if (episodes.size > 1) "series" else "single"
            }

            val categories = this.findCategoryList(movie.category, "Thể loại")
            val publishYear = this.findCategoryList(movie.category, "Năm")

            if (type == "single") {
                var dataUrl = "${url}@@@"
                if (episodes.isNotEmpty()) {
                    dataUrl = "${url}@@@${episodes[0].slug}"
                }

                return newMovieLoadResponse(movie.name, url, TvType.Movie, dataUrl) {
                    this.plot = movie.content
                    if (publishYear.isNotEmpty()) {
                        this.year = publishYear[0].name.toInt()
                    }
                    this.tags = categories.mapNotNull { category -> category.name }
                    this.recommendations = el.getMoviesList("${mainUrl}/films/phim-moi-cap-nhat", 1)
                    addPoster(movie.posterUrl)
                    addActors(movie.casts.split(",").mapNotNull { cast -> Actor(cast, "") })
                }
            }

            if (type == "series") {
                val episodesMapped = episodes.mapNotNull { episode ->
                    val dataUrl = "${url}@@@${episode.slug}"
                    Episode(
                        data = dataUrl,
                        name = episode.name,
                        posterUrl = movie.posterUrl,
                        description = episode.filename
                    )
                }

                return newTvSeriesLoadResponse(movie.name, url, TvType.TvSeries, episodesMapped) {
                    this.plot = movie.content
                    if (publishYear.isNotEmpty()) {
                        this.year = publishYear[0].name.toInt()
                    }
                    this.tags = categories.mapNotNull { category -> category.name }
                    this.recommendations = el.getMoviesList("${mainUrl}/films/phim-moi-cap-nhat", 1)
                    addPoster(movie.posterUrl)
                    addActors(movie.casts.split(",").mapNotNull { cast -> Actor(cast, "") })
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
            newUrl = "${mainUrlImage}/${url}"
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

            val text = request(newUrl).text
            val response = tryParseJson<ListResponse>(text)

            return response?.items?.mapNotNull{ movie ->
                val movieUrl = "${mainUrl}/film/${movie.slug}"
                newMovieSearchResponse(movie.name, movieUrl, TvType.Movie, true) {
                    this.posterUrl = if (horizontal) el.getImageUrl(movie.posterUrl) else el.getImageUrl(movie.thumbUrl)
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
            .fold(mutableMapOf<String, MappedEpisode>()) { acc, cur ->
                val key = cur.name
                val episode = acc.getOrPut(key) {
                    MappedEpisode(
                        name = cur.name,
                        slug = cur.slug,
                        filename = cur.filename,
                    )
                }
                episode.episodes.add(
                    MappedEpisodeItem(
                        server = cur.server,
                        linkM3u8 = cur.linkM3u8,
                        linkEmbed = cur.linkEmbed
                    )
                )
                acc
            }
            .values
            .sortedBy { it.name }
    }
}