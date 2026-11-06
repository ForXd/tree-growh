package com.forxd.treegrowth

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class ChallengeCell(val index: Int, val at: String = Instant.now().toString(), val note: String = "")
data class Challenge(val id: String = UUID.randomUUID().toString(), val title: String, val emoji: String = "🏃", val created: String = LocalDate.now().toString(), val cells: List<ChallengeCell> = emptyList(), val archived: Boolean = false) {
    fun nextIndex(): Int? = (0..99).firstOrNull { i -> cells.none { it.index == i } }
    fun setCell(index: Int, done: Boolean, note: String = ""): Challenge {
        require(index in 0..99)
        val previous = cells.find { it.index == index }
        val remaining = cells.filterNot { it.index == index }
        return copy(cells = if (done) (remaining + ChallengeCell(index, previous?.at ?: Instant.now().toString(), note.take(500))).sortedBy { it.index } else remaining)
    }
}
