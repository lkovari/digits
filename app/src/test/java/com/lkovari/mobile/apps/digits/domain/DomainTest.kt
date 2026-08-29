package com.lkovari.mobile.apps.digits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ArithmeticEdgeCasesTest {
    @Test
    fun addMultiplyAlwaysWork() {
        assertEquals(9, Arithmetic.evaluate(4, 5, Operator.ADD))
        assertEquals(20, Arithmetic.evaluate(4, 5, Operator.MUL))
    }

    @Test
    fun subtractionAllowsEqualAndRejectsNegative() {
        assertEquals(0, Arithmetic.evaluate(5, 5, Operator.SUB))
        assertEquals(2, Arithmetic.evaluate(5, 3, Operator.SUB))
        assertEquals(Arithmetic.INVALID, Arithmetic.evaluate(3, 5, Operator.SUB))
    }

    @Test
    fun divisionRequiresExactNonZeroDivisor() {
        assertEquals(3, Arithmetic.evaluate(6, 2, Operator.DIV))
        assertEquals(Arithmetic.INVALID, Arithmetic.evaluate(5, 2, Operator.DIV))
        assertEquals(Arithmetic.INVALID, Arithmetic.evaluate(5, 0, Operator.DIV))
    }

    @Test
    fun undoOperatorIsInvalidInEvaluate() {
        assertEquals(Arithmetic.INVALID, Arithmetic.evaluate(1, 1, Operator.UNDO))
    }
}

class GameEngineEdgeCasesTest {
    private fun board(vararg values: Int): List<Operand> {
        return values.mapIndexed { index, value -> Operand(id = index, value = value) }
    }

    private fun apply(engine: GameEngine, operands: List<Operand>, a: Int, op: Operator, b: Int): EngineResult {
        var state = engine.selectOperand(a, operands).operands
        state = engine.selectOperator(op, state).operands
        return engine.selectOperand(b, state)
    }

    @Test
    fun requiresOperatorBetweenOperands() {
        val engine = GameEngine()
        var state = board(10, 5, 3)
        state = engine.selectOperand(0, state).operands
        val withoutOp = engine.selectOperand(1, state)
        assertNull(withoutOp.operands.firstOrNull { it.selected && it.id == 1 })
        assertEquals(EngineEvent.None, withoutOp.event)
    }

    @Test
    fun ignoresOperatorWhenNoOperandSelected() {
        val engine = GameEngine()
        val result = engine.selectOperator(Operator.ADD, board(1, 2))
        assertEquals(EngineEvent.None, result.event)
        assertTrue(result.operands.none { it.selected })
    }

    @Test
    fun ignoresClicksOnDisabledOperands() {
        val engine = GameEngine()
        val disabled = board(8, 2).map { if (it.id == 0) it.copy(disabled = true) else it }
        val result = engine.selectOperand(0, disabled)
        assertEquals(disabled, result.operands)
    }

    @Test
    fun invalidDivisionClearsSelectionAndDoesNotChangeBoard() {
        val engine = GameEngine()
        val start = board(5, 2, 9)
        val result = apply(engine, start, 0, Operator.DIV, 1)
        assertEquals(EngineEvent.InvalidOperation, result.event)
        assertEquals(5, result.operands[0].value)
        assertEquals(2, result.operands[1].value)
        assertTrue(result.operands.none { it.selected })
        assertTrue(engine.operationSteps().isEmpty())
    }

    @Test
    fun invalidSubtractionDoesNotPolluteHistory() {
        val engine = GameEngine()
        val start = board(2, 9)
        val result = apply(engine, start, 0, Operator.SUB, 1)
        assertEquals(EngineEvent.InvalidOperation, result.event)
        val undone = engine.selectOperator(Operator.UNDO, result.operands)
        assertEquals(EngineEvent.None, undone.event)
        assertEquals(2, undone.operands[0].value)
    }

    @Test
    fun successfulOpDisablesLeftAndWritesResultOnRight() {
        val engine = GameEngine()
        val result = apply(engine, board(10, 5, 3), 0, Operator.SUB, 1)
        assertEquals(EngineEvent.OperationApplied(5), result.event)
        assertTrue(result.operands[0].disabled)
        assertEquals(5, result.operands[1].value)
        assertEquals(1, engine.operationSteps().size)
    }

    @Test
    fun undoRestoresBoardWithoutAddingSolutionStep() {
        val engine = GameEngine()
        var state = board(8, 2)
        state = apply(engine, state, 0, Operator.DIV, 1).operands
        assertEquals(1, engine.operationSteps().size)
        val undone = engine.selectOperator(Operator.UNDO, state)
        assertEquals(EngineEvent.Undone, undone.event)
        assertEquals(8, undone.operands[0].value)
        assertEquals(2, undone.operands[1].value)
        assertFalse(undone.operands[0].disabled)
        assertEquals(1, engine.operationSteps().size)
    }

    @Test
    fun clearAllHistoryWipesBoardAndSolutionStacks() {
        val engine = GameEngine()
        apply(engine, board(6, 3), 0, Operator.DIV, 1)
        engine.clearAllHistory()
        assertTrue(engine.operationSteps().isEmpty())
        val undo = engine.selectOperator(Operator.UNDO, board(2, 3))
        assertEquals(EngineEvent.None, undo.event)
    }

    @Test
    fun deselectingSelectedOperandClearsIt() {
        val engine = GameEngine()
        var state = board(4, 5)
        state = engine.selectOperand(0, state).operands
        assertTrue(state[0].selected)
        state = engine.selectOperand(0, state).operands
        assertFalse(state[0].selected)
    }

    @Test
    fun unknownOperandIdIsNoOp() {
        val engine = GameEngine()
        val start = board(1, 2)
        val result = engine.selectOperand(99, start)
        assertEquals(start, result.operands)
    }

    @Test
    fun equationFormattingUsesOperatorSymbols() {
        val op = GameOperation(10, 5, Operator.SUB, 5)
        assertEquals("10 - 5 = 5", op.formatEquation())
    }
}

class PuzzleGeneratorEdgeCasesTest {
    @Test
    fun alwaysReturnsFiveStagesWithSixOperandsSortedByTarget() {
        repeat(8) { seed ->
            val stages = PuzzleGenerator(Random(seed)).generateStages()
            assertEquals(5, stages.size)
            assertEquals((0..4).toList(), stages.map { it.stageIndex })
            stages.forEach { stage ->
                assertEquals(6, stage.operands.size)
                assertEquals(stage.operands.map { it.id }, (0..5).toList())
                assertTrue(stage.operands.map { it.value }.distinct().size == 6)
                assertTrue(stage.target > 0)
            }
            val targets = stages.map { it.target }
            assertEquals(targets.sorted(), targets)
        }
    }

    @Test
    fun differentSeedsUsuallyDiffer() {
        val a = PuzzleGenerator(Random(11)).generateStages().flatMap { listOf(it.target) + it.operands.map { o -> o.value } }
        val b = PuzzleGenerator(Random(99)).generateStages().flatMap { listOf(it.target) + it.operands.map { o -> o.value } }
        assertNotEquals(a, b)
    }
}
