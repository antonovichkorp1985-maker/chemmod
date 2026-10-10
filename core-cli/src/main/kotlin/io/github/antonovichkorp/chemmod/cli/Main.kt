package io.github.antonovichkorp.chemmod.cli

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.parse.MoleculeParseException
import io.github.antonovichkorp.chemmod.core.radiation.IsotopeData
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
        "isotopes" -> {
            if (args.size != 2) fail("Usage: chem isotopes <element symbol>")
            isotopes(args[1])
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
                (properties.boilingPointC?.let { "${"%.1f".format(it)} °C (${properties.boilingPointBasis})" }
                    ?: "not available (${properties.boilingPointBasis})"),
        )
        properties.polarizibilityCubicAngstroms?.let {
            println("Polarizibility:    ${"%.2f".format(it)} A^3 (sum of atomic values)")
        }
        println("Property model:    ${properties.model}")
        println("Structural flags:  ${properties.flags.ifEmpty { setOf("none") }.joinToString()}")
        println(
            "Hydrogen bonds:    ${properties.hydrogenBondDonors} donor / " +
                "${properties.hydrogenBondAcceptors} acceptor sites, network " +
                "${"%.1f".format(properties.hydrogenBondNetworkKilojoulesPerMole)} kJ/mol",
        )
        println(
            "Aromatic rings:    ${properties.aromaticRings}, resonance " +
                "${"%.1f".format(properties.aromaticResonanceKilojoulesPerMole)} kJ/mol",
        )
        println("Lewis-acid sites:  ${properties.coordinateBondAcceptors}")
        println(
            "Molecular shape:   " +
                (properties.molecularShape ?: "terminal atoms only") +
                (properties.bondAngleDegrees?.let { ", ${"%.1f".format(it)}°" } ?: ""),
        )
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
          chem isotopes <element symbol>     List known isotopes, half-lives and decay branches
          chem help                          Show this help

        M0 syntax supports B, C, N, O, F, P, S, Cl, Br and I; -, =, # bonds;
        branches, single-digit ring closures and simple charged bracket atoms.

        Examples:
          chem lookup CCO
          chem lookup этанол
          chem balance 'CCO + O=O -> O=C=O + O'
          chem react C=CC chemmod:alkene_hydrogenation 350 '[H][H]' --catalyst chemmod:palladium
          chem react CCO chemmod:complete_combustion 600 O=O
          chem isotopes K
        """.trimIndent(),
    )
}

private fun isotopes(symbol: String) {
    val data = IsotopeData.default()
    val found = data.of(symbol)
    if (found.isEmpty()) {
        val known = data.all().map { it.nuclide.symbol }.distinct().sorted().joinToString(", ")
        fail("No isotope data for '$symbol'. Data covers: $known")
    }
    println("Isotopes of $symbol:")
    found.forEach { isotope ->
        val halfLife = isotope.halfLifeSeconds
            ?.let { "${"%.6g".format(it)} s (${humanHalfLife(it)})" }
            ?: "stable"
        val branches = isotope.scheme.branches.joinToString(", ") { branch ->
            "${modeText(branch.mode)} -> ${branch.daughter.displayName} " +
                "(${"%.4g".format(branch.branchingFraction * 100)}%, ${"%.4g".format(branch.releasedEnergyMeV)} MeV)"
        }
        println("  ${isotope.nuclide.displayName.padEnd(7)} ${halfLife.padEnd(28)} $branches".trimEnd())
    }
}

private fun modeText(mode: io.github.antonovichkorp.chemmod.core.radiation.DecayMode): String = when (mode) {
    io.github.antonovichkorp.chemmod.core.radiation.DecayMode.ALPHA -> "alpha"
    io.github.antonovichkorp.chemmod.core.radiation.DecayMode.BETA_MINUS -> "beta-"
    io.github.antonovichkorp.chemmod.core.radiation.DecayMode.BETA_PLUS -> "beta+"
    io.github.antonovichkorp.chemmod.core.radiation.DecayMode.ELECTRON_CAPTURE -> "electron-capture"
}

private fun humanHalfLife(seconds: Double): String = when {
    seconds >= 31_557_600.0 -> "${"%.4g".format(seconds / 31_557_600.0)} years"
    seconds >= 86_400.0 -> "${"%.4g".format(seconds / 86_400.0)} days"
    seconds >= 3_600.0 -> "${"%.4g".format(seconds / 3_600.0)} hours"
    seconds >= 60.0 -> "${"%.4g".format(seconds / 60.0)} minutes"
    else -> "${"%.4g".format(seconds)} seconds"
}

private fun fail(message: String): Nothing {
    System.err.println("Error: $message")
    exitProcess(1)
}
