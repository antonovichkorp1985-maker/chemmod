package io.github.antonovichkorp.chemmod.core.reaction

import java.math.BigInteger

internal data class Rational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) {
    operator fun plus(other: Rational): Rational = of(
        numerator * other.denominator + other.numerator * denominator,
        denominator * other.denominator,
    )

    operator fun minus(other: Rational): Rational = plus(-other)
    operator fun times(other: Rational): Rational = of(numerator * other.numerator, denominator * other.denominator)
    operator fun div(other: Rational): Rational {
        require(!other.isZero) { "Division by zero" }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    operator fun unaryMinus(): Rational = Rational(-numerator, denominator)
    val isZero: Boolean get() = numerator == BigInteger.ZERO

    companion object {
        val ZERO = Rational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rational(BigInteger.ONE, BigInteger.ONE)

        fun of(value: BigInteger): Rational = Rational(value, BigInteger.ONE)

        private fun of(numerator: BigInteger, denominator: BigInteger): Rational {
            require(denominator != BigInteger.ZERO) { "Denominator cannot be zero" }
            if (numerator == BigInteger.ZERO) return ZERO
            val sign = if (denominator.signum() < 0) BigInteger.valueOf(-1) else BigInteger.ONE
            val gcd = numerator.gcd(denominator)
            return Rational(numerator / gcd * sign, denominator / gcd * sign)
        }
    }
}
