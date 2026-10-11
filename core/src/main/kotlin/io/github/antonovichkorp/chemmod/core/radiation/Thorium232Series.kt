package io.github.antonovichkorp.chemmod.core.radiation

/**
 * ChemMod's first canonical decay family: the complete thorium-232 series.
 *
 * Half-lives are represented in seconds and branch fractions in [0, 1]. Q
 * values are rounded gameplay data in MeV: conservation is enforced by the
 * network itself, while a later radiation-field adapter can decide how much of
 * each release becomes alpha, beta, or gamma dose at a position.
 */
object Thorium232Series {
    private const val JULIAN_YEAR_SECONDS = 31_557_600.0
    private const val DAY_SECONDS = 86_400.0

    @JvmField val THORIUM_232 = Nuclide(90, 232, "Th")
    @JvmField val RADIUM_228 = Nuclide(88, 228, "Ra")
    @JvmField val ACTINIUM_228 = Nuclide(89, 228, "Ac")
    @JvmField val THORIUM_228 = Nuclide(90, 228, "Th")
    @JvmField val RADIUM_224 = Nuclide(88, 224, "Ra")
    @JvmField val RADON_220 = Nuclide(86, 220, "Rn")
    @JvmField val POLONIUM_216 = Nuclide(84, 216, "Po")
    @JvmField val LEAD_212 = Nuclide(82, 212, "Pb")
    @JvmField val BISMUTH_212 = Nuclide(83, 212, "Bi")
    @JvmField val POLONIUM_212 = Nuclide(84, 212, "Po")
    @JvmField val THALLIUM_208 = Nuclide(81, 208, "Tl")
    @JvmField val LEAD_208 = Nuclide(82, 208, "Pb")

    const val BISMUTH_TO_POLONIUM_FRACTION: Double = 0.6406
    const val BISMUTH_TO_THALLIUM_FRACTION: Double = 0.3594

    @JvmField
    val network: DecayNetwork = DecayNetwork.of(
        DecayScheme(
            THORIUM_232,
            14.05e9 * JULIAN_YEAR_SECONDS,
            listOf(DecayBranch(DecayMode.ALPHA, RADIUM_228, 1.0, 4.082)),
        ),
        DecayScheme(
            RADIUM_228,
            5.75 * JULIAN_YEAR_SECONDS,
            listOf(DecayBranch(DecayMode.BETA_MINUS, ACTINIUM_228, 1.0, 0.046)),
        ),
        DecayScheme(
            ACTINIUM_228,
            6.15 * 3_600.0,
            listOf(DecayBranch(DecayMode.BETA_MINUS, THORIUM_228, 1.0, 2.127)),
        ),
        DecayScheme(
            THORIUM_228,
            1.9116 * JULIAN_YEAR_SECONDS,
            listOf(DecayBranch(DecayMode.ALPHA, RADIUM_224, 1.0, 5.520)),
        ),
        DecayScheme(
            RADIUM_224,
            3.6319 * DAY_SECONDS,
            listOf(DecayBranch(DecayMode.ALPHA, RADON_220, 1.0, 5.789)),
        ),
        DecayScheme(
            RADON_220,
            55.6,
            listOf(DecayBranch(DecayMode.ALPHA, POLONIUM_216, 1.0, 6.405)),
        ),
        DecayScheme(
            POLONIUM_216,
            0.145,
            listOf(DecayBranch(DecayMode.ALPHA, LEAD_212, 1.0, 6.906)),
        ),
        DecayScheme(
            LEAD_212,
            10.64 * 3_600.0,
            listOf(DecayBranch(DecayMode.BETA_MINUS, BISMUTH_212, 1.0, 0.574)),
        ),
        DecayScheme(
            BISMUTH_212,
            60.55 * 60.0,
            listOf(
                DecayBranch(DecayMode.BETA_MINUS, POLONIUM_212, BISMUTH_TO_POLONIUM_FRACTION, 2.254),
                DecayBranch(DecayMode.ALPHA, THALLIUM_208, BISMUTH_TO_THALLIUM_FRACTION, 6.207),
            ),
        ),
        DecayScheme(
            POLONIUM_212,
            0.299e-6,
            listOf(DecayBranch(DecayMode.ALPHA, LEAD_208, 1.0, 8.954)),
        ),
        DecayScheme(
            THALLIUM_208,
            3.053 * 60.0,
            listOf(DecayBranch(DecayMode.BETA_MINUS, LEAD_208, 1.0, 5.001)),
        ),
        DecayScheme.stable(LEAD_208),
    )
}
