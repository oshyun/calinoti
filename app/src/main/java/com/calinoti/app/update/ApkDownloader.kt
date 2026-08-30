package com.calinoti.app.update

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 원격 URL에서 APK 파일을 다운로드하여 앱 캐시(cacheDir/update/)에 저장한다.
 * 리디렉션을 추적하며 진행률을 전달하고, 취소 시 부분 다운로드 파일을 정리한다.
 */
class ApkDownloader(private val context: Context) {

    suspend fun downloadApk(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (percent: Int?, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "update")
        if (updateDir.exists()) {
            updateDir.deleteRecursively()
        }
        if (!updateDir.mkdirs() && !updateDir.exists()) {
            throw IOException("Failed to create update directory: ${updateDir.absolutePath}")
        }

        val targetFile = File(updateDir, targetFileName)
        var connection: HttpURLConnection? = null
        try {
            var currentUrl = URL(downloadUrl)
            var redirects = 0
            while (redirects < 5) {
                val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Calinoti-App")
                }
                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308
                ) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrEmpty()) {
                        throw IOException("HTTP redirect without Location header")
                    }
                    currentUrl = URL(location)
                    redirects++
                } else if (responseCode in 200..299) {
                    connection = conn
                    break
                } else {
                    conn.disconnect()
                    throw UpdateHttpException(responseCode)
                }
            }

            val activeConn = connection ?: throw IOException("Failed to connect after redirects")
            val totalBytes = activeConn.contentLengthLong
            var downloadedBytes = 0L
            var lastPercent: Int? = null

            activeConn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) {
                            throw CancellationException("Download cancelled")
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }

                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            if (!coroutineContext.isActive) {
                throw CancellationException("Download cancelled")
            }

            onProgress(100, downloadedBytes, if (totalBytes > 0) totalBytes else downloadedBytes)

            targetFile
        } catch (cancelled: CancellationException) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            throw cancelled
        } catch (e: Exception) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            throw e
        } finally {
            connection?.disconnect()
        }
    }
}
