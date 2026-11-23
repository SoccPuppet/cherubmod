package org.lullaby.cherubmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.StringTextComponent;
import org.lullaby.cherubmod.capability.Archangel;
import org.lullaby.cherubmod.capability.Race;
import org.lullaby.cherubmod.capability.RaceCapability;
import org.lullaby.cherubmod.network.CherubNetwork;

public class SwitchedCommand {
    public static final DamageSource ASCENSION_DAMAGE = new DamageSource("cherubification") {
        {
            bypassArmor();
            bypassMagic();
            bypassInvul();
        }
    };

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
                Commands.literal("switched")
                        .then(Commands.literal("on").executes(SwitchedCommand::executeOn))
                        .then(Commands.literal("off").executes(SwitchedCommand::executeOff))
        );
    }

    private static int executeOn(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();

        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            data.setPendingRaceChange(true);
            CherubNetwork.syncToPlayer(player);
        });

        player.hurt(ASCENSION_DAMAGE, Float.MAX_VALUE);

        player.getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                context.getSource().sendSuccess(
                        new StringTextComponent(
                                "Current race: " + data.getRace().name() +
                                        " (Pending change on respawn: " + data.isPendingRaceChange() + ")"
                        ),
                        false
                )
        );

        return 1;
    }

    private static int executeOff(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrException();

        player.getCapability(RaceCapability.INSTANCE).ifPresent(data -> {
            data.setRace(Race.NONE);
            data.setArchangel(Archangel.NONE);
            data.setPendingRaceChange(false);
            CherubNetwork.syncToPlayer(player);
        });

        player.getCapability(RaceCapability.INSTANCE).ifPresent(data ->
                context.getSource().sendSuccess(
                        new StringTextComponent("Current race: " + data.getRace().name()),
                        false
                )
        );

        return 1;
    }
}
