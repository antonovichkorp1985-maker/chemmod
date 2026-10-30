package io.github.antonovichkorp.chemmod.core.geometry

import kotlin.math.abs
import kotlin.math.acosh
import kotlin.math.cosh
import kotlin.math.max
import kotlin.math.sinh

/**
 * Catenary curve mathematics for physical cables.
 *
 * The shape of a hanging uniform cable under gravity is a hyperbolic cosine:
 * y(x) = a * cosh(x/a) + C, where a = H / (w) is the catenary parameter,
 * H = horizontal tension, w = weight per unit length.
 */
object CatenaryMath {

    /** Result of a catenary calculation. */
    data class CatenaryResult(
        /** Horizontal tension (N) that achieves the given span and sag. */
        val horizontalTensionNewtons: Double,
        /** Catenary parameter a = H/w (metres). */
        val parameterA: Double,
        /** Maximum sag at midpoint (metres). */
        val maxSagMetres: Double,
        /** Cable length along the curve (metres). */
        val curveLengthMetres: Double,
    )

    /**
     * Solve for the catenary parameter given span, cable length, and weight per unit length.
     *
     * Given: span S (horizontal distance between supports), total cable length L,
     * weight per unit length w (N/m). Find parameter a such that:
     * L = 2 * a * sinh(S / (2a))
     *
     * Uses Newton-Raphson iteration. Returns null if no solution exists
     * (cable too short for span, or iteration fails to converge).
     */
    fun solveParameter(
        spanMetres: Double,
        cableLengthMetres: Double,
        weightPerMetreNewtons: Double,
    ): CatenaryResult? {
        if (spanMetres <= 0.0) return null
        if (cableLengthMetres < spanMetres) return null
        if (weightPerMetreNewtons <= 0.0) return null

        // Initial guess: a ≈ L²/(8s) for small sag approximation
        // Better initial guess from L = 2a sinh(S/2a)
        val minA = spanMetres / 2.0 // lower bound where sinh approaches infinity
        var a = cableLengthMetres / 2.0 // upper bound for very taut cable

        // Newton-Raphson on f(a) = 2a*sinh(S/(2a)) - L = 0
        for (i in 0 until 50) {
            val halfSpanOverA = spanMetres / (2.0 * a)
            val sinhTerm = sinh(halfSpanOverA)
            val f = 2.0 * a * sinhTerm - cableLengthMetres
            if (abs(f) < 1e-9) break

            val coshTerm = cosh(halfSpanOverA)
            val dfda = 2.0 * sinhTerm - (spanMetres / a) * coshTerm
            val newA = a - f / dfda

            if (newA <= minA) {
                a = minA * 1.001
            } else if (newA > a * 10.0) {
                // Diverging, try different approach
                a = a * 1.5
            } else {
                a = newA
            }
        }

        // Verify solution
        val halfSpanOverA = spanMetres / (2.0 * a)
        val computedLength = 2.0 * a * sinh(halfSpanOverA)
        if (abs(computedLength - cableLengthMetres) > 1e-3) return null

        val horizontalTension = a * weightPerMetreNewtons
        val maxSag = a * (cosh(halfSpanOverA) - 1.0)

        return CatenaryResult(
            horizontalTensionNewtons = horizontalTension,
            parameterA = a,
            maxSagMetres = maxSag,
            curveLengthMetres = computedLength,
        )
    }

    /**
     * Compute y-coordinate of catenary at horizontal position x from left support.
     * Origin at left support: y(0) = 0, y(span) = 0.
     * Returns height above the support level (negative = below supports).
     */
    fun heightAt(xMetres: Double, spanMetres: Double, result: CatenaryResult): Double {
        val xShifted = xMetres - spanMetres / 2.0
        return result.parameterA * (cosh(xShifted / result.parameterA) - cosh(spanMetres / (2.0 * result.parameterA)))
    }

    /**
     * Tension at a point along the cable: T = H * cosh(x/a).
     * Maximum at supports, minimum at midpoint.
     */
    fun tensionAt(xMetres: Double, spanMetres: Double, result: CatenaryResult): Double {
        val xShifted = xMetres - spanMetres / 2.0
        return result.horizontalTensionNewtons * cosh(xShifted / result.parameterA)
    }

    const val GRAVITY_M_S2 = 9.80665
}