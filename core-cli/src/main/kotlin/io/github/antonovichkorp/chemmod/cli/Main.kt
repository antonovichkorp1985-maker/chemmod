package io.github.antonovichkorp.chemmod.cli

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.parse.MoleculeParseException
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBalanceException
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEngine
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEquationParser
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId
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
        "react" -> react(args.drop(1))
        else -> fail("Unknown command '${args[0]}'. Run 'chem help'.")
    }
}

private fun lookup(input: String) {
    try {
        val molecule = Molecule.fromInput(input)
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

private fun react(args: List<String>) {
    if (args.size < 3) {
        fail("Usage: chem react <target> <rule-id> <temperature-K> [co-reactant ...] [--catalyst namespace:tag ...]")
    }
    try {
        val target = Molecule.fromInput(args[0])
        val ruleId = ReactionRuleId.of(args[1])
        val temperature = args[2].toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: fail("Temperature must be a positive number in kelvin")
        val coReactants = mutableListOf<Molecule>()
        val catalysts = mutableSetOf<String>()
        var index = 3
        while (index < args.size) {
            if (args[index] == "--catalyst") {
                val catalyst = args.getOrNull(++index) ?: fail("--catalyst needs a namespace:tag value")
                catalysts += catalyst
            } else {
                coReactants += Molecule.fromInput(args[index])
            }
            index++
        }
        val environment = ReactionEnvironment(
            temperatureKelvin = temperature,
            pressureKilopascals = 101.325,
            catalystTags = catalysts,
            availableCanonicalKeys = coReactants.map { molecule -> molecule.canonicalKey() }.toSet(),
        )
        val outcomes = ReactionEngine.bundled().apply(target, ruleId, environment)
        if (outcomes.isEmpty()) {
            fail("Rule $ruleId does not apply under the supplied environment")
        }
        outcomes.forEach { outcome ->
            println(outcome.formatEquation())
            println("Atoms and formal charge conserved: ${outcome.isConserved()}")
        }
    } catch (exception: MoleculeParseException) {
        fail(exception.message ?: "Could not parse a reaction molecule")
    } catch (exception: IllegalArgumentException) {
        fail(exception.message ?: "Could not apply reaction rule")
    }
}

private fun printHelp() {
    println(
        """
        ChemMod CLI — predictive chemistry core

        Usage:
          chem lookup <SMILES-like-or-name>  Parse a molecule and print identity and properties
          chem balance '<equation>'          Balance atoms and formal charge with integer coefficients
          chem react <target> <rule> <K> [co-reactant ...] [--catalyst tag ...]
          chem help                          Show this help

        M0 syntax supports B, C, N, O, F, P, S, Cl, Br and I; -, =, # bonds;
        branches, single-digit ring closures and simple charged bracket atoms.

        Examples:
          chem lookup CCO
          chem lookup этанол
          chem balance 'CCO + O=O -> O=C=O + O'
          chem react C=CC chemmod:alkene_hydrogenation 350 '[H][H]' --catalyst chemmod:palladium
          chem react CCO chemmod:complete_combustion 600 O=O
        """.trimIndent(),
    )
}

private fun fail(message: String): Nothing {
    System.err.println("Error: $message")
    exitProcess(1)
}
