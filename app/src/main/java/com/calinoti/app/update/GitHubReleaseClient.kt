package com.calinoti.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal const val GITHUB_OWNER = "oshyun"
internal const val GITHUB_REPO = "calinoti"

data class GitHubLatestRelease(
    val tagName: String,
    val downloadUrl: String,
    val assetName: String,
    val size: Long,
)

class UpdateHttpException(val statusCode: Int) : IOException("HTTP $statusCode")
class MalformedReleaseException(message: String, cause: Throwable? = null) : IOException(message, cause)
class ReleaseApkMissingException(message: String) : IOException(message)

/**
 * GitHub Releases API를 통해 최신 공개 릴리스와 APK 다운로드 정보를 조회한다.
 * 인증 없이 호출하며 GitHub의 /releases/latest 엔드포인트를 사용한다.
 */
class GitHubReleaseClient(
    private val owner: String = GITHUB_OWNER,
    private val repo: String = GITHUB_REPO,
) {
    suspend fun fetchLatestRelease(): GitHubLatestRelease = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Calinoti-App")
            instanceFollowRedirects = true
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw UpdateHttpException(responseCode)
        }

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        parseReleaseJson(responseBody)
    }

    private fun parseReleaseJson(jsonString: String): GitHubLatestRelease {
        try {
            val jsonObject = JSONObject(jsonString)
            val tagName = jsonObject.optString("tag_name", "").ifEmpty {
                throw MalformedReleaseException("Release does not contain 'tag_name'")
            }
            val assetsArray = jsonObject.optJSONArray("assets")
                ?: throw MalformedReleaseException("Release does not contain 'assets' array")

            for (i in 0 until assetsArray.length()) {
                val asset = assetsArray.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                val size = asset.optLong("size", 0L)
                if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotEmpty()) {
                    return GitHubLatestRelease(
                        tagName = tagName,
                        downloadUrl = downloadUrl,
                        assetName = name,
                        size = size,
                    )
                }
            }
            throw ReleaseApkMissingException("No .apk asset found in latest release ($tagName)")
        } catch (e: JSONException) {
            throw MalformedReleaseException(e.message ?: "Invalid JSON response", e)
        }
    }
}
