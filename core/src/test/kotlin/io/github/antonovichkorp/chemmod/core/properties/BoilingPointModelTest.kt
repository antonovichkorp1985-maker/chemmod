package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.CommonSubstances
import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.chemistry.FunctionalGroups
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The boiling point has to come from the graph: dispersion over molar mass, plus ring and
 * aromaticity corrections, plus the hydrogen-bond network for alcohols and polyols, plus a branch
 * per compound class for amines, carbonyls and ethers. Every fit covers a stated domain, and
 * outside it the model refuses instead of extrapolating.
 * Anything that is still a substance row must say so through `boilingPointBasis`.
 */
class BoilingPointModelTest {
    private val engine = PropertyEngine(PropertyRuleSet.bundled())

    private fun referenceMolecule(structure: String): Molecule {
        assertNotNull(
            CommonSubstances.findByStructure(structure),
            "every molecule used to validate the property model must have a finite creative reference sample: $structure",
        )
        return Molecule.fromSMILESlike(structure)
    }

    private fun boiling(structure: String): Double? =
        engine.predict(referenceMolecule(structure).graph).boilingPointC

    private fun basis(structure: String): String =
        engine.predict(referenceMolecule(structure).graph).boilingPointBasis

    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1.0) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, got $actual")

    @Test
    fun `water is still a calibration anchor and says so`() {
        assertNear(100.0, boiling("O")!!, 0.1)
        assertEquals("calibration-anchor:H2O", basis("O"))
    }

    @Test
    fun `the anchor list is not allowed to grow`() {
        val anchors = PropertyRuleSet.bundled().boiling.exactFormulaCelsius
        assertEquals(setOf("H2O"), anchors.keys, "a new row would be a substance record, not a model")
    }

    @Test
    fun `alkanes follow dispersion instead of a carbon-count polynomial`() {
        assertNear(-91.0, boiling("CC")!!)
        assertNear(-29.9, boiling("C1CC1")!!)
        assertNear(63.6, boiling("CCCCCC")!!)
        assertEquals("dispersion+ring+aromatic", basis("CCCCCC"))
    }

    @Test
    fun `rings and aromatic rings carry their own corrections`() {
        assertNear(81.7, boiling("C1CCCCC1")!!)
        assertNear(76.0, boiling("C1=CC=CC=C1")!!)
        assertNear(109.4, boiling("CC1=CC=CC=C1")!!)
    }

    @Test
    fun `alcohols rise with the chain and with their hydrogen-bond network`() {
        assertNear(64.3, boiling("CO")!!)
        assertNear(83.7, boiling("CCO")!!)
        assertNear(101.2, boiling("CCCO")!!)
        assertNear(117.3, boiling("CCCCO")!!)
        assertEquals("hydroxyl-family+hydrogen-bond-network", basis("CCO"))
    }

    @Test
    fun `a diol and a triol boil higher than a heavier alkane because of their sites`() {
        val glycol = boiling("OCCO")!!
        val hexane = boiling("CCCCCC")!!
        assertNear(181.6, glycol)
        assertTrue(glycol > hexane, "ethylene glycol (62 g/mol) must outboil hexane (86 g/mol)")
        // Glycerol was not part of the fit: three donor and three acceptor sites, 63 kJ/mol network.
        assertNear(292.9, boiling("OCC(O)CO")!!, 3.0)
    }

    @Test
    fun `families outside the fitted domain are refused, not extrapolated`() {
        // Measured this way against experiment the old mass-only branch gave dimethyl ether
        // -41.0 instead of -24.8, acetone -7.3 instead of 56.1, acetonitrile -55.9 instead of
        // 81.6 and tetrachloromethane 208.1 instead of 76.7. Refusing is the smaller lie.
        // Ethers, ketones, amines, monohalides, nitriles, esters and monocarboxylic acids have
        // their own branches now; what is still refused is any polyhalide, because the monohalide
        // fit put dichlorodifluoromethane +131 K and tetrachloromethane +106 K out.
        assertNull(boiling("C(Cl)(Cl)(Cl)Cl"))
        assertNull(boiling("ClCCl"))
        assertEquals("no-model:halide-fit-covers-monohalides-only", basis("C(Cl)(Cl)(Cl)Cl"))
        // Hydrocarbons stay inside the fit and keep their numbers.
        assertEquals("dispersion+ring+aromatic", basis("CCCCCC"))
    }

    @Test
    fun `acids boil by dispersion over the fitted monocarboxylic domain`() {
        // Seven sourced monocarboxylic acids. The intercept is high because the class dimerises:
        // two acids hold each other with two hydrogen bonds, so acetic acid boils 61 K above
        // methyl acetate at a lower molar mass.
        assertNear(100.8, boiling("OC=O")!!, 4.5)              // formic
        assertNear(118.1, boiling("CC(=O)O")!!, 4.5)           // acetic
        assertNear(141.2, boiling("CCC(=O)O")!!, 4.5)          // propionic
        assertNear(163.5, boiling("CCCC(=O)O")!!, 4.5)         // butyric
        assertNear(186.0, boiling("CCCCC(=O)O")!!, 4.5)        // valeric
        assertNear(205.0, boiling("CCCCCC(=O)O")!!, 4.5)       // hexanoic
        // The worst point: the aromaticity correction was fitted on hydrocarbons.
        assertNear(249.0, boiling("OC(=O)C1=CC=CC=C1")!!, 7.0) // benzoic
        assertEquals("acid+dispersion+branching", basis("CC(=O)O"))
    }

    @Test
    fun `a branched acid outside the fit still lands near measurement`() {
        // Isobutyric acid was not in the fit. It has one skeleton branch point, so it is predicted
        // by the branch coefficient that nitriles and esters constrain - an out-of-sample check on
        // that shared value from a class that never fitted it. Sourced at 152 C.
        assertNear(152.0, boiling("CC(C)C(=O)O")!!, 4.0)
    }

    @Test
    fun `a dicarboxylic acid is refused rather than extrapolated`() {
        // Oxalic acid donates two hydrogen bonds and its carboxyls interact with each other, which
        // is not the fitted dimer. Every member of the fit donates exactly one.
        assertNull(boiling("OC(=O)C(=O)O"))
        assertEquals("no-model:acid-fit-covers-monocarboxylic-only", basis("OC(=O)C(=O)O"))
    }

    @Test
    fun `esters boil by dispersion, corrected for rings and for a branched skeleton`() {
        // Eight sourced esters. An ester cannot donate a hydrogen bond, so unlike the alcohols it
        // needs no network term: polarity is absorbed by the intercept and the rest is dispersion.
        assertNear(32.0, boiling("COC=O")!!, 4.5)               // methyl formate
        assertNear(57.0, boiling("CC(=O)OC")!!, 4.5)            // methyl acetate
        assertNear(77.1, boiling("CC(=O)OCC")!!, 4.5)           // ethyl acetate
        assertNear(79.8, boiling("CCC(=O)OC")!!, 4.5)           // methyl propionate
        assertNear(89.0, boiling("CC(=O)OC(C)C")!!, 4.5)        // isopropyl acetate
        assertNear(101.6, boiling("CC(=O)OCCC")!!, 4.5)         // propyl acetate
        assertNear(126.1, boiling("CC(=O)OCCCC")!!, 4.5)        // butyl acetate
        // The worst point, and the reason it is worst is named: the aromaticity correction was
        // fitted on hydrocarbons, and an ester carbonyl conjugated with a ring is not quite that.
        assertNear(199.0, boiling("COC(=O)C1=CC=CC=C1")!!, 7.0) // methyl benzoate
        assertEquals("ester+dispersion+branching", basis("CC(=O)OCC"))
    }

    @Test
    fun `branching separates ester isomers and the position of the oxygen does not`() {
        // Propyl acetate and isopropyl acetate are both C5H10O2 and boil 12.6 K apart, because one
        // is branched. The branching term sees that.
        assertTrue(boiling("CC(=O)OCCC")!! > boiling("CC(=O)OC(C)C")!!)
        // Ethyl acetate and methyl propionate are both C4H8O2, both unbranched, and boil 2.8 K
        // apart - and this model calls them the same substance, because nothing in it knows where
        // the ester oxygen sits. Asserting the equality is the honest test: it records the hole
        // instead of hiding it behind a tolerance wide enough to pass either way.
        assertEquals(boiling("CC(=O)OCC"), boiling("CCC(=O)OC"))
    }

    @Test
    fun `nitriles boil by dispersion, corrected for rings and for a branched skeleton`() {
        // Five sourced nitriles, saturated or aryl. The cyano group is polar - acetonitrile carries
        // 3.93 D - and that is what the intercept absorbs; nothing here is told it is polar.
        // Tolerance widened from 3.0 to 3.5 when the branching coefficient stopped being fitted on
        // nitriles alone and was refitted jointly with the esters: one physical effect, one number.
        assertNear(81.7, boiling("CC#N")!!, 3.5)            // acetonitrile
        assertNear(97.0, boiling("CCC#N")!!, 3.5)           // propionitrile
        assertNear(117.9, boiling("CCCC#N")!!, 3.5)         // butyronitrile
        assertNear(103.9, boiling("CC(C)C#N")!!, 3.5)       // isobutyronitrile
        assertNear(190.0, boiling("N#CC1=CC=CC=C1")!!, 3.5) // benzonitrile
        assertEquals("nitrile+dispersion+branching", basis("CC#N"))
    }

    @Test
    fun `a branch lowers the boiling point, and only a real one counts`() {
        // Same formula, same mass, 14 K apart: n-butane boils at -0.5 and isobutane at -11.8, so
        // compactness matters and molar mass alone cannot see it.
        assertTrue(boiling("CCCC#N")!! > boiling("CC(C)C#N")!!)
        assertEquals(1, FunctionalGroups.branchPoints(referenceMolecule("CC(C)C#N").graph))
        assertEquals(0, FunctionalGroups.branchPoints(referenceMolecule("CCCC#N").graph))
        // An aromatic carbon carrying the cyano group has three heavy neighbours but is not a
        // branch: counting it would drop benzonitrile by 14 K.
        assertEquals(0, FunctionalGroups.branchPoints(referenceMolecule("N#CC1=CC=CC=C1").graph))
    }

    @Test
    fun `a conjugated nitrile is refused rather than mispredicted`() {
        // Acrylonitrile boils at 77 C at 53 g/mol; propionitrile at 97 C at 55. The C=C conjugated
        // with the C#N is worth some 20 K that dispersion has no term for, so the model declines.
        assertNull(boiling("C=CC#N"))
        assertEquals("no-model:nitrile-fit-excludes-conjugated-alkenenitriles", basis("C=CC#N"))
        assertTrue(FunctionalGroups.hasConjugatedNitrile(referenceMolecule("C=CC#N").graph))
        assertTrue(!FunctionalGroups.hasConjugatedNitrile(referenceMolecule("CCCC#N").graph))
        // Benzonitrile is conjugated with its ring, but the ring is a different electronic
        // situation and the branch predicts it to within 0.6 K. Counting an aromatic double bond
        // here would refuse a molecule the model actually gets right.
        assertTrue(!FunctionalGroups.hasConjugatedNitrile(referenceMolecule("N#CC1=CC=CC=C1").graph))
    }

    @Test
    fun `monohalides boil by dispersion plus a term for the halogen they carry`() {
        // Fit on twelve sourced monohalides with the shared ring and aromaticity corrections,
        // RMS 4.9 K, worst chlorobenzene at +8.7 K. Chlorine is the reference of the fit, so its
        // per-element coefficient is zero and the others are measured against it: replacing
        // chlorine by bromine lowers the term by 63.7 K, by iodine by 113.5 K, because the extra
        // mass of the halogen is already in the dispersion term.
        assertNear(-78.4, boiling("CF")!!, 10.0)
        assertNear(-24.2, boiling("CCl")!!, 10.0)
        assertNear(12.3, boiling("CCCl")!!, 10.0)
        assertNear(46.6, boiling("CCC(Cl)")!!, 10.0)
        assertNear(3.6, boiling("CBr")!!, 10.0)
        assertNear(38.4, boiling("CCBr")!!, 10.0)
        assertNear(71.0, boiling("CCCBr")!!, 10.0)
        assertNear(42.6, boiling("CI")!!, 10.0)
        assertNear(72.3, boiling("CCI")!!, 10.0)
        assertNear(102.4, boiling("CCCI")!!, 10.0)
        assertNear(131.7, boiling("ClC1=CC=CC=C1")!!, 10.0)
        assertNear(156.0, boiling("BrC1=CC=CC=C1")!!, 10.0)
        assertEquals("monohalide+dispersion", basis("CCCl"))
        // Three isomers of C3H7I are indistinguishable here, but the halogen series is not: the
        // same carbon skeleton boils in the order F < Cl < Br < I, which is what the term does.
        assertTrue(boiling("CCCl")!! < boiling("CCBr")!!)
        assertTrue(boiling("CCBr")!! < boiling("CCI")!!)
    }

    @Test
    fun `polarizibility comes from element data, so halogens count for what they are`() {
        // Additive free-atom sums give 10.33 A^3 for CCl4 against 10.002 A^3 measured for the
        // molecule (+3 %): for a halide the sum is a usable descriptor. For a hydride it is
        // not - CH4 sums to 4.34 A^3 against 2.66 A^3 measured, because hydrogen bound to
        // carbon is far less polarizable than a free hydrogen atom. So the sum is a fitted
        // descriptor whose branch carries its own intercept, never a measured molecular value.
        assertNear(10.33, engine.predict(referenceMolecule("C(Cl)(Cl)(Cl)Cl").graph)
            .polarizibilityCubicAngstroms!!, 0.01)
        // Methane counts its four implicit hydrogens: 1.674 + 4 * 0.667.
        assertNear(4.34, engine.predict(referenceMolecule("C").graph)
            .polarizibilityCubicAngstroms!!, 0.01)
        // Livermorium is the single element the source table leaves without a value, and a
        // gap must stay a gap instead of quietly turning into zero.
        assertNull(engine.predict(Molecule.fromSMILESlike("[Lv]").graph).polarizibilityCubicAngstroms)
    }

    @Test
    fun `amines boil by dispersion plus the hydrogens their nitrogen can donate`() {
        // Fit on ten amines, RMS 4.4 K, worst trimethylamine at -7.3 K.
        assertNear(-6.3, boiling("CN")!!, 8.0)
        assertNear(16.6, boiling("CCN")!!, 8.0)
        assertNear(78.0, boiling("CCCCN")!!, 8.0)
        assertNear(3.5, boiling("CN(C)C")!!, 8.0)
        assertNear(89.5, boiling("CCN(CC)CC")!!, 8.0)
        assertEquals("amine+hydrogen-bond-donors", basis("CN"))
    }

    @Test
    fun `three isomeric butylamines separate by donor count, not by mass`() {
        // Same formula C4H11N, same molar mass: a primary amine donates two hydrogens, a
        // secondary one, a tertiary none - and that is exactly the order they boil in.
        val primary = boiling("CCCCN")!!
        val secondary = boiling("CCNCC")!!
        val tertiary = boiling("CCN(C)C")!!
        assertTrue(primary > secondary, "primary must outboil secondary: $primary vs $secondary")
        assertTrue(secondary > tertiary, "secondary must outboil tertiary: $secondary vs $tertiary")
    }

    @Test
    fun `ketones and ethers get their own branches`() {
        assertNear(56.1, boiling("CC(C)=O")!!, 4.0)
        assertNear(79.6, boiling("CCC(C)=O")!!, 4.0)
        assertEquals("carbonyl+dispersion", basis("CC(C)=O"))
        assertNear(-24.8, boiling("COC")!!, 2.0)
        assertNear(34.6, boiling("CCOCC")!!, 2.0)
        assertEquals("ether+dispersion", basis("COC"))
        // Isomers again: the alcohol keeps its hydrogen-bond network, the ether does not.
        assertTrue(boiling("CCO")!! - boiling("COC")!! > 100.0)
    }

    @Test
    fun `carbon-free substances outside the anchor get no value at all`() {
        assertNull(boiling("N"))
        assertEquals("no-model", basis("N"))
    }

    @Test
    fun `a hydrogen bond can never lower the boiling point`() {
        assertTrue(PropertyRuleSet.bundled().boiling.hydrogenBondCelsiusPerKilojoulePerMole > 0.0)
        assertTrue(PropertyRuleSet.bundled().boiling.hydrocarbonDispersionCelsius > 0.0)
    }
}
