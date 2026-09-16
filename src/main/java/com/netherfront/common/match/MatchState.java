package com.netherfront.common.match;

import com.netherfront.common.util.NbtUtils2;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Teams, settings and lifecycle for the current match. Every gameplay
 * calculation that depends on "who owns this" resolves through here, so there
 * is exactly one authority for team membership on the server (section 35).
 */
public final class MatchState {
    private final Map<String, MatchTeam> teams = new LinkedHashMap<>();
    private MatchSettings settings = new MatchSettings();
    private MatchPhase phase = MatchPhase.LOBBY;
    private long startedAtTick;
    private long endedAtTick;
    private String winningTeamId = "";

    public MatchState() {
        createDefaultTeams();
    }

    private void createDefaultTeams() {
        teams.put("team_a", new MatchTeam("team_a", "Team A", ChatFormatting.AQUA, "◆"));
        teams.put("team_b", new MatchTeam("team_b", "Team B", ChatFormatting.GOLD, "▲"));
    }

    public MatchSettings settings() {
        return settings;
    }

    public MatchPhase phase() {
        return phase;
    }

    public boolean isActive() {
        return phase == MatchPhase.ACTIVE;
    }

    public long startedAtTick() {
        return startedAtTick;
    }

    public long endedAtTick() {
        return endedAtTick;
    }

    /** Ticks elapsed since the match started, or 0 while in lobby. */
    public long elapsedTicks(long now) {
        if (phase == MatchPhase.LOBBY) {
            return 0L;
        }
        long end = phase == MatchPhase.ENDED ? endedAtTick : now;
        return Math.max(0L, end - startedAtTick);
    }

    public void start(long now) {
        this.phase = MatchPhase.ACTIVE;
        this.startedAtTick = now;
        this.endedAtTick = 0L;
        this.winningTeamId = "";
    }

    public void end(long now, String winningTeamId) {
        this.phase = MatchPhase.ENDED;
        this.endedAtTick = now;
        this.winningTeamId = winningTeamId == null ? "" : winningTeamId;
    }

    public void reset() {
        teams.clear();
        createDefaultTeams();
        settings = new MatchSettings();
        phase = MatchPhase.LOBBY;
        startedAtTick = 0L;
        endedAtTick = 0L;
        winningTeamId = "";
    }

    public String winningTeamId() {
        return winningTeamId;
    }

    public Map<String, MatchTeam> teams() {
        return teams;
    }

    public List<MatchTeam> teamList() {
        return new ArrayList<>(teams.values());
    }

    public Optional<MatchTeam> team(String id) {
        return Optional.ofNullable(teams.get(id));
    }

    public MatchTeam createTeam(String id, String displayName, ChatFormatting color, String symbol) {
        MatchTeam team = new MatchTeam(id, displayName, color, symbol);
        teams.put(id, team);
        return team;
    }

    public boolean removeTeam(String id) {
        return teams.remove(id) != null;
    }

    /** Assigns a player to a team, removing them from any other team first. */
    public void assign(UUID player, String teamId) {
        teams.values().forEach(t -> t.members().remove(player));
        MatchTeam target = teams.get(teamId);
        if (target != null) {
            target.members().add(player);
        }
    }

    public void unassign(UUID player) {
        teams.values().forEach(t -> t.members().remove(player));
    }

    public Optional<MatchTeam> teamOf(UUID player) {
        return teams.values().stream().filter(t -> t.contains(player)).findFirst();
    }

    public Optional<MatchTeam> teamOf(ServerPlayer player) {
        return teamOf(player.getUUID());
    }

    /** Team id for a player, or {@link MatchTeam#NEUTRAL} when unassigned. */
    public String teamIdOf(UUID player) {
        return teamOf(player).map(MatchTeam::id).orElse(MatchTeam.NEUTRAL);
    }

    public boolean sameTeam(UUID a, UUID b) {
        String ta = teamIdOf(a);
        return !MatchTeam.NEUTRAL.equals(ta) && ta.equals(teamIdOf(b));
    }

    public boolean isHostile(String ownerTeam, String viewerTeam) {
        return !MatchTeam.NEUTRAL.equals(ownerTeam)
                && !MatchTeam.NEUTRAL.equals(viewerTeam)
                && !ownerTeam.equals(viewerTeam);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("settings", settings.save());
        tag.putString("phase", phase.name());
        tag.putLong("startedAt", startedAtTick);
        tag.putLong("endedAt", endedAtTick);
        tag.putString("winner", winningTeamId);
        tag.put("teams", NbtUtils2.writeList(teamList(), MatchTeam::save));
        return tag;
    }

    public static MatchState load(CompoundTag tag) {
        MatchState state = new MatchState();
        state.settings = MatchSettings.load(tag.getCompound("settings"));
        try {
            state.phase = MatchPhase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException ignored) {
            state.phase = MatchPhase.LOBBY;
        }
        state.startedAtTick = tag.getLong("startedAt");
        state.endedAtTick = tag.getLong("endedAt");
        state.winningTeamId = tag.getString("winner");
        if (tag.contains("teams", Tag.TAG_LIST)) {
            state.teams.clear();
            for (MatchTeam team : NbtUtils2.readList(tag, "teams", MatchTeam::load)) {
                state.teams.put(team.id(), team);
            }
            if (state.teams.isEmpty()) {
                state.createDefaultTeams();
            }
        }
        return state;
    }
}
