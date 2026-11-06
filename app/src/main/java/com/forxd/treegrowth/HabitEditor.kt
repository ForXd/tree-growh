package com.forxd.treegrowth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HabitEditor(habit: Habit?, save: (String, String, Int, String, Int, List<Int>) -> Unit, close: () -> Unit) {
    val plan = habit?.plan(LocalDate.now())
    var title by rememberSaveable { mutableStateOf(habit?.title ?: "") }
    var emoji by rememberSaveable { mutableStateOf(habit?.emoji ?: "🌱") }
    var color by rememberSaveable { mutableIntStateOf(habit?.color ?: 0) }
    var target by rememberSaveable { mutableStateOf((plan?.target ?: 1).toString()) }
    var unit by rememberSaveable { mutableStateOf(habit?.unit ?: "次") }
    var days by rememberSaveable { mutableStateOf(plan?.days ?: (1..7).toList()) }
    val valid = title.isNotBlank() && unit.isNotBlank() && target.toIntOrNull() in 1..9999 && days.isNotEmpty()
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (habit == null) "种下一个小习惯" else "照顾这个小习惯", style = MaterialTheme.typography.headlineMedium)
            Text("小一点的目标，也值得认真对待。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (habit == null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("喝水", "💧", "杯"), Triple("阅读", "📖", "页"), Triple("散步", "🌿", "分钟"), Triple("早睡", "🌙", "次")).forEach { (name, icon, u) ->
                    SuggestionChip(onClick = { title = name; emoji = icon; unit = u; target = when (name) { "喝水" -> "8"; "阅读" -> "10"; "散步" -> "20"; else -> "1" } }, label = { Text("$icon $name") })
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, label = { Text("习惯名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("🌱", "💧", "📖", "🏃", "🌙", "🧘", "✍️", "🎧").forEach { icon ->
                    Surface(onClick = { emoji = icon }, color = if (emoji == icon) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) { Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(target, { target = it.filter(Char::isDigit).take(4) }, modifier = Modifier.weight(1f), label = { Text("每天目标") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp))
                OutlinedTextField(unit, { unit = it.take(8) }, modifier = Modifier.weight(1f), label = { Text("单位，如次 / 页") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            }
            Text("在哪些日子坚持", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..7).forEach { day ->
                    Surface(onClick = { days = if (day in days) days - day else days + day }, color = if (day in days) Forest else MaterialTheme.colorScheme.surface, shape = CircleShape, modifier = Modifier.weight(1f).padding(2.dp)) {
                        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) { Text(listOf("一","二","三","四","五","六","日")[day-1], fontSize = 12.sp, color = if (day in days) Color.White else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
            Text("喜欢的颜色", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Accents.forEachIndexed { index, c ->
                Surface(onClick = { color = index }, color = c, shape = CircleShape, modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { if (color == index) Icon(Icons.Default.Check, "已选颜色", tint = Forest) } }
            } }
            if (habit != null) Text("目标与重复日从今天起生效，过去的记录保持原样。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { save(title, emoji, color, unit, target.toInt(), days) }, enabled = valid, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Text(if (habit == null) "开始生长" else "保存习惯") }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HabitDetail(habit: Habit, data: GardenData, date: LocalDate, model: GardenModel, edit: () -> Unit, close: () -> Unit) {
    val entry = data.entry(habit.id, date)
    var note by rememberSaveable(habit.id, date.toString()) { mutableStateOf(entry?.note.orEmpty()) }
    var archive by remember { mutableStateOf(false) }
    val amount = entry?.amount ?: 0; val target = habit.plan(date).target
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(habit.emoji, fontSize = 36.sp); Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(habit.title, style = MaterialTheme.typography.headlineMedium); Text("${date.monthValue}月${date.dayOfMonth}日 · 目标 $target ${habit.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant) }; IconButton(onClick = edit) { Icon(Icons.Default.Edit, "编辑习惯") } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { model.record(habit, date, amount - 1) }, enabled = amount > 0) { Text("−", fontSize = 26.sp) }
                Text("$amount", style = MaterialTheme.typography.headlineLarge)
                FilledTonalButton(onClick = { model.record(habit, date, amount + 1) }, enabled = amount < 99999) { Text("+", fontSize = 26.sp) }
            }
            OutlinedTextField(note, { note = it.take(500) }, label = { Text("给这一天留句话（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, shape = RoundedCornerShape(18.dp))
            Button(onClick = { model.record(habit, date, if (amount >= target) amount else target, note); close() }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (amount >= target) "保存记录" else "完成目标并保存") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { model.record(habit, date, amount, note); close() }) { Text("只保存备注") }
                TextButton(onClick = { archive = true }) { Text("归档习惯", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (archive) AlertDialog(onDismissRequest = { archive = false }, title = { Text("让这个习惯暂时休息？") }, text = { Text("归档后将不再出现在今日计划，过去的记录与成长值会保留。") }, confirmButton = { TextButton(onClick = { model.archive(habit); close() }) { Text("归档并保留记录") } }, dismissButton = { TextButton(onClick = { archive = false }) { Text("继续坚持") } })
}
