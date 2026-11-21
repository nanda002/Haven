package com.app.haven.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import com.app.haven.BuildConfig
import java.util.concurrent.TimeUnit

class UsdaApi (
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
        suspend fun searchJson(query: String): String = withContext(Dispatchers.IO) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.nal.usda.gov")
                .addPathSegment("fdc/v1/foods/search")
                .addQueryParameter("api_key", BuildConfig.USDA_API_KEY)
                .addQueryParameter("query", query)
                .addQueryParameter("dataType", "Foundation,SR Legacy")
                .addQueryParameter("pageSize", "25")
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            //make the api call
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpStatusException(response.code)
                }
                response.body?.string() ?: throw IOException("Empty response") }
        }
}