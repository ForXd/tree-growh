package com.forxd.treegrowth

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class DomainTest {
    private val day = LocalDate.of(2026, 10, 5)
    @Test fun hundredChallengeIsIndependentOfDailySchedule() {
        var c = Challenge(title = "跳绳", created = day.toString())
        repeat(100) { c = c.setCell(c.nextIndex()!!, true, "跳绳500下") }
        assertEquals(100, c.cells.size); assertNull(c.nextIndex())
        assertEquals((0..99).toList(), c.cells.map { it.index })
        assertEquals(100, GardenData(challenges = listOf(c)).totalLeaves())
    }
    @Test fun arbitraryCellUndoAndRefillPreserveOtherNotes() {
        val c = Challenge(title = "英语").setCell(42, true, "听力20分钟").setCell(99, true, "阅读")
        val twice = c.setCell(42, true, "新备注")
        assertEquals(2, twice.cells.size); assertEquals(c.cells[0].at, twice.cells[0].at)
        val undone = twice.setCell(42, false)
        assertEquals(1, undone.cells.size); assertEquals("阅读", undone.cells.single().note)
        assertEquals(0, undone.nextIndex()); assertEquals(1, GardenData(challenges = listOf(undone)).totalLeaves())
    }
    @Test fun rejectOutOfRangeCell() { for (i in listOf(-1, 100)) assertThrows(IllegalArgumentException::class.java) { Challenge(title = "运动").setCell(i, true) } }
    @Test fun targetChangesKeepHistoricalMeaning() {
        val h = Habit(title = "阅读", created = day.toString(), plans = listOf(Plan(day.toString(), 5), Plan(day.plusDays(1).toString(), 10)))
        val data = GardenData(habits = listOf(h), entries = listOf(Entry(h.id, day.toString(), 5), Entry(h.id, day.plusDays(1).toString(), 5)))
        assertTrue(data.complete(h, day)); assertFalse(data.complete(h, day.plusDays(1)))
        assertEquals(1, data.totalLeaves()); assertEquals(.5f, data.rate(day, day.plusDays(1)), .001f)
    }
    @Test fun restDaysDontBreakStreakOrCountAsFailures() {
        val h = Habit(title = "运动", created = day.toString(), plans = listOf(Plan(day.toString(), 1, listOf(1,3,5))))
        val data = GardenData(habits = listOf(h), entries = listOf(Entry(h.id, day.toString(), 1), Entry(h.id, day.plusDays(2).toString(), 1)))
        assertEquals(2, data.streak(h, day.plusDays(3)))
        assertEquals(1f, data.rate(day, day.plusDays(3)), .001f)
        assertEquals(2, data.streak(h, day.plusDays(4)))
        assertEquals(0, data.streak(h, day.plusDays(7)))
    }
    @Test fun archivePreservesGrowthAndPastDenominator() {
        val h = Habit(title = "阅读", created = day.toString(), archived = day.plusDays(2).toString(), plans = listOf(Plan(day.toString())))
        val data = GardenData(habits = listOf(h), entries = listOf(Entry(h.id, day.toString(), 1)))
        assertEquals(1, data.totalLeaves()); assertEquals(.5f, data.rate(day, day.plusDays(10)), .001f)
    }
    @Test fun backupRoundTripIncludesChallengesNotesAndPlans() {
        val h = Habit(title = "阅读", created = day.toString(), plans = listOf(Plan(day.toString())))
        val data = GardenData(habits = listOf(h), entries = listOf(Entry(h.id, day.toString(), 1, "不错")), challenges = listOf(Challenge(title = "英语100次").setCell(25, true, "shadowing")), preferences = Preferences(theme = "dark", reminders = true))
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data)))
    }
    @Test fun invalidBackupsAreRejected() {
        val good = GardenData(challenges = listOf(Challenge(title = "自定义").setCell(0, true)))
        listOf("{}", "null", "broken", BackupCodec.encode(good).replace("\"schema\": 1", "\"schema\": 99"), BackupCodec.encode(good).replace("\"index\": 0", "\"index\": 100"), BackupCodec.encode(good).replace("\"title\": \"自定义\"", "\"title\": null")).forEach { text -> assertThrows("Accepted $text", IllegalArgumentException::class.java) { BackupCodec.decode(text) } }
    }
    @Test fun duplicateCellsAreRejectedInBackup() {
        val cell = ChallengeCell(1)
        val data = GardenData(challenges = listOf(Challenge(title = "运动", cells = listOf(cell, cell))))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(data)) }
    }
}
