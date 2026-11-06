package com.forxd.treegrowth

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

const val RELEASE_ROOT = "https://github.com/ForXd/tree-growh/releases"
data class Release(val version: String, val code: Long, val url: String, val bytes: Long, val sha256: String, val notes: String)
data class UpdateState(val phase: String = "idle", val release: Release? = null, val progress: Float = 0f, val message: String = "自动检查更新已就绪", val file: File? = null)
data class PackageIdentity(val name: String, val version: String, val code: Long, val signers: Set<String>)
object UpdatePolicy {
    const val MAX_APK = 32L * 1024 * 1024
    fun parse(text: String): Release {
        require(text.length <= 64000) { "更新清单过大" }
        val json = JsonParser.parseString(text).asJsonObject
        val release = Release(json["version"].asString, json["versionCode"].asLong, json["url"].asString, json["bytes"].asLong, json["sha256"].asString, json["notes"]?.asString.orEmpty().take(2000))
        validate(release)
        return release
    }
    fun validate(r: Release) {
        require(r.version.matches(Regex("[0-9]{1,5}\\.[0-9]{1,5}\\.[0-9]{1,5}"))) { "版本格式无效" }
        require(r.code > 0 && r.bytes in 1..MAX_APK) { "更新包大小或版本无效" }
        require(r.url == "$RELEASE_ROOT/download/v${r.version}/qingya-${r.version}.apk") { "更新来源不属于青芽" }
        require(r.sha256.matches(Regex("[a-f0-9]{64}"))) { "缺少有效的 SHA-256" }
    }
    fun identity(candidate: PackageIdentity, installed: PackageIdentity, r: Release) {
        require(candidate.name == installed.name) { "安装包名称不匹配" }
        require(candidate.version == r.version && candidate.code == r.code && candidate.code > installed.code) { "安装包版本不匹配或不是新版" }
        require(candidate.signers.isNotEmpty() && candidate.signers == installed.signers) { "安装包签名不匹配" }
    }
    fun copy(input: InputStream, output: OutputStream, expected: Long, progress: (Long) -> Unit = {}): String {
        require(expected in 1..MAX_APK)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536); var total = 0L
        while (true) {
            val n = input.read(buffer); if (n == -1) break
            total += n; require(total <= expected) { "安装包大小超过清单" }
            digest.update(buffer, 0, n); output.write(buffer, 0, n); progress(total)
        }
        require(total == expected) { "安装包下载不完整" }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
class AppUpdater(private val app: Application, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()
    private val prefs = app.getSharedPreferences("updates", 0)
    private var task: Job? = null
    @Volatile private var activeCall: okhttp3.Call? = null
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(4, TimeUnit.MINUTES)
        .addNetworkInterceptor { chain ->
            val url = chain.request().url
            require(url.isHttps && (url.host == "github.com" || url.host == "api.github.com" || url.host.endsWith(".githubusercontent.com"))) { "更新重定向地址无效" }
            chain.proceed(chain.request())
        }.build()
    private val dir = File(app.filesDir, "updates/ready").apply { mkdirs() }
    init { scope.launch(Dispatchers.IO) {
        // Remove interrupted downloads and stale files after upgrades.
        dir.listFiles()?.filter { it.extension == "part" || !it.name.startsWith("v") }?.forEach { it.delete() }
    } }
    fun check(manual: Boolean = false) {
        if (task?.isActive == true || _state.value.phase == "ready") return
        val elapsed = System.currentTimeMillis() - prefs.getLong("last", 0)
        if (!manual && elapsed in 0..86_400_000) return
        task = scope.launch {
            _state.value = UpdateState(phase = "checking", message = "正在检查新版本…")
            try {
                val release = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url("$RELEASE_ROOT/latest/download/update.json").header("Cache-Control", "no-cache").build()
                    client.newCall(request).execute().use { response ->
                        require(response.isSuccessful) { "更新服务暂不可用（${response.code}）" }
                        val bytes = response.body!!.byteStream().use { input ->
                            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (output.size() <= 64000) { val n = input.read(buffer); if (n == -1) break; output.write(buffer, 0, n) }; output.toByteArray()
                        }
                        require(bytes.size <= 64000) { "更新清单过大" }
                        UpdatePolicy.parse(bytes.toString(Charsets.UTF_8))
                    }
                }
                prefs.edit().putLong("last", System.currentTimeMillis()).apply()
                _state.value = if (release.code > BuildConfig.VERSION_CODE) UpdateState("available", release, message = "发现新版本 ${release.version}") else UpdateState("current", message = "已经是最新版本 ${BuildConfig.VERSION_NAME}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = UpdateState("error", message = "检查失败，请检查网络后重试")
            }
        }
    }
    fun download() {
        if (task?.isActive == true) return
        val release = _state.value.release ?: return
        task = scope.launch {
            val dest = File(dir, "v${release.code}-${release.sha256}.apk")
            val temp = File(dir, "${dest.name}.part")
            _state.value = UpdateState("downloading", release, message = "正在下载更新…")
            try {
                withContext(Dispatchers.IO) {
                    UpdatePolicy.validate(release)
                    require(app.filesDir.usableSpace > release.bytes * 3) { "存储空间不足" }
                    dir.listFiles()?.filter { it != dest && it != temp }?.forEach { it.delete() }
                    if (!dest.exists()) {
                        val call = client.newCall(Request.Builder().url(release.url).build())
                        activeCall = call
                        try {
                            call.execute().use { response ->
                                require(response.isSuccessful) { "下载失败（${response.code}）" }
                                val hash = response.body!!.byteStream().use { input -> temp.outputStream().use { out ->
                                    UpdatePolicy.copy(input, out, release.bytes) { received ->
                                        if (!isActive) throw CancellationException()
                                        _state.value = UpdateState("downloading", release, received.toFloat() / release.bytes, "正在下载 ${(received * 100 / release.bytes)}%")
                                    }
                                } }
                                require(hash == release.sha256) { "文件校验失败，请重新下载" }
                            }
                        } finally { activeCall = null }
                        verify(temp, release)
                        require(temp.renameTo(dest)) { "无法保存安装包" }
                    }
                    verify(dest, release)
                }
                _state.value = UpdateState("ready", release, 1f, "校验完成，可以安装", dest)
            } catch (e: Exception) {
                temp.delete(); if (e !is CancellationException) dest.delete()
                _state.value = UpdateState("available", release, message = if (e is CancellationException) "下载已取消，可以重试" else e.message?.take(100) ?: "下载失败，请重试")
            }
        }
    }
    fun cancel() { task?.cancel(); activeCall?.cancel() }
    private fun packageIdentity(file: File? = null): PackageIdentity {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION") val info: PackageInfo = (if (file == null) app.packageManager.getPackageInfo(app.packageName, flags) else app.packageManager.getPackageArchiveInfo(file.path, flags)) ?: error("无法读取安装包")
        @Suppress("DEPRECATION") val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        @Suppress("DEPRECATION") val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return PackageIdentity(info.packageName, info.versionName.orEmpty(), code, signers.orEmpty().map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet())
    }
    private fun verify(file: File, release: Release) {
        val digest = file.inputStream().use { UpdatePolicy.copy(it, object : OutputStream() { override fun write(b: Int) {} ; override fun write(b: ByteArray, off: Int, len: Int) {} }, release.bytes) }
        require(digest == release.sha256) { "安装包摘要校验失败" }
        UpdatePolicy.identity(packageIdentity(file), packageIdentity(), release)
    }
    suspend fun installIntent(): Intent = withContext(Dispatchers.IO) {
        val s = _state.value; val file = s.file ?: error("请先下载新版")
        verify(file, s.release ?: error("更新信息失效"))
        Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(app, "${app.packageName}.updates", file), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
