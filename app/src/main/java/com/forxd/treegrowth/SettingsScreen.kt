package com.forxd.treegrowth

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.LocalDate

@Composable fun SettingsScreen(model: GardenModel, data: GardenData, snackbar: SnackbarHostState, openUpdates: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = data.preferences
    var restore by remember { mutableStateOf<GardenData?>(null) }
    var privacy by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        model.preferences(model.data.value.preferences.copy(reminders = allowed))
        if (!allowed) scope.launch { snackbar.showSnackbar("未开启通知权限，你仍可以随时手动打卡") }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { scope.launch {
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(it, "wt")!!.use { stream -> stream.write(BackupCodec.encode(model.data.value).toByteArray()) } }; snackbar.showSnackbar("备份已导出，请妥善保存") }
            catch (_: Exception) { snackbar.showSnackbar("导出失败，请重新选择保存位置") }
        } }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch {
            try {
                restore = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(it)!!.use { input ->
                        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n == -1) break; require(output.size() + n <= BackupCodec.MAX_BYTES) { "备份超过 4 MB" }; output.write(buffer, 0, n) }; output.toByteArray()
                    }
                    BackupCodec.decode(bytes.toString(Charsets.UTF_8))
                }
            } catch (e: Exception) { snackbar.showSnackbar(e.message?.take(100) ?: "备份无法读取") }
        } }
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("MAKE ROOM FOR YOURSELF", "按自己的节奏", "一座属于你的花园，简单，也安心。") }
        item { Surface(shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text("🌱", style = MaterialTheme.typography.headlineLarge); Column(Modifier.padding(start = 16.dp)) { Text("青芽 Qingya", style = MaterialTheme.typography.titleLarge); Text("版本 ${BuildConfig.VERSION_NAME} · 离线也能好好生长", style = MaterialTheme.typography.bodySmall) } }
        } }
        item { Text("舒适的使用方式", style = MaterialTheme.typography.titleLarge) }
        item { SettingCard {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, title) -> FilterChip(selected = prefs.theme == mode, onClick = { model.preferences(prefs.copy(theme = mode)) }, label = { Text(title) }) }
            }
            SettingToggle("减少动画", "更安静的切换与反馈", prefs.reducedMotion) { model.preferences(prefs.copy(reducedMotion = it)) }
        } }
        item { SettingCard {
            SettingToggle("每日温柔提醒", "仅在当天还有习惯未完成时提醒", prefs.reminders) { enabled ->
                if (enabled && !Reminders.allowed(context) && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else model.preferences(prefs.copy(reminders = enabled))
            }
            TextButton(onClick = { TimePickerDialog(context, { _, hour, minute -> model.preferences(prefs.copy(hour = hour, minute = minute)) }, prefs.hour, prefs.minute, true).show() }) { Text("提醒时间  %02d:%02d".format(prefs.hour, prefs.minute)) }
            Text("系统省电模式可能延后提醒；百次挑战不按天催促。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { Text("你的数据，你来保管", style = MaterialTheme.typography.titleLarge) }
        item { SettingCard {
            SettingAction("导出备份", "包含习惯、百次挑战、记录与偏好") { export.launch("qingya-backup-${LocalDate.now()}.json") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingAction("恢复备份", "导入前会展示数量，并确认替换") { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingAction("隐私说明", "无需账号，记录只保存在本机") { privacy = true }
        } }
        item { SettingCard {
            SettingToggle("自动检查新版本", "每天首次打开时检查 GitHub 发布", prefs.autoUpdate) { model.preferences(prefs.copy(autoUpdate = it)) }
            SettingAction("应用更新", "当前 ${BuildConfig.VERSION_NAME} · 下载后由系统确认安装", openUpdates)
        } }
        item { Text("好好生活，慢慢生长。\nMade with care · ForXd", modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
    restore?.let { backup ->
        AlertDialog(onDismissRequest = { restore = null }, title = { Text("恢复这份花园备份？") }, text = { Text("包含 ${backup.habits.size} 个习惯、${backup.challenges.size} 个百次挑战、${backup.entries.size} 条习惯记录。\n\n这将替换本机现有数据，建议先导出当前备份。") }, confirmButton = { TextButton(onClick = { scope.launch { try { model.restore(backup); restore = null; snackbar.showSnackbar("恢复完成，欢迎回到你的花园") } catch (_: Exception) { snackbar.showSnackbar("恢复失败，现有数据未被替换") } } }) { Text("确认替换") } }, dismissButton = { TextButton(onClick = { restore = null }) { Text("取消") } })
    }
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text("你的花园，只属于你") }, text = { Text("青芽不需要账号，不含广告或分析 SDK。习惯、挑战和备注保存在应用私有目录，不上传到服务器。\n\n更新检测与下载会连接 GitHub，对方可看到网络 IP 等普通请求信息，但不会收到打卡数据。可关闭自动检查。\n\n卸载会删除本地数据，请定期导出备份。导出的 JSON 是明文，请保存在可信位置。系统自动云备份已关闭。") }, confirmButton = { TextButton(onClick = { privacy = false }) { Text("知道了") } })
}
@Composable fun SettingCard(content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(23.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) } }
@Composable fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onChange) } }
@Composable fun SettingAction(title: String, subtitle: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.KeyboardArrowRight, null) } }
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UpdateSheet(updater: AppUpdater, close: () -> Unit) {
    val state by updater.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    fun install() { scope.launch { try { context.startActivity(updater.installIntent()) } catch (e: Exception) { error = e.message ?: "无法打开系统安装器" } } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (context.packageManager.canRequestPackageInstalls()) install() else error = "尚未允许安装，已下载文件保留，可再次尝试" }
    LaunchedEffect(Unit) { updater.check(true) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("青芽在慢慢变好", style = MaterialTheme.typography.headlineMedium)
            Text("当前版本 ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.message)
            state.release?.let { Text("新版 ${it.version} · %.1f MB".format(it.bytes/1_000_000f)); if (it.notes.isNotBlank()) Text(it.notes) }
            if (state.phase == "checking") LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase == "downloading") { LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { updater.cancel() }) { Text("取消下载") } }
            if (state.phase == "available") Button(onClick = { updater.download() }, modifier = Modifier.fillMaxWidth()) { Text("下载更新") }
            if (state.phase == "ready") Button(onClick = {
                if (context.packageManager.canRequestPackageInstalls()) install()
                else try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { error = "请在系统设置中允许青芽安装应用" }
            }, modifier = Modifier.fillMaxWidth()) { Text("安装更新") }
            if (state.phase !in listOf("checking", "downloading", "ready")) TextButton(onClick = { updater.check(true) }) { Text("重新检查") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("更新保留你的花园。下载完成后校验文件、包名、版本及签名，由 Android 确认安装。首次需要允许青芽安装更新。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("关闭面板后下载继续；若应用进程被结束，需要重新打开后重试。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
