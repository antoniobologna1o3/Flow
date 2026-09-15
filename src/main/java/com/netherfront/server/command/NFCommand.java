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
                .then(Commands.literal("stats").executes(NFCommand::stats))
                .then(Commands.literal("relic")
                        .then(Commands.literal("list").executes(NFCommand::relicList)))
                .then(Commands.literal("village")
                        .then(Commands.literal("list").executes(NFCommand::villageList))
                        .then(Commands.literal("info").executes(NFCommand::villageInfo)))
                .then(Commands.literal("objective")
                        .then(Commands.literal("list").executes(NFCommand::objectiveList))
                        .then(Commands.literal("complete")
                                .requires(src -> src.hasPermission(ADMIN))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests(NFCommand::suggestTeams)
                                        .executes(NFCommand::objectiveComplete))))
                .then(Commands.literal("event")
                        .requires(src -> src.hasPermission(ADMIN))
                        .then(Commands.literal("list").executes(NFCommand::eventList))
                        .then(Commands.literal("stop").executes(NFCommand::eventStop)))
                .then(Commands.literal("boss")
                        .requires(src -> src.hasPermission(ADMIN))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            Arrays.stream(com.netherfront.common.boss.WorldBossType.values())
                                                    .map(Enum::name).forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(NFCommand::bossSpawn))))
                .then(Commands.literal("supply")
                        .then(Commands.literal("debug").executes(NFCommand::supplyDebug)))
                .then(Commands.literal("spy")
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(ADMIN))
                                .executes(NFCommand::spyDebug)))
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

    private static com.netherfront.common.MatchContext context(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        NFSavedData data = NFSavedData.get(server);
        return new com.netherfront.common.MatchContext(server, data,
                server.overworld().getGameTime(), net.minecraft.util.RandomSource.create());
    }

    /** Team of the command's sender, or neutral when run from console. */
    private static String senderTeam(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            return data(ctx).match().teamIdOf(player.getUUID());
        }
        return MatchTeam.NEUTRAL;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) {
        com.netherfront.common.stats.StatsSystem stats =
                data(ctx).sub(com.netherfront.common.stats.StatsSystem.class);
        if (stats == null) {
            reply(ctx, "Statistics are unavailable.");
            return 0;
        }
        for (Component line : stats.buildReport(context(ctx))) {
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int relicList(CommandContext<CommandSourceStack> ctx) {
        com.netherfront.common.relic.RelicSystem relics =
                data(ctx).sub(com.netherfront.common.relic.RelicSystem.class);
        if (relics == null || relics.sites().isEmpty()) {
            reply(ctx, "No relic sites exist.");
            return 0;
        }
        boolean admin = ctx.getSource().hasPermission(ADMIN);
        String team = senderTeam(ctx);
        int shown = 0;
        for (com.netherfront.common.relic.RelicSite site : relics.sites()) {
            // Players only see relics their team has found; admins see all.
            if (!admin && !site.isDiscoveredBy(team)) {
                continue;
            }
            shown++;
            String owner = site.isOwned()
                    ? data(ctx).match().team(site.ownerTeamId())
                            .map(t -> t.displayName()).orElse(site.ownerTeamId())
                    : "unclaimed";
            reply(ctx, site.type().displayName() + " @ " + site.pos().getX() + ", " + site.pos().getZ()
                    + "  \u00B7  " + owner + (site.isContested() ? "  \u00B7  CONTESTED" : ""));
        }
        if (shown == 0) {
            reply(ctx, "Your team has not discovered any relic sites yet.");
        }
        return 1;
    }

    private static int villageList(CommandContext<CommandSourceStack> ctx) {
        com.netherfront.common.village.VillageSystem villages =
                data(ctx).sub(com.netherfront.common.village.VillageSystem.class);
        if (villages == null || villages.villages().isEmpty()) {
            reply(ctx, "No villages are being tracked yet.");
            return 0;
        }
        boolean admin = ctx.getSource().hasPermission(ADMIN);
        String team = senderTeam(ctx);
        for (com.netherfront.common.village.VillageState village : villages.villages()) {
            if (!admin && !village.isDiscoveredBy(team)) {
                continue;
            }
            reply(ctx, village.name() + " (" + village.type().displayName() + ") @ "
                    + village.center().getX() + ", " + village.center().getZ()
                    + "  \u00B7  " + village.levelOf(team).displayName());
        }
        return 1;
    }

    /** Detail on the village nearest the sender. */
    private static int villageInfo(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            reply(ctx, "Run this in-game, next to a village.");
            return 0;
        }
        com.netherfront.common.village.VillageSystem villages =
                data(ctx).sub(com.netherfront.common.village.VillageSystem.class);
        if (villages == null) {
            reply(ctx, "The village system is disabled.");
            return 0;
        }
        com.netherfront.common.village.VillageState village =
                villages.nearest(player.level().dimension(), player.blockPosition(), 128);
        if (village == null) {
            reply(ctx, "No tracked village within 128 blocks.");
            return 0;
        }
        String team = senderTeam(ctx);
        reply(ctx, village.name() + "  \u2014  " + village.statusLine());
        reply(ctx, "Your standing: " + village.levelOf(team).glyph() + " "
                + village.levelOf(team).displayName() + " (" + village.reputationOf(team) + ")");
        if (village.requests().isEmpty()) {
            reply(ctx, "No outstanding requests.");
        }
        for (com.netherfront.common.village.VillageRequest request : village.requests()) {
            reply(ctx, "Request: " + request.describe()
                    + "  [" + request.progress() + "/" + request.required() + "]");
        }
        return 1;
    }

    private static int objectiveList(CommandContext<CommandSourceStack> ctx) {
        com.netherfront.common.objective.ObjectiveSystem objectives =
                data(ctx).sub(com.netherfront.common.objective.ObjectiveSystem.class);
        if (objectives == null || objectives.active().isEmpty()) {
            reply(ctx, "No active objectives.");
            return 0;
        }
        String team = senderTeam(ctx);
        for (com.netherfront.common.objective.Objective objective : objectives.active()) {
            reply(ctx, objective.title() + "  \u2014  " + objective.description()
                    + "  [" + Math.round(objective.progressFraction(team) * 100) + "%]");
        }
        return 1;
    }

    /** Admin override: hands the first active objective to a team. */
    private static int objectiveComplete(CommandContext<CommandSourceStack> ctx) {
        String team = StringArgumentType.getString(ctx, "team");
        com.netherfront.common.objective.ObjectiveSystem objectives =
                data(ctx).sub(com.netherfront.common.objective.ObjectiveSystem.class);
        if (objectives == null || objectives.active().isEmpty()) {
            reply(ctx, "No active objectives.");
            return 0;
        }
        com.netherfront.common.objective.Objective objective = objectives.active().get(0);
        objective.addProgress(team, objective.required());
        data(ctx).setDirty();
        reply(ctx, "Completed '" + objective.title() + "' for " + team + ".");
        return 1;
    }

    private static int eventList(CommandContext<CommandSourceStack> ctx) {
        com.netherfront.common.event.EventDirector events =
                data(ctx).sub(com.netherfront.common.event.EventDirector.class);
        if (events == null || events.active().isEmpty()) {
            reply(ctx, "No world events are running.");
            return 0;
        }
        long now = ctx.getSource().getServer().overworld().getGameTime();
        for (com.netherfront.common.event.WorldEventInstance event : events.active()) {
            reply(ctx, event.type().displayName() + "  \u00B7  "
                    + event.remainingSeconds(now) + "s remaining");
        }
        return 1;
    }

    private static int eventStop(CommandContext<CommandSourceStack> ctx) {
        com.netherfront.common.event.EventDirector events =
                data(ctx).sub(com.netherfront.common.event.EventDirector.class);
        if (events == null || events.active().isEmpty()) {
            reply(ctx, "No world events are running.");
            return 0;
        }
        int count = events.active().size();
        events.active().clear();
        data(ctx).setDirty();
        reply(ctx, "Stopped " + count + " world event(s).");
        return 1;
    }

    private static int bossSpawn(CommandContext<CommandSourceStack> ctx) {
        com.netherfront.common.boss.WorldBossType type;
        try {
            type = com.netherfront.common.boss.WorldBossType.valueOf(
                    StringArgumentType.getString(ctx, "type").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            reply(ctx, "Unknown boss type.");
            return 0;
        }
        com.netherfront.common.boss.BossSystem bosses =
                data(ctx).sub(com.netherfront.common.boss.BossSystem.class);
        if (bosses == null) {
            reply(ctx, "The boss system is disabled.");
            return 0;
        }
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(
                ctx.getSource().getPosition());
        bosses.forceSpawn(context(ctx), type, pos);
        reply(ctx, "Spawning " + type.displayName() + " nearby.");
        return 1;
    }

    private static int supplyDebug(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            reply(ctx, "Run this in-game.");
            return 0;
        }
        com.netherfront.common.supply.SupplySystem supply =
                data(ctx).sub(com.netherfront.common.supply.SupplySystem.class);
        if (supply == null) {
            reply(ctx, "The supply system is disabled.");
            return 0;
        }
        String team = senderTeam(ctx);
        boolean supplied = supply.isSupplied(team, player.level().dimension(), player.blockPosition());
        reply(ctx, "You are " + (supplied ? "IN supply" : "OUT of supply") + ".");
        var network = supply.networkOf(team);
        if (network.isEmpty()) {
            reply(ctx, "No supply network: build a Supply Depot, or win a village over.");
            return 1;
        }
        for (com.netherfront.common.supply.SupplyNode node : network) {
            reply(ctx, "  " + "\u2192".repeat(Math.max(1, node.depth() + 1)) + " " + node.label()
                    + " @ " + node.pos().getX() + ", " + node.pos().getZ()
                    + "  (r=" + node.radiusChunks() + " chunks, " + node.depth() + " hops)");
        }
        return 1;
    }

    private static int spyDebug(CommandContext<CommandSourceStack> ctx) {
        com.netherfront.common.spy.SpySystem spies =
                data(ctx).sub(com.netherfront.common.spy.SpySystem.class);
        if (spies == null || spies.spies().isEmpty()) {
            reply(ctx, "No spies are deployed.");
            return 0;
        }
        for (com.netherfront.common.spy.SpyState state : spies.spies().values()) {
            reply(ctx, state.teamId() + " spy  \u00B7  suspicion " + state.suspicion()
                    + "/100  \u00B7  " + (state.isDetected() ? "DETECTED" : "hidden")
                    + "  \u00B7  " + state.chunksRevealed() + " areas surveyed");
        }
        return 1;
    }
}
