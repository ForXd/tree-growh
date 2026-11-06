package com.forxd.treegrowth

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: GardenModel = viewModel()
            val data by model.data.collectAsState()
            GardenTheme(data.preferences) { GardenApp(model, data) }
        }
    }
}
@Composable fun GardenApp(model: GardenModel, data: GardenData) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedString by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val date = LocalDate.parse(selectedString)
    var editing by remember { mutableStateOf<Habit?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Habit?>(null) }
    var updates by rememberSaveable { mutableStateOf(false) }
    val updateState by model.updater.state.collectAsState()
    val loaded by model.loaded.collectAsState()
    val error by model.error.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycle, loaded, data.preferences.autoUpdate) {
        if (lifecycle == androidx.lifecycle.Lifecycle.State.RESUMED) {
            val current = LocalDate.now()
            if (date == today) selectedString = current.toString()
            today = current
            if (loaded && data.preferences.autoUpdate) model.updater.check()
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(30000); val current = LocalDate.now(); if (today != current) { if (selectedString == today.toString()) selectedString = current.toString(); today = current } } }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it) } }
    BackHandler(tab != 0) { tab = 0 }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                val labels = listOf("今天", "百次", "花园", "趋势", "我的")
                val icons = listOf(Icons.Default.CheckCircle, Icons.Default.Star, Icons.Default.Favorite, Icons.Default.DateRange, Icons.Default.Person)
                labels.forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], contentDescription = null, modifier = Modifier.size(23.dp)) }, label = { Text(title) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer))
                }
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(targetState = tab, modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(), transitionSpec = { fadeIn(tween(if (reduce) 0 else 220)) togetherWith fadeOut(tween(if (reduce) 0 else 120)) }, label = "page") { page ->
                when (page) {
                    0 -> TodayScreen(data, date, today, loaded, updateState.release != null,
                        chooseDate = { selectedString = it.toString() }, add = { editing = null; editor = true }, open = { detail = it }, updates = { updates = true },
                        check = { habit ->
                            val previous = data.entry(habit.id, date)?.amount ?: 0
                            val complete = data.complete(habit, date)
                            model.record(habit, date, if (complete) 0 else habit.plan(date).target)
                            if (!reduce) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result = snackbar.showSnackbar(if (complete) "已撤销这次打卡" else "一片新叶，来自你的坚持", "撤销", duration = SnackbarDuration.Short)
                                if (result == SnackbarResult.ActionPerformed) model.record(habit, date, previous)
                            }
                        })
                    1 -> ChallengeScreen(model, data)
                    2 -> GardenScreen(data)
                    3 -> TrendsScreen(data, today) { selectedString = it.toString(); tab = 0 }
                    else -> SettingsScreen(model, data, snackbar) { updates = true }
                }
            }
        }
    }
    if (editor) HabitEditor(editing, save = { title, emoji, color, unit, target, days -> model.saveHabit(editing, title, emoji, color, unit, target, days); editor = false }, close = { editor = false })
    detail?.let { selected ->
        val habit = data.habits.find { it.id == selected.id } ?: selected
        HabitDetail(habit, data, date, model, edit = { detail = null; editing = habit; editor = true }, close = { detail = null })
    }
    if (updates) UpdateSheet(model.updater) { updates = false }
}

@Composable fun PageTitle(eyebrow: String, title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
                Spacer(Modifier.height(5.dp)); Text(title, style = MaterialTheme.typography.headlineLarge)
            }
            action?.invoke()
        }
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp))
    }
}
@Composable fun TodayScreen(data: GardenData, date: LocalDate, today: LocalDate, loaded: Boolean, hasUpdate: Boolean, chooseDate: (LocalDate) -> Unit, add: () -> Unit, open: (Habit) -> Unit, updates: () -> Unit, check: (Habit) -> Unit) {
    val context = LocalContext.current
    val due = data.due(date)
    val completed = data.completed(date)
    val earliest = data.habits.minOfOrNull { LocalDate.parse(it.created) } ?: today
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("QINGYA  /  青芽", if (date == today) "今天，也长大一点" else "回到这一天", today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)), action = {
            IconButton(onClick = {
                DatePickerDialog(context, { _, y, m, d -> chooseDate(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis(); datePicker.minDate = earliest.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.show()
            }) { Icon(Icons.Default.DateRange, "选择日期补记") }
        }) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (6 downTo 0).forEach { offset ->
                    val d = today.minusDays(offset.toLong()); val selected = d == date
                    Surface(onClick = { chooseDate(d) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(19.dp), color = if (selected) Forest else MaterialTheme.colorScheme.surface, enabled = d >= earliest) {
                        Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(listOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1], fontSize = 11.sp, color = if (selected) Lime else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(d.dayOfMonth.toString(), modifier = Modifier.padding(top = 7.dp), fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                            Box(Modifier.padding(top = 6.dp).size(4.dp).background(if (data.completed(d) > 0) (if (selected) Lime else Forest) else Color.Transparent, CircleShape))
                        }
                    }
                }
            }
        }
        item { GrowthHero(completed, due.size, data.totalLeaves()) }
        if (hasUpdate) item { Surface(onClick = updates, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("青芽有新版本了"); Text("查看 →") } } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text(if (date == today) "今天的小习惯" else "${date.monthValue}月${date.dayOfMonth}日 · 补记", style = MaterialTheme.typography.titleLarge); Text("$completed / ${due.size} 已完成", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                FilledTonalIconButton(onClick = add, enabled = loaded && data.habits.size < 200) { Icon(Icons.Default.Add, "添加习惯") }
            }
        }
        if (!loaded) item { Text("正在读取你的花园… 若读取失败，可在“我的”恢复备份。") }
        else if (due.isEmpty()) item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌱", fontSize = 38.sp); Spacer(Modifier.height(12.dp))
                    Text(if (data.habits.isEmpty()) "从一件小事开始" else "这一天，给自己一点留白", style = MaterialTheme.typography.titleMedium)
                    Text(if (data.habits.isEmpty()) "喝水、阅读、散步。选一个喜欢的习惯，\n让第一片新叶从今天长出来。" else "没有安排习惯，休息也是生活的一部分。", modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (data.habits.isEmpty()) Button(onClick = add) { Text("种下第一个习惯") }
                }
            }
        }
        items(due, key = { it.id }) { habit -> HabitCard(habit, data, date, { open(habit) }, { check(habit) }) }
        item { Text("慢慢来，每一小步都算数。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}
@Composable fun GrowthHero(done: Int, total: Int, leaves: Int) {
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, tween(if (LocalReduceMotion.current) 0 else 600), label = "daily progress")
    Surface(shape = RoundedCornerShape(28.dp), color = Forest) {
        Row(Modifier.fillMaxWidth().heightIn(min = 195.dp).padding(start = 23.dp, top = 23.dp, bottom = 23.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.1f)) {
                Text("TODAY'S GROWTH", fontSize = 10.sp, letterSpacing = 2.sp, color = Lime)
                Text(if (total > 0 && done == total) "今天，长大了一点" else "让好习惯\n自然生长", color = Color.White, fontSize = 25.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$done", fontSize = 28.sp, color = Lime, fontWeight = FontWeight.Bold)
                    Text(" / $total 个小目标", color = Color(0xFFD0DFCF), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(top = 10.dp).fillMaxWidth(.86f).height(4.dp).clip(CircleShape), color = Lime, trackColor = Color.White.copy(alpha = .16f))
            }
            TreeArt(leaves = leaves, modifier = Modifier.weight(.9f).height(170.dp), bright = true)
        }
    }
}
@Composable fun TreeArt(leaves: Int, modifier: Modifier = Modifier, bright: Boolean = false) {
    val reduce = LocalReduceMotion.current
    // Finite growth animation; no idle render loop or battery-draining particles.
    val growth by animateFloatAsState(.82f + min(leaves, 60) / 330f, spring(dampingRatio = .72f, stiffness = if (reduce) 10000f else 160f), label = "tree growth")
    Canvas(modifier.semantics { contentDescription = "成长树，累计 $leaves 片新叶" }) {
        val w = size.width; val h = size.height
        val leaf = if (bright) Color(0xFFD4E6A8) else Color(0xFF73976A)
        drawOval(if (bright) Color(0xFF1E4732) else Color(0xFFCBD3BA), Offset(w * .14f, h * .87f), Size(w * .72f, h * .08f))
        scale(growth, growth, Offset(w * .5f, h * .88f)) {
            drawLine(Color(0xFFB59B71), Offset(w * .5f, h * .88f), Offset(w * .5f, h * .35f), w * .045f, StrokeCap.Round)
            drawLine(Color(0xFFB59B71), Offset(w * .5f, h * .65f), Offset(w * .29f, h * .44f), w * .028f, StrokeCap.Round)
            drawLine(Color(0xFFB59B71), Offset(w * .5f, h * .57f), Offset(w * .71f, h * .35f), w * .028f, StrokeCap.Round)
            val canopy = listOf(Triple(.3f,.39f,.22f), Triple(.53f,.25f,.24f), Triple(.73f,.38f,.2f), Triple(.48f,.45f,.25f))
            canopy.forEachIndexed { i, (x,y,r) -> drawCircle(leaf.copy(alpha = 1f), w*r, Offset(w*x,h*y)); if (i % 2 == 0) drawCircle(Color(0xFFABC787), w*r*.72f, Offset(w*x+w*.025f,h*y+h*.04f)) }
            drawCircle(Color(0xFFF0D59B), w * .045f, Offset(w*.38f,h*.35f))
            drawCircle(Color(0xFFF0D59B), w * .033f, Offset(w*.67f,h*.44f))
        }
        drawLine(leaf, Offset(w*.2f,h*.87f),Offset(w*.17f,h*.81f),2.dp.toPx(),StrokeCap.Round)
        drawLine(leaf, Offset(w*.8f,h*.87f),Offset(w*.83f,h*.8f),2.dp.toPx(),StrokeCap.Round)
        if (bright) { drawCircle(Lime.copy(.6f),2.dp.toPx(), Offset(w*.12f,h*.2f)); drawCircle(Lime.copy(.5f),1.5.dp.toPx(),Offset(w*.89f,h*.63f)) }
    }
}
@Composable fun HabitCard(habit: Habit, data: GardenData, date: LocalDate, open: () -> Unit, check: () -> Unit) {
    val done = data.complete(habit, date)
    val amount = data.entry(habit.id, date)?.amount ?: 0
    val target = habit.plan(date).target
    val progress by animateFloatAsState((amount.toFloat() / target).coerceIn(0f, 1f), tween(if (LocalReduceMotion.current) 0 else 400), label = "habit progress")
    val tint by animateColorAsState(if (done) MaterialTheme.colorScheme.secondaryContainer.copy(.6f) else MaterialTheme.colorScheme.surface, tween(if (LocalReduceMotion.current) 0 else 260), label = "habit color")
    Surface(onClick = open, shape = RoundedCornerShape(23.dp), color = tint) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(48.dp).background(Accents[habit.color], RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Text(habit.emoji, fontSize = 24.sp) }
            Column(Modifier.weight(1f)) {
                Text(habit.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (done) "已完成 · 又积攒了一份美好" else "$amount / $target ${habit.unit}  ·  连续 ${data.streak(habit, date)} 次", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (target > 1) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 9.dp).height(3.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
            }
            IconButton(onClick = check, modifier = Modifier.size(48.dp).semantics { contentDescription = if (done) "撤销${habit.title}打卡" else "完成${habit.title}" }) {
                Box(Modifier.size(32.dp).background(if (done) Forest else Color.Transparent, CircleShape).border(if (done) 0.dp else 1.5.dp, if (done) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) { if (done) Icon(Icons.Default.Check, null, tint = Lime, modifier = Modifier.size(19.dp)) else Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable fun GardenScreen(data: GardenData) {
    val leaves = data.totalLeaves(); val level = leaves / 30 + 1
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle("YOUR LITTLE FOREST", "这里，藏着你的坚持", "不必急着成为大树，先长出今天的新叶。") }
        item {
            Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Text("第 $level 季 · ${listOf("初见新芽", "枝叶舒展", "绿意渐浓", "自成一片森林")[(level-1).coerceAtMost(3)]}", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
                    TreeArt(leaves, Modifier.fillMaxWidth().height(270.dp))
                    Text("$leaves 片新叶", style = MaterialTheme.typography.headlineMedium)
                    Text("每完成一个习惯，就长出一片新叶", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                    LinearProgressIndicator(progress = { (leaves % 30) / 30f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                    Text("距离下一季还有 ${30 - leaves % 30} 片叶子", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        item { Text("一路生长的印记", style = MaterialTheme.typography.titleLarge) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1 to "第一片叶", 7 to "渐入佳境", 30 to "枝繁叶茂").forEach { (goal, title) ->
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(21.dp), color = if (leaves >= goal) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(horizontal = 6.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(if (leaves >= goal) "✿" else "○", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary); Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("$goal 片叶子", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item { Text("偶尔停一停，树也不会枯萎。\n你的每一次努力，都被好好收藏。", modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}
@Composable fun TrendsScreen(data: GardenData, today: LocalDate, select: (LocalDate) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val start = month.atDay(1); val end = minOf(month.atEndOfMonth(), today)
    val rate = data.rate(start, end)
    val earliest = data.habits.minOfOrNull { LocalDate.parse(it.created) } ?: today
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("SMALL STEPS, REAL CHANGE", "看见每一点改变", "把目光放长一点，你已经走了很远。") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("${(rate * 100).roundToInt()}%", "本月完成率", Modifier.weight(1f))
            Metric("${data.totalLeaves()}", "累计完成次数", Modifier.weight(1f))
        } }
        item { Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthText = month.minusMonths(1).toString() }, enabled = month > YearMonth.from(earliest)) { Icon(Icons.Default.KeyboardArrowLeft, "上个月") }
                    Text("${month.year}年 ${month.monthValue}月", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(today)) { Icon(Icons.Default.KeyboardArrowRight, "下个月") }
                }
                Row { listOf("一","二","三","四","五","六","日").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                val offset = start.dayOfWeek.value - 1
                val cells = ((month.lengthOfMonth() + offset + 6) / 7) * 7
                (0 until cells step 7).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        (row until row + 7).forEach { cell ->
                            val day = cell - offset + 1
                            if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(44.dp))
                            else {
                                val date = month.atDay(day); val count = data.completed(date); val total = data.due(date).size
                                val all = total > 0 && count == total
                                Surface(onClick = { select(date) }, enabled = date in earliest..today, shape = RoundedCornerShape(13.dp), color = if (all) Forest else if (count > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background, modifier = Modifier.weight(1f).height(44.dp).semantics { contentDescription = "${date} 完成${count}项 共${total}项" }) {
                                    Box(contentAlignment = Alignment.Center) { Text(day.toString(), color = if (all) Color.White else if (date > today) MaterialTheme.colorScheme.onSurfaceVariant.copy(.4f) else MaterialTheme.colorScheme.onSurface, fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                }
                Text("点击日期查看或补记   ·   深绿代表全部完成", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        item { Text("最近 7 天", style = MaterialTheme.typography.titleLarge) }
        item { Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                (6 downTo 0).forEach { n ->
                    val date = today.minusDays(n.toLong()); val total = data.due(date).size; val count = data.completed(date)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (total == 0) "—" else "${count}/${total}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.padding(vertical = 8.dp).height(85.dp).width(22.dp).background(MaterialTheme.colorScheme.background, CircleShape), contentAlignment = Alignment.BottomCenter) { Box(Modifier.fillMaxWidth().fillMaxHeight(if (total == 0) 0f else count.toFloat()/total).background(if (n == 0) Clay else Forest, CircleShape)) }
                        Text(if (n == 0) "今天" else "${date.dayOfMonth}", fontSize = 11.sp)
                    }
                }
            }
        } }
        item { Text("习惯足迹", style = MaterialTheme.typography.titleLarge) }
        if (data.habits.isEmpty()) item { Text("种下第一个习惯后，这里会记录你的变化。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(data.habits, key = { it.id }) { habit ->
            Surface(shape = RoundedCornerShape(20.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(habit.emoji, fontSize = 24.sp); Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(habit.title, style = MaterialTheme.typography.titleMedium); Text(if (habit.archived != null) "已归档 · 历史保留" else "连续完成 ${data.streak(habit, today)} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("${data.entries.count { it.habit == habit.id && it.amount >= habit.plan(LocalDate.parse(it.date)).target }} 次", fontWeight = FontWeight.SemiBold)
            } }
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}
@Composable fun Metric(value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(20.dp)) { Text(value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary); Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) } }
}
