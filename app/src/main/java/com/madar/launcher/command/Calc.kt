package com.madar.launcher.command

import java.math.BigDecimal
import java.math.MathContext

/** Tiny recursive-descent evaluator for palette arithmetic: + - * / % ^ and parentheses. */
object Calc {

    private val LOOKS_LIKE_MATH = Regex("^[0-9.\\s+\\-*/x×÷()%^]+$")
    private val HAS_OPERATOR = Regex("[0-9)]\\s*[+\\-*/x×÷%^]\\s*[0-9(]")

    fun tryEvaluate(input: String): String? {
        val expr = input.trim()
        if (!LOOKS_LIKE_MATH.matches(expr) || !HAS_OPERATOR.containsMatchIn(expr)) return null
        return runCatching {
            val parser = Parser(expr.replace('×', '*').replace('x', '*').replace('÷', '/').replace(" ", ""))
            val value = parser.expression()
            if (parser.pos != parser.s.length || value.isNaN() || value.isInfinite()) return null
            BigDecimal(value).round(MathContext(12)).stripTrailingZeros().toPlainString()
        }.getOrNull()
    }

    private class Parser(val s: String) {
        var pos = 0

        fun expression(): Double {
            var v = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                v = if (s[pos++] == '+') v + term() else v - term()
            }
            return v
        }

        private fun term(): Double {
            var v = power()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/' || s[pos] == '%')) {
                v = when (s[pos++]) {
                    '*' -> v * power()
                    '/' -> v / power()
                    else -> v % power()
                }
            }
            return v
        }

        private fun power(): Double {
            val base = unary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                return Math.pow(base, power())
            }
            return base
        }

        private fun unary(): Double = if (pos < s.length && s[pos] == '-') { pos++; -unary() } else atom()

        private fun atom(): Double {
            if (s[pos] == '(') {
                pos++
                val v = expression()
                require(s[pos++] == ')')
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            return s.substring(start, pos).toDouble()
        }
    }
}
