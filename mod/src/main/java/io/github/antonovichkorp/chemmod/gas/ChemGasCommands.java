package io.github.antonovichkorp.chemmod.gas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.core.CommonSubstance;
import io.github.antonovichkorp.chemmod.core.CommonSubstances;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.gas.GasMolecularAmount;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Operator-only finite-catalog tools for exercising and inspecting the world gas field. */
@EventBusSubscriber(modid = ChemMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ChemGasCommands {
    private static final double MAX_RELEASE_MOLES = 1_000_000.0;
    private static final SimpleCommandExceptionType UNKNOWN_SUBSTANCE =
        new SimpleCommandExceptionType(Component.translatable("command.chemmod.gas.unknown_substance"));
    private static final SimpleCommandExceptionType INVALID_GRAPH =
        new SimpleCommandExceptionType(Component.translatable("command.chemmod.gas.invalid_graph"));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chem")
            .then(Commands.literal("gas")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("release")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("substance", StringArgumentType.word())
                            .then(Commands.argument("moles", DoubleArgumentType.doubleArg(1.0e-9, MAX_RELEASE_MOLES))
                                .executes(context -> release(
                                    context.getSource(),
                                    EntityArgument.getPlayer(context, "player"),
                                    StringArgumentType.getString(context, "substance"),
                                    DoubleArgumentType.getDouble(context, "moles")
                                ))))))
                .then(Commands.literal("inspect")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> inspect(
                            context.getSource(), EntityArgument.getPlayer(context, "player")
                        ))))));
    }

    private static int release(CommandSourceStack source, ServerPlayer target, String name, double moles)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommonSubstance sample = CommonSubstances.INSTANCE.find(name);
        if (sample == null) throw UNKNOWN_SUBSTANCE.create();

        Molecule molecule = Molecule.Companion.fromSMILESlike(sample.getStructure());
        if (!molecule.validate().isEmpty()) throw INVALID_GRAPH.create();

        WorldGasField gas = target.serverLevel().getData(ChemAttachments.GAS_FIELD.get());
        var cell = gas.cellOf(target.blockPosition());
        var origin = WorldGasField.representativeBlock(cell);
        gas.releaseMolecules(target.blockPosition(), Map.of(molecule, moles));
        source.sendSuccess(() -> Component.translatable(
            "command.chemmod.gas.release.success", sample.getCanonicalName(), molecule.formula(), moles,
            target.getDisplayName(), origin.getX(), origin.getY(), origin.getZ()), true);
        return 1;
    }

    private static int inspect(CommandSourceStack source, ServerPlayer target) {
        WorldGasField gas = target.serverLevel().getData(ChemAttachments.GAS_FIELD.get());
        var cell = gas.cellOf(target.blockPosition());
        var origin = WorldGasField.representativeBlock(cell);
        List<GasMolecularAmount> contents = gas.molecularContentsIn(cell);
        if (contents.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.chemmod.gas.inspect.empty", target.getDisplayName(),
                origin.getX(), origin.getY(), origin.getZ()), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.chemmod.gas.inspect.header", target.getDisplayName(),
            origin.getX(), origin.getY(), origin.getZ()), false);
        Map<String, Double> composition = gas.atmosphereCompositionAt(target.blockPosition());
        double oxygenPercent = composition.getOrDefault("O2", 0.0) * 100.0;
        String formattedOxygenPercent = String.format(Locale.ROOT, "%.2f", oxygenPercent);
        source.sendSuccess(() -> Component.translatable(
            "command.chemmod.gas.inspect.oxygen", formattedOxygenPercent), false);
        for (GasMolecularAmount amount : contents) {
            String formula = amount.getMolecule().formula();
            double fraction = composition.getOrDefault(formula, 0.0);
            String volumePercent = String.format(Locale.ROOT, "%.3f", fraction * 100.0);
            String partsPerMillion = String.format(Locale.ROOT, "%.1f", fraction * 1_000_000.0);
            source.sendSuccess(() -> Component.translatable(
                "command.chemmod.gas.inspect.entry",
                formula, amount.getMolecule().structuralWitness(), amount.getMoles(),
                volumePercent, partsPerMillion), false);
        }
        return contents.size();
    }

    private ChemGasCommands() {
    }
}
