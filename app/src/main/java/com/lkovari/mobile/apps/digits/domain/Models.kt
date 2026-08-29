package com.lkovari.mobile.apps.digits.domain

data class Operand(
    val id: Int,
    val value: Int,
    val selected: Boolean = false,
    val disabled: Boolean = false
)

data class StagePuzzle(
    val stageIndex: Int,
    val target: Int,
    val operands: List<Operand>
)

data class GameOperation(
    val left: Int,
    val right: Int,
    val operator: Operator,
    val result: Int
) {
    fun formatEquation(): String {
        return "$left ${operator.symbol} $right = $result"
    }
}

data class StageLevel(
    val index: Int,
    val target: Int,
    val completed: Boolean = false,
    val selected: Boolean = false,
    val summary: String = ""
)

data class PuzzleDay(
    val dayEpochMillis: Long,
    val locale: String,
    val stages: List<StagePuzzle>
)

data class DailyProgress(
    val dayEpochMillis: Long,
    val stageIndex: Int,
    val completed: Boolean,
    val stageLevels: List<StageLevel>,
    val stages: List<StagePuzzle>
)
