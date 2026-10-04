package io.github.antonovichkorp.chemmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.antonovichkorp.chemmod.core.CommonSubstance;
import io.github.antonovichkorp.chemmod.core.CommonSubstances;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.ValidationIssue;
import io.github.antonovichkorp.chemmod.core.properties.PredictedProperties;
import io.github.antonovichkorp.chemmod.core.reaction.BalancedReaction;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEquationParser;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;
import java.util.stream.Collectors;

@Mod(ChemMod.MOD_ID)
public final class ChemMod {
    public static final String MOD_ID = "chemmod";

    public ChemMod(IEventBus modEventBus) {
        ChemComponents.register(modEventBus);
        ChemItems.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("chem")
                .then(Commands.literal("help").executes(context -> showHelp(context.getSource())))
                .then(
                    Commands.literal("lookup")
                        .then(
                            Commands.argument("molecule", StringArgumentType.string())
                                .executes(context -> lookup(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "molecule")
                                ))
                        )
                )
                .then(
                    Commands.literal("balance")
                        .then(
                            Commands.argument("equation", StringArgumentType.greedyString())
                                .executes(context -> balance(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "equation")
                                ))
                        )
                )
                .then(
                    Commands.literal("give")
                        .requires(source -> source.hasPermission(2))
                        .then(
                            Commands.argument("substance", StringArgumentType.string())
                                .executes(context -> giveVial(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "substance")
                                ))
                        )
                )
                .then(
                    Commands.literal("test")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> runSelfTest(context.getSource()))
                )
        );
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.chemmod.help.header").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("command.chemmod.help.lookup"), false);
        source.sendSuccess(() -> Component.translatable("command.chemmod.help.balance"), false);
        source.sendSuccess(() -> Component.translatable("command.chemmod.help.give"), false);
        source.sendSuccess(() -> Component.translatable("command.chemmod.help.test"), false);
        return 1;
    }

    private static int lookup(CommandSourceStack source, String input) {
        try {
            CommonSubstance known = CommonSubstances.INSTANCE.find(input);
            Molecule molecule = Molecule.Companion.fromInput(input);
            if (!molecule.validate().isEmpty()) {
                String issues = molecule.validate().stream()
                    .map(ValidationIssue::getMessage)
                    .collect(Collectors.joining("; "));
                source.sendFailure(Component.translatable("command.chemmod.lookup.invalid", issues));
                return 0;
            }

            PredictedProperties properties = molecule.properties();
            String boilingPoint = properties.getBoilingPointC() == null
                ? Component.translatable("command.chemmod.value.unavailable").getString()
                : String.format(Locale.ROOT, "%.1f °C", properties.getBoilingPointC());
            String flags = properties.getFlags().isEmpty()
                ? Component.translatable("command.chemmod.value.none").getString()
                : String.join(", ", properties.getFlags());

            Component displayName = known == null
                ? Component.translatable("substance.chemmod.custom")
                : Component.translatable("substance.chemmod." + known.getCanonicalName());
            source.sendSuccess(
                () -> Component.translatable("command.chemmod.lookup.header", displayName, input)
                    .withStyle(ChatFormatting.AQUA),
                false
            );
            source.sendSuccess(
                () -> Component.translatable(
                    "command.chemmod.lookup.result",
                    molecule.formula(),
                    String.format(Locale.ROOT, "%.3f", molecule.molarMass()),
                    boilingPoint
                ),
                false
            );
            source.sendSuccess(
                () -> Component.translatable(
                    "command.chemmod.lookup.identity",
                    Long.toUnsignedString(molecule.canonicalId()),
                    flags
                ),
                false
            );
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.translatable("command.chemmod.lookup.failure", safeMessage(exception)));
            return 0;
        }
    }

    private static int balance(CommandSourceStack source, String equationInput) {
        try {
            BalancedReaction reaction = ReactionEquationParser.INSTANCE.balance(equationInput);
            String formatted = ReactionEquationParser.INSTANCE.format(reaction);
            source.sendSuccess(
                () -> Component.translatable("command.chemmod.balance.success", formatted)
                    .withStyle(ChatFormatting.GREEN),
                false
            );
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.translatable("command.chemmod.balance.failure", safeMessage(exception)));
            return 0;
        }
    }

    private static int giveVial(CommandSourceStack source, String input) {
        try {
            String structure = CommonSubstances.INSTANCE.resolve(input);
            Molecule molecule = Molecule.Companion.fromSMILESlike(structure);
            if (!molecule.validate().isEmpty()) {
                throw new IllegalArgumentException(molecule.validate().getFirst().getMessage());
            }
            var stack = ChemItems.vialFromStructure(structure, 1_000_000L, 1_000_000);
            var player = source.getPlayerOrException();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            source.sendSuccess(
                () -> Component.translatable("command.chemmod.give.success", stack.getHoverName()),
                false
            );
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.translatable("command.chemmod.give.failure", safeMessage(exception)));
            return 0;
        }
    }

    private static int runSelfTest(CommandSourceStack source) {
        try {
            Molecule ethanol = Molecule.Companion.fromSMILESlike("CCO");
            if (!ethanol.validate().isEmpty()) {
                throw new IllegalStateException(ethanol.validate().getFirst().getMessage());
            }
            if (!"C2H6O".equals(ethanol.formula())) {
                throw new IllegalStateException("ethanol formula mismatch: " + ethanol.formula());
            }

            BalancedReaction combustion = ReactionEquationParser.INSTANCE.balance(
                "CCO + O=O -> O=C=O + O"
            );
            if (!combustion.isConserved()) {
                throw new IllegalStateException("combustion is not conserved");
            }

            String mass = String.format(Locale.ROOT, "%.3f", ethanol.molarMass());
            String boilingPoint = String.format(
                Locale.ROOT,
                "%.1f",
                ethanol.properties().getBoilingPointC()
            );
            String equation = ReactionEquationParser.INSTANCE.format(combustion);
            source.sendSuccess(
                () -> Component.translatable(
                    "command.chemmod.test.success",
                    ethanol.formula(),
                    mass,
                    boilingPoint,
                    equation
                ),
                false
            );
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.translatable("command.chemmod.test.failure", safeMessage(exception)));
            return 0;
        }
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
