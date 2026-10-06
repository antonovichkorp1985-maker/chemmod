package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.model.ElementTable
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
import io.github.antonovichkorp.chemmod.core.parse.SmilesLikeParser
import io.github.antonovichkorp.chemmod.core.properties.PredictedProperties
import io.github.antonovichkorp.chemmod.core.properties.PropertyPredictor
import io.github.antonovichkorp.chemmod.core.properties.StructuralProperties
import io.github.antonovichkorp.chemmod.core.properties.StructuralPropertyAnalyzer

class Molecule private constructor(val graph: MoleculeGraph) {
    fun canonicalId(): Long = MoleculeCanonicalizer.canonicalId(graph)
    fun canonicalKey(): String = MoleculeCanonicalizer.canonicalKey(graph)
    fun validate(): List<ValidationIssue> = MoleculeValidator.validate(graph)
    fun formula(): String = FormulaCalculator.hillFormula(graph)
    fun molarMass(): Double = FormulaCalculator.molarMass(graph)
    fun properties(): PredictedProperties = PropertyPredictor.predict(graph)

    companion object {
        private val defaultParser by lazy { SmilesLikeParser(ElementTable.default()) }

        /** Parse the structural notation used by the chemistry engine. */
        fun fromSMILESlike(input: String): Molecule = Molecule(defaultParser.parse(input))

        /** Resolve a common name/formula alias first, then parse its structure. */
        fun fromInput(input: String): Molecule = fromSMILESlike(CommonSubstances.resolve(input))
    }
}
