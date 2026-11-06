package com.forxd.treegrowth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable fun ChallengeScreen(model: GardenModel, data: GardenData) {
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Challenge?>(null) }
    var selectedCell by rememberSaveable { mutableStateOf<Int?>(null) }
    var showArchive by rememberSaveable { mutableStateOf(false) }
    var archiveConfirm by remember { mutableStateOf(false) }
    var celebration by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val active = data.challenges.find { it.id == activeId } ?: data.challenges.firstOrNull { !it.archived }
    val loaded by model.loaded.collectAsState()
    var previousSize by remember { mutableIntStateOf(data.challenges.size) }
    LaunchedEffect(data.challenges.size) { if (data.challenges.size > previousSize) activeId = data.challenges.lastOrNull()?.id; previousSize = data.challenges.size }
    var pendingCount by remember { mutableStateOf<Int?>(null) }
    val saveError by model.error.collectAsState()
    LaunchedEffect(saveError) { if (saveError != null) pendingCount = null }
    LaunchedEffect(active?.cells?.size) {
        if (pendingCount != null && active?.cells?.size == pendingCount) {
            val count = pendingCount!!
            notice = if (count == 100) "100 次，你真的做到了。" else if (count % 10 == 0) "第 ${count / 10} 行完成！已经坚持了 $count 次。" else "第 $count 次努力，已被收藏。"
            if (count % 10 == 0 || count == 1) celebration++
            pendingCount = null
            if (!reduce) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(notice) { if (notice != null) { delay(3500); notice = null } }
    LazyColumn(modifier = Modifier.testTag("challenge-list"), contentPadding = PaddingValues(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("ONE HUNDRED LITTLE WINS", fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary); Text("每一次，都算数", style = MaterialTheme.typography.headlineMedium) }
                FilledTonalIconButton(onClick = { editing = null; editor = true }, enabled = loaded && data.challenges.size < 100) { Icon(Icons.Default.Add, "新建百次挑战") }
            }
        }
        if (data.challenges.any { !it.archived }) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.challenges.filter { !it.archived }.forEach { challenge -> FilterChip(selected = active?.id == challenge.id, onClick = { activeId = challenge.id; pendingCount = null; selectedCell = null }, label = { Text("${challenge.emoji} ${challenge.title}") }) }
            }
        }
        if (active == null) item {
            Surface(shape = RoundedCornerShape(28.dp), color = Forest) {
                Column(Modifier.fillMaxWidth().padding(25.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("100", fontSize = 64.sp, lineHeight = 68.sp, color = Lime, fontWeight = FontWeight.Bold)
                    Text("不是连续 100 天，\n是为自己做 100 次。", style = MaterialTheme.typography.headlineMedium, color = androidx.compose.ui.graphics.Color.White)
                    Text("运动、英语、阅读……没有截止日期，\n同一天也可以记录多次。", color = Lime, lineHeight = 25.sp)
                    Button(onClick = { editing = null; editor = true }, enabled = loaded, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Forest), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("开启我的 100 次") }
                }
            }
        }
        if (active != null) {
            item { ChallengeBoard(active, selectedCell, celebration) { selectedCell = it } }
            item {
                if (!active.archived && active.nextIndex() != null) Button(onClick = {
                    active.nextIndex()?.let { next -> pendingCount = active.cells.size + 1; model.challengeCell(active.id, next, true) }
                }, enabled = pendingCount == null, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(20.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("完成一次 · 点亮第 ${active.nextIndex()!! + 1} 格", style = MaterialTheme.typography.titleMedium) }
                else if (active.cells.size == 100) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("✦  百次达成，值得为自己骄傲  ✦", modifier = Modifier.fillMaxWidth().padding(18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold) }
                AnimatedVisibility(notice != null) { Text(notice.orEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selectedCell = active.cells.maxByOrNull { it.at }?.index ?: 0 }) { Text("查看与撤销记录") }
                    TextButton(onClick = { editing = active; editor = true }) { Text("编辑挑战") }
                }
            }
            item {
                Text("每十次，都值得纪念", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 30, 50, 100).forEach { goal ->
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(17.dp), color = if (active.cells.size >= goal) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) { Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(if (active.cells.size >= goal) "✦" else "✧", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary); Text("$goal 次", fontSize = 13.sp); Text(when(goal) { 10 -> "初露锋芒"; 30 -> "稳步向前"; 50 -> "半程精彩"; else -> "百次成就" }, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    }
                }
            }
            item { TextButton(onClick = { archiveConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text(if (active.archived) "重新开启这个挑战" else "收藏这个挑战", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        if (data.challenges.any { it.archived }) item {
            TextButton(onClick = { showArchive = !showArchive }) { Text(if (showArchive) "收起收藏" else "查看收藏的挑战") }
            if (showArchive) data.challenges.filter { it.archived }.forEach { c -> TextButton(onClick = { activeId = c.id }) { Text("${c.emoji} ${c.title} · ${c.cells.size}/100") } }
        }
        item { Text("次数不是日历，是努力留下的形状。", modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (editor) ChallengeEditor(editing, close = { editor = false }) { title, emoji -> model.saveChallenge(editing, title, emoji); editor = false; if (editing == null) activeId = null }
    if (active != null && selectedCell != null) CellSheet(active, selectedCell!!, close = { selectedCell = null }, navigate = { selectedCell = it }) { index, done, note ->
        if (done && active.cells.none { it.index == index }) pendingCount = active.cells.size + 1
        model.challengeCell(active.id, index, done, note); selectedCell = null
    }
    if (archiveConfirm && active != null) AlertDialog(onDismissRequest = { archiveConfirm = false }, title = { Text(if (active.archived) "重新开启挑战？" else "收藏这份努力？") }, text = { Text("全部格子和备注都会保留。你可以随时从收藏中重新开启。") }, confirmButton = { TextButton(onClick = { model.archiveChallenge(active.id); activeId = null; archiveConfirm = false }) { Text("确认") } }, dismissButton = { TextButton(onClick = { archiveConfirm = false }) { Text("取消") } })
}
@Composable fun ChallengeBoard(challenge: Challenge, selected: Int?, celebration: Int, select: (Int) -> Unit) {
    val reduce = LocalReduceMotion.current
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            Box(Modifier.fillMaxWidth().background(Forest)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${challenge.emoji} ${challenge.title}", color = Lime, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(if (challenge.archived) "已收藏 · 努力不会消失" else "不必每天，一次也值得。", color = Lime.copy(.8f), fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    AnimatedContent(challenge.cells.size, transitionSpec = { fadeIn(tween(if (reduce) 0 else 180)) togetherWith fadeOut(tween(if (reduce) 0 else 100)) }, label = "challenge counter") { count ->
                        Text(String.format(java.util.Locale.ROOT, "%02d", count), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(" /100", color = Lime, fontSize = 12.sp)
                    Box(Modifier.padding(start = 12.dp).size(40.dp), contentAlignment = Alignment.Center) {
                        val progress by animateFloatAsState(challenge.cells.size/100f, tween(if (LocalReduceMotion.current) 0 else 400), label = "challenge ring")
                        CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = Lime, trackColor = Color.White.copy(.15f), strokeWidth = 3.dp)
                        Icon(if (challenge.cells.size == 100) Icons.Default.Star else Icons.Default.Check, null, tint = Lime, modifier = Modifier.size(18.dp))
                    }
                }
                Celebration(celebration, Modifier.matchParentSize())
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HundredGrid(challenge, selected, select)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("10 × 10 · 一格就是一次", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点格子查看 / 补打", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
@Composable fun HundredGrid(challenge: Challenge, selected: Int?, onCell: (Int) -> Unit) {
    val completed = remember(challenge.cells) { challenge.cells.map { it.index }.toSet() }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(10) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(10) { col ->
                    val index = row * 10 + col; val done = index in completed
                    val tint by animateColorAsState(if (done) Forest else MaterialTheme.colorScheme.background, tween(if (LocalReduceMotion.current) 0 else 260), label = "cell $index")
                    val fill by animateFloatAsState(if (done) 1f else 0f, tween(if (LocalReduceMotion.current) 0 else 360), label = "check $index")
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(7.dp)).background(tint).border(if (selected == index) 2.dp else 1.dp, if (selected == index) Clay else if (done) Forest else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
                        .clickable { onCell(index) }.semantics { contentDescription = "第${index + 1}格，${if (done) "已完成" else "未完成"}，查看记录" }, contentAlignment = Alignment.Center) {
                        if (done) Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                            val path = Path().apply { moveTo(0f, size.height*.5f); lineTo(size.width*.36f, size.height*.86f); lineTo(size.width, size.height*.1f) }
                            val measure = PathMeasure().apply { setPath(path, false) }; val segment = Path(); measure.getSegment(0f, measure.length*fill, segment)
                            drawPath(segment, Lime, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        } else Text("${index+1}", fontSize = 8.sp, lineHeight = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@Composable fun Celebration(trigger: Int, modifier: Modifier) {
    val progress = remember { Animatable(1f) }; val reduce = LocalReduceMotion.current
    LaunchedEffect(trigger) { if (trigger > 0 && !reduce) { progress.snapTo(0f); progress.animateTo(1f, tween(1200, easing = LinearEasing)) } }
    Canvas(modifier) {
        val t = progress.value
        if (t < 1) repeat(22) { i ->
            val angle = (i * 2.39996f); val radius = size.width * .55f * t
            val x = size.width*.6f + cos(angle)*radius; val y = size.height*.45f + sin(angle)*radius*.7f + t*t*size.height*.35f
            drawCircle(if (i % 2 == 0) Lime.copy(alpha = 1-t) else Color(0xFFE8B676).copy(alpha = 1-t), (2 + i % 3).dp.toPx(), Offset(x,y))
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ChallengeEditor(challenge: Challenge?, close: () -> Unit, save: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(challenge?.title ?: "") }
    var emoji by rememberSaveable { mutableStateOf(challenge?.emoji ?: "🏃") }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (challenge == null) "和自己约好，做 100 次" else "编辑百次挑战", style = MaterialTheme.typography.headlineMedium)
            Text("不限日期，不设连续天数。每做一次，就给自己一个对勾。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🏃" to "运动100次", "🎧" to "学英语100次", "📖" to "阅读100次").forEach { (icon, name) -> SuggestionChip(onClick = { title = name; emoji = icon }, label = { Text("$icon $name") }) }
            }
            OutlinedTextField(title, { title = it.take(40) }, label = { Text("挑战名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🏃", "🎧", "📖", "✍️", "🧘", "🎨").forEach { icon -> Surface(onClick = { emoji = icon }, shape = RoundedCornerShape(14.dp), color = if (icon == emoji) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) { Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp) } } }
            }
            Text("10 行 × 10 列 = 100 个小成就", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { save(title.trim(), emoji) }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(if (challenge == null) "开启百次挑战" else "保存") }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CellSheet(challenge: Challenge, index: Int, close: () -> Unit, navigate: (Int) -> Unit, save: (Int, Boolean, String) -> Unit) {
    val cell = challenge.cells.find { it.index == index }
    var note by rememberSaveable(challenge.id, index) { mutableStateOf(cell?.note.orEmpty()) }
    var undo by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navigate(index-1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowLeft, "上一格") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("第 ${index+1} 次", style = MaterialTheme.typography.headlineMedium); Text(if (cell != null) "✓ 已完成" else "等待你的下一份努力", color = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { navigate(index+1) }, enabled = index < 99) { Icon(Icons.Default.KeyboardArrowRight, "下一格") }
            }
            if (cell != null) Text(Instant.parse(cell.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(challenge.title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(note, { note = it.take(500) }, label = { Text("这次做了什么？（可选）") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
            Button(onClick = { save(index, true, note) }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !challenge.archived) { Text(if (cell == null) "为这一格打勾" else "保存这次记录") }
            if (cell != null && !challenge.archived) TextButton(onClick = { undo = true }, modifier = Modifier.fillMaxWidth()) { Text("撤销这一格的打卡") }
            Text("同一天可以记录多次。点错了也没关系，随时可以撤销。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (undo) AlertDialog(onDismissRequest = { undo = false }, title = { Text("撤销第 ${index+1} 格？") }, text = { Text("这个格子的打卡与备注将被清除，其他格子不受影响。") }, confirmButton = { TextButton(onClick = { save(index, false, "") }) { Text("确认撤销") } }, dismissButton = { TextButton(onClick = { undo = false }) { Text("保留") } })
}
