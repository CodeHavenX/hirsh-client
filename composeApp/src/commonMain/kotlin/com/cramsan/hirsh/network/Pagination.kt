package com.cramsan.hirsh.network

import com.cramsan.hirsh.network.dto.PageResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Page size every [getAllPages] request asks for -- large enough that a ward's roster is usually one request. */
const val ALL_PAGES_PAGE_SIZE = 100

/**
 * Reads every page of a paginated `GET` endpoint (`page`/`size` params, a [PageResponse] body) into
 * one flat list, in the server's order (HISS-661). Lists in this app are a ward's patients, staff or
 * one patient's stays -- hundreds of rows, not millions -- so the screens show a whole list rather
 * than paging through it. [filters] adds the endpoint's own query parameters to every page request.
 */
suspend inline fun <reified T> HttpClient.getAllPages(
    path: String,
    crossinline filters: HttpRequestBuilder.() -> Unit = {},
): List<T> = collectAllPages { page ->
    get(path) {
        filters()
        parameter("page", page)
        parameter("size", ALL_PAGES_PAGE_SIZE)
    }.body<PageResponse<T>>()
}

/**
 * Kept out of line so the loop isn't copied into every [getAllPages] call site. The page count is
 * read from the first page only.
 */
@PublishedApi
internal suspend fun <T> collectAllPages(fetchPage: suspend (Int) -> PageResponse<T>): List<T> {
    val firstPage = fetchPage(0)
    return buildList {
        addAll(firstPage.content)
        for (page in 1 until firstPage.page.totalPages) {
            addAll(fetchPage(page).content)
        }
    }
}
