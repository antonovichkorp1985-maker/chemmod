package io.github.antonovichkorp.chemmod;

import com.mojang.brigadier.CommandDispatcher;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.reaction.BalancedReaction;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEquationParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

@Mod(ChemMod.MOD_ID)
public final class ChemMod {
    public static final String MOD_ID = "chemmod";

    public ChemMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("chem")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("test").executes(context -> runSelfTest(context.getSource())))
        );
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
            source.sendFailure(Component.translatable("command.chemmod.test.failure", exception.getMessage()));
            return 0;
        }
    }
}
