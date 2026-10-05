package com.zlight106.nvvocab.domain

import com.zlight106.nvvocab.data.update.EasyUpdateRelease
import java.net.URI
import java.util.Locale

object EasyUpdatePolicy {
    const val MAX_APK_SIZE = 512L * 1024 * 1024

    fun normalizeServerUrl(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        require(uri != null && uri.scheme?.lowercase(Locale.ROOT) in listOf("http", "https") && !uri.host.isNullOrBlank()) {
            "请输入完整的 HTTP 或 HTTPS 更新服务器地址。"
        }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "更新服务器地址不能包含账号、查询参数或片段。"
        }
        require(uri.port in -1..65535 && uri.port != 0) { "更新服务器端口无效。" }
        val path = uri.rawPath.orEmpty().trimEnd('/').removeSuffix("/admin/help")
        return "${uri.scheme.lowercase(Locale.ROOT)}://${uri.rawAuthority}$path"
    }

    fun resolveDownloadUrl(serverUrl: String, downloadUrl: String): String {
        val source = URI("${normalizeServerUrl(serverUrl)}/")
        return checkedTarget(source, source.resolve(downloadUrl.trim()))
    }

    fun resolveRedirectUrl(currentUrl: String, location: String): String {
        val source = URI(currentUrl)
        return checkedTarget(source, source.resolve(location.trim()))
    }

    private fun checkedTarget(source: URI, resolved: URI): String {
        require(resolved.scheme?.lowercase(Locale.ROOT) in listOf("http", "https") &&
            !resolved.host.isNullOrBlank() && resolved.rawUserInfo == null && resolved.rawFragment == null) {
            "更新服务器返回的下载地址无效。"
        }
        fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
        require(source.scheme.equals(resolved.scheme, true) && source.host.equals(resolved.host, true) && port(source) == port(resolved)) {
            "更新下载或重定向地址必须与更新源使用相同的协议、主机和端口。"
        }
        return resolved.toASCIIString()
    }

    fun validateRelease(release: EasyUpdateRelease, currentVersionCode: Long) {
        require(release.versionCode > currentVersionCode) { "更新版本号必须高于当前版本。" }
        require(release.size in 1..MAX_APK_SIZE) { "更新文件大小无效，最大支持 512 MB。" }
        require(release.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "更新信息缺少有效的 SHA-256 校验值。" }
    }

    fun verifyDownload(release: EasyUpdateRelease, actualSize: Long, actualSha256: String) {
        require(actualSize == release.size) { "下载文件大小不一致，请重新检查更新。" }
        require(actualSha256.equals(release.sha256, ignoreCase = true)) { "APK 校验失败，请重新检查更新。" }
    }
}
