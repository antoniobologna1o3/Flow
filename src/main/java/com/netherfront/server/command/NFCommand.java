package com.netherfront.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.netherfront.common.feed.FeedEntry;
import com.netherfront.common.match.Frequency;
import com.netherfront.common.match.MatchMode;
import com.netherfront.common.match.MatchState;
import com.netherfront.common.match.MatchTeam;
import com.netherfront.common.match.NFSystem;
import com.netherfront.server.NFMatchController;
import com.netherfront.server.NFServerRuntime;
import com.netherfront.server.persistence.NFSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Administrative commands (section 40).
 *
 * <p>Read-only subcommands are available to everyone; anything that changes
 * match state requires permission level 2.
 */
public final class NFCommand {
    private NFCommand() {}

    private static final int ADMIN = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("netherfront")
                .then(Commands.literal("status").executes(NFCommand::status))
                .then(Commands.literal("feed").executes(NFCommand::feed))
                .then(Commands.literal("match")
                        .requires(src -> src.hasPermission(ADMIN))
                        .then(Commands.literal("start").executes(NFCommand::matchStart))
                        .then(Commands.literal("end")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(NFCommand::matchEnd)))
                        .then(Commands.literal("reset").executes(NFCommand::matchReset))
                        .then(Commands.literal("mode")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            Arrays.stream(MatchMode.values())
                                                    .map(Enum::name).forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(NFCommand::matchMode)))
                        .then(Commands.literal("system")
                                .then(Commands.argument("system", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            Arrays.stream(NFSystem.values())
                                                    .map(NFSystem::key).forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(NFCommand::matchSystem))))
                        .then(Commands.literal("eventfrequency")
                                .then(Commands.argument("frequency", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            Arrays.stream(Frequency.values())
                                                    .map(Enum::name).forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(NFCommand::matchEventFrequency)))
                        .then(Commands.literal("dominationtarget")
                                .then(Commands.argument("score", IntegerArgumentType.integer(1))
                                        .executes(NFCommand::matchDominationTarget))))
                .then(Commands.literal("team")
                        .requires(src -> src.hasPermission(ADMIN))
                        .then(Commands.literal("list").executes(NFCommand::teamList))
                        .then(Commands.literal("assign")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests(NFCommand::suggestTeams)
                                                .executes(NFCommand::teamAssign))))
                        .then(Commands.literal("unassign")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(NFCommand::teamUnassign)))
                        .then(Commands.literal("ronfaction")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(NFCommand::suggestTeams)
                                        .then(Commands.argument("faction", StringArgumentType.word())
                                                .executes(NFCommand::teamRonFaction)))))
                .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(ADMIN))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(NFCommand::debugToggle)))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(ADMIN))
                        .executes(NFCommand::reload)));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTeams(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        MinecraftServer server = ctx.getSource().getServer();
        NFSavedData.get(server).match().teams().keySet().forEach(builder::suggest);
        builder.suggest(MatchTeam.NEUTRAL);
        return builder.buildFuture();
    }

    private static NFSavedData data(CommandContext<CommandSourceStack> ctx) {
        return NFSavedData.get(ctx.getSource().getServer());
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal("[Netherfront] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE)), false);
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        NFSavedData data = data(ctx);
        MatchState match = data.match();
        long now = ctx.getSource().getServer().overworld().getGameTime();

        reply(ctx, "Mode: " + match.settings().mode().name() + "  Phase: " + match.phase().name());
        reply(ctx, "Elapsed: " + (match.elapsedTicks(now) / 20L) + "s");
        reply(ctx, "Teams: " + match.teamList().stream()
                .map(t -> t.displayName() + "(" + t.members().size() + ")")
                .collect(Collectors.joining(", ")));
        String enabled = Arrays.stream(NFSystem.values())
                .filter(s -> match.settings().isEnabled(s))
                .map(NFSystem::key)
                .collect(Collectors.joining(", "));
        reply(ctx, "Enabled systems: " + (enabled.isEmpty() ? "(none)" : enabled));
        reply(ctx, "Reign of Nether: "
                + (com.netherfront.integration.reignofnether.RonBridge.isAvailable()
                        ? (com.netherfront.integration.reignofnether.RonBridge.canResolveUnitOwners()
                                ? "integrated" : "present, manual teams")
                        : "not installed"));
        return 1;
    }

    private static int feed(CommandContext<CommandSourceStack> ctx) {
        NFSavedData data = data(ctx);
        String team = MatchTeam.NEUTRAL;
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            team = data.match().teamIdOf(player.getUUID());
        }
        var entries = data.feedFor(team);
        if (entries.isEmpty()) {
            reply(ctx, "No feed entries yet.");
            return 1;
        }
        int from = Math.max(0, entries.size() - 10);
        for (FeedEntry entry : entries.subList(from, entries.size())) {
            ctx.getSource().sendSuccess(() -> Component.literal(entry.clock() + " " + entry.category().glyph() + " ")
                    .withStyle(entry.category().color())
                    .append(entry.message().copy().withStyle(ChatFormatting.WHITE)), false);
        }
        return 1;
    }

    private static int matchStart(CommandContext<CommandSourceStack> ctx) {
        NFMatchController.start(ctx.getSource().getServer(), data(ctx));
        return 1;
    }

    private static int matchEnd(CommandContext<CommandSourceStack> ctx) {
        String team = StringArgumentType.getString(ctx, "team");
        NFMatchController.end(ctx.getSource().getServer(), data(ctx), team);
        return 1;
    }

    private static int matchReset(CommandContext<CommandSourceStack> ctx) {
        NFMatchController.reset(ctx.getSource().getServer(), data(ctx));
        return 1;
    }

    private static int matchMode(CommandContext<CommandSourceStack> ctx) {
        MatchMode mode = MatchMode.byName(StringArgumentType.getString(ctx, "mode"));
        if (mode == null) {
            reply(ctx, "Unknown mode. Valid: " + Arrays.stream(MatchMode.values())
                    .map(Enum::name).collect(Collectors.joining(", ")));
            return 0;
        }
        NFSavedData data = data(ctx);
        data.match().settings().setMode(mode);
        data.setDirty();
        reply(ctx, "Mode set to " + mode.name() + " (system toggles reset to that mode's defaults).");
        return 1;
    }

    private static int matchSystem(CommandContext<CommandSourceStack> ctx) {
        NFSystem system = NFSystem.byKey(StringArgumentType.getString(ctx, "system"));
        if (system == null) {
            reply(ctx, "Unknown system.");
            return 0;
        }
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        NFSavedData data = data(ctx);
        data.match().settings().setEnabled(system, enabled);
        data.setDirty();
        reply(ctx, system.key() + " " + (enabled ? "enabled" : "disabled"));
        return 1;
    }

    private static int matchEventFrequency(CommandContext<CommandSourceStack> ctx) {
        Frequency frequency = Frequency.byName(StringArgumentType.getString(ctx, "frequency"));
        NFSavedData data = data(ctx);
        data.match().settings().setEventFrequency(frequency);
        data.setDirty();
        reply(ctx, "Event frequency: " + frequency.name());
        return 1;
    }

    private static int matchDominationTarget(CommandContext<CommandSourceStack> ctx) {
        int score = IntegerArgumentType.getInteger(ctx, "score");
        NFSavedData data = data(ctx);
        data.match().settings().setDominationTargetScore(score);
        data.setDirty();
        reply(ctx, "Domination target score: " + score);
        return 1;
    }

    private static int teamList(CommandContext<CommandSourceStack> ctx) {
        NFSavedData data = data(ctx);
        for (MatchTeam team : data.match().teamList()) {
            String members = team.members().stream()
                    .map(uuid -> {
                        ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(uuid);
                        return p == null ? uuid.toString().substring(0, 8) : p.getGameProfile().getName();
                    })
                    .collect(Collectors.joining(", "));
            ctx.getSource().sendSuccess(() -> Component.literal(team.symbol() + " " + team.displayName())
                    .withStyle(team.color())
                    .append(Component.literal("  [" + team.id() + "]  score " + team.dominationScore()
                            + "  members: " + (members.isEmpty() ? "none" : members))
                            .withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int teamAssign(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String teamId = StringArgumentType.getString(ctx, "team");
        NFSavedData data = data(ctx);
        if (!MatchTeam.NEUTRAL.equals(teamId) && data.match().team(teamId).isEmpty()) {
            reply(ctx, "No such team: " + teamId);
            return 0;
        }
        data.match().assign(player.getUUID(), teamId);
        data.setDirty();
        NFServerRuntime.sendSnapshot(player);
        reply(ctx, player.getGameProfile().getName() + " assigned to " + teamId);
        return 1;
    }

    private static int teamUnassign(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        NFSavedData data = data(ctx);
        data.match().unassign(player.getUUID());
        data.setDirty();
        NFServerRuntime.sendSnapshot(player);
        reply(ctx, player.getGameProfile().getName() + " unassigned");
        return 1;
    }

    private static int teamRonFaction(CommandContext<CommandSourceStack> ctx) {
        String teamId = StringArgumentType.getString(ctx, "team");
        String faction = StringArgumentType.getString(ctx, "faction");
        NFSavedData data = data(ctx);
        return data.match().team(teamId).map(team -> {
            team.setRonFactionHint(faction);
            data.setDirty();
            reply(ctx, "Team " + teamId + " bound to Reign of Nether faction '" + faction + "'");
            return 1;
        }).orElseGet(() -> {
            reply(ctx, "No such team: " + teamId);
            return 0;
        });
    }

    private static int debugToggle(CommandContext<CommandSourceStack> ctx) {
        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
        NFSavedData data = data(ctx);
        data.match().settings().setDebug(enabled);
        data.setDirty();
        reply(ctx, "Debug visualisation " + (enabled ? "enabled" : "disabled"));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        // Forge reloads the config file itself; this re-pushes state to clients.
        ctx.getSource().getServer().getPlayerList().getPlayers()
                .forEach(NFServerRuntime::sendSnapshot);
        reply(ctx, "Netherfront state re-synchronised to all clients.");
        return 1;
    }
}
