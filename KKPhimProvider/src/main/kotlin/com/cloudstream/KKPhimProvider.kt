package com.cloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
import java.net.URL

class KKPhimProvider(val plugin: KKPhimPlugin) : MainAPI() {
    override var lang = "vi"
    override var name = "KK Phim"
    override var mainUrl = "https://phimapi.com"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        Pair("${mainUrl}/danh-sach/phim-moi-cap-nhat", "Phim Mới/vertical"),
        Pair("${mainUrl}/v1/api/danh-sach/phim-le", "Phim Lẻ/horizontal"),
        Pair("${mainUrl}/v1/api/the-loai/tinh-cam", "Phim Tình Cảm/vertical"),
        Pair("${mainUrl}/v1/api/quoc-gia/au-my", "Phim Âu - Mỹ/vertical"),
        Pair("${mainUrl}/v1/api/quoc-gia/han-quoc", "Phim Hàn Quốc/vertical"),
        Pair("${mainUrl}/v1/api/quoc-gia/trung-quoc", "Phim Trung Quốc/vertical"),
    )

    override val hasMainPage = true
    override val hasDownloadSupport = true

    var mainUrlImage = "https://phimimg.com"

    private suspend fun request(url: String): NiceResponse {
        return app.get(url)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return this.getMoviesList("${mainUrl}/v1/api/tim-kiem?keyword=${query}", 1)!!
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val name = request.name.split("/")[0]
        val horizontal = request.name.split("/")[1] == "horizontal"

        val homePageList = HomePageList(
            name = name,
            list = if (request.data.contains("v1/api"))
                this.getMoviesList(request.data, page, horizontal)!!
                else this.getMoviesListNotV1(request.data, page, horizontal)!!,
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
            val episodes = this.mapEpisodesResponse(response.episodes)

            var type = movie.type
            if (type != "single" && type != "series") {
                type = if (episodes.size > 1) "series" else "single"
            }

            if (type == "single") {
                var dataUrl = "${url}@@@"
                if (episodes.isNotEmpty()) {
                    dataUrl = "${url}@@@${episodes[0].slug}"
                }

                return newMovieLoadResponse(movie.name, url, TvType.Movie, dataUrl) {
                    this.plot = movie.content
                    this.year = movie.publishYear
                    this.tags = movie.categories.mapNotNull { category -> category.name }
                    this.recommendations = el.getMoviesList("${mainUrl}/v1/api/danh-sach/phim-le", 1)
                    addPoster(movie.posterUrl)
                    addActors(movie.casts.mapNotNull { cast -> Actor(cast, "") })
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
                    this.year = movie.publishYear
                    this.tags = movie.categories.mapNotNull { category -> category.name }
                    this.recommendations = el.getMoviesList("${mainUrl}/v1/api/danh-sach/phim-bo", 1)
                    addPoster(movie.posterUrl)
                    addActors(movie.casts.mapNotNull { cast -> Actor(cast, "") })
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

        val episodes = this.mapEpisodesResponse(response.episodes)
        val episodeItem = episodes.find { episode -> episode.slug == slug}

        if (episodeItem !== null) {
            episodeItem.episodes.forEach{ episode ->
                callback.invoke(
                    ExtractorLink(
                        episode.server,
                        episode.server,
                        episode.linkM3u8,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        isM3u8 = true,
                    )
                )
            }
        }

        return true
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

            return response?.data?.items?.mapNotNull{ movie ->
                val movieUrl = "${mainUrl}/phim/${movie.slug}"
                newMovieSearchResponse(movie.name, movieUrl, TvType.Movie, true) {
                    this.posterUrl = if (horizontal) el.getImageUrl(movie.posterUrl) else el.getImageUrl(movie.thumbUrl)
                }
            }
        } catch (error: Exception) {}

        return mutableListOf<SearchResponse>()
    }

    private suspend fun getMoviesListNotV1(url: String, page: Int, horizontal: Boolean = false): List<SearchResponse>? {
        val el = this

        try {
            var newUrl = "${url}?page=${page}"
            if (url.contains("?")) {
                newUrl = "${url}&page=${page}"
            }

            val text = request(newUrl).text
            val response = tryParseJson<ListDataResponse>(text)

            return response?.items?.mapNotNull{ movie ->
                val movieUrl = "${mainUrl}/phim/${movie.slug}"
                newMovieSearchResponse(movie.name, movieUrl, TvType.Movie, true) {
                    this.posterUrl = if (horizontal) el.getImageUrl(movie.posterUrl) else el.getImageUrl(movie.thumbUrl)
                }
            }
        } catch (error: Exception) {}

        return mutableListOf<SearchResponse>()
    }

    data class ListResponse (
        @JsonProperty("data") val data: ListDataResponse,
    )

    data class ListDataResponse (
        @JsonProperty("items") val items: List<MoviesResponse>
    )

    data class MoviesResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("poster_url") val thumbUrl: String,
        @JsonProperty("thumb_url") val posterUrl: String,
    )

    data class MovieResponse (
        @JsonProperty("movie") val movie: MovieDetailResponse,
        @JsonProperty("episodes") val episodes: List<MovieEpisodeResponse>,
    )

    data class MovieDetailResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("content") val content: String,
        @JsonProperty("poster_url") val thumbUrl: String,
        @JsonProperty("thumb_url") val posterUrl: String,
        @JsonProperty("year") val publishYear: Int,
        @JsonProperty("actor") val casts: List<String>,
        @JsonProperty("category") val categories: List<MovieTaxonomyResponse>,
    )

    data class MovieTaxonomyResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
    )

    data class MovieEpisodeResponse (
        @JsonProperty("server_name") val serverName: String,
        @JsonProperty("server_data") val serverData: List<MovieEpisodeDataResponse>,
    )

    data class MovieEpisodeDataResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("filename") val filename: String,
        @JsonProperty("link_m3u8") val linkM3u8: String,
        @JsonProperty("link_embed") val linkEmbed: String,
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

    private suspend fun mapEpisodesResponse(episodes: List<MovieEpisodeResponse>): List<MappedEpisode> {
        return episodes
            .flatMap { episode ->
                episode.serverData.map { item ->
                    MappedData(
                        name = item.name,
                        slug = item.slug,
                        filename = item.filename,
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