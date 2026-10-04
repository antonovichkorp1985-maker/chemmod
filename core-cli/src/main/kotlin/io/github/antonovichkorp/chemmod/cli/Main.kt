package io.github.antonovichkorp.chemmod.cli

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.parse.MoleculeParseException
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBalanceException
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEquationParser
import java.util.Locale
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Locale.setDefault(Locale.ROOT)
    if (args.isEmpty() || args[0] in setOf("help", "--help", "-h")) {
        printHelp()
        return
    }

    when (args[0]) {
        "lookup" -> {
            if (args.size != 2) fail("Usage: chem lookup <SMILES-like>")
            lookup(args[1])
        }
        "balance" -> {
            if (args.size < 2) fail("Usage: chem balance '<reactants> -> <products>'")
            balance(args.drop(1).joinToString(" "))
        }
        else -> fail("Unknown command '${args[0]}'. Run 'chem help'.")
    }
}

private fun lookup(input: String) {
    try {
        val molecule = Molecule.fromSMILESlike(input)
        val issues = molecule.validate()
        if (issues.isNotEmpty()) {
            System.err.println("Invalid molecule:")
            issues.forEach { System.err.println("  [${it.code}] ${it.message}") }
            exitProcess(2)
        }

        val properties = molecule.properties()
        println("Input:             $input")
        println("Formula:           ${molecule.formula()}")
        println("Molar mass:        ${"%.3f".format(properties.molarMass)} g/mol")
        println("Canonical ID:      ${molecule.canonicalId().toULong()}")
        println("Canonical key:     ${molecule.canonicalKey()}")
        println(
            "Boiling point:     " +
                (properties.boilingPointC?.let { "${"%.1f".format(it)} °C (predicted)" } ?: "not available"),
        )
        println("Property model:    ${properties.model}")
        println("Structural flags:  ${properties.flags.ifEmpty { setOf("none") }.joinToString()}")
    } catch (exception: MoleculeParseException) {
        fail(exception.message ?: "Could not parse molecule")
    }
}

private fun balance(equation: String) {
    try {
        val reaction = ReactionEquationParser.balance(equation)
        println(ReactionEquationParser.format(reaction))
        println("Atoms and formal charge conserved: ${reaction.isConserved()}")
    } catch (exception: ReactionBalanceException) {
        fail(exception.message ?: "Could not balance reaction")
    } catch (exception: MoleculeParseException) {
        fail(exception.message ?: "Could not parse a reaction species")
    }
}

private fun printHelp() {
    println(
        """
        ChemMod CLI — predictive chemistry core

        Usage:
          chem lookup <SMILES-like>   Parse a molecule and print its identity and properties
          chem balance '<equation>'   Balance atoms and formal charge with integer coefficients
          chem help                   Show this help

        M0 syntax supports B, C, N, O, F, P, S, Cl, Br and I; -, =, # bonds;
        branches, single-digit ring closures and simple charged bracket atoms.

        Examples:
          chem lookup CCO
          chem lookup COC
          chem lookup 'CC(=O)O'
          chem balance 'CCO + O=O -> O=C=O + O'
        """.trimIndent(),
    )
}

private fun fail(message: String): Nothing {
    System.err.println("Error: $message")
    exitProcess(1)
}
