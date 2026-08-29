package com.lkovari.mobile.apps.digits.domain

import kotlin.random.Random

class PuzzleGenerator(
    private val random: Random = Random.Default
) {
    private data class Difficulty(
        val ranges: List<IntRange>,
        val minTarget: Int,
        val maxTarget: Int
    )

    private val difficulties = listOf(
        Difficulty(
            ranges = listOf(1..5, 1..5, 1..5, 1..5, 5..10, 10..25),
            minTarget = 30,
            maxTarget = 100
        ),
        Difficulty(
            ranges = listOf(1..5, 1..5, 1..5, 5..10, 10..15, 10..25),
            minTarget = 50,
            maxTarget = 150
        ),
        Difficulty(
            ranges = listOf(1..5, 1..5, 5..10, 5..15, 10..20, 10..25),
            minTarget = 100,
            maxTarget = 200
        ),
        Difficulty(
            ranges = listOf(1..5, 5..10, 5..15, 10..15, 10..20, 10..25),
            minTarget = 300,
            maxTarget = 400
        ),
        Difficulty(
            ranges = listOf(1..10, 5..10, 5..15, 10..15, 10..25, 10..25),
            minTarget = 400,
            maxTarget = 550
        )
    )

    fun generateStages(): List<StagePuzzle> {
        val generated = difficulties.mapIndexed { index, difficulty ->
            generateStage(index, difficulty)
        }
        return generated
            .sortedBy { it.target }
            .mapIndexed { index, stage ->
                stage.copy(
                    stageIndex = index,
                    operands = stage.operands.mapIndexed { opIndex, operand ->
                        operand.copy(id = opIndex)
                    }
                )
            }
    }

    private fun generateStage(stageIndex: Int, difficulty: Difficulty): StagePuzzle {
        var attempts = 0
        while (attempts < 200) {
            attempts += 1
            val numbers = generateUniqueNumbers(difficulty.ranges) ?: continue
            val target = calculateTarget(numbers.toMutableList(), difficulty) ?: continue
            val operands = numbers.mapIndexed { id, value ->
                Operand(id = id, value = value)
            }
            return StagePuzzle(
                stageIndex = stageIndex,
                target = target,
                operands = operands
            )
        }
        val fallback = listOf(1, 2, 3, 4, 5, 10)
        return StagePuzzle(
            stageIndex = stageIndex,
            target = difficulty.minTarget,
            operands = fallback.mapIndexed { id, value -> Operand(id, value) }
        )
    }

    private fun generateUniqueNumbers(ranges: List<IntRange>): List<Int>? {
        val used = linkedSetOf<Int>()
        for (range in ranges) {
            var found: Int? = null
            repeat(64) {
                val candidate = random.nextInt(range.first, range.last + 1)
                if (candidate !in used) {
                    found = candidate
                    return@repeat
                }
            }
            val value = found ?: return null
            used.add(value)
        }
        return used.toList().sorted()
    }

    private fun calculateTarget(operands: MutableList<Int>, difficulty: Difficulty): Int? {
        for (attempt in 0 until 500) {
            val working = operands.toMutableList()
            val cycles = random.nextInt(3, 6)
            var keeper = random.nextInt(working.size)
            var completed = 0
            var guard = 0
            while (completed < cycles && working.size > 1 && guard < 200) {
                guard += 1
                var other = random.nextInt(working.size)
                if (working.size > 1) {
                    while (other == keeper) {
                        other = random.nextInt(working.size)
                    }
                }
                val op = chooseOperator(working[keeper], working[other]) ?: continue
                val result = Arithmetic.evaluate(working[keeper], working[other], op)
                if (result == Arithmetic.INVALID) {
                    continue
                }
                working[keeper] = result
                working.removeAt(other)
                if (other < keeper) {
                    keeper -= 1
                }
                completed += 1
            }
            if (working.isEmpty()) {
                continue
            }
            val target = working[keeper.coerceIn(0, working.lastIndex)]
            if (target in difficulty.minTarget..difficulty.maxTarget) {
                return target
            }
        }
        return null
    }

    private fun chooseOperator(a: Int, b: Int): Operator? {
        val candidates = mutableListOf(Operator.ADD, Operator.MUL)
        if (a >= b) {
            candidates.add(Operator.SUB)
        }
        if (b != 0 && a % b == 0) {
            candidates.add(Operator.DIV)
        }
        if (candidates.isEmpty()) {
            return null
        }
        return candidates[random.nextInt(candidates.size)]
    }
}
