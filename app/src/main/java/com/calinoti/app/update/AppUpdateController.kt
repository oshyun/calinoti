package com.calinoti.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.calinoti.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val remoteVersionName: String) : UpdateUiState
    data class Downloading(
        val remoteVersionName: String,
        val percent: Int?,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateUiState
    data class ReadyToInstall(
        val remoteVersionName: String,
        val apkFile: File,
    ) : UpdateUiState
    data class Error(
        @StringRes val messageResId: Int,
        val statusCode: Int? = null,
    ) : UpdateUiState
}

/**
 * 앱 업데이트 확인 및 다운로드 라이프사이클을 총괄하는 컨트롤러.
 * Application scope를 통해 화면 전환 및 백그라운드 전환에도 다운로드가 중단되지 않도록 보장한다.
 */
class AppUpdateController(
    private val context: Context,
    private val gitHubReleaseClient: GitHubReleaseClient = GitHubReleaseClient(),
    private val apkDownloader: ApkDownloader = ApkDownloader(context),
    private val controllerScope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdate(installedVersionName: String) {
        val currentState = _state.value
        if (currentState is UpdateUiState.Checking || currentState is UpdateUiState.Downloading) {
            return
        }

        _state.value = UpdateUiState.Checking
        controllerScope.launch {
            try {
                val release = gitHubReleaseClient.fetchLatestRelease()
                val remoteVersionName = release.tagName.trim().removePrefix("v").removePrefix("V")
                if (isRemoteVersionNewer(installed = installedVersionName, remote = remoteVersionName)) {
                    _state.value = UpdateUiState.Available(remoteVersionName)
                    _state.value = UpdateUiState.Downloading(
                        remoteVersionName = remoteVersionName,
                        percent = null,
                        downloadedBytes = 0L,
                        totalBytes = release.size,
                    )
                    val apkFile = apkDownloader.downloadApk(
                        downloadUrl = release.downloadUrl,
                        targetFileName = release.assetName.ifEmpty { "Calinoti-$remoteVersionName.apk" },
                        onProgress = { percent, downloaded, total ->
                            _state.value = UpdateUiState.Downloading(
                                remoteVersionName = remoteVersionName,
                                percent = percent,
                                downloadedBytes = downloaded,
                                totalBytes = total,
                            )
                        },
                    )
                    _state.value = UpdateUiState.ReadyToInstall(remoteVersionName, apkFile)
                } else {
                    _state.value = UpdateUiState.UpToDate
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: UpdateHttpException) {
                Log.e(TAG, "Update check failed with HTTP ${e.statusCode}", e)
                _state.value = UpdateUiState.Error(R.string.update_error_server_format, e.statusCode)
            } catch (e: MalformedReleaseException) {
                Log.e(TAG, "Update check failed: malformed release", e)
                _state.value = UpdateUiState.Error(R.string.update_error_release_unavailable)
            } catch (e: ReleaseApkMissingException) {
                Log.e(TAG, "Update check failed: apk missing in release", e)
                _state.value = UpdateUiState.Error(R.string.update_error_release_unavailable)
            } catch (e: IOException) {
                Log.e(TAG, "Update check failed with network error", e)
                _state.value = UpdateUiState.Error(R.string.update_error_network)
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed with unexpected error", e)
                _state.value = UpdateUiState.Error(R.string.update_error_release_unavailable)
            }
        }
    }

    /**
     * 알 수 없는 앱 설치 권한(REQUEST_INSTALL_PACKAGES)이 허용되어 있는지 확인한다.
     */
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * "알 수 없는 앱 설치" 설정 화면을 열기 위한 인텐트를 생성한다.
     */
    fun buildUnknownAppSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
    }

    /**
     * 다운로드된 APK 파일을 패키지 설치 관리자에게 전달하기 위한 Intent를 생성한다.
     */
    fun buildInstallIntent(apkFile: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private companion object {
        const val TAG = "AppUpdateController"
    }
}
