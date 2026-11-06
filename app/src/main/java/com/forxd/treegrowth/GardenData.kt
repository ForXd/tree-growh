package com.forxd.treegrowth

import android.app.Application
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Plans are dated so editing a goal never rewrites yesterday's denominator. */
data class Plan(val since: String, val target: Int = 1, val days: List<Int> = (1..7).toList())
data class Habit(
    val id: String = UUID.randomUUID().toString(), val title: String, val emoji: String = "🌱",
    val color: Int = 0, val unit: String = "次", val created: String = LocalDate.now().toString(),
    val archived: String? = null, val plans: List<Plan> = listOf(Plan(LocalDate.now().toString()))
) {
    fun plan(date: LocalDate): Plan = plans.lastOrNull { it.since <= date.toString() } ?: plans.first()
    fun due(date: LocalDate): Boolean = date.toString() >= created && (archived == null || date.toString() < archived) && date.dayOfWeek.value in plan(date).days
}
data class Entry(val habit: String, val date: String, val amount: Int = 0, val note: String = "")
data class Preferences(val theme: String = "system", val reducedMotion: Boolean = false, val reminders: Boolean = false, val hour: Int = 20, val minute: Int = 30, val autoUpdate: Boolean = true)
data class GardenData(val schema: Int = 1, val habits: List<Habit> = emptyList(), val entries: List<Entry> = emptyList(), val preferences: Preferences = Preferences(), val challenges: List<Challenge> = emptyList()) {
    fun entry(id: String, date: LocalDate) = entries.firstOrNull { it.habit == id && it.date == date.toString() }
    fun complete(habit: Habit, date: LocalDate) = (entry(habit.id, date)?.amount ?: 0) >= habit.plan(date).target
    fun due(date: LocalDate) = habits.filter { it.due(date) }
    fun completed(date: LocalDate) = due(date).count { complete(it, date) }
    fun totalLeaves(): Int = challenges.sumOf { it.cells.size } + entries.count { entry -> habits.find { it.id == entry.habit }?.let { entry.amount >= it.plan(LocalDate.parse(entry.date)).target } == true }
    fun streak(habit: Habit, today: LocalDate): Int {
        var day = today; var count = 0
        if (habit.due(day) && !complete(habit, day)) day = day.minusDays(1)
        while (day.toString() >= habit.created) {
            if (habit.due(day)) { if (!complete(habit, day)) break; count++ }
            day = day.minusDays(1)
        }
        return count
    }
    fun rate(start: LocalDate, end: LocalDate): Float {
        var planned = 0; var done = 0; var day = start
        while (day <= end) { planned += due(day).size; done += completed(day); day = day.plusDays(1) }
        return if (planned == 0) 0f else done.toFloat() / planned
    }
}

object BackupCodec {
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    const val MAX_BYTES = 4 * 1024 * 1024
    fun encode(data: GardenData): String = gson.toJson(data)
    fun decode(text: String): GardenData {
        require(text.toByteArray().size <= MAX_BYTES) { "备份超过 4 MB" }
        val data = try {
            val root = com.google.gson.JsonParser.parseString(text).asJsonObject
            require(root.has("schema") && root.has("habits") && root.has("entries") && root.has("preferences") && root.has("challenges"))
            gson.fromJson(root, GardenData::class.java)
        } catch (_: Exception) { throw IllegalArgumentException("不是有效的青芽备份") }
        try {
            require(data.schema == 1) { "不支持此备份版本" }
            require(data.habits.size <= 200 && data.entries.size <= 40000 && data.challenges.size <= 100)
            require(data.challenges.map { it.id }.distinct().size == data.challenges.size)
            data.challenges.forEach { c ->
                require(c.id.isNotBlank() && c.id.length <= 100 && c.title.isNotBlank() && c.title.length <= 40 && c.emoji.length in 1..12)
                require(c.cells.size <= 100 && c.cells.map { it.index }.distinct().size == c.cells.size)
                LocalDate.parse(c.created)
                c.cells.forEach { cell -> require(cell.index in 0..99 && cell.note.length <= 500); java.time.Instant.parse(cell.at) }
            }
            require(data.habits.map { it.id }.distinct().size == data.habits.size)
            val today = LocalDate.now()
            data.habits.forEach { h ->
                require(h.id.isNotBlank() && h.id.length <= 100 && h.title.isNotBlank() && h.title.length <= 40)
                require(h.emoji.length in 1..12 && h.unit.length in 1..8 && h.color in 0..4)
                require(LocalDate.parse(h.created) in LocalDate.of(2000, 1, 1)..today)
                h.archived?.let { require(LocalDate.parse(it) in LocalDate.parse(h.created)..today) }
                require(h.plans.isNotEmpty() && h.plans.size <= 1000 && h.plans.first().since == h.created)
                require(h.plans.map { it.since } == h.plans.map { it.since }.distinct().sorted())
                h.plans.forEach { p ->
                    require(LocalDate.parse(p.since) in LocalDate.parse(h.created)..today)
                    require(p.target in 1..9999 && p.days.isNotEmpty() && p.days.distinct().size == p.days.size && p.days.all { it in 1..7 })
                }
            }
            require(data.entries.map { it.habit to it.date }.distinct().size == data.entries.size)
            data.entries.forEach { e ->
                val habit = data.habits.single { it.id == e.habit }
                require(LocalDate.parse(e.date) in LocalDate.parse(habit.created)..today)
                require(e.amount in 0..99999 && e.note.length <= 500)
            }
            require(data.preferences.theme in listOf("system", "light", "dark"))
            require(data.preferences.hour in 0..23 && data.preferences.minute in 0..59)
        } catch (e: Exception) { throw IllegalArgumentException(e.message?.takeIf { it == "不支持此备份版本" } ?: "备份内容不完整或数据超出范围") }
        return data
    }
}

class GardenStore(app: Application) {
    companion object { private val fileLock = Any() }
    private val file = AtomicFile(File(app.filesDir, "garden.json"))
    fun read(): GardenData = synchronized(fileLock) { if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) GardenData() else BackupCodec.decode(file.openRead().bufferedReader().use { it.readText() }) }
    fun write(data: GardenData) = synchronized(fileLock) {
        val out = file.startWrite()
        try { out.write(BackupCodec.encode(data).toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
}
class GardenApplication : Application()
class GardenModel(app: Application) : AndroidViewModel(app) {
    private val store = GardenStore(app)
    private val lock = Mutex()
    private val _data = MutableStateFlow(GardenData())
    val data = _data.asStateFlow()
    val loaded = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val updater = AppUpdater(app, viewModelScope)
    init { viewModelScope.launch {
        try { _data.value = withContext(Dispatchers.IO) { store.read() }; loaded.value = true; Reminders.schedule(app, _data.value.preferences) }
        catch (_: Exception) { error.value = "本地数据无法读取，已暂停写入。请从设置恢复有效备份。" }
    } }
    private fun change(transform: (GardenData) -> GardenData) {
        if (!loaded.value) return
        viewModelScope.launch { lock.withLock {
            try {
                error.value = null
                val next = transform(_data.value)
                withContext(Dispatchers.IO) { store.write(next) }
                _data.value = next
                Reminders.schedule(getApplication(), next.preferences)
            } catch (_: Exception) { error.value = "保存失败，请检查可用存储空间后重试" }
        } }
    }
    fun saveHabit(old: Habit?, title: String, emoji: String, color: Int, unit: String, target: Int, days: List<Int>) {
        require(title.trim().isNotEmpty() && title.length <= 40 && unit.trim().isNotEmpty() && unit.length <= 8 && target in 1..9999 && days.isNotEmpty())
        change { data ->
            val today = LocalDate.now().toString()
            val plan = Plan(today, target, days.sorted())
            val habit = old?.copy(title = title.trim(), emoji = emoji, color = color, unit = unit.trim(), plans = old.plans.filter { it.since != today } + plan)
                ?: Habit(title = title.trim(), emoji = emoji, color = color, unit = unit.trim(), plans = listOf(plan))
            data.copy(habits = if (old == null) data.habits + habit else data.habits.map { if (it.id == old.id) habit else it })
        }
    }
    fun record(habit: Habit, date: LocalDate, amount: Int, note: String? = null) = change { data ->
        require(date <= LocalDate.now() && habit.due(date))
        val old = data.entry(habit.id, date)
        val entry = Entry(habit.id, date.toString(), amount.coerceIn(0, 99999), (note ?: old?.note.orEmpty()).take(500))
        data.copy(entries = data.entries.filterNot { it.habit == habit.id && it.date == date.toString() } + entry)
    }
    fun archive(habit: Habit) = change { data -> data.copy(habits = data.habits.map { if (it.id == habit.id) it.copy(archived = LocalDate.now().toString()) else it }) }
    fun saveChallenge(old: Challenge?, title: String, emoji: String) = change { data ->
        require(title.trim().isNotEmpty() && title.length <= 40)
        val challenge = old?.copy(title = title.trim(), emoji = emoji) ?: Challenge(title = title.trim(), emoji = emoji)
        data.copy(challenges = if (old == null) data.challenges + challenge else data.challenges.map { if (it.id == old.id) challenge else it })
    }
    fun challengeCell(id: String, index: Int, done: Boolean, note: String = "") = change { data ->
        data.copy(challenges = data.challenges.map { if (it.id == id) it.setCell(index, done, note) else it })
    }
    fun archiveChallenge(id: String) = change { data -> data.copy(challenges = data.challenges.map { if (it.id == id) it.copy(archived = !it.archived) else it }) }
    fun preferences(p: Preferences) = change { it.copy(preferences = p) }
    suspend fun restore(data: GardenData) = lock.withLock {
        withContext(Dispatchers.IO) { store.write(data) }
        _data.value = data; loaded.value = true; error.value = null
        Reminders.schedule(getApplication(), data.preferences)
    }
}
