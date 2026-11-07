package eu.kanade.tachiyomi.extension.pt.sakuramangas

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
abstract class SakuraMangas : KeiSource() {

    override fun Headers.Builder.configureHeaders(): Headers.Builder =
        set("Referer", "$baseUrl/")

    // TODO: ajustar os caminhos e seletores com o HTML real do site
    private fun parseList(doc: Document): MangasPage {
        val mangas = doc.select("TODO_SELETOR_DO_CARD").mapNotNull { el ->
            val link = el.selectFirst("a") ?: return@mapNotNull null
            SManga.create().apply {
                url = link.absUrl("href").removePrefix(baseUrl)
                title = el.selectFirst("TODO_SELETOR_TITULO")?.text() ?: return@mapNotNull null
                thumbnail_url = el.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNext = doc.selectFirst("TODO_SELETOR_PROXIMA_PAGINA") != null
        return MangasPage(mangas, hasNext)
    }

    override suspend fun getPopularManga(page: Int): MangasPage =
        parseList(client.get("$baseUrl/TODO_CAMINHO_POPULARES?page=$page").asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        parseList(client.get("$baseUrl/TODO_CAMINHO_RECENTES?page=$page").asJsoup())

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val searchUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("TODO_CAMINHO_BUSCA")
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return parseList(client.get(searchUrl).asJsoup())
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
            title = doc.selectFirst("TODO_SELETOR_TITULO_OBRA")?.text() ?: manga.title
            thumbnail_url = doc.selectFirst("TODO_SELETOR_CAPA")?.absUrl("src")
            description = doc.selectFirst("TODO_SELETOR_SINOPSE")?.text()
            genre = doc.select("TODO_SELETOR_GENEROS").joinToString { it.text() }
        }

        val chapterList = doc.select("TODO_SELETOR_CAPITULO").map { el ->
            val link = el.selectFirst("a")!!
            SChapter.create().apply {
                url = link.absUrl("href").removePrefix(baseUrl)
                name = link.text()
            }
        }

        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get("$baseUrl${chapter.url}").asJsoup()
        return doc.select("TODO_SELETOR_IMAGENS_DO_CAPITULO").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src"))
        }
    }
}
