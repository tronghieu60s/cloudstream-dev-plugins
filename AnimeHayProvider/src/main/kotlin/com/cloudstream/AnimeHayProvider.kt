package com.cloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse

import java.net.URLEncoder

class AnimeHayProvider(val plugin: AnimeHayPlugin) : MainAPI() {
    override var lang = "vi"
    override var name = "Anime Hay"
    override var mainUrl = "https://mth-cloudstream.vercel.app/api/animehay"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    var proxyUrl = "https://mth-cloudstream.vercel.app/"

    override val mainPage = mainPageOf(
        Pair("${mainUrl}/list", name),
    )

    override val hasMainPage = true
    override val hasDownloadSupport = true

    val movieUrl = "movies/danh-sach/phim-le";
    val tvSeriesUrl = "movies/danh-sach/phim-bo";

    private suspend fun request(url: String): NiceResponse {
        val headers: Map<String, String> = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer Dghb6eXKon9VsEigqhatqCbo4COpXY8wgoBCmuj33KGyc77XnWw8xdGs5b81vk2L"
        )
        return app.get(url, headers = headers)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return this.getMoviesList("${mainUrl}/movies/search?keyword=${query}", 1)!!
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val list = mutableListOf<HomePageList>()

        try {
            if (page <= 1) {
                val text = request(request.data).text
                val items = tryParseJson<ResponseListData<HomeResponse>>(text)
                items?.data?.forEach { item ->
                    list.add(HomePageList(
                        item.name,
                        this.getMoviesList("${mainUrl}/movies/${item.link}",1, item.horizontal)!!,
                        item.horizontal
                    ))
                }
            }
        } catch (error: Exception) {}

        return newHomePageResponse(
            list = list,
            hasNext = true
        )
    }

    override suspend fun load(url: String): LoadResponse {
        try {
            val el = this;

            val text = request(url).text
            val movie = tryParseJson<ResponseData<MovieResponse>>(text)?.data!!

            val episodes = movie.episodes.mapNotNull { episode ->
                val dataUrl = "${mainUrl}/episode/${movie.slug}/${episode.slug}"
                Episode(data = dataUrl, name = episode.name, posterUrl = movie.posterUrl)
            }

            if (movie.type == "single") {
                return newAnimeLoadResponse(movie.name, url, TvType.AnimeMovie) {
                    this.plot = movie.content
                    this.year = movie.publishYear
                    this.tags = movie.categories.mapNotNull { category -> category.name }
                    this.recommendations = el.getMoviesList("${mainUrl}/${movieUrl}", 1)
                    addPoster(movie.posterUrl)
                    addEpisodes(DubStatus.Subbed, episodes)
                }
            }

            if (movie.type == "series") {
                return newAnimeLoadResponse(movie.name, url, TvType.Anime) {
                    this.plot = movie.content
                    this.year = movie.publishYear
                    this.tags = movie.categories.mapNotNull { category -> category.name }
                    this.recommendations = el.getMoviesList("${mainUrl}/${tvSeriesUrl}", 1)
                    addPoster(movie.posterUrl)
                    addEpisodes(DubStatus.Subbed, episodes)
                }
            }
        } catch (error: Exception) {}

        return newMovieLoadResponse("Something went wrong!", url, TvType.Movie, "") {
            this.plot = "Error loading this movie. (CODE: ${url.split("/").lastOrNull()})"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val text = request(data).text
        val episode = tryParseJson<ResponseData<MoviesEpisodeResponse>>(text)?.data!!

        val episodes = episode.episodes

        episodes.mapNotNull { episode ->
            callback.invoke(
                ExtractorLink(
                    episode.server,
                    episode.server,
                    episode.linkM3u8,
                    referer = "$mainUrl/",
                    quality = Qualities.P1080.value,
                    isM3u8 = true,
                )
            )
        }

        return true
    }

    private suspend fun getMoviesList(url: String, page: Int, horizontal: Boolean = false): List<SearchResponse>? {
        try {
            var newUrl = "${url}?page=${page}"
            if (url.contains("?")) {
                newUrl = "${url}&page=${page}"
            }

            val text = request(newUrl).text
            val movies = tryParseJson<ResponsePaginationData<MoviesResponse>>(text)

            return movies?.data?.items?.mapNotNull{ movie ->
                newMovieSearchResponse(movie.name, "${mainUrl}/movie/${movie.slug}", TvType.Movie, true) {
                    this.posterUrl = if (horizontal) movie.posterUrl else movie.thumbUrl
                }
            }
        } catch (error: Exception) {}

        return mutableListOf<SearchResponse>()
    }

    data class ResponseData<T> (
        @JsonProperty("data") val data: T
    )

    data class ResponseListData<T> (
        @JsonProperty("data") val data: List<T>
    )

    data class ResponsePagination<T> (
        @JsonProperty("items") val items: List<T>
    )

    data class ResponsePaginationData<T> (
        @JsonProperty("data") val data: ResponsePagination<T>
    )

    data class HomeResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("link") val link: String,
        @JsonProperty("horizontal") val horizontal: Boolean,
    )

    data class MovieResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("content") val content: String,
        @JsonProperty("thumbUrl") val thumbUrl: String,
        @JsonProperty("posterUrl") val posterUrl: String,
        @JsonProperty("publishYear") val publishYear: Int,
        @JsonProperty("casts") val casts: List<MoviesTaxonomyResponse>,
        @JsonProperty("categories") val categories: List<MoviesTaxonomyResponse>,
        @JsonProperty("episodes") val episodes: List<MoviesEpisodesResponse>
    )

    data class MoviesResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("originName") val originName: String,
        @JsonProperty("thumbUrl") val thumbUrl: String,
        @JsonProperty("posterUrl") val posterUrl: String,
    )

    data class MoviesEpisodeResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("filename") val filename: String,
        @JsonProperty("episodes") val episodes: List<MoviesEpisodeItemResponse>
    )

    data class MoviesEpisodeItemResponse (
        @JsonProperty("server") val server: String,
        @JsonProperty("linkM3u8") val linkM3u8: String,
        @JsonProperty("linkEmbed") val linkEmbed: String,
    )

    data class MoviesEpisodesResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String
    )

    data class MoviesTaxonomyResponse (
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
    )
}