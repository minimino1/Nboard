package com.nboard.ime

import android.content.ClipDescription
import android.graphics.BitmapFactory
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal data class GifResult(
    val id: String,
    val gifUrl: String,
    val previewUrl: String
)

private val gifHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

internal fun NboardImeService.searchGifs(query: String) {
    gifSearchJob?.cancel()
    if (!isGifPanelInitialized()) return
    gifResultsRow.removeAllViews()
    if (query.isBlank()) {
        gifLoadingIndicator.isVisible = false
        return
    }
    gifLoadingIndicator.isVisible = true
    gifSearchJob = serviceScope.launch {
        val results = withContext(Dispatchers.IO) { fetchGifsFromTenor(query) }
        if (!isGifMode) return@launch
        gifLoadingIndicator.isVisible = false
        renderGifResults(results)
    }
}

private fun fetchGifsFromTenor(query: String): List<GifResult> {
    return try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.tenor.com/v1/search" +
            "?q=$encodedQuery" +
            "&key=$TENOR_API_KEY" +
            "&limit=$GIF_SEARCH_RESULT_LIMIT" +
            "&media_filter=minimal" +
            "&contentfilter=medium"
        val request = Request.Builder().url(url).build()
        val body = gifHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string().orEmpty()
        }
        if (body.isBlank()) return emptyList()
        val json = JSONObject(body)
        val results = json.optJSONArray("results") ?: return emptyList()
        val gifs = mutableListOf<GifResult>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val id = item.optString("id").ifBlank { continue }
            val mediaArray = item.optJSONArray("media") ?: continue
            val media = mediaArray.optJSONObject(0) ?: continue
            val gifUrl = media.optJSONObject("gif")?.optString("url") ?: continue
            val previewUrl = media.optJSONObject("nanogif")?.optString("url")
                ?: media.optJSONObject("tinygif")?.optString("url")
                ?: gifUrl
            gifs.add(GifResult(id = id, gifUrl = gifUrl, previewUrl = previewUrl))
        }
        gifs
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun NboardImeService.renderGifResults(results: List<GifResult>) {
    if (!isGifPanelInitialized()) return
    gifResultsRow.removeAllViews()
    results.forEach { gif ->
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = uiDrawable(R.drawable.bg_key)
            layoutParams = LinearLayout.LayoutParams(dp(GIF_THUMBNAIL_SIZE_DP), dp(GIF_THUMBNAIL_SIZE_DP)).also {
                it.marginEnd = dp(4)
            }
            setOnClickListener { onGifChosen(gif) }
        }
        gifResultsRow.addView(imageView)
        loadGifThumbnail(imageView, gif.previewUrl)
    }
}

private fun NboardImeService.loadGifThumbnail(imageView: ImageView, url: String) {
    val tag = url
    imageView.tag = tag
    serviceScope.launch(Dispatchers.IO) {
        val bitmap = try {
            val request = Request.Builder().url(url).build()
            gifHttpClient.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) { null }
        withContext(Dispatchers.Main) {
            if (bitmap != null && imageView.tag == tag) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }
}

internal fun NboardImeService.onGifChosen(gif: GifResult) {
    serviceScope.launch {
        val bytes = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(gif.gifUrl).build()
                gifHttpClient.newCall(request).execute().use { it.body?.bytes() }
            } catch (_: Exception) { null }
        }
        if (bytes == null) {
            toast("Failed to load GIF")
            return@launch
        }
        try {
            val gifDir = File(cacheDir, "gifs")
            gifDir.mkdirs()
            val file = File(gifDir, "${gif.id}.gif")
            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(
                this@NboardImeService,
                "$packageName.fileprovider",
                file
            )
            val contentInfo = InputContentInfo(
                uri,
                ClipDescription("gif", arrayOf("image/gif")),
                null
            )
            currentInputConnection?.commitContent(
                contentInfo,
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null
            )
        } catch (_: Exception) {
            toast("Failed to send GIF")
        }
    }
}

internal fun NboardImeService.toggleGifMode() {
    isGifMode = !isGifMode
    if (!isGifMode) {
        gifSearchJob?.cancel()
        if (::gifSearchInput.isInitialized) {
            gifSearchInput.text?.clear()
        }
        if (::gifResultsRow.isInitialized) {
            gifResultsRow.removeAllViews()
        }
        if (inlineInputTarget == InlineInputTarget.GIF_SEARCH) {
            clearInlinePromptFocus()
        }
    }
    refreshUi()
}

internal fun NboardImeService.isGifPanelInitialized(): Boolean {
    return ::gifPanel.isInitialized && ::gifResultsRow.isInitialized
}

internal fun NboardImeService.isGifSearchActive(): Boolean {
    return isGifMode && isEmojiMode
}

internal fun NboardImeService.isGifSearchInputActive(): Boolean {
    return isGifSearchActive() && inlineInputTarget == InlineInputTarget.GIF_SEARCH
}
