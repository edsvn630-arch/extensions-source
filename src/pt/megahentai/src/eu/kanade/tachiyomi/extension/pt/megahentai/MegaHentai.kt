package eu.kanade.tachiyomi.extension.pt.megahentai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class MegaHentai : KeiSource() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder =
        set("Referer", "$baseUrl/")

    private fun pagedUrl(path: String, page: Int) =
        if (page <= 1) "$baseUrl/$path/" else "$baseUrl/$path/page/$page/"

    private fun parseList(doc: Document): MangasPage {
        val mangas = doc.select("article.item").mapNotNull { el ->
            val link = el.selectFirst("h3 a") ?: return@mapNotNull null
            val href = link.absUrl("href")
            if (!href.contains("/ler-online/")) return@mapNotNull null
            SManga.create().apply {
                url = href.removePrefix(baseUrl)
                title = link.text()
                val img = el.selectFirst("img[data-src]") ?: el.selectFirst("img[src]")
                thumbnail_url = img?.let {
                    it.absUrl("data-src").ifEmpty { it.absUrl("src") }
                }
            }
        }
        val hasNext = doc.selectFirst("div.pagination a.arrow_pag:has(i.icon-caret-right)") != null
        return MangasPage(mangas, hasNext)
    }

    override suspend fun getPopularManga(page: Int): MangasPage =
        parseList(client.get(pagedUrl("popular", page)).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        parseList(client.get(pagedUrl("manga-hentai", page)).asJsoup())

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val searchUrl = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("s", query)
        }.build()
        val doc = client.get(searchUrl).asJsoup()

        val seen = HashSet<String>()
        val mangas = doc.select("a[href*=/ler-online/]").mapNotNull { a ->
            val href = a.absUrl("href")
            if (href.trimEnd('/') == "$baseUrl/ler-online") return@mapNotNull null
            if (!seen.add(href)) return@mapNotNull null
            val card = a.closest("article") ?: a.parent()
            val img = card?.selectFirst("img[data-src]") ?: card?.selectFirst("img[src]")
            val name = card?.selectFirst("h3, .title")?.text()?.takeIf { it.isNotBlank() }
                ?: img?.attr("alt")?.takeIf { it.isNotBlank() }
                ?: a.text().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SManga.create().apply {
                url = href.removePrefix(baseUrl)
                title = name
                thumbnail_url = img?.let {
                    it.absUrl("data-src").ifEmpty { it.absUrl("src") }
                }
            }
        }
        val hasNext = doc.selectFirst("a.arrow_pag:has(i.icon-caret-right), a.next") != null
        return MangasPage(mangas, hasNext)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get("$baseUrl${manga.url}").asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = doc.selectFirst(".data_main h1")!!.text()
                .removePrefix("Todos os Capítulos de ").trim()
            thumbnail_url = doc.selectFirst(".sheader_content .thumb img")?.absUrl("src")
            genre = doc.select(".gen_flex a").joinToString { it.text() }
            val statusText = doc.select("li.data_info")
                .firstOrNull { it.text().startsWith("Status") }
                ?.selectFirst("span")?.text().orEmpty()
            status = when {
                statusText.contains("Ativo", true) -> SManga.ONGOING
                statusText.contains("Completo", true) -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            val synopsis = doc.selectFirst(".sinopse .texto")?.text().orEmpty()
            val alts = doc.select("ul.alt_title li span").joinToString("\n") { it.text() }
            description = buildString {
                append(synopsis)
                if (alts.isNotEmpty()) append("\n\nNomes alternativos:\n").append(alts)
            }
        }

        val chapterList = doc.select("ul#capitulos li.caps").map { el ->
            val link = el.selectFirst("a")!!
            SChapter.create().apply {
                url = link.absUrl("href").removePrefix(baseUrl)
                name = el.select("span").last()?.text() ?: link.attr("title")
            }
        }

        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get("$baseUrl${chapter.url}").asJsoup()
        return doc.select("#content > img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }
}
