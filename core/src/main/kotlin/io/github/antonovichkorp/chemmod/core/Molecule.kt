package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.model.ElementTable
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
import io.github.antonovichkorp.chemmod.core.parse.SmilesLikeParser
import io.github.antonovichkorp.chemmod.core.properties.PredictedProperties
import io.github.antonovichkorp.chemmod.core.properties.PropertyPredictor

class Molecule private constructor(val graph: MoleculeGraph) {
    fun canonicalId(): Long = MoleculeCanonicalizer.canonicalId(graph)
    fun canonicalKey(): String = MoleculeCanonicalizer.canonicalKey(graph)
    fun validate(): List<ValidationIssue> = MoleculeValidator.validate(graph)
    fun formula(): String = FormulaCalculator.hillFormula(graph)
    fun molarMass(): Double = FormulaCalculator.molarMass(graph)
    /** Pure, content-coefficient property prediction for this graph. */
    fun properties(): PredictedProperties = PropertyPredictor.predict(graph)

    companion object {
        private val defaultParser by lazy { SmilesLikeParser(ElementTable.default()) }

        /** Parse the structural notation used by the chemistry engine. */
        fun fromSMILESlike(input: String): Molecule = Molecule(defaultParser.parse(input))

        /**
         * Wrap a graph built by a structure-preserving engine operation. The caller
         * must still use [validate] before treating the graph as a viable molecule.
         */
        fun fromGraph(graph: MoleculeGraph): Molecule = Molecule(graph)

        /** Resolve a common name/formula alias first, then parse its structure. */
        fun fromInput(input: String): Molecule = fromSMILESlike(CommonSubstances.resolve(input))
    }
}
