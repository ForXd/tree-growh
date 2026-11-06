package com.forxd.treegrowth

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.*
import java.io.File
import java.time.LocalDate

class GardenDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun store() = GardenStore(rule.activity.application)
    private fun waitSaved(predicate: (GardenData) -> Boolean) = rule.waitUntil(10000) { runCatching { predicate(store().read()) }.getOrDefault(false) }
    private fun seed(data: GardenData) {
        lateinit var model: GardenModel
        rule.runOnIdle { model = androidx.lifecycle.ViewModelProvider(rule.activity)[GardenModel::class.java] }
        rule.waitUntil(10000) { model.loaded.value }
        kotlinx.coroutines.runBlocking { model.restore(data) }
        rule.onNodeWithText("今天").performClick()
        rule.waitForIdle()
    }
    @Test fun customHundredChallengeCreateCheckUndoAndReopen() {
        seed(GardenData(preferences = Preferences(autoUpdate = false)))
        rule.onNodeWithText("百次", useUnmergedTree = false).performClick()
        rule.onNodeWithText("开启我的 100 次").performClick()
        rule.onNodeWithText("挑战名称").performTextInput("跳绳和英语")
        rule.onNodeWithText("开启百次挑战").performClick()
        waitSaved { it.challenges.singleOrNull()?.title == "跳绳和英语" }
        rule.onNodeWithTag("challenge-list").performScrollToNode(hasText("完成一次 · 点亮第 1 格"))
        rule.onNodeWithText("完成一次 · 点亮第 1 格").performClick()
        waitSaved { it.challenges.single().cells.size == 1 }
        rule.onNodeWithText("完成一次 · 点亮第 2 格").performClick()
        waitSaved { it.challenges.single().cells.size == 2 }
        rule.onNodeWithContentDescription("第43格，未完成，查看记录").performScrollTo().performClick()
        rule.onNodeWithText("这次做了什么？（可选）").performTextInput("跳绳500下")
        rule.onNodeWithText("为这一格打勾").performClick()
        waitSaved { it.challenges.single().cells.any { cell -> cell.index == 42 && cell.note == "跳绳500下" } }
        rule.activityRule.scenario.recreate(); rule.waitForIdle()
        // Saved navigation returns to the challenge and actual disk state remains intact.
        rule.onNodeWithText("百次").performClick()
        rule.onNodeWithContentDescription("第43格，已完成，查看记录").performScrollTo().performClick()
        rule.onNodeWithText("跳绳500下").assertExists()
        rule.onNodeWithText("撤销这一格的打卡").performClick()
        rule.onNodeWithText("确认撤销").performClick()
        waitSaved { it.challenges.single().cells.size == 2 && it.challenges.single().cells.none { cell -> cell.index == 42 } }
    }
    @Test fun dailyHabitCreateCompleteAndArchiveKeepsHistory() {
        seed(GardenData(preferences = Preferences(autoUpdate = false)))
        rule.onNodeWithContentDescription("添加习惯").performClick()
        rule.onNodeWithText("习惯名称").performTextInput("练琴")
        rule.onNodeWithText("开始生长").performScrollTo().performClick()
        waitSaved { it.habits.singleOrNull()?.title == "练琴" }
        rule.onNodeWithContentDescription("完成练琴").performScrollTo().performClick()
        waitSaved { it.totalLeaves() == 1 }
        rule.onNodeWithText("练琴", substring = false).performClick()
        rule.onNodeWithText("归档习惯").performClick()
        rule.onNodeWithText("归档并保留记录").performClick()
        waitSaved { it.habits.single().archived != null && it.totalLeaves() == 1 }
    }
    @Test fun hundredthCellCompletesAndUndoReopensTheGoal() {
        val challenge = (0..98).fold(Challenge(title = "运动100次")) { c, i -> c.setCell(i, true) }
        seed(GardenData(challenges = listOf(challenge), preferences = Preferences(autoUpdate = false)))
        rule.onNodeWithText("百次").performClick()
        rule.onNodeWithTag("challenge-list").performScrollToNode(hasText("完成一次 · 点亮第 100 格"))
        rule.onNodeWithText("完成一次 · 点亮第 100 格").performClick()
        waitSaved { it.challenges.single().cells.size == 100 }
        rule.onNodeWithText("✦  百次达成，值得为自己骄傲  ✦").assertExists()
        rule.onNodeWithContentDescription("第100格，已完成，查看记录").performScrollTo().performClick()
        rule.onNodeWithText("撤销这一格的打卡").performClick()
        rule.onNodeWithText("确认撤销").performClick()
        waitSaved { it.challenges.single().cells.size == 99 }
        rule.onNodeWithText("完成一次 · 点亮第 100 格").assertExists()
    }
    @Test fun backupAtomicRoundTripOnDevice() {
        val challenge = Challenge(title = "英语").setCell(67, true, "记住一句话")
        val data = GardenData(challenges = listOf(challenge), preferences = Preferences(reducedMotion = true, autoUpdate = false))
        seed(data)
        val json = BackupCodec.encode(store().read())
        store().write(GardenData())
        store().write(BackupCodec.decode(json))
        Assert.assertEquals(data, store().read())
        try { BackupCodec.decode("broken") } catch (_: IllegalArgumentException) { }
        Assert.assertEquals(data, store().read())
    }
    @Test fun captureScreensForVisualReview() {
        if (InstrumentationRegistry.getArguments().getString("screenshots") != "true") return
        val today = LocalDate.now(); val created = today.minusDays(20)
        val habits = listOf(Habit(title = "喝够八杯水", emoji = "💧", color = 2, unit = "杯", created = created.toString(), plans = listOf(Plan(created.toString(), 8))), Habit(title = "读几页喜欢的书", emoji = "📖", color = 1, unit = "页", created = created.toString(), plans = listOf(Plan(created.toString(), 10))), Habit(title = "出门走走", emoji = "🌿", color = 0, unit = "分钟", created = created.toString(), plans = listOf(Plan(created.toString(), 20))))
        val entries = (1..20).flatMap { n -> habits.filterIndexed { i, _ -> (n+i)%4 != 0 }.map { Entry(it.id, today.minusDays(n.toLong()).toString(), it.plan(today).target) } } + listOf(Entry(habits[0].id, today.toString(), 8), Entry(habits[1].id, today.toString(), 4))
        val c = (0..36).fold(Challenge(title = "英语100次", emoji = "🎧")) { acc,i -> acc.setCell(i,true) }
        seed(GardenData(habits = habits, entries = entries, challenges = listOf(c, Challenge(title = "跳绳100次")), preferences = Preferences(autoUpdate = false)))
        screenshot("today")
        listOf("百次" to "hundred", "花园" to "garden", "趋势" to "trends", "我的" to "settings").forEach { (tab, name) -> rule.onNodeWithText(tab).performClick(); rule.waitForIdle(); screenshot(name) }
        rule.onNodeWithText("深色").performClick(); waitSaved { it.preferences.theme == "dark" }
        rule.onNodeWithText("百次").performClick(); rule.waitForIdle(); screenshot("hundred-dark")
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(rule.activity.getExternalFilesDir(null), "$name.png"))
    }
}
