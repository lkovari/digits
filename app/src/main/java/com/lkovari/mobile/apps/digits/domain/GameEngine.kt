package com.lkovari.mobile.apps.digits.domain

class GameEngine {
    private val boardHistory = ArrayDeque<List<Operand>>()
    private val operationHistory = ArrayDeque<GameOperation>()

    private var selectedA: Int? = null
    private var selectedB: Int? = null
    private var selectedOperator: Operator? = null

    fun clearSelectionState() {
        selectedA = null
        selectedB = null
        selectedOperator = null
    }

    fun clearAllHistory() {
        boardHistory.clear()
        operationHistory.clear()
        clearSelectionState()
    }

    fun operationSteps(): List<GameOperation> {
        return operationHistory.toList()
    }

    fun selectedOperator(): Operator? {
        return selectedOperator
    }

    fun selectOperator(operator: Operator, operands: List<Operand>): EngineResult {
        if (operator == Operator.UNDO) {
            return undo(operands)
        }
        if (selectedA == null) {
            return EngineResult(operands = clearSelections(operands), event = EngineEvent.None)
        }
        selectedOperator = if (selectedOperator == operator) null else operator
        val updated = operands.map { op ->
            op.copy(selected = op.id == selectedA || op.id == selectedB)
        }
        return tryApply(updated)
    }

    fun selectOperand(operandId: Int, operands: List<Operand>): EngineResult {
        val tapped = operands.firstOrNull { it.id == operandId } ?: return EngineResult(operands)
        if (tapped.disabled) {
            return EngineResult(operands)
        }

        var nextA = selectedA
        var nextB = selectedB

        when {
            nextA == operandId -> {
                nextA = null
            }
            nextB == operandId -> {
                nextB = null
            }
            nextA == null -> {
                nextA = operandId
            }
            nextA != operandId && nextB == null -> {
                nextB = operandId
            }
        }

        selectedA = nextA
        selectedB = nextB

        var updated = operands.map { op ->
            op.copy(selected = op.id == selectedA || op.id == selectedB)
        }

        if (selectedA != null && selectedB != null && selectedOperator == null) {
            selectedB = null
            updated = updated.map { op ->
                op.copy(selected = op.id == selectedA)
            }
        }

        return tryApply(updated)
    }

    private fun tryApply(operands: List<Operand>): EngineResult {
        val aId = selectedA
        val bId = selectedB
        val op = selectedOperator
        if (aId == null || bId == null || op == null) {
            return EngineResult(
                operands = operands,
                selectedOperator = selectedOperator
            )
        }

        val a = operands.first { it.id == aId }
        val b = operands.first { it.id == bId }
        val snapshot = operands.map { it.copy() }
        boardHistory.addLast(snapshot)

        val result = Arithmetic.evaluate(a.value, b.value, op)
        if (result == Arithmetic.INVALID) {
            boardHistory.removeLast()
            clearSelectionState()
            return EngineResult(
                operands = clearSelections(operands),
                event = EngineEvent.InvalidOperation
            )
        }

        operationHistory.addLast(GameOperation(a.value, b.value, op, result))
        val updated = operands.map { operand ->
            when (operand.id) {
                aId -> operand.copy(disabled = true, selected = false)
                bId -> operand.copy(value = result, selected = false)
                else -> operand.copy(selected = false)
            }
        }
        clearSelectionState()
        return EngineResult(
            operands = updated,
            event = EngineEvent.OperationApplied(result),
            operationSteps = operationHistory.toList()
        )
    }

    private fun undo(operands: List<Operand>): EngineResult {
        if (boardHistory.isEmpty()) {
            clearSelectionState()
            return EngineResult(
                operands = clearSelections(operands),
                event = EngineEvent.None
            )
        }
        val previous = boardHistory.removeLast()
        clearSelectionState()
        return EngineResult(
            operands = previous.map { it.copy(selected = false) },
            event = EngineEvent.Undone
        )
    }

    private fun clearSelections(operands: List<Operand>): List<Operand> {
        return operands.map { it.copy(selected = false) }
    }
}

sealed class EngineEvent {
    data object None : EngineEvent()
    data object InvalidOperation : EngineEvent()
    data object Undone : EngineEvent()
    data class OperationApplied(val result: Int) : EngineEvent()
}

data class EngineResult(
    val operands: List<Operand>,
    val selectedOperator: Operator? = null,
    val event: EngineEvent = EngineEvent.None,
    val operationSteps: List<GameOperation> = emptyList()
)
