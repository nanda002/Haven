package com.app.haven.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit


class OpenFoodFactsApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    suspend fun productJson(code: String): String = withContext(Dispatchers.IO) {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("world.openfoodfacts.org")
            .addPathSegments("api/v2/product")
            .addPathSegment("$code.json")
            .addQueryParameter("fields", "code,product_name,generic_name,brands")
            .build()

        val request = Request.Builder()
            .url(url)
            //Open Food Facts asks apps to identify themselves
            .header("User-Agent", USER_AGENT)
            .build()

        //make the api call
        client.newCall(request).execute().use { response ->
            //404 means empty response
            if (!response.isSuccessful && response.code != 404) {
                throw HttpStatusException(response.code)
            }
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private companion object {
        const val USER_AGENT = "Haven CS501"
    }
}

class HttpStatusException(val code: Int) : IOException("HTTP $code")
