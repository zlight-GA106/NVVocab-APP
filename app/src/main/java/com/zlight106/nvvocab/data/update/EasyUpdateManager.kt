package com.zlight106.nvvocab.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zlight106.nvvocab.domain.EasyUpdatePolicy
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject

class EasyUpdateManager(context: Context) {
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(EasyUpdateState())
    private val mutex = Mutex()
    val state = mutableState.asStateFlow()

    suspend fun checkAndDownload(serverUrl: String) = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        var candidate: File? = null
        try {
            val source = normalizeServerUrl(serverUrl)
            val current = installedPackage()
            mutableState.value = EasyUpdateState(EasyUpdatePhase.CHECKING, "正在检查更新")
            val endpoint = "$source/api/v1/apps/${Uri.encode(context.packageName)}/latest?version_code=${current.longVersionCode}"
            val metadata = readMetadata(endpoint)
            require(metadata.optString("package_name") == context.packageName) { "更新源返回的包名与当前应用不一致。" }
            if (!metadata.getBoolean("update_available")) {
                val latestCode = metadata.getLong("latest_version_code")
                val latestName = metadata.getString("latest_version_name")
                require(latestCode in 1..current.longVersionCode && latestName.isNotBlank()) { "更新源返回的版本信息不一致。" }
                mutableState.value = EasyUpdateState(EasyUpdatePhase.NO_UPDATE, "当前版本无需更新（更新源最新版本：$latestName）")
                return@withContext
            }
            val release = EasyUpdateRelease(
                versionName = metadata.getString("version_name"),
                versionCode = metadata.getLong("version_code"),
                releaseNotes = metadata.optString("release_notes"),
                downloadUrl = EasyUpdatePolicy.resolveDownloadUrl(source, metadata.getString("download_url")),
                size = metadata.getLong("size"),
                sha256 = metadata.getString("sha256"),
                mandatory = metadata.optBoolean("mandatory"),
            )
            EasyUpdatePolicy.validateRelease(release, current.longVersionCode)
            val directory = File(context.cacheDir, "easyupdate").apply {
                check(isDirectory || mkdirs()) { "无法创建更新下载目录。" }
            }
            directory.listFiles()?.filter { it.name.startsWith("update-") }?.forEach(File::delete)
            candidate = File(directory, "update-${release.versionCode}.apk")
            mutableState.value = EasyUpdateState(EasyUpdatePhase.DOWNLOADING, "发现 ${release.versionName}，正在自动下载", 0f, release)
            download(release, candidate)
            verifyApk(candidate, release, current)
            mutableState.value = EasyUpdateState(EasyUpdatePhase.READY, "下载与校验完成，准备安装 ${release.versionName}", 1f, release, candidate.absolutePath)
            candidate = null
        } catch (cancelled: CancellationException) {
            mutableState.value = EasyUpdateState()
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = EasyUpdateState(EasyUpdatePhase.ERROR, error.message ?: "检查或下载更新失败，请重试。")
        } finally {
            candidate?.delete()
            mutex.unlock()
        }
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    fun installerIntent(): Intent {
        val ready = mutableState.value
        check(ready.phase == EasyUpdatePhase.READY) { "请先下载更新。" }
        val apk = File(requireNotNull(ready.apkPath))
        check(apk.isFile && apk.length() == ready.release?.size) { "下载文件已失效，请重新检查更新。" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.easyupdate.files", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun readMetadata(url: String): JSONObject {
        val connection = connection(url, "application/json")
        try {
            requireSuccess(connection)
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    check(result.length + count <= 1024 * 1024) { "更新响应过大。" }
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(release: EasyUpdateRelease, file: File) {
        val connection = connection(release.downloadUrl, "application/vnd.android.package-archive")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var previousProgress = -1
        try {
            requireSuccess(connection)
            val contentLength = connection.contentLengthLong
            require(contentLength == -1L || contentLength == release.size) { "下载响应的文件大小与更新信息不一致。" }
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= release.size) { "下载文件超过声明的大小。" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        val percent = (total * 100 / release.size).toInt()
                        if (percent != previousProgress) {
                            previousProgress = percent
                            mutableState.value = EasyUpdateState(EasyUpdatePhase.DOWNLOADING, "正在下载 ${release.versionName} · $percent%", total.toFloat() / release.size, release)
                        }
                    }
                }
            }
            EasyUpdatePolicy.verifyDownload(release, total, digest.digest().toHex())
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackage(): PackageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

    @Suppress("DEPRECATION")
    private fun verifyApk(file: File, release: EasyUpdateRelease, current: PackageInfo) {
        val apk = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("下载文件不是有效的 Android 安装包。")
        require(apk.packageName == context.packageName) { "下载的 APK 包名与当前应用不一致。" }
        require(apk.longVersionCode == release.versionCode && apk.longVersionCode > current.longVersionCode) { "APK 版本号与更新信息不一致。" }
        val currentSigning = requireNotNull(current.signingInfo) { "无法读取当前应用签名。" }
        val incomingSigning = requireNotNull(apk.signingInfo) { "无法读取更新 APK 签名。" }
        val installedSigners = currentSigning.apkContentsSigners.map { it.toByteArray().toHex() }.toSet()
        val incomingSigners = incomingSigning.apkContentsSigners.map { it.toByteArray().toHex() }.toSet()
        val compatible = if (currentSigning.hasMultipleSigners() || incomingSigning.hasMultipleSigners()) {
            installedSigners == incomingSigners
        } else {
            val history = incomingSigning.signingCertificateHistory.orEmpty().map { it.toByteArray().toHex() }.toSet()
            installedSigners.isNotEmpty() && history.containsAll(installedSigners)
        }
        require(compatible) { "更新 APK 签名不匹配，无法覆盖升级。请在更新服务器上传同签名安装包。" }
    }

    private fun connection(url: String, accept: String): HttpURLConnection {
        var currentUrl = url
        repeat(6) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                if (connection.responseCode !in setOf(301, 302, 303, 307, 308)) return connection
                val location = connection.getHeaderField("Location")?.takeIf(String::isNotBlank)
                    ?: error("更新服务器重定向地址为空。")
                currentUrl = EasyUpdatePolicy.resolveRedirectUrl(currentUrl, location)
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        error("更新服务器重定向次数过多。")
    }

    private fun requireSuccess(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status == 404) error("更新源未找到此应用或已发布版本，请先在 EasyUpdate 中发布 ${context.packageName}。")
        check(status in 200..299) { "更新服务器请求失败（HTTP $status）。" }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        fun normalizeServerUrl(value: String): String = EasyUpdatePolicy.normalizeServerUrl(value)
    }
}
