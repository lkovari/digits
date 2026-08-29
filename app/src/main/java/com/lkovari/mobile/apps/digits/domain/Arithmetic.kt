package com.lkovari.mobile.apps.digits.domain

object Arithmetic {
    const val INVALID = Int.MIN_VALUE

    fun evaluate(a: Int, b: Int, operator: Operator): Int {
        return when (operator) {
            Operator.ADD -> a + b
            Operator.SUB -> if (a >= b) a - b else INVALID
            Operator.MUL -> a * b
            Operator.DIV -> if (b != 0 && a % b == 0) a / b else INVALID
            Operator.UNDO -> INVALID
        }
    }
}
